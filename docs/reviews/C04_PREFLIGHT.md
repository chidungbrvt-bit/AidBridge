# C04 — trạng thái sau deploy 11/11/2026

**Đã deploy và đăng ký organization: runtime PASS, C04 In Review.** Các mục READY/BLOCKED bên dưới là lịch sử preflight. Kết quả hiện hành, manifest/receipts/readback và trạng thái Console Wallet chưa xóa tại [C04 deployment](C04_DEPLOYMENT.md). Không chạy lại broadcast; dùng verify-fuji.mjs chỉ đọc.

---

# C04 preparation — cập nhật 11/11/2026

**Software preparation PASS; trạng thái deploy: BLOCKED chỉ còn funding và xác nhận cuối. Chưa READY TO DEPLOY vì deployer balance=0.** Phần preflight cũ bên dưới được giữ làm lịch sử; các nhận định chưa có runner/network/wallet đã được thay thế.

## Kết quả đã hoàn thiện

- Runner `contracts/scripts/deploy.mjs` dùng ethers đã pin trong lockfile, core `deployment.mjs` được chia sẻ với dry-run test. Không thêm dependency, không đổi lockfile, không sửa contract C03.
- Network Fuji trong hardhat.config.ts: chainId 43113, official RPC, accounts=[]; CLI runner dùng provider riêng cùng endpoint, không dùng unlocked RPC account hoặc Hardhat external signer. Mọi call deploy ký chainId 43113; guard trước deploy và registration từ chối mainnet. Local injection chỉ dùng 31337 trong tests; CLI không có flag đổi chain/RPC.
- Default CLI chỉ kiểm tra; chỉ broadcast khi có cả --broadcast và AIDBRIDGE_DEPLOY_APPROVAL bằng creation hash đã pin. Không chạy broadcast trong lượt này. Xác nhận của user vẫn bắt buộc, env flag không tự cấp quyền cho AI.
- Pin normalized LF source SHA-256, full ABI hash, creation/runtime bytecode hashes từ C03. Drift source/artifact bị chặn. Compiler settings/ABI/fingerprints ở báo cáo trước vẫn đúng; không sử dụng bytecode C03 cũ.
- Kiểm tra checksum/nonzero addresses, relayer khác deployer/admin; runner dùng dedicated deployer=admin cho hai thao tác (được quy ước cho phép). EOA code check, xác minh địa chỉ ví giải mã khớp public config, kiểm tra pending nonce, gas estimate +20%, gas ceiling deploy 3m + registration 200k, legacy gasPrice >=1gwei và <=25gwei. Max transaction limits tổng cộng <=0.08 AVAX; funding target 0.1 AVAX là buffer bảo thủ, không phải phí dự báo. Rerun estimate/fee/balance sát lúc xác nhận.
- Journal được tạo exclusive trước broadcast, lưu nonce/predicted address; hash được bổ sung sau send. Nếu crash/timeout, file còn lại chặn deploy lặp; phải reconcile chain/nonce/predicted address và transaction trước tiếp tục, không xóa journal để retry mù. Runner không triển khai crash-safe worker C07. Manifest cuối ghi deployment receipt hash/block/address, orgRef/constructor, role readback và code hash; explorer NOT_VERIFIED cho đến khi xác minh thực tế. Nếu bị dừng sau deployment thì org setup có thể chưa xong: cần reconcile và bàn giao rõ, không deploy registry lần nữa.

## Ví testnet đã chuẩn bị

Đã tạo hai ví CSPRNG bằng ethers Wallet.createRandom; mnemonic/keys không ghi ra output/file plaintext. Helper chuyển secrets qua anonymous pipe tới Windows PowerShell, SecureString/Export-Clixml dùng DPAPI CurrentUser. Đã round-trip thành công và xác minh public addresses, không ký transaction thật.

- Deployer/admin: `0x131DD6d404f8D0B118D6DB122194bc15b4E4601E`
- Relayer: `0x559994839f19F40ac704918C3FF1a913AC02dC01`
- Encrypted store ngoài repo: `C:/Users/Khoa/AppData/Local/AidBridge/fuji-wallets.clixml`.
- Public config: `contracts/deployment/fuji-wallets.public.json`; public plan: `fuji-plan.public.json` gồm orgRef random bytes32 ổn định và hai thao tác dự kiến.

DPAPI gắn với Windows user/machine: không tự coi file copy sang máy khác là recoverable backup. Giữ encrypted backup trong môi trường/user được phép, không xóa Windows profile khi ví còn cần. Process chạy cùng user có thể giải mã; đây là bảo vệ at-rest cho testnet, không HSM hoặc production custody. Không import vào browser, không dùng ví cá nhân/Dũng. Helper init có bảo vệ không ghi đè encrypted store; public config phải giữ khớp store. Nếu mất store không tạo ví mới cùng địa chỉ; cần recovery/redeploy có chủ ý. Không commit store/keys. Đã thêm ignore *.clixml/*.key/*.pem.

## Funding và blocker thực tế

Read-only Fuji 43113 xác minh cả hai balances = 0 AVAX. Live eth_estimateGas cho creation transaction với public admin thật = **1134741 gas**; local cùng runner estimate **1124648**, gasLimit **1349577** với local account khác. Hai số khác nhau là theo constructor address và chain state; không tráo local estimate thành estimate Fuji. Khi đã fund runner sẽ tính lại buffer/fees thật; gas cap vẫn bắt buộc.

Official faucet https://build.avax.network/console/primary-network/faucet đã mở kiểm tra: yêu cầu wallet detected/connected và Builder Hub Account; phiên này chưa đăng nhập/kết nối. Không tự tạo account hoặc nhập khóa DPAPI vào browser để vượt yêu cầu. User cần nhận test AVAX qua faucet bằng phiên/account testnet do user quản lý và funding địa chỉ deployer nêu trên tối thiểu 0.1 AVAX Fuji C-Chain. Chỉ test AVAX, không mainnet/tiền thật; không gửi private key/seed cho faucet. Relayer balance=0 không chặn deploy/registration (admin trả gas), nhưng phải fund trước khi C05 gửi business events. Không gửi transfer từ deployer để fund relayer trong lượt này.

## Commands và xác nhận cuối

Trong D:/Project/AidBridge/contracts:

- `node scripts/deploy.mjs` — read-only signer/public config/chain/balance/estimate preflight; hiện exit 1 đúng với INSUFFICIENT_BALANCE_TARGET_0_1_AVAX.
- `node scripts/preflight.mjs` — fingerprint/chain probe cũ; nếu không set public env vars có thể báo missing dù public config mới đã có; dùng deploy.mjs làm readiness gate chính thức.
- `node node_modules/hardhat/dist/src/cli.js compile`
- `node node_modules/hardhat/dist/src/cli.js test`

Sau user funding: chạy preflight để thu actual gas/balance, xác nhận lại orgRef và hai thao tác trong public plan. Chỉ khi READY và user phê duyệt mới đặt approval environment bằng creation hash và dùng `node scripts/deploy.mjs --broadcast`. Đừng chạy command broadcast chỉ dựa vào tài liệu này. Không cần cung cấp secret trong chat/env plaintext; signer dùng DPAPI. Nếu approvals/môi trường không cấp quyền broadcast, dừng tại đó.

## Kiểm thử thực tế cuối

Compile PASS (No contracts to compile, current artifact đã kiểm chứng). **61/61 Hardhat tests PASS**, gồm 55 baseline C03 + local preflight + 5 runner tests. Core runner execute thực sự deploy và register/readback trên local 31337. Test wrong-chain (43114), zero/missing constructor/public addresses, key separation, zero balance, fee/gas cap, missing signer, signer mismatch và ABI/bytecode drift PASS. DPAPI creation/unlock/public identity PASS trên máy Windows hiện tại. CLI syntax PASS. C01 --ethers/SHA-256/Keccak/encode/decode PASS. Actual Fuji broadcast/receipt/explorer verification NOT RUN; chưa gọi C04 Done.

C03 commit 61cc738 được bảo toàn. Chỉ commit local C04 preparation sau diff/ignore checks; C04 In Progress, funding blocker do user hỗ trợ, chưa push/PR/merge. Không C05/F07. Lượt tiếp theo chỉ cần funding confirmed, rerun read-only readiness rồi tổng hợp xác nhận cuối trước hai giao dịch.

---

# Lịch sử preflight giai đoạn 1

# C04 — Fuji pilot, giai đoạn 1: Preflight

Ngày 11/11/2026. Owner Khoa. **Preflight tổng thể BLOCKED cho deploy thật**: contract/local checks PASS, thiếu public wallet addresses, funding verification và deploy script/network wiring. Không có lỗi C03 mới. Không deploy, không gửi transaction Fuji/mainnet hoặc ký bằng private key thật. C04 chưa đạt acceptance deployment receipt/code/address/block và source explorer.

## Baseline và phạm vi

Repo D:/Project/AidBridge, khoa, HEAD 61cc738385e8b374d38996e8a725a9da6fa7f12b. Working tree ban đầu sạch; không fetch/pull/merge/rebase/reset. Commit C03 bảo toàn, contract không sửa. Nguồn: AGENTS, TASK_BACKLOG C04, BLOCKCHAIN_ARCHITECTURE, DECISION_LOG D48/D49 và bổ sung sticky withdrawal, C03_REVIEW_GATE. Chưa có bảng phân công 11/11 chi tiết C04 mới trong lượt này; giai đoạn preflight theo yêu cầu user, full C04 acceptance theo backlog, không coi preflight là Done.

## Công cụ và bằng chứng thực tế

Node v24.22.1, Hardhat 3.21.2; dependencies từ package-lock đã có, không cài/nâng cấp thêm. Chạy trong contracts/:

- `node node_modules/hardhat/dist/src/cli.js compile`: PASS (No contracts to compile). Không tuyên bố rebuild mới; đã kiểm chứng current compiler input/output.
- `node node_modules/hardhat/dist/src/cli.js test`: **56 passing**: 55 C03/smoke + 1 C04 local dry-run, không failures/skips.
- `node scripts/preflight.mjs`: PASS network/build checks, báo missing wallet config. Exit 0 chỉ nghĩa các kiểm tra có thể chạy thành công, không có nghĩa đủ điều kiện deploy.
- Từ root `node docs/fixtures/c01/verify-c01.mjs --ethers`: PASS static, SHA-256/Keccak và encode/decode; Java agreement NOT RUN.
- Python kiểm tra source Git 61cc738 so với working tree sau chuẩn hóa LF/CRLF: PASS; build-info source byte-for-byte với file working tree: PASS; creation/runtime bytecode artifact bằng compiler output: PASS; D49 draft 4/4 names/types/order/indexed/mutability/outputs: PASS.
- Local dry-run chỉ Hardhat chain 31337, assert chain trước deployment: receipt status=1, runtime bytecode đúng artifact, constructor initialAdmin khác deployer chỉ cấp admin cho initialAdmin, register org và role readback PASS; zero initialAdmin revert. Đây không phải receipt/address Fuji.
- C02 smoke wrong-chain/missing-config/key-separation tests dùng dữ liệu giả PASS. Không suy ra deployment runner đã có guard: runner deploy chưa tồn tại.

## Network read-only

Official source: https://build.avax.network/docs/primary-network xác nhận Fuji C-Chain RPC https://api.avax-test.network/ext/bc/C/rpc, chainId 43113 (0xa869), mainnet 43114 không được dùng.

RPC thực tế: eth_chainId = 43113; eth_blockNumber = 60883114; eth_gasPrice = 160 wei tại lần đo. Gas thay đổi, không phải báo giá hoặc funding threshold. Script chỉ allow eth_chainId/eth_blockNumber/eth_gasPrice/eth_getBalance/eth_getCode; không Wallet/signer/send methods. Endpoint official được cố định để không vô tình dùng URL chứa credentials. Chain mismatch dừng trước các read phụ thuộc network. Không dùng blockchain keys.

## Contract fingerprint (artifact hiện tại sau C03)

Solidity 0.8.28, EVM paris, optimizer enabled 200. Constructor `(address initialAdmin)` nonzero; actual address CHƯA CHỐT. Full ABI giữ nguyên D49; code/storage đã đổi tại C03. Source hash bên dưới là raw working-tree UTF-8 CRLF; SHA khác nếu checkout LF, không tự kết luận code thay đổi. Bytecode metadata cũng phụ thuộc compiler inputs/settings/paths.

| Fingerprint | Giá trị |
|---|---|
| source SHA-256 | 3e9f3cd659c7ebd0c9804ebda106a0c809248347aa0aefacf67b2ed5c2817204 |
| ABI SHA-256, JSON.stringify(artifact.abi) | 8042b783190a40d0843157dca4e273528bc49176ad134011e48cbb97aef340be |
| creation bytecode Keccak-256 | 0x595854a953b927a241515105022d9aa15f4ebb3eee41daa482d9e59c3f5642fb |
| runtime bytecode Keccak-256 | 0x4c778ba83052da23d357828dca603bddb14c6e26a0fdbda94143cafb2a39e7ad |
| creation / runtime bytes | 5161 / 4822 |

Runtime hash không bao gồm constructor args; init transaction data phải là creation bytecode + ABI encoded initialAdmin. Không dùng artifact cũ trước 61cc738. Artifacts/cache/node_modules bị ignore, không cần commit chúng trong giai đoạn preflight; deployment manifest thực sẽ lưu fingerprint/context/evidence theo acceptance C04.

## Cấu hình và ví

hardhat.config.ts pin compiler và có validateExternalNetworkEnv nhưng không khai báo network Fuji hoặc accounts. Không có contracts/scripts/deploy.ts hoặc deployment manifest. Không sửa config để nối signer trong giai đoạn này. Tool mới scripts/preflight.mjs chỉ đọc, KHÔNG phải deploy runner và không thay thế dry-run thực của runner sau này.

Chỉ kiểm tra tên biến environment: FUJI_DEPLOYER_PRIVATE_KEY/FUJI_RELAYER_PRIVATE_KEY không hiện diện; không đọc giá trị secret. FUJI_DEPLOYER_ADDRESS/FUJI_ADMIN_ADDRESS/FUJI_RELAYER_ADDRESS cũng thiếu. Chỉ kiểm tra file .env tại root và contracts: không tồn tại, không đọc nội dung. Không suy ra user không có ví ở nơi khác. Chưa biết balance AVAX testnet vì chưa có public addresses; NOT RUN balance/code checks. Không tự tạo wallet hoặc tìm secret trong IDE/browser/files khác.

## Checklist trước lượt deploy thật

1. Khoa xác nhận public 0x addresses deployer, initialAdmin và relayer; admin/deployer có thể trùng nếu nhóm chọn, relayer phải tách admin theo quy ước. Chỉ cung cấp public address trong chat/tài liệu; giữ private key/mnemonic trong cơ chế lưu trữ riêng được duyệt, không gửi vào chat hoặc commit.
2. Ví dành riêng testnet; người giữ ví nhận test AVAX trên Fuji C-Chain qua faucet từ official docs. Chưa xác định số AVAX cần: phải estimate gas với constructor/public addresses thật, áp fee cap và có buffer cho org setup. Không dùng gasPrice snapshot làm ngân sách deploy.
3. Chạy lại read-only preflight với public addresses; kiểm tra balance/code, checksum/nonzero, đúng chain 43113. Public-key/address ownership và key separation phải được kiểm chứng an toàn trong giai đoạn signer setup được cho phép; presence không chứng minh cấu hình hợp lệ.
4. Chuẩn bị/review deploy.ts và explicit Fuji network configuration trong lượt triển khai được giao: fail-closed wrong-chain cả provider lẫn signed transaction, pin artifact/source/fingerprint, constructor address và signer, fee/gas caps; không lấy config flag làm bằng chứng chain thật. Chạy dry-run chính runner này local trước broadcast. Hiện chưa có runner này nên chưa xác nhận sẵn sàng deploy.
5. Chốt custodian/authorization thực thi, không dùng personal wallet có tài sản. Chỉ ký/broadcast sau khi được giao giai đoạn deploy thật. Rerun compile/tests/fingerprint nếu source/config/dependencies thay đổi.
6. Sau deploy (chưa thực hiện): lưu manifest chain/address/deploy tx/receipt/block/compiler/settings/ABI/hash, read code đúng artifact, admin và org-relayer readback; explorer source verification ghi trạng thái thật. Không dùng tx hash đơn lẻ làm bằng chứng thành công.

## Handoff/trạng thái

C04 bắt đầu In Progress (preflight), bị chặn readiness deploy bởi public wallet/funding + deploy runner/config chưa có. Owner Khoa chuẩn bị public config/funding; AI lượt sau thực hiện runner/preflight rồi xin quyền thực thi theo phạm vi user giao. C03 không đổi In Review/PASS; không thay task Dũng/Minh. Không bắt đầu C05/F07, không commit/push/PR. Actual human effort chưa có số liệu. Files mới: scripts/preflight.mjs, test/C04.preflight.test.ts và tài liệu này; backlog cập nhật ghi chú C04.
