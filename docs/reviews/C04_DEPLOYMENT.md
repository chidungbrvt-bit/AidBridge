# C04 — Fuji pilot deployment (11/11/2026)

**Runtime acceptance PASS; C04 In Review chờ review nhóm.** User phê duyệt deploy và đăng ký organization trên Fuji trong lượt này. Không C05/F07; không push/PR. C03 source 61cc738 giữ nguyên; runner preparation 480913b.

## Deployment thực tế

Cập nhật12/11: bảng dưới giữ lịch sử deployment/registration. **Relayer hiện hành là D54 của Dũng**, xem [rotation/funding/readback](FUJI_RELAYER_HANDOFF_2026_11_12.md). Source/ABI/runtime/address không đổi; không dùng ví cũ cho worker mới.

| Trường | Giá trị |
|---|---|
| Chain | Avalanche Fuji C-Chain 43113 |
| Registry | 0x015D941c679b0c402c8262BE237adAD36cEf6726 |
| Deployment tx | 0xf5a11026f7bea424b6ce538ab74f7618e619264e2be86d5a7407e92505b49d24 |
| Deployment block | 60885528 |
| Organization registration tx | 0xf3b4fb81d07483a797fd7b361491c6dc502bebc5994f5835e37f7f8d1638eeea |
| Registration block | 60885532 |
| orgRef | 0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46 |
| Deployer/Admin | 0x131DD6d404f8D0B118D6DB122194bc15b4E4601E |
| Relayer | 0x559994839f19F40ac704918C3FF1a913AC02dC01 |
| ABI | D49, schemaVersion 1; exported AidRegistry.fuji.abi.json |
| Compiler | Solidity 0.8.28, EVM paris, optimizer enabled 200 |
| Creation hash | 0x595854a953b927a241515105022d9aa15f4ebb3eee41daa482d9e59c3f5642fb |
| Runtime hash | 0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad |
| Explorer source | VERIFIED — Exact Match trên Snowtrace/Routescan, API và UI đã xác nhận |

orgRef dùng nguyên public plan trong commit 480913b, không sinh mới hoặc ép UUID thành bytes32. Đây là định danh organization pilot on-chain. Không tự gán cho UUID organization của backend; C05/Dũng cần ánh xạ organizations.public_ref và relayer_address đúng với manifest trước gửi job. C04 không sửa DB hoặc implementation backend.

## Lệnh và kết quả

- Compile PASS (No contracts to compile); source/fingerprint pin khớp C03.
- Toàn suite Hardhat **61 passing**; C01 --ethers, SHA-256/Keccak và ABI encode/decode PASS trước broadcast. Java cross-language NOT RUN.
- User approval public hash được truyền cho runner; chỉ DPAPI deployer được giải mã trong bộ nhớ, không xuất key/log/seed. `node scripts/deploy.mjs --broadcast` gửi đúng hai transactions trong plan.
- Deployment receipt status=1; registration status=1. Independent readback lưu ở fuji-readback.public.json: on-chain code bằng toàn bộ artifact deployedBytecode; adminHasAdmin=true, relayerHasRelayer=true, adminHasRelayer=false, relayerHasAdmin=false; org registered=true/active=true và đúng relayer. SCHEMA_VERSION_V1=1.
- Independent eth_call giả lập admin gọi recordCaseVerification bị UnauthorizedRelayer; không phát business transaction hoặc lưu case giả lên Fuji.
- Deployment gas thực tế **1124648**, registration **100000**, giá gas 1 gwei; tổng phí **0.001224648 test AVAX**. Không phải gas estimate. Funding trước deploy 0.35 AVAX; balance sau hai giao dịch được RPC xác nhận 0.348775352 AVAX; relayer 0.149958 AVAX.
- Read-only verifier đã chạy PASS sau deploy; có thể chạy lại: `node scripts/verify-fuji.mjs` trong contracts/. Không bao giờ chạy lại deploy.mjs --broadcast để “kiểm tra”; journal tồn tại phải reconcile trước.

## Bằng chứng và tiếp nhận

- contracts/deployment/fuji-deployment.json: manifest, ABI full, source commit, compiler/settings, constructor, tx hashes, blocks và gas.
- contracts/deployment/fuji-readback.public.json: hai receipts và readback độc lập.
- contracts/deployment/AidRegistry.fuji.abi.json: compiler ABI full, không ABI draft.
- contracts/deployment/fuji-plan.public.json: orgRef và plan trước deploy.
- contracts/deployment/fuji-wallets.public.json: public addresses; không phải key store.
- contracts/deployment/console-wallet-cleanup.public.json: trạng thái dọn ví riêng.

Dũng/Codex: giữ registry address/chainId/orgRef/ABI theo manifest; backend UUID binding chưa cấu hình, không tự dùng fixture orgRef 0x1010... hoặc tạo orgRef khác rồi gọi registry. Thực hiện decoder/worker và Java/Fuji event test trong C05 được giao sau. Minh dùng trạng thái chain thật khi adapter/API sẵn sàng, không coi C04 là tích hợp frontend hoàn chỉnh. Để explorer verify sau, dùng đúng compiler inputs/settings/source và constructor argument; source đã VERIFIED Exact Match; bằng chứng tại fuji-verification.result.json.

## Console Wallet: chưa xóa vì chưa đủ bằng chứng an toàn

Ví trung gian 0x59458713c6a0DCEB4718f8fe707e564D0E882172 đã chuyển hết 0.5 test AVAX: 0.35 cho admin, 0.149958 cho relayer, phí 0.000042. Funding phase xác nhận balance C=0 và latest=pending nonce=2. Không dùng nó làm registry admin/relayer.

Trong lượt cleanup, RPC giới hạn eth_getLogs 100 block/query; khi chia nhỏ thì endpoint trả 429. Không hoàn tất inventory token/NFT và Fuji X/P nên **NOT_DELETED**. Không suy từ balance native=0 rằng không còn tài sản trên mọi mạng. Giữ nguyên Console Wallet/browser data, không xóa thủ công. Có chức năng DELETE riêng trong Manage, nhưng chỉ dùng khi kiểm tra an toàn đủ bằng chứng; nếu trang cần PIN/phê duyệt bảo mật user sẽ thao tác. Không xóa hai ví DPAPI hoặc file ngoài repo.

C04 runtime PASS độc lập với cleanup chưa hoàn tất. Không đánh dấu Done khi chưa review nhóm. Actual human effort chưa có số liệu.


## Rà soát bổ sung theo phân công 11/11 — kết quả hiện hành

**4/4 đầu ra gốc C04 ngày 11/11 đạt:** (1) contract address thật, (2) receipt deploy thành công, (3) phiên bản ABI được lưu, (4) đọc lại quyền relayer. Các điều kiện chain43113, C03 gate, deploy được user cho phép, deployer khác relayer đều có bằng chứng. Bảng phân công ghi key đặt trong env; user đã chọn/cho phép DPAPI testnet store an toàn thay vì env plaintext trong quá trình preparation — ghi nhận khác biệt lưu trữ được user cho phép, không claim đã đặt raw key vào env.

Backlog yêu cầu receipt/code/address/block/compiler/ABI, org readback và explorer status trung thực: đều đã có. Source verification bổ sung nay PASS **Exact Match**: API Routescan trả Pass - Verified; getsourcecode và UI Snowtrace hiển thị Contract Source Code Verified (Exact Match), compiler v0.8.28+commit.7893614a, optimizer200, paris, constructor admin chính xác. Source và các setting ảnh hưởng bytecode khớp input đã deploy; explorer chuẩn hóa outputSelection riêng, đã ghi rõ trong evidence. Không redeploy/sửa contract.

Builder Hub API trả403; form browser upload bị crash. Routescan chỉ được dùng sau khi Khoa cho phép rõ việc gửi source/compiler input công khai; không gửi secrets. Source input/submission/poll/getsourcecode/result được lưu trong contracts/deployment/fuji-verification.*.json. Source verification không phải security audit hoặc bằng chứng physical truth.

Rerun: verify-fuji.mjs PASS, toàn suite **61 passing**. Mapping backend thuộc Dũng; [handoff](C04_BACKEND_HANDOFF.md) ghi cụ thể UUID vs bytes32, constraints V9/JPA immutable, query đọc và xử lý mismatch. Chưa sửa backend/DB, chưa làm C05.

Console Wallet kiểm tra lại C balance=0 và latest=pending nonce=2; chưa đủ inventory X/P/token. Public-key recovery cross-check không khớp sender nên không tin địa chỉ suy ra, không xóa ví. Đây là cleanup chưa hoàn tất, không phải lỗi contract hoặc thiếu đầu ra gốc C04. Giữ NOT_DELETED. Hai ví DPAPI không bị đọc/xóa trong lượt này.

C04 giữ **In Review**, không tự Done vì còn group review theo AGENTS. Acceptance implementation C04 đạt; group approval và integrated C05 không được suy ra từ việc source verified.
