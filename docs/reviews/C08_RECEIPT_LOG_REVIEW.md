# Task 7 — Review C08 receipt/log recovery

Phạm vi lịch 13/11/2026, review cập nhật ngày 14/11/2026 (Asia/Saigon). Reviewer: Khoa/Codex.

**Kết luận hiện hành: receipt recovery PASS sau sửa hai findings; log replay PENDING.** Hai sửa đổi chain/context và bốn tests được bàn giao đã được xác nhận; không còn blocker “chưa push implementation” của lượt trước. Không đánh C08 Done hoặc PASS toàn bộ.

## Cập nhật sửa lỗi ngày14/11/2026

Người dùng cho phép Khoa sửa đúng R1/R2 và cập nhật PR21. Baseline36108eb được cherry-pick riêng vào khoa (`76d2e79`), giữ Task5f89a2dd và review30cc805. Candidate gửi PR21 là **9a9f7fb8cccc1f914b8050585ffb467c03e8eb51**, chỉ6 file C08, parent là head Dũng f4319d0; không mang lịch sử Task5 vào PR, không force push.

- R1 **RESOLVED**: verifier reject logIndex null/âm/vượt long; worker không clamp thành0. Proof sai pending, không tạo receipt hoặc confirmed state. Index0 hợp lệ giữ nguyên.
- R2 **RESOLVED**: adapter chỉ nhận raw status canonical0x0/0x1; thiếu/bất thường ném RECEIPT_STATUS_INVALID. Verifier cũng phòng thủ int ngoài0/1; context vẫn kiểm trước revert0 hợp lệ. Poll giữ pending/attempt gốc, không REVERTED giả.
- [Existing manual C07 workflow run43473786126](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43473786126) **SUCCESS** trên đúng candidate: Java compile, **26 unit +32 IT PASS**, 0 failures/errors/skips. Đã đọc XML artifact trong bộ nhớ: verifier8, adapter5, crypto3, golden8, disabled2; WorkerIT28, persistence3, Hardhat local E2E1.
- Tests mới: null/negative/overflow index, raw status null/empty/0x2/malformed, verifier abnormal ints, status0/1 và index0 hợp lệ. Bảy WorkerIT cases kiểm pending/no receipt/no confirmed, giữ eventId/hash/nonce/attempt, phục hồi bằng proof hợp lệ rồi poll tiếp không cập nhật lại. Tests timeout/retry/rollback C08 cũ vẫn trong suite PASS.
- Không tạo/sửa workflow hoặc cài Java/DB lên Windows. Chỉ chạy workflow thủ công hiện có trên runner GitHub, không Fuji/wallet. Log scanner/cursor/LogReplayIT không triển khai, giữ PENDING riêng. Findings và phương pháp bên dưới là lịch sử review trước bản sửa, không còn là blocker R1/R2 hiện hành.

## Phiên bản và phương pháp

- Commit được review: `36108ebb67fdf7c898921209555cf909aad71799`; origin/dung head `f4319d0` không đổi C08 sau commit này. PR21 là bàn giao chung B08/C08; review này không nghiệm thu B08.
- Đọc diff, worker/verifier/adapter và assertions tests, đối chiếu `C08_DUNG_RECOVERY_2026_11_13.md`. Không sửa backend/contract, chạy Actions, RPC, wallet hoặc cài Java/DB.
- Kết quả 40/40 là **Dũng báo cáo đã thực chạy** trên snapshot f225385 cộng bốn file Java, V1–V14, bản sao fixture dùng cổng PostgreSQL55438. Commit36108eb nay có worker, verifier, WorkerIT và FakeBlockchainAdapter tương ứng; không nhầm file thứ tư với ReceiptVerifierTest. Chưa có test run độc lập mới của Khoa trên Windows; không suy40/40 bao phủ các input còn thiếu bên dưới.

## Phần đã kiểm chứng qua review code/tests

| Phần | Bằng chứng trong36108eb | Kết luận |
|---|---|---|
| Poll đúng chain | CaseVerifiedWorker.java:322–325 gọi readChainId; sai chain defer INVALID_CHAIN_ID | PASS review |
| Revert đúng context | CaseVerifiedReceiptVerifier.java:54–61 kiểm to/from trước phân loại revert | PASS review cho status0 hợp lệ |
| Exact successful event | hash/canonical block, registry/signer, đúng một CaseVerified không removed, topic0/4topics, org/case/eventId, 128bytes data, sequence/commitment/schema | PASS review cho proof hợp lệ |
| Timeout/late receipt | receiptTimeoutKeepsApprovalPendingAndLateProofConfirmsTheOriginalAttempt: HTTP pending/replay, SQL SUBMITTED, sau đó cùng hash/nonce/attempt confirmed, một audit/job/broadcast | PASS review assertions |
| Wrong receipt | rejectsReceiptWithoutMatchingProof: wrong contract, missing event, valid revert, foreign revert; job/attempt/error states kiểm riêng | PASS review assertions |
| Wrong chain/canonical block | defersOnWrongRpcChainAndNoncanonicalBlockBeforeConfirmingOriginalAttempt: pending rồi confirm original attempt | PASS review assertions |
| Rollback DB | receiptPollingRepairsRolledBackConfirmationWithoutNewRecords: ép SQLSTATE23514, rollback cả attempt/job; hết lease poll lại cùng eventId/hash/nonce/attempt; poll tiếp rỗng, counts không tăng | PASS review assertions |

Polling cập nhật job/attempt trong cùng short SQL transaction; không tạo event/job/sequence mới ở đường confirm. Recovery không ký giao dịch mới; khi receipt vắng có thể **rebroadcast đúng signed bytes/hash/nonce đã journal** theo C07, khác với tạo giao dịch hoặc mutation nghiệp vụ thứ hai. Khi đã nhận proof hợp lệ, đường confirm không rebroadcast. Không tuyên bố log scanner/replay đã được kiểm chứng từ các tests này.

## Findings lịch sử tại36108eb — đã giải quyết trong9a9f7fb

### C08-R1 — Medium: nhận proof không có log index hợp lệ

- `backend/aidbridge-backend/src/main/java/com/aidbridge/blockchain/worker/CaseVerifiedReceiptVerifier.java:109–110`: logIndex=null trở thành -1; số âm cũng không bị từ chối, vẫn trả Confirmed nếu các field khác đúng.
- `.../worker/CaseVerifiedWorker.java:347`: `Math.max(confirmed.logIndex(), 0)` biến index thiếu/âm thành0 và lưu proof CONFIRMED. Hậu quả là tự tạo vị trí log không được RPC chứng minh, không đáp ứng exact observation identity.
- Tái hiện ở test fixture: dùng receipt hợp lệ, chỉ thay logIndex thành null hoặc BigInteger(-1); verifier đi tới Confirmed và worker ghi index0. Đây là đường đi xác định từ code, **chưa chạy Java test mới**.
- Đề xuất: reject/defer integrity error khi index thiếu/âm/không biểu diễn được; bỏ clamp/default0. Thêm unit + WorkerIT xác minh không CONFIRMED/không ghi proof bịa; index0 thật vẫn hợp lệ.

### C08-R2 — Medium: status bất thường bị coi là revert thật

- `.../adapter/Web3jBlockchainAdapter.java:106`: mọi raw status khác `0x1` (kể cả null/`0x2`) bị chuyển thành0.
- `.../worker/CaseVerifiedReceiptVerifier.java:60–61`: mọi int khác1 được trả Reverted. Với context/hash/block đúng, worker ghi attempt REVERTED và job FAILED, dù chưa có status0 hợp lệ.
- Tái hiện: adapter nhận raw status null/`0x2`, hoặc gọi verifier với ChainReceipt.status=2; đường đi hiện tại phân loại revert. **Chưa chạy Java test mới**.
- Đề xuất: adapter parse/validate status chỉ0/1 theo JSON-RPC; malformed/missing status trả lỗi riêng, giữ attempt chưa có kết quả xác định. Verifier phòng thủ cũng phải reject int ngoài0/1. Bổ sung tests adapter/verifier/worker, giữ test revert0 thật và context trước revert.

Hai finding là fail-closed validation với RPC proof bất thường, không khẳng định giao dịch Ethereum hợp lệ tự sinh index âm/status2. Bốn tests tại36108eb chưa bao phủ các input này; regression tests trong9a9f7fb đã bổ sung và PASS theo bằng chứng CI ở đầu tài liệu. Các mô tả lỗi và đề xuất trong mục này chỉ ghi lại trạng thái trước bản sửa.

## Log replay và boundary đề xuất — chưa triển khai

Adapter chỉ có getReceipt/canonicalBlockHash, chưa readLogs/readEventRecord; chưa bounded scanner, durable cursor/observation hoặc LogReplayIT. **PENDING**, không phải test FAIL và không phải lỗi của receipt polling đã bàn giao.

Đề xuất tối thiểu để Dũng/Khoa thống nhất trước triển khai (PROPOSED, không phải quyết định kiến trúc mới đã chốt):

1. `readLogs(fromBlock,toBlock,registry,topic0Set)` trả observation giữ nguyên chainId, address, txHash, blockNumber/hash, transactionIndex, logIndex, topics/data/removed. Có thể thêm `readEventRecord(eventId)` để cross-check operation/digest; readback không thay thế receipt/log proof.
2. Cursor riêng theo chain/registry/deployment, bắt đầu deployment block, range có cap cấu hình; xác minh chain/canonical block và receipt/context của transaction trước apply. Không biến log missing/mismatch thành confirmed, không fabricate index, không nuốt lỗi rồi tiến cursor vượt dữ liệu chưa xử lý an toàn.
3. Unique observation `(chainId,txHash,logIndex)` và mapping logical job theo deployment + operation/eventId. Observation/projection/cursor cập nhật nguyên tử; rollback rồi đọc lại phải sửa cùng record, không tạo job/sequence mới. Removed/noncanonical observations phải được reconcile, không ghi đè lịch sử bằng proof mới không đối chiếu.
4. C10/D55 VOID: một resolveDispute/job/eventId/sequence, **hai observations** DeliveryVoided → DisputeResolved. Đối chiếu cùng tx/registry/org/case/commitment/sequence, đúng fields/ref/resolution và thứ tự logIndex; thiếu/sai/duplicate bộ log không confirmed. Không áp quy tắc “mỗi eventId chỉ được một log” của CASE_VERIFIED lên VOID.
5. LogReplayIT sau này: replay cùng observations hai lần, crash/SQL rollback giữa chừng, thiếu/sai log, noncanonical, hai log VOID chỉ apply một logical operation. Live audit projection API và các milestone C06 chưa được nghiệm thu trong review CASE_VERIFIED này.

Khoa phối hợp thống nhất receipt/log semantics và boundary; Dũng tiếp nhận transaction/observation/cursor tests khi triển khai phần log ingest; Minh phối hợp API audit projection. R1/R2 đã được Khoa sửa và kiểm chứng, không còn là việc chờ Dũng. Chưa thực hiện scanner hoặc mở task mới trong lượt review.

## Điều kiện chốt

**Receipt recovery PASS:** R1/R2 đã sửa tại9a9f7fb, 26 unit +32 IT PASS trên run43473786126; PR21 đã merge vào main tại7b7ab6e. Không còn blocker của hai finding này. Scanner/replay giữ PENDING riêng đến khi được triển khai/kiểm thử, không giả PASS toàn C08. Lượt bàn giao cuối không chạy lại Java/DB hoặc Actions.

## Task9 ngày14/11/2026 — chốt C07/C08 và điều kiện C10

**PASS WITH NOTES cho Task9 (review/readiness), không phải PASS toàn bộ C08/C10.** Main5122281, dung9c441eb, minhc64bb4e sau fetch. Local khoa giữ2713675 và review B08. Không có thay đổi Java blockchain hoặc WorkerIT so với9a9f7fb trên dung/main; không chạy lại tests/Actions/RPC. Minh mới sửa mock auditUrl theo D49 trên nhánh riêng, chưa có API audit thật được chứng minh. Dũng hiện chỉ thêm B09 FAILED; không có worker goods mới. [Task3 payload mapping](B08_GOODS_CHAIN_REVIEW_2026_11_14.md) giữ nguyên kết luận, không review lại B08.

### C07/C08 đã chốt

- SignerSessionLock theo chain+signer dùng connection PostgreSQL riêng, session pooling bắt buộc; V13 unique open signer/job và nonce journal. Worker:110–128 dùng max(node pending nonce, reserved nonce+1), persist PREPARED encrypted signed bytes/hash trước broadcast ở short transaction; recovery reuse attempt/bytes/hash/nonce, không ký lại. Giữ journal nonce lịch sử kể cả khi đổi registry cùng signer/chain.
- Guard assertHeld trước ký/persist/broadcast và sau RPC; lease hết không thay thế phiên còn giữ lock. Mất session fence bước kế tiếp; không hứa thu hồi RPC đã phát. Retry budget hết không coi missing receipt là revert, vẫn poll cùng attempt. Query previous-sequence phải CONFIRMED, không skip job trước chỉ vì worker chưa hỗ trợ operation đó.
- C08 verifier kiểm transaction hash, canonical block, destination/signer trước valid revert0, exact event topics/data/refs/sequence/schema/commitment. Poll đọc lại chainId; proof thiếu logIndex/âm/overflow hoặc status không0/1 không CONFIRMED/REVERTED giả. Worker confirm DB job+attempt cùng transaction; rollback rồi poll sửa cùng records, không tạo logical event/sequence mới.
- Bằng chứng giữ từ [C07 CI43191763706](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43191763706) và [C08 CI43473786126](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43473786126): lần lượt22+18 và26+32 tests PASS, compile PASS, không skips/errors/failures. Đây là PostgreSQL/Hardhat runner evidence đã xác minh trước, không chạy mới ngày14; không dùng số test làm bằng chứng Fuji goods.
- Scanner/readLogs/readEventRecord/cursor/observation/LogReplayIT **chưa triển khai**. Receipt rollback recovery đã PASS không thay thế durable bounded log replay trong acceptance C08. Trạng thái toàn task C08 giữ In Progress/In Review theo review nhóm, không Done.

### C10 — ranh giới kiểm chứng độc lập

| Operation nghiệp vụ | Function → event thực tế | Readiness |
|---|---|---|
| CASE_VERIFIED | recordCaseVerification → CaseVerified | Worker/receipt verifier đã có; hai Fuji receipts trên C03 trong báo cáo B05. C10 có thể đối chiếu slice này, nhưng cần DB expected trực tiếp để hoàn tất chứng minh độc lập. |
| AID_PLEDGED | recordAidCommitment → AidCommitted | B07 outbox + C06 source/tests/ABI có; worker/receipt Fuji chưa có bằng chứng. Không nhầm tên event là AidPledged. |
| COMMITMENT_REDUCED | recordCommitmentReduction → CommitmentReduced | B07 outbox + C06 source/tests có; worker/receipt Fuji chưa có. |
| GOODS_RECEIVED | recordGoodsReceipt → GoodsReceived | B08 private refs/payload/sequence đã review; worker và registry C06 Fuji chưa có. |
| DELIVERY_RECORDED | recordDelivery → DeliveryRecorded | Contract C06 có; B09 DELIVERED/outbox chưa bàn giao, chưa có worker/receipt Fuji. FAILED không có job/sequence chain. |

Expected phải lấy từ DB bất biến: audit→job→attempt; eventId=logical_event_id, org/case=public_ref, sequence/schema/commitment từ job, signer/registry/chain/hash từ attempt gắn deployment. Audit UUID/event_key/receiptId không thay eventId. Đối chiếu receipt status1, txHash, to/from, canonical blockHash/number, log transaction/block identity và index thật, exact topic0/indexed refs/data theo operation, thêm operation-bound digest/ref khi đọc getEventRecord. Không lấy calldata làm expected duy nhất, không đồng nhất evidenceCommitment với Keccak payloadDigest. Poll verifier hiện tại không tự chứng minh raw calldata/getEventRecord/full DB payload parity; C10 phải ghi rõ mức kiểm chứng.

D55 VOID: một resolveDispute/job/eventId/sequence, hai observations DeliveryVoided→DisputeResolved. Kiểm đủ cả hai logs cùng tx/org/case/refs/sequence, đúng resolution và thứ tự logIndex; thiếu/sai/duplicate không đủ xác nhận. Không áp verifier “đúng một CaseVerified” cho mọi operation. Dedupe observation theo chain/txHash/logIndex và logical operation theo deployment/eventId; đây là yêu cầu thiết kế cho verifier/scanner tương lai, không phải implementation đang có.

Fuji hiện có hai CaseVerified tx được ghi tại [B05 report](B05_REAL_FUJI_RUN_2026_11_13.md): `0x465140011495f85743088ada113097204a891e41bc0b985b15e653366cb8566a`, `0x97104a33730c05210da67a21c96b271876d744fcb61fbab46a5d87cff925f98e`. Report có RPC/explorer/readback, Dũng xác nhận DB; raw DB columns/B2/restart-time evidence vẫn có mục NOT_VERIFIED. Không xác minh RPC/DB lại hoặc đổi các mục đó thành PASS. Không có bằng chứng Fuji các operation goods/delivery/VOID trong phạm vi đã đọc.

### Phương án phối hợp mới — PROPOSED, chưa D56 chính thức

Không thấy D56 trong Decision Log trên main/dung/minh hiện tại. Đề xuất Dũng mở rộng worker/verifier operation, Khoa deploy/verify registry C06 sau B09 REST/SQL và contract gate là **tương thích kỹ thuật**, thay đề xuất owner worker ban đầu của Task3 nếu nhóm chấp thuận; không tự sửa phân công hoặc cấp phép deploy. Minh đã align mock campaign auditUrl theo D49 tại c64bb4e, nhưng chưa thay thế xác nhận cross-owner cho API URI. D49 `/api/public/cases/{publicCaseRef}/audit` là mục tiêu; endpoint thật và cập nhật T02/backend projection còn phải thống nhất, link chưa được coi là live.

**Điều kiện cutover cần bổ sung rõ, không phải lỗi C07 hiện tại:** job hiện có chainId nhưng chưa pin registry; attempt mới pin contract_address khi ký. Chỉ đổi BLOCKCHAIN_REGISTRY_ADDRESS và kiểm PREPARED/SUBMITTED là chưa đủ: PENDING/PROCESSING cũ chưa có attempt vẫn có thể được claim theo config mới. Dũng phải dừng worker, kiểm cả unprepared jobs/leases lẫn signed attempts và scope organization; bảo đảm queue C03 không thể bị chọn ở config C06 (drain/reconcile hoặc explicit deployment-aware routing được review). Không xóa journal/đổi confirmed/nhảy sequence để lách. Đây là rollout gate trước deploy/config switch, **không chặn Task9 hoặc viết B09**. Nếu vẫn cần poll C03 sau cutover, phải có cấu hình/routing riêng đúng deployment và cùng signer lock/journal, không giả định một global address xử lý cả hai.

Case demo mới trên C06 bắt đầu CASE_VERIFIED1 rồi pledge/reduction/receipt/delivery đúng sequence; state C03 không tự xuất hiện. Dũng phải hỗ trợ cả AID_PLEDGED/COMMITMENT_REDUCED cùng GOODS_RECEIVED/DELIVERY_RECORDED hoặc queue sẽ bị prefix pending chặn; không chỉ mở receipt/delivery. Giữ cùng orgRef/ví là khả thi sau register/authorization lại trên registry mới; giữ old proofs/address/ABI bất biến, không re-sign/broadcast signed bytes cũ sang address mới.

### Owner và điều kiện còn thiếu

- **Dũng, theo đề xuất mới chờ chốt:** B09 DELIVERED/outbox immutable snapshot; worker goods có đầy đủ prefix; verifier D55 theo operation; DB cutover inventory/routing/attempt proof và assertions. Không đánh implementation dự kiến là đã xong.
- **Khoa:** giữ gate contract/ABI, map refs/digest/receipt độc lập cho C10; chuẩn bị deploy/verify khi được giao/phê duyệt riêng. Không dùng admin key/deploy trong Task9. C10 full goods cần registry C06 và actual receipts, không chỉ local contract tests.
- **Khoa+Dũng:** thống nhất owner/slot scanner/log replay còn thiếu theo boundary đã đề xuất, không tự giao toàn phần mới cho Dũng; triển khai sau, không đưa scanner vào lượt này. Scanner là dependency của full C08/replay reconciliation, không phủ nhận bằng chứng receipt polling đã có.
- **Dũng+Minh/Khoa:** sanitized audit read projection và D49/T02 contract (C09/T01/T02), live statuses/explorer chỉ từ verified proof; chưa API E2E. Không coi dependency API là receipt verifier FAIL.

Không phát hiện regression mới cần sửa code trong scope C07/C08. Task9 hoàn thành review/readiness; C10 mới đủ phần thiết kế và slice CASE_VERIFIED có giới hạn bằng chứng DB, **chưa PASS goods integration**. Không chạy tests mới vì không có thay đổi worker hoặc nghi vấn regression; không sửa code/nhánh người khác, không push/PR/Actions/blockchain.
