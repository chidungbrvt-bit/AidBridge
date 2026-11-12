# Bàn giao F03 và F04 của Minh

> Bản lịch sử ngày 10/11. Bàn giao hiện hành, DTO đã đối chiếu main và kết quả mới: [12/11/2026](MINH_F03_F04_2026-11-12.md). Các ghi chú backend chưa có trong bản cũ không phản ánh main hiện tại.

Ngày bàn giao: 10/11/2026. Phạm vi gồm app shell và session client F03; danh sách, tạo và chi tiết hồ sơ F04; review độc lập F04. Mã frontend chạy với mock HTTP có cookie, đồng thời có cấu hình chuyển toàn bộ API sang backend thật. **Trạng thái: In Review**, chờ người phụ trách review và bằng chứng tích hợp backend; không đánh dấu Done.

Yêu cầu hiện tại của người dùng cho phép triển khai F03/F04, thay cho giới hạn documentation-only trong bản planning cũ đối với đúng hai task này. Quyền đó không mở rộng sang triển khai B04/B05, F05, commit/push, public deployment hoặc mua dịch vụ. Giữ nguyên primary owner, estimate và lịch sử tiến độ của nhóm.

## Kết quả được bàn giao

| Phần | Tệp chính | Hành vi |
|---|---|---|
| App Router và session | `frontend/src/app`, `frontend/src/components/session-provider.tsx`, `workspace-shell.tsx` | Login, chọn active organization, menu theo quyền, logout, 401 và mất session |
| Shared typed API client | `frontend/src/lib/api-client.ts`, `types.ts` | Cùng interface/DTO cho mock và real; CSRF, ProblemDetail, các lệnh create/review có key do caller giữ |
| Mock HTTP và dữ liệu | `frontend/src/lib/mock-backend.ts`, `mock-fixtures.ts`, `src/app/api/[...path]/route.ts` | Identity riêng, cookie HttpOnly, scope theo organization, validation, version và idempotency |
| Case list/create/detail | `frontend/src/components/cases/case-list.tsx`, `create-case.tsx`, `case-detail.tsx` | Loading/empty/error, nhiều need, consent, fixture allowlist, list → create → detail |
| Review | `frontend/src/components/cases/review-panel.tsx` | Approve/reject/request changes, snapshot evidence, cấm tự review, xử lý conflict và unknown outcome |
| Kiểm thử | `frontend/tests/api-session.test.ts`, `frontend/scripts/smoke-real.mjs` | Smoke mock lặp lại được; script real HTTP có điều kiện chạy rõ ràng |

Route map: `/` chuyển sang `/cases`; `/login` là form đăng nhập; `/cases` là danh sách; `/cases?status=SUBMITTED` là review queue; `/cases/new` là form tạo; `/cases/[id]` chứa chi tiết và review panel. Audit timeline thuộc F05 và chưa nằm trong phần bàn giao này.

## Cách chạy mock

Mở terminal trong thư mục `frontend` sau khi giải nén bản bàn giao:

```powershell
npm ci
$env:NEXT_PUBLIC_API_MODE = 'mock'
npm run dev
```

Mở `http://127.0.0.1:3000/login`. Mock là mặc định nếu không đặt `NEXT_PUBLIC_API_MODE`. Dừng và khởi động lại server để reset các hồ sơ/session giả; refresh trang không reset dữ liệu server. Dữ liệu mock chỉ nằm trong bộ nhớ của tiến trình, không phải PostgreSQL và không phải bằng chứng blockchain.

Versions đã pin trong `package.json` và `package-lock.json`: Next.js 16.4.6, React/React DOM 19.3.1, TypeScript 7.0.2. Runtime dùng trong phiên triển khai: Node.js 24.22.1 và npm 12.2.2; yêu cầu Node từ 22.13.0. Chạy các kiểm tra lặp lại trong `frontend`:

```powershell
npm test
npm run typecheck
npm run build
node scripts/smoke-real.mjs
```

Tất cả tài khoản dưới đây chỉ dành cho mock, dùng mật khẩu `Mock-only-123!`. Không dùng chúng để seed hoặc đăng nhập API thật.

| Identity | Email | Membership và vai trò | Mục đích |
|---|---|---|---|
| A · Alex Nguyen | `minh.a@example.test` | Riverlight Aid ACTIVE: VOLUNTEER + VERIFIER; Harbor Relief DISABLED | Tạo hồ sơ, chứng minh có thêm VERIFIER vẫn không tự review |
| B · Blair Tran | `minh.b@example.test` | Riverlight Aid ACTIVE: VERIFIER | Review hồ sơ của A |
| Reader · Casey Le | `minh.reader@example.test` | Riverlight Aid ACTIVE: DELIVERY_CONFIRMER | Đọc được nhưng create/review trả 403 |
| Outsider · Drew Pham | `minh.outsider@example.test` | Harbor Relief ACTIVE: VOLUNTEER + VERIFIER; PLATFORM_ADMIN | Kiểm tra cross-org 404; platform admin không được tự động đọc private case |

Riverlight organization ID: `10000000-0000-4000-8000-000000000001`. Harbor organization ID: `10000000-0000-4000-8000-000000000002`. Hồ sơ seed của A: `30000000-0000-4000-8000-000000000001`. Fixtures Riverlight: `riverlight-intake-v1`, `riverlight-needs-v1`; fixture Harbor: `harbor-intake-v1`.

## Vì sao navigation phải lấy từ /me

Login hiện tại chỉ trả `{user}`; response này không phải nguồn membership hiện hành. `/me` truy vấn lại tài khoản, membership ACTIVE và organization APPROVED. Vì vậy menu phải dựa vào membership của organization đang chọn từ `/me`, không dựa vào tên tài khoản, một role lưu từ lần login trước hoặc `platformRole`.

Trong source B03, `MeResponse` là `{userId, fullName, status, platformRole, donorEnabled, memberships:[{organizationId,roles,status}]}`. API document cũ lại mô tả `displayName`, `platformAdmin` và membership `active`. `normalizeMe` trong shared client nhận cả hai projection, đưa về một dạng dùng chung; role lạ không cấp quyền. Kiểm chứng phần B03 ở đây là **đối chiếu source**, chưa phải HTTP smoke thật. Aliases như FIELD_VOLUNTEER/CASE_VERIFIER là adapter tương thích tài liệu; các enum thực tế là VOLUNTEER/VERIFIER/ORG_ADMIN/DELIVERY_CONFIRMER.

Menu Cases cần active staff membership; Create case cần VOLUNTEER; Review queue cần VERIFIER. Membership DISABLED của A ở Harbor không xuất hiện trong lựa chọn active organization. `PLATFORM_ADMIN` không thay thế membership. Khi cửa sổ lấy focus lại, frontend refresh `/me`; khi user/org/quyền thay đổi hoặc logout/401, vùng private được unmount để không giữ case/form của context cũ. Đây là kiểm soát UX; backend vẫn phải kiểm tra quyền ở mỗi request.

Chuỗi session thực thi:

```text
GET /api/auth/csrf
POST /api/auth/login  {email,password} + CSRF header
GET /api/auth/csrf    token mới sau login
GET /api/auth/me      current memberships → navigation
... authenticated reads / mutations ...
POST /api/auth/logout + CSRF header → 204
clear private state
GET /api/auth/csrf    token anonymous mới
GET /api/auth/me      → 401 nếu thử đọc lại
```

Browser quản lý session cookie. Client dùng URL cùng origin `/api`, `credentials: same-origin` và `cache: no-store`; không đọc cookie HttpOnly bằng JavaScript, không ghi credential, CSRF token hoặc session vào localStorage. CSRF chỉ giữ trong bộ nhớ. `INVALID_CREDENTIALS` ở login là lỗi trên form; mọi 401 ở request khác xóa private state, kể cả khi `/me` trả `INVALID_CREDENTIALS` do account bị disable. `CSRF_INVALID` xóa token cached, lần thử do người dùng thực hiện tiếp theo bootstrap token mới; client không tự phát lại mutation.

Logout 204 không parse JSON. Nếu logout chưa xác nhận vì lỗi mạng, UI cho retry và không tuyên bố session đã bị hủy. Sau 204, state được xóa ngay cả khi bước lấy CSRF mới gặp sự cố.

## Contract đã đối chiếu và những phần còn là mock

Không tìm thấy D39/D40 trong `docs/planning/DECISION_LOG.md` của ZIP; file này chỉ đến D25. Không tìm thấy `B04_CASE_WORKFLOW.md`, package Java `casefile`/`verification`, CreateCaseRequest B04 hoặc controller/DTO B05. Không tự tạo nội dung giả thay cho các tài liệu thiếu. Baseline dùng cho lần triển khai này là yêu cầu trực tiếp của người dùng, API_SPECIFICATION A01–A04/E01/C01–C03/V01–V03, quy tắc nghiệp vụ và source B03 có trong ZIP.

| Nhóm | Đã đối chiếu với nguồn nào | Kết luận |
|---|---|---|
| Login `{user}`, `/me`, role enum, CSRF/logout/cookie | `AuthController`, `AuthService`, `AuthMapper`, auth DTO, `SecurityConfig`, `application.properties` | Khớp source B03; HTTP real chưa xác minh |
| CreateCaseRequest, NeedInput, ConsentInput | DTO dictionary trong API_SPECIFICATION | Khớp shape tài liệu; **không có B04 DTO để xác minh** |
| DecisionRequest/DecisionResponse và V01–V03 | API_SPECIFICATION | Khớp shape tài liệu; **không có B05 DTO/controller để xác minh** |
| CaseSummary và metadata fixture E01 | Tài liệu chỉ định ý nghĩa, chưa freeze đầy đủ projection | Shape frontend/mock chờ B04 xác nhận |
| CaseDetail title/alias/area/priority, consent summary, goodsTotals, disputeSummary, blockchainStatus | Projection cần cho màn hình, được chú thích trong `types.ts` | Giá trị/projection mock, chờ backend freeze |
| Case/evidence IDs, timestamps, sha256, reviewHistory, auditEventId và status chuyển tiếp | Mock backend | Dữ liệu giả; không có DB/outbox/transaction/chain thật |

Create form gửi chính xác các field sau, không gửi actorId, creator hoặc rowKey UI:

```ts
{
  organizationId,
  beneficiaryAlias,
  approximateArea,
  internalTitle,
  publicTitle?,
  publicSummary?,
  priority: 'NORMAL' | 'URGENT',
  consent: {
    basis: 'CONSENT' | 'REPRESENTATIVE',
    scope,
    capturedAt, // UTC ISO-8601
    publicSummaryAllowed
  },
  needs: [{ name, unit, quantity }],
  evidenceFixtureKeys: ['allowlisted-key']
  // supersedesCaseId?: contract hỗ trợ, UI slice này không nhập field đó
}
```

Organization lấy từ active membership. Needs có 1–20 dòng, số lượng nguyên 1–1.000.000, không trùng cặp name/unit. Evidence có 1–10 fixture keys của organization. Checkbox xác nhận consent là UI guard, không thêm field ngoài DTO. Thời gian form có nhãn UTC. Public title/summary chỉ gửi khi cho phép public summary. `fieldErrors` được gắn theo đường dẫn, ví dụ `needs[1].quantity`, `needs[1].name`, `consent.scope`; focus chuyển tới field hoặc dòng lỗi đầu tiên.

Review gửi `expectedVersion`, đúng tập `reviewedEvidenceVersionIds` hiện tại và `reason` khi reject/request changes. UI hiển thị revision case và ID/versionNo từng evidence; người review phải chọn đủ snapshot. Không gửi reviewerId; actor luôn lấy từ session ở backend. `SELF_APPROVAL` phải được backend từ chối dù UI đã disable action của creator.

## Replay smoke trên giao diện mock

Khởi động server mới trước một lượt kiểm tra nếu muốn dữ liệu seed sạch. Không dùng hai tab thường để mô phỏng hai người: các tab đó dùng chung cookie. Có thể sign out A rồi login B tuần tự, hoặc dùng hai browser context/profile riêng.

1. **Login đúng và menu:** chọn A tại `/login`, bấm Sign in. Kiểm tra có Cases/Create case/Review queue và chỉ Riverlight là active. Sign out rồi login B: có Cases/Review queue, không có Create case. Login reader: chỉ có Cases. Login outsider: không thấy hồ sơ Riverlight dù platform admin.
2. **Sai mật khẩu:** nhập email A với password khác; lỗi `Email or password is incorrect.` xuất hiện, password được xóa khỏi input, không mở private workspace. Đăng nhập lại bằng credential mock đúng.
3. **Logout và expiry:** bấm Sign out, kiểm tra về login; nút Back không phục hồi private screen. Login lại, mở `Mock test controls` → `Expire session`; `/me` nhận 401, session/private state bị xóa và UI yêu cầu đăng nhập lại. Trong Network có thể quan sát CSRF bootstrap mới sau logout; không sao chép token/cookie vào tài liệu.
4. **List loading/empty/error:** trong danh sách chọn Rejected khi seed mới để thấy empty state. Có thể dùng Network throttling để quan sát loading. Chọn `Fail next read`, rồi Refresh ở Cases để thấy lỗi 503; bấm Refresh lần nữa để phục hồi.
5. **Tạo hai needs:** login A, Create case. Dùng alias `Demo household 18`, area `Sample North District`, title `Essential goods for a demo household`; giữ Rice/bag/2 và Hygiene kit/kit/2; xác nhận simulated consent, chọn cả hai fixture Riverlight. Submit → HTTP 201/Location, detail có SUBMITTED và hai need. Organization, creator và evidence phiên bản được tạo từ mock server.
6. **400 gắn đúng dòng/field:** trên một form mới, đổi item 2 thành Rice/bag để trùng item 1; submit hợp lệ các mục khác → server 400 với lỗi ở `needs[1].name`, focus về dòng 2. Điền Consent scope chỉ bằng dấu cách để nhận lỗi `consent.scope`. Quantity bằng 0 bị HTML validation chặn sớm; test tự động kiểm tra thêm server 400 `needs[1].quantity`, không tạo một phần hồ sơ.
7. **A không được tự review:** mở seed case hoặc case A vừa tạo. Panel giải thích cần người khác và disable cả ba actions dù A có VERIFIER. Test tự động vẫn gọi API trực tiếp để khẳng định 403 SELF_APPROVAL; không coi việc disable nút là đủ bằng chứng backend guard.
8. **B approve A:** ghi nhớ URL detail, sign out A/login B rồi mở URL đó. Chọn đủ evidence versions, Approve case. Business status thành VERIFIED; Blockchain sync là PENDING_ON_CHAIN. Hai trạng thái hiển thị riêng ở detail và thông báo review.
9. **Thiếu reason, reject và request changes:** cần case SUBMITTED mới cho mỗi action. Login B, chọn đủ evidence, để reason trống rồi bấm Reject case hoặc Request changes: UI chỉ lỗi tại reason. Điền reason giả, submit → REJECTED hoặc CHANGES_REQUESTED, chain NOT_REQUIRED. HTTP 400 khi bỏ reason được kiểm tra riêng trong suite; form guard không giả nhận một HTTP đã không gửi.
10. **VERSION_CONFLICT:** với B ở một case SUBMITTED, mở `Mock review test controls` → `Simulate a stale revision`. Chọn snapshot đang hiển thị và submit → 409 VERSION_CONFLICT. Actions bị khóa; bấm `Refresh before reviewing again`, kiểm tra revision/evidence mới, chọn lại snapshot rồi mới submit. Không tự lấy version mới rồi approve mà bỏ qua bước review lại.
11. **Unknown outcome khi create:** điền form A, mở `Mock test controls` → `Lose next write response`, sau đó submit. Mock đã commit nhưng trả outcome chưa rõ; form bị khóa, bấm `Retry same request`. Request body và Idempotency-Key giữ nguyên trong bộ nhớ, nhận lại resource ban đầu, không tạo thêm case.
12. **Unknown outcome khi review:** trên case SUBMITTED của A, login B, dùng `Lose next mutation response`, sau đó review. Bấm `Retry same request` để lấy lại decision cũ với cùng action/body/key. Không refresh, đổi org, đóng tab hoặc điều hướng đi khi đang chờ retry: pending key chỉ giữ trong bộ nhớ; nếu rời trang cần kiểm tra list/detail trước khi bắt đầu mutation khác.
13. **403 và 404:** reader đọc case Riverlight được nhưng API create/review bị 403 FORBIDDEN; outsider mở URL seed Riverlight → 404 RESOURCE_NOT_FOUND. Fixture Riverlight cũng không được outsider enumerate. Suite tự động gọi API kể cả khi UI đã giấu action để phân biệt UX guard và permission guard.

Chạy `npm test` để replay đầy đủ các ca API bị UI chặn sớm: CSRF thiếu/sai, nested quantity validation, thiếu role, sai organization, self-review, missing reason, stale version/evidence, replay key và role bị thu hồi. Tệp test dùng shared API client với mock handler; đây là kiểm thử transport/contract trong tiến trình, không phải Spring/PostgreSQL thật. Browser smoke đi qua Next route HTTP được ghi riêng trong bảng kết quả cuối.

## Chuyển sang API thật

Backend trong ZIP đặt `server.port=8081`, khác port 8080 trong planning cũ. Dùng đúng origin được Dũng cung cấp nếu môi trường khác. Không fallback âm thầm sang mock nếu backend lỗi.

```powershell
# Chạy trong frontend; dừng dev server cũ trước khi đổi mode.
$env:NEXT_PUBLIC_API_MODE = 'real'
$env:API_INTERNAL_BASE_URL = 'http://127.0.0.1:8081'
npm run dev
```

Browser vẫn gọi `/api` cùng origin của frontend. `next.config.ts` rewrite API sang backend; backend quản lý JSESSIONID. Production/local build cần đúng mode tại thời điểm build, sau đó restart khi đổi cấu hình. Màn hình hiển thị REAL API; mock controls không xuất hiện, local mock route không phục vụ request real.

Cookie B03 mặc định `Secure=true`, `HttpOnly=true`, `SameSite=Lax`, timeout 30 phút. Nếu backend test chạy HTTP localhost, người vận hành backend phải đặt `SESSION_COOKIE_SECURE=false` **chỉ cho local test**, hoặc dùng HTTPS. Không hạ Secure trên public deployment. Backend còn cần PostgreSQL và các biến DB phù hợp; F03/F04 không tạo DB hoặc seed tài khoản thật.

Trước real smoke, Dũng cấp hai tài khoản riêng trong test environment: A có VOLUNTEER + VERIFIER, B có VERIFIER, cùng active organization và fixture keys allowlisted. Đưa các giá trị vào biến môi trường của terminal; không ghi password vào source, Git hoặc ảnh chụp. Script không dùng mock credential để thử API thật.

```text
API_BASE_URL=http://127.0.0.1:3000/api
REAL_A_EMAIL
REAL_A_PASSWORD
REAL_B_EMAIL
REAL_B_PASSWORD
REAL_ORGANIZATION_ID
REAL_EVIDENCE_FIXTURE_KEYS=key-one,key-two
```

```powershell
npm run smoke:real
```

Script chỉ chạy khi đủ biến; nếu thiếu, in Not Run và thoát mà không gửi mutation. Script kiểm tra header `X-AidBridge-Mock` trước khi gửi credentials và từ chối known mock credentials. Nó tạo một case giả, kiểm tra auth, 400/403, cùng key replay, B review A, VERSION_CONFLICT, missing reason và logout cả hai session; chỉ in stage, HTTP status và error code, không in cookie/password/private response. Network failure của mutation được retry tối đa một lần với nguyên payload/key. Nếu A không có VERIFIER, script chỉ xác minh FORBIDDEN và ghi SELF_APPROVAL riêng là Not Run.

Real idle-expiry, cross-org 404 và successful reject/request-changes cần account/fixture riêng, được script ghi Not Run. Chưa có backend B04/B05 trong ZIP nên script hiện là công cụ tái chạy khi môi trường sẵn sàng, không phải chứng nhận endpoint đã chạy. E01 script đang chờ list projection có `key`; backend freeze projection khác phải cập nhật adapter, fixture và test đồng bộ.

## Endpoint gaps và trách nhiệm tiếp theo

| Endpoint hoặc nguồn | Code backend có trong ZIP | HTTP thật xác nhận trong phiên này | Owner và bước tiếp |
|---|---|---|---|
| A01 CSRF, A02 login, A04 me | Có controller/service/DTO; có source tests | Chưa; probe mới nhất đến `127.0.0.1:8081` nhận ECONNREFUSED | Dũng/B khởi động API, cấp tài khoản test; Minh chạy auth smoke |
| A03 logout | Có Spring Security filter trả 204 | Chưa | Dũng/B + Minh xác minh cookie/CSRF qua proxy |
| E01 evidence fixtures | Không có | Not Run | Dũng/B triển khai allowlist và freeze metadata projection |
| C01 create, C02 list, C03 detail | Không có B04 DTO/controller | Not Run | Dũng/B cung cấp B04 workflow/DTO, DB effects và org guards; Minh đối chiếu response thực |
| E03 evidence content | Không có | Not Run | Dũng/B triển khai authenticated scope + no-store; mock chỉ dùng bytes giả |
| V01 verify, V02 reject, V03 request-changes | Không có B05 DTO/controller | Not Run | Dũng/B triển khai identity/version/reason/snapshot + audit/outbox; Minh chạy hai identity |
| D39/D40 và B04_CASE_WORKFLOW.md | Không có trong ZIP | Không áp dụng | Dũng/B cung cấp bản mới; Minh cập nhật contract comparison nếu có khác biệt |
| Case summary/detail projection chưa freeze | Chỉ shape mock/frontend | Not Run | Dũng/B + Minh thống nhất field list, consent/goods/dispute/chain projection |
| Human review và tích hợp | Chưa có xác nhận reviewer | Not Run | Minh phụ trách FE; B review auth/API, C review nghĩa chain status trước Done |

Probe localhost cũ trong sandbox từng bị socket permission từ chối. Kết quả mới hơn do root thực hiện sau khi có network permission là **ECONNREFUSED ở 8081**; báo cáo dùng bằng chứng mới này. Không suy từ source controller rằng HTTP đã thành công. Không có authorized real test accounts trong phiên, nên real mutation/session smoke là Not Run.

## Kết quả kiểm tra và giới hạn bàn giao

| Kiểm tra | Kết quả tại thời điểm soạn handoff | Bằng chứng hoặc việc còn lại |
|---|---|---|
| Mock API/session suite | PASS 20/20 tests | `node node_modules/tsx/dist/cli.mjs --test tests/api-session.test.ts`; có regression delayed 401 của session cũ |
| Real smoke syntax | PASS | `node --check scripts/smoke-real.mjs` |
| Real smoke execution | Not Run | `node scripts/smoke-real.mjs` in danh sách biến còn thiếu, exit 0; không thực hiện request |
| Real API readiness | Không sẵn sàng ở port đã kiểm tra | Latest root probe: ECONNREFUSED `127.0.0.1:8081` |
| TypeScript | PASS, không có lỗi | `node node_modules/typescript/bin/tsc --noEmit` và typecheck trong build |
| Production build | PASS, exit 0 | `node node_modules/next/dist/bin/next build`; 7 routes dynamic |
| Browser HTTP/UI smoke | PASS 9/9 trên dev và 9/9 trên production build | `node node_modules/@playwright/test/cli.js test`, Chrome headless; production port 3001, 30.4s |
| PostgreSQL atomicity/concurrency, Spring tests, Fuji | Not Run | Ngoài runtime đã có của F03/F04; cần backend/chain environment |
| Cross-owner review | Not Run | Giữ In Review |

Actual effort chưa được nhóm xác nhận, không tự thay estimate hoặc ngày hoàn thành kế hoạch. Bằng chứng kiểm tra được tóm tắt trong `VERIFICATION.json`; screenshots giao diện desktop/mobile được kiểm tra trực quan trong quá trình QA.

VERIFIED nghĩa quyết định nghiệp vụ approve đã được chấp nhận trong ứng dụng. PENDING_ON_CHAIN nghĩa bản ghi chain còn chờ, không phải transaction confirmed; NOT_REQUIRED ở reject/request changes nghĩa action đó không cần milestone chain trong contract hiện tại. Mock không tạo transaction hash hoặc chứng minh receipt/event Fuji. Việc hiển thị audit timeline và xác minh chain thật tiếp tục ở F05/C05.


## Các lệnh thực tế đã chạy và lỗi đã sửa

Môi trường không có npm trên PATH; đã tải npm CLI vào thư mục công cụ tạm và dùng Node gọi CLI. Dependency install thành công, package-lock pin phiên bản; kết quả audit tại lúc cài: 0 vulnerabilities. Lần thử pnpm trước đó bị Windows từ chối symlink nên bản bàn giao chỉ dùng npm/package-lock, không yêu cầu pnpm.

Các lệnh sau đã chạy trong frontend (biến đường dẫn chỉ dùng cho Chrome có sẵn trên máy kiểm thử):

```powershell
node node_modules/tsx/dist/cli.mjs --test tests/api-session.test.ts
node node_modules/typescript/bin/tsc --noEmit
node node_modules/next/dist/bin/next build
node node_modules/next/dist/bin/next start --hostname 127.0.0.1 --port 3001
$env:PLAYWRIGHT_CHROME_PATH = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:3001'
node node_modules/@playwright/test/cli.js test
node --check scripts/smoke-real.mjs
node scripts/smoke-real.mjs
```

Suite browser xác minh cookie HttpOnly/SameSite, localStorage trống, wrong password, navigation A/B, 201 hai item, HTTP 403 SELF_APPROVAL trực tiếp, review B, reason, VERSION_CONFLICT, 400 đúng item/focus, retry cùng body/key không tạo trùng, empty/error/expiry và bố cục 390px. Suite này dùng HTTP thật tới **mock Next route**, không tới Spring. Real script trả Not Run vì thiếu biến môi trường và tài khoản, không gửi mutation.

Build đầu tiên gặp EPERM khi Next tạo các thư mục prerender trên Windows. Root layout đã cấu hình render theo request (force-dynamic), phù hợp workspace có session; build cuối thành công và suite chạy lại trên bản production đạt 9/9. Browser smoke còn phát hiện Origin 127.0.0.1 khác URL localhost nội bộ Next; mock route đã kiểm tra theo Host của request, giữ CSRF. Đã sửa accessible name của need input để lỗi trong label không làm thay đổi tên trường. API client còn bỏ qua 401 đến muộn từ phiên trước để không xóa danh tính vừa login.

## Giới hạn đã biết

Mock dùng một process, mất dữ liệu khi restart, không mô phỏng transaction/locking PostgreSQL hoặc crash recovery. Idempotency trong UI chỉ giữ key/payload trong bộ nhớ khi ở form; khi đã rời trang, trước khi gửi lại cần tìm kết quả trong list/detail. Không dùng localStorage để giữ hồ sơ hoặc session. Chưa hỗ trợ edit/resubmit (slice sau), upload, campaign, audit timeline hoặc Fuji integration. Không tuyên bố các field projection mock đã được B04/B05 kiểm chứng.
