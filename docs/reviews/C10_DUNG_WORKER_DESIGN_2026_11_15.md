# Task 9 · Thiết kế worker Java cho C10 · 15/11/2026

**Bản thiết kế để review.** Dũng phụ trách worker và receipt verifier Java, dự kiến hoàn thành cùng kiểm thử local ngày 16/11. Khoa giữ vai trò primary owner C10 (4h), phụ trách ABI, xác nhận redeploy registry C06 và script xác minh độc lập. Mốc xét Fuji là 17/11. Giờ thực tế của Dũng chưa được ghi nhận. [D57](../planning/DECISION_LOG.md) · [Backlog C10](../planning/TASK_BACKLOG.md#c10---goods-to-chain-integration)

Baseline đã đọc là `dung` tại `017490dd4ae64273fbfc4bccf4d8ceb8f878aaf2` cùng D57 và các thay đổi đang có trong working tree. B09 đã có outbox D01 và `delivery_ref` ở V16. B10 đã có D02 tạo outbox `DELIVERY_CONFIRMED`, V17/V18 và luồng xử lý sau giao. Hiện worker Java vẫn chỉ claim/poll `CASE_VERIFIED`. [B08 R01](B08_R01_HANDOFF.md) · [B09 và B10](../planning/TASK_BACKLOG.md#b09---delivery-recordsfailed-attempts) · [BlockchainJobRepository](../../backend/aidbridge-backend/src/main/java/com/aidbridge/blockchain/repository/BlockchainJobRepository.java)

## Một case giả lập, bốn sequence

[Fixture JSON](../fixtures/c10-goods-milestones.design.json) dùng tổ chức mới đã đăng ký relayer test, một creator/recorder `VOLUNTEER`, một `VERIFIER` độc lập, một `ORG_ADMIN` và một donor đã được cấp quyền. Creator tạo case có consent cho public summary và evidence fixtures hợp lệ. Verifier duyệt đúng revision đang hiển thị. Admin tạo và publish campaign, đưa case lên `IN_PROGRESS`. Donor cam kết hai dòng, admin nhận từng phần trong một receipt, rồi volunteer ghi giao hai dòng và chọn evidence versions của lần giao. Các bước nghiệp vụ dùng API thật khi chạy harness. Publish là audit local, nên chỉ có bốn job.

Thứ tự HTTP để dựng fixture: `POST /api/cases` → `POST /api/cases/{id}/verify` → `POST /api/campaigns` → `POST /api/campaigns/{id}/publish` → `POST /api/campaigns/{id}/commitments` → `POST /api/commitments/{id}/receipts` → `POST /api/cases/{id}/deliveries`. Harness lấy ID từ các response, mỗi actor có session/CSRF riêng và dùng lại cùng key/body khi kiểm replay. [API Specification](../architecture/API_SPECIFICATION.md)

| Mặt hàng | Target | Pledged | Received | Delivered | Confirmed | Available to deliver |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Rice (kg) | 10 | 8 | 5 | 4 | 0 | 1 |
| Drinking Water (box) | 6 | 5 | 3 | 2 | 0 | 1 |

Calldata có sáu trường chung theo thứ tự `orgRef, caseRef, eventId, chainSequence, evidenceCommitment, schemaVersion`. Mỗi event có `topics = [topic0, orgRef, caseRef, eventId]`. Data bắt đầu từ `chainSequence, evidenceCommitment, schemaVersion, timestamp`; các refs bổ sung nối theo thứ tự ở bảng. `timestamp` là giờ block do contract ghi.

| Seq. | Audit type / nguồn snapshot | Function, selector và refs bổ sung | Expected log |
| ---: | --- | --- | --- |
| 1 | `CASE_VERIFIED` · `verificationId`, `reviewedCaseVersion` từ payload v1 và `case_revisions.snapshot_private` theo đúng org/case/version. | `recordCaseVerification` · `0x1bd1fadf` · 6 tham số chung. | `CaseVerified` · 128 bytes data. Hồi quy C05 giữ nguyên envelope và manifest hiện tại. |
| 2 | `AID_PLEDGED` · payload v1 có `contributionId`, `lines[{campaignItemId,quantity}]`. Scoped join contribution → campaign → case/org để lấy `contributions.public_ref` và kiểm ownership các dòng. Đọc lượng pledge gốc từ audit. | `recordAidCommitment` · `0x89f8809e` · thêm `commitmentRef`. | `AidCommitted` · 160 bytes data, word cuối là `commitmentRef`. |
| 3 | `GOODS_RECEIVED` · payload v1 có `receiptId`, `contributionId`, `receiptRef`, `commitmentRef`, `receivedAt` và các dòng có `contributionItemId`. So receipt và từng dòng với cam kết cùng org/case; so `commitmentRef` trong audit với pledge gốc và `contributions.public_ref`. | `recordGoodsReceipt` · `0x73a5f8b9` · thêm `receiptRef, commitmentRef`. | `GoodsReceived` · 192 bytes data, hai word cuối đúng thứ tự `receiptRef, commitmentRef`. |
| 4 | `DELIVERY_RECORDED` · payload v1 có `deliveryId`, `campaignId`, `deliveryRef`, `handedOverAt`, lines và `evidenceVersionIds`. Scope theo org/case/campaign, đối chiếu `outcome = DELIVERED`, `deliveryRef` bằng `deliveries.delivery_ref` của V16 và evidence snapshot theo version. | `recordDelivery` · `0x2df45447` · thêm `deliveryRef`. | `DeliveryRecorded` · 160 bytes data, word cuối là `deliveryRef`. Business review vẫn `PENDING`. |

Nguồn tạo audit/job: [VerificationOutboxService](../../backend/aidbridge-backend/src/main/java/com/aidbridge/verification/service/VerificationOutboxService.java) · [ContributionOutboxService](../../backend/aidbridge-backend/src/main/java/com/aidbridge/contribution/service/ContributionOutboxService.java) · [DeliveryService](../../backend/aidbridge-backend/src/main/java/com/aidbridge/delivery/service/DeliveryService.java) · [V16](../../backend/aidbridge-backend/src/main/resources/db/migration/V16__add_delivery_ref.sql) · [AidRegistry.sol](../../contracts/contracts/AidRegistry.sol)

**Tùy chọn sau khi bốn mốc pass:** `DELIVERY_CONFIRMED` → `confirmDelivery` (`0xa9967f54`) → `DeliveryConfirmed` (160 bytes, thêm `deliveryRef`). D02 dùng reviewer khác recorder; payload v1 có `confirmationId`, `deliveryId`, `campaignId`, `deliveryRef`, `decision`, `reviewedAt` và đúng tập `reviewedEvidenceVersionIds`. Khớp ref với delivery gốc và chỉ ký sequence 5 khi sequence 4 đã `CONFIRMED`. B10 đã ghi outbox; Dũng và Khoa quyết định đưa vào demo sau khi kiểm thử. Cancel/`COMMITMENT_REDUCED`, dispute và VOID nằm ngoài đường chain demo D57.

Các SQL UUID và snapshot mẫu là dữ liệu giả. `auditEventId` là ID dòng audit, `eventKey` là UUID nghiệp vụ, còn `logicalEventId` là bytes32 của job, dùng làm ABI `eventId`. Trong JSON, `privateFixture`, `auditPayload` và `privateSource` chỉ dùng đối chiếu ở backend. ABI chỉ dùng refs opaque, envelope và event data đã định nghĩa trong contract. Commitment trong fixture là hằng bytes32 **placeholder** để encode/decode. Test runtime tính commitment từ manifest và salt thực của job test. Salt, manifest, evidence, quantities và UUID nội bộ nằm ngoài chain. Mọi trường `observed` đang trống.

## Thiết kế Java cho ngày 16/11

1. **Giữ một vòng đời worker.** Bắt đầu với `AID_PLEDGED`, rồi thêm receipt và delivery. Tái dùng scheduler, adapter Web3j, `SignerSessionLock`, leases, journal và transaction boundaries của C05. Mỗi operation chỉ cần định nghĩa scoped snapshot loader, các refs theo thứ tự ABI, cách encode calldata và expected log. Chọn allowlist operation chung cho cả claim và poll. Retain regression C05 trên artifact C03 và chạy lại mốc đó trên C06.
2. **Gate thứ tự trước khi ký.** Query hiện loại job còn predecessor nhỏ hơn chưa `CONFIRMED`, nhưng lỗ hổng do thiếu hẳn N−1 vẫn có thể lọt khi mở rộng. Giữ guard hiện có và với N > 1 yêu cầu job N−1 tồn tại cùng case/chain, `CONFIRMED` và có attempt `CONFIRMED` gắn đúng registry đang chạy. Kiểm/khoá job theo `FOR UPDATE … SKIP LOCKED`. Đề xuất reason có giới hạn cho job được hỗ trợ: `PREDECESSOR_MISSING`, `PREDECESSOR_NOT_CONFIRMED`, `PREDECESSOR_OUT_OF_SCOPE`, `PREDECESSOR_REGISTRY_MISMATCH`. Ghi reason và thời điểm kiểm lại, giữ trạng thái chờ và không ký. Xử lý job bị chắn với lần kiểm riêng có giới hạn để các case khác vẫn tiến. `COMMITMENT_REDUCED` hiện vẫn có outbox B07; nếu đứng trước một mốc demo, dừng case đó, giữ nguyên sequence và job. `CASE_VERIFIED` vẫn chỉ được ký sequence 1.
3. **Đóng băng dữ liệu dự kiến.** Trong transaction ngắn, kiểm audit/job cùng case, org `APPROVED`, relayer binding, `payloadVersion = 1`, `schemaVersion = 1`, định danh và refs đúng shape/scope, quantities nguyên dương, evidence links bất biến. Payload sai phiên bản hoặc refs sai thì fail trước lúc ký và lưu reason đã chuẩn hóa. Đề xuất canonical manifest riêng cho mốc hàng hóa dựa trên snapshot v1, chuẩn hóa thứ tự dòng theo ID và biểu diễn UTC một lần; Khoa review vector Java/JS. Tái dùng canonicalization và công thức commitment C01/D49 ràng buộc chain/registry; lưu manifest, salt mã hóa và commitment theo mẫu C05. Cố định các fields dùng để dựng expected log từ preparation đã lưu khi poll/retry, dù aggregate nghiệp vụ đã thay đổi. Dùng `logical_event_id` và `chain_sequence` đang có; digest phía contract gồm selector, envelope trừ `eventId`, và refs bổ sung theo ABI.
4. **Ghi attempt rồi broadcast.** Giữ lock session theo chain/signer, V13 one open attempt/signer và one open attempt/job, nonce journal và fencing của C07. RPC kiểm chain, lấy nonce/gas, gửi và poll ở ngoài SQL transaction. Lưu attempt `PREPARED` gồm chain, registry, signer, nonce, hash và signed bytes mã hóa trước broadcast. Khi restart hoặc timeout, kiểm lại binding và hash rồi phát lại chính bytes đó nếu đến hạn, hoặc poll attempt gốc. Các worker dùng chung signer phải dùng chung DB/lock. Khi đổi deployment, xử lý hết nghĩa vụ signer ở DB cũ và dừng worker cũ trước khi bật worker mới. Signed bytes và commitment gắn deployment cũ chỉ dùng để hoàn tất đối chiếu deployment đó.
5. **Xác minh receipt theo operation.** Worker so RPC chain với chain đã ghim, verifier so from/to/hash với attempt và block number/hash với canonical block. Status 1 cần đúng một log chưa bị removed từ registry, đúng chữ ký event ở `topic0`, đủ bốn topics, đúng data length và các ABI word hợp lệ. So đủ org/case/event ID/sequence/schema/commitment/refs, kiểm kiểu của timestamp và log index. Script độc lập so timestamp với canonical block và đọc lại event record. Receipt vắng/RPC timeout giữ `SUBMITTED` và API `PENDING_ON_CHAIN`. Proof sai không xác nhận. Chỉ kết luận `TX_REVERTED` sau khi status 0 và context khớp. Với mốc hàng hóa, C06 revert `EventAlreadyRecorded` nếu cùng event ID/digest, hoặc `EventIdConflict` nếu digest đổi. Đối chiếu original hash/receipt/log trước khi xử lý chênh lệch. Cập nhật job và attempt trong cùng transaction, giữ nguyên delivery, evidence và quantities.

Nguồn worker hiện tại: [CaseVerifiedWorker](../../backend/aidbridge-backend/src/main/java/com/aidbridge/blockchain/worker/CaseVerifiedWorker.java) · [Receipt verifier](../../backend/aidbridge-backend/src/main/java/com/aidbridge/blockchain/worker/CaseVerifiedReceiptVerifier.java) · [V12 sequence](../../backend/aidbridge-backend/src/main/resources/db/migration/V12__link_blockchain_jobs_to_case.sql) · [C07 safety](C07_WORKER_SAFETY_REVIEW.md) · [C08 recovery](C08_DUNG_RECOVERY_2026_11_13.md). C08 từng kiểm receipt polling/restart; full log replay vẫn thiếu bằng chứng riêng.

## Ma trận kiểm thử

| Lượt | Ca ưu tiên và điều kiện pass | Trạng thái 15/11 |
| --- | --- | --- |
| 1 · Unit/ABI | Java và ethers từ artifact C06 khớp toàn bộ calldata, topic/data/refs, payload digest và vector commitment. Test refs riêng sai từng vị trí, đảo hai refs của receipt, thiếu/trùng/removed log, sai registry/signer/hash/chain, block chưa canonical, revert đúng/sai context, ABI data hỏng, wrong schema và payload sai version. Chạy lại `CaseVerifiedReceiptVerifierTest` và `GoldenVectorParityTest`. | **Pass** cho đối chiếu tĩnh artifact + encode/decode fixture bằng ethers. **Not Run** cho Java/ABI parity và unit C10. |
| 2 · PostgreSQL/retry | N dừng trước sign khi N−1 pending, failed, thiếu, khác registry hoặc là job ngoài scope chưa xử lý. Sau khi predecessor đúng được confirm, N tiếp tục; case khác vẫn tiến. Thử hai worker chung signer, restart sau persist `PREPARED` và ở `SUBMITTED`, RPC timeout rồi nhận proof. Assert cùng event ID/nonce/hash/signed bytes và một attempt trong các kịch bản reuse; không có duplicate audit/job/stock. So aggregate và số dòng trước/sau. Hồi quy `CaseVerifiedWorkerIT` và `BlockchainTransactionPersistenceIT`. | **Not Run** |
| 3 · Hardhat 31337 | Tái dùng harness `CaseVerifiedHardhatE2EIT`. Chạy riêng hồi quy C03 hiện có, rồi deploy đúng C06 local, đăng ký org/relayer test và tạo case hai mặt hàng qua HTTP. Mỗi job confirm đúng log/attempt/registry; DB và `getEventRecord` khớp digest, `getCaseSummary` kết thúc ở sequence 4. Idempotent retry giữ nguyên hàng; receipt status 1 chưa đủ để pass. | **Not Run** |
| Fuji | Local pass cả ba lượt, không skip, Dũng duyệt và Khoa xác nhận artifact + redeploy. | **Not Run**, chờ local gate và Khoa xác nhận. |

Tên test C10 dự kiến là `GoodsMilestoneAbiParityTest`, `GoodsMilestoneReceiptVerifierTest`, `GoodsMilestoneWorkerIT` và `GoodsMilestonesHardhatE2EIT`. Các tên này là checklist triển khai cho Dũng, chưa có test classes tương ứng. [Test Strategy IT10–IT14](../quality/TEST_STRATEGY.md) · [Harness C05](../../backend/aidbridge-backend/src/test/java/com/aidbridge/casefile/CaseVerifiedHardhatE2EIT.java) · [Workflow C07](../../.github/workflows/c07-worker-safety.yml)

Khi đã có code test, dùng JDK 21, PostgreSQL test `aidbridge_test` ở loopback port `5432`, tài khoản test `aidbridge_app` và cung cấp `AIDBRIDGE_TEST_DATABASE_URL`, `AIDBRIDGE_TEST_DATABASE_USERNAME`, `AIDBRIDGE_TEST_DATABASE_PASSWORD` qua cấu hình môi trường test. Harness tạo schema riêng và Flyway chạy đến latest, gồm V16 và cả V17/V18 trên cây hiện tại. Với outbox hiện mặc định `chain_id = 43113`, harness C10 cần chọn đúng bốn job từ các `auditEventId` HTTP vừa tạo và đặt `chain_id = 31337` trong schema test, khi các job còn `PENDING` và chưa có attempt. Automated scheduler tắt; test tự tạo worker local. Đây là setup của harness tương tự C05.

PowerShell 1, Hardhat từ thư mục contracts:

```powershell
cd 'D:\thi\UniHack2026\AidBridge\contracts'
node node_modules/hardhat/dist/src/cli.js node --hostname 127.0.0.1
```

PowerShell 2, sau khi có biến DB test, code test và artifact đúng hash:

```powershell
cd 'D:\thi\UniHack2026\AidBridge\backend\aidbridge-backend'
$env:BLOCKCHAIN_ENABLED = 'false'
$env:AIDBRIDGE_E2E_HARDHAT_RPC_URL = 'http://127.0.0.1:8545'
.\mvnw.cmd -B -ntp '-Dtest=CaseVerifiedReceiptVerifierTest,GoldenVectorParityTest,GoodsMilestoneAbiParityTest,GoodsMilestoneReceiptVerifierTest' test
.\mvnw.cmd -B -ntp '-Dit.test=CaseVerifiedWorkerIT,BlockchainTransactionPersistenceIT,GoodsMilestoneWorkerIT' verify
.\mvnw.cmd -B -ntp '-Dit.test=CaseVerifiedHardhatE2EIT,GoodsMilestonesHardhatE2EIT' verify
```

Đọc cả `target/surefire-reports` và `target/failsafe-reports` theo lượt chạy. Các suite yêu cầu phải xuất hiện với số test > 0 và 0 skip/error/failure. Hai lệnh `verify` dùng lựa chọn Failsafe riêng; Maven vẫn có thể chạy unit suite theo phase mặc định. Các lệnh Maven/Hardhat ở trên đều **Not Run** trong Task 9 ngày 15/11.

## Đối chiếu fixture offline

Từ thư mục `contracts`, chạy đoạn dưới với artifact và dependency ethers đã có. Nó dựng calldata và event từ hai nửa của fixture bằng ABI rồi kiểm lại từng value. Đây là phần kiểm tĩnh tái lập được cho ngày 15/11.

```powershell
@'
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { Interface } from 'ethers';

const artifact = readFileSync('artifacts/contracts/AidRegistry.sol/AidRegistry.json');
const fixture = JSON.parse(readFileSync('../docs/fixtures/c10-goods-milestones.design.json', 'utf8'));
assert.equal(createHash('sha256').update(artifact).digest('hex'), fixture.artifact.artifactSha256);
const abi = new Interface(JSON.parse(artifact).abi);
const { orgRef, caseRef, schemaVersion, fixtureBlockTimestamp } = fixture.publicFixture;
for (const { auditType, expected: e } of fixture.milestones) {
    const fn = abi.getFunction(e.functionSignature);
    const event = abi.getEvent(e.eventSignature);
    assert.equal(fn.selector, e.selector);
    assert.deepEqual(fn.inputs.slice(6).map(p => p.name), e.extraRefNames);
    assert.deepEqual(event.inputs.slice(7).map(p => p.name), e.extraRefNames);
    const common = [orgRef, caseRef, e.logicalEventId, BigInt(e.sequence), e.evidenceCommitment, BigInt(schemaVersion)];
    const fnArgs = [...common, ...e.extraRefValues];
    const eventArgs = [...common, BigInt(fixtureBlockTimestamp), ...e.extraRefValues];
    const calldata = abi.encodeFunctionData(fn, fnArgs);
    const log = abi.encodeEventLog(event, eventArgs);
    assert.deepEqual([...abi.decodeFunctionData(fn, calldata)], fnArgs);
    assert.deepEqual(log.topics, [e.topic0, orgRef, caseRef, e.logicalEventId]);
    assert.equal((log.data.length - 2) / 2, e.eventDataBytes);
    assert.deepEqual([...abi.decodeEventLog(event, log.data, log.topics)], eventArgs);
    console.log('PASS ' + auditType + ' sequence=' + e.sequence + ' selector=' + fn.selector + ' eventDataBytes=' + e.eventDataBytes);
}
'@ | node --input-type=module
```

```text
PASS CASE_VERIFIED sequence=1 selector=0x1bd1fadf eventDataBytes=128
PASS AID_PLEDGED sequence=2 selector=0x89f8809e eventDataBytes=160
PASS GOODS_RECEIVED sequence=3 selector=0x73a5f8b9 eventDataBytes=192
PASS DELIVERY_RECORDED sequence=4 selector=0x2df45447 eventDataBytes=160
```

## Gói bàn giao cho Khoa

- **Phiên bản ABI xuất phát:** source C06 `6944fff`; artifact local `contracts/artifacts/contracts/AidRegistry.sol/AidRegistry.json`, build `solc-0_8_28-6d69e2cf1ddbaa7f61c90daac742b8efd4cf518a`, Solidity 0.8.28, Paris, optimizer 200. SHA-256 artifact `976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592`; runtime Keccak-256 `0x525314d10311e01e01a61a6f9e75ee432f59ca09ff3aa27445aed3962e09011e`. Đã đối chiếu source với build input sau chuẩn hóa line endings và ABI/bytecode với build output bằng Node, exit 0. Khoa xác nhận artifact dùng để deploy và script độc lập. [Review C06](C06_PART2_D55_REVIEW.md)
- **Dữ liệu chạy thực tế:** sau local run, điền mỗi hàng `observed` với chain ID, registry, tx hash, block number/hash, log index, logicalEventId, sequence và raw public log (address/topics/data). Ghi thêm artifact SHA, commit/working tree, lệnh, kết quả và số test skipped trong báo cáo. Khoa dùng tx input, canonical block, raw log, `getEventRecord` tại block receipt và `getCaseSummary` tại block mốc cuối. Kết quả offline hôm nay chỉ có ABI fixture; toàn bộ runtime fields để trống.
- **Cổng 17/11:** Dũng trình kết quả local worker/verifier; Khoa chốt ABI và redeploy C06. Nếu qua cổng, dùng DB demo mới, case mới từ sequence 1, đăng ký lại org/relayer C06, lưu manifest deployment và giữ proof C03/C04/C05 theo registry cũ. Nếu sáng 17/11 chưa đạt, C10 dùng proof Fuji C05 đã có cho `CASE_VERIFIED` và trình bày mốc hàng hóa bằng snapshot + đối chiếu offline. T02 dùng `/api/campaigns/{campaignId}/audit-trail` theo D57; Minh xác nhận mock/UI.

| Điểm còn mở | Owner và tiếp theo |
| --- | --- |
| Worker/verifier hàng hóa và Java parity chưa có | Dũng triển khai `AID_PLEDGED` trước, review vector với Khoa, rồi nối receipt và delivery trong slot 16/11. |
| PostgreSQL retry và Hardhat C06 chưa có bằng chứng | Dũng hoàn thiện harness, chạy ba lượt, ghi kết quả thực tế và kiểm no-skips. Khoa đối chiếu proof độc lập và event mapping. |
| Điều kiện redeploy Fuji chưa đạt | Khoa xác nhận artifact/redeploy sau khi nhận local proof và Dũng duyệt. Tới mốc sáng 17/11 nếu vẫn thiếu, dùng fallback D57. |

Lệnh đã chạy trong lượt thiết kế: `git status`, `git log`, `git rev-parse`, `git diff`, `rg`, `Get-Content`, `Get-FileHash`, `node --input-type=module` (đọc build info, đối chiếu ABI, encode/decode fixture offline) và `git diff --check`. Proof Fuji trước đây được tham chiếu từ [báo cáo B05](B05_REAL_FUJI_RUN_2026_11_13.md).
