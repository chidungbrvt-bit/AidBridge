# Task #3 — C06 local worker/verifier gate + receipt context review, 17/11/2026

**LOCAL C10 GATE: NEEDS FIX.** Một finding Medium có thật trên PR head: CASE_VERIFIED thiếu RPC chain guard trước ký/rebroadcast. Đồng thời **INSUFFICIENT EVIDENCE** cho runtime acceptance trên đúng committed SHA. F1/F2 đã addressed ở source/design, **OPEN ở cấp nghiệm thu runtime**, không còn mô tả là thiếu implementation. Không merge/deploy/cutover trong review này.

## Evidence attachment inspected — kết luận cuối local17/11

**TASK3 LOCAL GATE: PASS WITH NOTES. F1/F2 CLOSED cho acceptance local; R1 source CLOSED, receipt7context local regression accepted theo evidence bàn giao.** Các kết luận thiếu evidence phía dưới là lịch sử trước attachment. Không suy thành Fuji PASS hoặc deploy approval.

Nguồn: [comment Dũng](https://github.com/chidungbrvt-bit/AidBridge/pull/34#issuecomment-6952865519), posted17/11/2026 15:41:11UTC (22:41:11+07); [AidBridge-PR34-406d5d8-evidence.txt](https://github.com/user-attachments/files/34979086/AidBridge-PR34-406d5d8-evidence.txt). Reviewer đã mở/đọc đủ file7945bytes; SHA256 `a71446717e91b791088465c6cf37c30e1e62b4e4f2adc914a9bc915c7032a8af`.

**Đã tự đối chiếu trong file:**
- Header commit406d5d8befda40eca00549635d0d77cc7cd1359e/tree59eb1487fa9154568210444a035d5aa088e03d81 khớp Git object đã fetch. Environment được khai báo Java26.0.1, PostgreSQL test cô lập, Hardhat31337; có reproduction commands targeted, không chạy lại.
- WorkerIT tests11/F0/E0/S0, đúng11testcase entries gồm fresh wrong-chain, receipt7context/T01, predecessor same deployment, original pledge, canonical poll, recovery/rebroadcast và4parameterized wrong-proof cases. Report time khai báo21:24:43+07.
- AuditTrailApiIT tests3/F0/E0/S0, đúng3names everyChainStateIsReportedHonestly/onlyMembersOfTheOwningOrganizationCanRead/pagesAreStableAndBounded; report time21:24:27+07.
- HardhatC06 tests1/F0/E0/S0, testcase c06ConfirmsMultipleCommitmentsAndPartialReceiptsInPersistedOrder; report time21:26:02+07. BuildID6d69e2cf…/artifactSHA976825c3… khớp candidate đã review.
- C06_E2E có8rows sequence1–8: verify,pledgeA,pledgeB,reduction,3receipts,delivery; registry duy nhất0x610178da211fef7d417bc0e6fed39f05609ad788, blocks43–50/logIndex0;8eventIds/txHashes riêng; job/attemptCONFIRMED từngrow. Registry này **Hardhat31337**, không phải địa chỉ FujiC06.
- Restart eventId/txHash khớp chính row reductionseq4,nonce33,signedBytesReused=true; cuối log có confirmedJobs8/oneattemptperjob/restart-stockPASS. Không ghép nonce33 với các lượt25/11/3 trước. Đây khớp assertions harness đã source-review, không review source lại ở lượt evidence.

**Mức chứng minh:** file là bản trích testsuite opening tags/testcase tags và system-out, không full XML document (thiếu closing/body đầy đủ), không có original XML hash hoặc clean-working-tree log. Commit/tree/env/report times là provenance Dũng khai báo, không phải attestation độc lập. Reviewer kiểm được counters/names/ledger/restart consistency và đối chiếu chúng với harness/acceptance đã review; không xác thực máy chạy hoặc tự quan sát canonical RPC/logs/raw refs. Raw blockHash/topics/data/receiptRef/commitmentRef không được xuất trong excerpt, kết luận refs/canonicality/stock dựa vào test được báo PASS với assertions đã review. Không mô tả parse-full-XML PASS hoặc independently executed/cryptographically attested runtime. Giữ nguyên attachment/original XML nếu cần audit sâu về sau, không yêu cầu rerun chỉ vì đây là excerpt.

Đủ chốt **local acceptance có notes**: đúng candidate được khai báo, testcase bắt buộc/counters/ledger phù hợp và không mâu thuẫn evidence; thiếu full XML được ghi là giới hạn provenance, không giữ missing-evidence blocker cũ. Combined PREPARED+wrong-chain vẫn chưa được chứng minh; không phải testcase local riêng trong matrix đã xem, nhưng giữ targeted regression gate trước deploy/cutover. Không bỏ qua nguy cơ rebroadcast sai chain.

**Handoff:** Dũng có thể dùng local PASS WITH NOTES để tiến hành chuẩn bị/đối soát Task5 và hoàn thiện các deploy gates. Chưa go-cutover/Task6Fuji: runnerC06riêng,formal freeze,currentpreflight,combined regression,approvaldeploy/readback và approvalcutover/queue pinning vẫn cần. Comment mới chỉ báo DBread-only inventory,2B05txkhớpC03/noopenattempt/3PENDINGjobs: đây là summary vận hành của Dũng, không phải DBreadback Khoa đã kiểm;3jobs vẫn cần disposition được duyệt, không tự đổi registry.

## Evidence delta mới trên cùng PR34 — kết luận cập nhật17/11

**Task3: INSUFFICIENT EVIDENCE cho reviewer acceptance; Dũng REPORTED PASS — EVIDENCE NOT INSPECTED.** Không có source blocker mới; R1 source CLOSED. Không giữ yêu cầu chạy lại full suite và không đánh các kết quả mới là NOT RUN: đã được Dũng báo chạy, nhưng reviewer chưa có artifacts để xác minh.

Fetch lại: PR34 head406d5d8/tree59eb1487fa9154568210444a035d5aa088e03d81 vẫn đúng, maina870577 không đổi. PR34/35 OPEN; comments vẫn rỗng; PR34 description chưa cập nhật counters mới. Tìm XML CaseVerified/AuditTrail/Failsafe trong repo và workspace không có kết quả, chưa có path/download artifact cụ thể. **Nguồn evidence mới là tin nhắn Dũng do người dùng chuyển tiếp**, không phải XML được Khoa đọc.

| Bộ test được báo trên406d5d8 | Counters Dũng báo | Mức kiểm chứng |
|---|---|---|
| Worker IT mục tiêu |11/11 PASS,0F/0E/0S | REPORTED PASS — EVIDENCE NOT INSPECTED; chưa có tên11cases/timestamps/XML để bind tree |
| T01 |3/3 PASS,0F/0E/0S | REPORTED PASS — EVIDENCE NOT INSPECTED; không coi report16/11 có sẵn là report mới này |
| Hardhat C068events |1/1 PASS,0F/0E/0S | REPORTED PASS — EVIDENCE NOT INSPECTED; expected source method c06ConfirmsMultipleCommitmentsAndPartialReceiptsInPersistedOrder, không suy actual testcase name từ summary |

Dũng báo8jobs/attemptsCONFIRMED, reductionseq4 restart giữ attempt/nonce25/txHash/bytes, fresh wrong-chain đã chạy và PREPARED restart đúng-chain đã chạy. Đây là observations mới thuộc lượt được báo; không ghép nonce25 với nonce3/11/2 của các lượt cũ. Source assertions tương ứng đã đọc ở review trước; chưa xem8rawlogs/canonical block và XMLsystem-out để independently verify runtime.

**PREPARED + wrong-chain:** không tìm thấy matrix/baseline hiện hành quy định tổ hợp này là một testcase riêng bắt buộc local gate; matrix yêu cầu chain guard và retry/restart safety, source406d5d8 đặt cùng guard trước nhánh sign/rebroadcast (CaseVerifiedWorker:98–110). Fresh wrong-chain và correct-chain recovery có coverage source riêng. Vì vậy **không dùng thiếu combined-case để giữ local gate OPEN**; giữ đây là **pre-deployment/cutover regression gate riêng**, owner Dũng. Một targeted test: persisted PREPARED → đổi RPCchain →0broadcast/0newattempt/0resign, giữ nonce/hash/ciphertext; chain phục hồi thì gửi lại đúng bytes/attempt. Chưa chứng minh tổ hợp, không cho phép rebroadcast signed transaction sai chain. Không cần mở full QA.

**Để chốt local gate trên evidence có thể kiểm tra:** gửi XML/log nguyên bản hoặc link/path truy cập được của11worker/3T01/1Hardhat cùng command/env/timestamps và final SHA/tree/clean-state (hoặc chứng minh content identity), system-out8events/restart nonce25. Reviewer chỉ kiểm counters/test names/provenance và assertions/proof delta; nếu artifacts khớp không cần rerun toàn bộ. Tới lúc đó F1/F2 source accepted, runtime acceptance vẫn OPEN do evidence inaccessible, không còn thiếu implementation.

## Review delta PR #34 — kết luận hiện hành17/11

**Task #3: INSUFFICIENT EVIDENCE. R1 CLOSED ở source; F1/F2 source addressed, runtime acceptance vẫn OPEN.** Nội dung NEEDS FIX bên dưới là snapshot PR33 trước patch, không phải finding source còn tồn tại ở PR34.

- Main `a87057783e971f610457bafa78a82ce99de5249a` đã merge PR33; PR34 OPEN, basea870577, head `406d5d8befda40eca00549635d0d77cc7cd1359e`, tree `59eb1487fa9154568210444a035d5aa088e03d81`. PR34 chỉ đổi CaseVerifiedWorker.java và CaseVerifiedWorkerIT.java, không thay contracts/ABI/audit guard.
- Delta worker:95–104 bỏ nhánh loại trừ CASE_VERIFIED, RPC chain guard chạy trước chọn ký mới/PREPARED recovery. Sai chain hoặc RPC exception schedule retry trước sign/broadcast. Persisted attempt chain/signer/registry/hash checks giữ nguyên; retry không tạo refs hoặc attempt mới. Không phát hiện lỗi source mới trong delta.
- Regression mới CaseVerifiedWorkerIT.doesNotPrepareCaseVerificationWhileRpcReportsWrongChain:1070 trở đi assert0attempt/0broadcast khi sai chain, tiếp tục1attempt khi chain đúng, logical eventId không đổi. Đây là coverage fresh signing; evidence PREPARED recovery wrong-chain vẫn cần đối chiếu trong regression/restart results, không suy test fresh đã chứng minh cả recovery.
- Bảy trường proof source và SQL guard không đổi so review trước; source PASS. F1/F2/8event harness đã ở main qua PR33, không review lại toàn bộ. Không coi mergePR33 là runtime acceptance.
- **Evidence mới:** PR34 description tự ghi XML hiện có chưa chứa regression mới, T01 report từ16/11, receipt-proof/8event chưa xác nhận trên final SHA. Không comment/XML mới trong PR; repo không có tracked test XML, tìm target/backend và D:/Codex vẫn không có XML mục tiêu. Không có raw XML/test log để reviewer xác minh counts hoặc canonical observations8events. Các số3tests0F/0E/0S trước đó vẫn là người dùng chuyển lời Dũng về dirty tree; không gắn chúng vào406d5d8.
- Dũng chỉ cần bàn giao focused XML+system-out cho receipt7context/T01, newchain guard và Hardhat8events với final SHA/tree + command/env/artifact. Chứng minh predecessor/original pledge cùng deployment; canonical receipt/log; restart giữ attempt/nonce/hash/bytes; số failures/errors/skipped0 cho các ca bắt buộc. Có thể chứng minh dirty-tree content identity bằng hashes đầy đủ nếu áp dụng, hoặc focused rerun trên commit. Không yêu cầu full repository rerun.

**Bàn giao sử dụng ngay:** Dũng có thể lấy source review R1/F1/F2 và checklist evidence để chốt Task2/local; chuẩn bị DB/queue inventory và plan Task5. **Chưa được dùng làm go-cutover hoặc Task6 Fuji pilot:** Task3 chưa PASS, Task4 chưa ready, chưa có deployment/queue approval/readback. Khoa không merge PR34 hoặc bật worker.

## 1. Phiên bản / provenance

Fetch main `627be561bc224286a3dc62d5d14898dc1b438141`, dung và PR33 head `f85d3902b7c2549987c78cd4438c6366a752d23a`; khoa `67cad7a` trước review, không merge nhánh Dũng/main vào khoa. PR33 OPEN, base627be56. Receipt fix `b1a59a63f2e3ba2cd00dcfdf8556fae580ec6d8c`, worker C06 `993ef8d605aba05da6b47830d089ef81440734f6`, DB handoff93bd88a, mergef85d390. Không có commit mới chứa chain-check fix/test được nhắc trong tin nhắn cuối. Không suy working tree ở máy Dũng là nội dung PR head.

Đã đọc diff/source repository/manifest/encoder/worker/verifier và assertions unit/IT/E2E trên origin/dung; tái dùng review16/11. Không đánh giá DB demo Task11 hoặc scanner/hosting. Không chạy Java/DB/CI/Hardhat/RPC mới.

**Evidence truy cập được:** PR description và committed C10 design vẫn ghi ca8mốc Not Run; báo cáo committed có bảng5mốc/registry/hash trích từ lượt cũ. PR33 chưa có comment đính XML. Không có XML Surefire/Failsafe tracked trên remote; repo local không có backend target, tìm TEST-*CaseVerified*.xml/C10 XML/failsafe-summary trong D:/Codex cũng không có. Không truy cập filesystem máy Dũng. Vì vậy không đọc được XML/test timestamp/system-out thực tế và không tự chứng thực counts/nonce/log values do Dũng báo.

| Evidence Dũng cung cấp | Kết quả được báo | Ràng buộc phiên bản / trạng thái review |
|---|---|---|
| Verifier và worker IT trước mở rộng |11unit +3IT,0F/0E/0S | Báo cáo mô tả patch trước multi-commitment; assertions có source nhưng không có XML/hash file để bind head |
| Hardhat C03 + C06 năm mốc |2tests,0F/0E/0S; reduction seq3 nonce2 | Committed doc: nền b1a59a6 + patch Task7, không nhận là run f85d390 |
| Tin nhắn proof regression và ca8events | mỗi ca1test,0F/0E/0S; 8jobs/attempts confirmed; restart seq4 nonce3 | Dũng ghi f85d390 nhưng XML không có để đối chiếu; chưa independently verified |
| Tin nhắn mới nhất, CaseVerifiedHardhatE2EIT |3tests,0F/0E/0S;8events; restart reduction nonce11 | Nền f85d390 **cộng worker/test chưa commit**, không phải bằng chứng PR head; fix mới chưa push |

Nonce3 và11 có thể thuộc các lượt Hardhat khác nhau, không tự coi là sai test; cần report nguyên bản và run/SHA tương ứng, không ghép observations các lượt. XML đếm tests không tự chứng minh code SHA nếu thiếu provenance.

## 2. Receipt proof context — Task6 cũ

**PASS source review, runtime regression chưa nghiệm thu.** CaseVerifiedWorker.java:462–485 chỉ tạo proof trong nhánh verifier Confirmed. Bảy field lấy từ expected sau khi khớp chain observation, không tự coi DB là proof:

| Field | Ghi từ đâu / verifier kiểm gì |
|---|---|
| contractAddress | pinned attempt registry lowercase; receipt.to và log.address phải khớp |
| eventId | job.logical_event_id bytes32 hex; log topic3 phải khớp |
| chainSequence | envelope integer; ABI data word0 khớp |
| eventType | operation enum; topic0 của đúng operation, không nhận event cùng envelope khác signature |
| schemaVersion | job/envelope integer; ABI uint16 word2 khớp |
| orgRef / caseRef | scoped source public refs; log topic1/2 khớp |

Verifier kiểm txHash/from/to, canonical block hash, status0/1, đúng một supported event/4topics/data length/schema/sequence/commitment/operation refs; reject removed/missing/duplicate hoặc logIndex thiếu/âm. Worker poll kiểm RPC chain và attempt chain/registry/signer binding, lưu blockHash/blockNumber/logIndex từ verdict cùng transactionHash đã đối chiếu, status1. Atomically cập nhật attempt/job; không đủ proof không CONFIRMED. Status0 chỉ revert sau context đúng. Đây là trusted RPC receipt verification, không phải independent consensus proof hoặc scanner.

Diff SQL AuditEventRepository và AuditChainStatusMapper so main rỗng: **không nới receiptVerified guard**. CaseVerifiedWorkerIT.confirmsOnlyAfterTheExactReceiptAndStoresTheBlock:187–244 assert7fields/types và T01 CONFIRMED_ON_CHAIN; negative mismatch/malformed/chain polling tests còn. Đủ căn cứ source cho fix b1a59a6; cần XML của regression này trên committed candidate để chốt runtime. Không lấy HTTP fixtures thay runtime.

## 3. F1 — reduction và safety

- AidRegistryAbi.java:60–99 có COMMITMENT_REDUCED → recordCommitmentReduction → CommitmentReduced, one commitmentRef,160bytes data. Mapped5functions phù hợp full C06 ABI. Loader join contribution/campaign/case/org/audit actor/key; manifest kiểm payload/schema/positive scoped lines, giữ original commitmentRef; receipt refs không đảo thứ tự. hasConfirmedOriginalPledge buộc pledge gốc cùng case/chain/registry, không chỉ kiểm N−1 (ngay trước reduction có thể là pledgeB).
- BlockchainJobRepository.java:24–55 kiểm predecessor tồn tại,CONFIRMED, cùng case/chain/registry qua confirmed attempt; giữ older-unconfirmed guard. CaseVerifiedWorker claimGoods bỏ tái kiểm predecessor khi có PREPARED attempt để recover nghĩa vụ đã ký, vẫn kiểm preparation context và persisted attempt binding trước broadcast. C07 lock/fencing/hash/encrypted bytes persistence còn nguyên; restart không tạo attempt mới theo source/test assertions.
- Claim/poll goods chỉ enable chain31337 trong query: cố ý giới hạn local, **không chứng minh goods đã bật trên Fuji43113**. Không tự đổi allowlist/cấu hình trong review.

### R1 · Medium · Chain guard bỏ CASE_VERIFIED trước ký/rebroadcast — Dũng sửa

**CaseVerifiedWorker.java:98–106** bọc readChainId bằng `operation != CASE_VERIFIED`; đường sign:119–132 và broadcast:149–161 của CASE_VERIFIED không kiểm RPC chain hiện tại. Kiểm persisted attempt chain với properties không chứng minh RPC endpoint còn ở cùng chain. Startup/poll guard không bao phủ đường ký/gửi này.

Tái hiện: process CASE_VERIFIED mới hoặc recover PREPARED, RPC endpoint đổi sang chain khác sau startup. Worker vẫn lấy nonce/gas, ký hoặc gửi bytes trước khi poll phát hiện; có thể reservation nonce từ sai node, gửi sai endpoint/unknown outcome và giữ job pending sai context. EIP155 có thể khiến node khác reject nhưng không thay thế fail-before-sign/send requirement.

**Sửa tối thiểu:** áp guard RPC chain cho mọi supported operation trước first sign và PREPARED rebroadcast; wrong chain/RPC error không ký/gửi, không tạo attempt mới, giữ recoverable pending. Bổ sung tests CASE_VERIFIED fresh +PREPARED recovery và kiểm counts/bytes/nonce; Dũng đã nói có patch nhưng chưa commit/push. Không tự sửa code trong review này.

## 4. F2 — kịch bản8events / assertions thực tế

Fixture remote đã có verify1 → pledgeA2 → pledgeB3 → reductionA4 → receiptA1=5 → receiptB1=6 → receiptA2=7 → delivery8. CaseVerifiedHardhatE2EIT.c06ConfirmsMultipleCommitmentsAndPartialReceiptsInPersistedOrder:184 và runC06GoodsScenario:188–269 dùng HTTP thật/schema test/Hardhat31337. Hai pledges khác commitmentRef; ba receiptRefs distinct, mỗi receipt dùng đúng contributionRef qua receive helper; delivered nhiều items,8audit/jobs, mỗijob1attempt, không có job mới sau queue empty.

Assertions stock Rice pledged8/cancelled2/received5/delivered4, Water5/1/3/2 và stock trước/sau worker giữ nguyên; available Rice1/Water1. Không có operation confirmation trong scenario, không coi đây là test D02/confirmed lifecycle; stock helper không assert riêng confirmedQty.

Restart tại reduction ở harness:405–445 lưu PREPARED rồi interrupt trước broadcast, expire lease, worker mới gửi cùng bytes; assert cùng attemptID/eventId/hash/nonce/ciphertext, count1. Không hardcode nonce3 hoặc11. assertMilestone:458–551 so job/attempt chain/registry/signer/status, canonical block, txinput function selector/ABI refs, exact topics/data/logHash, persisted7fields/logIndex và getEventRecord operation/digest; getCaseSummary cuối sequence8. Source coverage phù hợp F2, **chưa thấy XML execution của assertions này**.

Matrix wrong chain/registry/ref/duplicate/malformed/predecessor và11verifier/3worker tests đã đọc; không suy counts được báo là đã chạy trên PR head. D55 VOID không thuộc5operations worker này; hai logs/one job/sequence vẫn invariant cho verifier tương lai, không đòi triển khai VOID/scanner để đạt local gate.

## 5. ABI/artifact và quyết định gate

Đọc artifact local đã compile Task8: SHA256 `976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592`, build ID6d69e2cf… khớp report Dũng và full C06 signatures; không compile/test lại. Contract source/ABI không nằm trong diff PR33. Java enum/refs/data length mapping5operations đúng theo source review; chưa tuyên bố Java parity executable PASS mới.

| Hạng mục | Acceptance |
|---|---|
| Receipt7context fix | PASS source; runtime/T01 regression PENDING XML+SHA |
| F1 | Source/design đã addressed; OPEN nghiệm thu do R1 và thiếu evidence gắn committed candidate |
| F2 | Fixture/harness đã addressed; OPEN nghiệm thu runtime8events/provenance |
| Local gate | NEEDS FIX R1 + evidence chưa đủ; chưa được chốt PASS |
| Fuji/cutover | NOT RUN / NOT AUTHORIZED, không thuộc nghiệm thu lượt này |

**Dũng cần bổ sung có giới hạn:** commit/push patch chain guard và regression; bàn giao final head SHA + working-tree clean (hoặc chứng minh chính xác content hashes), command/env/build ID và XML Failsafe `TEST-com.aidbridge.casefile.CaseVerifiedHardhatE2EIT.xml` (3tests0F/0E/0S nếu chạy cả3ca), cùng XML proof/T01 regression và unit/IT target đã báo. Report/system-out nên giữ C06_E2E8records +restart/artifact để đối chiếu eventId/registry/txHash/block/logIndex, không có secrets. Chỉ chạy focused suites bị patch ảnh hưởng và nối evidence đã có; không yêu cầu full repo rerun. XML do working tree chưa commit không tự bind được source mới chỉ bằng cùng SHA nền.

**Task4 readiness:** có thể đọc checklist chuẩn bị sẵn nhưng **chưa đủ điều kiện chốt readiness/go-deploy**; không thực hiện Task4 trong lượt này. Sau source+evidence gate PASS vẫn phải freeze/runner/preflight và approval deploy/cutover riêng. Không chuyển sang Fuji pilot hay audit Task8.

Task3 ngày17/11 hoàn tất review tối đa trên remote hiện có; không chờ vô hạn và không triển khai thay Dũng. Commit chỉ tài liệu local trên khoa; giữ mọi review/handoff trước, không push/PR/merge/runtime/blockchain.

## Task3 handoff + Task4 readiness preparation — cập nhật17/11

Fetch lại sau dd77cfb: main627be56, dung/PR33 vẫn f85d390; PR OPEN, không comment mới/XML tracked, không commit chain-guard/regression mới. **Giữ NEEDS FIX R1 và insufficient evidence; F1/F2 acceptance OPEN.** Không review lại implementation hoặc chạy tests thay Dũng. Review receipt7context vẫn PASS source, regression runtime chưa nghiệm thu.

**Bộ bàn giao tối thiểu Dũng cần gửi để chốt lại ngay:**
1. Commit/push RPC chain guard cho CASE_VERIFIED fresh sign và PREPARED rebroadcast, kèm regression; gửi final head SHA + Git tree và trạng thái working tree. Không chỉ gửi SHA nền nếu có patch chưa commit.
2. XML của `CaseVerifiedWorkerIT.confirmsOnlyAfterTheExactReceiptAndStoresTheBlock`/receipt-context regression (7fields và T01), cùng regression chain guard mới; tên test, tests/failures/errors/skipped và lệnh thực chạy. Bổ sung unit/worker XML đã báo nếu dùng chúng làm acceptance evidence, không bắt chạy full suite lại.
3. Failsafe XML CaseVerifiedHardhatE2EIT có ca `c06ConfirmsMultipleCommitmentsAndPartialReceiptsInPersistedOrder`, expected8jobs/attemptsCONFIRMED; nếu chạy toàn class thì3tests,0F/0E/0S. Giữ system-out/artifact/buildID và C06_E2E8records; không gửi secrets/raw signed bytes.
4. Evidence scoped predecessor/original pledge cùng registry, receipt/log canonical, registry/eventId/sequence/txHash/block/logIndex, restart reduction giữ attemptID/nonce/hash/bytes. XML/log phải bind đúng committed tree; test từ uncommitted tree cần commit rồi chứng minh content identity (tree/file hashes) hoặc focused rerun trên commit, không tự coi commit mới đã được test chỉ vì báo cùng SHA nền.

Đối chiếu tiếp chỉ các delta R1/provenance/open acceptance, không lặp toàn PR. Không có thay đổi code mới được nghiệm thu trong lượt handoff này. Task4 đã chuẩn bị checklist ở [runbook cập nhật](C06_REDEPLOY_PREPARATION_2026_11_16.md), **chưa READY FOR DEPLOY APPROVAL** và không phải deploy/cutover authorization.
