# Task #5: evidence, inventory DB/queue, phương án cutover và runbook pilot C06 (18/11/2026)

Owner: Dũng. Trạng thái: **In Review**. Tài liệu này chỉ ghi nhận, kiểm kê và đề xuất. Chưa deploy C06, chưa sửa DB, chưa đổi cấu hình, chưa bật worker, chưa gửi giao dịch Fuji.

Nguồn yêu cầu: [C10_LOCAL_GATE_REVIEW_2026_11_17.md](C10_LOCAL_GATE_REVIEW_2026_11_17.md) (gate PREPARED + wrong-chain) và mục "Chốt acceptance Task4" của [C06_REDEPLOY_PREPARATION_2026_11_16.md](C06_REDEPLOY_PREPARATION_2026_11_16.md) (điều kiện nghiệm thu Task5), cả hai trên `origin/khoa`.

| Hạng mục | Kết quả |
|---|---|
| 1. Evidence PREPARED + wrong-chain | **PASS local** trên commit `5df5aac`, XML đã kiểm tra secrets |
| 2. Inventory DB/queue | **Xong**, đọc lại thô từ hai DB và Fuji RPC; 4 phát hiện cần quyết định |
| 3. Phương án DB/binding/cutover | **Đề xuất**, chờ nhóm duyệt; registry C06 để placeholder |
| 4. Recovery và pilot task #6 | **Đề xuất**, chưa chạy |

## 1. Evidence PREPARED + wrong-chain

**Testcase:** `CaseVerifiedWorkerIT.keepsPreparedCaseVerificationWhileRpcReportsWrongChain`.

| Tiêu chí (Khoa 17/11) | Assertion trong test |
|---|---|
| Có attempt PREPARED đã lưu | Worker bị chặn ngay trước broadcast; DB có job `PROCESSING`, attempt `PREPARED` |
| RPC sai chain thì 0 broadcast | Đổi RPC sang 43113 (worker cấu hình 31337): kết quả `RETRY_SCHEDULED / INVALID_CHAIN_ID`, `broadcasts` rỗng |
| 0 ký lại, 0 attempt mới | `beforeSign` ném lỗi nếu bị gọi; `pendingNonce` không đổi; vẫn 1 attempt |
| Giữ nonce, hash, bytes, attempt | Cùng `attempt_id`, `event_id`, `nonce`, `tx_hash`; ciphertext so sánh từng byte; `lease_until` null, `next_retry_at` có giá trị |
| Chain đúng thì gửi lại đúng bytes | Trả RPC về 31337: `REBROADCAST`, 1 broadcast bằng đúng raw tx gốc, vẫn 1 attempt, job và attempt `SUBMITTED` |

Code tương ứng: `CaseVerifiedWorker` gọi `readChainId()` trước khi chọn nhánh rebroadcast hay ký mới (commit `406d5d8`).

**Provenance:**

| Mục | Giá trị |
|---|---|
| Commit đã test | `5df5aacaecfda1a6a14d4eb7bd1b354c70de9faa` (cha `406d5d8`), tree `855c9482bc24196063c80b9e65a58690223bf5ff` |
| Thời điểm | Commit 18/11 00:40:59 +07 (`git log`); test chạy sau commit này |
| Môi trường | Windows 11, Java 26.0.1 (Oracle), Maven wrapper, PostgreSQL `aidbridge_test` với schema IT riêng, chain giả lập 31337 trong `FakeBlockchainAdapter` |
| Lệnh | `.\mvnw.cmd -B -ntp "-Dtest=NONE" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=CaseVerifiedWorkerIT" verify` |
| Kết quả | `TEST-com.aidbridge.casefile.CaseVerifiedWorkerIT.xml`: **34 tests, 0 failures, 0 errors, 0 skipped**; testcase mục tiêu PASS (1.108 s); `doesNotPrepareCaseVerificationWhileRpcReportsWrongChain` PASS |
| SHA-256 XML | `910d696c827ec329a5d6d76ce7cd9b804d6d3d87beb5c66d7a787868d3dc0393` |
| Kiểm tra secrets | XML có 3 khối system-out. Không có property nào chứa `AIDBRIDGE_TEST_DATABASE_*`, `DB_*`, `BLOCKCHAIN_*`; không có chuỗi `private key`, `secret key`, `password`; không có chuỗi hex 64 ký tự (key, hash, raw tx) nào |

Giới hạn: đây là regression local với RPC giả lập, không phải bằng chứng Fuji. XML gốc giữ trên máy Dũng, đính kèm PR khi reviewer cần.

## 2. Inventory DB/queue

**Phương pháp:** hai câu SQL chỉ `SELECT` chạy trong pgAdmin trên từng DB, cộng một lượt đọc JSON-RPC công khai trên Fuji, không dùng key. Không đọc payload, manifest, salt, ciphertext hay tên người. Kết quả gốc nằm ở [evidence/task5](evidence/task5/).

### 2.1 Trạng thái môi trường (đọc 18/11 00:48 +07)

| Mục | `aidbridge_demo` (lane C03 cũ) | `aidbridge_demo_v16` (staging) |
|---|---|---|
| Flyway cao nhất | **V15** (`-77633396`); chưa có V16–V18 | V18; V16 checksum `-569862596` (đúng bản đã commit) |
| Jobs | 2 `CONFIRMED`, 3 `PENDING` (chain 43113) | 5 `PENDING` (chain 43113) |
| Attempt mở | 0 | 0 (không có attempt nào) |
| Nonce signer trong DB | `0x95a9…1f55` max nonce 1 | Không có |
| Session khác ngoài pgAdmin | Không | Không |
| Advisory lock | 0 | 0 |

Kết luận: lúc đọc không có backend hay worker nào kết nối vào hai DB. B4: `netstat -ano` lúc 01:11 +07 không có tiến trình LISTENING ở cổng 8080, 8081, 8082.

**Fuji (00:49 +07):** `chainId` = 43113; relayer `0x95a9564899849CF566DE5713205C4B747F361f55` có nonce `latest` = `pending` = 2, khớp max nonce 1 trong `aidbridge_demo`: không có giao dịch đang bay hay giao dịch ngoài journal.

### 2.2 Ledger `aidbridge_demo`

Org `0x2395bfe7…0a46` (`APPROVED`, relayer `0x95a9…1f55`).

| Seq | Case | Loại | Job | Preparation | Attempt | Nhận xét |
|---|---|---|---|---|---|---|
| 1 | `0570f60f…` | CASE_VERIFIED | CONFIRMED | có | C03, nonce 0, block 60959263, `0x46514001…566a` | Proof B05, giữ nguyên |
| 1 | `e31ce4d6…` | CASE_VERIFIED | CONFIRMED | có | C03, nonce 1, block 60959653, `0x97104a33…f98e` | Proof B05, giữ nguyên |
| 1 | `e9bfb852…` | CASE_VERIFIED | PENDING | **không** | 0 | Chưa gắn registry nào |
| 2 | `e9bfb852…` | AID_PLEDGED | PENDING | không | 0 | C03 không có hàm hàng hóa |
| 3 | `e9bfb852…` | GOODS_RECEIVED | PENDING | không | 0 | C03 không có hàm hàng hóa |

Ba job PENDING đều thuộc case smoke D01 ngày 14/11, `retry_count` 0, không lease, không `last_error`.

### 2.3 Ledger `aidbridge_demo_v16`

Case `85e124e4…`, `caseRef` `0x87db7e09…de2e`, org `0x26b3e1fe…299f` (`APPROVED`, **`relayer_address` = NULL**).

| Seq | Loại | Job | Preparation | Attempt | Worker hiện tại có xử lý được không |
|---|---|---|---|---|---|
| 1 | CASE_VERIFIED | PENDING | không | 0 | Có, trên mọi chain |
| 2 | AID_PLEDGED | PENDING | không | 0 | Chỉ khi chain 31337 |
| 3 | GOODS_RECEIVED | PENDING | không | 0 | Chỉ khi chain 31337 |
| 4 | DELIVERY_RECORDED | PENDING | không | 0 | Chỉ khi chain 31337 |
| 5 | DELIVERY_CONFIRMED | PENDING | không | 0 | **Không**: worker chưa có operation này |

Không có `COMMITMENT_REDUCED` trong case này. Sequence 1–5 liên tục, mỗi job có predecessor đúng sequence trước.

### 2.4 Phát hiện

| ID | Phát hiện | Căn cứ | Hệ quả |
|---|---|---|---|
| T5-1 | **orgRef của plan C06 khác org staging.** `c06-fuji-plan.public.json` dùng `orgRef` `0x2395…` (org của `aidbridge_demo`), còn 5 job staging thuộc org `0x26b3…`. `public_ref` không được UPDATE | Plan trên `origin/khoa`, B1 staging | Setup C06 theo plan hiện tại thì job staging không có org hợp lệ trên registry mới. Cần chọn: đăng ký `0x26b3…` trên C06 (sửa plan), hoặc chạy pilot trên case mới thuộc org `0x2395…` |
| T5-2 | **`DELIVERY_CONFIRMED` không có trong worker.** `AidRegistryAbi.Operation` chỉ có 5 operation; claim query không lấy loại này. `ConfirmationService` (B10) vẫn tạo job | Source `dung` | Seq 5 staging PENDING mãi. Milestone nào sau nó trong cùng case sẽ bị chặn bởi kiểm tra predecessor |
| T5-3 | **Hàng hóa chỉ được claim khi chain 31337.** | `BlockchainJobRepository` (claim query) | Trên Fuji hiện tại chỉ CASE_VERIFIED chạy được. Seq 2–4 cần thay đổi code được review riêng trước pilot hàng hóa |
| T5-4 | **Khóa signer chỉ có tác dụng trong một database.** Advisory lock của PostgreSQL theo từng DB; hai backend trên hai DB dùng chung relayer sẽ không chặn nhau | `SignerSessionLock`, `pg_locks` | Toàn hệ thống chỉ được có **một** worker bật cho relayer `0x95a9…1f55`, nếu không sẽ trùng nonce |
| T5-5 | `aidbridge_demo` đang ở V15. Chạy backend hiện tại trên DB này sẽ tự migrate V16–V18 (V16 copy `deliveryRef`) | B2 | Không khởi động backend mới trên `aidbridge_demo` khi chưa có quyết định riêng |

Thêm một điểm cho cả hai DB: preparation (`evidence_commitment`) được tính từ chain **và** registry. Job nào đã chuẩn bị là đã khóa vào registry lúc đó. Hiện **không có job PENDING nào đã chuẩn bị**, nên chưa job nào bị khóa vào C03 hay C06.

### 2.5 Kết luận từng nhóm job

| Nhóm | Kết luận | Đề xuất (chờ duyệt) |
|---|---|---|
| 2 CONFIRMED C03 (`aidbridge_demo`) | Đủ proof, nonce 0–1 khớp Fuji | Giữ nguyên DB, proof, manifest, AES key; chỉ đọc |
| 3 PENDING `e9bfb852` (`aidbridge_demo`) | Chưa chuẩn bị, chưa ký | **Park:** giữ nguyên, worker trên DB này luôn tắt. Không đổi status, sequence, registry; không xóa |
| 5 PENDING `85e124e4` (staging) | Chưa chuẩn bị, chưa ký, đúng thứ tự | Dùng được cho C06 sau cutover, **với điều kiện** xử lý T5-1 (org) và chấp nhận chỉ seq 1 chạy được trên Fuji cho tới khi T5-3 có thay đổi được duyệt; seq 5 vướng T5-2 |

Lý do không "drain" seq 1 của `e9bfb852` qua C03: nếu seq 1 xác nhận trên C03 thì seq 2–3 không bao giờ chạy được (C03 không có hàm hàng hóa, còn C06 từ chối predecessor khác deployment `PREDECESSOR_DEPLOYMENT_MISMATCH`). Case sẽ nằm lửng ở hai registry. Rebind sang C06 cũng không được phép.

## 3. Phương án DB/binding/cutover (đề xuất)

**Nguyên tắc:** một relayer, một worker; mỗi DB chỉ một registry; không sửa lịch sử; mọi thay đổi DB/cấu hình phải có phê duyệt riêng và bản sao lưu trước.

### 3.1 Cách ly queue cũ (`aidbridge_demo`)

1. Sao lưu chỉ đọc trước mọi bước: `pg_dump -Fc aidbridge_demo` ra file có hash SHA-256, lưu ngoài repo.
2. Không khởi động backend nào với `BLOCKCHAIN_ENABLED=true` trên DB này. Nếu cần đọc API lịch sử thì chạy với `BLOCKCHAIN_ENABLED=false` và **chỉ** sau khi nhóm quyết định chuyện migrate V16–V18 (T5-5).
3. Giữ nguyên 3 job PENDING, 2 proof C03, `contracts/deployment/fuji-*.json` của C03 và AES key của DB này.
4. Kiểm tra định kỳ trong cửa sổ cutover: B2 trên `aidbridge_demo` không có session backend, không có attempt mới, max nonce vẫn 1.

### 3.2 Cấu hình staging (`aidbridge_demo_v16`, port 8082)

| Biến | Giá trị |
|---|---|
| `BLOCKCHAIN_ENABLED` | `false` cho tới bước 3.4 |
| `BLOCKCHAIN_CHAIN_ID` | `43113` |
| `BLOCKCHAIN_RPC_URL` | Endpoint HTTPS Fuji đã chọn |
| `BLOCKCHAIN_REGISTRY_ADDRESS` | `<C06_REGISTRY_ADDRESS>`: chỉ điền từ readback post-deploy đã kiểm, không dùng địa chỉ dự đoán hay Hardhat `0x6101…` |
| `BLOCKCHAIN_RELAYER_PRIVATE_KEY` | Key D54 nạp từ file ngoài repo, không in ra |
| `BLOCKCHAIN_SECRET_KEY` | AES key riêng cho staging, sao lưu trước khi job đầu tiên được chuẩn bị |
| `DB_URL` | `jdbc:postgresql://localhost:5432/aidbridge_demo_v16` |

Binding DB (thay đổi dữ liệu, **chỉ làm sau khi** org đã đăng ký trên C06 và có phê duyệt). Câu lệnh tự từ chối nếu org đã gắn relayer:

```sql
UPDATE organizations
   SET relayer_address = lower('0x95a9564899849CF566DE5713205C4B747F361f55')
 WHERE public_ref = decode('<ORG_REF_DA_DANG_KY_TREN_C06_KHONG_0x>', 'hex')
   AND relayer_address IS NULL;   -- phai tra ve 1 dong
```

### 3.3 Kiểm tra trước khi bật worker (tất cả phải đạt; một mục sai thì dừng)

| # | Kiểm tra | Cách kiểm | Đạt khi |
|---|---|---|---|
| G1 | Deploy C06 đã được phê duyệt và readback xong | `c06-fuji-deployment.public.json`, `post-deploy` | Receipt status 1, runtime keccak `0x525314d1…011e` |
| G2 | Org staging đã đăng ký trên C06 | `getOrganization(orgRef)`, `hasRole(RELAYER_ROLE, relayer)` | Active, relayer đúng `0x95a9…1f55` (xử lý T5-1 trước) |
| G3 | RPC đúng chain | `eth_chainId` | 43113 |
| G4 | Không có giao dịch đang bay của relayer | `eth_getTransactionCount` latest và pending | Bằng nhau, và không nhỏ hơn max nonce trong mọi DB cộng 1 |
| G5 | Chỉ một worker | B2 trên **cả hai** DB, cổng 8081/8082 | Không backend nào khác đang chạy; `aidbridge_demo` không có session |
| G6 | Queue staging sạch | B1 staging | Không job đã chuẩn bị, không attempt, sequence liên tục |
| G7 | Schema đúng | Flyway staging | V18, checksum khớp repo |
| G8 | Combined regression | Mục 1 | PASS trên commit được deploy |
| G9 | Sao lưu | `pg_dump` staging, AES key, deployment journal | Có file và hash, khôi phục thử được |
| G10 | Số dư relayer | `eth_getBalance` | Đủ cho số giao dịch dự kiến với gas cap |
| G11 | Phê duyệt cutover | Văn bản của nhóm | Riêng biệt với phê duyệt deploy |

### 3.4 Bật worker

1. Ghi lại B1, B2, B3 ngay trước khi bật (mốc T0).
2. Khởi động staging với `BLOCKCHAIN_ENABLED=true`, quan sát job đầu tiên đến `CONFIRMED`.
3. Đối chiếu ngay theo mục 4.3, rồi mới để worker chạy tiếp.

**Dừng và giữ worker tắt nếu:** nonce không khớp, có attempt lạ, chain sai, registry readback khác cấu hình, có job đã chuẩn bị trước T0, hoặc hai backend cùng chạy.

## 4. Runbook recovery và checklist pilot (đề xuất)

### 4.1 Điểm dừng

| Mốc | Trạng thái | Dừng an toàn bằng | Sau mốc này |
|---|---|---|---|
| S0 | Chưa bật worker | Không cần làm gì | Có thể đổi cấu hình |
| S1 | Job đã chuẩn bị, chưa có attempt | Tắt worker | Job đã khóa vào registry C06; không được chuyển registry |
| S2 | Attempt `PREPARED` (đã ký, có thể đã gửi) | Tắt worker | Phải giữ bytes, nonce, hash; worker lần sau chỉ gửi lại đúng bytes |
| S3 | Attempt `SUBMITTED` | Tắt worker | Chỉ chờ receipt; không ký lại |
| S4 | `CONFIRMED` | Bình thường | Không có rollback trên blockchain |

**Đổi cấu hình về cũ không đủ an toàn sau S1.** Nếu đổi `BLOCKCHAIN_REGISTRY_ADDRESS` về C03, worker sẽ từ chối attempt đã ký cho C06 (test `recoveredAttemptCannotBeSentUnderAnotherRegistry`), job nằm kẹt. Nếu khôi phục DB từ bản sao lưu trước S2, sẽ mất journal của một giao dịch có thể đã lên chain. Vì vậy sau S1 chỉ có một hướng: giữ cấu hình C06 và đối soát.

### 4.2 Xử lý giao dịch đang bay

| Tình huống | Hành động | Không được làm |
|---|---|---|
| Worker dừng sau khi ký, trước khi gửi (PREPARED) | Khởi động lại đúng cấu hình; worker kiểm chain rồi gửi lại đúng bytes | Xóa attempt, đổi nonce |
| RPC báo sai chain | Worker tự hoãn (`INVALID_CHAIN_ID`); sửa RPC rồi chờ retry | Chuyển sang RPC khác chưa kiểm chain |
| RPC lỗi hoặc timeout khi gửi | Coi là chưa rõ kết quả; worker chờ receipt rồi gửi lại cùng bytes | Đánh dấu FAILED, ký giao dịch mới |
| Không thấy receipt quá lâu (`RECEIPT_PENDING_OPERATOR_ATTENTION`, job vẫn `SUBMITTED`) | Tra hash trên Snowtrace; so nonce relayer; chờ hoặc gửi lại cùng bytes | Thay nonce bằng giao dịch khác |
| Receipt status 0 (`TX_REVERTED`) | Job FAILED; ghi lại; tìm nguyên nhân (org, quyền, sequence) | Tạo lại job, đổi sequence |
| Receipt không khớp event hoặc ref (mã lỗi có tiền tố `RECEIPT_`) | Job FAILED; giữ hash; báo Khoa | Sửa tay DB thành CONFIRMED |
| Nonce trên chain lớn hơn journal | Dừng toàn bộ: có giao dịch ngoài hệ thống | Bật lại worker trước khi đối soát |

### 4.3 Kịch bản pilot task #6

Điều kiện mở: G1–G11 đạt và có phê duyệt cutover.

1. **Phạm vi với code hiện tại:** chỉ seq 1 `CASE_VERIFIED` của case staging `85e124e4` lên C06. Seq 2–4 chỉ chạy được khi thay đổi bật hàng hóa trên 43113 (T5-3) được review và merge; seq 5 cần làm T5-2. Nếu thiếu, pilot dừng ở seq 1 và ghi rõ.
2. Mốc T0: chụp B1, B2, B3.
3. Bật worker. Theo dõi job seq 1: `PENDING → PROCESSING → SUBMITTED → CONFIRMED`.
4. Tắt worker ngay sau khi seq 1 `CONFIRMED`. Đối chiếu bằng chứng (mục 4.4).
5. Nếu đã có thay đổi T5-3 được duyệt, lặp từng job seq 2, 3, 4; mỗi job đối chiếu xong mới chạy tiếp.
6. Gọi `GET /api/campaigns/{id}/audit-trail` trên staging để thấy `CONFIRMED_ON_CHAIN` và link Snowtrace.

### 4.4 Bằng chứng cần thu (không có secret)

| Bằng chứng | Nguồn |
|---|---|
| B1/B2 trước và sau mỗi job | pgAdmin CSV |
| Nonce relayer latest/pending trước và sau | B3 |
| Mỗi giao dịch: hash, block, logIndex, registry, eventId | DB attempt + Snowtrace |
| Receipt khớp: status 1, `to` = registry C06, event đúng operation, topic `orgRef`/`caseRef`/`eventId` khớp DB | `eth_getTransactionReceipt` + so DB |
| `getEventRecord` / `getCaseSummary` trên C06 | Đọc contract |
| Response T01/T02 hiển thị đúng trạng thái và link | API staging |
| Lệnh, thời điểm, commit backend đang chạy | Ghi tay vào báo cáo |

## 5. Quyết định cần nhóm duyệt

1. Disposition 3 job PENDING `e9bfb852` ở `aidbridge_demo`: đề xuất **park**.
2. T5-1: đăng ký orgRef staging `0x26b3…` trên C06 (Khoa sửa plan) hay chạy pilot với org `0x2395…` trên case mới.
3. T5-2, T5-3: có làm trước pilot không, hay pilot chỉ CASE_VERIFIED.
4. Quyết định T5-5 về `aidbridge_demo` (giữ V15 chỉ đọc hay migrate).
5. Phê duyệt deploy và phê duyệt cutover (hai văn bản riêng).
