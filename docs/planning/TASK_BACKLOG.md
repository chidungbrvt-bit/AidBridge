# Task Backlog

Ngày tạo: 03/11/2026. Owner: B. Status: **36 tasks Not Started**; application chưa triển khai. Authorization hiện tại tạo planning workspace, không tự cho phép coding/Git/deploy/purchase.

## Issue conventions và estimates

Mỗi task dưới đây P0 hoặc P0 enabler; không cộng P1/P2 vào168h. Complexity S/M/L gồm security/integration uncertainty, không chỉ effort. Estimates gồm JIT learning, feature tests/review/docs; support không được double-book ngoài capacity. B/C/F mỗi12 tasks/56h +8h reserve.

Dependencies là gate cho integrated Done; mock/design/scaffold prework có thể overlap nhưng không đánh dấu Done khi chỉ có mocks. Planned integrated finish dưới đây là forecast từ daily allocation, không actual completion. State: Not Started -> In Progress -> In Review -> Testing -> Done; Blocked cần reason/owner/next action.

| ID | Feature | Owner | Support | Complexity | Hours | Dependencies | Deadline | Planned finish | Status |
|---|---|---|---|---|---:|---|---|---|---|
| B01 | Design | B | C/F | M | 2 | Planning review | 2026-11-05 | 2026-11-03 | Not Started |
| B02 | Foundation | B | C/F | M | 3 | B01 | 2026-11-06 | 2026-11-05 | Not Started |
| B03 | P0-01 | B | C/F | L | 5 | B02 | 2026-11-08 | 2026-11-07 | Not Started |
| B04 | P0-02 | B | C/F | M | 4 | B03 | 2026-11-08 | 2026-11-08 | Not Started |
| B05 | P0-03 | B | C/F | L | 4 | B04, C01 | 2026-11-09 | 2026-11-08 | Not Started |
| B06 | P0-04 | B | C/F | M | 6 | B05 | 2026-11-12 | 2026-11-11 | Not Started |
| B07 | P0-04 | B | C/F | L | 7 | B06 | 2026-11-15 | 2026-11-14 | Not Started |
| B08 | P0-05 | B | C/F | M | 5 | B07 | 2026-11-18 | 2026-11-15 | Not Started |
| B09 | P0-05 | B | C/F | L | 5 | B08 | 2026-11-20 | 2026-11-17 | Not Started |
| B10 | P0-05 | B | C/F | L | 6 | B09 | 2026-11-22 | 2026-11-20 | Not Started |
| B11 | Quality | B | C/F | L | 6 | B03, B04, B05, B06, B07, B08, B09, B10 | 2026-11-23 | 2026-11-21 | Not Started |
| B12 | Submission | B | C/F | M | 3 | B11 | 2026-11-25 | 2026-11-25 | Not Started |
| C01 | Design | C | B/F | L | 4 | B01 | 2026-11-05 | 2026-11-04 | In Review |
| C02 | Foundation | C | B/F | M | 3 | C01 | 2026-11-06 | 2026-11-06 | Not Started |
| C03 | P0-03/P0-06 | C | B/F | L | 5 | C01, C02 | 2026-11-07 | 2026-11-07 | Not Started |
| C04 | P0-06 | C | B/F | M | 2 | C03 | 2026-11-08 | 2026-11-08 | Not Started |
| C05 | P0-03/P0-06 | C | B/F | L | 6 | B05, C04 | 2026-11-10 | 2026-11-09 | Not Started |
| C06 | P0-04/P0-05/P0-06 | C | B/F | L | 6 | C03 | 2026-11-15 | 2026-11-12 | Not Started |
| C07 | P0-06 | C | B/F | L | 7 | C05 | 2026-11-16 | 2026-11-14 | Not Started |
| C08 | P0-06 | C | B/F | L | 7 | C06, C07 | 2026-11-18 | 2026-11-16 | Not Started |
| C09 | P0-06 | C | B/F | M | 3 | C08 | 2026-11-18 | 2026-11-18 | Not Started |
| C10 | P0-05/P0-06 | C | B/F | L | 4 | B08, B09, C08 | 2026-11-20 | 2026-11-20 | Not Started |
| C11 | Quality | C | B/F | L | 6 | C06, C07, C08, C09, C10 | 2026-11-22 | 2026-11-21 | Not Started |
| C12 | Submission | C | B/F | M | 3 | C11 | 2026-11-25 | 2026-11-25 | Not Started |
| F01 | Validation | F | B/C | M | 2 | Planning review | 2026-11-05 | 2026-11-03 | In Review |
| F02 | Design | F | B/C | M | 4 | B01 | 2026-11-06 | 2026-11-05 | In Review |
| F03 | P0-01 | F | B/C | M | 4 | F02, B03 | 2026-11-08 | 2026-11-07 | Not Started |
| F04 | P0-02/P0-03 | F | B/C | L | 6 | F03, B04, B05 | 2026-11-09 | 2026-11-08 | Not Started |
| F05 | P0-06 | F | B/C | L | 4 | F04, C05 | 2026-11-11 | 2026-11-09 | Not Started |
| F06 | P0-04 | F | B/C | M | 6 | F02, B06 | 2026-11-13 | 2026-11-12 | Not Started |
| F07 | P0-04 | F | B/C | M | 5 | F06, B07 | 2026-11-15 | 2026-11-14 | In Review |
| F08 | P0-05 | F | B/C | M | 5 | F04, B08, B09 | 2026-11-20 | 2026-11-17 | Not Started |
| F09 | P0-05/P0-06 | F | B/C | L | 5 | F08, B10, C09 | 2026-11-22 | 2026-11-20 | Not Started |
| F10 | Deployment | F | B/C | L | 4 | F09, C10 | 2026-11-22 | 2026-11-21 | Not Started |
| F11 | Quality | F | B/C | L | 6 | F10, B11, C11 | 2026-11-23 | 2026-11-22 | Not Started |
| F12 | Submission | F | B/C | L | 5 | F11 | 2026-11-25 | 2026-11-25 | Not Started |

**Total36 tasks/168h; B56h, C56h, F56h;24h reserve against192h capacity.**

## Issue-ready specifications

### B01 - Khóa RBAC/ERD/API

- **Feature / priority:** Design / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** M / 2h.
- **Dependencies:** Planning/source review; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-03 through 2026-11-03; deadline 2026-11-05, Asia/Bangkok.
- **Description / expected behavior:** Khóa RBAC/ERD/API; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** docs/architecture/API_SPECIFICATION.md; DATABASE_DESIGN.md.
- **Acceptance criteria:** B/C/F đồng ý case/need/consent ownership, DTO/error/idempotency và initial event contract.
- **Required tests:** BR01-P/N, BR02-P/N, BR03-P/N; design review.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B02 - Spring/PostgreSQL/Flyway skeleton

- **Feature / priority:** Foundation / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** B01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-04 through 2026-11-05; deadline 2026-11-06, Asia/Bangkok.
- **Description / expected behavior:** Spring/PostgreSQL/Flyway skeleton; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend/pom.xml; mvnw; application config; infra/compose.yml; V1 migration.
- **Acceptance criteria:** Sau coding approval: health+DB, clean migration; no ddl-auto update, no secrets in files.
- **Required tests:** Clean/upgrade migration smoke; safe errors/health access.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B03 - Session auth và tenant isolation

- **Feature / priority:** P0-01 / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** B02; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-05 through 2026-11-07; deadline 2026-11-08, Asia/Bangkok.
- **Description / expected behavior:** Session auth và tenant isolation; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend auth/organization controllers/services/repositories; SecurityConfig; AuthApiIT.
- **Acceptance criteria:** Login/logout/me/csrf, rotate session, own active membership; no shared staff account; separate seed secrets.
- **Required tests:** BR01-P/N; IT09, IT15; invalid login/CSRF/logout/fixation.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B04 - Case/needs/consent/evidence APIs

- **Feature / priority:** P0-02 / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** M / 4h.
- **Dependencies:** B03; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-07 through 2026-11-08; deadline 2026-11-08, Asia/Bangkok.
- **Description / expected behavior:** Case/needs/consent/evidence APIs; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend casefile package; case/evidence migrations; CaseApiIT; EvidenceAccessIT.
- **Acceptance criteria:** Atomic SUBMITTED case, relational need lines, consent; editable case versioning; private fixture authorization; old evidence immutable.
- **Required tests:** BR01-P/N, BR08-P/N, BR09-P/N; missing consent/stale/cross-org tests.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B05 - Independent case review

- **Feature / priority:** P0-03 / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** B04, C01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-08 through 2026-11-08; deadline 2026-11-09, Asia/Bangkok.
- **Description / expected behavior:** Independent case review; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend verification package; snapshot/audit/job creation; VerificationApiIT.
- **Acceptance criteria:** Other CV approves; creator dual-role403; reject/request-changes/resubmit; immutable reviewed snapshot; DB/audit/job atomic.
- **Required tests:** BR02-P/N, BR03-N, BR09-P; IT01, IT08; rollback and competing review.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B06 - Multi-item campaign draft/publish

- **Feature / priority:** P0-04 / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** M / 6h.
- **Dependencies:** B05; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-09 through 2026-11-11; deadline 2026-11-12, Asia/Bangkok.
- **Description / expected behavior:** Multi-item campaign draft/publish; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend campaign package; campaign/item migrations; CampaignApiIT.
- **Acceptance criteria:** Copy every approved need line and exact unit/qty; one campaign/case; unverified publish blocked; sanitized public DTO.
- **Required tests:** BR03-P/N, BR08-P/N; stale review/draft privacy/duplicate campaign.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B07 - Commitments/cancel/idempotency

- **Feature / priority:** P0-04 / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** L / 7h.
- **Dependencies:** B06; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-12 through 2026-11-14; deadline 2026-11-15, Asia/Bangkok.
- **Description / expected behavior:** Commitments/cancel/idempotency; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend contribution package; idempotency journal; commitment migrations/tests.
- **Acceptance criteria:** Atomic multi-line pledge, donor history, cancel only unreceived remainder, per-item row locks and event IDs; concurrent no overflow.
- **Required tests:** BR04-P/N, BR11-P/N; IT10; PostgreSQL last-unit/cancellation races.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B08 - Partial goods receipts

- **Feature / priority:** P0-05 / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** M / 5h.
- **Dependencies:** B07; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-14 through 2026-11-15; deadline 2026-11-18, Asia/Bangkok.
- **Description / expected behavior:** Partial goods receipts; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend receipt controller/service/repository; receipt migrations; ReceiptApiIT.
- **Acceptance criteria:** Immutable receipt per contribution line; received<=active pledge; repeat once; same org/line; not delivered.
- **Required tests:** BR05-P/N, BR11-P/N; IT05, IT10; receipt/cancel concurrency.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B09 - Delivery records/failed attempts

- **Feature / priority:** P0-05 / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** B08; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-15 through 2026-11-17; deadline 2026-11-20, Asia/Bangkok.
- **Description / expected behavior:** Delivery records/failed attempts; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend delivery package; delivery migrations; DeliveryConcurrencyIT.
- **Acceptance criteria:** Reserve all nonvoid recorded delivery against received, even unconfirmed; FAILED zero quantity; immutable evidence snapshot; safe retry.
- **Required tests:** BR05-P/N, BR09-P/N, BR11-P/N; IT11, IT16.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B10 - Confirm/dispute/resolve/close

- **Feature / priority:** P0-05 / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** B09; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-17 through 2026-11-20; deadline 2026-11-22, Asia/Bangkok.
- **Description / expected behavior:** Confirm/dispute/resolve/close; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend delivery confirmation/dispute services; campaign close; migrations; DisputeIT.
- **Acceptance criteria:** Separate DC; self403; dispute blocks/reopens close; all item targets confirmed; void preserves old rows; PA cannot auto-confirm.
- **Required tests:** BR06-P/N, BR07-P/N; IT06, IT07, IT12; concurrent flag+close.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B11 - Backend cross-feature regression

- **Feature / priority:** Quality / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** B03, B04, B05, B06, B07, B08, B09, B10; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-20 through 2026-11-21; deadline 2026-11-23, Asia/Bangkok.
- **Description / expected behavior:** Backend cross-feature regression; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend/src/test/java/com/aidbridge; Test Strategy actual results.
- **Acceptance criteria:** Real PostgreSQL tenant/state/quantity/idempotency/upgrade tests; no critical integrity/privacy/auth bug; feature tests already embedded earlier.
- **Required tests:** BR01-P/N..BR09-P/N as applicable; BR11-P/N; IT05-12/15/16.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B12 - Critical fixes/final handover

- **Feature / priority:** Submission / P0.
- **Owner / support / reviewer:** B / C/F / C cho auth/transaction; F cho API/UX.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** B11; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-24 through 2026-11-25; deadline 2026-11-25, Asia/Bangkok.
- **Description / expected behavior:** Critical fixes/final handover; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend affected fixes/tests; API/runbook/documentation updates.
- **Acceptance criteria:** Final smoke, actual gaps and evidence, no new features; regression only affected changes.
- **Required tests:** Final auth/quantity/dispute smoke; relevant regressions after fixes.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C01 - EVM/event/commitment agreement

- **Feature / priority:** Design / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** B01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-03 through 2026-11-04; deadline 2026-11-05, Asia/Bangkok.
- **Description / expected behavior:** EVM/event/commitment agreement; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Blockchain Architecture; event/manifest/ABI specification.
- **Acceptance criteria:** JIT learning, random refs, stable event IDs, salt/private manifest, sequence and custody boundary; B/F review.
- **Required tests:** BR08-P/N design, BR11/12 design; Java/TS golden-vector expectations.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (09/11/2026). Khoa hoàn thành bộ tài liệu review được giao theo `9_11.pdf`: [C01 handoff](../reviews/C01_BLOCKCHAIN_REVIEW.md), ABI draft, reviewed sample, golden-vector input và test expectations tại `docs/fixtures/c01/`. Design-fixture checks PASS; contract/Java/TypeScript/Fuji checks Not Run. Chưa Done: cần Dũng xác nhận C01-O01..O05 và cross-owner review. Actual effort chưa ghi nhận đủ tin cậy; không sửa estimate/lịch gốc. Ngày hoàn thành task: chưa có.

### C02 - Hardhat local setup

- **Feature / priority:** Foundation / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** C01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-05 through 2026-11-06; deadline 2026-11-06, Asia/Bangkok.
- **Description / expected behavior:** Hardhat local setup; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** contracts/package.json; package-lock; hardhat.config.ts; local harness.
- **Acceptance criteria:** After coding approval: pinned compatible toolchain/compiler/optimizer/EVM target, distinct test accounts, deterministic compile/test, no keys.
- **Required tests:** Local compile/test smoke; wrong chain/secret configuration checks.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C03 - Authorized verification contract slice

- **Feature / priority:** P0-03/P0-06 / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** C01, C02; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-06 through 2026-11-07; deadline 2026-11-07, Asia/Bangkok.
- **Description / expected behavior:** Authorized verification contract slice; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** contracts/contracts/AidRegistry.sol; contracts/test/AidRegistry.test.ts.
- **Acceptance criteria:** Org registration, scoped relayer, verification event/sequence, unique IDs; unknown/revoked/wrong-org callers rejected.
- **Required tests:** BR12-P/N, BR11-N; SC01-04/09-11.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C04 - Fuji pilot deployment

- **Feature / priority:** P0-06 / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** M / 2h.
- **Dependencies:** C03; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-07 through 2026-11-08; deadline 2026-11-08, Asia/Bangkok.
- **Description / expected behavior:** Fuji pilot deployment; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** contracts/scripts/deploy.ts; public deployment manifest.
- **Acceptance criteria:** Actual43113 deployment receipt/code/address/block/compiler/ABI saved, org role readback; explorer source status accurate.
- **Required tests:** Wrong-chain preflight, authorization readback, actual deploy receipt.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C05 - Java adapter/minimal audit slice

- **Feature / priority:** P0-03/P0-06 / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** B05, C04; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-08 through 2026-11-09; deadline 2026-11-10, Asia/Bangkok.
- **Description / expected behavior:** Java adapter/minimal audit slice; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend blockchain adapter/worker; minimal audit controller; AdapterContractIT.
- **Acceptance criteria:** Persistent attempt before broadcast, no RPC wait in SQL/HTTP; exact verification receipt/event; pending/failed/confirmed API.
- **Required tests:** BR10-P/N, BR12-P; IT01/03/13; ABI parity and actual Fuji smoke.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C06 - Full milestone contract

- **Feature / priority:** P0-04/P0-05/P0-06 / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** C03; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-10 through 2026-11-12; deadline 2026-11-15, Asia/Bangkok.
- **Description / expected behavior:** Full milestone contract; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** AidRegistry milestone functions; contract tests; exported ABI.
- **Acceptance criteria:** Pledge/reduction/receipt/delivery/confirm/dispute/resolve; same org/case refs, no duplicate/absent/void/disputed confirm, sequence guards.
- **Required tests:** BR08-P/N, BR11-P/N, BR12-P/N; SC04-11.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C07 - Durable signing/submission worker

- **Feature / priority:** P0-06 / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** L / 7h.
- **Dependencies:** C05; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-13 through 2026-11-14; deadline 2026-11-16, Asia/Bangkok.
- **Description / expected behavior:** Durable signing/submission worker; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend submission job/attempt repositories; chain migrations; CrashRecoveryIT.
- **Acceptance criteria:** Lease+signer serialization+nonce journal; encrypted signed bytes/hash committed before send; restart same tx, one in-flight/signer.
- **Required tests:** BR10-N, BR11-P/N; IT01/02/04.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C08 - Receipt/retry/log reconciliation

- **Feature / priority:** P0-06 / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** L / 7h.
- **Dependencies:** C06, C07; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-15 through 2026-11-16; deadline 2026-11-18, Asia/Bangkok.
- **Description / expected behavior:** Receipt/retry/log reconciliation; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend receipt/log jobs; cursor/observation migrations; LogReplayIT.
- **Acceptance criteria:** Exact receipt/context, timeout remains pending, reverts failed; durable bounded cursor repairs lost DB update without duplicate event.
- **Required tests:** BR10-P/N, BR11-P/N; IT02-04/13; SC09.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C09 - Full sanitized audit API

- **Feature / priority:** P0-06 / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** C08; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-17 through 2026-11-18; deadline 2026-11-18, Asia/Bangkok.
- **Description / expected behavior:** Full sanitized audit API; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend audit projections/DTOs; AuditPrivacyIT.
- **Acceptance criteria:** Paginated internal/public timelines; business/evidence/chain axes; correct explorer network; no salt/evidence/identity leaks.
- **Required tests:** BR08-P/N, BR10-P/N; IT09/14; E2E05/06 support.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C10 - Goods-to-chain integration

- **Feature / priority:** P0-05/P0-06 / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** B08, B09, C08; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-18 through 2026-11-20; deadline 2026-11-20, Asia/Bangkok.
- **Description / expected behavior:** Goods-to-chain integration; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend receipt/delivery event mappers; integration tests.
- **Acceptance criteria:** Anchor exact immutable multi-item snapshots and causal refs, DB quantities not altered by chain replay; pair B on confirm/dispute wiring.
- **Required tests:** BR05-P, BR09-P, BR11-P/N; IT10-12/14.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C11 - Contract/failure regression

- **Feature / priority:** Quality / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** C06, C07, C08, C09, C10; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-20 through 2026-11-21; deadline 2026-11-22, Asia/Bangkok.
- **Description / expected behavior:** Contract/failure regression; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** contracts/test; backend chain integration tests; actual test ledger.
- **Acceptance criteria:** All required SC/chain BR/IT pass; actual Fuji smoke, recovery/revoke tested, no fake tx success; auth/privacy errors release blocker.
- **Required tests:** BR08/10/11/12-P/N; SC01-11; IT01-04/12-14.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C12 - Deployment/key recovery runbook

- **Feature / priority:** Submission / P0.
- **Owner / support / reviewer:** C / B/F / B cho signing/permissions; F cho audit claims.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** C11; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-24 through 2026-11-25; deadline 2026-11-25, Asia/Bangkok.
- **Description / expected behavior:** Deployment/key recovery runbook; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Public deployment manifest; blockchain/key recovery documentation.
- **Acceptance criteria:** Actual address/ABI/block/tx evidence, dedicated key custodians/balance, no secrets; honest outage fallback.
- **Required tests:** Final actual Fuji receipt/readback; recovery/rotation review.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F01 - Interview recruitment/product assumptions

- **Feature / priority:** Validation / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** M / 2h.
- **Dependencies:** Planning/source review; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-03 through 2026-11-03; deadline 2026-11-05, Asia/Bangkok.
- **Description / expected behavior:** Interview recruitment/product assumptions; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Product Requirements validation notes; Risk Register.
- **Acceptance criteria:** Identify2-3 charity-experienced candidates; actual consented notes or explicit no-contact; no invented partner/impact, no agent unsolicited messages.
- **Required tests:** Workflow research review; R01/R02 status.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (10/11/2026). Khoa/Codex hoàn thành thiết kế F01 theo bảng phân công 10/11: [Product/Validation](../product/validation/F01_DEMO_AND_ASSUMPTIONS.md), checklist JSON và checker; full fictional lifecycle/multi-item/independent roles, 10 assumptions, explicit no-contact. Chưa có interview candidates/consented notes hoặc actual HTTP/demo/Fuji; không Done. Owner/estimate/lịch gốc giữ nguyên; actual effort chưa ghi nhận đủ tin cậy, completion date chưa có.

### F02 - Wireframes/mock API states

- **Feature / priority:** Design / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** M / 4h.
- **Dependencies:** B01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-04 through 2026-11-05; deadline 2026-11-06, Asia/Bangkok.
- **Description / expected behavior:** Wireframes/mock API states; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Screen contracts; typed mock DTO fixtures planned.
- **Acceptance criteria:** B/C/F agree combined case/goods/audit screens, loading/empty/error/403/409 states, accessibility, no beneficiary wallet.
- **Required tests:** API design review; E2E07 specification.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (09/11/2026). Khoa/Codex bàn giao phần F02 được giao trong `9_11.pdf`: [three wireframes and handoff](../design/F02_WIREFRAMES_AND_STATES.md), shared mock/typed declarations/state map/checker tại `docs/fixtures/f02/`. Fixture arithmetic, ID/DTO-shape and 403/409 atomic-rejection checks PASS; backend HTTP/frontend render/keyboard E2E Not Run. Cần Minh review UI và Dũng review F02-A01..A05; chưa Done; Khoa cho phép commit/push/PR sau final QA ngày 10/11. Owner/estimate/date gốc giữ nguyên; actual effort chưa ghi nhận đủ tin cậy, ngày hoàn thành task chưa có.

### F03 - Next shell/session client

- **Feature / priority:** P0-01 / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** M / 4h.
- **Dependencies:** F02, B03; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-06 through 2026-11-07; deadline 2026-11-08, Asia/Bangkok.
- **Description / expected behavior:** Next shell/session client; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend package/config/lock; app/login; typed API/session client.
- **Acceptance criteria:** After coding approval, same-origin proxy/CSRF/login/logout/nav; no browser secrets/localStorage auth; handle401/403.
- **Required tests:** Auth UI smoke, invalid login/logout, E2E06/07 basics.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F04 - Case and verifier UI

- **Feature / priority:** P0-02/P0-03 / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** F03, B04, B05; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-07 through 2026-11-08; deadline 2026-11-09, Asia/Bangkok.
- **Description / expected behavior:** Case and verifier UI; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend case list/new/detail forms/review panels; tests.
- **Acceptance criteria:** Case/consent/multi-need/evidence inputs, other-user review; self rejection/stale version handled; private error data absent.
- **Required tests:** BR02-N, BR09-P; E2E02/07; loading/empty/400/403/409.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F05 - Audit vertical slice UI

- **Feature / priority:** P0-06 / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** F04, C05; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-08 through 2026-11-09; deadline 2026-11-11, Asia/Bangkok.
- **Description / expected behavior:** Audit vertical slice UI; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** AuditTimeline; ChainStatus; ExplorerLink; UI tests.
- **Acceptance criteria:** Hash alone not confirmed; exact backend proof shown; business verification distinct from pending/reverted/mismatch; actual Fuji link.
- **Required tests:** BR10-P/N; E2E05; honest record-integrity wording.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F06 - Campaign multi-item UI

- **Feature / priority:** P0-04 / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** M / 6h.
- **Dependencies:** F02, B06; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-10 through 2026-11-12; deadline 2026-11-13, Asia/Bangkok.
- **Description / expected behavior:** Campaign multi-item UI; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend app/campaigns/detail; GoodsProgressTable; publish panel.
- **Acceptance criteria:** Sanitized browse, fixed units and five progress columns per item, draft private, no PII; publish UI in case detail.
- **Required tests:** BR03-N, BR08-P/N; E2E06/07; loading/empty/retry.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F07 - Donor pledge/history/cancel

- **Feature / priority:** P0-04 / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** M / 5h.
- **Dependencies:** F06, B07; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-13 through 2026-11-14; deadline 2026-11-15, Asia/Bangkok.
- **Description / expected behavior:** Donor pledge/history/cancel; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend contribution form/history/cancel; client methods.
- **Acceptance criteria:** Atomic multi-line form, same-key network retry, overflow refresh, own history, unreceived cancel; no provenance claim.
- **Required tests:** BR04-N, BR11-P/N; E2E03, donor privacy.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (10/11/2026). Khoa/Codex triển khai [F07 G01/G02](../design/F07_DONOR_IMPLEMENTATION.md) theo phân công ngày10: React module + developer preview mock, multi-item pledge/own history, 400/409/403, safe replay/recovery. Lint/typecheck/build PASS; 4 unit +8 browser E2E PASS (Edge headless desktop/mobile). Chưa có backend/Next integration, donorEnabled thật vẫn false; G03 cancellation/dependencies F06,B07 chưa đạt, không Done. Owner/estimate/lịch baseline giữ nguyên; actual effort chưa ghi nhận đủ tin cậy, completion date chưa có.

### F08 - Receipt/delivery UI

- **Feature / priority:** P0-05 / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** M / 5h.
- **Dependencies:** F04, B08, B09; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-15 through 2026-11-17; deadline 2026-11-20, Asia/Bangkok.
- **Description / expected behavior:** Receipt/delivery UI; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend case receipt/delivery/failed-attempt panels.
- **Acceptance criteria:** Partial OA receipt distinct from FV handover and review; stock limits and safe errors; FAILED zero effect; no PII in public.
- **Required tests:** BR05-P/N; IT10/16 UI; E2E01/07.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F09 - Confirmation/dispute/full timeline

- **Feature / priority:** P0-05/P0-06 / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** F08, B10, C09; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-17 through 2026-11-20; deadline 2026-11-22, Asia/Bangkok.
- **Description / expected behavior:** Confirmation/dispute/full timeline; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend confirmation/dispute/resolve/close panels; full audit UI.
- **Acceptance criteria:** Separate reviewer, self blocked, dispute guard, all item target completion, public/internal audit separation; no false chain claim.
- **Required tests:** BR06-N, BR07-N, BR10-P/N; E2E04/05/06.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F10 - Public hosting/recovery

- **Feature / priority:** Deployment / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** F09, C10; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-20 through 2026-11-21; deadline 2026-11-22, Asia/Bangkok.
- **Description / expected behavior:** Public hosting/recovery; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** infra deployment config; health/smoke/budget notes.
- **Acceptance criteria:** Deploy only with authorization, provider eligibility/forecast under$20, HTTPS/CSRF/protected files/private DB; backup/rollback ready.
- **Required tests:** Deployment smoke, cookie/CSRF/CORS/file guards, cost/recovery checklist.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F11 - E2E/accessibility/privacy regression

- **Feature / priority:** Quality / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** F10, B11, C11; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-21 through 2026-11-22; deadline 2026-11-23, Asia/Bangkok.
- **Description / expected behavior:** E2E/accessibility/privacy regression; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend/e2e; actual Test Strategy results; fixes.
- **Acceptance criteria:** E2E01-07 actual pass on integrated build, no critical auth/privacy/quantity/status issue, keyboard/error states usable.
- **Required tests:** BR08-N, BR10-N; E2E01-07 and relevant IT UI.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F12 - Demo/video/pitch/submission

- **Feature / priority:** Submission / P0.
- **Owner / support / reviewer:** F / B/C / B cho auth/API; C cho chain status.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** F11; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-23 through 2026-11-25; deadline 2026-11-25, Asia/Bangkok.
- **Description / expected behavior:** Demo/video/pitch/submission; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Demo artifacts after authorization; README/checklist actual state.
- **Acceptance criteria:** Fictional household multi-item flow, actual Fuji evidence+negative test, no fake social results; organizer rules/links verified.
- **Required tests:** Full rehearsal, public+local fallback, final submission checklist.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

## First ten immediate actions

1. Review scope/capacity and documentation vs coding authorization boundary.
2. Verify source PDF hash and19-document index; preserve original.
3. B01: validate ERD/RBAC, need/consent ownership and initial DTOs.
4. F01: identify2-3 interview candidates; record actual results or unvalidated assumptions.
5. C01: freeze event/commitment schema, golden vector and custody limits.
6. F02: agree wireframes and create/verify/audit mocks, errors/pending states.
7. Confirm compatible versions/env inventory and approved coding tasks before installs/scaffold.
8. B02/B03: backend/auth only after coding approval.
9. C02/C03/C04: local access tests before authorized actual Fuji pilot.
10. B04/B05+C05+F04/F05: one create->independent approval->Fuji receipt/event->UI slice.

## Progress log

| Task | Date | State | Actual hours | Evidence | Next action |
|---|---|---|---|---|---|
| All36 | 03/11/2026 | Not Started | Not recorded | Planning specification only | Await application task authorization |

### C01 review handoff - 09/11/2026

C01: Not Started -> In Progress -> In Review trong phiên làm việc C01; không thay trạng thái các task khác. Bản ghi All36 ngày 03/11 ở trên là baseline lịch sử, không phải tiến độ hiện tại. Bộ handoff đã chuẩn bị; chưa có human/cross-owner approval, contract implementation hoặc Fuji proof. Xem `docs/reviews/C01_BLOCKCHAIN_REVIEW.md` để biết checks, OPEN owners và bước tiếp theo.

### F02 review handoff - 09/11/2026

F02: Not Started -> In Progress -> In Review trong phiên F02. Chỉ hoàn thành design/mock handoff G01/R01/D01 được giao cho Khoa; không claim frontend/API đã chạy hoặc full F02 acceptance đã được nhóm duyệt. C01 đã lưu ở commit `2365378`; C01 còn In Review. Xem `docs/design/F02_WIREFRAMES_AND_STATES.md` và các F02-Axx về response items/read projections chưa freeze. Không sửa task khác, không tự đổi owner hoặc lịch sử estimate.

Final QA 10/11/2026: C01 commit `2365378` giữ nguyên; F02 static checkers/negative cases và review contracts PASS, thêm idempotencyConflict UI state bị thiếu. Khoa chấp nhận handoff ở mức review và cho phép commit F02/push khoa/PR main, không merge. C01/F02 còn In Review chờ Dũng/Minh; application/contract/Java/TypeScript runtime checks Not Run. Chi tiết ở final quality review trong `docs/design/F02_WIREFRAMES_AND_STATES.md`.

### F01 - phạm vi theo phân công 10/11/2026

Not Started -> In Progress -> In Review trong phiên F01. Khoa được giao product scenario/assumptions 2h theo ảnh phân công 10/11; không thay primary owner F hoặc estimate/lịch baseline 03/11. Chưa hoàn thành research ngoài đời của acceptance cũ; ghi no-contact/no evidence và R01/R02 chưa xác thực. Kết quả chỉ tài liệu, chưa chạy feature; không thay status task Dũng/Minh/F07, chưa commit/push/PR. Xem Product/Validation để có checklist, nguồn/API, role-name mismatch và hướng dẫn tiếp nhận.

Nghiệm thu nội bộ F01 10/11: đạt đủ bộ tài liệu theo phân công ngày 10/11 (scenario multi-item, independent roles, checklist, assumptions, Product/Validation). Giữ In Review, cho phép commit local theo yêu cầu Khoa; chưa Done/cross-owner approved. Baseline tuyển 2-3 người chưa có evidence, dùng explicit no-contact và giữ research R01/R02 OPEN; không claim đã tuyển người. Role alias và donorEnabled=false hiện tại là điểm tích hợp cần phối hợp, không phải code đã sửa. Xem ma trận nghiệm thu cuối tài liệu F01; không push/PR/F07 trong lượt này.

### F07 - nghiệm thu nội bộ G01/G02 ngày 10/11/2026

Not Started -> In Progress -> In Review. User giao implementation mock G01/G02 ngày10 và commit local riêng sau QA; không push/PR. Bộ React feature ở frontend/features/donor và harness dev riêng frontend/dev/donor-preview, không sửa shell/client của Minh hay backend Dũng. G03 unreceived cancellation là acceptance baseline còn lại, chưa thuộc phạm vi lượt này; không ghi Done hoặc tự đổi dependencies/owner/estimate. Xem F07 implementation để biết lệnh chạy, test results, UI routes, mock labels và các OPEN integration. F01 commit cd5d88d được bảo toàn.
