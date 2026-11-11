# F07 — G01/G02/G03 donor UI: triển khai và bàn giao

Người thực hiện: Khoa/Codex. Status: **In Review**. Đã chạy giao diện thật trong trình duyệt, sử dụng mock data; chưa tích hợp backend hoặc blockchain thật. Commit F01 cd5d88d38b971134e3a23ba5ba999b68e2711271 được giữ nguyên.

**Cập nhật 11/11:** G03 đã triển khai và nghiệm thu nội bộ ở mức mock, xem mục cuối tài liệu. Các counts/phạm vi ngày10 bên dưới là lịch sử; kết quả hiện hành là 17 unit +19 browser tests. F07 vẫn In Review, không đồng nghĩa backend/Next integration đã đạt.

## Phạm vi nghiệm thu

Phân công 10/11 yêu cầu G01 multi-item pledge + G02 own pledge history, lỗi400/409, mock nhất quán, không làm task Minh/Dũng. Khi hoàn thành ngày10, G03 chưa triển khai; ngày11 đã được bổ sung theo phân công mới. Dependencies tích hợp F06/B07 và review nhóm chưa đạt nên không đánh dấu Done. Không tự đổi owner/estimate/deadline hoặc status C01/F02/F01.

Đã fetch remote refs trước code: origin/main f676e41 (PR #4 merged), origin/minh d6efa49 không có frontend, origin/dung 84a042f chỉ C02. Checkout khoa ở cd5d88d sạch, chưa có frontend shell/shared components/styling/API client. Backend AuthService vẫn gọi toMeResponse(..., false); enum role vẫn ORG_ADMIN/VOLUNTEER/VERIFIER/DELIVERY_CONFIRMER. Không merge hay thay code của thành viên khác.

Lựa chọn triển khai: module React theo feature ở `frontend/features/donor/`, kèm **developer preview độc lập về tooling** ở `frontend/dev/donor-preview/`. Preview là harness để chạy/review module, không phải một production app/shell thứ hai và không thay Next.js App Router đã chọn. Không tạo login/session/nav toàn cục hoặc client HTTP dùng chung khác. Không có root frontend package.json; manifest/lockfile/dev dependencies nằm riêng trong harness để Minh có thể giữ shell của mình.

## File và trách nhiệm

| File / nhóm | Vai trò |
|---|---|
| frontend/features/donor/DonorApp.tsx | Client component G01/G02; nhận API adapter, capability, view/navigation từ host |
| frontend/features/donor/model.ts | Feature API interface, Page<T>, ProblemDetail error, local form validation; tái sử dụng DTO types F02 |
| frontend/features/donor/mock.ts | In-memory fake transport, baseline campaign S0 từ F02; atomic validation, replay journal, error injection |
| frontend/features/donor/donor.css | Styling có namespace ab-donor/ab-*; không global reset của shell |
| frontend/features/donor/model.test.ts | 4 unit tests cho validation, state, replay/unknown outcome, pagination |
| frontend/dev/donor-preview/ | React mount/router nhỏ dành dev, Vite, TS/ESLint, lockfile, Playwright E2E và preview controls |
| docs/planning/TASK_BACKLOG.md | Chỉ F07 chuyển Not Started -> In Progress -> In Review, ghi evidence/phần chưa làm |

## Chạy và mở giao diện

Đã kiểm chứng bằng Node 24.22.1, pnpm 11.25.0 trên Windows. Dependencies được pin exact trong package.json và pnpm-lock.yaml. Cài/chạy **trong** `D:/Project/AidBridge/frontend/dev/donor-preview`:

```powershell
pnpm install --frozen-lockfile
pnpm dev
```

Preview bind 127.0.0.1:5173, strictPort; không mở public network. Trong phiên triển khai đã để server chạy background ẩn (log tạm ở workspace Codex); nếu server dừng, chạy lệnh trên. Không ghi secrets/credentials vào repo. pnpm-workspace.yaml chỉ cho phép build script esbuild; không bật tất cả package scripts.

- G01: http://127.0.0.1:5173/campaigns/10000000-0000-4000-8000-000000000001
- G02: http://127.0.0.1:5173/me/commitments
- Root `/` mở G01. Navigation nội bộ giữ cùng in-memory mock state; reload/reset trả về S0 và history rỗng. Đây không phải dữ liệu đã persist ở DB/localStorage.
- `/?mode=real` cố ý hiển thị **Real API is not connected**, không fallback thành mock có vẻ đang gọi production. Harness không có real HTTP transport. Mode thật chỉ được host ứng dụng cung cấp adapter/session hợp lệ khi tích hợp.

Thử nhanh: check Rice + Drinking Water, nhập8kg/2box rồi Pledge goods; mở View my pledges. Campaign pledged từ6/1 lên14/3, received vẫn2/0, delivered1/0, confirmed0/0. G02 có đúng pledge vừa tạo, own received0/0 và outstanding8/2. Totals delivered/confirmed trên campaign là pool chung, được ghi nhãn tách biệt; không giả donor lot provenance.

Thanh developer controls chỉ nằm trong preview: chọn400,409,403, history-error/load-error, timeout-after-save; bật/tắt Mock donor enabled, Reset mock data. 400/409/timeout là fault một lần cho lần request kế tiếp; muốn lặp lại hãy chọn lại option (có thể chọn none rồi chọn lỗi). 403/read errors giữ đến khi đổi option. Không đưa thanh này vào production shell.

## G01 đã thực hiện

- Chỉ hiển thị public campaign title/summary/organization/approximateArea và per-item target/pledged/received/delivered/confirmed; không gọi private CaseDetail để lấy beneficiary alias hoặc caseId. Đây là thông tin nhu cầu mà API public cho phép, không tự thêm private fields.
- Chọn một/nhiều dòng, quantities nguyên1..1,000,000 và không vượt remainingPledgeQty, fixed units; không gửi unchecked/zero rows. Gửi campaignItemId, không contributionItemId.
- Loading, empty/read-only campaign, read error/retry, submitting, validation summary/field links, success, 400 VALIDATION_ERROR,409 QUANTITY_EXCEEDED,403 FORBIDDEN.
- Local validation không thay state. Mock command kiểm tra toàn bộ lines trước mutation; backend-like rejection không ghi quantity/history thành công. 409 refetch campaign, giữ draft và yêu cầu user sửa; fieldErrors ánh xạ theo body vừa gửi, không theo danh sách bị reorder sau refresh.
- Khóa double submit bằng ref; mỗi submission mới có UUID idempotency key. Response bị mất sau mock save giữ exact body/key, khóa chỉnh form và cho Retry same request; replay trả original record, không nhân đôi.
- Không suy campaign totals từ response donor; đọc lại campaign. Nếu refetch lỗi sau save vẫn nói pledge đã lưu và chặn pledge mới tới khi context được refresh.
- Keyboard focus về error summary, link tới quantity, success region; async status có text. Mobile390px dùng một cột và bảng có vùng scroll/focus riêng.

## G02 đã thực hiện

- Page response `{items,page,size,totalElements}`, loading/empty/error/retry/success, phân trang5 rows trong mock.
- Chỉ history của fictional donor hiện tại, không gửi actorId để đọc donor khác. Nhận own contribution quantities, status và blockchainStatus; không tự nâng PENDING_ON_CHAIN thành confirmed.
- G02 DTO hiện chỉ có campaignId/item IDs, chưa có case/public title/name/unit projection đầy đủ. UI dùng metadata public của campaign đang biết để enrich; với campaign khác hiển thị ID/Unit unavailable, không bịa case info. Cần Dũng chốt projection hoặc host cung cấp public metadata cho nhiều campaign trước tích hợp thật.
- Clear private history/draft/result khi donor capability false. Late pledge response sau thu hồi capability không hiện success/private reference. Không cache private history vào localStorage.

## Cách Minh tích hợp và Dũng tiếp nhận

1. Minh mount `DonorApp` ở App Router/client boundary và remount bằng `key={session.userId}` khi đổi identity; truyền `view`, `navigate`, `campaignId`, `mode="real"`, `donorEnabled` từ session thật. Không lấy donor permission từ staff role, không hardcode true trong host thật.
2. Truyền object thực thi `DonorApi`: getCampaign(id), getCommitments(page), pledge(id,body,key). Đây là feature interface, **không phải shared HTTP client mới**. Dùng client F03 của Minh để thực hiện P04/G02/G01 với same-origin cookie + CSRF + Idempotency-Key; convert response application/problem+json thành ApiError, network unknown giữ lỗi không phải business rejection. Backend vẫn là nơi enforce authorization.
3. DonorApp.tsx không import mock.ts hoặc JSON fixture runtime; mock factory chỉ được preview/tests import. Không bundle preview controls/mock donor vào production. CSS/React hooks có thể giữ nguyên; thay navigate callback bằng router.push. Không cần mang Vite, preview package hoặc root mount của harness vào Next shell.
4. Khi Minh có root frontend dependencies, React/type resolution do shell quản lý; alias/paths hiện có trong preview chỉ để resolve package cho module nằm ngoài harness. Không tạo Next app khác hoặc ghi đè package.json của Minh khi ghép.
5. Dũng xác nhận donorEnabled (hiện false), G01/G02 actual DTO/error/idempotency, metadata projection G02 và cách cấp donor capability. Roles dài trong F02 chỉ là mock actors; G01 quyết định bằng capability, không dùng chúng làm backend enum. Các OPEN cũ F02-Axx/F01-Axx không tự được đóng bởi F07.

## Nghiệm thu nội bộ theo phân công 10/11

| Tiêu chí | Kết quả / evidence |
|---|---|
| Giao diện pledge multi-item hoạt động | PASS mock runtime; E2E tạo8kg/2box rồi G02 thấy đúng record |
| Validation + HTTP400/409 rõ và không false success | PASS unit/E2E; 403 và revoked capability cũng đã kiểm tra |
| G02 own history và loading/empty/error/success | PASS E2E; pagination có unit test |
| Mock consistency/F02 DTO conventions | PASS runtime mock giữ bốn mốc riêng; dùng campaignItemId và UUID contribution IDs đúng shape |
| Desktop/mobile/keyboard cơ bản | PASS Edge headless1440px/390px, screenshots đã xem, không page overflow; chưa phải full accessibility audit |
| Dễ tích hợp, không sửa backend/Minh/F01 | Feature module + injected adapter; source phía khác không đổi; commit F01 giữ nguyên |
| Baseline F07 cancellation/live dependencies | Chưa thực hiện G03, F06/B07/live integration; không thuộc scope G01/G02 ngày10, không claim Done đầy đủ baseline |

## Lệnh kiểm tra và kết quả đã quan sát

Chạy trong frontend/dev/donor-preview:

```powershell
pnpm lint
pnpm typecheck
pnpm build
pnpm test
pnpm test:e2e
```

- **PASS lint:** ESLint10 + typescript-eslint recommended cho module và preview/tests, không bỏ qua module ngoài package.
- **PASS typecheck:** TypeScript5.9 strict/noEmit.
- **PASS build:** Vite7.3.7, exit0, 32 modules, bundle khoảng241KB raw/75KB gzip và CSS5.6KB. Vite có warning bỏ qua directive `use client` trong standalone bundle; directive được giữ để dùng với Next, không phải compiler contract/SSR proof.
- **PASS unit:** 4 tests (validation, multi-line totals/history/replay, atomic overflow+400/403, lost-response recovery/pagination).
- **PASS browser E2E:** 8 tests bằng Playwright + Edge headless: multi-item/history, local/server errors+focus, history states/campaign read error, lost-response recovery, donor disabled, desktop/mobile screenshots+real mode fail-closed, mobile403/success, keyboard field link/no /api requests, revoke-during-request. Một test gom nhiều assertions; không báo đây là kiểm thử backend.
- **PASS visual:** đã xem screenshots desktop1440/mobile390 do E2E tạo; mobile table scroll trong container, không page overflow. Ảnh nằm test-results/ bị gitignore; có thể tái tạo qua test:e2e.
- Trong quá trình debug có FAIL typecheck duplicate property, lint base path, preview JSX transform, encoding script ghi test. Đã sửa; kết quả cuối phía trên chạy lại pass, không che lỗi còn tồn tại. pnpm esbuild script cần allowBuilds riêng, đã cấu hình, không cài dependencies project khác.
- **Not Run:** real backend HTTP/session/CSRF, DB persistence/concurrency, Next integration/build, production deployment, G03 cancellation, Fuji/contract tests, full WCAG audit. Mock validation không chứng minh các phần này hoạt động.

E2E mặc định dùng Edge trên Windows để không tải browser mới; máy khác dùng Chromium sau khi cài browser Playwright tương ứng. Cấu hình theo process.platform; lệnh/browser engine ghi rõ khi chạy lại. Preview chỉ được build/chạy local, chưa deploy.

Status cuối: đạt nghiệm thu nội bộ phạm vi G01/G02 mock đã giao, sẵn sàng review/commit local riêng F07. Giữ **In Review** cho tới cross-owner review và dependency/integration gates; không push/PR hoặc làm task khác trong lượt này.

## QA độc lập sau commit F07 - 10/11/2026

Xem [báo cáo QA F01/F07](../reviews/F01_F07_QA_REVIEW.md): đã sửa 4 regression và field mapping409, kết quả mới nhất **7 unit +10 browser E2E PASS**, thay cho counts4/8 của ledger triển khai ban đầu. Test host race nằm riêng trong tests và không được import vào preview main/build. Adapter boundary và real integration vẫn OPEN; F07 In Review. Sửa QA chưa commit/push/PR.

## G03 — Hủy phần chưa được tiếp nhận (11/11/2026, kết quả hiện hành)

**Nghiệm thu nội bộ G03 PASS ở mức mock; F07 In Review.** Không sửa backend, contract, cấu hình Fuji hoặc ví. G03 nằm trong G02 `/me/commitments`: mỗi cam kết có outstanding dương có nút **Cancel unreceived goods**. Không thêm ứng dụng/router độc lập.

### Luồng và hợp đồng

- `CancellationForm.tsx` nhận commitment, public campaign metadata và injected `DonorApi`; chọn một/nhiều contribution item, số lượng nguyên1..1,000,000, không quá outstanding; nhập reason1..500 ký tự sau trim. Reason bắt buộc theo shape `CancellationRequest`; backend vẫn phải validate lại.
- `DonorApi.cancel(commitmentId, body, key)` tương ứng **POST `/api/commitments/{id}/cancellations`**, body `{items:[{contributionItemId,quantity}],reason}`, `Idempotency-Key`, response200 `CommitmentResponse`. Minh nối method này qua shared HTTP/session/CSRF client hiện có; không dùng campaignItemId thay contributionItemId.
- Mock kiểm tra tất cả lines trước khi thay đổi, dựa trên state mới nhất khi xử lý request. Giữ original `pledgedQty` và `receivedQty`; tăng `cancelledQty`, giảm `outstandingQty`. Campaign `pledgedQty` là lượng còn cam kết có hiệu lực, giảm đúng lượng hủy; `remainingPledgeQty` tăng tương ứng. Received/delivered/confirmed không thay đổi bởi cancellation.
- Status theo workflow: còn outstanding và chưa nhận gì -> `PLEDGED`; còn outstanding và đã nhận một phần -> `PARTIALLY_RECEIVED`; hết outstanding nhưng có received -> `RECEIVED`; hết outstanding và chưa nhận gì -> `CANCELLED`. Không che lịch sử bằng cách giảm pledgedQty gốc trên donor response. Chain status sau mock save vẫn `PENDING_ON_CHAIN`.
- Local invalid request không gửi mutation. Server400/403/409 không optimistic update. 409 hiển thị rõ **409 Conflict**, giữ draft/error và refetch history/campaign trước thao tác tiếp theo. Khi receipt thật trong mô phỏng xảy ra đồng thời, received có thể tăng do receipt đó; cancellation vẫn không thay cancelledQty hoặc giải phóng capacity sai.
- Ref khóa double submit; trong lúc gửi hoặc chưa rõ outcome, khóa đóng/chuyển màn hình/phân trang bằng controls trong module. Mất response giữ exact body/key, chỉ cho **Retry same cancellation**; replay được mock scope theo operation+resource+key, không hủy hay giải phóng capacity hai lần. Sau success refetch dữ liệu authoritative; read failure vẫn báo cancellation đã lưu và chặn mutation mới đến khi context đọc lại được.
- Capability bị thu hồi: form/history bị gỡ, response muộn không hiển thị private success. Host tiếp tục remount theo user identity như G01/G02. Mock in-memory không persist qua reload; đây không phải giải pháp durable recovery cho browser/DB production.

### Preview và fixtures

Trong preview đang có, bấm **Load partial receipt fixture** để dùng nguyên `goods-flow.mock.json.states.S2`: Rice8 pledged/5 received/3 outstanding, Water2/1/1. Nút này reset demo generation và mở history; không phải API production. Chọn cam kết, hủy Rice2 + Water1: history thành Rice8/2/5/1 và Water2/1/1/0 theo thứ tự pledged/cancelled/received/outstanding; campaign pledged12/2, received7/1 giữ nguyên. Hủy tiếp Rice1 -> `RECEIVED`, không phải `CANCELLED`.

**Mock receipt: receive remaining goods** giả lập receipt từ bên ngoài và báo host refetch history/campaign ngay, giữ form/draft. Nếu nhận hết hàng thì submit bị khóa; để thử race409, submit Rice3 trước rồi bấm receipt khi request còn pending. Cancellation bị từ chối, received8/outstanding0/cancelled0. Đây không phải triển khai R01 hoặc cấp quyền nhận hàng cho donor. Các fault400/403/409/timeout-after-save dùng lại controls sẵn có.

### Kiểm tra và giới hạn

Lệnh từ `frontend/dev/donor-preview`: `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test`, `pnpm test:e2e`.

| Kiểm tra | Kết quả |
|---|---|
| Lint / strict typecheck / preview build | PASS; không cài/nâng dependency hoặc đổi lockfile |
| Unit tests | **17/17 PASS**: 7 regression cũ +10 G03; validation, atomic rejection, 400/403/409, status arithmetic, same/different-key race, replay, lost response, receipt race, reset |
| Browser Edge headless | **19/19 PASS**: 10 regression G01/G02 +9 G03; success/refetch/capacity reuse, keyboard validation, 400/403/409, stale receipt conflict, unknown retry, mobile/double-submit, capability revoked |
| Network isolation | Browser tests xác nhận không phát `/api/` requests; mock mode không có HTTP/Fuji transport |
| Desktop/mobile | Screenshot trong `test-results/` bị ignore; form/table sử dụng được ở390px, không page overflow. Không phải full accessibility audit |
| `git diff --check` / references | PASS trước commit local |
| Backend HTTP/DB locks/concurrency, Next shell/session/CSRF integration | **NOT RUN**; mock scheduling trong JS không chứng minh PostgreSQL locking |

Vite cảnh báo bỏ qua `use client`/không resolve sourcemap khi bundle standalone; build exit0. Giữ directive cho client component khi Minh tích hợp Next; không coi preview build là Next build.

Minh tiếp nhận component/model/mock qua các file trong `frontend/features/donor`; production component không import mock. Chỉ preview/tests dùng fixture/control receipt. Dũng tiếp tục backend B07 cancellation/auth/quantity locks/idempotency; donorEnabled thật và shared client/session vẫn là điều kiện tích hợp, không được hardcode quyền donor. Chưa nhóm review/integration nên không Done hoặc đổi owner/estimate/lịch baseline.

Ghi chú cho PR cuối ngày: [C05 review](../reviews/C05_FUJI_RECEIPT_REVIEW.md) vẫn **PARTIAL** — B05 có outbox/integration tests, máy Khoa chưa có DB hợp lệ/job để đối chiếu, backend thiếu worker/adapter; proof CaseVerified Fuji độc lập PASS không phải B05 end-to-end. Phối hợp Dũng/Khoa về worker, org mapping/job thật/audit projection. Lượt G03 chỉ bổ sung một ghi chú làm rõ C05, không sửa backend hoặc chạy blockchain.


### Sửa receipt invalidation — 11/11/2026

Browser test tái hiện FAIL trước sửa: history còn received5/outstanding3 thay vì8/0. Mock trả DTO copies, nên mutation nội bộ không cập nhật state React; effects trước đây chỉ đọc lại khi view/page/refresh đổi. Preview nay tăng optional `dataRevision` sau receipt; DonorApp đưa revision vào dependencies đọc campaign/history, bỏ late responses qua effect cleanup. Không reload/đổi route/remount key hoặc xóa draft/pending. cancelTarget nhận history mới, form chặn mutation khi refresh và khóa hủy khi hết outstanding. Request đã gửi trước receipt vẫn bị mock latest-state validation từ chối409.

Hai regression mới kiểm tra My pledges cập nhật tại chỗ và form giữ quantity/reason nhưng nhận bounds mới. Test race đổi thứ tự submit -> receipt, không dựa vào UI giữ stale state. Kết quả cuối: lint/typecheck/build PASS,17 unit +19 browser Edge tests PASS; kiểm tra desktop1440 và screenshot `test-results/g03-receipt-refresh-desktop.png` (ignored). Warning Vite standalone use client như trước; build exit0. Không thay backend/dependencies/business rules. Optional revision mặc định0 là tín hiệu invalidation từ host, không polling hoặc mock import trong production component. Next/DB integration chưa chạy.
