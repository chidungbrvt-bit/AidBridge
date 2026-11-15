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

## Task10 ngày15/11/2026 — hợp đồng thiết kế scanner/cursor

**DONE WITH PENDING DECISIONS cho tài liệu thiết kế; scanner/replay vẫn NOT IMPLEMENTED.** Baseline main9c24630/dung0fc3e3d/minh0595149 sau fetch, khoa giữ a4752a1/2a0a58e/7ac567b. BlockchainAdapter hiện chỉ readChainId/nonce/sign/broadcast/getReceipt/canonicalBlockHash, chưa readLogs/readEventRecord hoặc head/finality API. Job có chainId/sequence/logical_event_id nhưng chưa pin deployment trước ký; attempt có chainId/contractAddress/hash/signer/nonce/receipt_private. Không suy schema hiện có đã đủ cho scanner.

Các invariants chain/context/privacy/idempotency dưới đây kế thừa D49/D53/D55 và architecture. **API bổ sung, data model, finality/completeness policy và defaults là PROPOSED cho Dũng/Khoa xác nhận trước triển khai**, không tạo migration hoặc Decision được duyệt thay nhóm. Dũng triển khai scanner Java/SQL/replay theo mục tiêu bàn giao của Task10 sau khi chốt capacity; Khoa review semantics/C10; Minh tiêu thụ sanitized API và [checklist F10](F10_HOSTING_AUDIT_CHECKLIST_2026_11_15.md). Phân công worker GOODS_* ở D56 không tự nghiệm thu scanner.

### 1. Hợp đồng read-only, không có đường ký/gửi

- DeploymentDescriptor bất biến: deploymentId, chainId, registryAddress chuẩn hóa, deploymentBlock+blockHash, reviewed source/ABI/runtime fingerprint, allowed event topic0→operation/version, finalityPolicyVersion. C03/C06 có descriptors/cursors riêng; không dùng global BLOCKCHAIN_REGISTRY_ADDRESS để relabel history. Registry mới chưa deploy thì không tạo descriptor giả hoặc scan address dự đoán.
- Adapter đề xuất: `readHead(policy)`, `readBlock(number)` (hash,parentHash,transactionHashes), `readLogs(deployment,fromInclusive,toInclusive,topic0Set)`, `getReceipt(txHash)`, `readEventRecord(deployment,eventId,atBlock)`. readEventRecord là cross-check operation/digest, không thay receipt/log proof; đọc tại block canonical đã xác định, không mặc định latest. ChainId kiểm đầu mỗi batch và trước apply; sai chain/provider không đáp ứng read bắt buộc thì pending/attention, không cursor advance.
- RawLog giữ nguyên chain/registry, blockNumber/hash, txHash, transactionIndex, logIndex, topics/data/removed. Không default index0, không âm/overflow, không tự sort sai để che order, không làm mất raw fields khi decode. Query filter address/topic đúng ABI của từng deployment; range inclusive, bắt đầu deploymentBlock (bao gồm logs trong block deploy), không trộn events C06 vào ABI C03.

### 2. Safe range, finality và missing-log completeness

- `nextBlock` = block đầu tiên chưa commit; cursor mới khởi tạo deploymentBlock. Batch `[nextBlock,min(safeHead,nextBlock+batchBlocks−1)]`. Không range khi safeHead<nextBlock. Batch default500 blocks theo architecture, cho phép giảm; cap logs/receipts/transactions và timeout tổng mỗi batch phải hữu hạn. **PROPOSED:** overlap10 blocks trong miền đã qua safeHead để revalidate, không rewind qua deploymentBlock. Dũng xác nhận sizing/config, không hardcode assumption vào verifier.
- Không chốt N confirmations như finality Fuji. `safeHead` chỉ từ policy accepted/executed/finalized đã được xác minh với provider thực tế và ghi evidence/version; block canonical phải kiểm lại trước SQL commit. Nếu provider không hỗ trợ semantics required thì strict promotion giữ pending/attention. Một depth cấu hình chỉ là lag an toàn **PROPOSED**, không được gọi finalized; chọn depth/provider/fallback phải chốt trước pilot, không tự dùng latest hoặc depth0 để PASS. Không hồi tố coi receipts C03 trước đây đã được scanner finality policy mới kiểm chứng.
- `eth_getLogs` trả rỗng/success **không đủ chứng minh không bỏ sót** khi provider có thể truncate/trả thiếu. PROPOSED strict completeness: lấy block với danh sách tx hashes cho mọi block trong range và đủ receipts tương ứng (hoặc block-receipts API nếu provider hỗ trợ đã kiểm chứng), đối chiếu txHash/blockHash/transactionIndex/status/log identity; lọc receipt logs đúng registry/topics và so tập với getLogs. Range quá lớn chia đôi hoặc giảm batch, không nuốt lỗi/advance. Nếu cap đạt giữa block, xử lý block đó bằng staged bounded pages nhưng chỉ commit coverage khi **toàn block** hoàn tất; chưa có cơ chế này thì attention, không cắt logs rồi coi đủ.
- Receipt nào thiếu/timeout hoặc getLogs thiếu một log so receipt enumeration: retry cùng range, giữ cursor. Duplicate byte-identical observations được dedupe; duplicate cùng identity nhưng khác payload là discrepancy. Không thể chứng minh completeness với malicious RPC nhất quán giả mọi response; trust provider và mức cross-provider sampling là giới hạn phải công bố, không claim cryptographic completeness. Khoa/Dũng phải chốt strict receipt enumeration hay trust policy thay thế có ghi rõ giới hạn trước implementation; chưa có evidence cho provider thực tế.

### 3. Persistence/restart/reorg — mô hình đề xuất, chưa schema

- Cursor key `(deploymentId,scannerVersion/filterVersion)` giữ chain/registry binding, nextBlock,lastCoveredBlockHash,policyVersion,lastSuccessfulAt,version/lease. Thay filter/ABI version phải có replay/backfill cursor rõ ràng, không chạy filter mới trên cursor cũ rồi bỏ event type mới. Deployment identity không chỉ là case/orgRef có thể tái dùng.
- Observation identity `(chainId,registryAddress,txHash,logIndex)`; DB unique key giữ identity này. Lưu blockNumber/hash,transactionIndex,topics/data,eventId/operation decode,firstSeen/lastValidatedAt,canonical state và proof status. Giữ append-only **occurrence/history theo blockHash** để reorg cùng tuple không ghi đè proof cũ; cùng tx remine với logIndex khác tạo observation mới và vô hiệu canonical occurrence cũ, không logical job mới. Derived canonical flag/projection có lịch sử thay đổi, không xóa evidence.
- RPC/network ngoài SQL transaction. Claim lease/CAS cursor version trong short transaction; đọc/verify batch ngoài DB; trước commit recheck canonical anchors rồi dùng cursor version/lease fence để ghi observations+coverage+cursor và verified sync projection (nếu đủ proof) **nguyên tử**. Worker/scanner cùng lock/CAS trên job/attempt khi confirm; chỉ một transition idempotent, không tăng retry/sequence/count hai lần. SQL rollback hoặc process crash thì cursor cũ, đọc lại range an toàn. Không giữ SQL transaction chờ RPC.
- Cursor chỉ advance khi mọi block coverage/log trong batch đã được xử lý thành công; empty range cũng cần coverage đã kiểm. Chưa đủ proof, unknown event/expected job hoặc mismatch thì dừng batch và lưu diagnostic riêng không advance; không tạo business job từ log lạ. Có thể thiết kế quarantine+durable reconcile queue cho phép advance sau này nhưng phải có decision/test riêng, không mặc định skip lỗi.
- Mỗi vòng recheck last covered anchor và overlap anchors; parent/hash mismatch hoặc removed log ⇒ dừng promote CONFIRMED, đánh dấu discrepancy và reconcile về common ancestor. Reorg ngoài overlap không được coi đã phát hiện hết: cần checkpoint validation/verified proof revalidation trước phục vụ confirmed, và operator deep-rescan workflow. Nếu không xác định common ancestor trong history/cap thì attention, không reset im lặng về0 hoặc bỏ proof.
- Rewind cursor theo deployment trong transaction có audit; giữ orphan occurrences, invalidate derived sync proof và reconciliation state, **không rollback nghiệp vụ/quantities/audit event hoặc sửa signed transaction**. Nếu job/attempt đã CONFIRMED theo proof mất canonical, không tự giả transition domain hiện tại hỗ trợ: cần reconcile state/projection bổ sung được review; API không tiếp tục quảng cáo verified proof đó. Không tự đánh REVERTED cho missing/reorg. Snapshot canonical có thể đổi ngay sau commit: lưu observedAt/policy/blockHash và revalidation, không hứa finality tuyệt đối từ hash check đơn lẻ.

### 4. Worker / scanner / verifier C10 và D55

| Thành phần | Trách nhiệm | Không được làm |
|---|---|---|
| Worker | Immutable preparation, signer/nonce lock, journal signed bytes/hash trước broadcast; receipt polling C07/C08 | Không nhảy sequence, tạo signed attempt mới khi outcome chưa rõ hoặc gán registry mới cho job cũ |
| Scanner | Read-only coverage, durable observations/cursor, canonical tracking; gọi verifier để reconcile eligible job | Không ký/gửi, tạo delivery/job/attempt/sequence hoặc cộng received/delivered/confirmed quantities |
| Verifier C10 | Resolve DB immutable expected từ audit→job→attempt/deployment, đối chiếu exact receipt/log/operation/digest/refs/sequence; authorize repair sync projection | Không lấy log/calldata làm expected duy nhất, suy DB PENDING là event tồn tại, coi receipt status1 là đủ |

Repair dùng existing job/attempt ID nếu hash/signer/nonce/deployment được chứng minh khớp và signed journal còn hợp lệ. DB mất attempt hoặc không còn expected snapshot: giữ observation unmatched/attention, không tự dựng attempt/nonce/business event để tạo vẻ E2E PASS. AID_PLEDGED→AidCommitted; GOODS_RECEIVED→GoodsReceived; DELIVERY_RECORDED→DeliveryRecorded không được map bằng wildcard tên. Evidence private/version/salt giữ server-side, DTO public không có raw manifest/PII.

D55 VOID cần **hai observations khác logIndex** trong cùng receipt/tx/registry/org/case/eventId/sequence: DeliveryVoided rồi DisputeResolved; kiểm đúng delivery/dispute refs, resolution và commitment/schema theo ABI, không đòi index liền nhau nếu có log khác xen vào. Unique eventId trên job không phải unique eventId trên raw log. Một expected operation `resolveDispute` và một sequence; chỉ confirm khi đủ bộ logs canonical. Missing/wrong/reversed/duplicate bộ log ⇒ discrepancy, không double job hoặc double apply. C03 scan chỉ whitelist events thực có trong runtime/ABI C03.

### 5. Acceptance/tests ĐỀ XUẤT — tất cả NOT RUN

| Case tối thiểu | Expected assertions |
|---|---|
| Crash trước/sau commit hoặc lease mất trong scan | Observations+cursor+projection cùng rollback/commit; restart đúng range, stale scanner CAS fail; counts/job/attempt/sequence không tăng |
| Timeout/RPC unavailable, receipt missing, getLogs rỗng/truncated | Cursor không bỏ qua block; pending/attention; retry range cũ; completeness không dựa array length hoặc response200 |
| Duplicate logs/range too large | Duplicate chính xác một observation; xung đột payload bị chặn; split range không bỏ ranh giới block hoặc nửa operation VOID |
| Reorg/removed/remine/logIndex thay đổi, deep reorg | Lưu occurrence cũ, không publish proof orphan, rewind/canonical reconcile đúng deployment; business quantities/audit không đổi |
| Wrong chain/registry/signer/hash/topic/refs/schema/sequence/index/status | Không CONFIRMED; không fabricated index/status; mismatch có diagnostics, cursor không skip |
| Replay nhiều lần + worker/scanner cùng confirm | Một logical sync transition, cùng attempt/hash/nonce; không thêm receipt/attempt/job/chain_sequence/quantity |
| D55 VOID đủ/thiếu/đảo log | Hai observations/one operation; chỉ đủ valid pair mới eligible confirmed; cùng eventId không bị dedupe mất log thứ2 |
| C03 và C06 đồng thời/cutover | Cursor/ABI/job binding riêng, không gán goods C06 cho C03; fresh case C06 seq1; queue C03/signed bytes không đổi context |
| Frontend/API thiếu proof, mismatch/reorg/scanner lag | Unknown/pending/unverified rõ ràng; không suy confirmed từ hash hoặc business VERIFIED; link chỉ hợp lệ theo availability/privacy rules |

Điều kiện nghiệm thu implementation: Dũng chốt policy/API/schema/lease bounds và viết tests DB thật/adapter fixtures; Khoa review invariants+actual provider/finality/receipt evidence, Minh kiểm API/UI và hosting checklist khi đủ prerequisites. Không gọi bảng tests này PASS. Chưa phê duyệt capacity/deploy/public hosting, không viết scanner/migration/verifier hoặc F10 trong Task10.
