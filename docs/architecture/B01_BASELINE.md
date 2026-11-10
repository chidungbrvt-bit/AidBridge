# B01 - RBAC, database và API thống nhất

Ngày cập nhật: 07/11/2026. Owner: Dũng (B). Status: **In Review**. USER_CONFIRMED: user yêu cầu thống nhất thiết kế. Đây là bản tổng hợp để nhóm review; chưa có xác nhận review của Khoa/Minh và không phải chứng nhận application đã chạy.

Đọc cùng [Database Design](DATABASE_DESIGN.md), [Business Workflows](BUSINESS_WORKFLOWS.md), [API Specification](API_SPECIFICATION.md) và [Blockchain Architecture](BLOCKCHAIN_ARCHITECTURE.md). PDF nguồn giữ nguyên. Các quy tắc BR-01..12, scope, estimates và deadlines giữ nguyên.

## 1. Quy ước identity và RBAC

| Khái niệm | Tên dùng trong code / SQL / API | Điều kiện / phạm vi |
|---|---|---|
| Tên tài khoản | `fullName` / `full_name` / `fullName` | Tối đa150 ký tự; không dùng `displayName`/`display_name` trong thiết kế hiện hành |
| Email | `email` / `email` / `email` | Tối đa255; strip + lowercase Locale.ROOT; unique LOWER(email); không đổi password khi chuẩn hóa email |
| User status | `status`: ACTIVE, DISABLED | ACTIVE mới được authenticate; DISABLED không được thao tác bằng session cũ; B03 phải reload/check account |
| Platform role | `platformRole` / `platform_role`: USER, PLATFORM_ADMIN | USER là mặc định; không suy ra quyền đọc evidence hoặc quyền theo tổ chức từ PLATFORM_ADMIN |
| Organization status | `status`: PENDING, APPROVED, SUSPENDED | PENDING mặc định; chỉ APPROVED cho operational writes; không thêm onboarding UI |
| Membership status | `status`: ACTIVE, INVITED, DISABLED | Chỉ ACTIVE cấp quyền; không invitation/member-management UI trong P0 |
| OA | `ORG_ADMIN` | Role trong membership của một organization |
| FV | `VOLUNTEER` | Role trong membership của một organization |
| CV | `VERIFIER` | Role trong membership của một organization |
| DC | `DELIVERY_CONFIRMER` | Role trong membership của một organization |
| PA | `PLATFORM_ADMIN` | Platform role của user; không là membership role |
| Donor | `donorEnabled` / `donor_enabled` | Capability riêng, không là membership role; ACTIVE + explicit donor grant; chỉ sửa/đọc private contribution của mình |

`FIELD_VOLUNTEER`, `CASE_VERIFIER`, `ORGANIZATION_ADMIN`, `enabled`, `active` boolean và `platform_admin` là tên của bản thiết kế cũ, không thêm song song làm nguồn quyền thứ hai. Nhãn giao diện tiếng Anh có thể mô tả ý nghĩa nhưng enum/JSON role dùng tên chuẩn trên.

PDF pp.4-5 xác định sáu vai trò, trong đó có Donor. Giữ thiết kế donor grant đã có trong Database/API proposal: cờ mặc định false, grant qua demo provisioning; user có thể đồng thời có membership roles và donor capability nếu được cấp rõ ràng. Không tự đổi platform_role thành DONOR hoặc cho PLATFORM_ADMIN bypass donor/ownership guards. **V1 và User hiện chưa có donor_enabled**; phải bổ sung bằng migration tiếp theo và mapping trước khi cho phép donor endpoints. Không gọi cờ chưa có là tính năng đã chạy.

## 2. Ownership và các tình huống review bắt buộc

| Tình huống | Kết quả thiết kế | Trace |
|---|---|---|
| VOLUNTEER có membership ACTIVE, organization APPROVED tạo case của org đó | SUBMITTED, actor từ session, beneficiary/consent/needs/evidence links cùng org | BR01-P, B03/B04 |
| User ngoài org hoặc membership INVITED/DISABLED thao tác private case |403/404 theo API contract; không DB/audit business/job side effects | BR01-N |
| VERIFIER khác creator duyệt đúng current case/evidence version | Immutable reviewed snapshot, VERIFIED + audit + outbox cùng transaction | BR02-P |
| Creator đồng thời có VOLUNTEER và VERIFIER tự duyệt |403 SELF_APPROVAL; identity guard không được bỏ qua vì multi-role | BR02-N |
| ORG_ADMIN publish campaign với needs đã được review, version còn hợp lệ | Copy mọi need line, fixed unit/integer quantities; public DTO sanitized | BR03-P |
| Publish khi case chưa verified hoặc snapshot stale |409 INVALID_STATE/VERSION_CONFLICT; không public campaign | BR03-N |
| Delivery recorder đồng thời có DELIVERY_CONFIRMER tự confirm |403 SELF_CONFIRMATION; giữ quantity/audit integrity | BR06-N |
| User DISABLED, org SUSPENDED, membership/role bị thu hồi sau login | Kiểm tra lại trước business read/write và idempotency replay; session authority cũ không đủ | IT15 |

Case, needs, consent, evidence versions và các goods records có ownership bắt nguồn từ organization/case. Client chỉ chọn organizationId trong active memberships; không gửi creatorId/actorId để ghi đè session. Beneficiary không có tài khoản/wallet. Public API không trả UserResponse, internal case identity hoặc evidence fixture key.

## 3. DTO và HTTP contract

Update07/11 (D41/A user-confirmed): B04 implement case/evidence DTOs và forward V6 theo API/workflow, giữ V1–V5. User báo B02/B03 đã hoàn tất; B01 vẫn **In Review**, không suy ra cross-owner acceptance toàn baseline. Latest implementation/projection differences, pending PostgreSQL tests và B05 handoff tại [B04 Case Workflow](B04_CASE_WORKFLOW.md).

- UserResponse đã có: `id`, `fullName`, `email`, `status`, `platformRole`, `createdAt`; chỉ dùng cho authorized account responses; không password hash.
- AuthResponse `{user:UserResponse}` là response login đã được user chọn (D40); dùng session cookie, không JWT fields. GET /api/auth/me trả MeResponse riêng.
- MeResponse đã có: `userId`, `fullName`, `status`, `platformRole`, `donorEnabled`, `memberships:[{organizationId,roles,status}]`. userId ánh xạ users.id; chỉ trả membership ACTIVE trong organization APPROVED. donorEnabled hiện mặc định false vì grant chưa có trong schema; không suy ra từ membership.
- OrganizationResponse: `id`, `name`, `description`, `status`, `rolesOfCurrentUser`; public chain reference/relayer projection chỉ dùng khi dữ liệu và authorization đã có. Organization name<=200; user fullName<=150; email<=255 theo V1 hiện có.
- `@Valid` + request DTO bounds ở HTTP; transaction/ownership/state/identity guards ở service; mapper không cấp quyền. Auth/me reload account/memberships; UserService đọc account theo ID/email hiện tại chưa phải authorization layer.
- JSON camelCase; SQL snake_case; UUID/UTC ISO-8601; quantities integer; error application/problem+json gồm type/title/status/code/detail/instance/traceId. fieldErrors nếu có là array `{field,code}`, không echo sensitive rejected values. GlobalExceptionHandler đã đồng bộ envelope, no-store và safe framework errors;11 MockMvc error-contract tests pass07/11. Hai PostgreSQL/HTTP regression cases mới chưa rerun do terminal thiếu test environment; user báo16/16 của bản trước đã pass. Cross-owner acceptance vẫn pending.
- Anonymous: GET public published campaign/audit, GET auth/csrf; POST auth/login vẫn cần CSRF. POST logout cần CSRF + session và trả204. Private resources cần authenticated ACTIVE account, membership/state checks tương ứng. Unknown/private-out-of-scope404; action thiếu role trên resource đã được phép đọc403.
- Business mutations ngoài login/logout cần Idempotency-Key; scope actor+operation+key, digest canonical request, same request replay giữ nguyên response, changed request409. Replay vẫn phải authorization lại.

## 4. Initial audit / contract event mapping

Đây là schema/interface baseline cần Khoa review trong C01; chưa phải ABI đã compile. Các function signatures/checks đầy đủ ở Blockchain Architecture, schemaVersion=1. Tên audit SQL có thể khác tên Solidity event nhưng chỉ dùng mapping dưới đây.

| Business audit event_type | Function | Solidity event | Ordering |
|---|---|---|---|
| CASE_CREATED | Không on-chain | Không có | Local audit, chưa có chainSequence |
| CASE_VERIFIED | recordCaseVerification | CaseVerified | Case milestone đầu tiên, chainSequence=1; org registration đã confirmed |
| AID_PLEDGED | recordAidCommitment | AidCommitted | Dependency theo case milestone trước |
| COMMITMENT_REDUCED | recordCommitmentReduction | CommitmentReduced | Giữ original pledge và ID cũ, không giảm receipt |
| GOODS_RECEIVED | recordGoodsReceipt | GoodsReceived | Không đồng nghĩa delivered/confirmed |
| DELIVERY_RECORDED | recordDelivery | DeliveryRecorded | Không tự confirm |
| DELIVERY_CONFIRMED | confirmDelivery | DeliveryConfirmed | Independent actor đã được backend kiểm tra |
| DISPUTE_OPENED | flagDispute | DisputeFlagged | Có thể reopen completed case |
| DISPUTE_RESOLVED | resolveDispute | DisputeResolved | UPHOLD không tự confirm |
| DELIVERY_VOIDED + DISPUTE_RESOLVED | resolveDispute với VOID_DELIVERY | DeliveryVoided + DisputeResolved | Một command/job, kiểm tra đủ expected events; giữ lịch sử |

Envelope business milestones: random orgRef/caseRef/eventId bytes32, chainSequence uint64, evidenceCommitment bytes32, schemaVersion uint16=1. Org registration là admin operation riêng, không mang caseRef/chainSequence giả. Audit sequence_no và chainSequence độc lập. HTTP auditEventId là UUID của audit row; chain eventId là random bytes32 lưu trong audit_events.event_id, không dùng lẫn hai ID. Không PII/SQL UUID/file URLs/salt trong calldata/events. Signed bytes/hash phải persisted trước broadcast; chỉ exact successful receipt + expected event(s) mới confirmed. Golden vectors và ABI compatibility vẫn thuộc C01/C03/C05, chưa được kiểm chứng trong B01.

## 5. Hiện trạng migration và việc cần làm

| Phần | Hiện trạng kiểm tra 04/11/2026 | Việc tiếp theo |
|---|---|---|
| V1 identity | Có SQL; tên field, status và membership role đã đối chiếu với code | B02 verify clean/upgrade + full app startup; giữ migration đã apply, sửa qua forward migration |
| Identity extensions | Chưa có donor_enabled, organization public_ref/relayer_address/approval metadata | B02/B03 bổ sung migration theo baseline trước donor/chain features; default false donor grant, demo provisioning riêng |
| V2–V5 | Đã có SQL; V3–V5 ghi theo user cung cấp ngày04/11, ghép V5 không trùng phần giao nhau; chưa chạy migration toàn bộ | B02 verify clean/upgrade; B04..B10/C05..C08 review gaps so với design trước integrated acceptance; xem D38 |
| Java identity | User/entity/repository/mapper/response/service có implementation; Organization classes đang skeleton | Tiếp tục theo approved task, không tự tạo CRUD/hard delete cho immutable records |
| Auth | Config/providers có focused tests; production csrf/login/me còn thiếu | B03 nối controller/service, full error envelope, membership/disabled guards và PostgreSQL integration |

Runtime hiện tại do user quản lý qua PostgreSQL installation riêng; version/endpoint/database/account cần xác nhận trong B02. Các mốc local17 planning và thử nghiệm18.6 là lịch sử. User đã yêu cầu gỡ instance workspace5433 cùng scripts/data/credentials (D37); kết quả test cũ không chứng minh database mới Ready. Schema baseline/implementation gaps giữ nguyên; không sửa V1 đã apply. Hosted version vẫn review riêng trước deploy.

## 6. Evidence và review

- 04/11/2026: đối chiếu text PDF pp.4-5, SQL V1 enums/columns, UserResponse và User enums; kiểm tra V2–V5 rỗng. Đây là static documentation review, không phải BR integration tests.
- Verification command từ repo root: bundled Python chạy `-X utf8 backend/aidbridge-backend/target/verify-b01.py`: **15 static consistency checks OK**,16 relative file links resolved trong11 documents;36 task rows có33 Not Started, B01 In Review, B02/B03 In Progress. Script local/ignored dưới target, kiểm tra SQL enum/role và Java enum, tên DTO, gap donor và trạng thái placeholders; không thực thi SQL hoặc Java. `git diff --check -- docs/architecture docs/planning docs/quality/TEST_STRATEGY.md` không có whitespace errors; cảnh báo LF/CRLF không phải lỗi thiết kế.
- Các lần test ứng dụng trước được ghi ở Task Backlog; không chạy lại test Java cho thay đổi Markdown. BR01/BR02/BR03 runtime tests chưa được đánh dấu Pass trong B01.
- Dũng review ownership/schema/DTO/error/idempotency; Minh review request/response/roles UX; Khoa review event envelope/mapping/ordering và trust boundaries. Chưa có chữ ký/xác nhận của ba owner ở đây; không gửi message thay nhóm.
- Actual human effort và thời điểm team acceptance chưa ghi nhận. B01 In Review; B02/B03 In Progress. B01 chỉ Done khi nhóm xác nhận baseline và các review findings đã xử lý, không yêu cầu dựng Fuji deployment trong một task design.
