# Task #3 — C06 local worker/verifier gate + receipt context review, 17/11/2026

**LOCAL C10 GATE: NEEDS FIX.** Một finding Medium có thật trên PR head: CASE_VERIFIED thiếu RPC chain guard trước ký/rebroadcast. Đồng thời **INSUFFICIENT EVIDENCE** cho runtime acceptance trên đúng committed SHA. F1/F2 đã addressed ở source/design, **OPEN ở cấp nghiệm thu runtime**, không còn mô tả là thiếu implementation. Không merge/deploy/cutover trong review này.

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
