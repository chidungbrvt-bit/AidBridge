# C10 · Thiết kế worker Java và local gate

Thiết kế Task #9 ngày 15/11, cập nhật Task #7 ngày 16/11/2026. Dũng phụ trách worker, verifier và test local. Khoa giữ primary owner C10 (4h), review ABI và proof, và phụ trách các quyết định redeploy/cutover thuộc task sau. Căn cứ [D56 bổ sung ngày 15/11](../planning/DECISION_LOG.md) và [Backlog C10](../planning/TASK_BACKLOG.md#c10---goods-to-chain-integration). Giờ thực tế chưa được ghi nhận.

## Fixture review ngày 16/11

[Fixture JSON](../fixtures/c10-goods-milestones.design.json) là dữ liệu giả lập cho một case mới, hai mặt hàng, hai donor độc lập, hai pledge, một lần hủy một phần và ba receipt. Tất cả commitment dùng để encode ABI trong JSON là placeholder. Mỗi `observed` để `NOT_RUN`; bằng chứng runtime nằm trong report Failsafe của lượt tương ứng.

`VOLUNTEER` tạo case, `VERIFIER` khác người duyệt đúng revision và evidence versions. `ORG_ADMIN` publish campaign và thực hiện R01. Hai donor gọi G01 và donor A gọi G03 cho pledge A. `VOLUNTEER` ghi giao D01 với evidence versions của lần giao. Campaign publish chỉ ghi audit local.

| Mặt hàng | Target | Pledge A | Pledge B | Hủy A | Nhận A1 | Nhận B1 | Nhận A2 | Giao | Còn giao được |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Rice (BAG) | 10 | 6 | 2 | 2 | 1 | 1 | 3 | 4 | 1 |
| Water (BOX) | 6 | 4 | 1 | 1 | 1 | 1 | 1 | 2 | 1 |

Tổng cam kết hiệu lực là `6/4`, tổng nhận là `5/3`, tổng giao là `4/2`. Review nghiệp vụ của delivery là `PENDING`, confirmed quantity là `0/0`.

### Mapping ABI

Calldata có sáu trường chung `orgRef, caseRef, eventId, chainSequence, evidenceCommitment, schemaVersion`. Mọi log có `[topic0, orgRef, caseRef, eventId]` và data bắt đầu bằng `chainSequence, evidenceCommitment, schemaVersion, timestamp`. Sau phần chung là các refs ở bảng. `auditEventId` và `event_key` là UUID riêng trong SQL. `eventId` dùng `logical_event_id` bytes32 từ job.

| Sequence fixture | Audit type, nguồn bất biến và refs | Function | Expected log |
| ---: | --- | --- | --- |
| 1 | `CASE_VERIFIED`. `verificationId`, `reviewedCaseVersion`, `case_revisions.snapshot_private`. | `recordCaseVerification` | `CaseVerified`, 128 bytes |
| 2 | `AID_PLEDGED` A. Pledge gốc từ audit v1, ref A từ `contributions.public_ref` theo đúng contribution/campaign/case/org. | `recordAidCommitment` + ref A | `AidCommitted`, 160 bytes |
| 3 | `AID_PLEDGED` B. Pledge và ref B lấy cùng cách với A, thuộc contribution B. | `recordAidCommitment` + ref B | `AidCommitted`, 160 bytes |
| 4 | `COMMITMENT_REDUCED` A. Audit v1 giữ lượng hủy theo dòng và `reason`. `event_key` riêng, ref lấy từ pledge A. N−1 là pledge B. | `recordCommitmentReduction` + ref A | `CommitmentReduced`, 160 bytes |
| 5 | `GOODS_RECEIVED` A1. `receiptRef` A1 từ audit R01. `commitmentRef` trong audit phải khớp pledge A và `goods_receipts.contribution_id`. | `recordGoodsReceipt` + receiptRef A1, ref A | `GoodsReceived`, 192 bytes |
| 6 | `GOODS_RECEIVED` B1. Receipt riêng thuộc contribution B và phải dùng ref B. | `recordGoodsReceipt` + receiptRef B1, ref B | `GoodsReceived`, 192 bytes |
| 7 | `GOODS_RECEIVED` A2. ReceiptRef mới của lần nhận thứ hai thuộc cùng pledge A. | `recordGoodsReceipt` + receiptRef A2, ref A | `GoodsReceived`, 192 bytes |
| 8 | `DELIVERY_RECORDED`. `deliveryRef` trong audit phải bằng `deliveries.delivery_ref` V16. So các dòng, thời điểm và evidence versions với record đã lưu. | `recordDelivery` + deliveryRef | `DeliveryRecorded`, 160 bytes |

Worker dùng `chain_sequence` đã lưu của từng case. Fixture hồi quy một pledge/một receipt vẫn dùng sequence 1–5 với reduction ở mốc 3. `DELIVERY_CONFIRMED`, dispute và VOID là operation riêng ngoài local gate Task #7. `FAILED` delivery chỉ có audit nghiệp vụ.

## Worker và verifier hiện có

- [BlockchainJobRepository](../../backend/aidbridge-backend/src/main/java/com/aidbridge/blockchain/repository/BlockchainJobRepository.java) claim/poll cả năm loại trên Hardhat `31337`. Với N > 1, worker kiểm N−1 `CONFIRMED` cùng case, chain và registry trước khi ký. Reduction và receipt còn kiểm pledge gốc của chính contribution trên deployment đó.
- [Manifest builder](../../backend/aidbridge-backend/src/main/java/com/aidbridge/blockchain/worker/CaseVerifiedManifestBuilder.java) đọc snapshot v1, đối chiếu nguồn V14/V16, dùng `CanonicalJson` và lưu manifest, encrypted salt, commitment một lần. Quantities, private IDs, evidence contents và URLs nằm ngoài chain. Khoa còn review vector canonical goods.
- [Worker](../../backend/aidbridge-backend/src/main/java/com/aidbridge/blockchain/worker/CaseVerifiedWorker.java) giữ signer session lock, nonce journal và một open attempt mỗi signer. Signed bytes và tx hash lưu trước broadcast. Restart trong cùng deployment dùng attempt đã lưu. RPC chạy ngoài SQL transaction.
- [Receipt verifier](../../backend/aidbridge-backend/src/main/java/com/aidbridge/blockchain/worker/CaseVerifiedReceiptVerifier.java) so chain qua worker, registry, signer, tx hash, canonical block, topic, event ID, org/case refs, sequence, commitment, schema và refs của operation. Receipt vắng hoặc timeout giữ `SUBMITTED` và API `PENDING_ON_CHAIN`. Chỉ receipt đúng context mới có thể được ghi `CONFIRMED` hoặc kết luận `TX_REVERTED` khi status 0. Proof lưu đủ context Task #6.

## Ma trận nghiệm thu Task #7

| Ca | Điều kiện nghiệm thu | Kết quả quan sát |
| --- | --- | --- |
| Unit/verifier | Hồi quy C05, reduction sai ref, receipt đảo ref, delivery sai ref. | **PASS** 11 tests, 0 failures/errors/skipped trong report trước khi bổ sung harness multi-commitment. |
| PostgreSQL worker | Job chờ khi predecessor chưa confirm. Reduction kiểm pledge gốc khi N−1 là pledge khác. Fixture năm mốc kiểm refs, stock và replay. | **PASS** 3 tests, 0 failures/errors/skipped trong report trước khi bổ sung harness multi-commitment. |
| Hardhat C03 và C06 một pledge | Artifact C06 đã ghim, sequence 1–5 và mỗi job/attempt `CONFIRMED`. Restart ở reduction, cùng bytes, nonce và hash. Đối chiếu calldata, log, `getEventRecord`, `getCaseSummary` và stock. | **PASS** 2 tests, 0 failures/errors/skipped ngày 16/11. Report trước patch mở rộng, nền `b1a59a6` + working tree Task #7. Local registry `0x5fbdb2315678afecb367f032d93f642f64180aa3`. Mốc 5 tx `0x020f5aae767ff927afdc9ae762c2b53ccd29dedf08130752ce4cb4ecdbb22e9b`. |
| Hardhat C06 hai pledge và ba receipt | Sequence 1–8 theo job đã lưu. A1 và A2 khác receiptRef nhưng cùng ref A. B1 dùng ref B. Reduction A đứng sau pledge B. Restart theo operation reduction, một attempt/job, tổng từng item giữ nguyên, case summary sequence 8. | **Not Run**. Ca hồi quy bổ sung. Nếu Khoa xác nhận ca này thuộc gate Task #7, Dũng chạy harness và bàn giao proof mới. |
| Fuji | Redeploy/cutover ở task riêng khi có approval. | **Not Run**. |

## Bàn giao kết quả local cho Khoa

**Task #7 · Local PASS · Pending Review**, ngày 16/11/2026. Dũng chạy `mvnw.cmd -B -ntp '-Dit.test=CaseVerifiedHardhatE2EIT' verify` với Hardhat `31337` và PostgreSQL `aidbridge_test`. Failsafe ghi **2 tests, 0 failures, 0 errors, 0 skipped** cho hồi quy C03 và C06 năm mốc. Lượt này dùng SHA nền `b1a59a63f2e3ba2cd00dcfdf8556fae580ec6d8c` cùng patch Task #7 trước khi thêm ca tám mốc.

Artifact C06 có build ID `solc-0_8_28-6d69e2cf1ddbaa7f61c90daac742b8efd4cf518a` và SHA-256 `976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592`. Mọi dòng dưới đây dùng registry `0x5fbdb2315678afecb367f032d93f642f64180aa3`, `logIndex=0`, job và attempt đều `CONFIRMED`.

| Sequence | Operation | Logical event ID | Tx hash | Block |
| ---: | --- | --- | --- | ---: |
| 1 | `CASE_VERIFIED` | `0x6d7f574f58bc3be206481a815b5d4d29e8239e2f4cf8ac3b9c4140dc8b4bc55e` | `0xfacb5dd91620dc390247cbffc5f0cb2fb0e66cb258dd2a5e157f313c44b71e77` | 3 |
| 2 | `AID_PLEDGED` | `0x57abcb91e748e3a4b46bb5d12f4f38bad4941af7087dd78f30d731053fd61e52` | `0x6a221929b1ad1cdd13630a0a01a97f0c389050e759e389893a2f4eb761f52c8f` | 4 |
| 3 | `COMMITMENT_REDUCED` | `0xb10cff15cce09eb6cd771564a1f64e3670cd124487b5650388ef7ac1d766947a` | `0x98a29725213f9d585a5eb49a51db81da9baf3074d0c059e9ad0b9129fb75ada6` | 5 |
| 4 | `GOODS_RECEIVED` | `0xeedc51817a24910c98c973f328415762dc6e64ce45de7f4222a26989fe01d31a` | `0xa86a505f3aefbfe8659bb3f7c6b41a961fc4c980fda760d307658cc6992ffa34` | 6 |
| 5 | `DELIVERY_RECORDED` | `0x514af415a9457ed6359081b1e0e6e15e412303bdc289ba128651dd8a765f43be` | `0x020f5aae767ff927afdc9ae762c2b53ccd29dedf08130752ce4cb4ecdbb22e9b` | 7 |

Report xác nhận mỗi job có một attempt, restart ở sequence 3 giữ `nonce=2` và `signedBytesReused=true`, số lượng từng mặt hàng giữ nguyên. Nguồn là phần trích `C06_E2E` từ `backend/aidbridge-backend/target/failsafe-reports/TEST-com.aidbridge.casefile.CaseVerifiedHardhatE2EIT.xml`.

Khoa review ABI, registry và receipt/log proof, rồi xác nhận ca tám mốc có thuộc gate hay không. Dũng cung cấp phần trích report và chạy lại trên SHA bàn giao khi cần gắn bằng chứng với một commit duy nhất. Ca tám mốc và Fuji **Not Run**. Fuji redeploy và cutover thuộc task riêng.

## Chạy và bàn giao

Dùng JDK 21, PostgreSQL `aidbridge_test` trên loopback port `5432`, user test `aidbridge_app`. Đặt `AIDBRIDGE_TEST_DATABASE_URL`, `AIDBRIDGE_TEST_DATABASE_USERNAME` và `AIDBRIDGE_TEST_DATABASE_PASSWORD` trong PowerShell chạy Maven. Schema riêng do harness tạo. Với outbox mặc định `43113`, test chỉ chuyển các job `PENDING` chưa có attempt của case vừa tạo sang `31337`.

PowerShell 1

```powershell
cd 'D:\thi\UniHack2026\AidBridge\contracts'
node node_modules/hardhat/dist/src/cli.js node --hostname 127.0.0.1
```

PowerShell 2

```powershell
cd 'D:\thi\UniHack2026\AidBridge\backend\aidbridge-backend'
$env:BLOCKCHAIN_ENABLED = 'false'
$env:AIDBRIDGE_E2E_HARDHAT_RPC_URL = 'http://127.0.0.1:8545'
.\mvnw.cmd -B -ntp '-Dit.test=CaseVerifiedHardhatE2EIT' verify
Select-String -Path '.\target\failsafe-reports\TEST-com.aidbridge.casefile.CaseVerifiedHardhatE2EIT.xml' -Pattern 'C06_E2E'
```

Lệnh trên chạy cả ca hồi quy mở rộng. Khi xác minh ca này, kỳ vọng ba Hardhat tests, 0 failures/errors/skipped. Kiểm dòng `C06_E2E PASS confirmedJobs=8` và tám dòng bằng chứng liền trước thuộc cùng registry. Bàn giao phần trích `C06_E2E` cho Khoa cùng test counts và SHA hoặc SHA nền + diff.

## Đối chiếu fixture offline

Từ thư mục `contracts`, đoạn dưới dùng artifact C06 và ethers để đối chiếu calldata/log của fixture giả lập. Kết quả thuộc phạm vi ABI theo fixture. Các trường `observed` vẫn để trống cho đến khi có bằng chứng runtime riêng.

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
