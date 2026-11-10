# C01 - Blockchain review và bàn giao contract draft

Ngày review: 09/11/2026 (Asia/Saigon). Người thực hiện: Khoa/Codex. Trạng thái: **In Review**, chưa Done. Primary owner/estimate trong backlog được giữ nguyên; phân công cá nhân theo `9_11.pdf` và yêu cầu hiện hành của Khoa. Đây là tài liệu review, ABI draft và fictional fixtures; không phải contract đã triển khai.

## 1. Phạm vi, baseline và cách dùng

- Repo `D:/Project/AidBridge`, nhánh `khoa`, baseline `e4f4c2037ea9e243f893780031b3626d5f6a77ba`.
- Đã chạy `git fetch origin`; `main` và `khoa` cùng baseline; working tree sạch trước khi tạo bộ này. `origin/khoa` đứng sau local 2 commits đã tồn tại trước phiên; không có commit mới trong phiên.
- `origin/dung` ở `84a042f` có C02 setup và smoke probe; chỉ đọc, không merge hoặc chạy. Smoke probe không phải `AidRegistry` và không chứng minh C03 đã có.
- Đọc `AGENTS.md`, Master Plan, MVP Scope, Decision Log, C01 backlog, Blockchain Architecture, Test Strategy; đối chiếu yêu cầu C01 trong `9_11.pdf` và giới hạn blueprint. Không sửa application, migrations, contract toolchain, frontend, sample gốc của Dũng hoặc F02.
- Các phát biểu SOURCE bên dưới có nghĩa là có trong tài liệu repo, không tự chứng minh quyết định đã duyệt hoặc runtime hoạt động.

Đọc file này trước, rồi dùng các file cùng bộ:

| File | Cách dùng / nhãn |
|---|---|
| [ABI draft](../fixtures/c01/AidRegistry.abi.draft.json) | JSON ABI array soạn tay; subset C01, không compiler-generated, không full deployment ABI |
| [Reviewed sample](../fixtures/c01/case-verified.reviewed.json) | Snapshot nguồn + envelope + encoded arguments/log data; dữ liệu giả, không RPC receipt |
| [Golden-vector input](../fixtures/c01/golden-vector.input.json) | Canonical manifest bytes và SHA-256 đã tính; Keccak/cross-language chưa chạy |
| [Test expectations](../fixtures/c01/test-expectations.json) | 17 test specifications cho C03/C05/C07/C08; mọi executionStatus là NOT_RUN |
| [Fixture checker](../fixtures/c01/verify-c01.mjs) | Kiểm tra tĩnh bằng Node built-ins; tùy chọn `--ethers` chỉ dùng ethers đã có trong C02, không cài package |

## 2. Kết luận review có thể dùng ngay

SOURCE: `CASE_VERIFIED` (business audit type) -> `recordCaseVerification` (Solidity function) -> `CaseVerified` (Solidity event). Không đổi tên business type thành event name trong SQL. `auditEventId` là UUID của audit row; `event_key` là UUID decision/verification; `eventId` trên chain là random bytes32 riêng. Không ép UUID hoặc beneficiary alias thành public refs.

SOURCE: Admin đăng ký organization trước; organization phải active; caller phải có relayer permission và đúng organization. Admin không mặc nhiên được ghi business milestones. Khác tổ chức, caller bị revoke và admin chưa được cấp relayer đều phải bị từ chối. First sequence của case là 1. Org registration không tiêu thụ sequence của case.

SOURCE: Salt/manifest/evidence/user identity ở off-chain. Chỉ public allowlist envelope và event block timestamp. Relayer signature chỉ chứng minh backend key gửi assertion; không phải chữ ký độc lập của verifier và không chứng minh sự thật ngoài đời. BR-02 vẫn do backend kiểm tra distinct identities.

SOURCE: Receipt `status=1` là điều kiện cần; phải đối chiếu đúng chain, registry, event signature, indexed refs và toàn bộ non-indexed fields với persisted job trước khi confirmed. Reverted retry không bao giờ là successful proof.

## 3. ABI draft và Java/Web3j decoding

```solidity
function recordCaseVerification(
    bytes32 orgRef,
    bytes32 caseRef,
    bytes32 eventId,
    uint64 chainSequence,
    bytes32 evidenceCommitment,
    uint16 schemaVersion
) external; // nonpayable; returns nothing

event CaseVerified(
    bytes32 indexed orgRef,
    bytes32 indexed caseRef,
    bytes32 indexed eventId,
    uint64 chainSequence,
    bytes32 evidenceCommitment,
    uint16 schemaVersion,
    uint64 timestamp
);
```

Thứ tự và types của write/event lấy từ sample + mục D48 trong Blockchain Architecture. Dùng tên `timestamp` nhất quán; `blockTimestamp` trong sample gốc là mô tả giá trị, không thêm một trường thứ hai. Giá trị phải lấy từ block time của transaction thực, không từ HTTP `reviewedAt`; hai thời điểm trong fixture cố ý cố định để kiểm tra encoding.

Canonical signatures không chứa tên biến hoặc `indexed`:

```text
recordCaseVerification(bytes32,bytes32,bytes32,uint64,bytes32,uint16)
CaseVerified(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64)
```

- Function selector = 4 bytes đầu Keccak-256 của canonical function signature UTF-8. Không dùng NIST SHA3-256.
- Call arguments: 6 static words, 192 bytes; calldata cùng selector: 196 bytes.
- Event non-anonymous: topic0 = Keccak-256 của canonical event signature; topics1/2/3 lần lượt orgRef/caseRef/eventId, mỗi giá trị bytes32 trực tiếp.
- Data: 4 ABI words (128 bytes): uint64 chainSequence, bytes32 evidenceCommitment, uint16 schemaVersion, uint64 timestamp. Integers được zero-pad bên trái thành 32 bytes; không packed encode.

Web3j decoder cần `TypeReference<Bytes32>(true)` cho ba indexed fields; data types lần lượt `Uint64`, `Bytes32`, `Uint16`, `Uint64`. Dùng `BigInteger` cho unsigned values; không cast timestamp/uint64 thành signed Java int. Kiểm tra topics count/data length và context trước decode; decode thành công chưa đủ để confirmed. Tránh dùng SHA-256 cho event signature; dùng `EventEncoder`/Web3j hashing theo ABI. Phần Java này là hướng dẫn tích hợp, chưa compile.

Read API dưới đây là **PROPOSED C01-O04**, vì nguồn chưa khóa exact output ABI:

```solidity
function getEventRecord(bytes32 eventId) external view returns (
    bool exists, bytes4 operation, bytes32 orgRef, bytes32 caseRef,
    uint64 chainSequence, bytes32 evidenceCommitment,
    uint16 schemaVersion, bytes32 payloadDigest
);
function getCaseSummary(bytes32 caseRef) external view returns (
    bool verified, uint256 openDisputeCount, uint64 lastChainSequence
);
```

Đề xuất flat outputs để decoder Java đơn giản; missing event trả exists=false và zero fields, không lẫn với một successful record. `operation` là function selector; không tự tạo enum numbering chưa thống nhất. ABI draft không chứa constructor, registration/update/revoke, inherited OpenZeppelin ABI hoặc errors. C03 phải bổ sung full compiler ABI và exact errors sau review; không dùng draft này deploy contract. Mọi sửa output types/order phải cập nhật fixtures/decoder cùng nhau.

Nguồn kỹ thuật kiểm tra 09/11/2026: [Solidity ABI specification](https://docs.soliditylang.org/en/latest/abi-spec.html), [Web3j filters/events](https://docs.web3j.io/4.14.0/advanced/filters_and_events/). Chỉ dùng quy tắc ABI/event ổn định; không thay phiên bản compiler/toolchain của nhóm.

## 4. OPEN register và phương án mặc định đề xuất

Các mã C01-Oxx là câu hỏi review mới, không thay thế hoặc mạo nhận D43-D48 trong Decision Log. Ghi proposal tại đây để tránh sửa lịch sử/quyết định thuộc owner khác. Dũng có thể chuyển phần được xác nhận sang Decision Log khi review.

| ID / trạng thái | Bằng chứng / vấn đề | Default PROPOSED và lý do | Owner / tác động / next action |
|---|---|---|---|
| C01-O01 OPEN | `B01_BASELINE.md` không có; Decision Log chỉ D01-D25. D43-D45 không có dù sample viện dẫn; D48 chỉ xuất hiện dưới dạng khẳng định trong Blockchain Architecture. Working tree và `origin/dung` chưa có code verification/outbox/V5-V8 được nhắc. | Giữ tất cả là tài liệu nguồn chưa đối chiếu được với code/approval; không tái tạo các quyết định bị thiếu từ suy đoán. | Dũng cung cấp commit/PR hoặc bản decision đã xác nhận. Không chặn draft C03; chặn tuyên bố backend compatible và C01 Done. |
| C01-O02 OPEN | Ordering section nói tạo eventId khi business DB commit; worker section và sample/D48 nói chuẩn bị ở worker. audit_events được mô tả immutable. | Worker tạo random bytes32 một lần và persist vào job trong short SQL transaction trước canonical manifest/signing; tạo salt/commitment và deployment context ổn định; không update audit row. Alternative: tạo eventId lúc business commit đòi B05/schema coordination. | Dũng xác nhận timing/storage với C05/C07. Không chặn guards/ABI C03, vì contract không biết thời điểm SQL; chặn freeze worker protocol và end-to-end proof. |
| C01-O03 OPEN | `domainV1` còn có 2 type alternatives; canonical manifest chưa khóa actorRef/null/unknown field/array details. | bytes32 domain = Keccak-256(UTF8("AIDBRIDGE_EVIDENCE_V1")); abi.encode types `[bytes32,uint256,address,bytes32,bytes32,bytes32,bytes32]`. Giữ scheme nguồn; canonical profile cụ thể trong golden input. ActorRef random opaque bytes32 private; không thay bằng email/UUID. | Dũng + Khoa review trước worker/golden-vector freeze. C03 có thể nhận commitment như opaque nonzero bytes32; chưa xác nhận Java/TS interoperability. |
| C01-O04 OPEN | `getEventRecord` chưa khóa outputs. D48 cho chọn full-envelope digest hoặc chỉ commitment; full-envelope ví dụ chưa bind milestone operation. | Dùng flat outputs như mục 3. Digest đề xuất = Keccak-256(abi.encode(bytes4 operation,bytes32 orgRef,bytes32 caseRef,uint64 chainSequence,bytes32 evidenceCommitment,uint16 schemaVersion)); operation là selector của function. Alternative chỉ commitment không phát hiện cùng eventId/commitment nhưng khác case/sequence. Full envelope không operation có nguy cơ nhầm type giữa milestones về sau. | Dũng + Khoa xác nhận protocol trước freeze C03/read adapter; C06 phải bind operation ở tất cả functions. Đây là đề xuất bổ sung so với công thức D48, chưa là quyết định chính thức. Không triển khai ngầm. |
| C01-O05 OPEN | Sample mô tả UnauthorizedRelayer/OrganizationNotActive nhưng chưa định thứ tự lỗi cho caller/org không hợp lệ; zero/schema/error signatures chưa có compiled ABI. | Thứ tự gợi ý: organization/caller authorization (không lookup event/case) -> validate refs/schema -> duplicate digest -> ownership/sequence/transition -> write+emit. Org inactive và caller mismatch đều fail-closed; exact error tên/args chờ implementation. Duplicate khác digest phải được phân biệt trước guard CaseAlreadyVerified khi input hợp lệ. | Dũng/Khoa thống nhất negative test assertions và error ABI trong C03. Chưa freeze tên lỗi ở verifier/backend. |

Không có thay đổi sản phẩm, key custody hoặc phạm vi MVP được thực hiện. Các defaults là đầu vào review; chỉ cần con người tham gia khi cross-owner review không giải quyết được xung đột, hoặc đề xuất đổi security/trust boundary. Giữ C01 In Review đến khi B/F review và các protocol blockers tương ứng được giải quyết.

## 5. ID timing, persistence và replay expectations

Đề xuất flow C01-O02:

1. Business transaction ghi approval/snapshot + audit UUID/event_key + pending job; response VERIFIED/PENDING_ON_CHAIN. Code này chưa có trong baseline đã review; không coi sample là code.
2. Worker claim job; prepare/persist logical eventId, random public refs, chainSequence, schemaVersion, deployment context, exact manifest và encrypted salt/commitment. Không ký trước khi dữ liệu ổn định được commit. Restart trước preparation commit có thể retry prepare vì chưa có signature/broadcast; sau commit phải tái dùng persisted values.
3. Prepare signed transaction; persist signed bytes + locally calculated txHash + signer/nonce trước broadcast. RPC ngoài SQL transaction.
4. Same eventId/same digest -> contract revert `EventAlreadyRecorded` (error name draft). Worker tìm original successful tx/log; xác thực đủ context rồi sửa projection, không dùng receipt của duplicate revert làm proof.
5. Same eventId/different digest -> `EventIdConflict` (draft), không ghi đè record, dừng auto-resign/new-ID loop và báo integrity mismatch. Caller không có quyền vẫn fail authorization trước lookup.

Sequence phải được cấp/reserve nhất quán theo case ở DB; không suy sequence từ số audit rows. D48 đề cập UNIQUE(aid_case_id,chain_sequence) ở blockchain_jobs nhưng list cột bổ sung chưa có aid_case_id: Dũng cần kiểm tra khóa trực tiếp hoặc schema cơ chế khác trong forward migration tương lai; không viết V9 ở C01. SQL BIGINT/SMALLINT có miền signed khác ABI uint64/uint16; validate/limit miền chung hoặc chọn schema phù hợp trước worker, không truncate/overflow.

## 6. Fixtures và golden-vector handoff

Reviewed sample giữ Rice 20 kg + Drinking Water 4 box, IDs của fixture Dũng; không thêm feature hàng hóa. `offChainOnly` chứa fictional private snapshot để trace nguồn, không phải API/public data. Never serialize cả bundle làm HTTP response hoặc calldata. Chỉ `publicEnvelope.arguments` được dùng encode function.

Hai loại dữ liệu cố ý tách biệt:

- **ABI encoding sample:** commitment `0x40` lặp 32 bytes là synthetic nonzero input; không chứng minh hash evidence. Fixed timestamp chỉ để decode. Expected encoded argument/data bytes đã tính được bằng primitive ABI word layout, chưa là log được emit.
- **Golden input:** manifest có exact canonical UTF-8/hex và SHA-256 đã tính. Evidence digests `11...`/`22...` được giữ từ nguồn và là synthetic metadata; không có file bytes để xác minh evidence hashes. Salt `0x60...` công khai chỉ dành cho test, không runtime secret. Không đưa fixed salt/refs này vào production/demo provisioning.

`expected.domainV1/evidenceCommitment/payloadDigest` để null và `NOT_RUN`, thay vì điền giá trị giả. C03 dùng existing C02 ethers tính candidate outputs, sau đó Java/Web3j phải tái tính độc lập và khớp bytes trước khi golden vector được chấp thuận. Checker `--ethers` chỉ in candidate kết quả, không sửa fixture thành accepted.

Canonical profile đề xuất: keys ASCII sort recursively, NFC trước snapshot, UTF-8 không BOM, no whitespace ngoài strings, integers decimal/no floats, timestamps UTC fixed milliseconds; goods/evidence arrays sort theo stable ID, reject duplicate IDs/unknown fields. Baseline fixture chỉ ASCII: phải thêm Unicode decomposed/reordered nested keys/arrays và boundary integer tests trong Java/TS implementation, không tuyên bố profile tổng quát đã được test từ một fixture.

## 7. Kiểm tra, kết quả và bước Dũng tiếp tục

Các lệnh đã chạy trong phiên review:

```text
git status --short --branch
git branch -avv
git fetch origin
git diff --stat
git diff --cached --stat
git ls-tree -r --name-only origin/dung
node docs/fixtures/c01/verify-c01.mjs
git diff --check
```

`git status` ban đầu lỗi sandbox; chạy ngoài sandbox thành công, working tree sạch trước sửa. Fetch thành công; không merge. Kết quả checker và diff-check được ghi sau bước kiểm tra thực tế ở cuối file. Git commands không có commit/push/PR.

Package install tạm `python -m pip install --target work/c01-deps pycryptodome==3.23.0 --disable-pip-version-check` bị automatic approval review từ chối (scope documentation/review chưa cấp quyền dependency install); không cài và không tìm đường vòng. Dùng Node built-ins/SHA-256 cho kiểm tra tĩnh. Keccak, Java/TypeScript decode, contract compile/tests và Fuji: **Not Run**. Không cần package install để đọc/review bộ này.

Dũng/Codex tiếp tục theo thứ tự:

1. Đối chiếu baseline/OPEN register; bổ sung provenance D43-D45/D48 và phản hồi C01-O01..O05. Không coi user duyệt deliverables là protocol đã được cả nhóm đồng ý.
2. Từ C02 branch/setup đã có, triển khai C03 trong phạm vi được giao; pin full compiler ABI theo interface đã review, thêm registration/revoke/guards/read getters. Không copy SmokeProbe thành business registry mà bỏ org scope.
3. Chạy C01-T01..T13 và T17 khi các functions liên quan có; mỗi revert phải kiểm tra không đổi state/emit event. Test expectations là expected behavior, không phải tests đã pass.
4. Khi existing C02 dependencies có sẵn, chạy `node docs/fixtures/c01/verify-c01.mjs --ethers`; xuất candidate Keccak/selector/log; đối chiếu Java độc lập. Chưa cài deps thì ghi Not Run, không tự xem optional step là done.
5. C05/C07/C08 thực hiện T14..T16 với actual backend/receipt/restart tests; actual Fuji proof thuộc task integration, không có trong bộ draft này.
6. Khoa kiểm tra bản diff và câu trả lời OPEN; người review signing/permissions là B, audit/privacy claims là F theo backlog. Chỉ chuyển Done khi acceptance/review/evidence đã đạt; không sửa ngày/estimate lịch sử để che trễ.

Checklist review của Khoa: mở sample, kiểm tra mapping UUID vs bytes32 và synthetic labels; đọc C01-O01..O05; xác nhận không có application changes; xem kết quả checker/diff. Đủ để duyệt chất lượng handoff, chưa phải chứng nhận contract an toàn hoặc backend đang hoạt động.

### Kết quả đã quan sát sau khi ghi file

| Check | Kết quả thực tế |
|---|---|
| `node --check docs/fixtures/c01/verify-c01.mjs` | PASS syntax (đã chạy trên staging trước khi copy byte-identical) |
| `node docs/fixtures/c01/verify-c01.mjs` | PASS static fixture checks: ABI layout, ID mapping, canonical UTF-8/SHA-256, array IDs, test labels, no fabricated chain proof |
| Negative fixture-checker checks trong thư mục work tạm | PASS: SHA-256 bị sửa và expected log data bị sửa đều bị checker từ chối; không thay fixtures trong repo |
| JSON parsing / Markdown links / code fences / new-file whitespace | PASS; tất cả links nội bộ của review tồn tại |
| `git diff --check` | PASS; Git có cảnh báo LF -> CRLF theo config, không có whitespace error |
| `git status --porcelain=v1 --untracked-files=all` | Đúng 6 file C01 mới + backlog; không có thay đổi application/F02 hoặc staged content |
| C03 contract tests, ethers/Java decode, Keccak, Java/TypeScript golden agreement, Fuji proof | Not Run; 17 test expectations vẫn là specification |

Không có commit/push/PR/merge trong phiên. Trạng thái cuối: bộ deliverables sẵn sàng review, C01 In Review; chưa đủ bằng chứng để đánh dấu Done.

### Cross-owner review và phản hồi chốt từ Dũng (B) - 10/11/2026 (`D49`)

Dũng (Backend Owner) đã đọc tài liệu review `C01`, chạy kiểm chứng `node docs/fixtures/c01/verify-c01.mjs --ethers` (sử dụng `ethers@6.14.0` từ `C02`) và chốt toàn bộ 5 điểm `C01-O01..O05` (ghi nhận tại `D49` trong [`docs/planning/DECISION_LOG.md`](../planning/DECISION_LOG.md)):

1. **`C01-O01` (RESOLVED):** Xác nhận nguồn các quyết định `D43` (`verifications.reviewed_case_version`), `D44` (`CASE_VERIFIED` outbox với `event_key = verificationId`), `D45` (Spring Data `JpaRepository`) và `D48` (Phương án A) trong `DECISION_LOG.md` và `B01_BASELINE.md`.
2. **`C01-O02` (RESOLVED):** Giữ `audit_events` hoàn toàn bất biến (`audit_events_immutable` trigger). Worker tạo `logical_event_id` (`bytes32` ngẫu nhiên) một lần và persist vào `blockchain_jobs` trong transaction ngắn trước khi ký. Khi hoàn thiện schema worker (`V9`/`C05`/`C07`), bổ sung cột `aid_case_id UUID REFERENCES aid_cases(id)` trên `blockchain_jobs` để đặt `UNIQUE (aid_case_id, chain_sequence)` và validate miền dương `chain_sequence >= 1`, `schema_version = 1`.
3. **`C01-O03` (RESOLVED):** Chốt `bytes32 domainV1 = keccak256(utf8("AIDBRIDGE_EVIDENCE_V1"))` (`0x4a5cac579b9b766d3889e71136e028adaa4b8d636ff24d257afcc5803ac49397`) và `abi.encode(bytes32 domainV1, uint256 chainId, address registryAddress, bytes32 caseRef, bytes32 eventId, bytes32 manifestDigestSha256, bytes32 salt)`. Kết quả chạy `node docs/fixtures/c01/verify-c01.mjs --ethers`: `evidenceCommitment = 0x0a00e80664506e74cbc0fa19c4e17cf3fdace8c2d70e5717b08c6804dc5c8882` (sẽ đối chiếu byte-identical với Java tại `C05`).
4. **`C01-O04` (RESOLVED):** Chốt công thức `payloadDigest = keccak256(abi.encode(bytes4 operation, bytes32 orgRef, bytes32 caseRef, uint64 chainSequence, bytes32 evidenceCommitment, uint16 schemaVersion))` với `operation = 0x1bd1fadf` (`recordCaseVerification` selector) -> candidate `payloadDigest = 0xcff6ea47eb121a61875dceef9200e998d5096f69d9fb7e7435ee6b2b8f74b1f8` và `topic0 = 0xc52bfb1c97b1de28337fe01b8bf6dce1267ee4b6f337c271935032cdf0629ea2`. Chốt flat outputs cho `getEventRecord(bytes32 eventId)` và `getCaseSummary(bytes32 caseRef)` đúng như `AidRegistry.abi.draft.json`.
5. **`C01-O05` (RESOLVED):** Chốt thứ tự guard fail-closed trong `AidRegistry.sol` (`C03`): (1) organization active + authorized relayer cho `orgRef` -> (2) non-zero refs/commitment/sequence và `schemaVersion == 1` -> (3) duplicate `eventId` check (`EventAlreadyRecorded` nếu cùng `payloadDigest`, `EventIdConflict` nếu khác `payloadDigest`, kiểm tra trước `CaseAlreadyVerified`) -> (4) case ownership theo `orgRef`, `chainSequence == lastChainSequence + 1` và chưa verified (`CaseAlreadyVerified`) -> (5) state write + emit `CaseVerified`.
