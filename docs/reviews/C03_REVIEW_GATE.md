# C03 — Nghiệm thu kỹ thuật sau sửa lifecycle (11/11/2026)

**Gate hiện tại: PASS. R1 RESOLVED theo policy USER_CONFIRMED ngày 11/11.** C03 giữ In Review để nhóm review bản sửa; không tự đánh dấu Done hoặc chứng nhận tích hợp/Fuji. Các kết luận BLOCKED bên dưới là lịch sử, được giữ nguyên để truy vết.

## Nguyên nhân và implementation

Baseline 053b0f5004662708e467211254fb1ff7f19202a6, source gốc 51d97be. Bộ đếm active organization assignments và quyền AccessControl có thể thay đổi độc lập; code cũ tự grant khi count đi từ 0 lên 1 nên vô tình bỏ qua ý định global revoke/renounce. Counter không sai số học; thiếu trạng thái lưu ý định thu hồi.

Theo yêu cầu mới của Khoa, bổ sung private mapping _relayerPermissionWithdrawn. Override các hàm public grantRole/revokeRole/renounceRole sẵn có, gọi super trước để giữ authorization/confirmation OpenZeppelin. Global revoke/renounce RELAYER_ROLE đặt cờ ngay cả khi role đã vắng mặt. Chỉ grantRole hợp lệ xóa cờ. Các helper lifecycle vẫn cập nhật count chính xác nhưng không tự grant nếu cờ đang bật. Internal _revokeRole do không còn active org không bật cờ: normal deactivate/reactivate khi chưa explicit withdrawal vẫn hoạt động như trước. Revoke một assignment không làm mất quyền của org khác; global revoke cố ý chặn mọi assignment của wallet. Register/rotate/reassign cũng không xóa explicit withdrawal; muốn tái dùng wallet phải admin grantRole tường minh. Org scope và trạng thái active vẫn là điều kiện bắt buộc sau grant.

Không sửa recordCaseVerification, replay/digest/sequence/guard ordering. Không đổi ABI, errors/events/getters hoặc output order. Full compiler ABI trước/sau bằng nhau; 4 draft entries D49 khớp. Thêm storage và đổi bytecode; contract non-upgradeable nên mọi deployment sau phải dùng artifact mới, không dùng bytecode cũ hoặc coi contract đã deploy là được vá.

## Bằng chứng kiểm thử cuối

- Compile bằng `node node_modules/hardhat/dist/src/cli.js compile` trong contracts/: PASS, compiled 1 changed Solidity file, solc 0.8.28 / paris / optimizer enabled 200.
- Full suite `node node_modules/hardhat/dist/src/cli.js test`: **55 passing**, 44 tests có sẵn + 11 regression review. Không failures/skips trong kết quả chạy.
- Review tests chuyển từ characterization sang policy assertions. Bao phủ revoke/renounce trên single/shared relayer; ba chu kỳ deactivate/reactivate không tự grant; explicit admin grant khôi phục; all-org cycles qua count=0, rotate đi/về, revoke lặp khi role vắng mặt, grant lặp; unauthorized grant/revoke/renounce, direct grant không bypass org; registration không xóa withdrawal; org-specific revoke không ảnh hưởng quyền org còn lại. Tests cũ xác nhận normal lifecycle/rotation và toàn bộ negative/privacy/replay/sequence không regression.
- `node docs/fixtures/c01/verify-c01.mjs --ethers`: PASS static, canonical UTF-8/SHA-256, Keccak, encode/decode. Java agreement NOT RUN; candidate label trong fixture không được đổi thành Java-certified.
- Python so sánh artifact ABI với snapshot trước sửa: PASS full ABI equality; draft ABI: PASS 4/4 với compiler metadata internalType được cho phép bổ sung.
- Build-info source bytes khớp file hiện tại và settings solc/paris/optimizer: PASS.
- git diff --check: PASS. Dependencies/artifacts/cache được ignore, không stage.
- Fuji/deploy/real keys/backend HTTP/Java/C06: NOT RUN, ngoài C03.

## Quyết định và bàn giao

R1 đã xử lý bằng implementation và regression đúng policy user chốt; không còn OPEN authorization blocker C03. orgRef=0 giữ OrganizationNotActive theo D49. Đối chiếu bàn giao PR #7 và privacy caveats trong lịch sử vẫn áp dụng; không truy cập lại GitHub. Không coi blockchain chứng minh sự thật hay bảo mật nội dung tùy ý relayer gửi.

Dũng review public AccessControl overrides, sticky withdrawal, normal lifecycle và 11 tests; tiếp tục dùng ABI hiện hành, không cần sửa decoder do thay đổi này. Minh không phải đổi DTO/UI. Với quản trị relayer, dùng org-specific revoke để thu hồi một assignment; dùng global revoke để chặn wallet trên mọi org; dùng grantRole hợp lệ để explicit restore, đồng thời vẫn phải có assignment active. Đã ghi bổ sung policy vào DECISION_LOG và BLOCKCHAIN_ARCHITECTURE, không sửa D48/D49.

Đủ điều kiện kỹ thuật contract để chuẩn bị C04 khi được giao. C04 vẫn cần preflight chain/address/key custody/manifest riêng; không deploy trong lượt này. C03 vẫn In Review cho review nhóm bản fix; hoàn tất nghiệm thu nội bộ và commit local riêng, không push/PR/merge. Actual human effort chưa có số liệu.

---

# Lịch sử review trước bản sửa

# C03 — Kết quả review tiếp nối ngày 11/11/2026

**Gate hiện tại: BLOCKED — chờ quyết định lifecycle authorization.** C03 giữ In Review. Phần lịch sử bên dưới được bảo toàn; các giới hạn môi trường/đọc bàn giao của lượt trước đã được khắc phục và không còn là blocker hiện tại.

## Baseline và môi trường

Nhánh khoa, HEAD 053b0f5004662708e467211254fb1ff7f19202a6; contract 51d97be963d44cc612f9dd84e9135422d30d9aa8. Không fetch/merge/rebase/reset trong lượt tiếp nối. Hai thay đổi review trước được giữ lại. Không sửa AidRegistry.sol, manifest hay package-lock.json.

User cho phép cài dependencies C03. Node v24.22.1, pnpm 11.25.0 có sẵn; npm không có trong PATH. Đã chạy `pnpm --package=npm@11.6.2 dlx npm ci --ignore-scripts --no-audit --no-fund` trong contracts/: thành công, 261 packages theo package-lock.json; không global install, không nâng cấp. npm công cụ chạy tạm qua pnpm cache. Lifecycle install scripts không chạy. Warnings deprecated inflight/glob được giữ nguyên theo lockfile, không tự cập nhật. Không audit vulnerabilities nên không kết luận dependencies sạch lỗ hổng.

## Kết quả thực thi độc lập

| Kiểm tra | Kết quả thực tế |
|---|---|
| `node node_modules/hardhat/dist/src/cli.js compile` trong contracts/ | PASS: Compiled 2 Solidity files, solc 0.8.28, paris; lần compile này thực sự tạo artifact mới |
| `node node_modules/hardhat/dist/src/cli.js test` | PASS 51 Mocha tests: 44 có sẵn (smoke 3 + happy/privacy 5 + negative 36) và 7 review mới; không failing/skipped trong output |
| `node docs/fixtures/c01/verify-c01.mjs --ethers` | PASS static, UTF-8/SHA-256, Keccak, ABI encode/decode; Java agreement NOT RUN |
| Compiler ABI so với 4 entries C01 | PASS names/types/order/indexed/stateMutability/outputs của recordCaseVerification, CaseVerified, getEventRecord, getCaseSummary. So sánh các trường draft, cho phép compiler bổ sung internalType; không so raw JSON equality |
| Build-info | PASS source bytes chính xác (gồm CRLF), solcVersion 0.8.28, paris, optimizer enabled/runs 200; source key project/contracts/AidRegistry.sol |
| ABI public boundary | PASS không payable/receive/fallback; privacy tests và envelope log thực sự đã chạy |
| Negative/guard ordering | PASS T02..T12: unknown/admin/wrong-org/inactive/revoked/rotated, replay và conflict trước case guards, input/schema/uint bounds |
| Fuji/backend HTTP/Java/C06 | NOT RUN, ngoài phạm vi; Hardhat là local EDR, không bằng chứng Fuji |

ABI CaseVerified được xác nhận từ artifact và runtime: 3 indexed refs orgRef/caseRef/eventId, tổng 4 topics, 128 bytes data uint64/bytes32/uint16/uint64; calldata 196 bytes. Selector 0x1bd1fadf, topic0 0xc52bfb1c97b1de28337fe01b8bf6dce1267ee4b6f337c271935032cdf0629ea2. Golden commitment và payloadDigest khớp D49 qua checker và contract test; không khẳng định byte parity Java.

## Lifecycle: đã tái hiện, không còn là nghi vấn thuần tĩnh

File mới `contracts/test/AidRegistry.review.test.ts` chứa 7 characterization tests. Chúng assert hành vi hiện tại để tái hiện bằng chứng; PASS không đồng nghĩa policy được phê duyệt.

1. Admin revokeRole, R phục vụ hai org: deactivate/reactivate một org vẫn leaves hasRole=false dù org active/assigned; recordCaseVerification revert UnauthorizedRelayer, event không tồn tại. Explicit admin grantRole khôi phục việc ghi.
2. R tự renounceRole với hai org: kết quả tương tự (test riêng).
3. Admin revokeRole, R chỉ phục vụ một org: deactivate/reactivate tự cấp lại role và ghi được mà không gọi explicit grantRole.
4. Renounce với một org cũng tự phục hồi sau deactivate/reactivate.
5. Direct grant cho outsider vẫn không vượt org.relayer; sau revokeOrganizationRelayer, direct grant cho R cũng không vượt assignment đã xóa. Không ghi event.
6. Non-admin không grant/revoke, không renounce thay R; R hợp lệ vẫn ghi được.
7. Register thêm org cho R đang globally revoked không khôi phục role nếu count trước đó khác 0.

**Phân loại R1: Medium — lifecycle/availability và semantics thu hồi quyền chưa được chốt.** Không chứng minh outsider tự ghi hoặc vượt tenant scope. Tuy nhiên active assigned relayer có thể mất quyền lâu hơn dự kiến; global revoke/renounce không là persistent stop vì quyền có thể tự phục hồi khi count đi qua 0. Phục hồi này cần admin thao tác organization, không phải attacker tự regrant. Bộ đếm phản ánh active assignments, không đếm role grants: cách gọi “counter lệch” trước đó cần hiểu là hai nguồn trạng thái khác nhau, không khẳng định arithmetic counter bị sai.

Tests cũ có organization-specific revoke/rotation/reactivation nhưng không có direct grant/revoke/renounce lifecycle trên multi-org. 7 tests mới lấp khoảng trống này. Không thấy lỗi ở org-specific revoke path được test.

**Khuyến nghị Dũng/nhóm trước C04:** chốt direct global revoke/renounce có phải persistent emergency stop hay không. Nếu có: Dũng sửa implementation để không tự regrant qua org lifecycle, xác định explicit restore và thêm regression tests theo semantics được duyệt; Khoa review lại. Nếu không: xác nhận bằng văn bản giới hạn đã biết, runbook dùng org-specific revoke/rotation, mô tả rõ explicit grant để khôi phục và nguy cơ tự regrant. Không tự coi phương án này đã được chấp nhận; không sửa contract thay Dũng. Gate giữ BLOCKED trong lúc chờ policy, thay vì kết luận FAIL một yêu cầu chưa được định nghĩa.

## Đối chiếu bàn giao PR #7 do Khoa cung cấp bằng 3 ảnh

Không truy cập GitHub lại. Ảnh là bằng chứng bàn giao do user cung cấp, không phải quyền triển khai. Các yêu cầu cụ thể:

| Khẳng định/yêu cầu Dũng | Kết quả review |
|---|---|
| Contract không đổi trong Part 2, nguồn 51d97be | Xác nhận lịch sử contract và working tree không sửa source |
| PR chỉ gồm 4 file | Cần phân biệt commit Part 2 với toàn PR: ảnh header ghi 73 files, 4 commits; timeline có thêm B04/B05/B06. “4 file” mô tả phần Part 2, không đúng nếu hiểu là toàn diff PR đã merge. Không review backend ngoài C03 |
| +17 negative và +3 privacy, tổng 44 tests | Suite hiện có xác nhận 36 negative, 5 happy/privacy, 3 smoke chạy PASS; review mới nâng tổng thành 51 |
| Guard authorization trước lookup; duplicate trước case guards | Xác nhận runtime qua T02/T03/T04/T06/T11 và T09/T10; schema validation trước duplicate vẫn đúng D49 |
| orgRef=0 deviation | Xác nhận OrganizationNotActive(0), chấp nhận theo D49; không cần đổi logic hoặc decision mới nếu giữ nguyên |
| ABI parity 4/4, decoder layout C05 | Xác nhận artifact và runtime như bảng trên; bàn giao layout, không bắt đầu Java adapter |
| Quyết định direct grant/revoke là blocker hay giới hạn | Đã chứng minh lifecycle; còn OPEN policy và cách xử lý R1. Chưa chấp nhận giới hạn thay nhóm |
| Privacy markers/no payable | Test PASS, nhưng fixed-size bytes32 không tự bảo đảm không PII; phụ thuộc producer off-chain đúng quy trình. Không tuyên bố confidentiality toàn EVM calldata |
| Chưa chạy Fuji/Java/C06, giữ In Review | Giữ đúng; không chạy task ngoài phạm vi |

## Điều kiện đóng gate

Môi trường, compile, tests, artifact và bàn giao đã đủ bằng chứng trong lượt này. **Blocker còn lại duy nhất: R1 cần quyết định được xác nhận và regression expectations theo policy đó; nếu có code fix cần compile/full tests/review lại.** Không cho PASS chỉ dựa 51 tests vì 7 tests mới cố ý tái hiện hành vi còn OPEN. Chưa đủ điều kiện contract để chuyển C04. Không tự đánh dấu Done, commit/push/PR/deploy; không đọc hoặc dùng credentials/private key thật. Local smoke tests dùng dữ liệu giả để kiểm tra preflight, không gửi giao dịch ngoài máy.

---

# Lịch sử lượt review trước (giữ nguyên để truy vết)

# C03 — Review gate trước Fuji pilot

Ngày: 11/11/2026. Reviewer: Khoa/Codex. **Gate: BLOCKED**. C03 giữ In Review; không sửa trạng thái task của Dũng/Minh. Đây là review tĩnh và fixture validation, không phải xác nhận compile/test hay cho phép deploy.

## Baseline và đồng bộ

Repo D:\Project\AidBridge, nhánh khoa. Working tree ban đầu sạch. Fetch origin thành công; fast-forward từ b53bc73 đến 053b0f5004662708e467211254fb1ff7f19202a6, không conflict, không reset/rebase. Git history có merge PR #5 tại e93c16d và PR #7 tại 58aecd7; cả hai thuộc origin/main. Main mới nhất còn có PR #8; không loại bỏ các thay đổi đã merge. Contract được review từ commit 51d97be963d44cc612f9dd84e9135422d30d9aa8; contracts/contracts/AidRegistry.sol không bị reviewer sửa.

## Giới hạn đọc PR #7

GitHub connector trả 404; trình duyệt in-app chưa đăng nhập cũng trả Page not found. GitHub CLI không có trong PATH. Phương án đọc Git credential để gọi API bị automatic approval review từ chối và không thực thi; không thử vượt chặn. Vì vậy chưa đọc được PR description/comments/reviews/CI trực tiếp. Không suy đoán nội dung comment. Bàn giao dưới đây lấy từ TASK_BACKLOG.md mục C03 Part 2 và TEST_STRATEGY.md trong Git, không phải trích PR conversation. Cần người có quyền cung cấp nội dung PR hoặc kênh đọc GitHub được cấp quyền trước khi đóng gate.

## Checklist review tĩnh

| Hạng mục | Kết quả / bằng chứng |
|---|---|
| Write ABI D48/D49 | Source đúng recordCaseVerification(bytes32,bytes32,bytes32,uint64,bytes32,uint16), nonpayable, không outputs; orgRef/caseRef/eventId/chainSequence/evidenceCommitment/schemaVersion đúng thứ tự. Chưa compiler-verified. |
| CaseVerified | 3 indexed bytes32 orgRef/caseRef/eventId; data uint64,bytes32,uint16,uint64 timestamp. Timestamp từ block.timestamp. Expected 4 topics, 128 bytes data, 196 bytes calldata có assertions trong AidRegistry.test.ts. Chưa chạy assertions. |
| Getters/digest | Flat outputs khớp mô tả D49; payloadDigest bind bytes4 operation + orgRef/caseRef/sequence/commitment/schema. eventId làm mapping key toàn registry. Cần compiler artifact parity để xác nhận. |
| Authorization | OpenZeppelin AccessControl; constructor cấp DEFAULT_ADMIN_ROLE cho initialAdmin khác zero (không nhất thiết deployer). Admin không tự có quyền ghi; phải vừa có RELAYER_ROLE vừa đúng org.relayer. Org chưa registered/inactive bị chặn trước lookup event. |
| Guard ordering | org/caller -> zero/schema -> duplicate digest -> case ownership -> already verified -> sequence -> write/emit. D49 liệt kê ownership/sequence/state trong cùng bước 4; existing tests/source xác định already verified trước sequence. Không thấy lệch thứ tự giữa các nhóm guard. |
| Replay/sequence | Cùng eventId/digest: EventAlreadyRecorded; khác digest: EventIdConflict, sau structural validation và trước case guards. Case mới phải sequence 1; case verified không ghi lại. Revert trước state writes. |
| Privacy | Source/getters chỉ opaque refs/commitments, booleans, counters, operation digest và relayer addresses; không field PII/URL/salt. Không external call/value flow tùy ý. Fixed-size ABI và marker tests hỗ trợ kiểm tra envelope, không chứng minh mọi bytes32 đều không chứa PII. Relayer phải tạo random refs và commitment đúng quy trình off-chain. Blockchain công khai metadata và liên kết refs; không chứng minh sự thật giao hàng hay verifier identity. |
| Phạm vi | Chỉ verification slice. Milestone functions và C01-T17 thuộc C06; Java/TS interoperability, receipt recovery thuộc C05/C07/C08, không phải lỗi thiếu C03. |

## Phản hồi các điểm bàn giao lưu trong repo

1. Rerun compile/test/fixture: static fixture PASS; compile/full suite/ethers/artifact NOT RUN như bảng dưới. Không dùng 44 passing do Dũng báo để thay kết quả độc lập.
2. Guard order: source và negative tests có assertions unauthorized replay, inactive org replay, revoked/rotated caller, duplicate trước case guards, invalid schema trước duplicate. Chưa chứng minh runtime vì chưa chạy.
3. C01-T12 orgRef=0: OrganizationNotActive(0) là hệ quả hợp lệ của D49 authorization-first, vẫn fail-closed. Không đề xuất sửa contract chỉ để khớp expectation cũ InvalidZeroInput; cần đọc D49 cùng test-expectations.json lịch sử.
4. Direct AccessControl role operations: cần Dũng xác nhận cách vận hành và bổ sung regression coverage trước gate PASS; xem phát hiện dưới.
5. Decoder C05: dùng compiler ABI full và đối chiếu 4 draft entries; CaseVerified 3 indexed refs, data 128 bytes. Receipt status=1 chưa đủ; kiểm tra chain/address/topics/payload với job. Không dùng draft để deploy.

## Phát hiện và mức độ

### R1 — Medium: global revoke/renounce và bộ đếm active organizations không đồng bộ

Phân tích source, chưa tái hiện runtime: register A và B cùng relayer R làm count=2 và role=true. Admin revokeRole(RELAYER_ROLE,R), hoặc R renounceRole, làm role=false nhưng count vẫn 2. Sau đó deactivate A giảm count xuống 1; reactivate A tăng 1 lên 2 nhưng _incrementActiveRelayer chỉ _grantRole khi currentCount==0, nên role vẫn false dù org active và assigned relayer. Ngược lại nếu count về 0 rồi register/reactivate, role có thể được cấp lại sau global revoke. Đây là lifecycle/operational semantics, không phải bypass org scope: direct grant cho stranger vẫn không vượt được org.relayer check.

Đề xuất Dũng: test direct revoke + multi-org deactivate/reactivate, renounce, direct grant outsider và restoration qua lifecycle; xác định global revoke là emergency stop hay chỉ dùng organization-specific revoke/rotation. Không tự chốt policy hoặc sửa contract. Nếu giữ nguyên, cần quyết định rõ runbook hạn chế thao tác và cách phục hồi quyền, kèm tests; nếu sửa logic cần review lại. Chưa coi đây là giới hạn đã được nhóm chấp nhận. Chặn sign-off an toàn cho đến khi có bằng chứng/quyết định rõ.

### R2 — Giới hạn privacy tests

Marker fixture tests kiểm tra dữ liệu mẫu đã được tạo đúng; chúng không ngăn authorized relayer nhét PII vào bytes32 hoặc gửi calldata có dữ liệu thừa. Public EVM calldata không thể được coi là kênh bảo mật. Cần backend producer/commitment validation ở bước tích hợp; không đòi contract chứng minh nội dung vật lý hoặc confidentiality tuyệt đối. Không thấy source chủ động đưa PII lên chain.

## Kiểm tra thực tế trong lượt review

| Kiểm tra | Kết quả |
|---|---|
| node docs/fixtures/c01/verify-c01.mjs | PASS: layout, canonical UTF-8/SHA-256, ID mapping, test labels |
| node docs/fixtures/c01/verify-c01.mjs --ethers | Static phần đầu PASS; Keccak/ethers NOT RUN vì dependency C02 không có |
| npm run compile; npm test | NOT RUN: npm không có trong PATH môi trường thực thi; contracts/node_modules không tồn tại |
| Cài dependencies để phục vụ tests | Không thực thi: automatic approval review từ chối npm ci vì chưa có ủy quyền cài dependencies theo AGENTS |
| Compiler artifact parity 4/4 | NOT RUN: contracts/artifacts/contracts/AidRegistry.sol/AidRegistry.json không tồn tại |
| Negative/privacy/guard/replay runtime | NOT RUN; đã đọc source và assertions, không gọi là PASS runtime |
| Fuji, private keys, transaction, backend/Java | NOT RUN, ngoài phạm vi lượt này |

## Điều kiện chuyển sang C04

Chưa đủ điều kiện contract để chuyển sang C04. Cần (1) môi trường Node/npm + dependencies theo lockfile được cho phép; compile và toàn suite local thành công, xác minh artifact/source settings Solidity 0.8.28, paris, optimizer 200 và parity ABI; (2) giải quyết R1 bằng tests và policy được xác nhận hoặc contract fix của Dũng rồi re-review; (3) đọc trực tiếp PR #7/comments và phản hồi mọi yêu cầu chưa thể thấy. Chỉ khi đủ bằng chứng mới đổi gate sang PASS; không tự đổi C03 Done. C04 còn cần các preflight/manifest/secret custody riêng khi được giao, không thực hiện trong review này.

Không deploy, không dùng private key, không sửa logic contract/backend/frontend; chưa commit/push/PR. Actual human effort chưa có dữ liệu để ghi giờ.
