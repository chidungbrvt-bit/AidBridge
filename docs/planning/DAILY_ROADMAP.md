# Daily Roadmap

Ngày tạo: 03/11/2026. Owner: F. Status: **future allocation, no application execution yet**. Calendar03-25/11/2026, Asia/Bangkok; reforecast actual start if approval/availability changes.

## Capacity and exact task allocations

17 weekdays x2h +6 weekend days x5h =64h/person;192h team. Planned56h/person=168h; reserve8h/person=24h. Task hours include learning/tests/review/docs. Reserve covers defects/pairing/overruns or rest, not extra features. Mock prework can overlap dependencies; real integration/Done waits for prerequisite output.

| Date | Capacity/person | Backend | Blockchain | Frontend/Product | Team planned / reserve |
|---|---:|---|---|---|---|
| 2026-11-03 | 2h | B01 2h | C01 2h | F01 2h | 6h / 0h |
| 2026-11-04 | 2h | B02 2h | C01 2h | F02 2h | 6h / 0h |
| 2026-11-05 | 2h | B02 1h, B03 1h | C02 2h | F02 2h | 6h / 0h |
| 2026-11-06 | 2h | B03 2h | C02 1h, C03 1h | F03 2h | 6h / 0h |
| 2026-11-07 | 5h | B03 2h, B04 3h | C03 4h, C04 1h | F03 2h, F04 3h | 15h / 0h |
| 2026-11-08 | 5h | B04 1h, B05 4h | C04 1h, C05 4h | F04 3h, F05 2h | 15h / 0h |
| 2026-11-09 | 2h | B06 2h | C05 2h | F05 2h | 6h / 0h |
| 2026-11-10 | 2h | B06 2h | C06 2h | F06 2h | 6h / 0h |
| 2026-11-11 | 2h | B06 2h | C06 2h | F06 2h | 6h / 0h |
| 2026-11-12 | 2h | B07 2h | C06 2h | F06 2h | 6h / 0h |
| 2026-11-13 | 2h | B07 2h | C07 2h | F07 2h | 6h / 0h |
| 2026-11-14 | 5h | B07 3h, B08 2h | C07 5h | F07 3h; reserve 2h | 13h / 2h |
| 2026-11-15 | 5h | B08 3h, B09 2h | C08 5h | F08 2h; reserve 3h | 12h / 3h |
| 2026-11-16 | 2h | B09 2h | C08 2h | F08 2h | 6h / 0h |
| 2026-11-17 | 2h | B09 1h, B10 1h | C09 2h | F08 1h, F09 1h | 6h / 0h |
| 2026-11-18 | 2h | B10 2h | C09 1h, C10 1h | F09 2h | 6h / 0h |
| 2026-11-19 | 2h | B10 2h | C10 2h | F09 1h; reserve 1h | 5h / 1h |
| 2026-11-20 | 2h | B10 1h, B11 1h | C10 1h, C11 1h | F09 1h, F10 1h | 6h / 0h |
| 2026-11-21 | 5h | B11 5h | C11 5h | F10 3h, F11 2h | 15h / 0h |
| 2026-11-22 | 5h | No planned task; reserve 5h | No planned task; reserve 5h | F11 4h; reserve 1h | 4h / 11h |
| 2026-11-23 | 2h | No planned task; reserve 2h | No planned task; reserve 2h | F12 2h | 2h / 4h |
| 2026-11-24 | 2h | B12 1h; reserve 1h | C12 1h; reserve 1h | F12 2h | 4h / 2h |
| 2026-11-25 | 2h | B12 2h | C12 2h | F12 1h; reserve 1h | 5h / 1h |

## Day-by-day execution cards

### 2026-11-03 - Scope và identity baseline

- **Main objective:** Scope và identity baseline.
- **Backend tasks:** B01 2h.
- **Blockchain tasks:** C01 2h.
- **Frontend tasks:** F01 2h.
- **Prerequisites:** B01; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** B/C chốt BR/signing; F tìm contacts, ghi assumptions.
- **Concrete deliverables:** B01: Khóa RBAC/ERD/API (2h); C01: EVM/event/commitment agreement (2h); F01: Interview recruitment/product assumptions (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** No NGO contact ->R01; chỉ docs nếu chưa coding approval.
- **Can move:** Visual formatting; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-04 - ERD/events/wireframes

- **Main objective:** ERD/events/wireframes.
- **Backend tasks:** B02 2h.
- **Blockchain tasks:** C01 2h.
- **Frontend tasks:** F02 2h.
- **Prerequisites:** B01; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Agree case needs, ownership, random refs và error DTO.
- **Concrete deliverables:** B02: Spring/PostgreSQL/Flyway skeleton (2h); C01: EVM/event/commitment agreement (2h); F02: Wireframes/mock API states (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Interface drift; review trước scaffold.
- **Can move:** Nonessential screen variants; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-05 - Freeze initial contracts

- **Main objective:** Freeze initial contracts.
- **Backend tasks:** B02 1h, B03 1h.
- **Blockchain tasks:** C02 2h.
- **Frontend tasks:** F02 2h.
- **Prerequisites:** B01, B02, C01; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Review create/verify/audit mock success/403/409/pending.
- **Concrete deliverables:** B02: Spring/PostgreSQL/Flyway skeleton (1h); B03: Session auth và tenant isolation (1h); C02: Hardhat local setup (2h); F02: Wireframes/mock API states (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Chưa interview được ->unvalidated, không invent partner.
- **Can move:** Extra research ngoài core questions; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-06 - M0 design readiness

- **Main objective:** M0 design readiness.
- **Backend tasks:** B03 2h.
- **Blockchain tasks:** C02 1h, C03 1h.
- **Frontend tasks:** F03 2h.
- **Prerequisites:** B02, B03, C01, C02, F02; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Scope/ERD/DTO/events/capacity và toolchain compatibility review.
- **Concrete deliverables:** B03: Session auth và tenant isolation (2h); C02: Hardhat local setup (1h); C03: Authorized verification contract slice (1h); F03: Next shell/session client (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Unsupported dependency ->pin compatible patch.
- **Can move:** Scaffold conveniences; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-07 - Auth/case và local registry

- **Main objective:** Auth/case và local registry.
- **Backend tasks:** B03 2h, B04 3h.
- **Blockchain tasks:** C03 4h, C04 1h.
- **Frontend tasks:** F03 2h, F04 3h.
- **Prerequisites:** B02, B03, B04, B05, C01, C02, C03, F02, F03; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** B/F login integration; C negative relayer/org tests.
- **Concrete deliverables:** B03: Session auth và tenant isolation (2h); B04: Case/needs/consent/evidence APIs (3h); C03: Authorized verification contract slice (4h); C04: Fuji pilot deployment (1h); F03: Next shell/session client (2h); F04: Case and verifier UI (3h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 5h/person capacity; team planned 15h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** CSRF/roles errors block rollout.
- **Can move:** Navigation styling; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-08 - Case review và Fuji pilot

- **Main objective:** Case review và Fuji pilot.
- **Backend tasks:** B04 1h, B05 4h.
- **Blockchain tasks:** C04 1h, C05 4h.
- **Frontend tasks:** F04 3h, F05 2h.
- **Prerequisites:** B03, B04, B05, C01, C03, C04, C05, F03, F04; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** B snapshot/outbox; C actual deploy; F case/reviewer binding.
- **Concrete deliverables:** B04: Case/needs/consent/evidence APIs (1h); B05: Independent case review (4h); C04: Fuji pilot deployment (1h); C05: Java adapter/minimal audit slice (4h); F04: Case and verifier UI (3h); F05: Audit vertical slice UI (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 5h/person capacity; team planned 15h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Faucet/RPC/ABI issue ->early fix, no fake address.
- **Can move:** Explorer source verification if service unavailable; retain receipt; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-09 - Adapter/UI handshake

- **Main objective:** Adapter/UI handshake.
- **Backend tasks:** B06 2h.
- **Blockchain tasks:** C05 2h.
- **Frontend tasks:** F05 2h.
- **Prerequisites:** B05, C04, C05, F04; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** C/F validate exact eventId/receipt; B campaign groundwork.
- **Concrete deliverables:** B06: Multi-item campaign draft/publish (2h); C05: Java adapter/minimal audit slice (2h); F05: Audit vertical slice UI (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** ABI mismatch ->fix mapping, no manual confirmation.
- **Can move:** Campaign polish; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-10 - Audit slice stabilization

- **Main objective:** Audit slice stabilization.
- **Backend tasks:** B06 2h.
- **Blockchain tasks:** C06 2h.
- **Frontend tasks:** F06 2h.
- **Prerequisites:** B05, B06, C03, F02; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Show status1+expected log, prepare milestone events.
- **Concrete deliverables:** B06: Multi-item campaign draft/publish (2h); C06: Full milestone contract (2h); F06: Campaign multi-item UI (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Null receipt stays pending.
- **Can move:** Extra milestone screen variants; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-11 - M1 vertical slice acceptance

- **Main objective:** M1 vertical slice acceptance.
- **Backend tasks:** B06 2h.
- **Blockchain tasks:** C06 2h.
- **Frontend tasks:** F06 2h.
- **Prerequisites:** B05, B06, C03, F02; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** All: create->other CV verify->actual Fuji receipt/event->UI; self-approval rejected.
- **Concrete deliverables:** B06: Multi-item campaign draft/publish (2h); C06: Full milestone contract (2h); F06: Campaign multi-item UI (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** If fails freeze screens and pair critical path.
- **Can move:** Campaign increments until slice passes; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-12 - Core phase gate

- **Main objective:** Core phase gate.
- **Backend tasks:** B07 2h.
- **Blockchain tasks:** C06 2h.
- **Frontend tasks:** F06 2h.
- **Prerequisites:** B06, C03, F02; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Verify exact approved campaign targets; milestone contract tests.
- **Concrete deliverables:** B07: Commitments/cancel/idempotency (2h); C06: Full milestone contract (2h); F06: Campaign multi-item UI (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Commitment gate still planned15/11; do not overclaim phase1.
- **Can move:** Browse filters; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-13 - Multi-line pledges/durable worker

- **Main objective:** Multi-line pledges/durable worker.
- **Backend tasks:** B07 2h.
- **Blockchain tasks:** C07 2h.
- **Frontend tasks:** F07 2h.
- **Prerequisites:** B06, B07, C05, F06; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** B/F same-key retry and409 examples; C persist before broadcast.
- **Concrete deliverables:** B07: Commitments/cancel/idempotency (2h); C07: Durable signing/submission worker (2h); F07: Donor pledge/history/cancel (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Any item overflow rolls back all.
- **Can move:** Donor history styling; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-14 - Concurrency and nonce proving

- **Main objective:** Concurrency and nonce proving.
- **Backend tasks:** B07 3h, B08 2h.
- **Blockchain tasks:** C07 5h.
- **Frontend tasks:** F07 3h; reserve 2h.
- **Prerequisites:** B06, B07, C05, F06; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** B/C shared lock/nonce review; F pledge integration.
- **Concrete deliverables:** B07: Commitments/cancel/idempotency (3h); B08: Partial goods receipts (2h); C07: Durable signing/submission worker (5h); F07: Donor pledge/history/cancel (3h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 5h/person capacity; team planned 13h; reserve 2h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Deadlock/unknown nonce requires fix before wider writes.
- **Can move:** F2h reserve; nonessential UI polish; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-15 - M2 goods commitment gate

- **Main objective:** M2 goods commitment gate.
- **Backend tasks:** B08 3h, B09 2h.
- **Blockchain tasks:** C08 5h.
- **Frontend tasks:** F08 2h; reserve 3h.
- **Prerequisites:** B07, B08, B09, C06, C07, F04; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Concurrent pledge+partial receipt tests; F mocks goods UI.
- **Concrete deliverables:** B08: Partial goods receipts (3h); B09: Delivery records/failed attempts (2h); C08: Receipt/retry/log reconciliation (5h); F08: Receipt/delivery UI (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 5h/person capacity; team planned 12h; reserve 3h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** No-show is not received; F3h reserve available.
- **Can move:** Filters/reports; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-16 - Goods/receipt recovery

- **Main objective:** Goods/receipt recovery.
- **Backend tasks:** B09 2h.
- **Blockchain tasks:** C08 2h.
- **Frontend tasks:** F08 2h.
- **Prerequisites:** B08, B09, C06, C07, F04; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** C lost response/restart tests; B/F stock availability.
- **Concrete deliverables:** B09: Delivery records/failed attempts (2h); C08: Receipt/retry/log reconciliation (2h); F08: Receipt/delivery UI (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Wrong/null receipt never confirmed.
- **Can move:** Timeline polish; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-17 - Delivery/confirmation groundwork

- **Main objective:** Delivery/confirmation groundwork.
- **Backend tasks:** B09 1h, B10 1h.
- **Blockchain tasks:** C09 2h.
- **Frontend tasks:** F08 1h, F09 1h.
- **Prerequisites:** B08, B09, B10, C08, C09, F04, F08; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** F binds delivery API; B/C immutable snapshot refs.
- **Concrete deliverables:** B09: Delivery records/failed attempts (1h); B10: Confirm/dispute/resolve/close (1h); C09: Full sanitized audit API (2h); F08: Receipt/delivery UI (1h); F09: Confirmation/dispute/full timeline (1h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Mocks cannot satisfy integrated Done.
- **Can move:** Secondary display variants; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-18 - M3 chain sync gate

- **Main objective:** M3 chain sync gate.
- **Backend tasks:** B10 2h.
- **Blockchain tasks:** C09 1h, C10 1h.
- **Frontend tasks:** F09 2h.
- **Prerequisites:** B08, B09, B10, C08, C09, F08; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** BR10/11, cursor replay/repair, public/private audit.
- **Concrete deliverables:** B10: Confirm/dispute/resolve/close (2h); C09: Full sanitized audit API (1h); C10: Goods-to-chain integration (1h); F09: Confirmation/dispute/full timeline (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** If unstable cut enhancements and pair.
- **Can move:** Monitoring dashboards; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-19 - Independent review/disputes

- **Main objective:** Independent review/disputes.
- **Backend tasks:** B10 2h.
- **Blockchain tasks:** C10 2h.
- **Frontend tasks:** F09 1h; reserve 1h.
- **Prerequisites:** B08, B09, B10, C08, C09, F08; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Distinct recorder/confirmer, open dispute guards.
- **Concrete deliverables:** B10: Confirm/dispute/resolve/close (2h); C10: Goods-to-chain integration (2h); F09: Confirmation/dispute/full timeline (1h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 5h; reserve 1h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** No auto-complete; F1h reserve.
- **Can move:** Extra artwork; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-20 - Core workflow regression readiness

- **Main objective:** Core workflow regression readiness.
- **Backend tasks:** B10 1h, B11 1h.
- **Blockchain tasks:** C10 1h, C11 1h.
- **Frontend tasks:** F09 1h, F10 1h.
- **Prerequisites:** B03, B04, B05, B06, B07, B08, B09, B10, C06, C07, C08, C09, C10, F08, F09; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** All compare per-item totals, void history and public status.
- **Concrete deliverables:** B10: Confirm/dispute/resolve/close (1h); B11: Backend cross-feature regression (1h); C10: Goods-to-chain integration (1h); C11: Contract/failure regression (1h); F09: Confirmation/dispute/full timeline (1h); F10: Public hosting/recovery (1h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 6h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Auth/privacy/quantity/false-confirmation bug blocks release.
- **Can move:** New features; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-21 - Security/failure suites and hosting

- **Main objective:** Security/failure suites and hosting.
- **Backend tasks:** B11 5h.
- **Blockchain tasks:** C11 5h.
- **Frontend tasks:** F10 3h, F11 2h.
- **Prerequisites:** B03, B04, B05, B06, B07, B08, B09, B10, B11, C06, C07, C08, C09, C10, C11, F09, F10; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** B/C mandatory tests; F HTTPS/cookies/private networking smoke.
- **Concrete deliverables:** B11: Backend cross-feature regression (5h); C11: Contract/failure regression (5h); F10: Public hosting/recovery (3h); F11: E2E/accessibility/privacy regression (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 5h/person capacity; team planned 15h; reserve 0h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Budget/eligibility fail ->local/video, no overspend.
- **Can move:** Deployment cosmetics; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-22 - M4 E2E stabilization

- **Main objective:** M4 E2E stabilization.
- **Backend tasks:** No planned task; reserve 5h.
- **Blockchain tasks:** No planned task; reserve 5h.
- **Frontend tasks:** F11 4h; reserve 1h.
- **Prerequisites:** B11, C11, F10; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** F E2E01-07; B/C each5h reserve for defects/pairing.
- **Concrete deliverables:** F11: E2E/accessibility/privacy regression (4h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 5h/person capacity; team planned 4h; reserve 11h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** Incomplete core ->Minimum Demo with explicit gaps.
- **Can move:** All P1/new screens; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-23 - Feature freeze/submission prep

- **Main objective:** Feature freeze/submission prep.
- **Backend tasks:** No planned task; reserve 2h.
- **Blockchain tasks:** No planned task; reserve 2h.
- **Frontend tasks:** F12 2h.
- **Prerequisites:** F11; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** F script/pitch; B/C each2h reserve for critical fixes.
- **Concrete deliverables:** F12: Demo/video/pitch/submission (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 2h; reserve 4h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** No unrun test Pass or invented impact.
- **Can move:** Cosmetic issues; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-24 - Recovery and demo rehearsal

- **Main objective:** Recovery and demo rehearsal.
- **Backend tasks:** B12 1h; reserve 1h.
- **Blockchain tasks:** C12 1h; reserve 1h.
- **Frontend tasks:** F12 2h.
- **Prerequisites:** B11, C11, F11; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Final runbooks, actual Fuji proof and local/video contingency.
- **Concrete deliverables:** B12: Critical fixes/final handover (1h); C12: Deployment/key recovery runbook (1h); F12: Demo/video/pitch/submission (2h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 4h; reserve 2h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** RPC/public outage ->honest pending/historical proof.
- **Can move:** Optional slide details; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

### 2026-11-25 - M5 final smoke/submission

- **Main objective:** M5 final smoke/submission.
- **Backend tasks:** B12 2h.
- **Blockchain tasks:** C12 2h.
- **Frontend tasks:** F12 1h; reserve 1h.
- **Prerequisites:** B11, C11, F11; integrated Done follows dependency gates.
- **Collaboration / acceptance focus:** Actual closing time/rules verified; B/C smoke; F final artifacts.
- **Concrete deliverables:** B12: Critical fixes/final handover (2h); C12: Deployment/key recovery runbook (2h); F12: Demo/video/pitch/submission (1h). Output paths and behavior are in task specs; partial daily increments are drafts/testable slices, not premature Done.
- **Effort:** 2h/person capacity; team planned 5h; reserve 1h.
- **Acceptance:** demonstrate changed behavior and relevant test results; any task at planned finish must satisfy its specific acceptance, dependency, review and test gates. Record actual hours/results/blockers; no completion claim from code generation alone.
- **Risks/blockers:** No last-minute feature; unknown deadline hour must verify.
- **Can move:** All noncritical changes; never move mandatory privacy/identity/quantity/receipt checks merely to claim completion.

## Milestones

| Gate | Latest date | Evidence |
|---|---|---|
| M0 | 06/11 | Scope/BR trace/ERD/DTO/events/capacity reviewed; interview status explicit |
| M1 | 11/11 | Distinct create/verify actors, actual Fuji receipt+expected event, UI hash/status, self-approval rejected |
| M2 | 15/11 | Multi-item atomic commitment, receipt separation, concurrent capacity and replay tests |
| M3 | 18/11 | Durable signing, pending/revert/mismatch/restart/log repair and audit API |
| M4 | 22/11 | Full goods lifecycle/independent confirmation/dispute + public E2E |
| M5 | 25/11 | No critical blockers, actual Fuji proof, demo/video/official rules ready |

## Compressed and lower-capacity fallback

| Trigger | Compressed target schedule | Scope choices |
|---|---|---|
| Start3 days late /3-day delay | Freeze contracts first available day; slice by14/11, goods18/11, delivery/E2E22/11, submission23-25/11 | Parallel mocks/local contracts, no P1/dashboard/polish; recalculate actual available hours |
| Start7 days late /about118h total |10-12/11 contracts/auth;13-15/11 actual verified slice;16-19/11 minimal multi-item goods journey;20-22/11 security/E2E;23-25/11 submission | Minimum Demo, fewer records/screens; report missing Target features/anchors rather than promise full scope |
|18/11 chain unstable | One signer/worker, exact receipt/event verification and actual verification anchor; business flow/tests by22/11 | Cut enhancements, keep authorization/IDs/private evidence/status honesty |
| Public/RPC outage near submission | Stable local build+dated video+actual historical Fuji proof | Current unconfirmed events stay pending; no invented delivery/chain results |

Actual remaining hours must be recalculated from availability and date; compressed calendar is a contingency goal, not guaranteed Target MVP. Multi-item goods are user-confirmed and cannot silently become single-type. Mandatory separation/quantity/privacy/authorization/real-chain claims stay intact.

## Progress tracking

Task Backlog holds actual states/evidence. Preserve this baseline when changing forecasts; log actual effort/blocked days/buffer use. Critical dependency delayed>1 day or >50% buffer used before18/11 triggers scope-cut checkpoint. Auth/privacy/false-confirmation failures block release immediately. Do not use lines of code/commits as progress.
