# C08 — Bàn giao kiểm thử khôi phục của Dũng, 13/11/2026

**C08 In Progress.** Khoa phụ trách nghiệm thu và review quy tắc đối chiếu receipt/log. Dũng phụ trách kiểm tra Java, HTTP và dữ liệu PostgreSQL trong slot 2 giờ ngày 13/11. Giữ estimate C08 là 7 giờ. Giờ làm thực tế của Dũng chưa được ghi nhận.

## Phạm vi

- Worker `CASE_VERIFIED` đã poll theo hash của attempt `SUBMITTED`, lấy hash block canonical, kiểm receipt và ghi trạng thái job/attempt trong cùng SQL transaction. Khi receipt chưa rõ, worker giữ attempt và có thể gửi lại chính signed bytes đã lưu.
- Hai thay đổi nhỏ cần Khoa review. Worker kiểm lại `eth_chainId` lúc poll để trì hoãn nếu RPC đổi chain sau startup. Verifier kiểm destination và signer trước khi phân loại receipt status 0 thành `TX_REVERTED`. Nhờ đó, một receipt status 0 sai registry không làm attempt bị ghi `REVERTED`.
- API hiện trả `PENDING_ON_CHAIN` trong response của `POST /api/cases/{id}/verify`. Idempotent replay trả lại response đó. Chưa có kiểm chứng API đọc trạng thái `CASE_VERIFIED` trực tiếp từ job sau confirm. Test dùng response B05 và đọc SQL để quan sát hai lớp.
- Task 2 đã hoàn tất theo xác nhận của Dũng. Backlog C05/C07 ghi hai giao dịch Fuji, trong đó `0x97104a33…f98e` là D1 với cùng nonce 1, cùng hash và một attempt sau restart. Báo cáo B05 ghi receipt và đối chiếu C03. Đây là bằng chứng lần chạy trước. [Báo cáo B05](B05_REAL_FUJI_RUN_2026_11_13.md) · [Backlog](../planning/TASK_BACKLOG.md)

## Kết quả chạy

Lượt chạy ngày 14/11/2026 dùng snapshot của nhánh `dung` tại `f225385dda54869faeb92feb490d32f31b6ff4ab` cùng bốn file Java C08 trong working tree. Maven 3.9.16, JDK 26.0.1 và PostgreSQL 18 trên `127.0.0.1:55438`, database `aidbridge_test`. Fixture tạo schema riêng, Flyway áp dụng V1–V14. Bản sao test đổi cổng allowlist của `AbstractCaseHttpIT` sang 55438. Checkout dùng guard 5432. RPC fixture dùng chain 31337, địa chỉ registry fixture và test signer. `BLOCKCHAIN_ENABLED=false` tắt worker cấu hình thật. Lượt chạy này siết assertion IT03 và IT04 trong `CaseVerifiedWorkerIT`.

```powershell
mvn -o -B -ntp "-Dmaven.repo.local=C:\Users\Dell\.m2\repository" "-Dtest=CaseVerifiedWorkerIT,CaseVerifiedReceiptVerifierTest,Web3jBlockchainAdapterTest,VerificationApiIT" test
```

```text
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Kịch bản | Bộ chọn test trong lệnh Maven đã chạy | Kỳ vọng | Quan sát và bằng chứng | Kết quả |
| --- | --- | --- | --- | --- |
| IT03 · Timeout hoặc receipt tạm vắng | `CaseVerifiedWorkerIT#receiptTimeoutKeepsApprovalPendingAndLateProofConfirmsTheOriginalAttempt`, `#waitsWhileNotMinedThenRebroadcastsTheSamePersistedBytes` | Giữ pending tới khi có proof hợp lệ. Nếu gửi lại thì dùng đúng giao dịch đã lưu. | Approve và idempotent replay trả HTTP 200, `PENDING_ON_CHAIN`. RPC timeout dù fixture đã có receipt hợp lệ trả `PENDING / RPC_UNAVAILABLE`. Khi receipt vắng, poll trả `PENDING / RECEIPT_PENDING`. Job và attempt giữ `SUBMITTED` khi chưa có proof. Lần gửi lại trả `REBROADCAST`, hai lần gửi khớp bytes giải mã từ journal, giữ nguyên nonce và hash. Proof hợp lệ xác nhận job và attempt gốc. | Pass |
| IT13 · Receipt hoặc event không hợp lệ | `CaseVerifiedWorkerIT#rejectsReceiptWithoutMatchingProof`, `#defersOnWrongRpcChainAndNoncanonicalBlockBeforeConfirmingOriginalAttempt`, `CaseVerifiedReceiptVerifierTest` | Chỉ xác nhận receipt khớp chain, registry, block canonical, giao dịch và event dự kiến. | Receipt status 1 sai registry, thiếu event, sai `eventId` lần lượt cho job `FAILED / RECEIPT_WRONG_CONTRACT`, `FAILED / RECEIPT_EVENT_MISSING`, `FAILED / RECEIPT_EVENT_ID_MISMATCH`, attempt vẫn `SUBMITTED`. Status 0 đúng context cho job `FAILED / TX_REVERTED`, attempt `REVERTED`. Status 0 sai registry cho `FAILED / RECEIPT_WRONG_CONTRACT`, attempt vẫn `SUBMITTED`. Sai chain hoặc block chưa canonical lần lượt cho `PENDING / INVALID_CHAIN_ID`, `PENDING / BLOCK_NOT_CANONICAL`, job và attempt giữ `SUBMITTED` cho tới khi proof hợp lệ. | Pass |
| IT04 · Mất cập nhật SQL sau khi receipt hợp lệ | `CaseVerifiedWorkerIT#receiptPollingRepairsRolledBackConfirmationWithoutNewRecords`. `LogReplayIT` dự kiến. | Sửa projection gốc đúng một lần, giữ một decision, audit event, job và signed attempt. Khi có scanner, phát lại cùng observation hai lần và kiểm tra chỉ có một observation. | Constraint fixture gây SQLSTATE `23514`. Sau rollback, job và attempt vẫn `SUBMITTED`, projection xác nhận rỗng. Poll lại ghi receipt và xác nhận cùng `eventId`, attempt ID, hash và nonce. Poll tiếp rỗng, projection giữ nguyên, đếm được một decision, một audit event, một job, một attempt và một broadcast. Chưa có scanner, durable cursor hoặc đường lưu observation để đo số observation. | **Pass** cho receipt polling. **Not Run** cho log replay end-to-end. |

## Quy tắc Khoa cần review

- `BlockchainConfiguration` kiểm chain ở startup. `CaseVerifiedWorker` đối chiếu chain RPC lúc poll và chain/signer/registry của attempt. Verifier so receipt hash với hash đã journal, block hash với block canonical, destination với registry và sender với signer. Khi hash block chưa khớp, job tiếp tục pending.
- Với status 1, chỉ nhận đúng một log `CaseVerified` chưa bị removed, từ registry, có topic0 đúng ABI và tổng cộng 4 topics. So `orgRef`, `caseRef`, `eventId` với envelope DB. Data phải có 128 bytes, đúng `uint64 chainSequence`, `bytes32 evidenceCommitment`, `uint16 schemaVersion`, `uint64 timestamp`. Luồng này yêu cầu sequence 1 và dùng schema 1. Đối chiếu raw transaction/calldata, timestamp với block và contract state readback là các kiểm tra có trong proof độc lập C05. Poller hiện chỉ đọc receipt và canonical block hash.
- `BlockchainAdapter` chưa có `readLogs` hay `readEventRecord` như thiết kế. **Khoa** chốt boundary cho scanner theo deployment block/cursor, giới hạn khoảng block, định danh duy nhất `(chainId, txHash, logIndex)`, cách xử lý log lạ hoặc mất canonical và cách commit observation/projection/cursor. **Dũng** hỗ trợ DB transaction và `LogReplayIT` khi có đường ingest. Acceptance replay vẫn **Not Run**. [Blockchain Architecture](../architecture/BLOCKCHAIN_ARCHITECTURE.md#receipt-verification-indexing-và-reconciliation)
- **Khoa và Minh** xác nhận hợp đồng API audit để test trạng thái live. Phần API đọc projection sau khi worker confirm vẫn **Not Run**. [Test Strategy](../quality/TEST_STRATEGY.md)

Lượt 14/11 dùng `git archive`, `tar.exe`, `pg_ctl`, `psql` và `mvn` với cluster test đã có. Log local `backend/aidbridge-backend/target/c08-2026-11-14/maven-c08-task6.log` và XML Surefire trong `backend/aidbridge-backend/target/c08-2026-11-14/source/backend/aidbridge-backend/target/surefire-reports`. Lượt 13/11 tại `2bec453229880c3fbdf73cf49b8ea74eba040416` dùng V1–V13, đạt 40/40 sau khi chuẩn hóa kiểu JDBC của `receipt_status` và thêm ca rollback. Log lượt trước ở `backend/aidbridge-backend/target/c08-2026-11-13/maven-c08-final.log`.
