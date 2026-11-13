# B05 real Fuji run — 13/11/2026

**In Review.** Hai giao dịch CaseVerified đã được kiểm chứng trên Fuji. Dũng đã chạy SQL trong pgAdmin trên aidbridge_demo và xác nhận kết quả đọc lại khớp các tiêu chí kiểm tra. B2 và bằng chứng theo thời điểm của D1 còn thiếu.

## Worker và preflight

- Journal đi từ PREPARED → SUBMITTED → CONFIRMED khi receipt và event hợp lệ, hoặc REVERTED khi receipt xác nhận revert. Khi revert, job là FAILED. PENDING và PROCESSING là trạng thái của job.
- Worker commit signed bytes đã mã hóa AES-GCM cùng hash và nonce trước broadcast. Nếu app chết sau broadcast và trước khi ghi SUBMITTED, attempt còn PREPARED. Sau khi job đến hạn claim lại, worker giải mã và gửi lại đúng bytes đã lưu, giữ txHash và nonce.
- SignerSessionLock giữ advisory lock trên cùng một kết nối PostgreSQL qua RPC. Session pooling giữ nguyên backend session. Transaction pooling có thể đổi session giữa lần lấy khóa và bước kiểm tra, khiến khóa thiếu hiệu lực bảo vệ worker.
- B1 — BLOCKCHAIN_ENABLED, BLOCKCHAIN_RPC_URL, BLOCKCHAIN_CHAIN_ID, BLOCKCHAIN_REGISTRY_ADDRESS, BLOCKCHAIN_RELAYER_PRIVATE_KEY, BLOCKCHAIN_SECRET_KEY, BLOCKCHAIN_POLL_INTERVAL_MS và DB_PASSWORD đều **chưa set trong phiên kiểm chứng này**. Env của tiến trình Dũng đã chạy qua đêm là **NOT_VERIFIED**.
- B2 — **Not Run / NOT_VERIFIED.** Phiên kiểm chứng không có biến môi trường BLOCKCHAIN_RELAYER_PRIVATE_KEY và chưa xác định được file khóa D54 ngoài repo. Dũng kiểm tra cấu hình chạy backend hoặc nơi lưu khóa lần trước. Khi tìm được, chạy lệnh dưới đây trong terminal đã nạp khóa D54, từ thư mục contracts. Lệnh chỉ in địa chỉ suy ra để đối chiếu với 0x95a9564899849CF566DE5713205C4B747F361f55. Hai receipt xác nhận địa chỉ signer của các giao dịch đã ghi nhận.
- B3/B4 — **MATCH.** Chạy nguyên verifier của Khoa tại ref ad250546d0a4d131ad78764ce23276a574b69f6a. Fuji chain 43113, block 60976590. Tổ chức registered/active, relayer D54 có RELAYER_ROLE, relayer cũ hết quyền. Số dư ví mới 0.149679526901130368 test AVAX, cao hơn ngưỡng 0.02. Với trần 300000 gas × 25 gwei, dự toán tối đa theo cấu hình yêu cầu là 0.0075 AVAX/tx. Chi phí quan sát của hai tx nằm ở bảng dưới.
- B5/B6 — Dũng đã xác nhận seed DB demo ở Task 1 và xác nhận kết quả truy vấn journal sau lượt Fuji. Snapshot trước chạy và số dòng journal trước D1 vẫn **NOT_VERIFIED** từ bằng chứng hiện có.

~~~powershell
node --input-type=module -e "import { computeAddress } from 'ethers'; let key = process.env.BLOCKCHAIN_RELAYER_PRIVATE_KEY; if (!key) { console.error('BLOCKCHAIN_RELAYER_PRIVATE_KEY unset'); process.exit(1); } if (!key.startsWith('0x')) key = '0x' + key; if (!/^0x[0-9a-f]{64}$/i.test(key)) { console.error('Invalid private key format'); process.exit(1); } try { console.log(computeAddress(key)); } catch { console.error('Invalid private key'); process.exit(1); }"
~~~

## Receipt và đối chiếu

Registry C03 là 0x015D941c679b0c402c8262BE237adAD36cEf6726, source 61cc738. Runtime hash khớp 0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad. collectProof trả PASS 17 checks cho mỗi tx và đọc được getEventRecord/getCaseSummary ở block chứa tx. caseRef, eventId và evidenceCommitment dùng làm expected trong lượt này được decode từ calldata công khai. Nguồn đó cho phép đối chiếu calldata với receipt và storage của contract. Cột DB vẫn cần dữ liệu riêng.

**MATCH theo xác nhận của Dũng.** Kết quả SQL trong pgAdmin khớp các tiêu chí đã hướng dẫn gồm job và attempt CONFIRMED, receipt_status = 1, nonce và block của hai giao dịch, cùng một attempt cho job dùng trong D1. Chưa có output từng cột để đối chiếu độc lập toàn bộ bảng.

| Hạng mục | DB | Tx 1 trên Fuji | Tx 2 trên Fuji | Kết luận |
| --- | --- | --- | --- | --- |
| Tx hash | Ảnh báo hai hash rút gọn | [0x46514001…8566a](https://testnet.snowtrace.io/tx/0x465140011495f85743088ada113097204a891e41bc0b985b15e653366cb8566a) | [0x97104a33…f98e](https://testnet.snowtrace.io/tx/0x97104a33730c05210da67a21c96b271876d744fcb61fbab46a5d87cff925f98e) | MATCH với hash rút gọn trong ảnh |
| from / to | NOT_VERIFIED | D54 / C03 registry | D54 / C03 registry | MATCH với cấu hình công khai |
| nonce | Ảnh báo giữ nguyên qua restart, chưa có giá trị DB | 0 | 1 | NOT_VERIFIED DB ↔ Fuji |
| orgRef | Seed Task 1 theo xác nhận Dũng | 0x2395…0a46 | 0x2395…0a46 | MATCH với orgRef bàn giao |
| caseRef | NOT_VERIFIED | 0x8d5c62bea7acd49a2e54e29efadc01b56f39fa0e55a40d6c9d33217c2b3dd9d6 | 0x3429fa203bf357eb91aba6f367e70180da2563ab9e564c9d5ee345461ae7929c | MATCH calldata ↔ event, NOT_VERIFIED DB ↔ Fuji |
| eventId | NOT_VERIFIED | 0xb015509dd7c0f529e05293bc96fc263115a1b524a139a5ddf70e7d0ffe389a78 | 0x72c71be20cc1f8f0120839dff5fd781b8ce6d1cd7f761520b1afe0ace0adb91f | MATCH calldata ↔ event, NOT_VERIFIED DB ↔ Fuji |
| evidenceCommitment | NOT_VERIFIED | 0xf5dcb21ed670a1cd234303764c55063fef0f5ec5fe1c961397b250391e298f62 | 0x93a214c2cd07c01f32c7af1ee88a8ffa33ea6e477c03513347a7bfe37f6390dc | MATCH calldata ↔ event, NOT_VERIFIED DB ↔ Fuji |
| chainSequence / schemaVersion | NOT_VERIFIED | 1 / 1 | 1 / 1 | MATCH với kỳ vọng C05 |
| CaseVerified / readback | NOT_VERIFIED | 1 log, eventExists=true, caseVerified=true, lastChainSequence=1 | 1 log, eventExists=true, caseVerified=true, lastChainSequence=1 | MATCH |
| Receipt / block / gas | NOT_VERIFIED trực tiếp | 1 / 60959263 / 257473 | 1 / 60959653 / 257473 | MATCH RPC ↔ Routescan |
| Mốc block (UTC+07) | prepared_at / submitted_at / confirmed_at chưa có | 13/11/2026 00:59:37 | 13/11/2026 01:09:29 | MATCH timestamp event ↔ block |

Mỗi tx có effective gas price 192 wei và phí 0.000000000049434816 test AVAX theo RPC. Việc mở trực tiếp trang Snowtrace trong phiên kiểm chứng là **NOT_VERIFIED** vì công cụ web không tải được trang. Link ở bảng để Minh và Khoa mở kiểm tra.

## D1 và D2

- **D1 phía chain — MATCH.** Routescan lọc theo registry, topic0 CaseVerified và eventId từ block deploy 60885528 tới 60976640. Mỗi eventId trả đúng một log, với txHash tương ứng trong bảng. Fuji RPC trả đúng một log khớp ở từng block chứa receipt. Phạm vi này chứng minh có một event canonical quan sát được cho mỗi ID. Số lần gửi lại cùng signed bytes qua RPC cần log worker nếu muốn xác minh.
- **D1 phía DB — Dũng xác nhận một attempt khi đọc lại.** Ảnh ghi cùng txHash và nonce trước/sau restart. Cần Dũng xác nhận giao dịch nào là D1 và bổ sung log hoặc ảnh trước dừng, sau bật lại để xác minh mốc restart. Truy vấn bên dưới cho biết các timestamp journal.
- **D1 mốc restart — NOT_VERIFIED.** Chưa tìm thấy log worker lưu trong repo cho hai tx, và truy vấn Windows Event Log trong khoảng 00:50–01:30 ngày 13/11/2026 theo UTC+07 chưa cung cấp bằng chứng tiến trình. Tx 2 được mined sau Tx 1, nhưng thứ tự này chưa xác định được tx nào thuộc lần restart. Dũng đối chiếu hai mốc block với ảnh hoặc console đã lưu để xác nhận tx D1.
- **D2 — Not Run** trong lượt kiểm chứng này.

~~~sql
SELECT
    j.id AS job_id, j.status AS job_status, j.created_at,
    '0x' || encode(o.public_ref, 'hex') AS org_ref,
    '0x' || encode(c.public_ref, 'hex') AS case_ref,
    '0x' || encode(j.logical_event_id, 'hex') AS event_id,
    '0x' || encode(j.evidence_commitment, 'hex') AS evidence_commitment,
    j.chain_sequence, j.schema_version,
    count(t.id) OVER (PARTITION BY j.id) AS attempt_count,
    t.status AS attempt_status, t.tx_hash, t.nonce, t.chain_id,
    t.contract_address, t.signer_address, t.block_number, t.receipt_status,
    t.prepared_at, t.submitted_at, t.confirmed_at
FROM public.blockchain_jobs j
JOIN public.audit_events a ON a.id = j.audit_event_id
JOIN public.organizations o ON o.id = a.organization_id
JOIN public.aid_cases c ON c.id = j.aid_case_id
JOIN public.blockchain_transactions t ON t.blockchain_job_id = j.id
WHERE a.event_type = 'CASE_VERIFIED'
  AND j.logical_event_id IN (
    decode('b015509dd7c0f529e05293bc96fc263115a1b524a139a5ddf70e7d0ffe389a78', 'hex'),
    decode('72c71be20cc1f8f0120839dff5fd781b8ce6d1cd7f761520b1afe0ace0adb91f', 'hex')
  )
ORDER BY j.created_at, t.prepared_at;
~~~

## Bàn giao

- **Minh (F05).** Dùng caseRef, eventId và link Snowtrace của Tx 1 ở bảng làm bằng chứng public đã xác minh trên chain. Xác nhận mapping DB/case cùng Dũng trước khi dùng làm fixture cho UI tích hợp. Projection txHash/blockNumber/explorerUrl của audit API cần được kiểm tra riêng.
- **Khoa (C08/C10).** Hai tx ở bảng có receipt status 1, runtime C03 và storage readback đã kiểm chứng. Review bằng chứng D1 sau khi Dũng bổ sung mốc restart. C06 cần triển khai riêng trên Fuji trước khi chạy luồng milestone C06. Giao dịch triển khai C06 là **Not Run** trong lượt này.
- **Dũng (B05).** Tìm lại khóa D54 đã lưu để chạy B2. Đính kèm kết quả SQL đã chạy, chỉ rõ tx D1 và bổ sung log restart cho review. Worker C05 chỉ claim CASE_VERIFIED, các job B07 AID_PLEDGED/COMMITMENT_REDUCED vẫn chờ worker hỗ trợ.

Lệnh đã chạy gồm git show để đọc handoff của Khoa, node --check và node chạy verify-relayer-handoff.mjs từ bản trích của ref Khoa, node --input-type=module - gọi collectProof, Invoke-RestMethod đọc Fuji/Routescan, và psql -X -w kiểm tra truy cập aidbridge_demo. psql trả no password supplied. Lượt này dùng readback, không tạo job mới hoặc lặp lại restart.
