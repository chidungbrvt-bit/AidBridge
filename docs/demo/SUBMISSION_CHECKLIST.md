# Submission Checklist

Ngày tạo: 03/11/2026. Owner: F. Status: future execution checklist. **Unchecked application items are intentional: implementation/tests/deployment have not started.** Internal targets: P0 complete18/11, stable20/11, package ready22/11.25/11/2026 is external submission limit only; official time/timezone/platform rules must be verified.

## Planning workspace

- [x]19 substantive Markdown documents present and linked.
- [x]Source PDF copied, original preserved, SHA-256 equal.
- [x]All31 Master Prompt sections mapped to documents.
- [x]36 tasks, one primary owner each,168 planned hours +24h reserve against192h capacity.
- [x]23 dates, valid dependencies and task-hour allocations.
- [x]P0->story->BR/API->task->test->milestone trace complete.
- [x]No application source/migrations/dependencies/Git/containers/deployment created during planning.
- [x]All changes inside AidBridge.

These planning checks may be marked complete only after actual filesystem/document validation. They are distinct from application completion.

## Before coding

- [ ]User authorizes application implementation/task scope; technical proposals and change boundaries understood.
- [ ]B/C/F actual availability confirmed; reforecast if start date changes.
- [ ]Initial DTO/error/idempotency/event schema reviewed across all three owners.
- [ ]Toolchain patches compatible and pinning plan accepted.
- [ ]Demo account/key custodians chosen; no shared staff account.
- [ ]NGO interview results or explicit unvalidated assumptions recorded.

## Early-delivery schedule

- [ ]18/11 all P0 integrated with feature-level tests/reviews, actual Fuji milestone flow and public first smoke.
- [ ]20/11 mandatory full regression/security/failure/E2E and public demo stable, no critical defects.
- [ ]22/11 video/pitch/runbooks/README/package complete and rehearsed.
- [ ]23-25/11 kept for actual contingencies/submission only; pull ready tasks earlier whenever possible.

## Functional / integrity gate

- [ ]Separate accounts authenticate/logout; membership isolation and CSRF work.
- [ ]FV creates case with consent/evidence; private fields not public.
- [ ]Distinct CV approves/rejects/requests changes; self-approval fails even dual-role.
- [ ]Only verified case publishes; approved multi-item target copies exact.
- [ ]Multi-line pledge/cancellation obey target and idempotency; concurrent overflow rejected atomically.
- [ ]Partial receipt cannot exceed unreceived pledge; pledge not counted as received.
- [ ]Delivery cannot exceed received less already-recorded delivery; failed attempt counts zero.
- [ ]Distinct DC confirms; recorder cannot self-confirm; repeat confirmation cannot double-count.
- [ ]Every item confirmed target before close; dispute blocks/reopens completion.
- [ ]Evidence versions and reviewed snapshots immutable; corrections preserve original history.
- [ ]Public timeline distinguishes business, evidence and chain status; no false physical proof wording.

## Blockchain gate

- [ ]Contract tests SC01-SC11 and BR12 unauthorized/wrong-org/revoked callers pass.
- [ ]Non-upgradeable registry deployed on actual Fuji43113; address/code/ABI/compiler/deployment block recorded.
- [ ]Dedicated admin vs relayer separation, balance/rotation/revoke runbook checked.
- [ ]Real verification transaction has successful receipt and exact expected event.
- [ ]Target MVP pledge/receipt/delivery/confirmation/dispute anchors integrated or gaps explicitly reported.
- [ ]Signed bytes/hash persisted before send; retry same logical ID; nonce history preserved.
- [ ]Null/reverted/mismatch receipts never become confirmed; missing receipt shows pending.
- [ ]Crash/restart and event-log reconciliation demonstrated.
- [ ]Explorer links correct network/address/hash; source verification claim accurate.
- [ ]No PII, files, URLs, salts or keys in calldata/events/public responses/logs.

## Tests and review

- [ ]BR01-P/N through BR12-P/N executed with evidence.
- [ ]IT01-IT16 mandatory scenarios checked, including actual PostgreSQL concurrency.
- [ ]E2E01-E2E07 critical flows/accessibility and privacy pass.
- [ ]Auth/contract/signing/concurrency changes cross-reviewed.
- [ ]No unresolved critical auth/privacy/quantity/false-confirmation issue.
- [ ]Results list real commands/build/date/actual status; no claimed passing unrun tests.

## Public deployment / recovery

- [ ]Provider/current pricing/eligibility and user-authorized purchase checked; total budget ceiling$20 including relevant fees.
- [ ]Usage alerts/limits with margin and projected spend checked; do not guarantee absolute provider billing cap without verifying semantics.
- [ ]HTTPS/same-origin proxy/secure cookies/CSRF; CORS restrictive.
- [ ]Private DB networking, secrets env only, protected evidence, detailed health/OpenAPI restricted.
- [ ]Flyway validate/migrate from clean and prior schema; no auto-ddl update.
- [ ]Backup+restore and app rollback instructions rehearsed; tx journal preserved.
- [ ]Pending/failed job age, balance/RPC errors/log privacy observed.
- [ ]Local stable build, actual historical Fuji evidence and backup video prepared.

## Competition submission / demo

- [ ]Official event/submission URL, closing date/time/timezone verified by F;25/11 planning date not substitute for rules.
- [ ]Required repository visibility/license/team/track/Avalanche requirements verified; create/publish repo only when authorized.
- [ ]README and architecture/API docs reflect actual implemented state and gaps.
- [ ]Contract address/chain/deployment and selected actual tx hashes included safely.
- [ ]Demo data is fictional; no fabricated NGO partner or measured impact.
- [ ]Video length/format/link access meets rules; actual flow and negative security test visible.
- [ ]Pitch emphasizes social/operational/trust/technical value and limitations.
- [ ]Rehearsal runs entire workflow with separate actors; public and fallback paths work.
- [ ]Submission reviewed by all three; proof of submission saved after actual submit.

## After submission

- [ ]Stop unnecessary paid services within budget; preserve required artifacts first.
- [ ]Rotate/revoke demo secrets, preserve public chain context, reset simulated DB/files/backups within30 days.
- [ ]Record actual effort, defects, NGO feedback, next pilot conditions and legal/privacy decisions; no real beneficiaries onboarded by default.

## Evidence ledger template

`Gate/Test ID | date | owner | build/environment | command or actual tx | actual result | artifact link | remaining gap`. Populate only after execution. Planning QA evidence is recorded separately in Master Plan; no deployment hash or application pass result exists at this stage.
