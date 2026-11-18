# Khoa — INTERIM REVIEW Task3/4/5/8, 16/11/2026

**PASS WITH NOTES cho chất lượng review/preparation của Khoa.** Không có Critical/High hoặc finding code mới trong phạm vi đối chiếu. **Task4 F1/F2 vẫn NEEDS FIX**, không biến interim PASS thành C10/runtime/deploy PASS. Chưa thực hiện review Task6/7 hoặc final audit toàn ngày.

## Baseline / phương pháp

Fetch main15bcba4, dungc288f44; khoa3325184 trước interim. Remote không có thay đổi mới so với bốn task. Đã đọc toàn bộ GOODS_RECEIVED_REVIEW, C10_FULL_CHAIN_FIXTURE_REVIEW, ONE_CAMPAIGN_PER_CASE_REVIEW và C06_REDEPLOY_PREPARATION ngày16/11, đối chiếu source/schema/spec/fixture/Decision Log liên quan và backlog. Kiểm tra nhẹ bằng đọc file/hash/JSON và asserts trên source remote; không chạy compile hoặc Java/JUnit/DB/CI/Hardhat E2E, không RPC/sign/deploy.

| Task | Kết luận giữ nguyên | Bằng chứng đã đối chiếu lại |
|---|---|---|
| #3 GOODS_RECEIVED | PASS WITH NOTES | ReceiptOutbox payload/ref; GoodsReceiptService case lock/transaction/scope; payloadVersion1/job schema1; MAX+1; mapping recordGoodsReceipt/GoodsReceived. Nhận nhiều lần có refs riêng, replay không enqueue lại. Không có worker goods/proof runtime để nghiệm thu. |
| #4 C10 design/fixture | NEEDS FIX F1/F2 | JSON chỉ verify/pledge/receipt/delivery; một contribution/receipt; design dòng29,36 còn loại/block reduction, matrix/harness chỉ bốn jobs. Có2items nhưng chưa phải multi-commitment/multi-receipt full-chain coverage. Dũng chưa push bản sửa theo refs đã fetch. |
| #5 cardinality | PROPOSED — PENDING APPROVAL D15; implementation invariant đã enforce | V3 aid_case_id NOT NULL UNIQUE, V10/V15 không bỏ; JPA OneToOne, create case lock + exists guard; CampaignApiIT duplicate assertion. Optional publicCaseRef query phù hợp 0..1campaign/case, không cần migration mới. |
| #8 preparation | DONE WITH GATES | Artifact/source SHA hiện còn khớp runbook, full committed ABI khớp artifact. C03 manifest registry/runtime khớp bảng lịch sử; unsigned constructor encoding không phải deploy. Runner hiện hữu pin C03, không sẵn sàng chạy C06. |

## Cross-task consistency

- **Chain:** Task3 và Task4 thống nhất chuỗi CASE_VERIFIED → AID_PLEDGED → COMMITMENT_REDUCED → GOODS_RECEIVED → DELIVERY_RECORDED, sequence theo logical operation của case. Một campaign/case không giới hạn số pledges/receipts; vector hai commitments của Task4 không mâu thuẫn Task5. Các seq1–5/1–8 trong ví dụ không phải hằng worker.
- **Refs/privacy:** commitmentRef theo contribution; receiptRef theo receipt; deliveryRef theo delivery; không gán ref riêng cho mỗi item. caseRef lấy public_ref của case, không dùng campaign UUID hoặc logical eventId thay thế. R01 không ghi evidence file riêng: Task7 phải chốt manifest snapshot phù hợp, không dùng current case evidence hay placeholder fixture.
- **Audit/cardinality:** D49 URI publicCaseRef và repository Optional dựa trên case public_ref UNIQUE + campaigns.aid_case_id UNIQUE. Giữ phân biệt D15 PROPOSED (approval) với ràng buộc kỹ thuật đã chạy trong source. Không có lý do nới constraint cho Task7.
- **ABI/proof:** full C06 đã có reduction và D55; F1 là thiếu loader/allowlist/fixture acceptance, không thiếu Solidity function. D55 VOID hai logs cùng eventId/sequence vẫn một job; không áp quy tắc một log receipt cho VOID. Task6/7 cần writer/proof context + verifier; SQL guard không được nới. Chưa đánh giá bản sửa Task6 trong interim.
- **Deployments:** C03 historic proof/attempt/registry giữ nguyên. C06 mới cần registry/roles/state mới; same orgRef không chuyển rights/sequence. Pin predecessor và signed attempts đúng chain/registry, drain/reconcile signer trước cutover. Compile và same artifact hash không phê duyệt chuyển backend/global config.

## Sửa tài liệu rõ ràng trong interim

1. Task4 artifact note: phân biệt mismatch lịch sử với kết quả rebuild full C06 đã giải quyết ở Task8; link runbook, không xóa evidence cũ/khẳng định deployed.
2. Backlog C06: giải thích ghi chú review chưa chạy11–12/11 là lịch sử, review C06/D55 đã có; không đổi Java/VOID/Fuji NOT RUN.
3. Backlog C10: ghi trạng thái design/review hiện hành và F1/F2 OPEN, baseline Not Started là lịch sử. Không đổi owner/capacity hoặc đánh Done; không mở rộng dọn backlog C09/B09 ngoài bốn task.

Các notes Task3, Task5 và Task8 đã phân biệt source review, reports Dũng và actual checks nên không cần sửa. D15 vẫn PROPOSED. Không sửa code/fixtures/scripts/config hoặc docs thuộc nhánh Dũng/Minh.

## Gates còn lại / owner trước final audit

- **Dũng Task6:** bàn giao proof-context writer + regression trên SHA cụ thể; Khoa review khi được giao. Không tự gọi thay đổi chưa có là PASS, không nới SQL guard.
- **Dũng Task7:** xử lý F1/F2; chốt canonical manifest/vector với Khoa; triển khai full operation claim/poll/encoder/verifier gồm reduction; cung cấp Java parity, PostgreSQL retry/restart/fencing, Hardhat full-chain với proof và no-duplicate/stock invariants. Không dùng tests B09/B10 hoặc ABI offline thay bằng chứng này.
- **Nhóm/người dùng:** formal approval D15 nếu chốt decision; không cần migration để giữ hành vi A hiện tại. Deployment và cutover là hai approval riêng, chưa cấp.
- **Khoa trước deploy:** freeze candidate artifact + chuẩn bị/review runner/journal C06 riêng; local full-chain và các gates phải đạt trước đề nghị deploy. Task8 đã hoàn thành chuẩn bị checklist/offline evidence, không coi runner chưa triển khai là đã sẵn sàng broadcast.
- **Dũng vận hành:** DB demo/schema history, C03 pending/processing/submitted + prepared/signed attempts, nonce obligations và pinning cần evidence hiện hành trước switch. Roles/gas/nonce số liệu cũ chỉ là lịch sử, cần preflight tại bước được phép. Không audit DB/wallet trong interim.
- Sau bàn giao: review focused phần mới cùng commit/tests thực tế; tổng hợp final audit đúng snapshot. Nếu thiếu gates thì báo OPEN/NOT RUN và owner, không suy goods Fuji PASS. Scanner/Fuji chưa triển khai không phải yêu cầu mở thêm code ở lượt này.

Runbook C06 hiện đủ phân biệt deploy/setup/cutover, queue/no-go, unknown outcome recovery và rollback không đảo tx đã mine. C03 public manifest/snapshot/journals không thay đổi. Mismatch artifact cũ đã khép ở cấp offline consistency; các gate worker/freeze/approval vẫn OPEN. **Không cần sửa kỹ thuật ngoài tài liệu trong interim này.**
