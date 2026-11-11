# Master Plan - AidBridge v1.1

Ngày tạo: 03/11/2026. Cập nhật:04/11/2026. Owner: B. Status: implementation roadmap; backend foundation/user/auth đã bắt đầu một phần, **chưa có integrated P0 workflow**. B01 consolidation In Review theo [baseline](../architecture/B01_BASELINE.md), B02/B03 In Progress; chưa đổi estimates/forecasts. Baseline03/11/2026; P0 complete18/11, stable20/11, package22/11; external submission limit25/11/2026, Asia/Bangkok.

## Nhân sự và phối hợp đã xác nhận

**Dũng = B (Backend), Khoa = C (Blockchain & Integration), Minh = F (Frontend & Product).** Các task IDs/primary owners/estimates/deadlines giữ nguyên.

Dũng chịu đầu ra nghiệp vụ/SQL/audit/outbox/REST và hỗ trợ Java tích hợp từ B05–C05, không đợi toàn bộ backend xong. Khoa chịu contract/Fuji/ABI/events/signing/nonce/receipt/log và kết quả tích hợp. Hai bạn ghép C05/C07/C08/C09/C10 theo [phân công chi tiết](TEAM_RESPONSIBILITIES.md); Minh ghép UI/E2E, không để hai bạn gánh thêm frontend.

Công hỗ trợ phải ghi người/phần việc/giờ một lần trong task; không mặc định Dũng có8h rảnh sớm hoặc cộng thêm việc vào lịch kín. Hỗ trợ sâu hơn dùng actual saved time hoặc reforecast capacity trước. Đây là chia việc đã có, không scope/architecture change hoặc coding authorization mới.

## Executive roadmap

AidBridge giúp NGO tiếp nhận và xác minh nhu cầu, điều phối nhiều loại nhu yếu phẩm, ghi receipt/delivery và independent confirmation. Người thụ hưởng không login/wallet. MVP dùng simulated data; Fuji lưu các accountability milestones. Blockchain tăng record integrity, không chứng minh physical truth hoặc loại bỏ collusion.

Workspace hiện có planning/PDF, backend Spring Boot skeleton, V1 identity SQL, focused user/auth implementation/tests và nhiều feature packages còn rỗng/skeleton. V2–V5 đã có SQL; V3–V5 ghi theo SQL user cung cấp ngày04/11, chỉ kiểm tra cấu trúc tĩnh, chưa chạy migration toàn bộ. User đã cho phép từng phần user/security, B01 documentation consolidation và ghi SQL V3–V5; approval không tự mở rộng sang full CRUD/deploy/purchase. Git repository đã được khởi tạo/merge trong phiên hướng dẫn trước; chưa có deployment evidence. Quy trình collaboration áp dụng trong từng approved task.

| Phase | Dates | Outcome / gate | Coordination |
|---|---|---|---|
| 0 Design contracts |03-05/11 | Freeze scope/DTO/ERD/events; NGO validation attempt | B/C/F, no waiting if input ready |
| 1 First working slice |06-09/11 | Auth/case/independent verification/actual Fuji/UI | Continuous handoffs across three owners |
| 2 Goods and reliable audit |10-16/11 | Multi-item pledge/receipt, milestones, durable sync | M2 pledges13/11, M3 sync16/11 |
| 3 Complete P0 |17-18/11 | Delivery/separate confirm/disputes/public first smoke, feature freeze | All six P0 integrated18/11 |
| 4 Hardening |19-20/11 | Full regression/security/recovery/E2E, stable public demo | Stable gate20/11; no new features |
| 5 Package ready |21-22/11 | Video/pitch/runbooks/README/rehearsal complete | B/C handover, F assets |
| 6 Contingency/submission |23-25/11 | Actual defects/outages/submission checks only |25/11 external limit, can submit earlier when ready/authorized |

User-confirmed revision03/11/2026 (D26/D27) replaces original phase dates. PDF retained unchanged as source baseline. Dates mean finish-by targets, never waiting dates: finish a task, hand off immediately and pull next dependency-ready work.18/11 feature-complete is distinct from20/11 stable release.

## Capacity and feasibility

Total remains64h/person and192h/team.36 owner tasks remain B56h/C56h/F56h=168h, with24h team reserve. Estimates, owners, P0 scope and mandatory tests/reviews are unchanged.

User selected front-loaded hours:03-20/11,14 weekdays x2.5h+4 weekend days x5h=55h/person;21-22/11 x3h=6h/person;23-25/11 x1h=3h/person. Total64h/person, replacing original2h-weekday curve. Early full weeks may be about22.5h; whole-period capacity still about20h/week. Learning/testing/review/docs are already included, support not double-booked.

Core B01-B10/C01-C10/F01-F10 completes by18/11 (B47h,C47h,F45h) with feature-level positive/negative tests and reviews. Full regression B11/C11/F11 completes20/11. Runbooks/assets B12/C12/F12 complete21-22/11. No planned implementation23-25/11. If available hours/start date changes, reforecast honestly; about118h lower-capacity case cannot promise full early Target MVP.

## Critical path and milestones

B01/C01/F02 -> B02/B03 -> B04/B05+C03/C04 -> C05/F04/F05 -> real slice -> B06/B07 -> B08/B09 -> B10/F09/C10 -> F10 -> B11/C11/F11 -> stable20/11 -> B12/C12/F12 -> package22/11.

F uses agreed mocks, C tests ABI/permissions early, B hands off actual APIs incrementally. Integrated Done needs real dependencies/tests/reviews; mocks cannot fake milestone completion. WIP1 main task/person; if blocked, assist critical dependency/review/tests rather than wait or add scope.

| Milestone | Latest target | Required evidence |
|---|---|---|
| M0 design ready |05/11 | Scope/BR trace/ERD/DTO/errors/idempotency/events/privacy/capacity; NGO results or unvalidated risk |
| M1 real vertical slice |09/11 | Distinct creator/verifier -> durable job -> actual Fuji receipt+expected event ->UI hash/status; self-approval denied |
| M2 core goods |13/11 | Multi-item atomic pledge/cancel, per-item concurrency/replay tests |
| M3 reliable sync |16/11 | Durable signing/receipt/context checks, restart/log repair, full audit |
| M4 feature-complete |18/11 | All P0 integrated, receipt/delivery/independent confirm/dispute, actual milestone anchors, public first smoke; feature freeze |
| M4.1 stable demo |20/11 | Mandatory PostgreSQL/contract/recovery/E2E tests+reviews, public smoke pass, no critical integrity/privacy/auth defect |
| M5 package ready |22/11 | Runbooks/video/pitch/README/submission package/rehearsal complete |
| M6 external submission limit |25/11 | Official closing time/rules verified; may submit earlier when ready and authorized |

## Development environments (future implementation)

| Environment | Shape | Gate / cost |
|---|---|---|
| Local | FE3000 -> /api proxy BE8080; PostgreSQL do user cài đặt/quản lý; local Hardhat node | Workspace PostgreSQL instance/automation đã gỡ theo D37; cần xác nhận DB/account/endpoint mới. Datasource/migrations/full-app health chưa hoàn tất; không real data, Compose chưa triển khai |
| Shared integration | Shared API contract/mocks then local integration builds; public demo project once available | Avoid duplicate always-on hosted environments under$20 |
| Public demo | Railway Next.js + one Spring Boot replica + PostgreSQL; private DB/API networking, public HTTPS FE | Only after local tests and provider/budget approval; no purchase yet |

Suggested baseline tools/versions are in System Design, not installed. B02/C02/F03 must verify current compatible patches and pin lockfiles/wrapper/compiler. Windows prerequisites: JDK21, Maven Wrapper, supported Node22 LTS >=22.13.0, npm, Docker Desktop/Compose and editor; do not make team learn Foundry too. No custom infrastructure stack.

### Environment inventory

| Variable | Used by | Secret? / purpose |
|---|---|---|
| SPRING_PROFILES_ACTIVE=demo | BE | No; demo-only bootstrap explicitly enabled |
| SPRING_DATASOURCE_URL/USERNAME/PASSWORD | BE/Flyway | Credentials secret; private DB connection |
| APP_PUBLIC_ORIGIN | BE/FE | No; exact HTTPS origin/redirect validation |
| API_INTERNAL_BASE_URL | FE server | Server-only; proxy destination, no secret in NEXT_PUBLIC |
| FUJI_RPC_URL, FUJI_CHAIN_ID=43113 | Worker | RPC credential secret if present; enforce chain ID |
| AID_REGISTRY_ADDRESS, AID_REGISTRY_DEPLOYMENT_BLOCK, AID_REGISTRY_VERSION | Worker/API | Public; exact deployed registry context |
| FUJI_RELAYER_PRIVATE_KEY | BE worker | Secret dedicated test key; admin key never routine BE env |
| FUJI_DEPLOYER_PRIVATE_KEY | Controlled deploy tooling only | Secret, separate from relayer; never frontend/runtime logs |
| DATA_ENCRYPTION_KEY / DATA_KEY_VERSION | BE | Secret for optional private data, salts and signed tx journal |
| DEMO_BOOTSTRAP_ENABLED / DEMO_ACCOUNT_*_PASSWORD | Demo provisioning | Password secrets, unique actors; not migrations/README |
| CHAIN_POLL_INTERVAL / CHAIN_MAX_FEE / CHAIN_RPC_TIMEOUT | Worker | Nonsecret operational bounds; default documented in blockchain spec |

No .env or example secret values are created during documentation preparation. Future `.env.example` must contain variable names/dummy markers only and `.env` excluded from Git when Git work is authorized.

## Deployment, rollback and demo-day contingency

1. F verifies provider eligibility/card/tax/limits and official pricing; set alerts/spending controls below total$20 with margin. User budget selection is not evidence money has been spent.
2. Build reviewed immutable artifacts; C records contract address/ABI/compiler/version/tx/block. Verify RPC chainId, deploy admin vs relayer roles, secret ownership.
3. Backup DB; Flyway validate/migrate before backend accepts traffic; no Hibernate auto-update; stop rollout on migration failure.
4. BE one replica, bounded heap appropriate provider resources, private DB URL; health readiness checks DB/config without exposing secrets. Serve detailed health/OpenAPI only authenticated internal/dev access.
5. FE HTTPS same-origin API proxy; secure cookies, CSRF, private caching disabled. Smoke login, case, verification, audit, multi-item receipt/delivery/confirmation with fixtures.
6. Review structured logs (traceId/eventId/code), pending-job age, failed jobs, relayer test balance, RPC errors and projected billing. No extra paid monitoring required for P0.
7. Release failure: restore previous application artifact/config; retain DB schema compatible with previous version. Failed/destructive schema issue requires forward fix or tested backup restore; do not delete applied migrations. Preserve tx journal to avoid nonce/event duplication.
8. Contract failure: revoke/suspend writes as appropriate; new deployment version, retain historical addresses/ABI; never relabel old tx as new contract success.
9. Demo contingency: local stable build and video; preconfirmed actual Fuji tx/logs visible, live delayed event remains pending; simulated workflows labeled simulated.

[Railway pricing](https://docs.railway.com/pricing/plans) is usage-based; base Hobby fee is not all-service fixed cost. [Cost controls](https://docs.railway.com/pricing/cost-control) must be checked before deployment. No unsupported promise that three services will always fit budget/free tier.

## Team workflow after Git is authorized

Use the existing monorepo and component directories; Git/local-remote merge was completed during the user's Git workflow. For approved coding tasks use short branches `feat/B04-create-case`, `fix/BR10-receipt-validation`; commits `feat(casefile): ...`, `fix(blockchain): ...`, `docs: ...` when authorized. One issue/task ID, one primary owner, WIP limit1 substantive task/person. This schedule revision does not commit or push changes.

Board: Backlog -> Ready -> In Progress -> In Review -> Testing -> Done; Blocked label includes reason/owner/next action. No release branch needed; tag stable demo builds only when authorized. PRs small, one cross-owner reviewer, tests required, no self-merge security-sensitive change without review. Author resolves conflicts by reading both intents and rerunning affected tests; do not overwrite other's work. Breaking DTO/ABI/migration changes coordinated before merge.

Definition of Ready: behavior, owner, dependencies, DTO/schema/event contract, acceptance, tests, estimate and scope known. Definition of Done: behavior works, actor allowed/forbidden checked, DB/audit effects, validation/error UI, success/failure tests, review, integration evidence, docs. Blockchain also actual Fuji proof, exact expected event and checked retry behavior. Generated code or green compile alone is not Done.

Issue template: Task ID; feature/priority; description; owner/support/reviewer; dependencies; expected files; expected behavior; acceptance criteria; test IDs/commands; completion evidence; actual hours/status/date. No code implementations stored in planning docs.

## AI-assisted engineering

Developer-led: product/security/architecture decisions, trust claims, review and final integration. AI-assisted: small implementation drafts, tests, explanations, migration/API documentation drafts and debugging hypotheses. Strict human review: auth/RBAC, contract permissions, signing, nonce/concurrency, secrets/evidence handling.

Workflow: read task/rules -> identify modules -> define expected behavior -> request small plan -> review -> implement slice -> run targeted tests -> debug -> review diff/security -> integrate/update docs -> commit only if authorized. Developer must explain generated code and demonstrate failure path before Done. Never send real beneficiary data/private keys to AI prompts.

Reusable prompts (replace IDs with approved task):

- **Issue plan:** "Read AGENTS.md, task B07, API G01 and BR-04/11. Propose a small implementation plan with affected modules, transaction/lock boundary, positive/concurrent/idempotency tests, and scope exclusions. Do not code yet."
- **Backend feature:** "Implement only approved B08 against R01 DTO/schema. Enforce org ownership, per-line remaining quantities and idempotency in one transaction; no RPC inside DB transaction. Run receipt and cross-org tests; report actual commands/results."
- **Solidity review:** "Review AidRegistry against BR-08/11/12, SC01-SC11 and org-scoped relayer trust model. Identify concrete unauthorized/duplicate/transition failures; do not claim human delivery proof; propose minimal fixes with tests."
- **Tests:** "For BR-06 and IT07, derive meaningful success, dual-role self-confirm, concurrent duplicate and open-dispute tests using separate identities/PostgreSQL; avoid tests mirroring implementation internals."
- **Integration debug:** "Given sanitized eventId/txHash/receipt and outbox state, locate where DB and Fuji diverged. Check chain/contract/nonce/log matching before suggesting retry. Never create a new event ID or mark null receipt confirmed."
- **PR review:** "Review this diff against task acceptance, DTO compatibility, tenant scope, state/quantity integrity, secrets/PII and regression tests. Report actionable findings with file/line; separate unverified concerns from reproduced failures."

## Operating rhythm và health metrics

Daily asynchronous update (<=5m/person): completed with evidence; next task; blocker; help required; actual hours/buffer. 10-15m integration checkpoint at M0/M1/M2/M3/M4 and at least weekly: shared build, API/ABI compatibility, migrations, end-to-end flow, security failures, pending chain jobs. Pair only on blockers; no heavy Scrum ceremony.

Track planned vs accepted tasks, blocked critical dependencies/days, actual remaining effort, failing mandatory tests, integration readiness, public demo readiness and budget projection. Do not measure performance by lines/commits. Pull next ready task immediately after completion; critical path delay>=one checkpoint triggers focused help; budget/privacy/auth/false-confirmation failures block release immediately.

## First 48 hours

Current authorized action is preparing this planning workspace, not scaffolding. Team checklist after reading it:

1. B: verify roles/organization ownership, draft case/needs/consent schema and verify DTO; C: approve event/commitment format and key trust boundary; F: identify2-3 interview contacts and sketch case/detail/audit screens.
2. Agree exactly what VERIFIED, DELIVERED and CONFIRMED mean; verify multi-item progress and negative flows.
3. Freeze create/verify/audit DTOs and error/CSRF/idempotency conventions; provide mock success/403/409/pending responses.
4. Record proposed toolchain versions/env inventory and developer availability; no installs/Git until corresponding approval.
5. Review smallest backend feature (create+independent approve), smallest contract feature (authorized CaseVerified), smallest frontend feature (case form/detail/audit badges) as one slice, not disconnected demos.
6. Slice acceptance by09/11: distinct actors, real persistence, real Fuji tx, receipt/event check, UI status/hash; self-approval fails.

## Full Master Prompt coverage

| Master section | Canonical document(s) / content |
|---|---|
| 1 Role | README; Team Responsibilities: ownership and mentoring |
| 2 Source of truth | Decision Log; source PDF; source/assumption labels |
| 3 Background | Product Requirements: problem, users, limits, metrics |
| 4 Stack | System Design: version families, modular monolith |
| 5 Roles | Business Workflows; Security: identity/RBAC/ownership |
| 6 BR-01..12 | Test Strategy BR matrix; workflows/API guards |
| 7 Contract scope | Blockchain Architecture: interface/authorization/events |
| 8 Product analysis | Product Requirements: stakeholders/mapping/stories |
| 9 MVP scope | MVP Scope: P0/P1/P2 and cut levels |
| 10 System design | System/Database Design; Business Workflows |
| 11 API | API Specification: every P0 route/DTO/effects/errors/tests |
| 12 Blockchain integration | Blockchain Architecture: concepts/tooling/sync/runbook |
| 13 Frontend | System Design screen contracts/states/accessibility |
| 14 Distribution | Team Responsibilities; Backlog owners/support |
| 15 Daily roadmap | Daily Roadmap:23 dates, capacity, fallback |
| 16 Learning | Team Responsibilities: JIT topics/exercises/readiness |
| 17 Git/team workflow | Master Plan team workflow (future only) |
| 18 Tests | Test Strategy: four levels, BR/IT/SC/E2E matrices |
| 19 Security/privacy | Security & Privacy; evidence/chain boundaries |
| 20 Failure scenarios A-H | Test Strategy IT01-IT12; Blockchain recovery |
| 21 Deployment | Master Plan env/deploy/rollback; Submission checklist |
| 22 Documents | README index + coverage here; owner registry in Team Responsibilities |
| 23 Demo | Demo Scenario: scripts/accounts/screens/security/impact |
| 24 Risks | Risk Register: early signals/owners/cut tree |
| 25 Done | Milestone gates + feature DoD here; Submission Checklist |
| 26 AI workflow | Master Plan AI-assisted engineering and six prompts |
| 27 Execution management | Operating rhythm/metrics; Team Responsibilities |
| 28 First48h | Checklist here; first10 tasks in Backlog |
| 29 Deliverables A-L | README linked docs; roadmap/backlog/calendar/design/workflow/team/tests/gates/risks/demo/submission/next actions |
| 30 Quality requirements | AGENTS; traceability; validation record below |
| 31 Objective | Executive roadmap + M4/M5: simulated aid journey with actual Fuji audit |

## Planning workspace validation

Authoring checks must confirm19 Markdown documents, matching PDF hashes, all links/files, 36 owner tasks totaling168h,23 dates totaling192h, BR01-12 positives/negatives, task dependency ordering and no application/Git/install/deployment artifacts. Record actual check results after running validation; do not treat these documentation checks as application tests. No test suite exists yet.

### Initial documentation QA - 03/11/2026 (historical, before Git/schedule revision)

- PASS:19 Markdown documents, required folder/file inventory, all local Markdown link targets exist, balanced code fences and consistent Markdown table widths.
- PASS: original/source-copy PDF SHA-256 equal: `8829a87ae80192b8e33aa22ef792f54289affb8052ea1e36a42bd1985f91b696`.
- PASS:36 unique tasks, acyclic dependencies, one owner each; B/C/F each56h.23 daily allocations cover64h/person with8h/person reserve; integrated finish respects dependency finish and task deadlines.
- PASS: all12 BRs have positive/negative test specifications;31-section source coverage mapping; explicit six-P0 story/API/task/test/milestone trace.
- PASS: all changes inside AidBridge; backend/frontend/contracts/infra empty; no .git, application source, migration, dependency manifest, container or deployment artifact created.
- Manual consistency corrections: Node minimum aligned with official Hardhat3 >=22.13.0; confirmed-delivery uniqueness made implementable without cross-table partial-index predicate; chain sequence columns added to outbox schema; public/internal/chain privacy boundary reviewed.

Validation used read-only Python/PowerShell filesystem/text/hash checks after authoring, not application test commands. Application tests, Fuji deployment and hosting remain **not executed**. Remaining product/operational unknowns are recorded as O01-O08 and R01-R21, not hidden by documentation QA.

### Early-completion revision - 03/11/2026

D26/D27 user-confirmed:18/11 feature-complete,20/11 stable,22/11 package,25/11 external submission only. Calendar/backlog recalculated with half-hour allocations and same36 estimates. Validation must preserve56h work+8h reserve/person, core finish<=18/11, mandatory full regression<=20/11, package tasks<=22/11, no scheduled implementation23-25/11. These are documentation calculations, not application completion/test results.
