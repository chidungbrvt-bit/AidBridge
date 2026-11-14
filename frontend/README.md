# AidBridge frontend — F03/F04

Next.js App Router, TypeScript, Tailwind. Mặc định **API thật**, proxy cùng origin `/api` tới `http://127.0.0.1:8081`, không tự fallback sang mock. F05 có Audit preview bằng fixture. Timeline sống còn chờ T01/T02 và mapping case trong báo cáo Fuji.

## Chạy với backend trên máy Dũng

Backend/PostgreSQL cần chạy trước. Port và các biến DB nằm trong `backend/aidbridge-backend/src/main/resources/application.properties`; evidence grants theo `docs/architecture/B04_CASE_WORKFLOW.md`. Chỉ trên HTTP local đặt `SESSION_COOKIE_SECURE=false` ở backend. Main `051e40f` không có `CLAUDE.md`.

Trong thư mục `frontend`:

```powershell
npm ci
$env:NEXT_PUBLIC_API_MODE = 'real'
$env:API_INTERNAL_BASE_URL = 'http://127.0.0.1:8081'
npm run dev
```

Mở `http://127.0.0.1:3000/login`. localhost trên máy Minh không trỏ tới máy Dũng: chạy frontend cùng máy có backend hoặc cấu hình origin test được nhóm cấp. Đổi mode phải restart/rebuild. Dev/build dùng Webpack để tránh lỗi Turbopack PostCSS worker trong môi trường Windows kiểm tra; không nâng dependencies.

## Mock và kiểm tra

Đặt `NEXT_PUBLIC_API_MODE=mock`, rồi `npm run dev`. A `minh.a@example.test`, B `minh.b@example.test`, password giả `Mock-only-123!`. A tạo case → B request changes → A Edit case/Save revision/Resubmit for review → B refresh/Approve. A không tự duyệt dù có cả VOLUNTEER và VERIFIER.

Trong terminal khác, cùng thư mục:

```powershell
npm test
npm run typecheck
$env:PLAYWRIGHT_CHROME_PATH = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
npm run test:e2e -- --project=mock
```

Không có Chrome thì `npx playwright install chromium` và bỏ biến `PLAYWRIGHT_CHROME_PATH`. Dừng dev server trước khi `npm run build`; chạy bản build bằng `npm start`. Server phải chạy cùng mode với project test.

## Smoke thật trên môi trường Dũng

Trong process test, đặt `API_BASE_URL=http://127.0.0.1:3000/api`, `REAL_A_EMAIL`, `REAL_A_PASSWORD`, `REAL_B_EMAIL`, `REAL_B_PASSWORD`, `REAL_ORGANIZATION_ID`, `REAL_EVIDENCE_FIXTURE_KEYS` (key ngăn cách dấu phẩy). Không gửi/commit secrets. A cần VOLUNTEER + VERIFIER, B cần VERIFIER; cùng organization và khác tài khoản.

```powershell
node scripts/smoke-real.mjs
$env:REAL_UI_BASE_URL = 'http://127.0.0.1:3000'
npm run test:e2e -- --project=real
```

HTTP smoke tạo **hai hồ sơ giả**, giữ audit: changes/edit/resubmit/approve và reject. Script từ chối header mock trước khi gửi credentials; không log body/cookie/password. Thiếu cấu hình báo **Not Run**; Playwright real skipped không được tính pass.

F07 G01–G03 đã tích hợp vào Next: `/campaigns`, `/campaigns/{id}`, `/me/commitments`; dùng session/cookie/CSRF và API client chung, cần donor grant thật. Preview riêng `dev/donor-preview` vẫn dùng mock và không được copy vào real path.

Test F07 HTTP contract trên server **real mode**: `npm run test:e2e -- --project=donor-api`. Playwright intercept API bằng fixtures trong tests; đây không phải Spring Boot/DB E2E. Project mock của F03/F04 loại donor-api để tránh trộn mode. `npm test` gồm session/case, donor adapter và donor model tests.

Xem [bàn giao F07 B07](../docs/reviews/F07_B07_API_INTEGRATION.md) về routes, retry/idempotency, kết quả và môi trường còn thiếu.

## Campaign staff và audit preview

Trong mock mode, B duyệt case mẫu **Essential supplies for Household Willow**, sau đó đăng nhập `minh.admin@example.test` (cùng mật khẩu mock), mở case và chọn **Create campaign draft → Publish campaign → View published campaign**. Frontend dùng lại routes đọc campaign của F07.

Real mode dùng cùng màn hình với B06 P01–P04. Smoke dành cho một case test mới, đã VERIFIED, cho public consent, có ít nhất hai mặt hàng và chưa có campaign. Đặt `REAL_F06_ADMIN_EMAIL`, `REAL_F06_ADMIN_PASSWORD`, `REAL_F06_VERIFIED_CASE_ID`, `REAL_ORGANIZATION_ID` và `REAL_UI_BASE_URL` rồi chạy:

```powershell
npm run test:e2e -- --project=real campaign-real.spec.ts
```

**Audit preview** trong workspace trình bày ba trạng thái fixture, gồm proof Fuji độc lập từ C05. Đây là màn minh họa; T01/T02 và chain status sống vẫn chờ backend.

Xem [bàn giao F05/F06](../docs/handoffs/MINH_F05_F06_2026-11-12.md) và [bàn giao F03/F04](../docs/handoffs/MINH_F03_F04_2026-11-12.md).


## F07 E2E backend thật trên CI

[PR13](https://github.com/chidungbrvt-bit/AidBridge/pull/13) có workflow `F07 real backend E2E`: PostgreSQL16 + JDK21 trên runner, `DonorUiE2EIT` tạo fixture qua API và khởi động Next production proxy tới Spring Boot thật. Project `donor-real` chỉ bật khi AIDBRIDGE_F07_REAL_E2E=true và được Java test truyền schema/account test qua env; không dùng credentials thật hoặc seed production. Không chạy trực tiếp bộ này trên DB dev chưa được cấp.

[Run43200835548](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43200835548) đã PASS5 browser E2E thật +SQL assertions, cùng57 frontend unit và9 backend validation; không skip. Received rows được seed theo fixture SQL B07, không phải receipt creation API. Xem [F07 evidence](../docs/reviews/F07_B07_API_INTEGRATION.md). Không cần cài Java/DB lên Windows.

## Nhận hàng với B08 (R01)

Trong real mode, đăng nhập bằng ORG_ADMIN sở hữu hồ sơ. Mở case detail và chọn một cam kết trong **Record goods receipt**. Sau 201, giữ receipt ID để đối chiếu và kiểm tra outstanding đã cập nhật. Có thể chạy `npm run test:e2e -- --project=receipt-api` với server real mode để kiểm tra browser bằng HTTP fixture. Dùng workflow **F07 real backend E2E** để chạy qua Spring Boot và PostgreSQL.

## Xác nhận giao hàng và tranh chấp (F09 fixture)

Mở **Goods demo · MOCK** trong mock mode. Load S2, ghi D01 thành công bằng actor Field volunteer và chọn evidence. Trong **Delivery confirmation and disputes**, chọn D02 và tick đúng toàn bộ evidence version. Actor Independent delivery confirmer có thể xác nhận hoặc yêu cầu xem xét. Actor Delivery recorder with confirmer role bị chặn tự xác nhận.

S01 mở tranh chấp cấp hồ sơ hoặc gắn một delivery. S03 dùng actor Platform admin để Uphold hoặc Void delivery. Giữ nguyên dữ liệu nhận hàng khi void, UI tính lại số đã giao và đã xác nhận. Hàng chờ xác nhận và timeline dùng dữ liệu trả về từ fixture D01. Xem [bàn giao R01/F09 và trạng thái tích hợp](../docs/handoffs/MINH_F08_F09_2026-11-14.md).
