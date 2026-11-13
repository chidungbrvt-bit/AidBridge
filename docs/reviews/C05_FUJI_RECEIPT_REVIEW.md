# C05 — Kiểm chứng receipt và CaseVerified trên Fuji (11/11/2026)

## Kết luận và phạm vi

**Cập nhật12/11, thay thế mô tả checkout ngày11 bên dưới:** worker/adapter đã ở main qua PR10/12; C07 CI PASS. Dũng xác nhận worker tắt, không old-signer obligations; [rotation/funding D54](FUJI_RELAYER_HANDOFF_2026_11_12.md) đã PASS. Chưa chạy job B05 thật trên Fuji trong lượt này: **C05 vẫn PARTIAL**. Không tiếp tục coi thiếu code worker là blocker hiện hành; DB binding/job/runtime proof do Dũng tiếp nhận. Proof ngày11 vẫn là independent smoke, backendJobId=null.

**C05 PARTIAL: bằng chứng contract Fuji PASS; tích hợp B05 BLOCKED.** Phần được giao ngày 11/11 yêu cầu một bằng chứng Fuji cho luồng xác minh hoặc trạng thái chờ/lỗi có lý do để Dũng/Minh tiếp nhận. Đã có bằng chứng CaseVerified thật và ghi rõ lý do chưa ghép được job B05. Không tuyên bố toàn bộ C05 trong backlog hoàn thành: task gốc còn Java adapter, SQL attempt/job effects và audit API. Giữ C05 **Blocked** vì chưa đủ đầu vào tích hợp; chờ review nhóm trước Done. Không đổi trạng thái B05/C03/C04 hoặc task của thành viên khác.

Baseline trước C05: nhánh `khoa`, HEAD `e44423ce215bd7ad6a96663bd5885f311fa9aacc`; source contract C03 `61cc738385e8b374d38996e8a725a9da6fa7f12b`. Không sửa/redeploy contract, ABI D49/schemaVersion 1 không đổi. Người dùng đã cho phép một giao dịch Fuji bằng Relayer hiện có; không dùng Admin ký hoặc thay đổi quyền.

## B05 thực tế và điều kiện chưa đủ

Đọc trực tiếp `VerificationService`, `VerificationOutboxService.enqueueApprovedVerification`, `BlockchainJob`, `BlockchainJobRepository`, migrations V5/V9, D48/D49, API và workflows:

- B05 **có code** ghi approval/audit/job `PENDING` trong SQL transaction; approval trả `PENDING_ON_CHAIN`. Job có random `logical_event_id`, sequence 1, schema 1. Không phải backend hoàn toàn chưa làm outbox.
- B05 có `VerificationApiIT` và `VerificationOutboxIT`; backlog ghi Dũng đã chạy 10 HTTP + 4 outbox tests PASS. Máy Khoa hiện không có PostgreSQL service/listener hoặc DB env hợp lệ để đối chiếu job thật; không suy kết quả máy Dũng thành bằng chứng runtime ở máy này. Worker phía chain là phần C05/C07 của Khoa, phối hợp Dũng về DB/Java/REST; không coi thiếu worker là B05 chưa implement nghiệp vụ.
- Checkout hiện tại chỉ có entity/repository trong package `blockchain`, chưa có adapter/worker/signing/receipt projection hay audit controller triển khai các endpoint T01–T03.
- Terminal không có `DB_URL/DB_USERNAME/DB_PASSWORD` hoặc `AIDBRIDGE_TEST_DATABASE_*`. Không đọc IDE credentials, không provision DB, tạo SQL fixture/job hay tự chạy B05 approval. **Số job PENDING thật trong DB: chưa xác minh**, không kết luận bằng 0. Kết quả DB/HTTP Dũng đã báo trước đây không thay thế bằng chứng job hiện tại.
- Binding UUID organization backend -> `organizations.public_ref` -> orgRef pilot chưa được xác nhận. Tuân thủ [C04 backend handoff](C04_BACKEND_HANDOFF.md): không overwrite public_ref bất biến, không ép UUID thành bytes32, không tự đăng ký ref khác bằng Admin.
- V9 hiện chưa có `blockchain_jobs.aid_case_id`/unique `(aid_case_id,chain_sequence)` theo D49; constraint schema hiện `>=1`, chưa `=1`. Ghi nhận để Dũng/Khoa đối chiếu trong phần schema/worker, không tự sửa migration đã có. Verifier C05 chỉ chấp nhận sequence 1/schema 1.

Do đó giao dịch dưới đây là **INDEPENDENT_CONTRACT_SMOKE_NOT_B05_JOB**, `backendJobId=null`. Không ghi SQL CONFIRMED, không sửa audit bất biến, không gắn hash này vào case/job backend bất kỳ.

## Bằng chứng Fuji thật

| Trường | Expected / actual đã đối chiếu |
|---|---|
| Chain | 43113, xác minh bằng `eth_chainId` và transaction.chainId |
| Contract / destination / log emitter | `0x015D941c679b0c402c8262BE237adAD36cEf6726` |
| Runtime Keccak | `0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad` — khớp artifact C03 và deployment manifest |
| Signer | Relayer `0x559994839f19F40ac704918C3FF1a913AC02dC01`, nonce 0; không phải Admin |
| Transaction | `0xc3b02934afb1f29816836fb22146dbc350202d3b2a9e8dbbb19c2fd2e7e8c00f` |
| Receipt | status 1; block `60893112`; logIndex 0 |
| Block hash | `0x0674c217754aa707b93250deb1b3daa6b6e09accba5f00483ade77db66d3e421` |
| Event | `CaseVerified(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64)` |
| topic0 | `0xc52bfb1c97b1de28337fe01b8bf6dce1267ee4b6f337c271935032cdf0629ea2` |
| orgRef | `0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46` |
| caseRef | `0x9667486a2b4613986fab8955798abe2ac28f337549e622c29d2058d4c6b64494` |
| eventId | `0x13c87e35c29891a05f4daf06fca618b2647cb486fe4edc7a7ec336f6d84addc9` |
| chainSequence / schemaVersion | 1 / 1 |
| evidenceCommitment | `0x6d48585ce429c7129e2ef8f24ef1bf84fdd440d7123b09f0a31ab907fbb8cac8` |
| timestamp | 1794403480 — khớp block timestamp |
| payloadDigest | `0x9c78761c9a4fe690ddbd3b2bce671f6a78f0bba06931ab5fea500c820548c66f` — khớp công thức D49 và getter |
| Gas / fee | 257473 gas × 1 gwei = **0.000257473 test AVAX**; value=0 |
| Relayer balance sau giao dịch | 0.149700527 test AVAX |

[Xem giao dịch trên Fuji explorer](https://testnet.snowtrace.io/tx/0xc3b02934afb1f29816836fb22146dbc350202d3b2a9e8dbbb19c2fd2e7e8c00f).

Bằng chứng máy đọc: [fuji-case-verified.proof.json](../fixtures/c05/fuji-case-verified.proof.json). `expected` lấy từ [attempt chuẩn bị trước gửi](../../contracts/deployment/c05-attempt.public.json), không suy ngược expected từ event sau mining. `state=PREPARED` trong file attempt là snapshot trước gửi, không phải trạng thái hiện tại của receipt/job SQL. Proof chứa actual, topics/data gốc, decoded fields, gas và readback.

Verifier kiểm tra chain, hash, from/to, nonce/value, calldata sáu tham số, receipt status, block number/hash canonical, đúng một expected event từ registry, 4 topics/128 data bytes, từng field và timestamp, `removed=false`. Đọc `getEventRecord`/`getCaseSummary` tại đúng block receipt: event exists, operation `0x1bd1fadf`, digest đúng, verified=true, disputes=0, sequence=1. Đây là kiểm tra receipt + canonical block readback; không tuyên bố đã kiểm chứng độc lập consensus finality hoặc dùng số block tùy ý làm quy tắc finality.

Dữ liệu là manifest giả lập C01 với refs mới, timestamp mới, salt CSPRNG mới và context contract Fuji thật. SHA-256 canonical manifest và commitment theo D49; không tái sử dụng fixed salt của golden vector. Evidence metadata C01 là dữ liệu tổng hợp, **không phải hash file bằng chứng của một hồ sơ B05 đã duyệt**. Contract không chứng minh nội dung hộ gia đình hoặc tính độc lập của reviewer backend.

## Kiểm thử thực tế

Các lệnh dưới đây chạy từ repo root, trừ lệnh có ghi rõ `contracts/`:

| Kiểm tra | Kết quả |
|---|---|
| `node node_modules/hardhat/dist/src/cli.js compile` trong contracts/ | PASS, No contracts to compile; source/artifact pins được kiểm tra lại bởi runner |
| `node node_modules/hardhat/dist/src/cli.js test` trong contracts/ | **61/61 PASS**, gồm authorization, lifecycle, guard ordering, replay/sequence, privacy, ABI layout và C04 runner |
| `node --test contracts/review-tests/c05-proof.test.mjs` | **46/46 PASS**, gồm receipt null/reverted, sai chain/from/to/hash/nonce/calldata, event thiếu/trùng/sai fields, removed/reorg context, raw-log regression và getter mismatch |
| `node docs/fixtures/c01/verify-c01.mjs --ethers` | PASS static/canonical UTF-8/SHA-256/Keccak/ABI encode-decode. Output vẫn có nhãn candidate lịch sử; không coi đó là Java agreement |
| `node contracts/scripts/verify-fuji.mjs` | PASS read-only: deployment/runtime/org/Admin/Relayer vẫn đúng |
| C05 actual Fuji receipt/event/readback | PASS; duy nhất một transaction nonce 0, hash ở trên |
| Runner read-only rerun / nonce không đổi | PASS, xem kết quả nghiệm thu cuối lượt |
| Syntax/JSON/reference checks và `git diff --check` | PASS sau rà soát cuối lượt |
| B05 DB/HTTP job -> worker -> SQL confirmation/API/UI | **NOT RUN / BLOCKED** vì chưa có DB access/job binding/worker; không dựng luồng giả |
| Java/TypeScript golden-vector agreement; DB crash/concurrency/attempt persistence | **NOT RUN**; Node test không thay bằng chứng Java/PostgreSQL |

Lần compile đầu trong môi trường hạn chế timeout compiler-cache lock; chạy lại bằng cache Windows hiện có thành công, không nâng/cài dependencies. Lần đọc proof đầu bị từ chối vì ethers v6 bỏ `removed` khỏi receipt Log dù JSON-RPC có `false`. Đã sửa normalizer đối chiếu chính xác raw log và thêm 3 regression tests; không nới kiểm tra sang mặc định false. Giao dịch đã mined không bị gửi lại để sửa lỗi đọc proof.

## Chạy lại và lưu trữ an toàn

```powershell
cd D:\Project\AidBridge
node contracts/scripts/c05-pilot.mjs
node --test contracts/review-tests/c05-proof.test.mjs
```

Mặc định chỉ đọc, không unlock key, không ghi file và không broadcast. `--broadcast-independent` là chế độ vận hành có side effect, **không dùng làm lệnh test thông thường**: chỉ phục hồi cùng attempt đã lưu; không tạo case/event/nonce mới nếu attempt tồn tại. Trước broadcast lưu signed bytes bằng DPAPI rồi lưu hash/request công khai; timeout phải reconcile hash cũ. Nếu journal riêng bị thiếu/orphan hoặc nonce xung đột, dừng, không xóa journal rồi chạy lại. Chưa có fault-injection/crash tests cho runner này; không xem journal file là worker SQL production.

`--save-proof` chỉ đọc chain và ghi lại bằng chứng công khai của attempt hiện có; không unlock signer hoặc broadcast. File attempt PREPARED không bị đổi khi chạy lại.

- `contracts/deployment/c05-attempt.private.clixml`: **mới tạo trong repo, bị Git ignore**, mã hóa DPAPI CurrentUser, chứa signed transaction, manifest giả lập và salt; không chứa bản sao wallet private key. Giữ cho recovery/kiểm tra riêng có kiểm soát, không commit/upload/đọc ra log.
- Hai khóa ví gốc tại `%LOCALAPPDATA%/AidBridge/fuji-wallets.clixml` không bị sửa. Thêm `unlockRelayer()` chỉ trả Relayer signer; không tạo Admin Wallet trong C05. Không tạo ví mới hoặc dữ liệu ví ngoài repo.
- Không tạo output/source ngoài repo. Hardhat dùng cache Windows sẵn có; lần chạy hạn chế có truy cập compiler-cache path riêng của môi trường. Không dọn cache hoặc browser/Console Wallet.

## Bàn giao và điều kiện nghiệm thu tích hợp

**Dũng/Codex:** xác nhận đúng DB/schema và một approval B05 thật. Cung cấp job/audit/verification IDs, snapshot reviewed revision/evidence, org/case public refs và deployment context qua kênh nội bộ phù hợp; không đưa secret/salt vào PR public. Đối chiếu binding theo C04, giữ public_ref hiện có nếu khác pilot và phối hợp đăng ký ref bằng quy trình được phê duyệt. Tiếp tục phần Java/DB/REST cùng Khoa: constraint D49, chuẩn bị immutable manifest/commitment, encrypted attempt+nonce/hash trước broadcast, không chờ RPC trong transaction/HTTP, receipt projection và retry/reconcile. Không sửa audit_events bất biến hoặc tạo job từ proof độc lập này.

**Khoa/Codex:** khi có job hợp lệ và org được authorize, sử dụng envelope từ persisted job; xác minh đúng eventId/org/case/sequence/schema/commitment, original tx nếu replay, rồi lưu receipt và cập nhật job/projection trong transaction ngắn. Node verifier là bộ đối chiếu tham khảo, không phải implementation Web3j đã tích hợp. Bổ sung tests IT01/03/13, crash/DB effects và Java parity trước nghiệm thu full C05.

**Minh/Codex:** có thể dùng file proof để review decoding và link explorer thật; không gắn proof vào case/backend job bất kỳ. API audit hiện chưa được tích hợp: `PENDING_ON_CHAIN`/`FAILED_ON_CHAIN`/`CONFIRMED_ON_CHAIN` vẫn phải dựa trên projection API thật khi có, không đổi trạng thái case SQL vì giao dịch độc lập. Receipt chưa có là pending; reverted mới failed; sai event/context không được confirmed. Tên field/blockchain DTO tiếp tục theo API specification, không thêm endpoint hoặc trạng thái mới từ tài liệu này.

OPEN: DB job availability, organization binding, worker/Java signer+salt custody và API projection chưa xác minh/triển khai trong lượt này. Không có blocker contract C03/C04 mới được phát hiện. Actual effort/human review chưa có số liệu; không thay estimate/deadline gốc.
