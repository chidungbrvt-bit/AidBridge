# C06 — morning readiness and handoff, 18/11/2026

**Task #4: READY FOR HANDOFF TO TASK #5. Release: FREEZE CANDIDATE. Deployment/cutover: NOT APPROVED / NOT RUN.** Task #3 remains LOCAL PASS WITH NOTES; no C10 runtime tests rerun. This status supersedes older readiness snapshots, without granting freeze, organization setup, deployment or cutover permission.

## 1. Version and reproducible release candidate

- PR #35 remains OPEN. Observed remote head before this work: `5267e994f8521f6741d32c88ea36e163983b252f`; PR #34 merged into main `fbf8c3efeb78c260a97040a1612f76531559cf44`.
- Fast-forwarded local khoa to the remote runner fixes, then merged main normally at `ff08f97f63ad372f0653cae66dc69f06e941fcac`, without conflicts. All earlier review commits are preserved. No unmerged dung/minh code integrated.
- **Release-candidate SHA: `58a3ebb5d29e85e6e0ab81ca2b73b806c8b5235d`.** It contains the merged source, manifests, constructor plan, runner fixes from `5267e99`, tests and the public build archive below. Later status-only commits do not change this candidate.
- Archive: [`contracts/deployment/c06-release-candidate-2026-11-18.zip`](../../contracts/deployment/c06-release-candidate-2026-11-18.zip), **185464 bytes**, SHA-256 `3b3126518a0e27e18b34197101fb03329643f8a40564ed23f49c116900bf74c0`.
- Archive has 15 allowlisted entries: candidate manifest, proposed public plan, exact artifact, full build input/output including dependency sources, committed ABI/source, runner/core/helper/tests, package/lock/config and an internal SHA-256 inventory. No wallet, environment file, approval record, transaction or recovery journal. Its basis commit is `ff08f97`; archive itself is committed at `58a3ebb`.
- These exact build bytes are retained for reproduction/explorer verification. Do not normalize compiler-input source line endings. Git checkout line endings may change plan/runner file-byte hashes: approval must bind the actual reviewed manifest/plan bytes returned by preflight; it must never reuse a hash from a different checkout or approval.

| Component | Candidate pin |
|---|---|
| Contract source last-change SHA | `6944fffd9858565f36c909ae9e5f83804afbe36f` |
| Source SHA-256, normalized LF | `c27379bba82dd88897a3fe2bf1b0f5c2ad77e7f29830afef4dde7380ed272153` |
| Compiler/settings | solc `0.8.28+commit.7893614a`, Paris, optimizer enabled / 200 runs; full settings/remappings in manifest |
| Build ID | `solc-0_8_28-6d69e2cf1ddbaa7f61c90daac742b8efd4cf518a` |
| Standard input SHA-256 | `905d75015aea92047c4f9d1ae810f9b4b96df0e223f2437078ed9f804519a14e` |
| Artifact SHA-256 | `976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592` |
| ABI SHA-256, compact JSON | `548b46168243852ef455752f09afa6fba7989bb1fcc0b13ac0f6a64f24175eae` |
| Creation Keccak-256 | `0x9e8943c4e5dc82f7a0e9c60feaa6a2fc28d1b9c2b616aac80e20beccc143258c` |
| Runtime Keccak-256 | `0x525314d10311e01e01a61a6f9e75ee432f59ca09ff3aa27445aed3962e09011e` |
| Constructor | `initialAdmin(address)` = proposed `0x131DD6d404f8D0B118D6DB122194bc15b4E4601E` |
| Network | Fuji C-Chain 43113; official RPC in public plan; no RPC used this morning |

### Offline validation actually executed

1. Recompiled exact archived build input in memory with the existing native solc 0.8.28; exact ABI, creation and runtime bytecode **MATCH** current artifact. No artifact overwritten, compiler installed or download performed. One existing mutability warning, no compiler error.
2. `node --test contracts/scripts/c06-readiness.test.mjs`: **11 tests, 11 PASS, 0 failures/errors/skips**. Mocked RPC only: reproducibility, approval/hash/expiry gates, wrong chain, nonce/target checks, observed-tx resume without broadcast, same prepared bytes, AES authentication and canonical-receipt rejection. This is not Java/Hardhat/Fuji evidence.
3. `node --check` on deploy/core/helper/test modules: PASS. Archive CRC and all 14 public-file hashes against its internal inventory: PASS.

Runner default/prepare is offline; preflight and post-deploy are read-only RPC modes; broadcast requires explicit approved execution, FROZEN manifest and matching approval. It checks current chain/approval before signing or sending, persists separate C06 recovery journals first, and stops on partial journals, nonce/target/context mismatch or unknown outcomes. Recovery preserves bytes/hash/nonce; it does not deploy organization setup or alter backend/DB. Live execution of these paths remains NOT RUN; offline PASS is not an operational approval.

## 2. Current gates and owners

| Gate | Status | Owner / minimal next action |
|---|---|---|
| Candidate source/build/ABI/constructor/runner and offline rebuild | PASS | Khoa; package SHA above, not formally frozen |
| Task #3 local C10 | PASS WITH NOTES | Existing reviewed PR34 evidence; not reopened or rerun |
| Combined worker PREPARED + wrong-chain | SOURCE PRESENT / REPORTED PASS; XML NOT INSPECTED | Dung `5df5aacaecfda1a6a14d4eb7bd1b354c70de9faa`, tree `855c9482bc24196063c80b9e65a58690223bf5ff`; source assertions cover 0 send/resign/new attempt, retained bytes/nonce/hash and recovery. Task5 report says WorkerIT 34/0/0/0, XML SHA `910d696c827ec329a5d6d76ce7cd9b804d6d3d87beb5c66d7a787868d3dc0393`. Original XML remains on Dung's machine: provide it or relevant verifiable excerpts, no rerun requested |
| Org/pilot scope and public plan | PENDING GROUP DECISION | Dung/Khoa: choose org below before freeze; current plan still uses historical org, not staging org |
| Formal artifact + public plan freeze | PENDING | Group confirms candidate SHA/build/constructor/selected org and controlled freeze record; changed plan requires a new candidate/hash review |
| Admin/deployer/relayer identity, current funds/nonce/EOA, RPC/fees | NOT RUN current preflight | Khoa/operator checks public addresses with approved read-only preflight; no key needed. Historical rights/balance do not prove current readiness |
| Deployment approval | PENDING; broadcast BLOCKED | Group/operator approves exact FROZEN manifest/plan hashes, scope, nonce, target and expiry after gates are met |
| C03 isolation / staging binding / backup / one-worker plan | IN REVIEW on dung, NOT APPLIED | Dung Task5; refresh inventory at execution time, provide approved disposition and recovery plan |
| Deployment, canonical post-readback, explorer verification, org setup | NOT RUN | Khoa after respective permissions; new registry starts empty, C03 roles/state do not migrate |
| Cutover approval / registry binding / Fuji pilot | PENDING / NOT RUN | Separate group permission + Dung execution after verified C06 deployment/setup and Task5 gates |

**Readiness handoff is complete; READY FOR DEPLOY APPROVAL is not yet reached.** Remaining evidence/operational decisions have named owners; they do not keep Khoa's preparation task BLOCKED.

## 3. Proposal before noon — org and scope

New information is read from **unmerged** `origin/dung@9f29b762f4021467d6e265b76db31b9f7f496c84`, [Task5 report](https://github.com/chidungbrvt-bit/AidBridge/blob/9f29b762f4021467d6e265b76db31b9f7f496c84/docs/reviews/C06_TASK5_DB_CUTOVER_2026_11_18.md) and its committed CSVs. This is Dung-provided read-only evidence, not a new DB/RPC inspection by Khoa.

**Recommended limited pilot: staging org + existing staging case, CASE_VERIFIED only.** This reuses the backend demo prepared for Minh and avoids starting the legacy V15 DB or fabricating new demo IDs. It is a proposal, not approval:

- DB `aidbridge_demo_v16`, case `85e124e4-922b-4366-8f70-6c35cfd3d6e8`, public caseRef `0x87db7e09856e03b7d8465936543a1821ff91e48e7b226254b0fe9a67ed37de2e`.
- Org public ref `0x26b3e1feb12244348aae40a3eb2db479daa4562b627049eba9d6710b92b7299f`, APPROVED in the CSV, **relayer_address NULL**. Org UUID, campaign ID/public visibility and actor accounts still require Dung's binding handoff; do not infer them from case UUID.
- Current C06 candidate plan uses **different** orgRef `0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46`. Do not deploy/setup this plan and assume it authorizes staging. If the staging proposal is approved, Khoa updates the public plan and republishes/revalidates a new candidate before freeze; no plan silently changed here.
- Alternative if group retains the existing plan org: Dung provisions/qualifies a **new** case/campaign under that org in an approved staging lane, starting sequence 1. No such new IDs are claimed to exist.
- Existing staging jobs are sequence 1 CASE_VERIFIED, 2 AID_PLEDGED, 3 GOODS_RECEIVED, 4 DELIVERY_RECORDED, 5 DELIVERY_CONFIRMED: all PENDING and unprepared/no attempts per CSV. This is **not** the five-operation full-chain fixture: it lacks reduction; DELIVERY_CONFIRMED has no worker implementation. Do not rewrite jobs/sequence or insert a historical reduction to make it look complete.
- Current worker claims goods only on 31337. On Fuji, sequence 1 can be the limited pilot after deployment/setup/cutover; 2–4 remain PENDING, 5 unsupported. Use one worker only, stop after the approved CASE_VERIFIED proof; do not authorize goods by changing a flag. Any extension to Fuji goods needs Dung's reviewed code/tests and deployment-context safeguards separately.
- Three legacy PENDING jobs remain **parked unchanged** in `aidbridge_demo`; keep its worker off, its two C03 confirmed proofs/attempts and journals intact. Do not run the latest backend on its V15 schema without a separate migration decision. PostgreSQL signer locks are per DB: never enable two databases' workers with the same relayer.

For demo 19/11: backend-real goods/delivery/independent-confirmation UI can run with explicit blockchain PENDING/local labels. Only historical C03 CASE_VERIFIED currently has accepted Fuji evidence; C06 CASE_VERIFIED becomes Fuji-proven only after the new pilot is actually accepted. Hardhat full-chain PASS is not Fuji goods PASS.

## 4. Post-deploy and afternoon proof checklist — all NOT RUN

1. Capture approved release SHA, exact FROZEN manifest/plan hashes and actual chain ID 43113, deployed address and deployment tx hash. Predicted addresses/local addresses are not binding evidence.
2. Check original transaction chain/from/to=null/nonce/constructor against plan; receipt status 1, hash, contractAddress, block number/hash and canonical block. Compare **full** runtime bytes/hash to release. Save public C06 journal separately from C03.
3. Read DEFAULT_ADMIN_ROLE and empty initial organization/relayer state before setup; run the existing `post-deploy` verifier at this stage. After separately approved registration, read org active/assigned relayer/RELAYER_ROLE again. Initial-state verifier is not to be rerun blindly after org setup.
4. Verify source on explorer with archived standard JSON, exact compiler/settings/constructor, then retain verification result/link. Do not label VERIFIED merely because runtime matches. Handoff real address/chain/deploy block/tx, ABI/artifact hashes, setup receipts and role readback to Dung.
5. Dung checks Task5 backup/inventory/preparation/attempts/nonces, same-deployment bindings and one active worker, then obtains **separate cutover approval** before configuration/DB changes. After preparation/signing, never switch back to C03 or restore a backup that discards signed attempts: stop and reconcile the pinned deployment.
6. For each approved pilot operation, match DB outbox/job/attempt and expected ABI event: orgRef/caseRef/eventId/operation/schemaVersion/chain_sequence, stable refs and evidence snapshot. Check txHash/from/to/chain, canonical receipt/blockHash/blockNumber, topic0/address/data, nonnegative logIndex and not removed. Receipt status alone is insufficient. Predecessor must be same case/chain/registry; sequence starts at 1 on the new C06 case prefix.
7. If a later approved scope includes D55 VOID, DeliveryVoided and DisputeResolved have distinct logIndex but one logical eventId/job/sequence. Do not count two operations or mix any C03 proof into C06.
8. Capture anonymous **D49** `GET /api/public/cases/{publicCaseRef}/audit`, and scoped T01 `/api/cases/{id}/audit-trail`. Compare statuses/sequence/eventId/chain/registry/hash/block to the proof ledger; missing/mismatched proofs must remain unverified. Dung's new Task5 runbook section 4.3 step 6 still names the old campaign URI: update that instruction to D49 before pilot; do not add an endpoint alias.
9. Minh checks the actual response, business/review/blockchain status separation and explorer links for the accepted deployment; link failures stay disabled. Do not treat the public DTO as independent C10 proof: raw block/log context comes from the private review bundle. See [C09 proof checklist](C09_FUJI_PROOF_ACCEPTANCE_2026_11_17.md).

## 5. Decisions/handoff now

- **Dung/group before noon:** accept limited CASE_VERIFIED scope; confirm staging org/case/campaign/accounts or the alternate fresh-case lane; approve parking the three old jobs; provide original combined-regression XML/excerpts and final backend SHA. No new test run requested.
- **Group/Khoa:** after org choice, review the resulting candidate public plan/constructor/addresses and freeze decision. Current candidate is retained for comparison, not approved deployment input.
- **Khoa after permission:** revise candidate if needed; run current read-only preflight; submit exact freeze/deploy approval inputs. Only after explicit transaction permission deploy/verify C06 and hand off actual metadata. Dung then seeks cutover permission separately; pilot requires its own accepted scope.
- No backend/frontend/worker changes, DB/queue changes, secret access, live RPC, signing, deployment, source-verification submission or cutover were performed in this work.
