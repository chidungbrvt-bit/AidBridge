# Bàn giao Khoa — 14/11/2026

**READY WITH KNOWN LIMITATIONS.** Ba task hoàn thành trong phạm vi review/frontend integration; không nghiệm thu backend/DB E2E, goods Fuji hoặc scanner chưa triển khai. Nhánh khoa giữ nguyên7ee46b5/736f5f7/2713675/e31521f; PR22 đã MERGED tại5122281, không đưa lại nội dung ngày13 vào diff mới.

| Task | Kết quả | Bằng chứng |
|---|---|---|
| #3 B08/C08 review | PASS WITH NOTES | [B08 mapping review](B08_GOODS_CHAIN_REVIEW_2026_11_14.md): payload/refs/sequence/idempotency phù hợp; GOODS_RECEIVED→recordGoodsReceipt→GoodsReceived, pledge event là AidCommitted. Hai guards C08 và R1/R2 giữ nguyên; không thay backend. |
| #4 R01 B08 API integration | PASS WITH LIMITATIONS | [F08 evidence](F08_MOCK_GOODS_WORKSPACE.md): real route `/cases/{id}/receipts`, GET `/api/org/cases/{id}`, POST `/api/commitments/{contributionId}/receipts`; shared session/CSRF/idempotency, backend IDs, partial/multi-item/multiple receipt, authoritative refetch. D01/F09 mock độc lập không đổi. |
| #9 C07/C08/C10 readiness | PASS WITH NOTES | [C07](C07_WORKER_SAFETY_REVIEW.md), [C08/C10](C08_RECEIPT_LOG_REVIEW.md): signer/nonce/persistence/fencing PASS; receipt recovery PASS; scanner/log replay PENDING; C10 goods chưa đủ prerequisites/Fuji receipts. |

## Final QA và bằng chứng

- **Finding Medium đã sửa:** sau outcome504 chưa rõ, retry403/409 không chứng minh request ban đầu chưa commit. Trước đây journal xóa key, có nguy cơ nhận trùng. Bản sửa giữ body/key và uncertain xuyên các lần retry đến success, khóa draft, không tự tạo request mới. Hai regression tests504→403/409→success kiểm một logical receipt/cùng key. First-request rejection400/401/403/404/409 vẫn không nhận là success; timeout/408/5xx giữ nguyên request.
- **Chạy lại PASS:**38 unit tests (14 R01 mapping/validation/client/retry +24 F08/F09-boundary/F07-adapter regression), lint integration/client/session/case-link/tests, typecheck `--noEmit --incremental false -p frontend`. Không chạy toàn bộ QA backend/contract.
- **Browser HTTP-fixture:** suite4 tests real-mode Next/shared client + Edge: partial/multiple receipts/refetch, committed504 retry, POST201+GET503 giữ receipt rồi retry GET không POST lại,409 không tăng quantities. Final rerun sau sửa: **4/4 PASS**; không coi đây là PostgreSQL E2E.
- **Evidence cũ vẫn áp dụng, không chạy mới:** C08 CI[43473786126](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43473786126), SHA9a9f7fb, compile +26 unit +32 IT PASS, không failures/errors/skips. Diff Java blockchain/WorkerIT không đổi ở main5122281/dung9c441eb. C07 CI43191763706 giữ bằng chứng signer/restart/fencing.
- **NOT RUN:** B08 HTTP→Spring Boot→PostgreSQL E2E (localhost8080/8081 không có listener ở Task4); build mới, scanner/replay, goods Fuji receipts hoặc deployment. Không chạy Actions/cài môi trường hoặc lấy secret. Audit link không bật vì endpoint chưa chứng minh hoạt động. Browser API được route bằng test fixtures, không phải backend thật.
- **Giới hạn R01:** journal chỉ giữ trong session memory, cảnh báo rời trang khi outcome chưa rõ; không tuyên bố crash/hard-reload exactly-once. Sau mất journal phải đối soát backend trước tạo receipt khác. Backend vẫn kiểm quyền/quantity/concurrency cuối cùng.

## Quyết định và ranh giới triển khai

Chưa thấy D56 trên main/dung/minh đã fetch. Phương án Dũng được người dùng chuyển tiếp: Dũng mở worker đa operation/verifier/DB tests, Khoa deploy/verify registry C06 sau B09 và contract gate; dùng case demo mới sequence1, giữ proof/job C03 riêng. Đây là **phương án phối hợp chờ ghi Decision**, không phải lệnh deploy hoặc config switch. Task3 từng đề xuất owner khác; phần phối hợp Task9/phương án mới supersede đề xuất cũ khi nhóm chốt.

Registry C03 hiện có CaseVerified receipts, không có runtime C06. Goods/delivery lên Fuji cần deployment C06 mới được phê duyệt riêng, đăng ký org/Relayer và lifecycle fresh case từ1; không đổi nhãn proof C04/C05 hoặc re-use signed bytes cũ. Dũng phải kiểm cả PENDING/PROCESSING chưa ký và PREPARED/SUBMITTED/attempts trước đổi BLOCKCHAIN_REGISTRY_ADDRESS; job chưa pin registry có thể bị claim theo config mới. Cần drain/reconcile hoặc deployment-aware routing được review. Giữ nonce journal/signer lock khi cùng ví/chain; không reset nonce hoặc bỏ sequence pending. Worker phải xử lý pledge/reduction trước receipt/delivery.

D55 VOID có hai observations DeliveryVoided→DisputeResolved cùng eventId/sequence, chỉ một job/logical operation; verifier/scanner mới phải kiểm đủ bộ theo thứ tự và không đếm đôi. C10 hiện chỉ có slice CASE_VERIFIED với proof Fuji trong báo cáo B05; raw DB/B2/restart evidence còn các mục NOT_VERIFIED được bảo toàn, không nâng thành full E2E PASS.

## Người tiếp nhận và bước tiếp theo

- **Dũng:** cung cấp staff/campaign/pledge B08 test hợp lệ cho E2E R01, đối chiếu receipt/audit/job/receivedQty; tiếp tục B09 DELIVERED và worker goods/verifier D55 theo phương án đã trao đổi. Chốt inventory/routing C03 trước cutover; không chỉ kiểm signed attempts. [B08 review](B08_GOODS_CHAIN_REVIEW_2026_11_14.md).
- **Minh:** mở case→Receive goods · R01 API trong real mode, dùng shared proxy `API_INTERNAL_BASE_URL` và backend staff ORG_ADMIN; kiểm R01 thật/refetch/receipt ID. Nhánh minh đang có R01/song song API client/session changes; cần thống nhất điểm tích hợp khi review, không merge mù hai implementation. PR này không lấy code nhánh minh, không thay D01/F09 fixture. [F08 run/test guide](F08_MOCK_GOODS_WORKSPACE.md).
- **Khoa cùng nhóm:** chốt D56/owner/slot, gate C06 và C10 independent verification khi đủ backend/registry; scanner/replay là phần chưa làm, không nhận đã hoàn thành. Không triển khai trong handoff này.
- **Dũng+Minh:** URI mục tiêu theo D49 `/api/public/cases/{publicCaseRef}/audit`; chờ Minh xác nhận/API thực thi và docs T02 đồng bộ trước bật link. Minh đã align mock URI ởc64bb4e, không chứng minh endpoint thật. Không suy txHash là CONFIRMED.

PR chỉ chứa thay đổi ngày14 của Khoa; không workflow/backend/contract/secret/dependency/cache/build output. Không merge PR bàn giao mới. Các dependency tích hợp trên là giới hạn đã biết, không cần người dùng xử lý blocker trước khi nhóm review PR.
