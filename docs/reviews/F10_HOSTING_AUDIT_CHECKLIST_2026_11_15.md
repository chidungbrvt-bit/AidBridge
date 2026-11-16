# Checklist chuẩn bị F10 — 15/11/2026

**Cập nhật final audit:** bảng readiness bên dưới giữ evidence snapshot main9c24630 trước PR27. Trên candidate khoa/aee0a78+206d2f4 nay đã có C09/V16 và source fixes; gate không còn “chưa có code” mà là **runtime validation NOT RUN — DELEGATED**, proof cũ thiếu context fail-closed pending và frontend D49 integration chờ Minh. Không suy API live/hosting ready từ việc code có trong local. [Bảng năm task và validation handoff hiện hành](KHOA_2026_11_15_FINAL_HANDOFF.md) supersede các readiness blocker lịch sử, không triển khai F10/scanner trong lượt này.

Owner F10 Minh; Dũng hỗ trợ API/security/DB, Khoa chain/proof/recovery. **Checklist design only, NOT RUN; không triển khai hosting hoặc UI.** Nguồn: TASK_BACKLOG F10 Public hosting/recovery (dependencies F09/C10, HTTPS/CSRF/private files/private DB, backup/rollback, forecast dưới$20, deploy chỉ khi được phép), F09/C09/C10, D49/D56, [scanner boundary](C08_RECEIPT_LOG_REVIEW.md#task10-ngày15112026--hợp-đồng-thiết-kế-scannercursor).

## A. Dữ liệu audit hiện có và chưa có

Main9c24630/dung0fc3e3d hiện chỉ có audit entity/repository/LocalAuditService; chưa có controller T01/T02 hoặc public audit route. PR25 description nói có API audit không thay thế source evidence. Không xây F10 dựa vào DTO được mô tả nhưng chưa được implement. `frontend/src/components/audit/audit-timeline.tsx` là fixture rendering, không proof/live projection.

| Dữ liệu | Có thể dùng hiện tại | Quy tắc khi ghép API thật |
|---|---|---|
| Business/evidence decision | Response nghiệp vụ (VERIFY/DELIVERED/FAILED), không phải live chain read | Tách axes, không mất VERIFIED vì RPC lỗi, không biến receipt chain thành chứng minh hàng đã giao vật lý |
| Receipt/delivery ID và trạng thái lúc tạo | B08/B09 response có IDs/auditEventId và chain status snapshot | Không suy response PENDING_ON_CHAIN là job đã gửi; FAILED delivery là NOT_APPLICABLE, không phải TX_REVERTED |
| txHash/blockNumber/explorerUrl | Proof JSON C05 có historical CaseVerified; API live chưa chứng minh cung cấp | Chỉ render khi DTO actual có; không tự bịa từ auditEventId; không gắn pilot độc lập vào job B05 khác |
| chainId/registry/event/sequence/logIndex/canonical block/verification time | Job/attempt/proof có một số fields server-side; public DTO chưa chốt/triển khai | Hiển thị khi API sanitized trả đúng deployment; thiếu field hiển thị unavailable, không lấy mặc địnhC06/C03 toàn cục để điền |
| scanner progress/finality/verifiedAt | Chưa có scanner hoặc API projection | Không thiết kế UI coi fields này tồn tại; nếu API bổ sung sau phải chốt DTO, không suy receipt polling PASS là scanner caught-up |

`F05_AUDIT_STATUS_EXPECTATIONS.md` có bảng wording hữu ích nhưng các đoạn “chưa có worker/Fuji” và timeout→FAILED sau5 lần là lịch sử; timeout policy hiện hành theo C07/C08 vẫn pending/attention. Dùng evidence B05 thực tế và các mục NOT_VERIFIED của báo cáo, không chạy lại/ghi lại bằng chứng trong Task10.

## B. Checklist trạng thái và liên kết — chuẩn bị cho Minh

- [ ] PENDING/SUBMITTED hoặc RPC timeout: “Awaiting on-chain verification”, hash nếu API có không làm badge confirmed. Missing proof/unknown status/API unavailable: unavailable/unverified, không default confirmed và không đánh thành business failure.
- [ ] CONFIRMED_ON_CHAIN chỉ từ server verified projection đủ chain/registry/receipt/event/canonical policy; ghi Fuji testnet, operation và verified timestamp nếu API có. Thiếu proof/context hoặc reorg/discrepancy không tiếp tục hiện anchored badge từ cache cũ.
- [ ] FAILED_ON_CHAIN chỉ báo lỗi sync; REVERTED chỉ khi receipt status0 đúng context. Mismatch không hiển thị public success explorer link; FAILED delivery khác revert và có zero chain job. Không tự decode/phán canonical trong browser.
- [ ] Tách business/evidence/chain statuses; “Record integrity anchored” không phải “Delivery physically proven”. Đổi số lượng chỉ theo backend nghiệp vụ, không theo replay log hoặc badge.
- [ ] explorerUrl chỉ khi có hash thật, chain43113, registry/context phù hợp, URL host/network được allowlist và field thực có. Không mainnet link, không link cho mock hash/mismatch. Link pending/reverted nếu API hỗ trợ phải ghi đúng tình trạng, không ngầm confirmed.
- [ ] auditUrl chuẩn D49/D56 `/api/public/cases/{publicCaseRef}/audit` (0x+64 lowercase hex), **chưa bật vì endpoint chưa có**. [Audit URI evidence/owner patch guide](AUDIT_URL_ALIGNMENT_2026_11_15.md) xử lý Minh0595149 đang viện dẫn D57 chưa có Decision. Không tạo endpoint thứ2 để dung hòa. Dũng/Minh đồng bộ actual contract trước khi bật link.
- [ ] Empty/error/loading/retry đọc API và stale proof phải rõ; không trộn fixture fallback với dữ liệu real cùng nhãn. Scanner chưa triển khai có thể vẫn có receipt proof hợp lệ nhưng phải ghi đúng coverage/reconciliation chưa kiểm, không tuyên bố “all events recovered”.
- [ ] Public/internal projection khác quyền/fields: public không actor identity/PII/salt/manifest/private evidence/consent, không exposure private links. F09 independent confirmer/self403/dispute guards giữ ở backend, không thay bằng badge.

## C. Gate hosting/recovery theo F10 thực tế (checklist chuẩn bị, không deploy)

- [ ] Minh/Dũng kiểm F09/API readiness, Khoa kiểm C10 evidence và giới hạn coverage; nếu chỉ CASE_VERIFIED thì ghi goods chưa verified, không tự hạ scope P0/acceptance. B09 thiếu V16 là gate environment cần sửa trước chứng minh lifecycle deployable; xem [review V16](B09_DELIVERY_REF_REVIEW_2026_11_15.md).
- [ ] Có phê duyệt public deploy riêng, provider eligibility và forecast dưới$20 (runtime/DB/storage/egress/overage), owner theo dõi/dừng; không tự mua dịch vụ. Domain/HTTPS/cookie Secure+HttpOnly/CSRF/same-origin proxy được smoke-test trên deployment thật, không suy localhost PASS thay hosting.
- [ ] DB private network/credentials server-side; private files chỉ authenticated authorized route; CORS/cookies/file guards test allowed/forbidden, không public debug/fixture controls/secrets. Không mở DB hoặc deploy mock như sản phẩm thật.
- [ ] Backup/restore/rollback trước release; pin code/schema/ABI/deployment descriptor. DB backup giữ encrypted tx journal/AES key theo custody riêng, không đưa keys vào UI/log/report. Schema rollback không phá append-only facts; không restore DB rồi reset nonce/journal dưới signer có transaction thật.
- [ ] Rehearsal restart/recovery của worker/scanner theo deployment và cutover plan, không replay quantities hoặc resend signed bytes sang registry khác; operator gate cho orphan/unmatched/reorg. Bằng chứng C03 giữ nguyên khi có C06.
- [ ] Record health/smoke/cost/recovery evidence và actual field coverage sau triển khai; ở checklist hiện tại tất cả vẫn NOT RUN, F10 chưa Done. Không thực hiện public deploy chỉ để đánh dấu checklist.

## D. Acceptance đề xuất và ownership

Minh kiểm status/error/empty/keyboard/responsive và public/internal privacy bằng HTTP fixtures trước; Dũng kiểm DTO/authorization/DB projections; Khoa kiểm actual chain/context/receipt/reorg/scanner coverage. Bộ cases scanner nằm trong C08 Task10: crash giữa batch, timeout/duplicate/truncated logs, reorg/removed/remine, wrong context, replay, VOID pair, dual-registry cutover. Frontend cases tối thiểu: hash-only không confirmed; missing/mismatch proof không anchored; API error không “success”; D01 FAILED NOT_APPLICABLE; scanner lag được diễn đạt đúng; link canonical nhưng endpoint absent vẫn disabled.

Tất cả là **tests ĐỀ XUẤT/NOT RUN**, không tuyên bố bộ UI/backend/hosting mới PASS. Task8 audit API đối chiếu thật còn cần API đã bàn giao và DB/Fuji mapping; triển khai public hosting F10 cần F09/C10, recovery/security/cost gates và authorization. Task12 chỉ chuẩn bị phối hợp như phần E, không thực hiện hosting.

## E. Task12 — Handoff phối hợp cho Minh,15/11/2026

**DONE WITH DEPENDENCIES cho phần chuẩn bị của Khoa, không phải F10 Done.** Fetch main9c24630/dung0fc3e3d/minh và minh-d010595149, giữ local commits. Không chạy lại Tasks2/3/8/10. Tái sử dụng [C09 readiness](C09_AUDIT_API_READINESS_2026_11_15.md), [V16 finding](B09_DELIVERY_REF_REVIEW_2026_11_15.md), [auditUrl alignment](AUDIT_URL_ALIGNMENT_2026_11_15.md) và scanner design ở C08. Chưa sửa implementation, chạy build/tests/CI/RPC hoặc chọn/mua/deploy provider.

### Trạng thái implementation và việc có thể làm ngay

| Hạng mục | Readiness | Evidence/owner | Điều kiện hoàn thành |
|---|---|---|---|
| Audit UI fixture + status wording | READY FOR MOCK | main `frontend/src/app/(workspace)/audit-preview/page.tsx`, `components/audit/audit-timeline.tsx`; Minh | Có thể chuẩn bị loading/empty/error/unknown và mock proofs tách nhãn. AuditExample hiện chỉ3 status, source/occurredAt và proof hash/block/address; không tự nhận có live sequence/eventName/canonical fields. |
| R01/D01 frontend | READY FOR MOCK/HTTP-contract preparation, chưa release | Minh0595149 có `receipt-panel.tsx`, `delivery-panel.tsx`; chưa merge main; B08/B09 backend code có | Minh giữ một bản UI, nhận IDs từ API thật, cùng CSRF/key qua retry; actual backend DB gate V16 và E2E R01/D01 còn phải đối chiếu. Không phục hồi UI Khoa đã rút ở PR24. |
| F09 D02/S01/S03 | READY FOR MOCK | Minh `goods-mock/delivery-review.ts`, handoff F08/F09; B10 thật chưa bàn giao trong refs đã đọc | D01 DELIVERED response.id→deliveryId, recorderId/evidenceVersionIds chính snapshot cho confirmation độc lập; không truyền receiptId/campaignItemId thay deliveryId. Sau mutation refetch backend, không tự tăng confirmed/chain badge. |
| Public/internal audit live | BLOCKED | C09 report; Dũng implementation, Minh consumer, Khoa proof review | T01/T02 controller/query/DTO/tests chưa có trên refs. URI D49 đúng không đồng nghĩa endpoint tồn tại. Không bật link hoặc giả contract mới trong production. |
| Full goods proof/C10 và scanner coverage | BLOCKED | Khoa semantics/C10; Dũng worker/DB theo D56; scanner policy còn PROPOSED | V16/worker nhiều operations/C06 registry và proof thật; scanner chưa triển khai. CaseVerified C03 historical proof chỉ chứng minh slice đó, không goods/delivery. |
| Runtime/proxy configuration plan | READY (đặc tả source, chưa smoke trên hosting) | next.config.ts/package.json/application.properties; Minh +Dũng | Chọn target runtime/network/HTTPS, test cookies/proxy trên target, không mặc định localhost settings chạy trên public host. |
| Public hosting, cost và recovery | BLOCKED để deploy; READY để chuẩn bị checklist | F10 backlog: Minh primary, Dũng API/DB/security, Khoa chain/recovery | F09/C10 gates, provider eligibility/budget, backup/rollback evidence và explicit deploy permission riêng. Không đánh F10 Testing/Done vì mới có checklist. |

### Boundary dữ liệu cho audit/UI

- Route `/audit-preview` nằm trong workspace shell, không phải public audit API hoặc public audit webpage đã triển khai. `auditUrl` D49 là **API URI**, không mặc định là trang Next có thể điều hướng tới. Minh có thể chuẩn bị presentation riêng bằng fixture rõ nhãn nhưng chưa tạo endpoint/client contract mới ở Task12.
- Public audience chỉ sanitized data của case/campaign được public; internal staff API cần session và tenant authorization. Không dùng SessionProvider workspace làm bằng chứng public route hoạt động, không render raw AuditEvent chứa payload/identity. Không dùng UUID/auditEventId làm public ref hoặc đưa evidenceVersionIds/private evidence ra public timeline.
- Field availability phải theo DTO **actual**: ghi API chưa có khi chainId/registry/operation/sequence/hash/blockHash/logIndex/verifiedAt thiếu; frontend không fill bằng global registry/chain defaults để làm proof. Ba status trong AuditExample chỉ là fixture type, không phải enum API đủ mọi UNKNOWN/REVERTED/error. Khi ghép live cần explicit adapter + unknown fallback theo actual DTO, không ép enum có3 giá trị rồi default CONFIRMED.
- F09 DELIVERED/PENDING review và PENDING_ON_CHAIN là hai axes; response201 không phải independent confirmation hay mined proof. FAILED là NOT_APPLICABLE, không REVERTED. Receipt/log/canonical verification server-side; browser chỉ trình bày verified projection, không suy từ DB job/hash. Không có proof thì unverified/pending; API lỗi thì unavailable, không fallback fixture dưới nhãn real.
- `auditUrl` chỉ giữ URI D49 hợp lệ và nút/link disabled đến endpoint chạy thật. Issue D57 trên0595149 đã có owner patch guide; không tự sửa nhánh Minh hoặc tạo endpoint campaign thứ2. Explorer link là loại khác auditUrl: chỉ từ trusted sanitized response/allowlist Fuji đúng registry/hash/network, mismatch không public success link. Historical proof ghi đúng deployment C03, không ghép cho goods C06.

### Runtime/env/security plan — không có giá trị secrets hoặc cấu hình triển khai mới

| Cấu hình đã có | Cách chuẩn bị/giới hạn |
|---|---|
| `frontend/package.json` Node>=22.13.0, Next build/runtime scripts | Dùng đúng lockfile/runtime hiện có; production là build rồi start, không `next dev`. Script hiện bind127.0.0.1; nếu provider/container cần listen interface khác, Minh phải chọn start command/port được provider hỗ trợ khi được phép triển khai. Không sửa bind hoặc mở port ở lượt này. |
| `NEXT_PUBLIC_API_MODE` | real cho demo tích hợp, mock chỉ demo fixture được ghi rõ. Giá trị public/build-time không chứa secret; không bật mock controls trên bản product public. Rebuild đúng mode, không hứa đổi runtime env sẽ đổi browser bundle đã build. |
| `API_INTERNAL_BASE_URL` | Server-side Next rewrite `/api/*` tới Spring origin, mặc định localhost8081; trên hosting phải là origin backend được phép reachable từ Next. Origin không username/password/path/query/hash; không đưa credentials vào URL hoặc NEXT_PUBLIC variables. Không dùng localhost mặc định khi backend ở máy/container khác. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Dũng cấu hình private DB và secret manager/environment của runtime; không ghi giá trị vào docs/source/client logs. Flyway validate/checksum, ddl-auto validate, clean-disabled giữ nguyên; gate V16 trước start tương ứng source B09. |
| `SESSION_COOKIE_SECURE` | Backend defaulttrue, JSESSIONID HttpOnly/SameSite=Lax; HTTPS thật và reverse proxy forwarded origin/cookie behavior phải smoke-test. Không đổi secure=false cho public host chỉ vì login lỗi. Shared CSRF lấy từ `/api/auth/csrf`, giữ token/header/session cùng origin và Idempotency-Key cho mutation. |
| CORS/public access | Thiết kế hiện tại same-origin API proxy, không cần tự mở credentialed wildcard CORS. Nếu target khác topology thì phải chốt lại với Dũng, không thay architecture hoặc bypass CSRF trong UI. |
| `BLOCKCHAIN_ENABLED`/`BLOCKCHAIN_*` | Worker mặc định tắt, registry/signer/cipher secrets chỉ server-side theo custody. F10 UI/hosting không tự bật worker, không đổi registry hoặc đọc khóa. Mọi rollout/cutover tách permission theo D56. |

Health: backend có `/actuator/health` public, details/components never, port8081 trong source. Next rewrite chỉ `/api/*`, nên không đoán `/api/actuator/health` là proxy health hoặc tạo health endpoint giả. Minh/Dũng chọn health target trực tiếp private backend/Next route hiện có trên target runtime, kiểm healthy/unhealthy/readiness thật khi deploy; không expose DB details/stacktrace. Không có infra deployment config trong checkout, chưa có host được chọn hoặc smoke build/HTTPS mới.

Budget: chưa chọn provider và chưa tra giá/eligibility; **không có ước tính giá thực tế được xác minh**. Đề xuất chỉ là trần phân bổ để lập forecast: frontend3 +backend6 +DB5 +backup/egress2 +reserve3 =19 USD/tháng; không phải báo giá hay phê duyệt chi tiêu. Minh/Dũng phải lấy giá hiện hành/limitations theo usage thực (compute/storage/egress/builds/backup, quota và overage/tax) rồi giữ tổng dưới20; không đủ thì chốt lại với nhóm trước deploy. Không giả định free tier đủ P0 hoặc tài khoản đủ điều kiện.

Recovery: backup DB theo policy target, test restore trong môi trường riêng và pin compatible app/schema release. Giữ encrypted signed journal/AES custody tách source, không restore snapshot cũ rồi ký nonce mới dưới ví đang có tx. App rollback không rollback on-chain history hoặc xóa business facts; incompatible schema/backfill cần gate Dũng và data recovery plan riêng. Worker/scanner paused/reconcile trước resume, API trả unavailable khi proof/context không đáng tin. Chưa chạy restore/drill, chưa tạo backup hoặc truy cập secrets.

### Bàn giao ngắn cho Minh (chưa gửi ra ngoài)

Khoa đã chuẩn bị F10 gates trong tài liệu này. Minh có thể làm presentation/loading/error/unknown và fixture audit ngay, nhưng `/audit-preview` chưa phải public API. R01/D01 dùng bản UI của Minh; F09 giữ id/recorderId/evidenceVersionIds từ DELIVERED response và không đồng nhất review với chain status. Chờ Dũng bàn giao T01/T02 actual DTO +V16; URI theo D49 và link disabled đến khi route chạy. C10 goods/scanner/C06 evidence và public deploy permission vẫn là gate riêng. Khi chọn hosting kiểm Node/runtime, same-origin rewrite, HTTPS cookie/CSRF, private DB/files, health, forecast dưới20 và backup/rollback; không tự bật worker hoặc đổi registry.

Task12 hoàn thành phần phối hợp: chỉ đọc source và cập nhật docs; tests/build/HTTP/DB/hosting/restore/CI/Fuji mới đều **NOT RUN**. Không chuyển các hàng BLOCKED thành PASS bằng fixture. F10 giữ Not Started theo implementation baseline; checklist/handoff ready không thay nghiệm thu F09/C10 hoặc cấp phép public deploy.


## F. Kết quả Minh ngày 16/11/2026

Sau `git fetch origin`, `main` ở `ed19bdf` (PR #29 đã merge) và `minh` ở `647a68f`. `git merge --no-edit origin/main` báo `Already up to date`. Căn cứ cho D49 là API trên `main` và kết quả HTTP bên dưới. Các mục A–E ghi lại mốc chuẩn bị ngày 15/11.

PR #30 (`dung` ở `ef30487`) còn mở. F09 chờ merge PR #30 và nghiệm thu E2E với B10 sau merge. Phần kiểm tra local dưới đây dùng mã `647a68f`, chứa B10 candidate, PostgreSQL dữ liệu giả lập và worker tắt. ML-4 đang hoãn theo P0, rà lại tại mốc 17/11 theo D51.

### B. Trạng thái và liên kết

| Mục | Kết quả ngày 16/11 | Bằng chứng và phần còn chờ |
|---|---|---|
| B1. Pending và proof thiếu | Pass fixture | Playwright T01 kiểm pending và thiếu blockNumber vẫn không hiện confirmed, trình duyệt hiện trạng thái proof unavailable. Timeout RPC thật chưa chạy. |
| B2. Confirmed | Pass fixture | T01 hiện hash, block và link Fuji với projection đầy đủ. Receipt, reorg và timestamp xác minh trên Fuji chưa kiểm ở lượt này. |
| B3. Failed và mismatch | Pass fixture | UI hiện sync failed và bỏ link với `EVENT_MISMATCH`. D01 FAILED giữ tồn trong bài test delivery. Receipt REVERTED chạy thật chưa kiểm. |
| B4. Tách trạng thái | Pass fixture | `campaign-audit.spec.ts` kiểm riêng business, evidence và chain. Fixture D01 kiểm refresh tồn sau conflict và replay giữ key. |
| B5. Explorer | Pass fixture | T01 mở link Fuji hợp lệ từ DTO. T02 bỏ link khi URL trỏ host khác. Link từ receipt thật chờ bằng chứng Khoa. |
| B6. D49 | Pass local HTTP | API công khai `/api/public/cases/{publicCaseRef}/audit` trả 200 ẩn danh cho hồ sơ giả lập có campaign công bố. UI fixture chỉ hiện liên kết chia sẻ sau khi API đọc thành công. |
| B7. Lỗi, retry và phân trang | Pass fixture một phần | T01 phân trang đến trang cuối, T02 hiện lỗi 404 rồi retry thành công. Coverage scanner và stale proof chưa kiểm runtime. |
| B8. Public, nội bộ và evidence | Pass local HTTP | T02 có danh sách trường công khai, thiếu header CORS cấp quyền cho origin ngoài. T01 và evidence trả 401 khi ẩn danh, 200 đúng tổ chức và 404 khác tổ chức. Tệp riêng có `Cache-Control: no-store`. Guards xác nhận/tranh chấp của F09 chờ E2E B10. |

### C. Hosting và recovery

| Mục | Kết quả ngày 16/11 | Bằng chứng và phần còn chờ |
|---|---|---|
| C1. F09 và C10 | Blocked | PR #30 chưa vào `main`. C10 chờ worker/verifier, Hardhat và bằng chứng Fuji của Khoa. V18 chạy được trên DB phục hồi local. |
| C2. HTTPS, cookie, CSRF và chi phí | Pass local HTTPS, hosting chưa chạy | Next production real mode qua proxy loopback dùng chứng chỉ test. `JSESSIONID` có Secure, HttpOnly, SameSite=Lax và đổi ID sau login. POST thiếu CSRF trả 403, login và `/me` trả 200, logout 204 rồi `/me` trả 401. Chưa có phê duyệt deploy, domain, nhà cung cấp hay forecast dưới $20/tháng đã kiểm. |
| C3. DB riêng và tệp riêng | Pass local một phần | PostgreSQL, Spring, Next và proxy chỉ bind loopback. Quyền T01/evidence đã thử với hai tài khoản giả lập khác tổ chức. Private network, secret store và các header trên hosting chưa chạy. |
| C4. Backup, restore và rollback | Backup/restore pass local | `pg_dump --format=custom --schema=<schema-test>` tạo 326.784 byte trong 0,65 giây. `pg_restore --no-owner --exit-on-error` vào DB mới mất 1,58 giây. So khớp số dòng và fingerprint nội dung 12 bảng, gồm 24 deliveries, 17 confirmations, 17 disputes, 251 audit events và 129 jobs. Spring khởi động từ bản restore, Flyway validate 19 migration đến V18, health UP. Diễn tập bổ sung cho thấy rollback về B09 sau V18 bị chặn vì sai số lượng P04. Chạy lại B10 khôi phục đúng kết quả. Chi tiết ở mục G. Rollback public và phục hồi signer thật chưa chạy. |
| C5. Worker/scanner recovery | Not Run | Bàn giao Khoa đối chiếu deployment/receipt, restart và reconciliation cùng Dũng khi có C10. Worker local tắt, chưa kiểm replay hoặc Fuji. |
| C6. Health và smoke sau deploy | Pass local, hosting chưa chạy | `GET /actuator/health` trả UP, ẩn components/details. `/login` và HTTPS API chạy qua Next production local. Chi phí, health, smoke và rollback trên môi trường public chưa chạy. |

Lệnh kiểm frontend và kết quả trên `647a68f`:

- `npm test`: 77 pass, 0 fail, 0 skip. `npm run typecheck`: pass.
- `node node_modules/next/dist/bin/next build` với `NEXT_PUBLIC_API_MODE=real`: pass bằng Turbopack trên bản sao local có cấu hình workspace root để đọc fixture F02. `npm run build` (Webpack mặc định) pass trên thư mục `.next` sạch. Lượt đầu dùng lại thư mục build cũ gặp lỗi Windows `EPERM`; đã tách thư mục cũ và chạy lại với cấu hình nguyên gốc. Smoke HTTPS dùng bản build Turbopack đã chạy trước đó.
- Playwright `--project=audit-api --project=delivery-api`: 6 pass, 0 fail, 0 skip. Các test này dùng HTTP fixtures.
- Playwright `--project=mock campaign-audit.spec.ts --output=test-results-f10-mock`: 2 pass, 0 fail, 0 skip. Lượt đầu hai phiên Playwright đụng thư mục trace, đã tách thư mục kết quả và chạy lại đạt.
- Backend candidate `mvn -o -DskipTests package`: build pass. Smoke trên DB restore gồm cookie/CSRF/session, audit, evidence và phân quyền. Lượt đầu thiếu cấu hình allowlist evidence giả lập nên trả 404. Sau khi cấp quyền cho đúng tổ chức test trong cấu hình chạy local, smoke pass.
- F09 E2E real sau merge B10, public deployment smoke, rollback release, và proof/recovery Fuji: **Not Run**.

Bàn giao Khoa dùng B2, B3, B5 và C5 để đối chiếu receipt/recovery Fuji. Dũng tiếp tục PR #30 và xác nhận runtime DB của bản demo. Minh ghép F09 native B10 khi PR #30 vào `main`, dùng `delivery.id` và snapshot `evidenceVersionIds` từ D01, kiểm hai tài khoản độc lập, self-confirm 403, dispute chặn close rồi resolve và refresh T01/T02. F10 ở **In Progress**.

## G. Diễn tập rollback F10 và bàn giao cuối ngày 16/11/2026

**#2:** `main` trên GitHub ở `ed19bdf`. Merge trên `minh` báo `Already up to date`. Bản ghi test frontend và hai tài liệu đã lên `minh` ở `b551d86`. PR #31 (`minh → dung`) đang mở và GitHub báo không có xung đột với base. [PR #31](https://github.com/chidungbrvt-bit/AidBridge/pull/31)

**#9 · B10:** Báo cáo của Dũng trên `ef30487` ghi Surefire 146 run, 0 fail/error/skip và Failsafe 205 run, 0 fail/error, 2 skip opt-in. ReceiptApiIT 5 và DeliveryRefMigrationIT 9 đều không lỗi và không skip. Tại thời điểm diễn tập rollback ở mục G, PR #30 còn mở. PR #31 đang chờ review cho API đọc ngữ cảnh tranh chấp và `reviewCampaign`. Kết quả cập nhật sau merge ở mục H. [PR #30 và báo cáo kết quả](https://github.com/chidungbrvt-bit/AidBridge/pull/30)

Sau khi B10 và API đọc liên quan vào `main`, chạy với hai phiên độc lập cho VOLUNTEER và DELIVERY_CONFIRMER, cùng các tài khoản seed ORG_ADMIN, PLATFORM_ADMIN và DONOR cần cho từng bước. Dùng R01 → D01, lấy delivery ID và snapshot evidence từ phản hồi D01, kiểm D02 tự xác nhận trả `403 SELF_CONFIRMATION`. Kiểm S01 chặn P05 khi mở tranh chấp, rồi thử S03 `UPHOLD` và `VOID_DELIVERY` trên các hồ sơ riêng. Đọc lại C03/P04 và T01/T02 sau từng thay đổi. P05 lấy ID từ `reviewCampaign` và chỉ hoàn tất khi đủ confirmed totals và hết tranh chấp.

**#10 · Backup và rollback:** Dùng PostgreSQL 16.16 trên loopback, DB giả lập, worker tắt. Restore riêng từ `pg_dump --format=custom` và `pg_restore --no-owner --exit-on-error`. Lượt này tạo bản dump 326.784 byte trong 0,61 giây và restore trong 1,42 giây. Probe HTTPS, CSRF, T01/T02 và evidence chạy lại đều pass. Frontend build từ nhánh `minh` giữ nguyên trong ba lần đổi backend.

| Bước | Kết quả đã quan sát |
|---|---|
| Build backend trên `main@ed19bdf` | Fail khi chạy `mvn -o -B -ntp -Dmaven.test.skip=true package`. `AuditTrailService.java:102` truyền `String` vào `Hashing.sha256(byte[])`. Dũng đã sửa ở `b976a29` trong PR #30. |
| Khởi động bản đối chứng trước B10 | Áp riêng thay đổi mã nguồn T02 từ `b976a29` vào bản sao `ed19bdf`. Build pass. Backend cũ khởi động trên cùng bản restore V18, nhưng rollback **Blocked** vì P04 tính sai tổng với delivery đã void. Cùng một campaign giả lập, `b551d86` trả tổng delivered/confirmed là **3/3**, bản đối chứng trả **8/0**. T02 cho cùng hồ sơ khớp phản hồi giữa hai bản. |
| Chạy lại backend B10 ở `b551d86` | Pass local. P04 và T02 trở về đúng kết quả trước diễn tập. So khớp số dòng và fingerprint nội dung ở 12 bảng trước và sau khi chuyển phiên bản. Không hạ schema hoặc sửa dữ liệu nghiệp vụ. |

Khi DB đã có dữ liệu B10, bản B09 trong diễn tập không an toàn để phục vụ API nghiệp vụ. Dũng cần xác nhận phiên bản tương thích schema và kết quả P04 trước khi rollback trên môi trường demo. Khoa tiếp tục đối chiếu replay/recovery Fuji khi có bằng chứng C10. Public deploy, thao tác trên DB demo, phục hồi signer/worker và link từ proof chain thật vẫn chưa được kiểm trong lượt này. F10 **In Progress**. ML-4 tiếp tục tạm hoãn để ưu tiên F09/F10.

## H. F09 sau khi PR #30 merge

PR #30 đã vào `main` tại `15bcba4`. Nhánh `minh` đã gộp `main@627be56` và `dung@f85d390`. Phần `ml/` đã được bỏ khỏi diff PR #31, bản thay đổi ML vẫn có trong lịch sử `647a68f`.

F09 dùng C03 để đọc deliveries, disputes và tổng theo mặt hàng. `reviewCampaign.id` giữ được ID khi campaign là FULFILLED hoặc CLOSED, còn `campaignId` phục vụ form D01. D02 tải snapshot của đúng lần giao và chặn người ghi tự xác nhận. S01 chặn P05 khi dispute OPEN. S03 dùng màn riêng cho PLATFORM_ADMIN, chỉ mở và tải evidence qua route đọc theo dispute, rồi cập nhật totals từ C03/P04 khi tải lại hồ sơ.

Chạy trên PostgreSQL 16.16 ở loopback với schema test đến V18, Next production Webpack ở chế độ real, JDK 21 và Playwright Chromium. Worker chain tắt. Dùng tài khoản VOLUNTEER và DELIVERY_CONFIRMER riêng, cùng các tài khoản ORG_ADMIN, PLATFORM_ADMIN và DONOR giả lập do fixture tạo.

```powershell
# Bật AIDBRIDGE_F07_REAL_E2E=true và CI=true trên DB test riêng.
# Dùng AIDBRIDGE_TEST_DATABASE_* và PG* như workflow F07 real backend E2E.
cd frontend
npm test
npm run typecheck
cd ../backend/aidbridge-backend
mvn -B -ntp "-Dtest=CommitmentRequestValidationTest,CancellationRequestValidationTest" "-Dit.test=DonorUiE2EIT" verify
```

`DonorUiE2EIT` build Next rồi chạy `node node_modules/@playwright/test/cli.js test --project=donor-real --reporter=list,junit`. Trên Windows local dùng thêm `-DargLine=-javaagent:<mockito-core.jar>` để tránh dynamic attach bị Windows chặn. Chạy riêng `ReceiptApiIT,DeliveryApiIT,DeliveryConfirmationIT,CampaignCloseIT,DisputeResolveIT,StaffCaseReviewIT` khi cần số liệu HTTP/DB ngoài browser.

**SHA đã chạy browser:** `5815d715169f9238741cd70f2ee318295462104d`. Playwright real **6 pass, 0 fail, 0 skip** (40,8 giây). `DonorUiE2EIT` **1 pass, 0 fail, 0 error, 0 skip**. Maven verify, `npm test` (77 pass, 0 fail/skip), typecheck và build Webpack đều pass trong lượt cuối.

| Bước F09 | Kết quả |
|---|---|
| R01 → D01 | 201/201 với 2 bao Rice và 1 thùng Water. C03 đọc lại đúng delivery mới và snapshot. |
| D02 | Người ghi có cả hai role vẫn nhận 403 `SELF_CONFIRMATION`. Tài khoản khác nhận 200 `CONFIRMED`, chain là `PENDING_ON_CHAIN`. C03/P04 hiện FULFILLED và tổng xác nhận 2/1. |
| S01 → P05 | S01 trả 201. UI khóa Close campaign. P05 trả 409 `DISPUTE_OPEN` dù đã đủ số lượng xác nhận. |
| Đọc evidence theo tranh chấp | Staff thường nhận 403. PLATFORM_ADMIN đọc snapshot đúng nhận 200 và `Cache-Control: no-store`. Version ngoài snapshot và đọc sau resolve trả 404. |
| S03 → reload | `VOID_DELIVERY` trả 200. C03 và P04 sau reload hiện PUBLISHED, lượng đã nhận vẫn 2/1, lượng đã giao và xác nhận về 0/0 cho mỗi mặt hàng và số dispute OPEN về 0. |
| Giao thay thế → P05 | D02 xác nhận lại qua tài khoản độc lập. P05 trả 200, UI tải lại thấy campaign CLOSED, hồ sơ COMPLETED, totals 2/1 và T01 có `DELIVERY_CONFIRMED`, `CASE_COMPLETED`. PostgreSQL giữ đúng một bản ghi void và một audit hoàn tất. |

Trên SHA `af7528a`, các lớp `CampaignCloseIT` (5), `DeliveryApiIT` (7), `DeliveryConfirmationIT` (12), `DisputeResolveIT` (7), `ReceiptApiIT` (5), `StaffCaseReviewIT` (7) có tổng **43 pass, 0 fail/error/skip** qua hai lượt. Backend và các lớp này giữ nguyên ở SHA cuối. Lượt browser trước trên `af7528a` có 5 pass, 1 fail, 0 skip. D01 đã trả 201 nhưng bước chọn delivery cho D02 timeout ở giao diện. Lượt cuối thêm xác minh C03 200, tải lại và chọn theo tên trường rõ ràng. Worker/Fuji và hosting công khai chưa chạy trong lượt này.

## I. Sửa review PR #31 ngày 17/11/2026

Đã fetch và đối chiếu `main@627be56`, `dung@f85d390` và `minh@9fe2ad2` trước khi sửa. SHA chạy lại là `a2de59e1ab15f876b04949f52372e76eea347196`.

- Sau mutation F09, case detail đọc lại C02 ở nền cùng lần làm mới C03/audit. Playwright đóng campaign qua P05 và kiểm ngay trên trang đang mở rằng campaign hiện CLOSED, case hiện COMPLETED và T01 có CASE COMPLETED. Bài test không reload trang sau P05.
- Lúc D02 đang gửi hoặc chưa rõ kết quả, khóa Sign out, chuyển tổ chức và điều hướng trong workspace. Payload và Idempotency-Key giữ trong bộ nhớ của tab, gắn với ID tài khoản. Sau 401, đăng nhập lại bằng cùng tài khoản trên trang ứng dụng đã chuyển tới để quay về hồ sơ và dùng **Retry same request**. Nếu dùng tài khoản khác hoặc đã mất quyền vào hồ sơ, attempt không được mở lại. Tải mới hoặc đóng hẳn tab sẽ xóa bộ nhớ. Trình duyệt có thể nhắc trước khi rời tab khi request đang chờ. Khi tab đã đóng, kiểm tra lại quyết định ở C03/T01 trước khi tạo yêu cầu khác.
- Ca Playwright để Spring lưu D02 và trả 200 rồi thay response phía trình duyệt thành 504. Sau khi xóa cookie test, lần thử lại nhận 401 thật từ `/api/auth/me`. Đăng nhập lại cùng tài khoản, request gửi đúng body và key ban đầu, Spring trả 200 với `Idempotent-Replayed: true`, và PostgreSQL có đúng một dòng confirmation cho delivery đó.
- Trên `3090519`, bài Playwright đầu tự gọi `goto('/login')` khi đã ở trang đăng nhập và tải mới tab, làm mất attempt trong bộ nhớ. Đã sửa test để đăng nhập trên trang hiện tại. Kết quả cuối: Playwright real **6 pass, 0 fail, 0 skip** trong 55,1 giây. Maven verify **1 DonorUiE2EIT pass**, 9 validation test pass, 0 fail/error/skip. Frontend **77 unit pass**, typecheck và Webpack build pass. Dùng cùng lệnh và cấu hình DB test ở mục H.
