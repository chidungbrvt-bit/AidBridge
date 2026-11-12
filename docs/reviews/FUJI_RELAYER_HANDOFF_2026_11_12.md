# Fuji — Chuyển Relayer và funding cho Dũng, 12/11/2026

**Rotation/readback/funding PASS. C05 B05 end-to-end vẫn PARTIAL.** Không redeploy, sửa contract, gửi job nghiệp vụ hoặc bật worker. Người dùng đã phê duyệt các giao dịch này; chưa push/PR.

## Điều kiện vận hành

Người dùng chuyển xác nhận của Dũng: backend chưa giữ khóa ví cũ; blockchain_transactions rỗng, không PREPARED/SUBMITTED từ ví cũ, không job PROCESSING/SUBMITTED, không org DB trỏ ví cũ; không cấu hình BLOCKCHAIN_* hoặc worker chạy. Đây là **xác nhận của Dũng**, không phải Khoa truy cập DB máy Dũng. Worker tiếp tục tắt đến khi Dũng tiếp nhận bằng chứng/cấu hình.

Preflight RPC xác minh chain/runtime/organization/Admin, nonce latest=pending. Routescan testnet tìm thấy một OrganizationRegistered, đã kiểm log bằng receipt RPC; RPC kiểm thêm 100 blocks gần nhất. Không phát hiện org khác gán ví cũ trong tập tìm thấy. Indexer discovery không phải chứng minh mật mã tính đầy đủ mọi log; mutation chỉ đổi đúng orgRef và old role=false sau rotation phù hợp bộ đếm active org về 0 theo C03.

## Cấu hình hiện hành

| Trường | Giá trị |
|---|---|
| Network/chain | Avalanche Fuji C-Chain / 43113 |
| RPC | https://api.avax-test.network/ext/bc/C/rpc |
| Registry | `0x015D941c679b0c402c8262BE237adAD36cEf6726` |
| orgRef | `0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46` |
| Admin, giữ nguyên | `0x131DD6d404f8D0B118D6DB122194bc15b4E4601E` |
| Relayer mới, D54 | `0x95a9564899849CF566DE5713205C4B747F361f55` |
| Relayer cũ, không còn quyền | `0x559994839f19F40ac704918C3FF1a913AC02dC01` |
| ABI/runtime | D49/schemaVersion 1; `0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad` |
| Source | C03 `61cc738385e8b374d38996e8a725a9da6fa7f12b`, reviewed artifact snapshot; không dùng source C06 đang phát triển |

## Giao dịch và readback

| Thao tác | Transaction hash | Block | Status | Gas/fee thực tế |
|---|---|---|---|---|
| Admin nonce2 gọi updateOrganizationRelayer, value0 | `0x59f1076214a547795cdab875a364e2652959a43cd11ec254c9bbdee5e656c169` | 60950516 | 1 | 94861 gas ×1 gwei =0,000094861 AVAX |
| Ví cũ nonce1 gửi ví mới **0,149679527 test AVAX**, sau rotation/readback | `0xf778d9026a71b5819930e8181a50af12dba7be2931f2324b162ad92fd04ba5f7` | 60950527 | 1 | 21000 gas ×1 gwei =0,000021 AVAX |

Đúng một OrganizationRelayerUpdated từ registry, đúng orgRef/previous/new/active=true. Organization registered=true/active=true, assigned=new; new RELAYER_ROLE=true/Admin=false; old RELAYER_ROLE=false; Admin vẫn Admin=true/Relayer=false. Không thay đổi ABI/source/bytecode.

Readback độc lập block60950531, verifier kiểm lại block60950623: Admin **0,348680491** AVAX (nonce3), old **0** (nonce2), new **0,149679527** (nonce0); latest=pending. Receipt khớp sender/to/value/calldata/chain và canonical block hashes. Không tuyên bố finality vượt readback này. Explorer: `https://testnet.snowtrace.io/tx/{hash}`.

## Faucet

Đã mở faucet chính thức trong Work: session đăng nhập còn hiệu lực, C-Chain FUJI READY, hạn mức0,5 AVAX/24h. Console Wallet “Fuji Funding Only” còn lưu, khóa PIN, hiển thị0 C-Chain AVAX; menu mạng có copy/refresh/explorer/faucet/disconnect, **không có Send**. Không claim thêm, xuất khóa, nhập PIN thay người dùng, tạo/xóa ví. Chưa có đường gửi tiếp an toàn bằng UI đã quan sát; mục tiêu khoảng0,5 chưa đạt. Số dư UI không phải audit mọi tài sản/mạng của ví trung gian.

Funding thực 0,149679527 có thể dùng chuẩn bị pilot; Dũng vẫn phải estimate gas job thật và cap fee. Tham khảo 600000gas ×1gwei =0,0006AVAX/job, **không phải estimate B05 đã chạy**. Top-up thêm không chặn rotation hoặc chuẩn bị pilot với số dư hiện có. Không lấy tiền từ Admin để đủ mục tiêu.

## Bằng chứng và kiểm tra

- [Public journal](../../contracts/deployment/fuji-relayer-handoff.public.json): trước/sau, requests/hash/receipts/gas và quyền; không key/raw signed bytes.
- [Runner](../../contracts/scripts/relayer-handoff.mjs): mặc định read-only, `--execute` giới hạn hai thao tác đã pin chain/địa chỉ; lưu signed bytes và hash **trước broadcast**, resume cùng bytes/hash. Không xóa journal để tạo replacement hoặc funding lần nữa.
- Trong `contracts`, `node --test scripts/relayer-handoff.test.mjs`: **PASS 2 tests**, request allowlist và negative mainnet/to/data/value/type/nonce/gas; `node --check` scripts **PASS**.
- `node scripts/verify-relayer-handoff.mjs`: **PASS**, RPC độc lập receipts/call/event/current rights, không mở ví.
- `node scripts/verify-fuji.mjs`: **PASS**, kiểm deploy/registration lịch sử rồi kiểm rotation để lấy signer hiện hành. Không sửa manifest `fuji-deployment.json`/registration ban đầu.
- **NOT RUN:** worker/B05 SQL/Fuji E2E, audit API, C06/C08. Hai receipt này không phải CaseVerified/job proof.

## Dữ liệu mới và khóa

Không tạo file ngoài repo. Hai ví gốc `%LOCALAPPDATA%/AidBridge/fuji-wallets.clixml` giữ nguyên, dùng DPAPI helper hiện có; khóa chỉ ở bộ nhớ, không vào argv/log/chat. Dũng giữ khóa ví mới theo D54; Khoa không yêu cầu/đọc khóa đó.

Hai recovery journals mới **trong repo, Git ignored**: `contracts/deployment/fuji-relayer-rotation.private.clixml`, `contracts/deployment/fuji-relayer-funding.private.clixml`, chứa signed bytes mã hóa DPAPI CurrentUser, không wallet private key. Giữ để recovery, không commit/upload/đọc ra log. Không cleanup hoặc thay đổi/xóa Console Wallet.

## Dũng/Minh tiếp nhận

1. Dũng dùng signer **D54 mới**, registry/chain/orgRef bảng trên. Seed **dev-only phương án A**, không migration: xác minh organizations.public_ref đúng32bytes orgRef và relayer_address mới. Khoa không sửa DB/backend; không ép UUID hoặc ghi đè org production.
2. Dùng code C07/PR10/12 đã kiểm thử và V13; giữ BLOCKCHAIN_SECRET_KEY AES-GCM ổn định qua restart, không chạy lẫn worker cũ/mới hoặc dùng signer ngoài worker. Khóa D54 chỉ Dũng nạp env, không dùng Admin/key DPAPI cũ thay thế.
3. Dũng kiểm DB binding/config/gas/job B05 thật rồi mới chủ động bật worker. Đối chiếu exact CaseVerified envelope/job/eventId/orgRef/caseRef/sequence/schema/payloadDigest/canonical block trước CONFIRMED, thử recovery original bytes/hash theo C07. Không tạo job từ proof độc lập ngày11.
4. Minh giữ business status tách chain; dùng audit API thật khi có. Không gắn rotation/funding/independent smoke vào job B05 chưa chứng minh. F08 mock không liên quan giao dịch chain mới.

**Đã gỡ blocker quyền và funding tối thiểu phía chain. C05 E2E còn Dũng chạy job B05 thật bằng DB/worker của Dũng và bàn giao proof; chưa tự đổi C05 thành PASS/Done.**
