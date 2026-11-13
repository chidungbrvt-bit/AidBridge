# C08 — Bàn giao kiểm thử khôi phục của Dũng, 13/11/2026

**C08 In Progress.** Khoa phụ trách nghiệm thu và review quy tắc đối chiếu receipt/log. Dũng phụ trách kiểm tra Java, HTTP và dữ liệu PostgreSQL trong slot 2 giờ ngày 13/11. Giữ estimate C08 là 7 giờ. Giờ làm thực tế của Dũng chưa được ghi nhận.

## Phạm vi

- Worker `CASE_VERIFIED` đã poll theo hash của attempt `SUBMITTED`, lấy hash block canonical, kiểm receipt và ghi trạng thái job/attempt trong cùng SQL transaction. Khi receipt chưa rõ, worker giữ attempt và có thể gửi lại chính signed bytes đã lưu.
- Hai thay đổi nhỏ cần Khoa review. Worker kiểm lại `eth_chainId` lúc poll để trì hoãn nếu RPC đổi chain sau startup. Verifier kiểm destination và signer trước khi phân loại receipt status 0 thành `TX_REVERTED`. Nhờ đó, một receipt status 0 sai registry không làm attempt bị ghi `REVERTED`.
- API hiện trả `PENDING_ON_CHAIN` trong response của `POST /api/cases/{id}/verify`. Idempotent replay trả lại response đó. Chưa có kiểm chứng API đọc trạng thái `CASE_VERIFIED` trực tiếp từ job sau confirm. Test dùng response B05 và đọc SQL để quan sát hai lớp.
- Task 2 đã hoàn tất theo xác nhận của Dũng. Backlog C05/C07 ghi hai giao dịch Fuji, trong đó `0x97104a33…f98e` là D1 với cùng nonce 1, cùng hash và một attempt sau restart. Báo cáo B05 ghi receipt và đối chiếu C03. Đây là bằng chứng lần chạy trước. [Báo cáo B05](B05_REAL_FUJI_RUN_2026_11_13.md) · [Backlog](../planning/TASK_BACKLOG.md)

## Kết quả chạy

Maven 3.9.16, JDK 26.0.1, PostgreSQL 18 trên `127.0.0.1:55438`, database `aidbridge_test`, schema riêng do fixture tạo. Flyway áp dụng V1–V13. Chạy trên snapshot `2bec453229880c3fbdf73cf49b8ea74eba040416` có bốn file C08 hiện tại. Bản sao test đổi cổng allowlist của `AbstractCaseHttpIT` sang 55438. Main checkout vẫn dùng guard 5432. RPC fixture dùng chain 31337, địa chỉ registry fixture và test signer. Worker cấu hình thật tắt qua `BLOCKCHAIN_ENABLED=false`.

```powershell
mvn -o -B -ntp "-Dmaven.repo.local=C:\Users\Dell\.m2\repository" "-Dtest=CaseVerifiedWorkerIT,CaseVerifiedReceiptVerifierTest,Web3jBlockchainAdapterTest,VerificationApiIT" test
```

```text
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Kịch bản | Test trong lệnh trên | Kỳ vọng | Kết quả quan sát từ assertion | Kết quả |
| --- | --- | --- | --- | --- |
| Timeout rồi nhận receipt hợp lệ | `CaseVerifiedWorkerIT#receiptTimeoutKeepsApprovalPendingAndLateProofConfirmsTheOriginalAttempt` | API vẫn pending, không báo confirmed sớm. Receipt hợp lệ xác nhận attempt gốc. | HTTP 200, `PENDING_ON_CHAIN` cả lúc approve và replay. Lỗi RPC cho `PENDING / RPC_UNAVAILABLE`, job và attempt vẫn `SUBMITTED`, `receipt_status` và `confirmed_at` null. Proof hợp lệ đưa cả hai sang `CONFIRMED`, cùng attempt ID, hash, nonce, một broadcast, một decision và một event/job. | Pass |
| Receipt tạm vắng, đến hạn gửi lại | `CaseVerifiedWorkerIT#waitsWhileNotMinedThenRebroadcastsTheSamePersistedBytes`, `#exhaustedRecoveryBudgetKeepsOriginalAttemptAndAcceptsLateReceiptAfterRestart`, `#repeatedRpcOutageAndDuplicateBroadcastNeverTurnUnknownOutcomeIntoFailure` | Pending khi chưa có proof, retry cùng transaction. | `PENDING / RECEIPT_PENDING`, sau đó `REBROADCAST` cùng bytes, nonce và hash, một attempt. Hết budget vẫn `SUBMITTED` với `RECEIPT_PENDING_OPERATOR_ATTENTION`. Receipt đến muộn xác nhận job gốc. | Pass |
| Receipt status 1 sai registry | `CaseVerifiedWorkerIT#rejectsReceiptWithoutMatchingProof` | Không xác nhận. | `FAILED / RECEIPT_WRONG_CONTRACT`, attempt giữ `SUBMITTED`, `confirmed_at` null. | Pass |
| Receipt status 1 thiếu event hoặc sai eventId | `CaseVerifiedWorkerIT#rejectsReceiptWithoutMatchingProof`, `#aMismatchedLogFailsTheJobAndIsNeverConfirmed` | Không xác nhận. | Job `FAILED`, attempt giữ `SUBMITTED`, `confirmed_at` null. Mã lần lượt là `RECEIPT_EVENT_MISSING` và `RECEIPT_EVENT_ID_MISMATCH`. | Pass |
| Receipt revert đúng context / revert sai registry | `CaseVerifiedWorkerIT#rejectsReceiptWithoutMatchingProof` | Chỉ ghi revert cho attempt đúng context. | Revert đúng context cho `FAILED / TX_REVERTED`, attempt `REVERTED`, receipt status 0. Sai registry cho `FAILED / RECEIPT_WRONG_CONTRACT`, attempt giữ `SUBMITTED`. | Pass |
| RPC sai chain / block mất canonical | `CaseVerifiedWorkerIT#defersOnWrongRpcChainAndNoncanonicalBlockBeforeConfirmingOriginalAttempt` | Chờ nguồn hợp lệ, giữ attempt. | `PENDING / INVALID_CHAIN_ID`, rồi `PENDING / BLOCK_NOT_CANONICAL`. Job/attempt giữ `SUBMITTED`; sau khi khôi phục context, attempt gốc `CONFIRMED` với một broadcast. | Pass |
| SQL rollback sau receipt hợp lệ, phục hồi bằng poll theo hash | `CaseVerifiedWorkerIT#receiptPollingRepairsRolledBackConfirmationWithoutNewRecords` | Một lần poll sau khi lease hết hạn sửa trạng thái gốc. Poll tiếp không ghi lại. | Constraint fixture gây SQLSTATE `23514`. Job/attempt giữ `SUBMITTED`. Poll lại xác nhận cùng attempt ID, hash, nonce. Poll kế tiếp rỗng, dữ liệu giữ nguyên, một decision, event, job và attempt, một broadcast. | Pass |
| Phát lại cùng log hai lần qua scanner và cursor | `LogReplayIT` dự kiến | Cùng observation sửa projection một lần. Cursor và observation nhất quán sau rollback. | Thiếu đường đọc logs trong adapter, scanner bounded, durable cursor và kho observation. Chưa có đường ingest để phát lại cùng log. | Not Run |

## Quy tắc Khoa cần review

- `BlockchainConfiguration` kiểm chain ở startup. `CaseVerifiedWorker` đối chiếu chain RPC lúc poll và chain/signer/registry của attempt. Verifier so receipt hash với hash đã journal, block hash với block canonical, destination với registry và sender với signer. Khi hash block chưa khớp, job tiếp tục pending.
- Với status 1, chỉ nhận đúng một log `CaseVerified` chưa bị removed, từ registry, có topic0 đúng ABI và tổng cộng 4 topics. So `orgRef`, `caseRef`, `eventId` với envelope DB. Data phải có 128 bytes, đúng `uint64 chainSequence`, `bytes32 evidenceCommitment`, `uint16 schemaVersion`, `uint64 timestamp`. Luồng này yêu cầu sequence 1 và dùng schema 1. Đối chiếu raw transaction/calldata, timestamp với block và contract state readback là các kiểm tra có trong proof độc lập C05. Poller hiện chỉ đọc receipt và canonical block hash.
- `BlockchainAdapter` chưa có `readLogs` hay `readEventRecord` như thiết kế. **Khoa** chốt boundary cho scanner theo deployment block/cursor, giới hạn khoảng block, định danh duy nhất `(chainId, txHash, logIndex)`, cách xử lý log lạ hoặc mất canonical và cách commit observation/projection/cursor. **Dũng** hỗ trợ DB transaction và `LogReplayIT` khi có đường ingest. Acceptance replay vẫn **Not Run**. [Blockchain Architecture](../architecture/BLOCKCHAIN_ARCHITECTURE.md#receipt-verification-indexing-và-reconciliation)
- **Khoa và Minh** xác nhận hợp đồng API audit để test trạng thái live. Phần API đọc projection sau khi worker confirm vẫn **Not Run**. [Test Strategy](../quality/TEST_STRATEGY.md)

Lệnh chuẩn bị đã chạy gồm `git archive`, `tar.exe`, `initdb`, `pg_ctl`, `createdb` và `psql`. Lượt Maven đầu chạy 39 test với 2 assertion thất bại do kiểu JDBC của `receipt_status`. Lượt cuối chuẩn hóa kiểu trả về của truy vấn test và thêm ca rollback, kết quả 40/40. Log local `target/c08-2026-11-13/maven-c08-final.log` và XML Surefire trong snapshot. Không có giao dịch Fuji hoặc deploy mới trong lượt C08.
