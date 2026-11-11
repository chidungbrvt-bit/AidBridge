# Database Design

Ngày tạo: 03/11/2026. Cập nhật:04/11/2026. Owner: B. Status: **baseline thống nhất In Review**; V1–V5 đã có SQL, chưa kiểm thử migration toàn bộ. Sources: PDF p.12; chi tiết bổ sung để thực thi BR và lựa chọn multi-item của user. [B01 baseline](B01_BASELINE.md) phân biệt schema hiện có, design target và implementation gaps. SQL V3–V5 được ghi theo user cung cấp; xem D38 về các điểm còn cần review.

## Conventions

Implementation update07/11/2026: user chọn D41/A; [B04 runbook](B04_CASE_WORKFLOW.md) mô tả forward V6 đã viết. V1–V5 giữ nguyên; clean/upgrade PostgreSQL V6 chưa chạy trong tool terminal. V6 thêm case version/private-public fields/explicit consent relation, consent metadata, fixed-case/org evidence ownership, immutable snapshots/audit, retired need/link projections và actor-operation-key journal. Guard dừng legacy LOW/HIGH hoặc evidence ownership mâu thuẫn; không invent consent hoặc public authorization. Schema target toàn P0 phía dưới vẫn gồm các phần tương lai; B04 chỉ implement phần đã nêu, không migration goods/worker hoặc B05.

PostgreSQL version/endpoint lấy từ installation do user quản lý và cần xác nhận ở B02; instance/script/version-pin trong workspace đã gỡ theo D37. Focused PostgreSQL18.6 tests trước là evidence lịch sử, không là xác nhận kết nối database mới. Hosted version phải review riêng trước deploy. `uuid` PK cho operational records; `bytea` length=32 cho chain references/commitments; `timestamptz` UTC; quantities `integer` CHECK >=0 và input <=1,000,000 mỗi line để tránh overflow. IDs không encode tên/số điện thoại. String status dùng varchar + CHECK để Flyway dễ evolve; không PostgreSQL enum types.

Mutable business aggregates cần optimistic edit/version checks có `created_at`, `updated_at`, `version bigint NOT NULL DEFAULT 0`; V1 users/organizations hiện chỉ có timestamps, memberships dùng joined_at, không tự thêm @Version khi chưa có cột. Immutable records có `created_at`, không UPDATE/DELETE từ application role sau insert (status/projection thuộc bảng mutable tương ứng). Every org-owned aggregate có `organization_id NOT NULL`; cross-aggregate references phải verify cùng org/campaign. Foreign-key delete mặc định RESTRICT; không cascade-delete audit/evidence history.

## Conceptual ERD

```mermaid
erDiagram
  USERS ||--o{ ORGANIZATION_MEMBERSHIPS : joins
  ORGANIZATIONS ||--o{ ORGANIZATION_MEMBERSHIPS : contains
  ORGANIZATION_MEMBERSHIPS ||--o{ MEMBERSHIP_ROLES : grants
  ORGANIZATIONS ||--o{ AID_CASES : owns
  BENEFICIARIES ||--o{ AID_CASES : concerns
  AID_CASES ||--|{ CASE_NEED_ITEMS : requests
  BENEFICIARIES ||--o{ CONSENT_RECORDS : has
  AID_CASES ||--o{ VERIFICATIONS : reviewed_by
  AID_CASES ||--o| CAMPAIGNS : publishes
  CAMPAIGNS ||--|{ CAMPAIGN_ITEMS : targets
  USERS ||--o{ CONTRIBUTIONS : pledges
  CONTRIBUTIONS ||--|{ CONTRIBUTION_ITEMS : contains
  CAMPAIGN_ITEMS ||--o{ CONTRIBUTION_ITEMS : receives_pledges
  CONTRIBUTIONS ||--o{ GOODS_RECEIPTS : receives
  GOODS_RECEIPTS ||--|{ GOODS_RECEIPT_ITEMS : contains
  CONTRIBUTION_ITEMS ||--o{ GOODS_RECEIPT_ITEMS : fulfills
  AID_CASES ||--o{ DELIVERIES : delivers
  DELIVERIES ||--o{ DELIVERY_ITEMS : contains
  CAMPAIGN_ITEMS ||--o{ DELIVERY_ITEMS : allocated
  DELIVERIES ||--o{ DELIVERY_CONFIRMATIONS : reviewed_by
  AID_CASES ||--o{ DISPUTES : controlled_by
  EVIDENCE_FILES ||--|{ EVIDENCE_VERSIONS : versions
  AID_CASES ||--o{ CASE_EVIDENCE_LINKS : references
  DELIVERIES ||--o{ DELIVERY_EVIDENCE_LINKS : references
  AUDIT_EVENTS ||--o| BLOCKCHAIN_JOBS : anchors
  BLOCKCHAIN_JOBS ||--o{ BLOCKCHAIN_TRANSACTIONS : attempts
```

## Relational schema

Trong bảng dưới, `id uuid PK` và common timestamps được ngầm hiểu trừ bảng có composite key được ghi rõ. `FK` là foreign key; `UQ` unique; mọi required field NOT NULL trừ `?`.

| Table | Columns chính / relationships | Constraints / ownership / indexes |
|---|---|---|
| organizations | V1: name varchar(200), description text?, status PENDING/APPROVED/SUSPENDED default PENDING, created_at/updated_at | Operational writes chỉ APPROVED; public_ref bytea32 UQ NOT NULL và relayer_address varchar(42)? đã thêm ở V9; approved_by FK users?, approved_at? vẫn là planned extension; relayer secret không trong row |
| users | V1: email varchar(255), password_hash varchar(255), full_name varchar(150), status ACTIVE/DISABLED default ACTIVE, platform_role USER/PLATFORM_ADMIN default USER, created_at/updated_at | UQ lower(email); donor_enabled boolean NOT NULL default false là design extension chưa có trong V1/User; explicit donor grant, không mặc định mọi USER là donor; seed secrets không plaintext |
| organization_memberships | V1: organization_id FK, user_id FK, status ACTIVE/INVITED/DISABLED default ACTIVE, joined_at | UQ(org,user); index(user); chỉ ACTIVE cấp quyền; không invitation UI |
| membership_roles | V1: membership_id FK, role varchar(40) | Composite PK(membership_id,role); CHECK ORG_ADMIN/VOLUNTEER/VERIFIER/DELIVERY_CONFIRMER; không gán PLATFORM_ADMIN hoặc DONOR ở org role |
| beneficiaries | organization_id FK, alias varchar(120), private_details_ciphertext bytea?, key_version?, approximate_area varchar(160) | No globally dedup hash of identity; UQ(id,org) hỗ trợ composite FK; index org |
| consent_records | organization_id FK, beneficiary_id FK, recorded_by FK users, basis CONSENT/REPRESENTATIVE, scope varchar(200), captured_at timestamptz, public_summary_allowed boolean, evidence_version_id? | Immutable; index beneficiary/time; demo luôn fictional consent, không legal compliance assertion |
| aid_cases | organization_id FK, beneficiary_id FK, consent_record_id FK, creator_id FK users, status, public_ref bytea32 UQ, internal_title varchar(160), public_title?, public_summary?, approximate_area?, priority NORMAL/URGENT, submitted_at, completed_at?, supersedes_case_id FK self? | Composite FKs beneficiary/consent org; index(org,status,created_at); version for PATCH; reviewed fields locked |
| case_need_items | organization_id FK, case_id FK, name varchar(120), unit varchar(40), requested_qty integer | requested_qty>0; UQ(case,lower(name),unit); editable only with case version before approval; reviewed snapshot captures IDs/values |
| verifications | organization_id FK, case_id FK, reviewer_id FK users, decision, reason?, reviewed_case_version bigint, snapshot jsonb, reviewed_at | Immutable; reason required for rejection/changes; index(case,reviewed_at); creator!=reviewer service check under case lock |
| campaigns | organization_id FK, case_id FK UQ, status DRAFT/PUBLISHED/FULFILLED/CLOSED, title varchar(160), public_summary varchar(2000), published_at?, closed_at?, version bigint (V10; cột case thực tế là `aid_case_id`) | One case/one campaign; UQ(id,organization_id); FK(aid_case_id,organization_id)->aid_cases(id,organization_id); index(status,published_at,created_at,id) và (organization_id,status,created_at,id); no beneficiary PII |
| campaign_items | organization_id FK, campaign_id FK, case_need_item_id FK, item_name varchar(120), unit varchar(40), target_quantity integer (V10) | UQ(campaign,case_need_item); UQ(campaign,lower(item_name),lower(unit)); FK(campaign_id,organization_id)->campaigns(id,organization_id); target 1..1000000; copy all approved need lines, exact approved quantities; targets immutable after publish |
| contributions | organization_id FK, campaign_id FK, donor_id FK users, public_ref bytea32 UQ, created_at | Immutable identity header; progress derived from items; index(donor,created_at), campaign |
| contribution_items | organization_id FK, contribution_id FK, campaign_item_id FK, pledged_qty integer, cancelled_qty integer default0 | UQ(contribution,campaign_item); pledged>0; 0<=cancelled<=pledged; same campaign composite FK/service validation; lock on receipt/cancel |
| goods_receipts | organization_id FK, contribution_id FK, recorder_id FK users, received_at timestamptz, note? | Immutable; index(contribution,received_at); active pledge check |
| goods_receipt_items | organization_id FK, receipt_id FK, contribution_item_id FK, quantity integer | quantity>0; UQ(receipt,contribution_item); same contribution/org; sum bound via locks |
| deliveries | organization_id FK, case_id FK, recorder_id FK users, public_ref bytea32 UQ, outcome DELIVERED/FAILED, recorded_at, handed_over_at?, reason? | Immutable claim fields; current review/void status derived from confirmation/dispute decisions, no duplicated mutable status column; failed reason required |
| delivery_items | organization_id FK, delivery_id FK, campaign_item_id FK, quantity integer | quantity>0 for successful delivery; FAILED has no rows; UQ(delivery,item); same case campaign |
| delivery_confirmations | organization_id FK, delivery_id FK, reviewer_id FK users, decision CONFIRMED/REQUIRES_REVIEW, reason?, evidence_snapshot jsonb, reviewed_at | Immutable decisions; partial UQ(delivery_id) WHERE decision='CONFIRMED'; index(delivery,time); voided delivery retains historical decision, replacement uses a new delivery ID; self-confirm guard service |
| disputes | organization_id FK, case_id FK, delivery_id FK?, opened_by FK users, reason varchar(2000), status OPEN/RESOLVED, resolution UPHOLD/VOID_DELIVERY?, resolved_by FK users?, resolution_reason?, resolved_at? | delivery must belong case; VOID_DELIVERY requires delivery_id; index(case,status); resolution fields all-or-none; immutable opening fields. Hiện trạng SQL V5 (đối chiếu 11/11/2026): `aid_case_id`, `delivery_id?`, `opened_by`, `status` OPEN/UNDER_REVIEW/RESOLVED/REJECTED, `reason text`, `resolution_note?`, `resolved_by?`, `resolved_at?`; chưa có `organization_id`/`resolution`, B10 cần forward migration |
| evidence_files | organization_id FK, logical_name varchar(160), case_id FK, created_by FK users | Logical artifact identity, no public URL; evidence all belongs one case |
| evidence_versions | organization_id FK, evidence_file_id FK, version_no integer, fixture_key varchar(120), mime_type, byte_size bigint, sha256 bytea32, created_by FK users, supersedes_id FK self? | UQ(file,version_no); immutable; fixture allowlist only; CHECK byte_size>=0 and hash length32; no arbitrary filesystem paths |
| case_evidence_links | organization_id FK, case_id FK, evidence_version_id FK, attached_by FK users | Composite PK(case,version); immutable; same case/org; approved snapshot does not follow newest version automatically |
| delivery_evidence_links | organization_id FK, delivery_id FK, evidence_version_id FK | Composite PK(delivery,version); immutable; version must belong owning case |
| audit_events | organization_id FK, case_id FK?, actor_id FK users?, event_type varchar(64), event_id bytea32 UQ, schema_version integer, sequence_no bigint, entity_id uuid, request_id uuid, payload_private jsonb, public_projection jsonb, occurred_at | Immutable; UQ(case,sequence_no) where case present; index(org,time),(case,sequence); no credentials/PII in public projection |
| idempotency_records | actor_id FK users, operation varchar(120), key varchar(128), request_digest bytea32, response_status integer, response_private jsonb, completed_at | UQ(actor,operation,key); record written atomically with mutation; body excludes passwords/keys; retain demo lifetime, no expiry replay hole |
| blockchain_jobs | audit_event_id FK UQ, organization_id FK, case_id FK?, logical_event_id bytea32 UQ, chain_sequence bigint?, deployment_id varchar(80), state, dependency_event_id bytea32?, next_attempt_at, lease_until?, attempt_count integer, commitment bytea32, manifest_private jsonb, salt_ciphertext bytea, last_error_code? | Mutable sync state only; UQ(case_id,chain_sequence) for case milestones; org registration has no case sequence; index(state,next_attempt_at); immutable target/context/commitment; dependency prevents out-of-order anchoring |
| blockchain_transactions | job_id FK, chain_id bigint, contract_address varchar(42), signer_address varchar(42), nonce bigint, tx_hash varchar(66) UQ, signed_tx_ciphertext bytea, attempt_state, receipt_private jsonb?, block_number bigint?, block_hash?, prepared_at,broadcast_at?, checked_at? | UQ(chain,signer,nonce) for no automatic fee replacement MVP; length/nonce checks; index(job,prepared_at); attempts never overwrite earlier hashes |
| blockchain_sync_cursors | chain_id bigint, contract_address varchar(42), deployment_block bigint, next_block bigint, last_verified_block_hash?, last_scan_at? | Composite PK(chain,contract); next_block>=deployment_block; no volatile-only cursor |

Để deduplicate observed logs, thêm `blockchain_log_observations` (chain_id, tx_hash, log_index composite PK, contract_address, event_id, block_number, block_hash, observed_at). Đây là projection/reconciliation ledger, không business event mới. Contract deployments được lưu trong reviewed deployment manifest và `deployment_id` cấu hình; chưa cần deployment management service.

## Implementation notes cho constraints

SQL CHECK không kiểm tra được sums qua rows hoặc user identity ở bảng khác. Các bounds BR-02/04/06/07/ownership nằm ở transactional services; FK/unique/check là defense-in-depth. Không ghi aggregate counters riêng trong MVP; tính sums dưới item locks với indexes. Nếu tối ưu counters sau này phải có reconciliation tests.

Để composite FK nhất quán, parent org-owned table thêm UQ(id,organization_id); child FK(parent_id,organization_id). Với item-campaign/contribution correlation, repository loads validated joins dưới locks; migrations có composite unique keys phù hợp thay vì chỉ tin body IDs. Chỉ dùng jsonb cho immutable review/audit snapshots, không giấu relational goods lines trong JSON.

Một delivery có nhiều review decisions nhưng chỉ một CONFIRMED decision được insert thành công; duplicate request dùng idempotency, different-key double confirmation 409. Khi VOID_DELIVERY, giữ confirmation row nhưng aggregate join loại delivery có resolved VOID_DELIVERY dispute. Re-review sau REQUIRES_REVIEW tạo decision mới; old rows bất biến.

Cancelled remainder có audit COMMITMENT_REDUCED và chain job; cancelled_qty là projection có version, original pledge immutable. Receipt corrections không hỗ trợ PATCH/reverse trong P0; flag dispute, giữ record và không complete sai. Mở rộng reversal cần decision riêng.

## Sensitive-data boundaries / retention

- Public projection chỉ title/approximate area đã sanitize, item targets/totals, milestone type/time và chain proof; không internal user email, beneficiary alias hay private file key.
- Optional private_details chỉ simulated và encrypted khi persisted; key ở secret store, không database. Không collect health/ID/address thật trong demo.
- Evidence fixture bytes nằm private backend path; DB lưu allowlisted fixture_key, SHA-256, MIME/size. GET file qua ownership guard, no direct URL.
- File/evidence versions và audit không soft-delete để che lịch sử. Suspend users/org bằng flags; không hard-delete khi còn business references.
- Demo dataset backup/reset sau submission theo retention policy trong Security; không cascade xóa chain history. Real data retention và deletion workflow cần operational/legal review trước pilot.

## Transaction scopes và Flyway plan

| Migration proposal | Objects | Verification |
|---|---|---|
| V1 identity | organizations/users/memberships/roles | FK/role constraints, lower-email unique, bootstrap sau schema |
| V2 cases/evidence | beneficiary/consent/cases/reviews/evidence/version links | Ownership FKs; immutable snapshot; case version checks |
| V3 campaigns/goods | campaign/items/contributions/items/receipts/items | Positive quantities, same campaign, concurrency services |
| V4 delivery/control | deliveries/items/confirmations/disputes | Duplicate confirmation, immutable decision history |
| V5 audit/chain | audit/idempotency/jobs/transactions/cursors/log observations | UQ logical IDs/nonce; pending indexes; durable retry |

Hiện trạng04/11: V1–V5 đã có SQL. V2 do user điền; V3 campaign/contribution/receipt, V4 delivery/confirmation và V5 evidence/consent/dispute/audit/blockchain ghi theo SQL user gửi. Bảng proposal phía trên là design target, chưa khẳng định SQL hiện tại đã đáp ứng đủ. Kiểm tra tĩnh V1–V5:26 tables,29 explicit indexes, không trùng tên bảng/index, ngoặc cân bằng và reference trỏ tới bảng/cột đã khai báo; chưa chạy PostgreSQL/Flyway clean/upgrade. Identity extensions donor_enabled/public_ref/relayer/approval metadata và các gaps D38 vẫn cần review; không sửa migration đã apply. Application setup dùng Flyway validate, không Hibernate ddl-auto update. Schema test từ empty PostgreSQL và upgrade từ version trước; bootstrap simulated accounts/evidence ở demo-only profile/tooling, không plaintext passwords trong migration. Deployment không rollback bằng xóa migration đã apply; dùng forward fix và backup restore khi cần.

Hiện trạng 11/11 (B06): `V10__reconcile_campaign_schema.sql` là forward migration xử lý gap D38 cho `campaigns`/`campaign_items` (thêm `organization_id`, `public_summary`, `closed_at`, `version`, `item_name`, `unit`; chuẩn hóa status DRAFT/PUBLISHED/FULFILLED/CLOSED; composite FKs theo organization). V10 có nhánh backfill cho dữ liệu cũ (ánh xạ ACTIVE→PUBLISHED, COMPLETED→FULFILLED, PAUSED/CANCELLED→CLOSED) nhưng nhánh này chưa được test trên dữ liệu thật; `CampaignApiIT` chỉ chứng minh clean V1–V10 và `ddl-auto=validate`.
