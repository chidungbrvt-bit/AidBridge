# F08 — Khung nhận/giao hàng ngày 12/11

Status: **In Review — PASS trong phạm vi mock R01/D01**. Khoa thực hiện theo phân công ngày 12/11; giữ owner/estimate baseline của F08. Chưa nghiệm thu nhóm, chưa tích hợp B08/B09.

## Bổ sung Task 5 ngày 13/11

Không viết lại logic đã đúng: F02 S1→S2 nhận một phần, S2→S3 giao thành công và FAILED zero stock effect đã có từ PR14. Bổ sung thông báo 400/409 cụ thể theo dòng hàng, giới hạn tồn hiện hành, `aria-invalid`/liên kết feedback; UI có Latest mock result thể hiện hàng/số lượng, trạng thái hoặc reason FAILED. Kết quả chỉ là state trong bộ nhớ, không phải persisted API/audit/blockchain record. Fixture snapshot không đổi, tiếp tục parity với F02; receipt note giữ cap500 đã sửa.

Task 5 kiểm tra bốn tình huống: partial receipt để lại outstanding3kg/1box; delivery4kg/1box cập nhật pooled delivered5kg/1box, confirmed vẫn0; over-delivery của một dòng trả409, tất cả dòng giữ nguyên; FAILED yêu cầu reason và items/evidence rỗng, không tăng delivered/confirmed hay giảm available. Chuỗi R01→D01→FAILED giữ contribution outstanding độc lập với campaign delivery pool.

Kiểm tra ngày13: unit F08 **13/13 PASS**, gồm thêm chuỗi R01/D01/FAILED, atomic multi-item over-delivery, zero/negative/fractional/unknown goods; lint và typecheck PASS. Browser F08 **7/7 PASS** trên Edge hiện có (desktop/mobile/keyboard): 5 tests cũ + over-delivery/recovery và chuỗi partial receipt→FAILED/reason. Lần đầu test mới lỗi ký tự phân cách trong assertion do encoding; đã chuẩn hóa assertion và chạy lại toàn bộ 7/7 PASS, không đổi logic để né lỗi. Không chạy build hoặc điều tra lại WasmHash; không cài môi trường, Actions, B08/B09/DB/Fuji E2E.

Minh tiếp nhận UI/feedback/actor mô phỏng và kiểm tra tích hợp shell. Dũng cung cấp B08/B09 và read projections theo D49 trước ghép API thật ngày14; không chuyển mock controls sang production. Giữ In Review, chưa Done toàn phần. [Kết luận Task4 đã lưu](C06_PART2_D55_REVIEW.md), không review lại C06 trong Task5.

## Phạm vi và cách mở

- Route `/goods-demo` trong Next.js shell chung; navigation chỉ xuất hiện khi `NEXT_PUBLIC_API_MODE=mock`. Real mode hiển thị thông báo chưa tích hợp, không cho thao tác mock.
- Trong `frontend`: đặt `NEXT_PUBLIC_API_MODE=mock`, chạy `node node_modules/next/dist/bin/next dev --webpack --hostname 127.0.0.1 --port 3001`. Mở `http://127.0.0.1:3001/login`, dùng tài khoản demo `minh.a@example.test`, mật khẩu demo `Mock-only-123!`, chọn **Goods demo · MOCK**.
- Session đăng nhập vẫn do shared session/client quản lý. Bộ chọn actor chỉ mô phỏng F02, không cấp quyền, không thay session, không phải UI quản trị role. Organization của fixture khác organization session và được ghi rõ là mô phỏng.
- R01 chọn pledge và nhiều dòng `contributionItemId`, nhập số nguyên/UTC/note; admin fixture mới được nhận. D01 dùng `campaignItemId`, tồn nhận trừ giao, evidence allowlist; volunteer fixture mới được giao. Actor thiếu quyền thấy 403 và mutation bị khóa.
- Nhận 5 kg/1 box từ S1 tạo đúng số lượng S2; giao 4 kg/1 box từ S2 tạo đúng S3. FAILED yêu cầu reason, không effective items/evidence, stock không đổi; trạng thái chain NOT_APPLICABLE. Giao thành công vẫn chờ xác nhận độc lập, không tăng confirmed.
- Loading/empty/error/retry đọc context, receipt/delivery feedback, fault 409 có sẵn trong controls **developer-only**. Không request B08/B09, không DB/audit/chain thật, không chia sẻ hay chỉnh state G01–G03. Rời route sẽ mất state mock. Không tuyên bố đã kiểm chứng persistence/idempotency backend.

## Nguồn và tích hợp sau

`frontend/features/goods-mock/{GoodsDemo.tsx,model.ts,f02-snapshot.json}`. Snapshot trích S1/S2, actors/evidence từ `docs/fixtures/f02/goods-flow.mock.json` để bundler không phải nạp runtime JSON ngoài frontend. Unit test đối chiếu snapshot với F02 và kết quả tính toán S2/S3 để bắt drift.

Các F02-A01..A05 trong phần lịch sử đã được **D49 giải quyết ở mức contract thiết kế**; không mở lại chúng. Khi tích hợp B08/B09, dùng C03 org-private read projection đã chốt trong D49 (outstandingCommitments, availability/dispute/evidence), DTO/role canonical và shared session/API client; không đưa fixture actor/controls hoặc bộ tính mock vào production. Không dùng G02 của donor khác làm staff selector; không GET Location nếu API không hỗ trợ. Mutation thật phải refetch context theo D49, không coi phép cộng mock là kiểm chứng concurrent DB.

## Bằng chứng kiểm thử thực tế

- **PASS:** `node node_modules/tsx/dist/cli.mjs --test tests/goods-mock.test.ts` — 8 tests: parity F02, R01/S2, D01/S3, role separation, validation/atomic rejection, stale/409, FAILED zero effect, evidence/time.
- **PASS:** TypeScript `tsc --noEmit`; ESLint trên các file F08 bằng config hiện có của donor-preview.
- **PASS:** `node node_modules/next/dist/bin/next build --turbopack` — build frontend chung và route mới.
- **FAIL (công cụ trên Windows hiện tại):** build `--webpack` ném `WasmHash._updateWithBuffer`/FileSystemInfo, trước khi báo lỗi source. Không đổi dependency/config chung để lách lỗi. Turbopack build và Webpack dev/browser chạy thành công; cần Minh kiểm tra lại lệnh build Webpack chuẩn khi tích hợp/CI.
- **PASS:** `PLAYWRIGHT_BASE_URL=http://127.0.0.1:3001`, `PLAYWRIGHT_CHROME_PATH` trỏ Edge đã cài; `node node_modules/@playwright/test/cli.js test tests/e2e/goods-mock.spec.ts --project=mock` — 5 tests. Desktop/mobile 390px, keyboard submit/focus feedback, loading/empty/error, R01/D01, FAILED, 403/409, immediate totals, không B08/B09 writes. Đã xem ảnh desktop/mobile trong `frontend/test-results` (ignored, không commit).
- **NOT RUN:** B08/B09 HTTP/DB/worker/Fuji E2E, integration receipt/delivery production; ngoài phân công mock hôm nay. Không chạy lại toàn bộ QA C03–C07/F07 đã PASS. Những lần browser test đầu có selector sai (accessible name/route announcer), đã sửa test và rerun 5/5 PASS.

## Nghiệm thu nội bộ và bàn giao

Khung hai màn hình, fixtures, vai trò mô phỏng, số lượng/trạng thái, thiếu dữ liệu/lỗi, responsive/keyboard và tài liệu đều đạt phạm vi ngày 12/11. F08 toàn phần vẫn cần Minh review và B08/B09 của Dũng trước tích hợp thật; chưa Done. Actual effort của con người chưa được đo, không tự sửa lịch/estimate.

Không tạo file ngoài repo cho F08. Build/cache/screenshots trong frontend và bị ignore; không đưa dependency hoặc dữ liệu nhạy cảm vào commit.
