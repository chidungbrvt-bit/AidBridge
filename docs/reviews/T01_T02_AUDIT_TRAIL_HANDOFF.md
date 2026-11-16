# T01/T02 audit trail: bàn giao (15/11/2026)

Owner: Dũng (B). Trạng thái: **In Review**. Hai API chỉ đọc: không sửa worker, journal, contract, không thêm migration.

| Commit | Nội dung |
|---|---|
| `277c205` | T01 `GET /api/cases/{id}/audit-trail` |
| (T02, commit tiếp theo) | T02 `GET /api/campaigns/{id}/audit-trail` |

## 1. Trả lời Pha 0

a. Event nối với job bằng `blockchain_jobs.audit_event_id → audit_events.id` (UNIQUE, tối đa 1 job/event). `event_key` không dùng để nối. On-chain eventId là `blockchain_jobs.logical_event_id`. Một job có thể có nhiều attempt trong `blockchain_transactions`, nhưng V13 chỉ cho 1 attempt mở (PREPARED/SUBMITTED). Attempt hiển thị: bỏ `PREPARED` (chưa chắc đã broadcast), ưu tiên `CONFIRMED` → `REVERTED` → `SUBMITTED`, cùng mức lấy `submitted_at` mới nhất rồi `id`.

b. Không có job: `CASE_CREATED`, `CASE_UPDATED`, `CASE_RESUBMITTED`, `CASE_REJECTED`, `CASE_CHANGES_REQUESTED`, `EVIDENCE_VERSION_CREATED`, `EVIDENCE_CONTENT_READ`, `CAMPAIGN_CREATED`, `CAMPAIGN_PUBLISHED`, `DELIVERY_ATTEMPT_FAILED` → `NOT_APPLICABLE`. Có job: `CASE_VERIFIED`, `AID_PLEDGED`, `COMMITMENT_REDUCED`, `GOODS_RECEIVED`, `DELIVERY_RECORDED`.

c. Private, không ra T02: `audit_events.payload_private`, `actor_user_id`, `organization_id`, `entity_id`, `event_key`; `blockchain_jobs.manifest_private`, `salt_ciphertext`, `evidence_commitment`, `last_error`; `blockchain_transactions.signed_tx_ciphertext`, `receipt_private`, `signer_address`, `nonce`; tên/email người dùng, evidence, consent. Query không đọc payload, manifest, salt, signed bytes, receipt ở cả T01.

d. Campaign công khai: `campaigns.status IN (PUBLISHED, FULFILLED, CLOSED)` và `organizations.status = APPROVED` (cùng quy tắc `CampaignService.getById`).

e. T01: membership ACTIVE có ít nhất một role trong tổ chức sở hữu case (cùng quy tắc `CaseService.scoped`). Khác tổ chức, PA không membership, case không tồn tại → 404.

## 2. Bảng ánh xạ (quyết định theo `job.status`, không theo `last_error`)

| job | attempt | `blockchain.status` | txHash | blockNumber | explorerUrl T01 | T02 |
|---|---|---|---|---|---|---|
| không có | – | `NOT_APPLICABLE` | – | – | – | – |
| PENDING / PROCESSING | không có hoặc chỉ PREPARED | `PENDING_ON_CHAIN` | – | – | – | – |
| PROCESSING / SUBMITTED | SUBMITTED | `PENDING_ON_CHAIN` | có | – | có | có |
| CONFIRMED | CONFIRMED | `CONFIRMED_ON_CHAIN` | có | có | có | có |
| CONFIRMED | không CONFIRMED (bất thường) | `PENDING_ON_CHAIN` | – | – | – | – |
| FAILED `TX_REVERTED` | REVERTED | `FAILED_ON_CHAIN` | có | có | có | có, không errorCode |
| FAILED mã khác (`RECEIPT_*`, `MANIFEST_*`…) | SUBMITTED nếu có | `FAILED_ON_CHAIN` | có nếu có | – | có | **ẩn hash và link** |

- Link chỉ khi `chain_id = 43113` và hash khớp `^0x[0-9a-fA-F]{64}$`. Base URL: `aidbridge.blockchain.explorer-tx-base-url` (env `BLOCKCHAIN_EXPLORER_TX_BASE_URL`, mặc định `https://testnet.snowtrace.io/tx/`, bắt buộc https). Trường `network` = `Avalanche Fuji testnet` để UI luôn gắn nhãn testnet.
- `RECEIPT_PENDING` / `RECEIPT_PENDING_OPERATOR_ATTENTION` không phải thất bại: job vẫn SUBMITTED nên hiện pending.
- `errorCode` chỉ ở T01 và chỉ khi job FAILED. DTO T02 không có trường này.
- Attempt PREPARED có thể đã được broadcast nếu app chết trước khi ghi SUBMITTED (xem B05 report). API vẫn không hiện hash đó: chỉ hiện pending, không bao giờ nói quá.

## 3. DTO và ví dụ JSON

T01 item: `{eventId, type, entityType, occurredAt, actor{id, fullName}, logicalEventId, blockchain{status, chainId, network, contractAddress, txHash, blockNumber, explorerUrl, errorCode}}`.
T02 item: `{eventId, type, occurredAt, publicActorRole, logicalEventId, blockchain{status, chainId, network, contractAddress, txHash, blockNumber, explorerUrl}}`.
Trang: `{items, page, size, totalElements}`; sắp xếp `occurredAt` rồi `eventId` tăng dần; `size` 1..100 (mặc định 20), ngoài khoảng → 400 `VALIDATION_ERROR`. Trường không có giá trị trả `null`.

Hash và id dưới đây là minh họa định dạng, không phải giao dịch thật.

```json
{"eventId":"…","type":"CAMPAIGN_PUBLISHED","occurredAt":"2026-11-14T08:00:00.123456Z","publicActorRole":"ORGANIZATION","logicalEventId":null,
 "blockchain":{"status":"NOT_APPLICABLE","chainId":null,"network":null,"contractAddress":null,"txHash":null,"blockNumber":null,"explorerUrl":null}}

{"type":"AID_PLEDGED","publicActorRole":"DONOR","logicalEventId":"0x<64 hex>",
 "blockchain":{"status":"PENDING_ON_CHAIN","chainId":43113,"network":"Avalanche Fuji testnet","contractAddress":null,"txHash":null,"blockNumber":null,"explorerUrl":null}}

{"type":"GOODS_RECEIVED","blockchain":{"status":"PENDING_ON_CHAIN","chainId":43113,"network":"Avalanche Fuji testnet",
 "contractAddress":"0x<40 hex>","txHash":"0x<64 hex>","blockNumber":null,"explorerUrl":"https://testnet.snowtrace.io/tx/0x<64 hex>"}}

{"type":"CASE_VERIFIED","publicActorRole":"VERIFIER","blockchain":{"status":"CONFIRMED_ON_CHAIN","chainId":43113,"network":"Avalanche Fuji testnet",
 "contractAddress":"0x<40 hex>","txHash":"0x<64 hex>","blockNumber":123456,"explorerUrl":"https://testnet.snowtrace.io/tx/0x<64 hex>"}}

// T01, revert
{"type":"DELIVERY_RECORDED","actor":{"id":"…","fullName":"…"},"blockchain":{"status":"FAILED_ON_CHAIN","txHash":"0x<64 hex>","blockNumber":123,"explorerUrl":"https://testnet.snowtrace.io/tx/0x<64 hex>","errorCode":"TX_REVERTED", "…":"…"}}

// T02, receipt mismatch
{"type":"DELIVERY_RECORDED","publicActorRole":"FIELD_VOLUNTEER","blockchain":{"status":"FAILED_ON_CHAIN","chainId":43113,"network":"Avalanche Fuji testnet",
 "contractAddress":null,"txHash":null,"blockNumber":null,"explorerUrl":null}}
```

T02 allowlist và nhãn: `CASE_VERIFIED` → `VERIFIER`; `CAMPAIGN_PUBLISHED`, `GOODS_RECEIVED` → `ORGANIZATION`; `AID_PLEDGED` → `DONOR`; `DELIVERY_RECORDED` → `FIELD_VOLUNTEER`. Nhãn suy ra từ loại event, không tra tài khoản. Query T02 trả actor và entity là NULL ngay trong SQL.

## 4. Test

Chạy trên máy Dũng, `aidbridge_test` (schema riêng mỗi lớp IT, Flyway tới bản mới nhất), lệnh `.\mvnw.cmd -o test ...`:

| Lệnh | Kết quả |
|---|---|
| `"-Dtest=*Test,*Tests,*IT"` (sau T01, chưa set `DB_URL`) | 298 run, 0 failures, 1 error (`contextLoads`, môi trường), 2 skipped; T01: 12 + 3 pass |
| `"-Dtest=AuditChainStatusMapperTest,AuditTrailApiIT,PublicAuditTrailIT"` | Pass: 12 + 3 + 3 |
| `"-Dtest=*Test,*Tests,*IT"` (sau T02, có `DB_URL` trỏ `aidbridge_test`) | **301 run, 0 failures, 0 errors, 2 skipped** |

- Skipped (chưa chạy, không tính pass): `CaseVerifiedHardhatE2EIT`, `DonorUiE2EIT`, cần Hardhat node / trình duyệt.
- Hồi quy B05/B07/B08/B09 trong lần chạy trên đều pass: `VerificationOutboxIT`, `CaseVerifiedWorkerIT`, `ContributionOutboxIT`, `ReceiptApiIT`, `DeliveryApiIT`, `DeliveryConcurrencyIT`, `CampaignApiIT`.
- Lần chạy đầu `contextLoads` lỗi vì cửa sổ chưa set `DB_PASSWORD` (môi trường), chạy lại với `DB_URL` trỏ `aidbridge_test` thì pass.
- Lúc chạy test, file `V16__add_delivery_ref.sql` (B09, chưa commit) có trong thư mục migration nên cũng được áp vào schema test.
- N+1: mỗi trang đúng 2 câu SQL (rows + count), cố định theo code. Đếm số câu SQL lúc chạy: **NOT_VERIFIED** (chưa bật Hibernate statistics).
- Chưa kiểm với dữ liệu Fuji thật qua API (chưa gọi T01/T02 trên `aidbridge_demo`): **NOT_VERIFIED**.

| Test | Bao phủ |
|---|---|
| `AuditChainStatusMapperTest` (12) | Mọi dòng bảng ánh xạ, retry code không phải failure, CONFIRMED không receipt, hash sai định dạng, chain 31337 không link, base URL https |
| `AuditTrailApiIT` (3) | 9 trạng thái job; cùng org 200, khác org/PA/case lạ 404, chưa login 401; phân trang ổn định khi trùng timestamp; size 0/101, page −1 → 400; body không có payload, manifest, hash PREPARED |
| `PublicAuditTrailIT` (3) | Allowlist, nhãn vai trò, hash ẩn với mismatch và hiện với revert; quét toàn chuỗi JSON (id actor, tên, email, org, case, alias, title, evidence id, consent id, marker, mã lỗi); DRAFT (kể cả với staff), id lạ, org SUSPENDED → 404; phân trang khi có event bị lọc |

## 5. Chưa làm / điểm mở

- PA đọc T01 khi có dispute: chờ S01–S03. Hiện PA → 404.
- `businessStatus`, `evidenceReviewStatus` (DTO spec) chưa có nguồn, chờ D02; v1 không trả.
- `COMMITMENT_REDUCED` có job on-chain nhưng chưa trong allowlist T02 (cần quyết định; thêm thì gắn `DONOR`).
- T03 retry: chưa làm.
- `auditUrl` (D49 `/api/public/cases/{publicCaseRef}/audit` vs code `/api/campaigns/{id}/audit-trail`): T02 dùng đường dẫn theo spec T02; cần chốt với Minh trước khi đổi `auditUrl` trong `CampaignResponse`.

## 6. Bàn giao

**Minh (F05/F09):** timeline gọi T02 cho trang công khai, T01 cho màn staff. Hiển thị theo `blockchain.status` (bảng mục 2 và câu chữ F05 mục 4). Chỉ hiện "anchored" khi `CONFIRMED_ON_CHAIN`; `txHash` có mà status `PENDING_ON_CHAIN` thì ghi "submitted, not yet confirmed"; chỉ render link khi `explorerUrl` khác null và luôn kèm `network`. Fixture F02 có thể lấy theo các ví dụ JSON ở mục 3 (GAP-6).

**Khoa (C09):** đối chiếu bằng `logicalEventId` (= eventId on-chain) và `txHash` từ T01. Hiện worker chỉ xử lý `CASE_VERIFIED`, nên job `AID_PLEDGED`, `COMMITMENT_REDUCED`, `GOODS_RECEIVED`, `DELIVERY_RECORDED` (B07/B08/B09) đều nằm ở `PENDING`: API trả `PENDING_ON_CHAIN`, không hash. Chỉ `CASE_VERIFIED` có thể đối chiếu với Fuji lúc này.
