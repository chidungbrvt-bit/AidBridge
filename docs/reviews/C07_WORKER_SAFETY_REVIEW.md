# C07 — Worker safety review, 12/11/2026

## Kết luận hiện hành

**PARTIAL / chưa sign-off Fuji worker.** Đã review code Dũng PR10 head `94a99e1`, sửa finding receipt-budget trên khoa và bổ sung 3 Java integration tests. Chưa compile/chạy tests Java vì máy Khoa không có Java 21/Maven hoặc PostgreSQL/test env hợp lệ; không coi tests đã viết là PASS. C05 Fuji job thật vẫn chưa chạy. Không thay C06 contract hoặc policy D53/D54.

## Git và code chung

PR9 đã được nhóm merge trước lượt này (main `051e40f`); không tự merge lại. PR11 được merge bằng merge commit `8005917` sau review client/session, 31 Node/TypeScript tests PASS bằng tsx hiện có; GitHub mergeable/clean, không checks báo fail hoặc required-check bypass. Không có CI checks nên không ghi CI PASS; build/browser trước đó là evidence Minh báo, không phải rerun ở lượt này. PR10 chưa merge vào main vì finding safety còn phải kiểm chứng. Khoa đã fast-forward main rồi merge origin/dung và origin/minh vào khoa, sau đó merge main `8005917`; không reset/rebase/checkout branch khác, không push nhánh Dũng/khoa hoặc sửa PR10. Các merge local giữ toàn bộ lịch sử cho C07; không có conflict.

## Review safety theo kế hoạch bước5

| Hạng mục | Kết luận / giới hạn |
|---|---|
| Signer/chain/contract | Config enabled mặc định false; chỉ 43113/31337; Fuji HTTPS, startup chainId check, key/env được redact. Web3j ký EIP155 zero-value với registry config. Khoa đã readback đúng deployed C03 code/roles; Dũng phải xác nhận cùng config/signer khi bật worker |
| Nonce | pendingNonce + maxOpenNonce; UNIQUE(chain,lower(signer),nonce) và saveAndFlush trước gửi. Conflict không broadcast attempt mới; query signer không phải global signer lease. Pilot chạy một worker/process; long-running expired lease/multi-process recovery chưa có runtime evidence ở máy này |
| Persistence | Job manifest/commitment/salt được attach một lần; AES256-GCM với AAD(jobId,purpose), signed bytes/hash/nonce/chain/registry/signer được lưu PREPARED trong short SQL transaction trước broadcast |
| Restart/unknown | PREPARED attempt được decrypt/rebroadcast exact bytes, không sinh manifest/eventId/nonce mới. SUBMITTED poller đọc receipt bên ngoài SQL; giữ expected signer từ attempt. Existing IT có restart trước TX3; bổ sung late receipt sau restart |
| Receipt/event | Check receipt hash/from/to/status, canonical block, một CaseVerified đúng topic/refs/sequence/schema/commitment; mismatch không confirmed. Chỉ success đúng mới CONFIRMED atomically; revert có receipt mới FAILED. Chưa Java-vs-Fuji-job evidence |
| D53 | ContributionService khóa case trước MAX(sequence)+1; V12 UNIQUE(case,sequence). Worker lọc CASE_VERIFIED, không lấy AID_PLEDGED/COMMITMENT_REDUCED; verification là sequence1. Không bật worker milestone trước khi có dependency-confirmed gate và deployment C06 đã được review riêng |
| Key custody | D54 ví backend riêng trên máy Dũng; không cần Khoa lấy key đó. Hai ví DPAPI Khoa/Console Wallet không được đọc hoặc giải mã ở lượt này. Secret-key AES phải ổn định qua restart; không thay key khi còn pending attempts |

## Finding đã sửa

CaseVerifiedWorker.handleMissingReceipt trước đây dùng retryCount>=5 để markFailed(RECEIPT_NOT_FOUND). retryCount còn tăng do binding/RPC errors, không phải bằng chứng tx reverted. Sau RPC outage hoặc mining chậm, worker dừng polling vĩnh viễn dù receipt có thể đến sau.

Sửa tối thiểu: hết budget thì **dừng automatic rebroadcast**, giữ job/attempt SUBMITTED và original hash/encrypted signed bytes/nonce, scheduleRetry 30s với RECEIPT_PENDING_OPERATOR_ATTENTION, trả PENDING. Polling tiếp tục; receipt hợp lệ đến muộn vẫn CONFIRMED. Không re-sign, không thay DB migration hoặc generic retry policy. retryCount vẫn là bộ đếm chung, không tuyên bố bằng đúng số lần broadcast. Operator attention qua reason code/log; chưa có UI operator mới.

## Gate còn thiếu: signer serialization và lease fencing

Acceptance C07 yêu cầu **one in-flight/signer**, không chỉ unique nonce. Code hiện tại chưa thực thi điều kiện này: `lockNextCaseVerifiedJob` chỉ khóa từng job; `processNextJob` lấy `maxOpenNonce + 1`, nên ngay cả một worker cũng có thể gửi job B khi job A cùng signer còn SUBMITTED. UNIQUE(chain, signer, nonce) ngăn trùng nonce nhưng không ngăn nhiều nonce đang chờ. Đây là khoảng trống implementation so với acceptance, không phải chỉ thiếu kết quả test. Pilot một worker không tự giải quyết khoảng trống này.

Lease 60 giây cũng chưa có fencing token trước persist/broadcast; khi RPC chậm quá lease, worker khác có thể reclaim job. Test mới chỉ chứng minh mục tiêu hai workers tranh **cùng job trong lease**, chưa chứng minh hai jobs cùng signer hoặc lease hết hạn. Cần Dũng/Khoa phối hợp signer serialization + lease-expiry regression trước sign-off đầy đủ C07; lượt này chỉ sửa finding receipt-budget, không âm thầm mở rộng data model/worker locking. Không merge PR10 với tuyên bố C07 đã hoàn tất.

## Regression và bằng chứng

- `CaseVerifiedWorkerIT.exhaustedRecoveryBudgetKeepsOriginalAttemptAndAcceptsLateReceiptAfterRestart`: retry_count 10, nhiều lần missing receipt vẫn SUBMITTED, không broadcast thêm; hash/nonce/ciphertext nguyên; worker restart cùng cipher, receipt đến muộn ->CONFIRMED.
- `repeatedRpcOutageAndDuplicateBroadcastNeverTurnUnknownOutcomeIntoFailure`: already-known rebroadcast cùng bytes, 6 RPC outages rồi receipt ->CONFIRMED, một attempt.
- `concurrentWorkersClaimOneJobAndPersistOnlyOneAttempt`: 2 workers cạnh tranh cùng job, một claim/broadcast/attempt; bên còn lại không nhận job.
- FakeBlockchainAdapter thêm fault receipt một lần; không sửa production adapter hoặc tạo secret mới.
- **Java compile/JUnit/PostgreSQL: NOT RUN**, không có JDK21/Maven trên PATH/JAVA_HOME, DB env/listener 5432/5433/8081 không có. Không cài môi trường, tạo DB/seed hoặc chạy tests ghi dữ liệu bằng credentials khác.
- Lệnh Dũng cần chạy trong backend/aidbridge-backend với DB test được phép: `./mvnw -Dtest=CaseVerifiedReceiptVerifierTest,Web3jBlockchainAdapterTest,JobSecretCipherTest,GoldenVectorParityTest test`, sau đó `./mvnw -Dit.test=CaseVerifiedWorkerIT,BlockchainTransactionPersistenceIT verify`. Dùng AIDBRIDGE_TEST_DATABASE_* và giữ toàn bộ rollback/idempotency/nonce assertions. Restart E2E/lease-expiry cũng cần xác nhận trước sign-off toàn bộ C07.
- `node frontend/dev/donor-preview/node_modules/tsx/dist/cli.mjs --test frontend/tests/api-session.test.ts frontend/tests/api-workflow.test.ts`: **31/31 PASS**, không cài root dependencies.
- Hardhat local: **Mocha 147 passing, 1 pending**; Hardhat summary ghi 148 passing nhưng không tính test pending là PASS. Pending C06 chưa triển khai không thuộc C07; không báo đã chạy 148 test thành công. Không dùng suite này làm bằng chứng worker Java.
- C04 read-only verify-fuji PASS với snapshot C03; F02 checker PASS (5 snapshots/10 scenarios). Source contract C06 không sửa.
- git diff --check và scope check trước commit PASS. Không CI/runtime Java PASS được suy ra từ kết quả Dũng báo.

## Fuji và bước7 bàn giao Dũng

Read-only chain 43113, registry 0x015D941c679b0c402c8262BE237adAD36cEf6726; orgRef 0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46 vẫn registered/active và assigned 0x559994839f19F40ac704918C3FF1a913AC02dC01. Admin readback PASS. Balance Admin 0.348775352, old Relayer 0.149700527, new 0x95a9564899849CF566DE5713205C4B747F361f55=0 AVAX. Latest=pending nonce lần lượt 2/1/0; đây không chứng minh DB trên máy Dũng không có PREPARED/SUBMITTED attempts.

**Chưa rotate/fund.** Blocker cụ thể: thiếu xác nhận worker đã dừng và danh sách/đối chiếu attempts/job ký bằng ví cũ cùng các nghĩa vụ organization khác. Không suy từ nonce chain hoặc lời bàn giao E2E local rằng DB dev không còn nghĩa vụ ký. Dũng cung cấp signer/chain/job IDs, statuses/tx hashes và xác nhận không có pending obligation của ví cũ; không gửi secret. Khoa không có DB được phép để tự lấy bằng chứng này. Worker safety tests mới cũng cần chạy trước pilot.

Sau khi đủ điều kiện và được tiếp tục: dùng Admin updateOrganizationRelayer đúng orgRef, kiểm receipt/code/assignment/new-role/old-role/Admin độc lập; nếu sticky withdrawal làm thiếu role thì explicit grant theo policy được duyệt. Sau rotation thành công có thể chuyển đề xuất 0.149 test AVAX từ ví cũ sang mới, trừ phí và đọc lại balance/receipt, không lấy Admin để funding. Không redeploy, không gửi thử milestone C06 vào registry C03. Lượt này không dùng signer, không faucet/wallet browser, không tx hashes mới. Nếu receipt chưa có thì pending/attention, không confirmed hoặc failed giả.

## Readiness F07/F08 — chưa triển khai trong lượt này

- F07: shell/API client F03/F04 đã ở main qua PR11; 31 client tests PASS. B07 chỉ có trên khoa/origin/dung, chưa vào main vì PR10 chưa merge. DB/campaign published/donor dev seed D52 không kiểm chứng được trên máy này; cần Dũng cung cấp môi trường dev được phép + published campaign + donor session. Chưa đủ nghiệm thu real G01-G03. Không tự seed hoặc dựng app mới.
- F08: fixtures F02 R01/D01, types và endpoint/state mapping đầy đủ, checker PASS; có thể bắt đầu mock UI theo phân công, không chờ B08/B09. API receipt/delivery thật chưa sẵn sàng; lượt này chỉ đánh giá readiness, không code UI.

## Việc tiếp theo và người phụ trách

Khoa: commit local safety fix/tests/report, giữ C07 PARTIAL chờ runtime evidence; không push/PR trong lượt này. Dũng: chạy tests mới trên JDK21/PostgreSQL, review patch, đối chiếu signer attempts/binding và job B05 thật; phối hợp đưa fix cùng PR10 vào main khi đạt gate. Minh: shell đã merge; real donor integration cần B07/main + dev session/campaign, F05 vẫn không dùng independent pilot hash thay job thật. C05 vẫn PARTIAL; không đánh dấu task của Dũng/Minh Done hoặc thay estimate/deadline gốc.
