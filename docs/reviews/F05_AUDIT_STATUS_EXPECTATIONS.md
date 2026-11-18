# F05 — Bảng trạng thái audit kỳ vọng cho timeline (12/11/2026)

## 0. Trạng thái hiện hành — Minh đối chiếu ngày 18/11/2026

Các mục 1–6 bên dưới giữ lịch sử chuẩn bị ngày 12–15/11. Các nhận định “API chưa có”, “chưa merge”, “chỉ fixture” và chính sách timeout ở phần lịch sử không còn là trạng thái triển khai hiện tại.

- T01 `/api/cases/{id}/audit-trail` và T02 **duy nhất** `/api/public/cases/{publicCaseRef}/audit` đã có trong backend chia sẻ qua PR #30, D49. Route campaign `/api/campaigns/{id}/audit-trail` ở mục 3 là URI lịch sử đã được thay thế; không tạo alias hoặc dùng lại ở FE. `/audit/{publicCaseRef}` là trang Next đọc T02, không phải endpoint API thứ hai.
- FE candidate PR #31 đọc T01/T02, phân trang và làm mới timeline. Chỉ bật liên kết chia sẻ sau khi đọc T02 thành công; khi đọc lại lỗi thì bỏ dữ liệu/link cũ và hiển thị lỗi/retry. Không fallback fixture trong real mode.
- `CONFIRMED_ON_CHAIN` do backend kiểm persisted proof/context. FE chỉ trình bày projection; hash một mình không đủ. Link explorer chỉ hiện khi status confirmed, chain 43113, hash/block/registry hợp lệ và URL Snowtrace testnet khớp hash. PENDING, FAILED, NOT_APPLICABLE và thiếu proof có cách hiển thị riêng; không thay business/review status.
- T01 giữ quyền staff đúng tổ chức. T02 anonymous dùng opaque `audit_...` IDs, nhãn vai trò; không actor identity, evidence, consent, payload private hoặc errorCode. Allowlist hiện có sáu loại: CASE_VERIFIED, CAMPAIGN_PUBLISHED, AID_PLEDGED, COMMITMENT_REDUCED, GOODS_RECEIVED, DELIVERY_RECORDED. Không gọi T02 là toàn bộ lịch sử D02/S01/S03; các bước nội bộ đọc T01.
- Worker local và receipt proof-context đã được Dũng/Khoa bàn giao riêng. Inventory Task #5 ngày 18/11 ghi C06 chưa deploy, staging có job PENDING, worker tắt; không chuyển kết quả local/Hardhat thành Goods Fuji PASS. Proof CASE_VERIFIED C03 lịch sử vẫn tách khỏi các job hàng hóa.
- T03 retry/operator và SQL statement counts không được coi đã nghiệm thu. Các capture API/chain thật phải theo checklist C09 của Khoa trên `origin/khoa` tại `5267e99`, file `C09_FUJI_PROOF_ACCEPTANCE_2026_11_17.md`; phần cutover theo [Task #5 của Dũng](C06_TASK5_DB_CUTOVER_2026_11_18.md).

Kết quả lượt kiểm thử hiện hành và giới hạn F09/F10 được ghi tại [bàn giao Minh ngày 18/11](../handoffs/MINH_F05_F09_F10_2026-11-18.md). F05 vẫn **In Review**, còn gate proof/pilot Fuji và review chéo.

Owner F05: Minh (F). Tài liệu này là phần hỗ trợ 1h của Dũng (B): đối chiếu `blockchainStatus`/tx hash mà API trả với bằng chứng C05 và lập bảng trạng thái kỳ vọng để Minh ghép timeline. Chỉ là tài liệu đối chiếu: không sửa backend, frontend hay contract. **F05 chưa Done** — cần Minh ghép UI và Khoa xác nhận chain status.

Nguồn đã đọc/grep: `API_SPECIFICATION.md` (DTO dictionary, T01–T03), `BUSINESS_WORKFLOWS.md`, `BLOCKCHAIN_ARCHITECTURE.md` (Java worker algorithm, Receipt verification), `docs/fixtures/f02/`, `VerificationController`, `VerificationService`, `LocalAuditService`, `ContributionMapper`, `BlockchainJob`, `CaseVerifiedWorker`, `CaseVerifiedReceiptVerifier`, migrations V5, V9.

Quy ước cột "Hỗ trợ": **Code** = đã có trong code đã commit; **Worker local** = có trong worker C05 trên máy Dũng, chưa commit, chưa chạy Fuji; **Spec** = chỉ có trong tài liệu; **GAP** = chưa có ở đâu.

## Quy tắc cứng

1. **Hash alone is not confirmed.** Có tx hash chỉ có nghĩa là giao dịch đã được gửi. Chỉ `CONFIRMED_ON_CHAIN` (job `CONFIRMED`, receipt status=1 và qua toàn bộ kiểm tra BR-10) mới được hiển thị "anchored".
2. Business verification (SQL `VERIFIED`) tách biệt với trạng thái chain. Revert, mismatch hay timeout **không** làm mất `VERIFIED`.
3. Wording trung thực: chain chứng minh *record integrity* (dấu vân tay của bản ghi), **không** chứng minh hàng hóa đã được giao thật.
4. Không đưa PII, manifest private, salt, evidence, consent, tên reviewer vào phần công khai.

## 1. Giá trị `blockchainStatus`

| Giá trị | Nguồn | Ghi chú |
|---|---|---|
| `PENDING_ON_CHAIN` | Code: `VerificationService` (verify), `ContributionService` | Snapshot lúc ra quyết định; replay trả lại đúng snapshot, không tự cập nhật |
| `NOT_APPLICABLE` | Code: `VerificationService` (reject, request-changes) | Không có job |
| `CONFIRMED_ON_CHAIN` | Code: `ContributionMapper.chainStatus()` (chỉ G02 commitments); fixture `goods-flow.types.ts` | Không endpoint case/verification nào trả giá trị này |
| `FAILED_ON_CHAIN` | Như trên | Như trên |
| mismatch riêng | Không có | Spec chỉ có `AuditEventResponse.blockchain.errorCode?` (đề xuất) |

## 2. `blockchain_jobs.status` → hiển thị

| Job status (V5) | Nghĩa | blockchainStatus (theo `ContributionMapper`) |
|---|---|---|
| `PENDING` | Chờ outbox hoặc chờ retry | `PENDING_ON_CHAIN` |
| `PROCESSING` | Worker đang lease job | `PENDING_ON_CHAIN` |
| `SUBMITTED` | Đã broadcast, có tx hash, chưa có receipt hợp lệ | `PENDING_ON_CHAIN` |
| `CONFIRMED` | Receipt status=1 + đúng contract/signer/event/org/case/eventId/data + block canonical | `CONFIRMED_ON_CHAIN` |
| `FAILED` | Revert (`TX_REVERTED`), receipt sai (`RECEIPT_*`), hoặc hết rebroadcast (`RECEIPT_NOT_FOUND`) | `FAILED_ON_CHAIN` |

`blockchain_transactions` (V9) lưu từng attempt: `PREPARED → SUBMITTED → CONFIRMED | REVERTED`. Tx hash nằm ở bảng này, không ở job.

## 3. Endpoint audit

| Endpoint / DTO | Trạng thái |
|---|---|
| `T01 GET /api/cases/{id}/audit-trail` | Code (`277c205`), xem `T01_T02_AUDIT_TRAIL_HANDOFF.md` |
| `T02 GET /api/campaigns/{id}/audit-trail` (public) | Code (commit T02), allowlist 5 event, không errorCode |
| `T03 POST /api/audit-events/{eventId}/retry` | Spec only |
| `AuditEventResponse.blockchain{status,chainId,contractAddress,txHash?,blockNumber?,explorerUrl?,errorCode?}` | Code: thêm `network`; `errorCode` chỉ T01; `businessStatus`/`evidenceReviewStatus` chưa có |
| `LocalAuditService` | Code, nhưng chỉ ghi (append/revision), không có read API |

Endpoint có thật trả `blockchainStatus`: `POST /api/cases/{id}/verify|reject|request-changes`, `POST /api/campaigns/{id}/commitments`, `GET /api/me/commitments`, `POST /api/commitments/{id}/cancellations`.

## 4. Bảng trạng thái kỳ vọng

| # | Tình huống | SQL nghiệp vụ | blockchain_jobs.status | blockchainStatus | txHash | Timeline nên hiển thị | Link explorer? | Câu chữ gợi ý | Hỗ trợ |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Vừa verify, chưa có tx | case `VERIFIED`, verification `APPROVED` | `PENDING` | `PENDING_ON_CHAIN` | Không | Verified + badge pending | Không | "Verified in AidBridge. On-chain record pending." | Code |
| 2 | Đã gửi tx, chưa có receipt | `VERIFIED` | `SUBMITTED` | `PENDING_ON_CHAIN` | Có (DB) | Vẫn pending, không confirmed | Chỉ khi có hash thật, nhãn rõ | "Transaction submitted, awaiting confirmation. Not yet confirmed." | Worker local; API GAP (không trả hash) |
| 3 | Receipt status=1, event khớp hoàn toàn | `VERIFIED` | `CONFIRMED` | `CONFIRMED_ON_CHAIN` | Có | Confirmed + block number + link | Có | "Record integrity anchored on Avalanche Fuji testnet. This does not prove goods were delivered." | Worker local; API GAP (case không có status sống) |
| 4 | Receipt status=0 (revert) | `VERIFIED` (giữ nguyên) | `FAILED` (`TX_REVERTED`) | `FAILED_ON_CHAIN` | Có | Quyết định nghiệp vụ còn hiệu lực; ghi chain thất bại | Có, nhãn "Reverted" | "On-chain recording failed. The verification decision in AidBridge is unchanged. Operator review required." | Worker local; API GAP |
| 5 | Receipt status=1 nhưng sai event/contract/commitment | `VERIFIED` | `FAILED` (`RECEIPT_*`) | `FAILED_ON_CHAIN` | Có | Không confirmed | Không cho public (explorer hiện "Success", gây hiểu nhầm) | "On-chain record could not be verified. Operator review required." | Worker local; API GAP (thiếu errorCode) |
| 6 | Timeout RPC | `VERIFIED` | Giữ `SUBMITTED` (hoãn poll) hoặc `PENDING` (chờ retry) | `PENDING_ON_CHAIN` | Có hoặc không | Vẫn pending, không chuyển failed | Như #1 hoặc #2 | "On-chain record pending." | Worker local; chỉ `FAILED` (`RECEIPT_NOT_FOUND`) sau 5 lần rebroadcast |
| 7 | Reject / request-changes | `REJECTED` / `CHANGES_REQUESTED` | Không có job | `NOT_APPLICABLE` | Không | Sự kiện nội bộ, không badge chain | Không | "Recorded in AidBridge. No blockchain record for this action." | Code |
| 8 | User không có quyền đọc phần private | — | — | Theo projection công khai | Chỉ hash công khai | Nhãn vai trò thay tên; ẩn reviewer, manifest, salt, evidence, consent | Theo #1–#6 | "Verified by an authorized reviewer." | Spec only (T01/T02) → GAP |

## 5. Đối chiếu bằng chứng thật

**Chưa có bằng chứng Fuji, bảng chỉ dùng được với fixture.** Prompt 2 (C05 compare) đang BLOCKED: chưa có job thật nào do worker gửi lên Fuji (chờ Khoa `updateOrganizationRelayer`, xem D54). Tx Fuji trong `C05_FUJI_RECEIPT_REVIEW.md` do Khoa gửi tay, không đi qua API/job nên không dùng để đối chiếu API. Mọi mục: `NOT_VERIFIED`. Không điền tx hash giả.

Quy tắc explorer link:
- Chỉ Fuji testnet, `chainId` 43113; định dạng `https://testnet.snowtrace.io/tx/0x` + 64 hex.
- Chỉ render khi API trả hash thật đúng định dạng; không tự ghép link từ fixture.
- Luôn kèm nhãn "testnet". Không hiện link ở tình huống #5.

## 6. GAP (chờ quyết định đưa vào backlog cho Dũng)

| ID | GAP | Mức |
|---|---|---|
| GAP-1 | ~~Chưa có T01/T02/T03~~ T01, T02 đã có (15/11, chưa merge); T03 retry chưa làm | Còn T03 |
| GAP-2 | ~~Không API nào trả txHash/blockNumber/explorerUrl~~ T01/T02 trả theo bảng ánh xạ của handoff | Đã xử lý (chưa merge) |
| GAP-3 | `DecisionResponse.blockchainStatus` vẫn là snapshot; trạng thái sống đọc qua T01/T02 | Đã xử lý qua T01/T02 (chưa merge) |
| GAP-4 | Không phân biệt revert và mismatch; cần `errorCode` công khai an toàn hoặc chốt public chỉ thấy `FAILED_ON_CHAIN` | Nên làm cùng GAP-1..3 |
| GAP-5 | ~~Chưa có cấu hình explorer base URL~~ `aidbridge.blockchain.explorer-tx-base-url` / `BLOCKCHAIN_EXPLORER_TX_BASE_URL` | Đã xử lý (chưa merge) |
| GAP-6 | Fixture F02 chỉ có `PENDING_ON_CHAIN`/`NOT_APPLICABLE`, không có confirmed/failed/txHash | Minh/Dũng bổ sung fixture |
| GAP-7 | Worker C05 chưa commit và chưa chạy Fuji; các dòng "Worker local" phụ thuộc mục này | Đang xử lý (Prompt 2, D54) |

## Change record

| Ngày | Thay đổi |
|---|---|
| 12/11/2026 | Tạo bảng (Prompt 3, phần hỗ trợ F05 của Dũng). Chưa có bằng chứng Fuji; F05 vẫn Not Started (owner Minh). |
| 15/11/2026 | T01/T02 có code và test (GAP-1 trừ T03, GAP-2, GAP-3, GAP-5). Public chốt chỉ `FAILED_ON_CHAIN`, không errorCode; mismatch ẩn hash (GAP-4 theo hướng này). Chưa gọi trên dữ liệu Fuji thật: NOT_VERIFIED. |
