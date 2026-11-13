# Task 7 — Review C08 receipt/log recovery

Phạm vi lịch 13/11/2026, review cập nhật ngày 14/11/2026 (Asia/Saigon). Reviewer: Khoa/Codex.

**Verdict: NEEDS FIX cho validation receipt proof; log replay PENDING.** Hai sửa đổi chain/context và bốn tests được bàn giao đã được xác nhận; không còn blocker “chưa push implementation” của lượt trước. Không đánh C08 Done hoặc PASS toàn bộ.

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

## Findings cần Dũng sửa

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

Hai finding là fail-closed validation với RPC proof bất thường, không khẳng định giao dịch Ethereum hợp lệ tự sinh index âm/status2. Chúng vẫn ảnh hưởng trực tiếp tiêu chí không nhận proof thiếu/sai của C08. Bốn tests mới hiện không bao phủ các input này.

## Log replay và boundary đề xuất — chưa triển khai

Adapter chỉ có getReceipt/canonicalBlockHash, chưa readLogs/readEventRecord; chưa bounded scanner, durable cursor/observation hoặc LogReplayIT. **PENDING**, không phải test FAIL và không phải lỗi của receipt polling đã bàn giao.

Đề xuất tối thiểu để Dũng/Khoa thống nhất trước triển khai (PROPOSED, không phải quyết định kiến trúc mới đã chốt):

1. `readLogs(fromBlock,toBlock,registry,topic0Set)` trả observation giữ nguyên chainId, address, txHash, blockNumber/hash, transactionIndex, logIndex, topics/data/removed. Có thể thêm `readEventRecord(eventId)` để cross-check operation/digest; readback không thay thế receipt/log proof.
2. Cursor riêng theo chain/registry/deployment, bắt đầu deployment block, range có cap cấu hình; xác minh chain/canonical block và receipt/context của transaction trước apply. Không biến log missing/mismatch thành confirmed, không fabricate index, không nuốt lỗi rồi tiến cursor vượt dữ liệu chưa xử lý an toàn.
3. Unique observation `(chainId,txHash,logIndex)` và mapping logical job theo deployment + operation/eventId. Observation/projection/cursor cập nhật nguyên tử; rollback rồi đọc lại phải sửa cùng record, không tạo job/sequence mới. Removed/noncanonical observations phải được reconcile, không ghi đè lịch sử bằng proof mới không đối chiếu.
4. C10/D55 VOID: một resolveDispute/job/eventId/sequence, **hai observations** DeliveryVoided → DisputeResolved. Đối chiếu cùng tx/registry/org/case/commitment/sequence, đúng fields/ref/resolution và thứ tự logIndex; thiếu/sai/duplicate bộ log không confirmed. Không áp quy tắc “mỗi eventId chỉ được một log” của CASE_VERIFIED lên VOID.
5. LogReplayIT sau này: replay cùng observations hai lần, crash/SQL rollback giữa chừng, thiếu/sai log, noncanonical, hai log VOID chỉ apply một logical operation. Live audit projection API và các milestone C06 chưa được nghiệm thu trong review CASE_VERIFIED này.

Khoa chốt receipt/log semantics và phối hợp boundary; Dũng tiếp nhận sửa Java, transaction/observation/cursor tests khi triển khai phần log ingest; Minh phối hợp API audit projection. Chưa thực hiện scanner hoặc mở task mới trong lượt review.

## Điều kiện chốt

Dũng sửa R1/R2 và bổ sung regression evidence để receipt recovery đạt PASS đầy đủ với input bất thường. Scanner/replay giữ PENDING riêng đến khi được triển khai/kiểm thử, không tự biến thành lỗi của Khoa hoặc giả PASS toàn C08. Không cần chạy lại C03/C04/C07/F07/F08 để xử lý hai finding này.
