# B08 — R01 và bàn giao F08

Ngày đối chiếu 13/11/2026, Asia/Bangkok. Mốc bàn giao mục tiêu 14/11/2026. Dũng phụ trách backend, Khoa review payload/chain, Minh review HTTP/F08. Dũng đã duyệt triển khai B08 và xác nhận test backend B08 pass trong IntelliJ. B07 ở `In Review`. B07 đã có G01–G03 và có bằng chứng F07 chạy với backend và PostgreSQL. Phần received của F07 dùng dữ liệu SQL dựng cho test. [Backlog](../planning/TASK_BACKLOG.md#b08---partial-goods-receipts), [F07 integration evidence](F07_B07_API_INTEGRATION.md).

## 1. Đối chiếu contract và code

| Phần | Đã có trong repo | Cách dùng cho R01 |
|---|---|---|
| Actor và tenant | `OrganizationService.currentMemberships()` đọc tài khoản ACTIVE và membership ACTIVE ở organization APPROVED. G03 có scalar projection cho commitment và khóa theo scope. | Tìm commitment bằng ID trong tập organization có membership hợp lệ. Commitment không truy cập được trả `404 RESOURCE_NOT_FOUND`. Nếu tìm thấy, kiểm tra `ORG_ADMIN` của chính organization đó và trả `403 FORBIDDEN` khi thiếu role. Lấy `recorderId` từ session, kiểm tra lại trước mỗi replay. |
| Khóa và số lượng | `ContributionService.cancel()` khóa case → campaign → campaign items theo ID → contribution → contribution items theo ID. `ContributionItem.outstanding(received)` và `ContributionItemRepository.receivedTotals()` đã có. | Giữ cùng thứ tự khóa và lấy tổng đã nhận sau khi giữ khóa. Dùng scalar refs trước bước khóa để tránh đọc entity cũ từ persistence context. |
| Retry | `IdempotencyService.execute()` lưu response, HTTP status và Location theo actor, operation và key. | Dùng operation `POST:/api/commitments/{id}/receipts`. Chạy guard quyền trước journal, chạy guard số lượng và trạng thái chỉ khi thực hiện request mới. |
| Audit và job | `ContributionOutboxService` cấp `maxChainSequence(caseId) + 1` dưới khóa case. V12 có unique key theo case/sequence. | Dùng cùng cách cấp sequence. Ghi `GOODS_RECEIVED` với `event_key = receipt.id`, rồi ghi job `PENDING` cùng transaction. |
| Dữ liệu đọc | G02 và G03 dùng `receivedTotals()`. | P03/P04 lấy pledged và received trong cùng truy vấn aggregate. Staff có GET riêng trả `outstandingCommitments` theo D49. |

Nguồn contract và thiết kế đã chốt cho R01. [API Specification](../architecture/API_SPECIFICATION.md#endpoint-specification), [Business Workflows](../architecture/BUSINESS_WORKFLOWS.md#goods-receipt), [D49 và D53](../planning/DECISION_LOG.md), [Test Strategy](../quality/TEST_STRATEGY.md).

## 2. Schema diff V14

V3 tạo `goods_receipts` và `goods_receipt_items`. V11 giữ nguyên hai bảng này. Sau V13, B08 thêm V14. V12 đã có `UNIQUE (aid_case_id, chain_sequence)`. Số version áp dụng thực tế trong `flyway_schema_history` của database hiện tại chưa được kiểm tra vì terminal thiếu cấu hình `aidbridge_test`. Kiểm tra kết nối, version hiện có, Flyway validate và các guard dữ liệu dưới đây trước khi triển khai. [V3](../../backend/aidbridge-backend/src/main/resources/db/migration/V3__create_campaign_contribution_tables.sql), [V11](../../backend/aidbridge-backend/src/main/resources/db/migration/V11__reconcile_contribution_schema.sql), [V12](../../backend/aidbridge-backend/src/main/resources/db/migration/V12__link_blockchain_jobs_to_case.sql), [V13](../../backend/aidbridge-backend/src/main/resources/db/migration/V13__serialize_blockchain_signer_attempts.sql).

`V14__reconcile_goods_receipt_schema.sql` thực hiện các thay đổi dưới đây. Giữ V1–V13 và checksum. Ánh xạ `received_by` thành `recorderId`, `received_at` thành `receivedAt` và `created_at` thành `recordedAt`. Receipt mới chỉ có một trạng thái nghiệp vụ `RECORDED` theo workflow. `ReceiptResponse` giữ đúng tám trường đã chốt.

| Bảng | Thay đổi đề xuất | Dữ liệu hiện có |
|---|---|---|
| `goods_receipts` | Bỏ `NOT NULL` trên `idempotency_key` để bản ghi mới dùng journal chung. Giữ unique constraint cũ để bảo toàn ý nghĩa của các legacy key đã lưu. Thêm `UNIQUE (id, contribution_id)` cho FK ghép. | Giữ ID, key cũ, note, actor và timestamps. |
| `goods_receipt_items` | Thêm `contribution_id UUID`, backfill từ header, đặt `NOT NULL`. Thêm hai FK ghép `(goods_receipt_id, contribution_id) → goods_receipts(id, contribution_id)` và `(contribution_item_id, contribution_id) → contribution_items(id, contribution_id)`. | Chạy guard trước khi thêm ràng buộc. Dừng migration và báo ca cần review nếu item đã trỏ sang contribution khác. Giữ số lượng và lịch sử. |
| `contribution_items` | Thêm `UNIQUE (id, contribution_id)` làm đích của FK ghép. | Giữ dữ liệu cam kết gốc và `cancelled_quantity`. |
| Cả hai bảng receipt | Thêm trigger `BEFORE UPDATE OR DELETE` dùng `reject_immutable_fact_change()` của V6, sau bước backfill. | Giữ receipt lịch sử ở dạng append-only. |

Guard kiểm tra dữ liệu sai quan hệ trước khi đi tiếp. Truy vấn sau là kiểm tra đọc, dùng trên schema được phép.

```sql
SELECT count(*) AS mismatched_receipt_lines
FROM goods_receipt_items gri
JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
JOIN contribution_items ci ON ci.id = gri.contribution_item_id
WHERE gr.contribution_id <> ci.contribution_id;
```

Migration cần kiểm tra cả dữ liệu lịch sử có tổng đã nhận vượt `pledged_quantity - cancelled_quantity`. Dừng khi gặp trường hợp này để Dũng xem chứng từ trước khi quyết định cách xử lý. `RECORDED` có thể là trạng thái mặc định theo workflow, không cần thêm cột nếu chưa có hành vi khác. B08 cấp `receiptRef` qua `ChainRefs.randomBytes32()` và lưu cùng `commitmentRef` trong `audit_events.payload_private` bất biến. Các receipt legacy giữ nguyên dữ liệu. Khoa review format payload trước khi nối C10. `auditEventId`, `event_key`, `receiptRef` và `logical_event_id` có mục đích riêng.

**Cách khác đã cân nhắc.** Giữ schema V3 và cấp UUID cho cột legacy khi insert sẽ giảm một migration, nhưng database vẫn cho phép gắn một receipt line sang commitment khác và sửa receipt đã ghi. Đề xuất V14 giữ invariants ở database, tái dùng journal và giữ dữ liệu cũ.

## 3. Luồng POST

1. `ContributionController` nhận session, CSRF, `Idempotency-Key` và `ReceiptRequest` với `@Valid`. Dùng `receivedAt` UTC ISO-8601. Yêu cầu ít nhất một dòng, UUID khác nhau, quantity nguyên và dương. Số lượng phải vừa kiểu số nguyên của API và nằm trong lượng còn lại của dòng cam kết. `note` tùy chọn, trim, tối đa 500 ký tự, lọc HTML. Kiểm tra `receivedAt` từ lúc publish hoặc `createdAt` đối với campaign legacy thiếu mốc publish. Giới hạn thời gian tương lai là 5 phút.
2. `GoodsReceiptService` mở transaction và kiểm tra membership theo mục 1. Khóa case rồi campaign. Ở nhánh thực thi mới, lấy refs của commitment, khóa toàn bộ campaign item liên quan theo ID, khóa contribution và các dòng theo ID như G03. Kiểm tra campaign đang mở nhận hàng và tranh chấp theo workflow. Lấy tổng đã nhận từ PostgreSQL sau khóa. Dòng không thuộc commitment trả `409 INVALID_STATE` theo yêu cầu cross-line 409 của transition table. ID sai định dạng, dòng lặp và quantity sai trả `400 VALIDATION_ERROR`.
3. Kiểm tra **tất cả dòng** với `requested <= pledged - cancelled - previouslyReceived`. Một dòng vượt trả `409 QUANTITY_EXCEEDED`. Nếu commit được, lưu một receipt và các dòng bất biến qua JPA. Dùng thời điểm server cấp làm `recordedAt`, ổn định qua replay. Tính trạng thái trên **tất cả** contribution items sau khi nhận. Còn lượng chờ nhận thì `PARTIALLY_RECEIVED`, hết lượng chờ nhận thì `RECEIVED`. Chỉ nhận trên contribution còn nhận được hàng.
4. Ghi audit và job theo D53 trong transaction trên. Private payload dành cho Khoa gồm receipt ID, contribution ID và từng dòng với contribution item ID, campaign item ID và quantity. `receiptRef` và `commitmentRef` phải ổn định cho C10. Public metadata để trống. Note nằm trong receipt riêng. Retry giống hệt trả `201`, body và Location gốc cùng `Idempotent-Replayed: true`. Payload khác với cùng key trả `409 IDEMPOTENCY_CONFLICT`.
5. G02 hiện có đủ dữ liệu để tính `receivedQty` và `outstandingQty` từ receipt line. G03 dùng cùng tổng để giới hạn hủy. P03/P04 trả tổng `receivedQty` theo campaign item. Receipt chỉ tăng lượng đã nhận. Số đã giao/xác nhận và các bảng delivery do B09/B10 đảm nhận.

Minh review cách UI hiển thị `409 INVALID_STATE` khi dòng nằm ngoài commitment. `409 QUANTITY_EXCEEDED` giữ cho trường hợp vượt số lượng. [Transition table](../architecture/BUSINESS_WORKFLOWS.md#transition-implementation-table).

## 4. Ví dụ HTTP và cách refresh F08

Ví dụ lấy đúng shape của fixture `R01-success`. Khi chạy trên backend thật, giữ cookie session của tài khoản `ORG_ADMIN` đang ACTIVE trong owning organization, lấy CSRF sau đăng nhập từ `GET /api/auth/csrf`, thay UUID bằng ID từ dữ liệu test và dùng thời điểm nhận phù hợp với campaign lúc chạy. [F02 fixture](../fixtures/f02/goods-flow.mock.json).

```http
POST /api/commitments/30000000-0000-4000-8000-000000000001/receipts
Content-Type: application/json
Cookie: JSESSIONID=<session-cookie>
X-CSRF-TOKEN: <csrf-token>
Idempotency-Key: 90000000-0000-4000-8000-000000000004

{
  "receivedAt": "2026-11-09T01:39:00Z",
  "items": [
    {"contributionItemId": "40000000-0000-4000-8000-000000000001", "quantity": 5},
    {"contributionItemId": "40000000-0000-4000-8000-000000000002", "quantity": 1}
  ],
  "note": "Partial receipt for simulated goods."
}
```

```http
HTTP/1.1 201 Created
Location: /api/receipts/50000000-0000-4000-8000-000000000001
Cache-Control: no-store
Content-Type: application/json

{
  "id": "50000000-0000-4000-8000-000000000001",
  "contributionId": "30000000-0000-4000-8000-000000000001",
  "items": [
    {"contributionItemId": "40000000-0000-4000-8000-000000000001", "quantity": 5},
    {"contributionItemId": "40000000-0000-4000-8000-000000000002", "quantity": 1}
  ],
  "receivedAt": "2026-11-09T01:39:00Z",
  "recordedAt": "2026-11-09T01:40:00Z",
  "recorderId": "70000000-0000-4000-8000-000000000002",
  "auditEventId": "80000000-0000-4000-8000-000000000002",
  "blockchainStatus": "PENDING_ON_CHAIN"
}
```

- **Lấy ID và refresh bằng staff session.** Theo D49, gọi `GET /api/org/cases/{id}` để lấy `outstandingCommitments[].contributionId` và `outstandingCommitments[].items[].contributionItemId`, rồi gọi cùng GET sau `201` để refresh. Mỗi commitment cần `status`, `pledgedAt`. Mỗi dòng cần thêm `campaignItemId`, `itemName`, `unit`, `pledgedQty`, `receivedQty`, `outstandingQty`.
- **Cập nhật luồng đọc.** `StaffCaseController` triển khai `GET /api/org/cases/{id}` theo D49. Trường `outstandingCommitments` chỉ chứa dòng còn số lượng chờ nhận. `GET /api/me/commitments` phục vụ donor. Public P04 trả tổng theo mặt hàng. `Location` là định danh receipt theo D49, dùng body để render bản ghi vừa tạo. Phần `availability`/dispute của màn hình giao hàng còn thuộc B09. [StaffCaseController](../../backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/controller/StaffCaseController.java).
- **Lỗi chính cho F08.** `403 FORBIDDEN` khi staff cùng org thiếu role, `403 CSRF_INVALID` khi CSRF không hợp lệ, `404 RESOURCE_NOT_FOUND` khi commitment thiếu hoặc ngoài scope, `409 QUANTITY_EXCEEDED` khi một dòng vượt lượng còn lại, `409 INVALID_STATE` khi trạng thái hoặc dòng không phù hợp, `409 DISPUTE_OPEN` khi tranh chấp đang chặn thao tác, và `409 IDEMPOTENCY_CONFLICT` khi cùng key nhưng payload đổi. Khi có xung đột số lượng, refetch staff projection trước khi nhập lại.

## 5. Kiểm thử và bàn giao

| Tình huống | Assertion trong bộ test B08 | Lớp test |
|---|---|---|
| Upgrade V13 → V14 | Flyway validate, giữ ID/key/note/actor/timestamps/quantities, khóa ghép và trigger đúng. Migration từ chối dòng thuộc cam kết khác hoặc tổng nhận vượt lượng còn hiệu lực. | `ReceiptSchemaIT` |
| ORG_ADMIN nhận một phần hai mặt hàng | HTTP 201, Location và shape đúng, `PARTIALLY_RECEIVED`, hai receipt lines, một audit/job. G02/P04 cập nhật received, staff GET cập nhật outstanding. Ghi thêm lần cuối đạt `RECEIVED`. | `ReceiptApiIT` |
| Một dòng hợp lệ và một dòng nhận quá mức hoặc thuộc commitment khác | HTTP 409, giữ status, số receipt, line, audit và job như trước request. | `ReceiptApiIT` |
| Role và scope | Staff cùng org thiếu role trả 403. Admin org khác trả 404. Replay sau khi thu hồi role vẫn trả 403. | `ReceiptApiIT` |
| Retry | Hai request giống hệt cùng key trả cùng body/Location, chỉ có một receipt, một audit event và một job. Đổi dữ liệu với cùng key trả 409. | `ReceiptApiIT` |
| Receipt cạnh tranh G03 | Cùng còn 2 đơn vị, R01 nhận 2 và G03 hủy 2 từ hai session. Một request thành công, request còn lại trả 409. Tổng đã nhận cộng đã hủy bằng 2. | `ReceiptApiIT` |
| Tách hàng thực nhận khỏi delivery | G03 chỉ hủy tối đa outstanding sau R01. `deliveredQty`, `confirmedQty` và số delivery rows giữ ở 0. | `ReceiptApiIT` |

Dùng `AbstractCampaignHttpIT` và `aidbridge_test` với PostgreSQL thật trong schema test riêng. Dũng chọn chạy JUnit trong IntelliJ với ba biến `AIDBRIDGE_TEST_DATABASE_*` đã cấu hình. Chạy `ReceiptApiIT`, `ReceiptSchemaIT`, rồi chạy các lớp hồi quy gồm `ContributionCancelIT`, `ContributionConcurrencyIT`, `ContributionSchemaIT` và `CampaignApiIT`. Lệnh tương đương khi có biến trong terminal.

```powershell
cd backend/aidbridge-backend
.\mvnw.cmd -o test "-Dtest=ReceiptRequestValidationTest,ReceiptApiIT,ReceiptSchemaIT,ContributionCancelIT,ContributionConcurrencyIT,ContributionSchemaIT,CampaignApiIT"
```

**Maven, 13/11.** Chạy lệnh Maven ở trên bằng `mvn -o -B -ntp` với cùng bộ lớp. Build thành công, 5 validation tests pass, 45 test entries PostgreSQL skip vì terminal thiếu `AIDBRIDGE_TEST_DATABASE_*` (bao gồm một test parameterized bị skip ở cấp method). Kiểm tra offline khởi tạo repository với Hibernate và serialize staff JSON phẳng thành công. `node docs/fixtures/f02/verify-f02.mjs` exit 0, đối chiếu thành công 5 snapshots và 10 mock scenarios.

**IntelliJ, 13/11.** Dũng xác nhận toàn bộ test đã chạy đều pass với `aidbridge_test` sau khi chạy bộ B08 gồm `ReceiptApiIT` và `ReceiptSchemaIT`. Kết quả này dựa trên xác nhận trong chat. Chưa có báo cáo IntelliJ để đối chiếu tổng số test và các lớp hồi quy đã chạy. F08 browser E2E và Fuji receipt sync **Not Run** trong các lượt kiểm chứng được ghi tại đây.

- **Khoa** review `GOODS_RECEIVED`, refs và thứ tự sequence cùng `AID_PLEDGED` và `COMMITMENT_REDUCED` để tiếp nhận sang C10. Worker receipt và Fuji nằm ngoài B08.
- **Minh** đối chiếu route staff, tám trường response, xử lý 403/404/409 và refetch. Chạy F08 với tài khoản staff thật trong môi trường test sau khi có R01 và staff projection. B09 delivery là dependency riêng.
