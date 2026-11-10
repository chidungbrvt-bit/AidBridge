# Test Strategy

Ngày tạo: 03/11/2026. Cập nhật:04/11/2026. Owner: C, B/F review. Status: **P0 test specification**; một số focused user/auth tests đã chạy, evidence ở Task Backlog; chưa có full P0/E2E/contract acceptance. Không coi spec IDs dưới đây tự động Pass vì các focused tests pass.

## Levels, tools và entry/exit

| Level | Tool proposal / scope | Owner | Entry -> exit |
|---|---|---|---|
| Unit | JUnit service/domain guards, canonical manifest vectors | B/C | Agreed rules -> success/failure boundary assertions |
| API/DB integration | Spring tests + PostgreSQL đúng version B02 xác nhận;18.6/instance tests trước chỉ là lịch sử, instance workspace đã gỡ. Testcontainers/Compose chỉ khi được chọn và khả dụng | B | Migrated schema -> auth/isolation/state/concurrency/rollback verified trên target DB mới; không chuyển kết quả instance đã gỡ thành P0/full-app acceptance |
| Contract | Hardhat3 TypeScript, local isolated accounts, decoded event assertions | C | Frozen ABI -> all access/state/replay tests pass |
| Adapter integration | Web3j -> local Hardhat node; controlled RPC stubs for faults | C | Compiled ABI -> receipt/restart/reconciliation verified |
| E2E | Playwright core workflows, seeded fictional fixtures | F | Stable local/shared API -> UI totals/roles/statuses correct |
| Fuji smoke | Dedicated test deployment/tx with actual receipt+event | C/F | Local security tests pass -> explorer evidence recorded |

Use real PostgreSQL for locking/unique constraints, không H2 thay behavior concurrency. Tests isolated, reset simulated data, no paid RPC dependency for routine CI. Fuji smoke không chạy mọi test suite. Không đặt arbitrary coverage % thay behavior acceptance; security/integrity cases mandatory.

## Complete BR traceability matrix

Mỗi test ID dưới đây là specification ID; task phải bổ sung test path/command/result khi implement.

[B01 baseline](../architecture/B01_BASELINE.md) thống nhất OA=ORG_ADMIN, FV=VOLUNTEER, CV=VERIFIER, DC=DELIVERY_CONFIRMER, PA=PLATFORM_ADMIN; donor cần ACTIVE account + explicit donorEnabled grant (schema extension chưa triển khai). Role aliases chỉ để đọc matrix, không là enum khác trong fixtures. B01 đã viết các tình huống review BR01/BR02/BR03; runtime tests cho các BR này vẫn chờ implementation/integration. Test login/role revocation phải kiểm tra DISABLED user, non-ACTIVE membership, non-APPROVED org và donor grant bị thu hồi, kể cả replay idempotency.

| Rule / failure consequence | Module / enforcement | Positive test / action -> expected | Negative test / action -> expected | Owner / tasks / priority |
|---|---|---|---|---|
| BR-01: impersonated organization | organization/case service + scoped query | BR01-P active FV creates own-org case ->201/SUBMITTED | BR01-N outside/inactive membership ->403/404, no rows/outbox | B; B03,B04,B11; P0 |
| BR-02: creator approves fabricated need | verification service identity check under lock | BR02-P other CV approves ->snapshot/VERIFIED/job | BR02-N same creator even dual-role ->403, no review/job | B; B05,B11; P0 |
| BR-03: unverified public solicitation | campaign publish transaction | BR03-P verified needs copied/published exact ->public campaign | BR03-N unverified/rejected/stale review ->409, no public campaign | B; B06; P0 |
| BR-04: excess pledges / wrong allocation | contribution service row locks + quantity checks | BR04-P remaining capacity multi-line pledge ->atomic201 | BR04-N two simultaneous pledges exceed one line ->only allowable commit, other409; other lines rollback | B; B07,B11; P0 |
| BR-05: false outcome / missing stock | receipt/delivery separate records | BR05-P pledge2 ->receive1 ->deliver1 ->confirm1 shows four distinct totals | BR05-N pledge alone cannot delivery/complete; overreceipt/overdelivery409 | B/F; B08,B09,F08; P0 |
| BR-06: recorder verifies own false claim | delivery confirmation identity check | BR06-P other DC confirms ->decision/confirmed totals/job | BR06-N recorder dual-role confirms ->403, no totals/job change | B; B10,B11; P0 |
| BR-07: disputed case closes | case/campaign/dispute transaction | BR07-P resolved disputes+all targets confirmed ->close200 | BR07-N open dispute or concurrent flag+close ->cannot end COMPLETED with open dispute | B; B10,B11; P0 |
| BR-08: permanent sensitive disclosure | public DTO/manifest builder/ABI allowlists | BR08-P random refs+salted commitment only in signed calldata/log | BR08-N forbidden PII/URL marker injected fixture ->absent from calldata/log/public response/error | C/B/F; C01,C06,C11,F11; P0 |
| BR-09: evidence silently replaced | evidence version service + immutable snapshots | BR09-P editable case new version keeps original bytes/hash/audit | BR09-N overwrite/version reuse or edit reviewed snapshot ->409; prior manifest unchanged | B; B04,B05,B11; P0 |
| BR-10: false chain confirmation | adapter receipt+event verifier, UI | BR10-P exact status1 receipt/event/context ->CONFIRMED_ON_CHAIN | BR10-N status0/null/wrong contract/wrong event ->never confirmed | C/F; C05,C08,C11,F05; P0 |
| BR-11: duplicate pledge/delivery | HTTP idempotency+DB uniques+contract eventIds | BR11-P same request/key retry returns original ID/totals | BR11-N changed payload same key409; repeated eventId reverts; replay cannot increment twice | B/C; B07,B09,C06,C07,C11; P0 |
| BR-12: unauthorized chain writer | AccessControl+org-scoped relayer | BR12-P registered active org relayer emits exact event | BR12-N unknown/revoked/wrong-org caller reverts; no event | C; C03,C06,C11; P0 |

## Integration/failure scenarios

| ID / precondition | Action | Expected DB / chain / UI / recovery | Rule / owner / type / priority |
|---|---|---|---|
| IT01 verified case DB commit; RPC offline | Worker submits | VERIFIED persists, job pending/retry; chain unchanged; UI Pending on-chain; no long SQL tx | BR10; C C07/C08; integration P0 |
| IT02 send accepted, response lost | Restart worker | Persisted hash/bytes reused, same nonce; at most one logical chain event; no duplicate quantities | BR10/11; C C07; integration P0 |
| IT03 tx mined, receipt fetch timeout | Poll later/reconcile logs | Job pending until exact proof; then repair confirmed projection without resubmit | BR10; C C08; integration P0 |
| IT04 receipt confirmed, DB save fails | Restart and scan | Original event repairs job; cursor/log unique avoids duplicates | BR10/11; C C08; integration P0 |
| IT05 donor pledge, no actual receipt | Try delivery/close | received/delivered/confirmed0; API409; UI still pledged, no successful delivery claim | BR05; B/F B08/F08; integration P0 |
| IT06 delivery recorded, nobody confirms | Try close | Delivery DELIVERED, confirmed0, reserved stock unchanged, case not complete | BR05/06; B B10; integration P0 |
| IT07 all items confirmed, dispute opened | Try close; later resolve | Open blocks/reopens completion; resolve does not auto-confirm/reclose; chain flag/resolve order retained | BR07; B/C B10/C06; integration P0 |
| IT08 two reviewers hold same case version | Approve vs reject concurrently | Exactly one decision applies, other409; no contradictory successful audit/jobs | BR02/03; B B05; DB P0 |
| IT09 orgA private IDs known to orgB | GET/PATCH/file/reference API | 404/403, no resource data, binary, timing-independent public explanation; no mutation | BR01/08; B/F B11/F11; integration P0 |
| IT10 commitment food2/hygiene2, received food2/hygiene1 | Deliver hygiene2 or close after partial | Overdelivery409; case not complete while hygiene missing; food unaffected | BR04/05; B B08/B09; DB P0 |
| IT11 two staff record last received stock concurrently | Deliver each same available quantity | At most one consumes remaining; other409, no oversubscription | BR05/11; B B09/B11; DB P0 |
| IT12 open dispute on confirmed delivery | PA voids, then FV creates replacement | Old rows/events retained; aggregate excludes voided; replacement new ID; separate DC review required | BR06/07/09; B/C B10/C06; integration P0 |
| IT13 receipt status1 but expected event missing | Process receipt | No false confirmation; check duplicate original via processedEvent/log; alert mismatch | BR10/11; C C08/C11; adapter P0 |
| IT14 different private evidence bytes/version | Recompute commitment | Different commitment; fixed golden vector Java/TS agree; salt private | BR08/09; C C01/C11; unit/adapter P0 |
| IT15 role revoked after login/replay stored | Retry old command key | Active membership rechecked; no private response leak or new write | BR01/12; B B03/B11; integration P0 |
| IT16 failed handover attempt | Record FAILED with no items | No delivered stock decrement; audit local; nonzero qty+FAILED rejected | BR05; B/F B09/F08; integration P0 |

## Contract suite details

SC01 register/read org, zero refs, unauthorized admin call; SC02 correct/wrong/revoked org relayer; SC03 verification first sequence and single case ownership; SC04 duplicate same event vs conflicting digest; SC05 pledge/receipt refs same case and org; SC06 recorded delivery then confirm, no absent/double/void confirm; SC07 dispute count and multiple disputes resolve independently; SC08 dispute after confirmation, void/replacement; SC09 out-of-order sequence/dependency rejected; SC10 event args/schemaVersion/commitment exact; SC11 no payable flow/token functionality and no unintended external call. Owner C; C03/C06/C11. Local tests include separate callers, not only happy-path deployer.

## E2E suite

E2E01 happy multi-item lifecycle with distinct FV/CV/DC/OA/donor accounts; E2E02 rejected/request-changes case and self-approval; E2E03 pledge retry/remaining capacity; E2E04 self-confirm/open dispute/close prevention; E2E05 pending/failed/confirmed badges and explorer URL network; E2E06 public campaign/audit privacy and cross-org access; E2E07 keyboard form validation/focus/loading/error/empty states. Owner F with B/C support; F11. Local chain/stub scenarios labeled simulated; one separate real Fuji smoke confirms actual integration.

## Test data and results discipline

D42–D45 và B05 (09/11/2026) bổ sung kiểm thử cho V7, V8, outbox và các endpoints thẩm định độc lập:
- `casefile/B04MigrationIT`: kiểm tra clean đến V8, upgrade V6 → V7 chuẩn hóa `NEEDS_INFO` sang `CHANGES_REQUESTED`, và upgrade V7 → V8 liên kết `verifications.reviewed_case_version` với `case_revisions` của đúng hồ sơ.
- `casefile/VerificationOutboxIT` (4 tests): lưu 1 `CASE_VERIFIED` audit + 1 `PENDING` job cho mỗi approval, chống trùng `event_key`, rollback nguyên tử cùng quyết định, từ chối khác org / khác trạng thái `APPROVED` / thiếu `reviewed_case_version`, và bắt buộc có transaction hiện hành.
- `casefile/VerificationApiIT` (10 tests): bao phủ `POST /api/cases/{id}/verify`, `/reject`, `/request-changes`; kiểm tra duyệt/từ chối/yêu cầu sửa đúng revision, bắt buộc và làm sạch `reason` cho `/reject` và `/request-changes`, idempotency replay (`200` + `Idempotent-Replayed: true`) và conflict (`409 IDEMPOTENCY_CONFLICT`), chặn tự thẩm định kể cả khi có đồng thời `VOLUNTEER` + `VERIFIER` (`403 FORBIDDEN`), chặn khác tổ chức (`404`), thu hồi membership trước replay (`404`), từ chối stale version (`409`) hoặc bộ bằng chứng thiếu/trùng/đã supersede (`400`), cạnh tranh đồng thời giữa 2 `VERIFIER` (duyệt-duyệt và duyệt-từ chối chỉ commit đúng 1 quyết định), rollback khi lỗi sau khi ghi outbox, và vòng đời tích hợp `request-changes → PATCH + thêm evidence version → resubmit → independent verify`.
- `casefile/CaseRequestValidationTest` (5 tests): bổ sung kiểm thử chuẩn hóa và giới hạn của `DecisionRequest`.

B04 tests07/11 (D41/A): `casefile/CaseApiIT`, `EvidenceAccessIT`, `B04MigrationIT` chạy production HTTP + installed PostgreSQL trong schemas riêng của aidbridge_test; guards từ chối dev URL hoặc account khác aidbridge_app. Không clean/drop/provision DB. Coverage BR01/08/09/11, IT09/15: two-line create/read, tenant/role/creator/revocation, consent/public consent/validation, session/CSRF, version/concurrent edit, idempotency same-key/payload/org conflicts, audit-failure rollback, evidence byte/hash/history/access audit/traversal, immutable overwrite rejection và private log markers. B04MigrationIT có clean V6, upgrade-preserve và legacy guard tests. Đây là tests đã viết và compile, chưa ghi Pass cho PostgreSQL khi thiếu env. `CaseRequestValidationTest`/`EvidenceFixtureCatalogTest` kiểm tra DTO constraints, actual resource hashes/bytes và property binding không dùng DB. Latest commands/results tại Task Backlog; [B04 runbook](../architecture/B04_CASE_WORKFLOW.md) chỉ cách chạy IntelliJ. B05 review states trong fixtures là SQL simulation, không B05 API implementation.

Fictional household `Demo Household A`, org `AidBridge Demo Organization`, need food package2 and hygiene kit2; alternate second org; users each own identity plus dual-role negative account. Private evidence markers are synthetic strings used to assert no leak, not real people data. Each test creates isolated IDs; test transaction/snapshots must not obscure separate-thread locking tests.

Results template to append after execution: `Test ID | date | environment/build | command | expected | actual | pass/fail | owner | evidence link | defect/task`. Không đánh dấu Pass/Done trước khi command thực sự chạy. Fuji evidence: actual chain/address/hash/block/log arguments; local passing tests không thay Fuji gate. Không broaden/repeat suites sau khi pass nếu không có change/failure mới.
