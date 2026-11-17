# Task #8 — C06 redeploy preparation / offline dry-run, 16/11/2026

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
