# B04 — Case, needs, consent và private evidence

Ngày07/11/2026. Primary owner Dũng (B). Implementation theo D41/A đã được user cho phép. Status **In Progress — PostgreSQL/HTTP acceptance và human review pending**. B02/B03 completed là user-reported evidence; không restart hai task. B01 vẫn In Review. Estimate B04 gốc **4h** giữ nguyên; chưa có actual human effort/overrun do Dũng cung cấp.

## Implementation và transaction boundary

`casefile/controller/CaseController` xử lý HTTP; `CaseService` giữ authorization/state/transaction; `CaseMapper` chỉ nhận aggregate đã authorize. Theo D45, backend ánh xạ case/need và các bảng consent, evidence, audit, history, idempotency qua Spring Data `JpaRepository`. `LocalAuditService` ghi audit và revision cùng transaction nghiệp vụ. `EvidenceService` tạo phiên bản và đánh dấu liên kết cũ hết hiệu lực trong transaction của `CaseService`. Khóa chống request trùng dùng `pg_advisory_xact_lock` qua native query của JPA. Hibernate ánh xạ payload JSONB, còn các trigger và constraint tiếp tục bảo vệ lịch sử trong PostgreSQL.

POST create ghi atomically beneficiary + consent GRANTED đầy đủ metadata + SUBMITTED case + nhiều relational need lines + immutable fixture versions/links + case snapshot + CASE_CREATED audit + idempotency result. B04 events local-only, không blockchain_jobs/RPC. Actor lấy từ session, không body. Public consent false không được kèm publicTitle/publicSummary.

PATCH/E02/resubmit khóa case trước, kiểm tra creator VOLUNTEER, version và state. `@Version` + case row lock bảo vệ concurrent edit; stale trả409 VERSION_CONFLICT. Editable states SUBMITTED/CHANGES_REQUESTED. Need lines cũ được retire bằng version, không xóa; case_revisions giữ immutable snapshot. E02 retire current link projection và append version mới; bytes/hash/old links/snapshots vẫn còn. VERIFIED và các state khác không edit qua B04.

Resubmit chỉ CHANGES_REQUESTED, có consent/needs/evidence hợp lệ và một case revision mới sau latest request-changes review. Theo D42, V7 đổi decision `NEEDS_INFO` cũ thành `CHANGES_REQUESTED`, giữ nguyên thông tin review. Truy vấn resubmit đọc `verifications.decision='CHANGES_REQUESTED'` và dùng `verified_at` làm mốc. V8 (D43) thêm liên kết tới revision mà reviewer đã xem. Các bản ghi cũ có `reviewed_case_version` là `NULL`. B05 (`VerificationController`, `VerificationService`, `VerificationOutboxService`) đã triển khai đầy đủ ba endpoints `/verify`, `/reject`, `/request-changes`.

Idempotency key 16..128 visible ASCII chars; scope actor+operation (gồm resource ID)+key. Hash serialized validated DTO theo field order cố định, trim text trong DTO; array order giữ nguyên. PostgreSQL transaction advisory lock serialize same-key requests; completed response/status/Location lưu cùng business transaction. Same payload replay giữ original response và `Idempotent-Replayed:true`; changed payload409 IDEMPOTENCY_CONFLICT. Account/org/membership/role/creator được kiểm tra trước replay. Failed transaction không để claim/result/business records nửa chừng.

## HTTP contract đã implement, chưa chứng nhận runtime acceptance

| Endpoint | Điều kiện / result |
|---|---|
| POST /api/cases | ACTIVE FV + APPROVED org; CSRF + Idempotency-Key; CreateCaseRequest ->201 CaseDetailResponse + Location |
| GET /api/cases?organizationId=UUID&status=SUBMITTED&page=0&size=20 | Current own staff; size1..100; stable createdAt/id; CasePage `{items,page,size,totalElements}` |
| GET /api/cases/{id} | Current own staff; authorized private CaseDetailResponse, no entities |
| PATCH /api/cases/{id} | FV creator; expectedVersion; CSRF/key;200 CaseDetailResponse |
| POST /api/cases/{id}/resubmit | FV creator; expectedVersion + requirements trên; CSRF/key;200 CaseDetailResponse |
| GET /api/evidence-fixtures?organizationId=UUID | Own staff; metadata/key allowlist của org, không fixture bytes/path |
| POST /api/cases/{id}/evidence-versions | FV creator; expectedCaseVersion, fixtureKey, optional paired evidenceFileId/supersedesVersionId; CSRF/key;201 EvidenceVersionResponse |
| GET /api/evidence-versions/{id}/content | Related-case own staff; access audit + actual bytes, attachment, no-store, nosniff |
| POST /api/cases/{id}/verify | CV độc lập (khác creator); SUBMITTED, expectedVersion, exact current evidence IDs; CSRF/key;200 DecisionResponse (`PENDING_ON_CHAIN`) |
| POST /api/cases/{id}/reject | CV độc lập (khác creator); SUBMITTED, expectedVersion, required sanitized reason, exact current evidence IDs; CSRF/key;200 DecisionResponse (`NOT_APPLICABLE`) |
| POST /api/cases/{id}/request-changes | CV độc lập (khác creator); SUBMITTED, expectedVersion, required sanitized reason, exact current evidence IDs; CSRF/key;200 DecisionResponse (`NOT_APPLICABLE`) |

CaseDetailResponse hiện có id/org/creator, beneficiaryAlias, internal/public fields, priority/status/version, current needs/consent/evidence metadata, timestamps và supersedesCaseId. Review history/campaign/dispute/goods projections trong target DTO thuộc các task sau; không trả giả các tính năng đó đã triển khai. Không email/password hash/privateDataEncrypted/storageKey/fixture path trong response. Case summary không chứa consent/beneficiary/evidence details. Private GET/mutations/errors dùng no-store; cross-org/private ID404, known resource thiếu action role403, no session401. Platform admin không bypass membership hoặc tự đọc evidence.

## V6 và dữ liệu legacy

V1–V5 không sửa. V6 đổi case NEEDS_INFO->CHANGES_REQUESTED, MEDIUM->NORMAL; URGENT giữ nguyên. LOW/HIGH làm migration dừng để xin mapping riêng; không reset/drop dữ liệu. Guard cũng kiểm tra evidence case/delivery links dùng chung nhiều case hoặc khác org. V6 thêm case version/private-public fields, explicit consent relation/metadata, retired need/link projections, fixed file/version case+org ownership, supersedes relation, case_revisions, idempotency_records và audit private payload. Immutable-fact triggers chặn UPDATE/DELETE consent/evidence/audit/snapshots/idempotency; không tuyên bố chống được DB superuser.

Legacy title được copy vào internal_title (cột200 giữ original lengths; request mới tối đa160). title/public_description/region_label cũ giữ nguyên nhưng không dùng làm public projection mới. Không tự copy public_description sang publicSummary, không tạo consent/capturedAt/public consent giả. Legacy thiếu consent metadata vẫn đọc riêng theo quyền nhưng không tiếp tục mutation/resubmit. Legacy evidence không xác định được fixture key không có download; cần remediation có chủ ý, không mở arbitrary path.

## Private fixture configuration

Ba simulated classpath resources nằm `src/main/resources/private-evidence`, không nằm static/public. Các key cố định: household-note-v1, household-note-v2, consent-note-v1. Hash/MIME/size từ actual bytes, không client metadata. Chỉ cấp key theo org trong server configuration; default không grant cho org nào. Dùng Program arguments của cấu hình AidBridge, thay `YOUR_ORGANIZATION_UUID` bằng UUID approved organization hiện có:

```text
--aidbridge.evidence.organization-fixtures[YOUR_ORGANIZATION_UUID]=household-note-v1,household-note-v2,consent-note-v1
```

Đây là nonsecret config; không sửa database để tự cấp membership hoặc bật onboarding. Test classes tự cấu hình các fixture grants cho test orgs. Không nhập URL/path/upload; unknown key/cross-org404. Resource bytes của version cũ phải giữ nguyên qua các build; download đối chiếu stored hash/size và trả503 nếu bytes không khớp. Việc cấp/thu hồi grant thuộc cấu hình server, không client request.

## Chạy tests bằng IntelliJ

Mở backend module `backend/aidbridge-backend`. Chạy từng class bằng nút cạnh class, dùng environment của JUnit config đã dùng cho AuthApiIntegrationTest:

```text
AIDBRIDGE_TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/aidbridge_test
AIDBRIDGE_TEST_DATABASE_USERNAME=aidbridge_app
AIDBRIDGE_TEST_DATABASE_PASSWORD=<set privately in the run environment>
```

Chạy B04MigrationIT, CaseApiIT, EvidenceAccessIT; rồi AuthApiIntegrationTest regression. Test guards chỉ chấp nhận localhost/127.0.0.1:5432/aidbridge_test và role aidbridge_app. Schema random b04_it_/b04_migration_ tạo bằng Flyway trong test DB đã có; không CREATE DATABASE, clean/drop hoặc dev DB. Test schemas/fixtures được giữ lại. HTTP tests dùng embedded server/random port và actual production controllers/services/PostgreSQL; SQL fixtures giả lập B05 state, không tạo B05 endpoints. Mockito spy chỉ inject failure ở audit trong rollback test.

Coverage: first checkpoint create two needs/read/cross-org; validation/consent/public consent/duplicate lines/quantities; missing session/CSRF/key; wrong role/creator, disabled account/revoked role/membership/org; current scoped reads; same-key sequential/concurrent replay and payload/org conflicts; concurrent/stale edits; audit-failure rollback; evidence hashes, private downloads/read audit, traversal/URL/key rejection, immutable old bytes/links/snapshots và database overwrite rejection; logs không chứa private markers/credentials. B04MigrationIT kiểm tra clean V8, upgrade qua V6, guard legacy, chuẩn hóa decision V6 → V7 và liên kết review với revision khi upgrade V7 → V8. DatabaseMigrationTest B02 kiểm tra target V5.

Đừng dùng unfiltered `mvn test` với dev credentials: repository hiện còn contextLoads/default tests dùng main configuration. Command có selection và kết quả agent ghi trong Task Backlog; missing env phải báo SKIPPED. Cross-owner review, PostgreSQL results và integration checkpoint còn pending; không ghi B04 Done trước khi có bằng chứng.

## B05 implementation và C05 handoff

- **Endpoints & transitions:** `VerificationController` và `VerificationService` thực thi `POST /api/cases/{id}/verify` (`SUBMITTED -> VERIFIED`), `POST /api/cases/{id}/reject` (`SUBMITTED -> REJECTED`) và `POST /api/cases/{id}/request-changes` (`SUBMITTED -> CHANGES_REQUESTED`).
- **Quyền, độc lập reviewer và khóa giao dịch:** Bắt buộc active `VERIFIER` membership trong owning `APPROVED` organization và `reviewerId != createdBy` (kể cả khi một tài khoản giữ nhiều role). Luôn khóa `cases` (`SELECT ... FOR UPDATE`) trước `idempotency.execute` theo đúng thứ tự với B04 để tránh deadlock giữa hai reviewer hoặc giữa reviewer và creator.
- **Snapshot bất biến và tương thích resubmit:** Kiểm tra khớp `expectedVersion`, trạng thái `SUBMITTED`, tập `reviewedEvidenceVersionIds` bằng đúng tập current evidence của case và sự tồn tại của dòng `case_revisions(case_id, reviewed_case_version)`. Mỗi quyết định ghi dòng bất biến `verifications(case_id, verifier_id, decision, reviewed_case_version, notes)` và snapshot bằng chứng `verification_evidence_versions`. Khi `request-changes`, service không ghi `case_revisions` mới để giữ nguyên quy tắc `CaseService.resubmit` yêu cầu ít nhất một chỉnh sửa (`created_at > verified_at`) từ phía creator trước khi nộp lại.
- **Tách trục SQL và blockchain (handoff sang C05):**
  - `verify`: `VerificationOutboxService.recordCaseVerified` ghi đồng thời `audit_events` (`event_type = 'CASE_VERIFIED'`, `commitment_hash` SHA-256 canonical JSON 64 hex) và `blockchain_jobs(status = 'PENDING', attempt_count = 0)` trong cùng SQL transaction; HTTP trả `status = "VERIFIED"`, `blockchainStatus = "PENDING_ON_CHAIN"`. Trạng thái duyệt SQL (`VERIFIED`) tách biệt hoàn toàn với đồng bộ on-chain (`PENDING`); không mô tả job `PENDING` là đã xác nhận trên Fuji.
  - `reject` / `request-changes`: `LocalAuditService.appendVerification` chỉ ghi `audit_events` cục bộ (`CASE_REJECTED` / `CASE_CHANGES_REQUESTED`, `commitment_hash = null`), không tạo `blockchain_jobs`; HTTP trả `blockchainStatus = "NOT_APPLICABLE"`.
  - **C05 scope (Khoa):** nhận `blockchain_jobs(PENDING)` và gói mẫu `CASE_VERIFIED Design Fixture & ABI Review Bundle` (`D48`) để nối Web3j/Fuji relayer, gửi giao dịch và đối chiếu receipt/event trước khi chuyển job sang `CONFIRMED`.
