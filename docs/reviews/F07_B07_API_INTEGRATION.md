# F07 — tích hợp G01–G03 với B07, 12/11/2026

## Kết luận và phạm vi

**PARTIAL về nghiệm thu end-to-end; implementation frontend/API và kiểm thử HTTP contract đã hoàn thành.** Giữ F07 **In Review**, không Done. Không triển khai lại mock preview ngày11. Baseline main/khoa a300153 (PR10/11/12 đã merge); fetch không có code mới, working tree ban đầu sạch. Không đổi backend, contract, Fuji, ví, task F08 hoặc trạng thái task khác.

Routes trong **frontend Next.js chung**, mặc định real mode:

| Trang | API thực được client gọi | Hành vi |
|---|---|---|
| `/campaigns` | `GET /api/campaigns?page&size` | Danh sách public campaign, loading/error/empty/pagination; không tạo campaign giả |
| `/campaigns/{id}` | `GET /api/campaigns/{id}`, G01 `POST .../{id}/commitments` | Nhiều mặt hàng theo ID, integers1..1000000, tối đa50 dòng; 400/403/409; refetch public capacity sau mutation |
| `/me/commitments` | G02 `GET /api/me/commitments?page&size` | Chỉ history của session; dùng page/size/totalElements server trả; lấy tên/unit từ public campaign, fallback ID nếu metadata không có |
| Form G03 trong history | `POST /api/commitments/{id}/cancellations` | Gửi contributionItemId, quantity, reason; chỉ outstanding; refetch history và public totals sau success/conflict |

Không gọi GET `/api/commitments/{id}` vì B07 không có endpoint đó; Location chỉ là định danh. Không gọi private CaseDetail để lấy thông tin donor. Pledged gốc, cancelled, received, outstanding của contribution được giữ riêng; campaign pledged là net pledged trừ cancelled. Delivery/confirmation chỉ là tổng campaign, không gán provenance cho donor.

## Tích hợp và các mismatch đã xử lý

- `ApiClient` chung thêm G01/G02/G03 và public campaign reads; cookie same-origin, no-store, CSRF và Idempotency-Key dùng đúng cơ chế Minh đã triển khai. Feature adapter chỉ đổi shared ApiError sang feature error, giữ status/code/fieldErrors; không tạo fetch client hoặc mock fallback thứ hai.
- Shell cho phép tài khoản ACTIVE donor không có staff membership vào donor routes, không mở quyền private cases. `donorEnabled` lấy từ `/api/auth/me`; staff/platform role không tự cấp donor grant. Donor-only login về `/campaigns`; grant false không gọi G02 hoặc cho gửi mutation. 401 dùng handler session chung và xóa private workspace.
- Donor layout giữ component khi G01/G02 đổi route. Trong lúc G01 đang gửi vẫn có thể mở history để nhận refresh khi write hoàn tất; khi outcome chưa rõ thì khóa draft và điều hướng nội bộ. Shell khóa links/signout/org switch trong mutation/unknown. beforeunload cảnh báo khi pending. Không tự reload hoặc chuyển trang để cập nhật quantities.
- G01 trước đây coi mọi ApiError là rejected, làm mất key khi HTTP504 dù server đã commit. Nay network loss/5xx/undocumented outcome giữ exact body/key; retry nhiều lần không tạo pledge mới. G03 giữ cơ chế tương tự đã kiểm thử.
- CSRF_INVALID: giữ body/key và thao tác Retry same request/cancellation; shared client fetch CSRF mới rồi gửi đúng request cũ. Không tự replay mutation. Definitive business400/409 không cập nhật optimistic state; lỗi field map theo submitted IDs, không dựa thứ tự items của API (D50).
- Reason G03 khớp Java validator: required1..500, trim, không `<`, `>`, `://`, NUL; không tự thêm field DTO. Không hardcode donor capability hoặc seeded campaign ID trong production.
- Mock controls và `createMockDonorApi` chỉ trong preview/tests. Next real mode proxy `/api` qua origin backend được cấu hình, không fallback nếu backend không chạy. Staff mock mode chỉ hỗ trợ F03/F04 hiện có; dùng Vite preview riêng cho F07 mock.

## Giới hạn retry và vận hành

Pending key/body nằm trong bộ nhớ component; layout chung giữ được qua điều hướng G01/G02. Không lưu reason hoặc session vào localStorage. beforeunload là cảnh báo, không phải durable journal qua browser close/reload được người dùng xác nhận hoặc unmount bên ngoài donor layout. Không tuyên bố tự khôi phục pending qua phiên đăng nhập khác. Trước thao tác mới sau mất phiên/reload, đối chiếu history và backend idempotency/audit; không tự replay với key mới để đoán outcome.

## Kiểm thử thực tế

| Kiểm tra | Kết quả / mức bằng chứng |
|---|---|
| Typecheck frontend chung và preview | PASS |
| ESLint feature/preview và các TS/TSX chung mới/sửa | PASS; dùng ESLint hiện có trong preview, không thêm dependency |
| Next production build (Webpack) | PASS, routes campaigns/detail/history được build dynamic trong shell chung |
| Unit / client contract | 57 PASS:31 session/case regressions +18 donor model/mock +8 B07 adapter/CSRF/DTO/error tests |
| Browser trên Next dev + production build | 14 PASS trên production build (13 ca trước đó cũng PASS trên dev build): G01→G02→G03, non-member donor, no dev controls, reversed items order, 400/403/409/504, 401, CSRF retry, stale receipt, pagination, empty/error, desktop/mobile/keyboard |
| Vite preview regression | 21 PASS, gồm history mở trước write hoàn tất, receipt/dataRevision, stale conflict và repeated504 cancellation |
| Trình duyệt thật không intercept | Đã mở `/campaigns`, chuyển `/login`, hiện REAL API và báo không kết nối session service; fail-closed, không hiện dữ liệu mock |
| Spring Boot + PostgreSQL HTTP E2E với UI mới | **NOT RUN**; không có listener8081/5432/5433 hoặc DB env được cấp trên máy Khoa |
| Donor grant dev / campaign published thật | **NOT VERIFIED**; không có DB/API được phép để đọc seed hoặc xác minh dữ liệu |
| CI cho các thay đổi F07 chưa push | **NOT RUN**; workflow đã có trên main kiểm thử C07, không chứng minh UI mới chưa được push. Không push/PR trong lượt này theo user |
| Blockchain checks | Không chạy lại, không gửi giao dịch hoặc bật worker |

Browser contract tests dùng Playwright `page.route` với fixtures/model mock **chỉ trong test**, không có Spring Boot/PostgreSQL phía sau. PASS này chứng minh wiring của Next shell/session/shared client và error/retry UI, không chứng minh real backend authentication, cookie proxy, DB locks/concurrency hoặc outbox đã chạy end-to-end. Partial-receipt fixtures có received>0; B07 thật hiện received/delivered/confirmed còn0 đến B08–B10, không tự tạo receipt thật để demo.

Đã sửa regression do khóa My pledges cả khi G01 đang gửi; test race cũ PASS lại. Error copy giữ thông báo cũ kèm status/code để regression không mất semantics. Test locator lỗi đầu do Next route-announcer cũng có role alert đã scope lại đúng error. Hai browser suites được chạy tuần tự sau một timeout scroll khi chạy đồng thời; không force-click hoặc bỏ assertions. CSRF test so sánh refresh tăng1 lần, không cố định tổng lượt bootstrap trong Strict Mode.

## Chạy / kiểm tra lại

Trong `frontend`, dependencies đúng `package-lock.json` (không đổi version):

```powershell
node node_modules/next/dist/bin/next dev --webpack --hostname 127.0.0.1 --port 3000
node node_modules/typescript/bin/tsc --noEmit
node node_modules/tsx/dist/cli.mjs --test tests/*.test.ts features/donor/model.test.ts
$env:PLAYWRIGHT_CHROME_PATH = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
node node_modules/@playwright/test/cli.js test --project=donor-api
```

Project donor-api phải chạy trên Next **real mode**, còn project mock của Minh tiếp tục chạy trên mock-mode server riêng như README. Hai project không tự trộn mode. Dừng dev server trước build; `node node_modules/next/dist/bin/next build --webpack`; sau đó `node node_modules/next/dist/bin/next start --hostname 127.0.0.1 --port 3000`.

Hiện đã để production server tại http://127.0.0.1:3000. Chưa có backend nên không đăng nhập/tạo pledge thật tại máy Khoa. Không cung cấp fake credentials hoặc bypass quyền trong real path. Khi có backend, set NEXT_PUBLIC_API_MODE=real và API_INTERNAL_BASE_URL tới HTTP(S) origin dev được cấp rồi restart/rebuild. HTTP local backend cần SESSION_COOKIE_SECURE=false theo runbook; production vẫn giữ Secure.

## Dũng / Minh tiếp nhận

- Dũng: cung cấp môi trường B07 dev được phép và tài khoản donor ACTIVE/donor_enabled=true, published campaign có capacity; API P03/P04/G01/G02/G03 đã ở main. Seed là dev data theo D52/D54, không migration giả production. Chạy E2E thật: login→multi-line pledge→G02→cancel outstanding→G02/P04 refetch, cùng 403/CSRF/409/replay/outbox; xác minh DB atomicity và ownership. Không cần sửa backend trong lượt này.
- Minh: review thay đổi giới hạn trong shell/login, donor route group/layout và shared client methods; không dựa item index hoặc platform role. Không copy mock controls vào app. Sau khi có env Dũng, kiểm cookie/CSRF đi qua proxy thật và các task frontend khác vẫn chạy.
- Khoa: local commit F07, chưa push/PR; giữ In Review/PARTIAL end-to-end. Không bắt đầu F08.

## File/cache phát sinh

Source/tests/docs trong D:/Project/AidBridge. Cài frontend bằng `pnpm dlx npm@12.2.2 ci --ignore-scripts --cache .cache/npm` vì npm CLI không có trên PATH Codex; không cài global hoặc đổi lockfile. node_modules, .cache/npm, .next và test-results nằm trong frontend, bị ignore. Tool npm qua pnpm dlx tái sử dụng store hiện có D:/.pnpm-store/v11; cache pnpm do runtime quản lý, không đổi cấu hình hoặc dọn cache. Không tạo ví hoặc output riêng ngoài repo.


## Bổ sung kiểm chứng CI backend thật — lượt tiếp 12/11

Đã chuẩn bị workflow `f07-real-e2e.yml`: PostgreSQL16/JDK21 trên runner; `DonorUiE2EIT` khởi động full Spring Boot HTTP trên random port và schema test b04_it_*; tái sử dụng AbstractCampaignHttpIT để tạo case → approve độc lập → draft/publish qua API. Donor grant chỉ cập nhật trong schema test. Java test build/start Next production với proxy tới đúng port backend, rồi chạy project Playwright donor-real.

Không thêm seed production hoặc test controller trong src/main. Không truyền fake success responses: chỉ hai fault tests dùng route.fetch tới backend thật, kiểm DB đã commit rồi thay riêng response bằng504. Received goods dùng SQL fixture đúng như ContributionCancelIT vì R01/B08 chưa có; không tuyên bố kiểm thử receipt creation API. Kiểm tra audit/outbox, original key/body và số cancelled trong PostgreSQL. Credentials ngẫu nhiên chỉ truyền qua env child process, không log/artifact. Workflow kiểm cả Java/JUnit/JSON evidence không bị skip.

Tại lúc chuẩn bị commit: local typecheck/lint/diff PASS; Java và 5 browser scenarios này chưa chạy trên Windows, chờ CI. Kết quả PARTIAL trước đó chỉ được thay sau khi có Actions run thực sự PASS. Không cài môi trường nặng lên Windows hoặc bật blockchain. Push/PR được người dùng cho phép ở lượt này; không merge.


CI run43199326961 đầu: frontend57 unit và backend9 validation tests PASS; DonorUiE2EIT chưa chạy browser vì readiness Next không nhận200. Sửa test harness: dùng HTTP/1.1 cho readiness, ghi/log startup có redact password, chuyển stdout child sang file thay vì ghi trực tiếp vào Maven fork protocol. Chưa nới assertions hoặc kết luận E2E PASS; chạy lại để xác minh.
