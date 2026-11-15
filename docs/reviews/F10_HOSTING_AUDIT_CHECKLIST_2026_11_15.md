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

## C. Gate hosting/recovery theo F10 thực tế (không thực hiện Task12)

- [ ] Minh/Dũng kiểm F09/API readiness, Khoa kiểm C10 evidence và giới hạn coverage; nếu chỉ CASE_VERIFIED thì ghi goods chưa verified, không tự hạ scope P0/acceptance. B09 thiếu V16 là gate environment cần sửa trước chứng minh lifecycle deployable; xem [review V16](B09_DELIVERY_REF_REVIEW_2026_11_15.md).
- [ ] Có phê duyệt public deploy riêng, provider eligibility và forecast dưới$20 (runtime/DB/storage/egress/overage), owner theo dõi/dừng; không tự mua dịch vụ. Domain/HTTPS/cookie Secure+HttpOnly/CSRF/same-origin proxy được smoke-test trên deployment thật, không suy localhost PASS thay hosting.
- [ ] DB private network/credentials server-side; private files chỉ authenticated authorized route; CORS/cookies/file guards test allowed/forbidden, không public debug/fixture controls/secrets. Không mở DB hoặc deploy mock như sản phẩm thật.
- [ ] Backup/restore/rollback trước release; pin code/schema/ABI/deployment descriptor. DB backup giữ encrypted tx journal/AES key theo custody riêng, không đưa keys vào UI/log/report. Schema rollback không phá append-only facts; không restore DB rồi reset nonce/journal dưới signer có transaction thật.
- [ ] Rehearsal restart/recovery của worker/scanner theo deployment và cutover plan, không replay quantities hoặc resend signed bytes sang registry khác; operator gate cho orphan/unmatched/reorg. Bằng chứng C03 giữ nguyên khi có C06.
- [ ] Record health/smoke/cost/recovery evidence và actual field coverage sau triển khai; ở checklist hiện tại tất cả vẫn NOT RUN, F10 chưa Done. Không thực hiện public deploy chỉ để đánh dấu checklist.

## D. Acceptance đề xuất và ownership

Minh kiểm status/error/empty/keyboard/responsive và public/internal privacy bằng HTTP fixtures trước; Dũng kiểm DTO/authorization/DB projections; Khoa kiểm actual chain/context/receipt/reorg/scanner coverage. Bộ cases scanner nằm trong C08 Task10: crash giữa batch, timeout/duplicate/truncated logs, reorg/removed/remine, wrong context, replay, VOID pair, dual-registry cutover. Frontend cases tối thiểu: hash-only không confirmed; missing/mismatch proof không anchored; API error không “success”; D01 FAILED NOT_APPLICABLE; scanner lag được diễn đạt đúng; link canonical nhưng endpoint absent vẫn disabled.

Tất cả là **tests ĐỀ XUẤT/NOT RUN**, không tuyên bố bộ UI/backend/hosting mới PASS. Task8 audit API đối chiếu thật còn cần API đã bàn giao và DB/Fuji mapping; Task12 public hosting cần F09/C10, recovery/security/cost gates và authorization. Tài liệu này chỉ chuẩn bị, không thực hiện hai task đó.
