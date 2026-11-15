# Task3 — B09 DELIVERY_RECORDED / deliveryRef / V16

15/11/2026, Khoa/Codex. **NEEDS FIX — High: migration delivery_ref chưa được bàn giao.** Review hoàn thành trong phạm vi REST/SQL/ref/sequence, không triển khai thay Dũng.

## Phiên bản và finding B09-R1

Fetch main9c24630/dung0fc3e3d. B09 DELIVERED bf39d4c/ebeacc6 đã MERGED qua [PR25](https://github.com/chidungbrvt-bit/AidBridge/pull/25); B09 Java/migrations giống nhau trên hai remote branches. Đã đọc PR description/comments, remote trees/history, không kết luận từ diff main..dung rỗng. PR25 tự ghi `V16__add_delivery_ref.sql` chưa commit; không có V16 trong hai remote trees hoặc fetched commit history.

**High — fresh checkout không tái lập schema B09:**

- `backend/aidbridge-backend/src/main/java/com/aidbridge/delivery/entity/Delivery.java:56–57` map `delivery_ref bytea`; :69–72 viện dẫn check `chk_delivery_ref_by_outcome`.
- `.../delivery/service/DeliveryService.java:152–156` insert DELIVERED kèm ref; :193 dùng ref cột vào payload. FAILED cũng insert cùng entity có column mapping, dù ref null.
- `backend/aidbridge-backend/src/main/resources/db/migration/V15__reconcile_delivery_schema.sql` không thêm cột/check này; không có migration khác tạo delivery_ref. `application.properties:16` dùng `ddl-auto=validate`, không tự thêm schema.
- Tái hiện từ source: DB sạch chỉ áp tracked V1–V15 rồi start backend sẽ thiếu delivery_ref lúc Hibernate validate. Nếu bypass validate, SQL insert/select vẫn dùng cột thiếu. Đây là lỗi packaging/schema ảnh hưởng cả DELIVERED/FAILED, không phải thiếu worker/Fuji. **Chưa chạy Java/DB để ghi error runtime** trong lượt review này.
- Owner Dũng: commit/push V16 đúng bản đã test và rerun B09 trên checkout/migrations tái lập. Không dùng test DB đã thêm cột thủ công/untracked migration làm proof fresh-checkout PASS. Không sửa checksum V15 đã áp dụng hoặc bỏ column mapping chỉ để che thiếu migration.

## D56 mục2 và điều kiện V16

**Chấp nhận hướng thiết kế hiện tại:** `deliveries.delivery_ref` là source of truth, audit payload chụp cùng giá trị. Mô tả ngày14 “chỉ payload” không còn khớp source. Đây là review thiết kế, **chưa phải migration PASS**; file V16 chưa có nên chưa thể xác minh backfill/trigger/checks.

Yêu cầu Dũng bàn giao:
1. bytea32/nonzero cho DELIVERED, FAILED null; bảo vệ uniqueness. Backfill từ deliveryRef hợp lệ đã lưu trong immutable DELIVERY_RECORDED audit, kiểm đúng delivery/case/org và một mapping rõ ràng; không sinh lại ref đã có payload/signed attempt. Missing/malformed/duplicate/mismatch fail-closed, reconcile riêng.
2. V15 có deliveries_immutable: nếu backfill, tạm tắt đúng trigger trong transaction migration, bật lại trước commit; failure rollback cả DDL/backfill. Không tắt trigger diện rộng, sửa audit cho khớp ref mới hoặc để trigger tắt sau lỗi. DB rỗng/FAILED-only phải upgrade được.
3. Tests fresh V1→V16, upgrade V15 có DELIVERED/FAILED, column=payload, malformed/mismatch/duplicates, rollback và immutability sau upgrade. Giữ replay/concurrency tests B09; bổ sung failure audit/job rollback assertions khi phù hợp. Không cần redeploy để test migration.

## Review REST/SQL và mapping

| Phần | Kết luận dựa trên code/tests đã đọc |
|---|---|
| Auth/CSRF/scope | DeliveryController @Valid, shared SecurityConfig session/CSRF; DeliveryService:61–92 transaction, ACTIVE memberships/owning org scoped case lock, VOLUNTEER trước journal/replay. Cùng org thiếu role403, ngoài scope404. |
| DTO | DeliveryRequest positive integer≤1,000,000, distinct lines/evidence. DELIVERED items/evidence không rỗng. FAILED reason1..2000, hai mảng rỗng, handedOverAt null. DELIVERED time optional nhưng không trước publish hoặc quá now+5 phút. |
| Stock/reservation | Case→campaign→items theo ID; received trừ mọi DELIVERED kể cả chưa confirm. Validate toàn bộ goods/evidence trước ghi; header/items/evidence/audit/job/journal cùng transaction, lỗi rollback không partial stock effect. B10 phải loại VOID sau này, không giả PASS phần chưa triển khai. |
| Ref/retry | ChainRefs.randomBytes32 loại all-zero, chỉ gọi trong recordDelivered mới; Delivery immutable/updatable=false và V15 trigger. Payload lấy getDeliveryRef, không random lại. Journal replay cùng key/body trả response cũ, không enqueue lần2. Persistence schema vẫn bị R1 chặn. |
| Evidence | delivery_evidence_links giữ đúng version IDs/actor/purpose/case/org; V15 composite FKs/immutable trigger và V6 evidence_versions immutable. Worker tương lai lấy versions/checksums đã chụp, không dùng current evidence mới hoặc hash JSON mặc định. |
| Payload | DeliveryService:184–206: payloadVersion1,deliveryId,campaignId,deliveryRef,handedOverAt,lines[{campaignItemId,quantity}],evidenceVersionIds. Handover note/reason không có trong DELIVERY_RECORDED payload; FAILED reason chỉ private audit riêng. |
| Identity/sequence | event_key/entity_id=delivery UUID khác eventId=job.logical_event_id bytes32; orgRef/caseRef từ public refs, schema_version1 khác payloadVersion1. MAX+1 dưới case lock, V12 unique(case,sequence), job cùng transaction. Seq4 chỉ fixture verify→pledge→receipt→delivery, không constant nghiệp vụ. FAILED không job/sequence chain, không inventory. |
| Response/ABI | DELIVERED201/PENDING/PENDING_ON_CHAIN; FAILED201/NOT_APPLICABLE hai axes/auditUrl null. DELIVERY_RECORDED→recordDelivery(envelope,deliveryRef)→DeliveryRecorded: indexed orgRef/caseRef/eventId; data sequence,commitment,schema,timestamp,deliveryRef. Quantities/campaign UUID/evidence IDs không lên calldata/log, chỉ private manifest. |

D55 flagDispute/resolveDispute phải dùng đúng ref gốc/cùng case/org, không random lại hoặc thay bằng delivery UUID. VOID hai logs DeliveryVoided→DisputeResolved cùng eventId/sequence, một job. Goods worker/backend dispute chưa triển khai; không tuyên bố recordDelivery đã lấy ref DB gửi Fuji hoặc dispute E2E PASS. B09 REST/SQL không cần redeploy ngay; thiếu worker/C06 không phải finding của REST/SQL này.

## Test evidence và nghiệm thu

Đọc DeliveryApiIT7 methods: DELIVERED lines/evidence/một job, payload ref32=column, update trigger reject, replay một lần; reservation unconfirmed; FAILED stock/job/ref null; scope/role/replay reauthorization; foreign evidence/items/time, dispute replay và public totals. DeliveryConcurrencyIT1 method có3 vòng,201+409, một header/lines/evidence/audit và một sequence; dòng Water hợp lệ của request thất bại cũng không được ghi.

Backlog/Dũng báo7/7 API, concurrency1×3 và hồi quy66 PASS; PR25 còn báo301 tests/2 skips cho phạm vi rộng hơn. **Đó là evidence báo cáo, không phải tests mới của Khoa hoặc proof upgrade DB sạch khi V16 chưa tracked.** Chưa có tests V16 upgrade/backfill trong remote đã đọc. Compile/Java/PostgreSQL/Fuji tests **NOT RUN**, không cài môi trường hoặc chạy Actions. Không cần thực thi suite để xác minh file migration đang thiếu.

**Điều kiện chốt PASS:** Dũng bàn giao V16 + upgrade/backfill evidence và rerun tests B09 liên quan trên checkout tái lập; ref/immutability/rollback/sequence giữ đúng. Không phát hiện lỗi logic đáng kể khác trong scope đã đọc, không khẳng định hệ thống không còn bug. Không review lại auditUrl/cutover Task2 hoặc C07/C08/C10 tổng thể, không sửa backend. Task3 hoàn thành review với NEEDS FIX; không tự đánh B09 Done.
