# F08 — Khung nhận/giao hàng ngày 12/11

## Quyết định bàn giao hiện hành — R01 theo PR23 của Minh

Nhóm thống nhất giữ R01 UI của Minh tại PR23 làm bản chính. **PR24 chỉ bàn giao tài liệu/review**, đã bỏ toàn bộ frontend R01 của Khoa, route `/cases/{id}/receipts`, shared-client/session/playwright hunks và receipt tests riêng. Không có UI R01 thứ hai được merge từ PR24. D01/F09 và frontend có trước giữ nguyên; không sửa nhánh Minh.

Minh cần port cải tiến giữ request body/Idempotency-Key khi outcome chưa xác định, kể cả khi retry sau đó bị403/409. Tham khảo commit `d6e255b`: `frontend/features/receipts/model.ts` (sticky uncertain và pending key), `frontend/tests/receipts-api.test.ts` (hai regression tests504→403/409→success: cùng key/body, một logical receipt). `2713675` giữ integration baseline và bốn browser HTTP-fixture tests tại `frontend/tests/e2e/receipts-api.spec.ts`. Source/tests chỉ còn trong lịch sử Git, không còn trong working tree hoặc net diff PR24; đây là yêu cầu chuyển giao, **chưa xác minh đã port sang PR23**.

Phần Task4 và tests phía dưới là lịch sử thử nghiệm bản Khoa, không phải hướng dẫn UI hiện hành sẽ merge. Minh cập nhật run guide/evidence của bản chính qua PR23; không suy tests của Khoa là PASS cho implementation Minh. E2E thật backend/PostgreSQL vẫn chưa được chứng minh trong lượt Khoa.

## Lịch sử Task4 ngày14/11 — R01 API integration của Khoa (đã rút khỏi PR24)

**PASS WITH LIMITATIONS, In Review.** R01 production path đã nối B08 qua shared ApiClient/session/CSRF, nhưng chưa xác minh Spring Boot/PostgreSQL E2E trên máy này (read-only TCP probe127.0.0.1:8080/8081 không có listener). Các phần mock/historical bên dưới giữ làm bằng chứng trước đây; không còn mô tả toàn bộ R01 chỉ là fixture.

FINAL QA14/11: sửa edge case **Medium** 504→retry403/409 từng làm mất pending key dù receipt ban đầu có thể đã commit. Journal nay giữ sticky uncertain cho đến response thành công; không mở draft/key mới chỉ vì retry bị từ chối. Regression mô phỏng server lưu một receipt rồi504→403/409→success xác nhận cùng body/key và một record. Nếu quyền/state chưa được khôi phục, UI tiếp tục khóa mutation mới và cần đối soát backend; không tự bỏ key. Unit tổng **38 PASS** sau sửa (14 R01 +24 regression), lint/typecheck PASS; số36 phía dưới là kết quả trước final fix. Giới hạn journal trong memory/hard reload vẫn giữ nguyên.

- Bật `NEXT_PUBLIC_API_MODE=real`, chạy frontend theo README và cấu hình `API_INTERNAL_BASE_URL` theo proxy hiện có. Staff ORG_ADMIN mở case → **Receive goods · R01 API**, route `/cases/{id}/receipts`. Chỉ lấy IDs từ `GET /api/org/cases/{id}` cùng owning organization; POST `/api/commitments/{contributionId}/receipts`. Không dùng donor history hoặc fixture IDs làm staff input.
- Chọn commitment/multiple goods, quantity nguyên dương ≤ outstanding và Java int, UTC receivedAt, note trim≤500 không markup/URL/NUL. Backend kiểm quyền/scope/timestamps/state lần cuối; UI không cấp thêm role. Response hiển thị receipt ID/items/recordedAt/blockchainStatus; PENDING_ON_CHAIN không phải confirmed.
- Sau POST luôn đọc lại staff projection; không cộng received bằng mock. POST thành công mà GET lỗi vẫn giữ receipt xác nhận, khóa form cho tới GET thành công; Refresh chỉ GET, không gửi lại POST. 409 hoặc rejection có thông báo status/code, refetch trước lần gửi tiếp.
- Timeout/network/408/5xx: journal giữ nguyên body/key, khóa draft và navigation, explicit **Retry same receipt**; không tự tạo key mới. Chặn double-submit bằng journal busy đồng bộ. Journal scoped user/org/case trong SessionWork, sống qua remount nhưng chỉ trong memory; session clear xóa private state. Không khôi phục qua hard reload, không lưu dữ liệu private trong localStorage; cảnh báo beforeunload khi outcome chưa rõ. Sau hard reload phải đối soát backend trước nhập lại, không hứa exactly-once qua browser crash.
- D01/F09 giữ nguyên tại `/goods-demo` trong explicit mock mode, không lấy receipt API vào pooled fixture. Real receipt page không gọi delivery API. Không tích hợp B09, không thay source model/guards F08/F09 mock.
- Kiểm tra mới: **36 unit tests PASS** (12 R01 mapping/validation/retry/error tests +24 regression F08/F09 boundary/F07 adapter), **4 Edge browser HTTP-fixture tests PASS**: partial/multiple receipts và authoritative refetch, response504 sau commit retry cùng key/body, POST201+GET503 recovery không POST lại,409 không tăng quantities. Lint các file integration/tests và typecheck PASS. Lần đầu browser không launch trong môi trường hạn chế, rồi server/browser khác môi trường không kết nối; đã chạy cùng môi trường và PASS, không thay source để lách test.
- **NOT RUN:** B08 real HTTP/DB E2E, backend permissions/concurrency trên DB thật, build mới/Actions/Fuji. Browser routing là HTTP simulation, không phải PostgreSQL evidence. Không cài JDK/DB/dependencies.
- Minh tiếp nhận route/client/session và dùng staff ORG_ADMIN thực ở test env để chạy partial→refetch→receipt lần2, kiểm receipt IDs/G02/P04. Dũng cung cấp backend/dev DB, staff/campaign/pledge hợp lệ, kiểm audit/job đúng một lần và quyền/quantity guards; không phải thiếu frontend implementation. Chỉ commit local, chưa push/PR.

Status: **In Review — PASS trong phạm vi mock R01/D01**. Khoa thực hiện theo phân công ngày 12/11; giữ owner/estimate baseline của F08. Chưa nghiệm thu nhóm, chưa tích hợp B08/B09.

## Bổ sung Task 5 ngày 13/11

Không viết lại logic đã đúng: F02 S1→S2 nhận một phần, S2→S3 giao thành công và FAILED zero stock effect đã có từ PR14. Bổ sung thông báo 400/409 cụ thể theo dòng hàng, giới hạn tồn hiện hành, `aria-invalid`/liên kết feedback; UI có Latest mock result thể hiện hàng/số lượng, trạng thái hoặc reason FAILED. Kết quả chỉ là state trong bộ nhớ, không phải persisted API/audit/blockchain record. Fixture snapshot không đổi, tiếp tục parity với F02; receipt note giữ cap500 đã sửa.

Task 5 kiểm tra bốn tình huống: partial receipt để lại outstanding3kg/1box; delivery4kg/1box cập nhật pooled delivered5kg/1box, confirmed vẫn0; over-delivery của một dòng trả409, tất cả dòng giữ nguyên; FAILED yêu cầu reason và items/evidence rỗng, không tăng delivered/confirmed hay giảm available. Chuỗi R01→D01→FAILED giữ contribution outstanding độc lập với campaign delivery pool.

Kiểm tra ngày13: unit F08 **13/13 PASS**, gồm thêm chuỗi R01/D01/FAILED, atomic multi-item over-delivery, zero/negative/fractional/unknown goods; lint và typecheck PASS. Browser F08 **7/7 PASS** trên Edge hiện có (desktop/mobile/keyboard): 5 tests cũ + over-delivery/recovery và chuỗi partial receipt→FAILED/reason. Lần đầu test mới lỗi ký tự phân cách trong assertion do encoding; đã chuẩn hóa assertion và chạy lại toàn bộ 7/7 PASS, không đổi logic để né lỗi. Không chạy build hoặc điều tra lại WasmHash; không cài môi trường, Actions, B08/B09/DB/Fuji E2E.

Minh tiếp nhận UI/feedback/actor mô phỏng và kiểm tra tích hợp shell. Dũng cung cấp B08/B09 và read projections theo D49 trước ghép API thật ngày14; không chuyển mock controls sang production. Giữ In Review, chưa Done toàn phần. [Kết luận Task4 đã lưu](C06_PART2_D55_REVIEW.md), không review lại C06 trong Task5.

## Phạm vi và cách mở

### Kiểm tra tích hợp bàn giao cuối ngày13 (thực hiện14/11)

Đồng bộ main tại `7b7ab6e` bằng merge giữ lịch sử trên `khoa`; giải quyết phần giao nhau với F09 của Minh bằng cách giữ review state, dispute guard và reset generation của main, cộng feedback/kết quả Task5. Không thay logic F09 hoặc đưa mock sang API thật.

Chạy thực tế trên phiên bản đã kết hợp: **13 F08 +3 delivery-review unit tests PASS**, **7 F08 +1 F08→F09 browser tests PASS** (Edge, desktop/mobile/keyboard), ESLint bốn file F08 thay đổi PASS, TypeScript `--noEmit --incremental false -p frontend` PASS. Browser gồm receipt một phần, delivery/FAILED, 403/409/atomic rejection, reset, loading/empty/error, rồi confirmation/dispute/VOID trên cùng totals. Không chạy build, Java/DB, Actions hay Fuji. Các số 8/5 tests và build bên dưới là bằng chứng lịch sử ngày12, không phải kết quả chạy lại hôm nay.

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
