# Checklist chuẩn bị F10 — 15/11/2026

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
