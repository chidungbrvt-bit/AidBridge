# Task #8 — C06 redeploy preparation / offline dry-run, 16/11/2026

**Gói kỹ thuật cập nhật17/11:** entrypoint chính `contracts/scripts/c06-deploy.mjs`; manifest sinh từ build thực tế `contracts/deployment/c06-freeze-candidate.json` (**FREEZE CANDIDATE**, không FROZEN); plan publicC06riêng, coreC06riêng và7offline tests PASS. Runner đã được viết, chưa chạy preflightFuji/broadcast/post-deploy thật. Các ghi chú “chưa có runner” bên dưới là snapshot cũ, được mục “Gói deployment readiness hoàn chỉnh” cuối tài liệu thay thế. HandoffTask4 DONE; approval/combinedregression/currentpreflight/DBcutover vẫn riêng.

**KẾT LUẬN HIỆN HÀNH17/11 — Task4: READY FOR HANDOFF TO TASK #5.** Phần readiness/preparation của Khoa đã hoàn tất, Task3 local PASS WITH NOTES. **Chưa READY FOR DEPLOY APPROVAL; chưa có quyền deploy/cutover.** Các nhãn BLOCKED ở snapshot cũ bên dưới mô tả execution gates tại thời điểm đó, không còn là trạng thái handoff Task4. Xem mục “Chốt acceptance Task4” cuối tài liệu.

**DONE WITH GATES.** Chuẩn bị của Khoa hoàn tất; **BLOCKED BY WORKER / C06 RUNNER / FREEZE-APPROVAL** trước deployment. Artifact consistency kỹ thuật đã PASS sau compile, chưa phải approved release artifact. **C06 NOT DEPLOYED; CUTOVER NOT EXECUTED.** Tài liệu này không cấp phép deploy, đăng ký org, chuyển cấu hình hoặc bật worker. Deploy và cutover cần hai approval riêng.

## 1. Baseline / C03 lịch sử

Fetch main `15bcba4ec5cce8bf07b929353e68d26b006a0668`, dung `c288f4409023e9f2352082ff91428f324d7445bb`; khoa `88821c4` trước runbook. Source/config/deploy runner trên khoa không khác main. Giữ các local review cda5360/e98ac9d/27bae6b/88821c4.

Theo manifest C04 và [handoff Relayer](FUJI_RELAYER_HANDOFF_2026_11_12.md), **không đọc RPC lại trong Task8**:

| C03 public record | Giá trị lịch sử |
|---|---|
| Network / chain | Avalanche Fuji C-Chain /43113 |
| Registry | `0x015D941c679b0c402c8262BE237adAD36cEf6726` |
| Source | `61cc738385e8b374d38996e8a725a9da6fa7f12b` |
| Reviewed snapshot / ABI | contracts/deployment/AidRegistry.c03-reviewed.artifact.json / AidRegistry.fuji.abi.json |
| Creation Keccak256 | `0x595854a953b927a241515105022d9aa15f4ebb3eee41daa482d9e59c3f5642fb` |
| Runtime Keccak256 | `0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad` |
| ABI JSON SHA256 | `8042b783190a40d0843157dca4e273528bc49176ad134011e48cbb97aef340be` |
| Deploy tx / block | `0xf5a11026f7bea424b6ce538ab74f7618e619264e2be86d5a7407e92505b49d24` /60885528 |
| Org registration tx | `0xf3b4fb81d07483a797fd7b361491c6dc502bebc5994f5835e37f7f8d1638eeea` |
| Admin/deployer public address | `0x131DD6d404f8D0B118D6DB122194bc15b4E4601E` |
| Pilot orgRef | `0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46` |
| D54 current assigned relayer per12/11 readback | `0x95a9564899849CF566DE5713205C4B747F361f55` |
| Source explorer record | VERIFIED_EXACT_MATCH C03; không áp nhãn này cho C06 |

C04 manifest giữ relayer cũ là lịch sử; handoff12/11 ghi rotation tx `0x59f1076214a547795cdab875a364e2652959a43cd11ec254c9bbdee5e656c169`, funding tx `0xf778d9026a71b5819930e8181a50af12dba7be2931f2324b162ad92fd04ba5f7`. Không coi balance/nonce/role đọc12/11 là trạng thái hiện tại16/11. C03 proofs/C04/C05 phải giữ address/chain/ABI/receipt cũ; không ghi đè manifest hoặc journals.

## 2. C06 candidate / dry-run thực chạy

Source cuối hiện có: contracts/contracts/AidRegistry.sol, last change `6944fffd9858565f36c909ae9e5f83804afbe36f`, trùng origin/main sau chuẩn hóa CRLF/LF. Constructor **initialAdmin:address**, nonzero, không payable; DEFAULT_ADMIN_ROLE cấp admin, không tự cấp RELAYER_ROLE cho admin. SchemaVersion1. Không proxy/upgrade: thêm C06 không thể thay runtime C03 tại address cũ.

Artifact local ban đầu là **C06 phần1 stale**, thiếu confirmDelivery/flagDispute/resolveDispute và source build-input không khớp. Điều này giải thích hash mismatch đã ghi trong review PR30; không phải finding source bytecode lỗi. Không dùng artifact stale đó làm release.

Đã chạy offline, không signer/provider/RPC:

- `node node_modules/hardhat/dist/src/cli.js compile`: **PASS**, solc0.8.28, paris, optimizer enabled200; Hardhat tải compiler0.8.28/native-WASM theo toolchain hiện có. Một warning pure-mutability ở _resolveDisputeDigest:1208, không lỗi compile. Không cài project dependencies hay môi trường Java/DB.
- So source với main; chọn build input khớp source; so ABI + creation/runtime bytecode đúng compiler output: **PASS**. Build info `solc-0_8_28-6d69e2cf1ddbaa7f61c90daac742b8efd4cf518a.json`.
- ABI8 milestone functions +9 events (gồm DeliveryVoided), và full committed `docs/fixtures/c06/AidRegistry.c06.abi.json`: **PASS**. Mapping năm operations nhận từ [Task4 review](C10_FULL_CHAIN_FIXTURE_REVIEW_2026_11_16.md); không chạy lại unit/E2E.
- Ethers `ContractFactory(abi,bytecode)` **không signer/provider**, chỉ `getDeployTransaction(publicAdmin)`: **PASS** unsigned constructor encoding, value0. Không lấy nonce, estimate gas, sign hoặc gửi request. Đây là offline encoding dry-run, **không phải EVM execution/local deployment/gas simulation**.
- `node --check scripts/deploy.mjs` và `scripts/deployment.mjs`: **PASS syntax only**; không import/chạy CLI để tránh wallet-store preflight. Generated build/cache artifacts Git-ignored, không stage/commit.

| Fingerprint C06 candidate sau compile | Giá trị |
|---|---|
| Normalized source SHA256 (LF) | `c27379bba82dd88897a3fe2bf1b0f5c2ad77e7f29830afef4dde7380ed272153` |
| Artifact file SHA256 | `976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592` |
| ABI JSON SHA256 | `548b46168243852ef455752f09afa6fba7989bb1fcc0b13ac0f6a64f24175eae` |
| Creation Keccak256 | `0x9e8943c4e5dc82f7a0e9c60feaa6a2fc28d1b9c2b616aac80e20beccc143258c` |
| Runtime Keccak256 | `0x525314d10311e01e01a61a6f9e75ee432f59ca09ff3aa27445aed3962e09011e` |
| Runtime size |11693 bytes |

Artifact SHA/runtime khớp metadata Dũng C10 sau rebuild. Formal source/artifact freeze vẫn cần owner review; thay source/compiler/settings/dependencies phải recompute và review fingerprint, không đổi hash pin chỉ để bypass gate.

**Chưa biết/chưa tạo:** C06 deployed address, deployment/registration txHash, block/hash, gas thực tế, nonce thực tế, explorer verification ID/status. Không dự đoán address bằng nonce cũ hoặc điền C03 address vào cấu hình C06.

## 3. Runner/config readiness — không dùng nhầm C03

Hardhat config pin chain43113, compiler0.8.28/paris/200, Fuji accounts=[]; deployment dùng runner khác. validateExternalNetworkEnv chỉ chạy khi REQUIRE_FUJI_CONFIG=true và đòi key-env; Task8 không bật cờ này, đọc/đặt key hoặc chạy secret-dependent preflight.

`scripts/deployment.mjs:6–22` pin creation/runtime/ABI hashes **C03** và đọc committed C03 snapshot. `deploy.mjs` dùng journal `fuji-deployment.json` đã tồn tại, CLI có publicWallets/unlockWallets; execute còn tự deploy + register org và dùng public plan/relayer cũ. Không chạy script đó “dry-run”, không sửa pin C03, xóa journal hoặc chạy --broadcast. Không gọi preflight.mjs vì nó có phần RPC; Task8 tự kiểm offline ở trên.

**PENDING Khoa trước lệnh deploy được phép:** chuẩn bị/review runner hoặc cấu hình release riêng cho C06, snapshot full ABI/bytecode và hash pin mới, public plan/journals mới không ghi đè C03. Phân biệt deploy receipt, org registration/setup và worker cutover; setup chỉ làm sau khi có quyền tương ứng. Runner cần bounded fee/gas + no pending signer tx + chain guard và resume/reconcile journal, không resend deployment khi outcome unknown. Không dùng lại cap/gas estimate C03 làm estimate C06. Giữ signer/admin/relayer tách biệt; không lộ secrets/raw signed bytes. Chưa triển khai runner trong Task8.

## 4. Go/no-go và owner

| Gate | Trạng thái16/11 | Owner/bằng chứng cần trước bước thật |
|---|---|---|
| C06 source/ABI/compiler output consistency | PASS offline candidate | Khoa freeze/review fingerprint chính thức; contract review C06/D55 đã có |
| Full-chain design/fixture có reduction/multi commitments/receipts | NEEDS FIX | Dũng xử lý F1/F2 Task4 trước acceptance Task7 |
| C07/C08/C10 compatibility và proof writer | PENDING Task7 / Task6 riêng | Dũng cung cấp runtime SHA/results, proof context theo SQL guard; Khoa review6 khi được giao, không nới guard |
| Java parity, PG retry/fencing, Hardhat C06 full chain | PENDING | Dũng local gates đủ cases, không skip, reduction không bị bỏ; scope tests theo Task4/7 |
| Runner/manifest C06 riêng | PENDING | Khoa chuẩn bị bản reviewable riêng sau scope/approval thích hợp, giữ C03 runners/proofs |
| Admin/relayer rights + gas funds | HISTORICAL ONLY | Owner xác nhận public addresses/current chain/roles/balance/nonce qua read-only preflight lúc được phép; không cần gửi key cho reviewer |
| Deployment approval | NOT GRANTED trong lượt này | Người dùng/nhóm chốt artifact + plan + thời điểm; không suy approval từ compile/PASS tài liệu |
| Queue/cutover DB & config | NOT CHECKED / NOT AUTHORIZED | Dũng inventory current queue/attempts và pinning, then approval cutover riêng |

**READY FOR DEPLOY REVIEW** chỉ khi release artifact freeze, runner mới và bằng chứng Task6/7 đủ, plan/setup roles/gas được review. **ACTUALLY DEPLOYED** chỉ sau canonical receipt + runtime/admin readback và manifest mới, không phải khi có unsigned tx. Cutover vẫn có gate riêng ngay cả khi contract đã deploy thành công.

## 5. Deploy verification checklist — chỉ chuẩn bị

1. Ngay trước approval, xác nhận chain43113, source/compiler/input/output/ABI/dependency hashes; constructor public admin đúng, nonzero và khác relayer; public orgRef32bytes nonzero và binding DB cụ thể. Có thể reuse pilot orgRef trên registry mới nếu nhóm chọn, nhưng phải đăng ký lại: rights/state C03 không tự chuyển.
2. Sau approval, owner dùng runner C06 mới được review để deploy. Khi unknown outcome: dừng/reconcile cùng signed attempt/hash/address; không tạo tx/nonce mới để thử. Persist journal/recovery trước broadcast, không ghi đè C03.
3. Verify receipt status1, chain/from/contractAddress/constructor input/canonical block; getCode so full expected deployedBytecode/hash, ABI/view/schema/roles. DEFAULT_ADMIN_ROLE đúng public admin, admin không implicit relayer; không coi code tồn tại hoặc explorer link là verification thành công.
4. Org setup khi được phép: getOrganization đúng orgRef/assigned relayer/active; RELAYER_ROLE đúng, admin/relayer separation. Cùng orgRef/address trên C03 không thay thế kiểm này ở C06. Không gửi smoke business event lên case lịch sử.
5. Explorer exact-match source verification bằng đúng standard JSON/compiler/settings/constructor; lưu submission/result công khai và manifest C06 riêng. Không đánh VERIFIED trước kết quả thật. Receipt/log/getEventRecord/business proof là gate C10 riêng, không thay bởi explorer source match.

## 6. Cutover riêng — preserve C03 / abort / rollback

- **Dũng primary DB/config/worker; Khoa review deployment/ABI/event context.** Dừng scheduler/producers trong cửa sổ cutover đã chốt; không đổi toàn bộ BLOCKCHAIN_REGISTRY_ADDRESS chỉ vì C06 vừa deploy.
- Inventory **mọi** PENDING/PROCESSING/SUBMITTED jobs; PREPARED/SUBMITTED attempts (PREPARED là trạng thái attempt, không job), signed bytes/hash đã lưu, stale leases, receipts chưa reconcile, nonce journal/signer lock và cả job chưa có attempt/chưa pin registry. DB máy Dũng chưa được kiểm tra ở Task8. Không coi PENDING chưa ký là mặc nhiên có thể gửi sang C06.
- Pin C03 history theo attempt chain/registry/ABI/source/receipt và giữ immutable business/audit/jobs. Nếu job chưa pin registry, phân loại theo origin case/deployment và xử lý trước; không đổi job IDs/sequence/ref hoặc lấy global registry mới để đoán. Candidate code hiện chưa có routing đầy đủ per-deployment cho mọi pending job; cần explicit plan, không thêm cột/worker ở Task8.
- Ưu tiên D56: DB/demo mới hoặc tập case mới cô lập, C06 từ CASE_VERIFIED sequence1 rồi đủ pledge/reduction/receipt/delivery. Không replay case C03 vào C06 hoặc reset sequence C03. Prefix predecessor phải cùng chain/case/registry và đã confirmed; migration/replay case cũ cần decision riêng ngoài runbook này.
- Giữ AES key của signed attempts C03, DB/journals/manifest cũ và khả năng giải mã/reconcile. Một signer dùng nhiều DB không được chạy hai worker độc lập cùng lúc; DB lock chỉ serialize trong DB đó. Đối soát/drain mọi nghĩa vụ nonce across workers trước switching; không clear signer journal/lease để tạo transaction mới.
- **NO-GO/ABORT:** queue chưa drain/phân loại, signed attempt unknown outcome, signer nonce latest/pending bất nhất chưa giải thích, missing/wrong registry prefix, proof mismatch hoặc gates chưa pass → workers giữ tắt, không switch config. Phân biệt RPC pending do tx biết rõ với nonce không giải thích được, vẫn phải reconcile theo plan trước proceed.
- **Rollback trước khi C06 có nghĩa vụ mới:** có thể khôi phục config C03 sau kiểm quyền/DB/pinning và approval; contract C06 đã deploy không biến mất. **Sau khi đã ký/broadcast hoặc tạo C06 jobs:** dừng worker/producers, bảo toàn bytes/hash/ledger, reconcile C06 riêng; không redirect C06 jobs về C03 hoặc tạo signed replacement tùy ý. Nếu routing chưa hỗ trợ thì giữ tắt, không dùng rollback global config như giải pháp. Blockchain tx đã mine không đảo ngược; correction/void theo nghiệp vụ và approval riêng, không xóa proof.

## 7. Handoff / kết thúc

Khoa bàn giao candidate fingerprints + runbook; Dũng giữ Task6/7/runtime + queue/cutover; người dùng/nhóm quyết định deploy và cutover riêng. D15 formal approval đang pending theo Task5, không tự thay cardinality trong kế hoạch. Gates không đạt thì chưa deploy; fallback snapshot/offline cần nhóm chốt, không tuyên bố goods Fuji đã PASS.

Chỉ compile và offline/static checks nêu trên đã chạy; không local EVM deploy, gas simulation, Java/DB/CI, explorer/RPC readback mới, wallet/key access, transaction, fee/funding, worker/config/queue mutation. Source/runner/config không sửa; artifacts/cache generated ignored được giữ local. Commit chỉ runbook và backlog note; giữ khoa và mọi review local, không push/PR/merge. Chưa làm Task7 hoặc review6.

## Task4 readiness preparation — 17/11/2026 (chưa deploy)

**PREPARATION UPDATED / BLOCKED BY LOCAL GATE AND APPROVAL.** Main627be56, PR33/dungf85d390; Task3 review dd77cfb giữ NEEDS FIX: RPC chain guard CASE_VERIFIED chưa push, XML/provenance thiếu. Receipt7context source PASS và F1/F2 source addressed không thay local acceptance. Chưa triển khai runner C06, chưa freeze/approve release, không dùng source PASS thay tests.

Đọc lại artifact/source hash/full committed ABI: vẫn khớp fingerprints phần2 và source/config/scripts không đổi so dung. Đây chỉ là file/hash comparison, **không compile/dry-run/runtime mới**; kết quả compile/unsigned constructor ngày16/11 là evidence lịch sử đã nêu. Không có approval freeze mới. Deploy CLI hiện vẫn pin C03 snapshot và journal cũ; không dùng nó để gửi C06.

### Điều kiện chuyển sang READY FOR DEPLOY APPROVAL

| Điều kiện | Owner / tình trạng |
|---|---|
| Chốt Task3 local gate trên committed candidate | Dũng push guard/test + XML/provenance; Khoa review focused rồi mới PASS/PASS WITH NOTES nếu đủ; hiện BLOCKED |
| Freeze source/build input/output/ABI/artifact và hash release | Khoa/nhóm xác nhận candidate/final SHA, compiler0.8.28/paris/200, full C06 ABI; offline consistency PASS nhưng formal freeze PENDING |
| Runner/plan/journal C06 riêng reviewable | Khoa chuẩn bị khi được giao triển khai; pin hashes mới, constructor admin, chain43113, phí/gas caps, unknown-outcome resume/reconcile; bảo toàn runner/journal C03; hiện PENDING |
| Public deployment/setup plan | Public admin khác relayer, orgRef rõ; phải đăng ký org/roles lại trên C06 khi được phép; addresses hiện ghi lịch sử, không tự coi current rights/gas sẵn sàng |
| Preflight hiện hành trước xin deploy | Chain/code/roles/public balances/admin nonce pending-vs-latest, gas estimate/caps theo C06; không đọc key, không sign/broadcast; chưa chạy ở Task4 readiness này |
| C03 preservation + cutover/abort plan | Runbook phần6 đã có; Dũng xác nhận owner/window/routing+recovery; không tự di chuyển C03 jobs/proofs/refs/sequence |

Khi đủ các mục trên mới trình **READY FOR DEPLOY APPROVAL** cùng reviewed plan/fingerprints/evidence. Đây là trạng thái đủ để xin phê duyệt, không phải đã được phép deploy. Sau đó vẫn cần approval deployment/setup rõ ràng và kiểm lại plan không drift trước execution. C06 address/txHash/block/explorer status vẫn chưa có.

### Tách khỏi gate cutover / Fuji pilot

- Deployment contract mới không tự chuyển backend, jobs hoặc quyền/state C03; approval deploy không bao gồm cutover. Binding registry DB, inventory mọi jobs/attempts và nonce nghĩa vụ thật là **Dũng thực hiện/xác nhận trước cutover riêng**; kế hoạch đã chuẩn bị không phải inventory đã PASS.
- Trước cutover: kiểm PENDING/PROCESSING/SUBMITTED jobs, PREPARED/SUBMITTED/signed attempts, jobs chưa pin registry, quyền/current binding, C03 proof retention, AES/journal recovery và signer concurrency. Sai/unknown context → NO-GO, giữ worker tắt; không redirect signed bytes hoặc sequence sang deployment mới.
- Query worker PR33 chỉ enable goods trên31337; local PASS không tự bật goods43113. Cần Dũng bàn giao kế hoạch/implementation activation Fuji được review đúng deployment khi đến bước đó, không sửa allowlist/config trong readiness này. Đây không phải yêu cầu triển khai mới thuộc local review hoặc lệnh cutover.
- Task4 preparation không nghiệm thu Task5 DB/cutover, Task6 Fuji pilot hoặc Task8 audit17/11. Không đòi scanner/hosting để chốt local worker gate.

**Handoff:** nhận patch+committed-tree evidence của Dũng → Khoa chốt delta gateTask3 → hoàn thiện release runner/plan/freeze và preflight thuộc scope được phép → trình deployment approval riêng → deployment/readback nếu được phép → cutover approval/evidence riêng. Không dùng lịch hoặc compile PASS để bỏ qua bất kỳ bước nào.

## Task4 review PR34 — readiness hiện hành17/11

**BLOCKED để chuyển READY FOR DEPLOY APPROVAL.** Maina870577 (PR33 đã merge), PR34head406d5d8/tree59eb1487…, patch chain guard đã source PASS; Task3 còn INSUFFICIENT EVIDENCE vì XML/provenance trên final head chưa bàn giao. Không giữ nhận định patch chưa push cho PR34; các đoạn trước là lịch sử các lần fetch.

Delta PR34 chỉ worker/test, không đổi source C06/ABI/compiler/deploy config. Đọc artifact hiện có: SHA256976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592 vẫn đúng candidate phần2; không compile/runtime/preflight RPC mới. Artifact consistency không đồng nghĩa formal freeze/approval hoặc actual deployed address.

**Gates còn thiếu và owner:**
- Dũng: focused evidence receipt7context/T01 + chain-guard +8events/Hardhat/PostgreSQL đúng committed SHA/tree; gửi canonical proof và restart identity. Khoa chỉ review delta rồi mới chốt local gate.
- Khoa/nhóm: freeze release C06/input/output/ABI/hash và runner/config+journal C06 riêng được review; runner hiện tại vẫn pin C03 và journal lịch sử, không dùng để gửi C06.
- Owner triển khai/nhóm: public admin/relayer/orgRef và gas/nonce/read-only preflight hiện hành; dữ liệu role/balance cũ không phải xác nhận17/11. Không đọc key hoặc tự cấp approval.
- Dũng trước cutover: inventory PENDING/PROCESSING/SUBMITTED jobs, PREPARED/SUBMITTED/signed attempts, jobs chưa pin registry, nonce obligations và schema/binding; chưa có readback/inventory trong PR34 để nghiệm thu. Giữ C03 proofs/attempts/journals đúng registry, không chuyển unsigned jobs cũ bằng global address mới. Deployment approval và cutover approval riêng; cutover cần evidence mới ngay trước switching.
- Goods query hiện chỉ bật31337; local PASS không tự cho phép workerFuji43113. Dũng/nhóm chốt activation/scope/pinning khi đến bước được phép.

**Mở khóa công việc:** đủ thông tin để Dũng tiếp tục hoàn thiện Task2/local và chuẩn bị Task5 plan/inventory; **chưa đủ go-cutover hoặc Task6pilot**. Sau local gate đạt còn freeze/runner/preflight/deployment approval, deploy/readback thật rồi cutover approval riêng. Không có C06 address/txHash mới, không lập proof giả, không thực thi Task5/6 trong review này.

## Evidence delta trên406d5d8 — readiness cập nhật17/11

**Task4 vẫn BLOCKED để READY FOR DEPLOY APPROVAL.** Worker11/T013/HardhatC061 đều được Dũng báo PASS,0F/0E/0S trênhead406d5d8/tree59eb1487; **REPORTED PASS — EVIDENCE NOT INSPECTED**, chưa có XML/log truy cập được để Khoa chốt Task3. Không nói các tests chưa chạy, cũng không ghi independently verified. Không có source/artifact/config/registry mới cần review lại.

Combined PREPARED+wrong-chain không được matrix hiện hành ghi thành testcase local bắt buộc riêng; không thêm nó làm blocker local. Vẫn giữ targeted regression này **trước deploy/cutover**: không broadcast/resign/tạo attempt mới trên sai RPCchain, bảo toàn nonce/hash/bytes/attempt và recovery đúng-chain. Dũng có thể bổ sung đúng một focused test, không cần full suite.

Sau khi Task3 đủ artifacts để PASS/PASS WITH NOTES, vẫn phải freeze release/ABI/hash, runner/journal C06 riêng, current rights/gas/nonce preflight và deployment approval. Queue/signed attempts C03 được Dũng **báo mới kiểm kê read-only**, không đồng nghĩa drain/pinning đã nghiệm thu hoặc có quyền chuyển DB/worker. Dũng tiếp tục Task5plan/inventory và bàn giao bảng trạng thái/readback; **chưa go-cutover hoặc Task6Fuji pilot**. Deploy và cutover hai approval độc lập, không gộp local PASS vào quyền triển khai.

## Sau khi inspect attachment PR34 — 17/11

**Task3 local PASS WITH NOTES**, F1/F2 local CLOSED theo excerpt11Worker/3T01/1Hardhat,8confirmed/restartseq4nonce33 tại declared406d5d8/tree59eb1487. Xem gate review cho giới hạn provenance: đã đọc attachment, không phải full original XML hoặc independent RPC verification. Không tiếp tục ghi “không có evidence accessible” như current status.

**Task4 vẫn BLOCKED để READY FOR DEPLOY APPROVAL** do runner/journalC06riêng,formal freeze,current roles/gas/nonce preflight và combined PREPARED+wrong-chain regression chưa hoàn tất/được chứng minh. Local acceptance gate đã gỡ; deployment/cutover approvals vẫn riêng và chưa cấp.

Dũng có thể tiếp tục Task5 inventory/disposition plan. Comment báo2B05transactions đúngC03/noopenattempt nhưng còn3PENDINGjobs ởDBcũ: cần record job/registry ownership và phương án được duyệt (drain/park/reconcile theo deployment), không tự chuyển3jobs sangC06. Đây là author summary chưa đối chiếu raw DBreadback. Chưa chạy Task6Fuji trước actual deployment/readback + DB/worker cutover được phép; không dùng Hardhat address0x610178… làm registryFuji. Không deploy/cutover ở lượt evidence.

## Chốt acceptance Task4 — READY FOR HANDOFF TO TASK #5 (17/11)

**Phạm vi DONE của Khoa:** candidate/release identity, ABI/constructor/chain verification, offline preparation tool, C03/C06 separation, DB disposition proposal, ownership/approvals và go/no-go checklist đã đủ để Dũng làm phần Task5. Không giữ readiness handoff BLOCKED vì chưa có DB mutation/approval hoặc chưa deploy. **Task3 đã PASS WITH NOTES**, evidence excerpt đã inspected, không review/chạy lại.

### A. Candidate + công cụ offline đã hoàn thiện

- Source/ABI/artifact/compiler pins phần2 đã đối chiếu lại, vẫn maina870577/PR34head406d5d8; PR34 không sửa Solidity. Candidate fullC06/source6944fff và build6d69e2cf… khớp các hashes đã ghi. Constructor proposed public admin0x131DD6…601E, schema1, chain43113; relayer0x95a956…61f55 khác admin. Public addresses là proposed/historical binding chờ current preflight, không phải quyền hiện hành đã được verify lại.
- **Công cụ mới:** `node contracts/scripts/prepare-c06.mjs` từ repo root, hoặc `node scripts/prepare-c06.mjs` từ contracts. Tool chỉ đọc source/artifact/build-info/full committed ABI/public C03manifest; assert exact SHA/Keccak/compiler input-output,8functions/9events/constructor, encode unsigned constructor không signer/provider. Không đọc env/keystore, không tạo files, không sign/broadcast/RPC. Kết quả **OFFLINE_CANDIDATE_VERIFIED_NOT_APPROVED**.
- Kết quả thực chạy: helper PASS, node syntax PASS, negative invocation `--broadcast` bị reject trước mọi operation (OFFLINE_ONLY_NO_ARGUMENTS). Không compile hoặc chạy lại Task3/Java/DB/Hardhat. Deployment calldata Keccak256 constructor proposed `0xd39ed189b3ce2791278307261b137affd847dcba8af46bb3e8a66a2d51836373`.
- C06 public journal namespace dự kiến `contracts/deployment/c06-fuji-deployment.public.json`, encrypted recovery `contracts/deployment/c06-fuji-recovery.private.clixml`; **chỉ reserved names, chưa tạo journal**. Không dùng/ghi đè C03fuji-deployment.json hoặc recovery cũ. New helper là **verification/preparation tool, không phải broadcast executor**; `deployment.mjs/deploy.mjs` cũ vẫn C03 và không sửa.
- **Để freeze còn thiếu chính xác:** người có thẩm quyền xác nhận final source/release commit/tree (gồm worker406d5d8 nếu dùng), build input/dependencies/compiler/settings và fingerprints phần2, constructor/public org-relayer binding được chọn, approval record của candidate. Các hash đã kiểm đủ để review, không còn mystery artifact mismatch; chưa có quyết định freeze nên không gắn APPROVED.

### B. Phương án handoff DB — đề xuất, KHÔNG áp dụng

Nguồn inventory do người dùng chuyển xác nhận Dũng, không phải SQL do Khoa chạy: aidbridge_demo có2/2B05tx khớpC03,0openattempt,3PENDING chưa gắn registry; aidbridge_demo_v16 có5PENDINGseq1–5,0attempt; cutoverNOTRUN.

**Đề xuất mặc định cho3PENDINGcũ:** giữ nguyên tại DBcũ, giữ C03 lane và worker cũ tắt; chưa đổi status/ref/sequence/registry hoặc tạo attempt. Đây là trạng thái vận hành cô lập (park by disabling worker), không tự thêm statusPARKED hoặc markFAILED. Không dùng config C06 mới quét DBcũ. Bảo toàn2B05proof, C03manifest, AES/recovery/noncejournal và khả năng đọc API lịch sử.

Dũng lập **disposition ledger** cho từng job: job/audit/case/type/chain/sequence, nguồn tạo/lý do tồn tại, preparation/manifest/commitment có hay không, original deployment/ABI, predecessor cùngcase/registry, attemptcount/signedobligation. Nếu prefix/capabilityC03 hợp lệ và nhóm duyệt drain thì xử lý bằng đúng C03 binding; C03 không có goods functions nên job goods không được thử gửi C03. Nếu không phù hợp thì tiếp tục cô lập chờ disposition được duyệt; không rebind sang C06, xóa/gộp, resetseq hoặc copyproof để “drain”. Xử lý/migration/replay trường hợp cũ là quyết định riêng, không tự cho phép bởi Task4.

**5PENDINGstaging:** chỉ là inventory candidate, không suy chúng đã đủ5operations hoặc cùngcase/source/phù hợpC06 vì mới biết count/seq, chưa biết types/refs/preparation. Dũng kiểm case/org/publicrefs, exact types và predecessor; đủrequiredprefix córeduction, persistedschema/snapshots. **0attempt không chứng minh chưa pin deployment**: manifest_private/salt/evidence_commitment có thể đã chuẩn bị trước attempt; nếu domain gắn registry cũ, dừng/reconcile, không tùy tiện xóa preparation. Ưu tiên case/job mới sạch trên C06 sau approval, hoặc chỉ dùng staging jobs nếu chứng minh đúng domain/prefix và kế hoạch được duyệt. Không gửi CASE_VERIFIEDseq>1 hoặc resetstate của C03 để tận dụng staging.

**Điều kiện nghiệm thu Task5 thuộc Dũng:** inventory theo thời điểm/window có rawreadback tối thiểu khôngPII; staging5jobtypes/prefix/refs/preparation rõ;3legacyjob códisposition được duyệt; các workers/signer sessions cũ dừng; signed/noncejournal không có nghĩa vụ mơ hồ; schema/seed/current binding phù hợp và recovery/drain procedure. Các thao tác DB/config/seed/cutover chỉ được làm trong quyền riêng đã cấp cho Dũng, không xuất phát từ checklist Khoa.

### C. Ma trận owner / thời điểm — không gộp handoff, deployment và cutover

| Việc | Owner | Gate / hiện trạng |
|---|---|---|
| Readiness hồ sơ + offline preparation | Khoa | **DONE**, đủ bàn giao Task5, không approval execution |
| Local5operations/F1F2/receipt7context | Dũng/Khoa | Task3 **PASS WITH NOTES** theo inspectedexcerpts; không phải Fuji proof |
| Combined PREPARED+wrong-chain | Dũng | Một targeted regression trước deploy/cutover, không blockerlocal/handoff mới;0sign/newattempt/broadcast khi sai chain, giữ bytes/nonce/hash rồi recover đúngchain; chưa claimPASS |
|3C03pending disposition +5stagingjob qualification | Dũng; nhóm duyệt phương án | Có thể bắt đầu ngay bằng inventory/plan; actual mutation/routing còn cần quyền tương ứng |
| Release freeze / finalcandidate chọn | Nhóm/người phê duyệt; Khoa cung cấp pins | **PENDING DECISION**, không phải thiếu hash |
| C06 broadcast executor review | Khoa trước authorized execution | **PENDING execution tooling**: sau chọn release/plan, chuẩn bị/review entrypoint riêng với pins/journals ởA, fee/gas caps và unknownoutcome recovery. Không có broadcast path ở helper; không dùng C03executor. Đây là gate trước xin executionapproval, không giả executor đã sẵn sàng chỉ vì handoffDONE |
| Current read-only preflight | Owner deployment cùng Khoa | RPC43113, publicadmin/relayer/currentnonce/balance, gasestimate/capsC06, plan/setupauthority; chưa chạy, không đọc keys. Vai trò/gas cũ không đủ |
| Deployment/setup approval | Người dùng/nhóm | Chỉ khi candidate/tooling/gates đã reviewed; chưa cấp; không gồm cutover |
| Cutover approval +switch | Nhóm duyệt; Dũng vận hành | Task5readback/disposition đạt, registryC06 thật đã deploy+verify, activationgoods43113 đượcreview, gateprefix/signernonce đạt; approval riêng; chưa thực hiện |
| Fuji pilot Task6 | Dũng sau gates | Chỉ sau deploy/readback và cutover hợp lệ; chưa được mở bằng handoff này |

**Khi nào READY FOR DEPLOY APPROVAL?** Localgate accepted + combinedregression đạt + finalrelease freeze record + C06executiontooling được review + current preflight/gas plan/pins đúng + preservation/cutover plan đủ. Hiện helper và hồ sơreadiness đầy đủ, nhưng các gates trên còn PENDING; không tự tuyên bố trạng thái này.

**Khi nào go-cutover?** Sau approved deployment/readback, Task5 inventory/disposition/seed/pinning/recovery đạt và quyền cutover riêng được cấp. Unknownnonce/attempt, prefix sai deployment, domain preparation cũ hoặc chưa có phương án3legacyjobs → abort giữ worker tắt. Sau broadcast không rollback blockchain; giữ journal/bytes/proof và reconcile đúnglane, không redirect sangregistrykhác.

### D. Bàn giao dứt điểm

**Task4 READY FOR HANDOFF TO TASK #5**; không còn việc khảo sát/document/offline verifier nào cần Dũng chờ Khoa trong phạm vi readiness hiện tại. Dũng bắt đầu ngay inventory/disposition/stagingqualification và đề xuất DB/binding plan theoB. Các execution tooling/preflight/gates/approval được liệt kê hữu hạn theoowner để đi tới deployment, không coi là đã hoàn thành hoặc gộp trách nhiệm Task5 vào Task4. Không thay DB/worker/backendconfig/registry, không giữ Task4handoff BLOCKED vì approval chưa cấp.

## Gói deployment readiness hoàn chỉnh — phân công mới17/11

**READY FOR HANDOFF TO TASK #5; candidate kỹ thuật có thể review để freeze, CHƯA READY FOR DEPLOY APPROVAL.** Bốn đầu ra đã có: manifest thực sinh, runnerC06riêng, pre/post verification checklist, binding/sequence/regression criteria. Task3 PASS WITH NOTES giữ nguyên; không chạy lại Task3.

### 1. Artifact freeze candidate / reproducibility

- `contracts/deployment/c06-freeze-candidate.json`: sinh bằng entrypoint prepare từ actual source/compiler/build output/artifact. Chứa source commit6944fff/fileSHA(LF), compiler0.8.28+commit.7893614a, **toàn bộ compilationSettings** (paris, optimizer200, remappings, outputSelection), standard input SHA, hash từng build source/dependency, ABI/artifactSHA, creation/runtimeKeccak, constructor types/values và artifact path.
- ArtifactSHA `976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592`; runtimeKeccak `0x525314d10311e01e01a61a6f9e75ee432f59ca09ff3aa27445aed3962e09011e`. Không điền hashes thủ công trong manifest: prepare tính từ files và so compiler outputs. Helper prepare-c06.mjs cũ giữ quick offline pins check; **entrypoint gói mới là c06-deploy.mjs**.
- Rebuild: checkout source/dependencies đúng release, Hardhat compiler/settings/standardJSON như manifest; preserve **exact compiler input bytes/source line endings**, không chỉ normalized LF source. Chạy compile offline/toolchain đã có khi cần, rồi `prepare` ra file tạm và so mọi field với manifest. Input sourceSHA có thể khác normalized fileSHA do CRLF; không tự normalize build input hoặc sửa pin để “reproducible”. Build/artifact files vẫn ignored; khi formalfreeze phải lưu release bundle/standard input/artifact/hash ở nơi kiểm soát để reviewer/operator tái lập, không phụ thuộc cache mất đi.
- Điều kiện FROZEN: nhóm xác nhận candidate/source/release tree và constructor/public binding, lưu quyết định freeze; tạo bản manifest **được kiểm soát riêng** với statusFROZEN và approval-reference được xác nhận. Không sửa candidate thànhFROZEN trong lượt này. Broadcast cần approvalrecord riêng bind SHA256 của **đúng bytes manifestFROZEN** và planC06, không dùng approval của candidate/C03.

### 2. RunnerC06 / modes / recovery

Files: `scripts/c06-release.mjs` core; `scripts/c06-deploy.mjs` CLI; `deployment/c06-fuji-plan.public.json`; manifest ởtrên; `scripts/c06-readiness.test.mjs`. Tất cả paths dưới tương đối contracts/.

```text
node scripts/c06-deploy.mjs
node scripts/c06-deploy.mjs prepare
node scripts/c06-deploy.mjs preflight --manifest <reviewed-manifest-path>
node scripts/c06-deploy.mjs post-deploy --manifest <same-frozen-manifest-path>
```

Default/prepare **OFFLINE**, chỉ đọc/printcandidate, khôngwritefiles/RPC/envsecret. Nếu muốn sinh lại file candidate, operator capture stdout vào file riêng rồi diff, không overwrite releaseđã duyệt.

Preflight **read-onlyRPC** (NOTRUNtrênFuji trong lượt này): validatecandidate/fullartifact/config, RPCchain43113, deployer/relayerEOA, latestnonce=pendingnonce, predicted target chưa cócode, gasestimateC06+20%,gasprice+20% và capplan, balance đủcost; trả manifest/planSHA,nonce/address/estimate/relayerbalance. Plan gascap4M/pricecap25gwei là giới hạn dừng, **không phải estimate hoặc gas đã dùng**. Không đụng worker/config/DB, không tự đăng kýorg hoặc chuyểnRelayer.

**Future broadcast command — NOT RUN, chỉ sau approvals/gates:**

```text
node scripts/c06-deploy.mjs broadcast --manifest <frozen-manifest-path> --approval <approved-public-record-path> --execute-approved
```

- Localapproval kiểm **trước provider/secret access**: manifestFROZEN, statusAPPROVED/scopeC06_DEPLOY_ONLY/chain43113, exactmanifestSHA/planSHA, approvedBy, expiresAt, expectedNonce/predictedAddress; combinedPreparedWrongChainRegression=PASS và preservationPlanReviewed=true. Approvalfile là record do operator cung cấp, **không phải cơ chế xác minh danh tính nhóm**; nhóm review/quản lý provenance của file ngoàiCLI. Không có approval thật được tạo/commit trong lượt này.
- Template record cần các fields kể trên; values lấy từ reviewedpreflight, không dùng placeholder làmapproval. Mất/chỉnh config/manifest/nonce/target hết hạn → stop. Không hỗ trợ --force hoặc bypass.
- Chỉ nhánh approvedbroadcast mới đọc tên env `C06_DEPLOYER_PRIVATE_KEY` (freshsign) và `C06_RECOVERY_KEY` (32byte AESkey,64hex). **Không nạp/đọc giá trị envsecrets trong lượt này**, không tự sao chép DPAPI/wallet-store. Futureoperator chọn secretinjection được kiểm soát; không đặt key trên argv/repo/report.
- Chainguard trước signing và trước initialbroadcast/**rebroadcast**. Signed deployment kiểm chain,from,to=null,value0,nonce,target,gas/fee caps,exactcreation+constructor và txhash. Không gửi registration/funding/jobnghiệp vụ trong runner này.
- Publicjournal `deployment/c06-fuji-deployment.public.json`; AES256GCM recovery `deployment/c06-fuji-recovery.private.aes.json` riêng và Gitignored. AADbindmanifest/planSHA; signedbytesmãhóa, không log rawtx. Private+publicrecord tạo bằng flagwx **trước broadcast**. Partialjournal/orphan/unknown/mismatch → stop/manualreconcile, không xóa để retrynewnonce.
- Runner đọc plan một lần để parse và tính SHA-256. Lần ký mới và mỗi lần gửi đều kiểm tra lại approval ngay sau lần kiểm tra chain cuối.
- Resume dùng cùng manifest, plan, nonce và địa chỉ đã duyệt. Nếu receipt đã có, runner verify/readback. Nếu RPC đã thấy đúng tx hash, runner chờ receipt. Nếu RPC chưa thấy hash, runner chỉ gửi lại bytes đã lưu khi cả nonce `latest` và `pending` khớp journal và địa chỉ đích còn trống. Khi nonce lệch, RPC timeout hoặc còn file `.next`, giữ journal để reconcile trước lần resume tiếp theo.
- Errors CLI chỉ thông báo sanitized STOP, không dumpRPC/signer/crypto errors; operator cần kiểm journal/gates theo runbook. Không có worker/queue/clientconfig mutation ở bất kỳ mode.

### 3. Post-deploy / metadata / explorer (NOT RUN thật)

`post-deploy` read-only kiểm chain,receiptstatus1/hash/from/to/contractAddress/canonicalblock, originaltxchain/nonce/calldata constructor, **full runtimebytes/hash**, DEFAULT_ADMIN_ROLE đúngadmin và admin không implicitrelayer. Registry mới phải chưa registerorg: getOrganization(planorgRef) trống và relayer chưa cóROLE. Đây là initialstate của contract; không giả định quyềnC03 chuyểnqua. Runnerbroadcast cũng verify và lưu metadata tự động nếu approvedexecution thànhcông.

Public metadata: chainId,address,txHash/nonce/block/hash/gasUsed,admin/relayer/orgRef,constructor,buildID,manifest/planSHA/runtimehash; sourceVerification/organizationSetup/backendCutover=NOTRUN. Chạy postverifytrước orgsetup; nếu đã có orgsetup thì verifierinitialstate dừng để reconcile, không overwriteproof cũ.

Sau actualdeploy, explorerexactmatch: dùng đúng standardJSON/compilerlongversion/settings/constructor encodedaddress và deployedaddress thật; lưu sourceverificationrequest/result côngkhai ở namespaceC06. Runner không gửi requestexplorer hoặc tạo verdictVERIFIEDgiả. Orgregistration/roles cần approvalsetup riêng và readback orgRef/active/assignedrelayer/RELAYER_ROLE sau setup; **runnerchỉdeploy**, không tự register. Dũng chỉ lấy registry/binding từ manifest/readback đã kiểm, không dùng predictedaddress/Hardhataddress.

### 4. Gate/ownership — trạng thái thực tế

| Output/gate | Status | Owner / next action |
|---|---|---|
| Candidate manifest/source/build/ABI/constructor | PASS offline | Khoa đã sinh và đối chiếu; nhóm chốtformalfreeze |
| RunnerC06riêng / journals recovery / approvalguards | PASS offline tests | Khoa đã viết; execution/network behavior chưa live-run, reviewrelease trướcapproval |
| Offline validation | PASS7tests,0F/0E/0S | manifestreproducible,approvalbinding/expiry/gates,wrongchain/pendingnonce/occupiedtarget,AEStamper/AAD,postcanonicalnegative,CLIdefault/noapproval; mocks, **khôngsigner/RPC thật** |
| PreflightFuji/fees/currentrights | NOT RUN | Khoa/operator ở bước được phép; xác nhận proposedaddresses/caps/balance/currentchain/nonce, không dùng sốlịch sử |
| PREPARED+wrong-chain worker regression | PENDING evidenceDũng | wrongchain0sign/newattempt/broadcast, byte/nonce/hash/attemptunchanged; restorechainrecover samebytes/no duplicate; localgate không bị mởlại |
| ArtifactFROZEN +deploymentapproval | PENDING nhóm/operator | Táchfreeze/deployrecord khỏi compile/sourcePASS; candidate vẫn FREEZECANDIDATE |
| C03/staging inventory/disposition +cutoverplan | PENDING Task5Dũng | Theo phầnChốtacceptanceB:3legacyPENDINGgiữC03lane,5stagingqualifytypes/refs/preparation; không tự rebind/delete |
| Actualdeploy/postreadback/explorer/orgsetup | NOT RUN | Chỉ sauapproval; không address/hash/blockC06thật ở gói này |
| Cutover / Fuji pilot | NOT RUN | Sauverifieddeployment+setup,Task5plan/readback+approvedcutover; không phải output readiness đã chạy |

**Task4 gói readiness hoàn thành thực chất**, Dũng dùng ngay manifest/candidatepins, entrypoint/runbook, DBbinding/sequence và regressioncriteria để hoàn thiệnTask5. Candidateplan và recordFROZEN/approval chưa được nhóm chốt không làm mất giá trị handoff, nhưng vẫn chặn actualbroadcast. Không sửaworker, DB, queue, signerstate hoặc tạoFuji evidence. KhôngreviewTask3/Task6/Task8 lại.


## Current morning status — 18/11/2026

Task4 remains **READY FOR HANDOFF TO TASK5**; release **FREEZE CANDIDATE**, not FROZEN or READY FOR DEPLOY APPROVAL. [Morning status and pilot proposal](C06_MORNING_STATUS_2026_11_18.md) is the current summary and supersedes older runner/evidence counts above.

- Candidate release SHA `58a3ebb5d29e85e6e0ab81ca2b73b806c8b5235d` includes main `fbf8c3e`, remote runner safety fixes `5267e99` and the public exact-byte build archive. Existing compiler rebuilt ABI/creation/runtime identically offline; runner validation **11/11 PASS**, no real RPC/signing. Formal freeze still requires group decision.
- Dung has now committed the combined PREPARED+wrong-chain regression at `5df5aac`; assertions reviewed for this gate only. Task5 reports 34/0/0/0 WorkerIT and an XML hash, but original XML has not been inspected. This is no longer missing test implementation; request that evidence, not a rerun. Task3 local PASS WITH NOTES is unchanged.
- Proposed pilot: existing staging org `0x26b3…299f`, case `85e124e4…`, CASE_VERIFIED only; **not approved**. Current candidate plan instead pins historical org `0x2395…0a46`. Org choice must be resolved and any plan change re-hashed/revalidated before freeze. Staging seq5 DELIVERY_CONFIRMED unsupported; goods are local31337-only, so staging five jobs are not full-chain Fuji readiness.
- Three legacy unprepared PENDING jobs stay parked unchanged, with C03 worker off and historical proofs retained. One relayer must not run concurrently across DBs; do not start the latest backend on legacy V15 without a separate migration decision.
- Current preflight/rights/gas, deployment approval, actual deploy/readback/explorer/orgsetup, cutover approval and Fuji pilot remain **NOT RUN/PENDING** as appropriate. Deployment and cutover are separate permissions. The linked status has the precise owner/actions and D49 post-pilot API check.
