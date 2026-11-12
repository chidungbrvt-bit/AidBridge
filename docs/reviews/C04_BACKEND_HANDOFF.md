# C04 — Bàn giao mapping cho Dũng/Codex

Ngày 11/11/2026. Scope: thông tin/chỉ dẫn đọc; không sửa DB/backend hoặc làm C05.

## Cấu hình đã xác minh

**Cập nhật 12/11:** Relayer hiện hành đã chuyển sang **`0x95a9564899849CF566DE5713205C4B747F361f55`** theo D54, funding0,149679527 test AVAX; [bằng chứng/điều kiện bật worker](FUJI_RELAYER_HANDOFF_2026_11_12.md). Signer và receipts ngày11 dưới đây là lịch sử deployment ban đầu. `verify-fuji.mjs` nay kiểm thêm rotation trước quyền hiện tại.

- Fuji C-Chain 43113; RPC https://api.avax-test.network/ext/bc/C/rpc
- registryAddress: 0x015D941c679b0c402c8262BE237adAD36cEf6726
- orgRef pilot: 0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46
- admin: 0x131DD6d404f8D0B118D6DB122194bc15b4E4601E
- relayer: 0x559994839f19F40ac704918C3FF1a913AC02dC01
- ABI full: contracts/deployment/AidRegistry.fuji.abi.json, D49/schema1; source VERIFIED Exact Match trên Snowtrace/Routescan.
- getOrganization(orgRef) = (true,true,relayer); admin role true cho admin, relayer role true cho relayer; không cấp chéo. Manifest và receipts tại contracts/deployment; rerun scripts/verify-fuji.mjs không cần signer.

## Quy ước backend đã kiểm tra

V9__add_blockchain_worker_columns.sql tạo organizations.public_ref BYTEA UNIQUE NOT NULL, 32 bytes/nonzero, random cho từng row; relayer_address VARCHAR(42) là nullable và có hex constraint. Organization.java có publicRef được ChainRefs.randomBytes32() sinh riêng và @Column(updatable=false). Organization.id vẫn UUID nội bộ. Case public_ref, logical_event_id và orgRef là các định danh khác nhau; không ép UUID thành bytes32 hoặc lấy fixture 0x1010... làm giá trị thật.

**Chưa xác minh/đặt DB binding**: không có UUID organization thật được cung cấp cho C04, không đọc credentials/IDE để tìm DB. Không giả định public_ref đang có bằng pilot orgRef. Owner Dũng (Backend), Khoa phối hợp readback chain trong task tiếp theo được giao.

## Checklist tiếp nhận (không thực thi ở C04)

1. Dũng chọn đúng organization demo đã APPROVED, đọc id/public_ref/relayer_address bằng kết nối do Dũng quản lý. Query chỉ đọc tham khảo:

```sql
SELECT id, status, encode(public_ref, 'hex') AS public_ref_hex, relayer_address
FROM organizations
WHERE id = :confirmed_organization_uuid;
```

2. So sánh đúng 32 bytes với orgRef trên. Nếu trùng, kiểm tra relayer_address và cấu hình registry/chain theo manifest, rồi xác minh code/readback lại trước worker.
3. Nếu khác: DỪNG tích hợp job, không overwrite public_ref hoặc dùng SQL UPDATE để vượt @Column(updatable=false). Khuyến nghị giữ ref backend hiện có và đăng ký ref đó trên cùng registry bằng admin khi task/transaction được phê duyệt riêng. Alternative rebind pilot ref cần Dũng đánh giá references/jobs/audit và có quyết định rõ trước migration. Không tự coi C04 đã lựa chọn alternative này.
4. Hai key DPAPI còn nguyên trên máy Khoa, không export vào handoff/chat. Cách vận hành signer backend là phần phối hợp C05/C07, không sao chép key admin làm relayer. DB mapping chưa có không phủ nhận C04 deployment/readback, nhưng chặn tuyên bố integrated C05 đã chạy.
5. Khi được giao C05: job/event IDs, caseRef, orgRef, sequence và commitment phải đến từ persisted job, đúng ABI D49; đối chiếu receipt/log thực rồi mới CONFIRMED. Không dùng deploy/registration receipt để thay CaseVerified proof.

Minh: C04 cung cấp registry/explorer evidence; chưa có API audit integration mới. Giữ phân biệt mock và chain thật.
