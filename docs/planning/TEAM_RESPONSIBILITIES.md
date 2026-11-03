# Team Responsibilities

Ngày tạo: 03/11/2026. Owner: F. Status: planned assignments, member names chưa được cung cấp nên dùng B/C/F; đúng ba developers, không giả định đã có thêm staff.

## Responsibility / workload

| Member | Primary | Support/review | Task count / effort / reserve | Learning / blockers / critical path |
|---|---|---|---|---|
| B Backend Engineer | Spring/DB/Flyway, auth/RBAC, cases/review, goods APIs, quantity/concurrency | Review C contract permissions và ABI; review F API/error behavior |12 /56h /8h | Spring Security, row locks, idempotency; backend load cao nhất về số domain rules |
| C Blockchain & Integration | Solidity/Hardhat, deploy, Java adapter, durable signing/jobs, logs, audit projection | Own audit API để giảm B load; DB-chain integration tests; pairing delivery event integration |12 /56h /8h | EVM/nonce/receipts và Java integration là technical risk lớn nhất; cần B review |
| F Frontend & Product | Interviews, wireframes, Next UI, mocks/client, public deployment, E2E/video/pitch | TypeScript contract-test help khi C blocked; public/privacy claims review |12 /56h /8h | CSRF/proxy/session, async statuses, hosting; UI breadth được giảm bằng gộp screen |

Hours là estimates, không giả vờ complexity cân bằng tuyệt đối. C có ít domain screens nhưng technical uncertainty cao; B có nhiều business rules; F có external validation/deployment uncertainty. Không giao hết audit API/test cho B. Mỗi issue có một owner, support không tạo double booking; việc hỗ trợ lớn phải re-estimate hoặc dùng buffer.

Before implementation, từng member xác nhận calendar2h weekday/5h weekend có thực tế không; nếu khác, update actual capacity và scope. Người phỏng vấn/đối tác bên ngoài không tính là developer thứ tư.

## Just-in-time learning (đã gồm trong task effort)

Không học full courses. Topic estimates dưới đây là phần bên trong 56h, không cộng thêm; bài tập nhỏ có thể thành implementation/test của task.

| Member / topic | Why / minimum knowledge | When / learning effort | AidBridge exercise / ready when |
|---|---|---|---|
| B Security sessions/CSRF (JWT đọc so sánh, không implement) | Session lifecycle, cookie flags, CSRF token rotation | B03,1.5h | Login/logout; missing CSRF403; giải thích vì sao không cần JWT refresh |
| B Multi-org RBAC | Principal vs membership, owner/state/identity guards | B01/B03,1h | FV orgA không tạo/read orgB; same-user dual-role self-approval fails |
| B JPA/SQL transactions/locking | Transaction scope, FOR UPDATE, lock order, rollback | B07/B09,1.5h | Two concurrent pledges last unit ->one success; no partial multi-line write |
| B Flyway | Ordered SQL migrations, validation, no ddl-auto update | B02,0.5h | Empty PostgreSQL migrates; existing schema upgrades without reset |
| B DTO validation/errors | Bean Validation, problem+json, no entity serialization | B04,0.5h | Invalid need qty400 fieldErrors; public DTO no private fields |
| B Idempotency/integration tests | Unique key, atomic response journal, actual PostgreSQL | B07/B11,1h | Retried command same resource, changed body409 |
| B Scheduled integration boundary | Outbox not synchronous RPC | B05 with C,0.5h | DB commits even RPC offline; explain no SQL tx wait |
| C EVM/Fuji/keys/gas | Wallet vs account, chainId, public RPC, test funding | C01,1.5h | Explain deploy admin/relayer roles and chain43113 |
| C Solidity state/events/ABI | mappings, modifiers, custom errors, logs | C02/C03,2h | Authorized verify emits exact event; unknown caller revert |
| C AccessControl/security | Org-scoped roles, revoke, no money/external calls | C03/C06,1h | Wrong-org/revoked address cannot record; admin not automatic relayer |
| C Hardhat tests/deploy/verify | Local accounts, receipt/event assertions, compiler metadata | C02/C04,1h | Test local then actual Fuji pilot, retain ABI/address/block |
| C Web3j signing/nonce | ABI encoding, signed raw bytes/hash, pending vs mined | C05/C07,1.5h | Persist before broadcast; retry exact same hash after timeout |
| C Receipts/logs/reconcile | Status1 not enough without expected context; cursor replay | C08/C11,1h | Wrong log never confirmed; DB save failure repaired from logs |
| F Next routing/TS/components | App Router, typed DTOs, client/server secret boundaries | F02/F03,1h | Login/case routes, no secret in browser bundle |
| F API/session/CSRF | Credential forwarding, refresh token after login/logout | F03,1h | Authenticated request via same-origin proxy; handle401/403 |
| F Forms/validation/accessibility | Controlled inputs, field errors, keyboard/focus | F04/F06,1h | Need rows keyboard editable; 400 errors focus summary |
| F Async UI/local state | Pending/failed/confirmed, stale409, polling backoff | F05/F09,1h | Submitted tx hash stays pending until exact confirmed projection |
| F Role-based UX | Hide affordance plus server denial; same-user restriction | F04/F09,0.5h | Self-approve UI unavailable, direct API still403 |
| F Deployment/E2E | HTTPS/env/private networking/cost limits, Playwright | F10/F11,1.5h | Public smoke under budget; privacy flow automated |

## Parallel work / handoffs

03-06: B schema/DTO, C events/commitment vectors, F workflows/mocks/interview attempts. Freeze create/verify/audit first; DTO/ABI changes reviewed before independent implementation.

07-11: B auth/case/review; C local registry/Fuji/Java adapter; F mocked screens then real endpoints. No frontend idle waiting for all APIs. C05 includes minimal audit read API; C09 expands full projection.

12-18: B multi-item pledge/receipt; C contract milestones/outbox/reconcile; F campaign/donor/audit. Mock responses include overflow409, rejected review and pending receipt, not just success.

19-22: pair B/C for delivery/confirmation/disputes; F full UI/E2E/public. All members run complete demo using separate accounts, not just their own layer.

23-25: freeze new features; prioritized defects and submission. Review/public demo readiness over code-volume metrics.

## Documentation ownership registry

All19 documents created as planning baseline03/11/2026. Initial documentation is complete before coding; implementation-related updates below have target completion dates, not claims of work done.

| Owner | Documents | Update gate |
|---|---|---|
| B | AGENTS, Decision Log, Master Plan, Task Backlog, System Design, Business Workflows, Database Design, API Specification, Security & Privacy | Contracts06/11, affected task each merge, final23-25/11 |
| C | Blockchain Architecture, Test Strategy | Slice11/11, full sync18/11, actual tests/evidence22-25/11 |
| F | README, Product Requirements, MVP Scope, Daily Roadmap, Team Responsibilities, Risk Register, Demo Scenario, Submission Checklist | Validation06/11, daily progress, public/demo22-25/11 |

## Standup / checkpoint templates

Daily async: `Date | member | completed task+evidence | next task | actual hours | blocker+owner+next action | help needed | remaining buffer`. No status theater; a failing mandatory test is not Done.

Integration checkpoint: `build/branch artifact | API/ABI compatibility | Flyway upgrade | roles/org isolation | create->verify->audit | multi-item quantities | pending/failed chain behavior | critical defects | cost forecast | next owner/action`. Run at phase gates and at least weekly,10-15m; focused pairing for blockers.

Task states: Not Started, In Progress, Blocked, In Review, Testing, Done. Done requires evidence from DoD. Report planned vs accepted, blocked critical days, tests failing, readiness, actual effort and budget. Reassign tasks only with explicit owner update; don't leave shared tasks ownerless.
