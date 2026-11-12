# C07 — Worker safety review, 12/11/2026

## Kết luận hiện hành

**PASS về kỹ thuật C07, đã xác minh lại tại commit `4795c9a368fd33c725ad3b182b8cb0eb5fc78dc7`**, có bằng chứng CI compile + 22 unit tests + 18 integration tests, 0 failures/errors/skips. Giữ task **In Review** cho nhóm nghiệm thu, không tự đánh Done. Signed persistence/recovery, one in-flight/signer, nonce và session-loss fencing đã chạy trên PostgreSQL thật và Hardhat local; đây không phải Fuji end-to-end. C05 vẫn PARTIAL: chưa có job B05 thật do worker xác nhận trên Fuji. Không thay contract/ABI hoặc ví. Các điều kiện rollout và bàn giao bên dưới vẫn bắt buộc.

## Git và điều kiện merge

PR9 và PR11 đã merge trước đó. **PR10 đã merge vào main** tại `d38fadb5e0cdf81ca0ef1e894b569e3701b9edd7` sau xác nhận mới của người dùng và CI safety PASS. Nhánh khoa đã merge main an toàn, giữ nguyên lịch sử. Diff [PR12](https://github.com/chidungbrvt-bit/AidBridge/pull/12) chỉ còn 15 files C07/evidence/docs; không lặp B07/C06 hoặc PR9/PR11. Không sửa nhánh Dũng, force-push hoặc bypass protection.

Auto-review ban đầu đã từ chối merge/push khi chưa đủ phép và bằng chứng. Người dùng sau đó cho phép rõ push/CI/PR và merge lần lượt khi đủ gate. Đã push khoa, tạo PR12, chạy CI và sửa lỗi fixture tới PASS. `application.properties` vẫn mặc định BLOCKCHAIN_ENABLED=false; ConditionalOnProperty và 2 tests xác nhận không có adapter/worker/scheduler nếu không opt-in. Merge không kích hoạt worker hoặc cấp secret. PR12 bàn giao safety fix để tích hợp ngay sau baseline PR10; trạng thái GitHub và checks tại link PR là nguồn kết quả merge hiện hành.

## Các lỗi và cách sửa

1. **Receipt chưa rõ bị đánh FAILED:** giữ sửa từ `8ce40cec`: hết retry budget chỉ dừng tự rebroadcast; giữ SUBMITTED, hash/nonce/signed bytes và polling với RECEIPT_PENDING_OPERATOR_ATTENTION. Receipt đến muộn vẫn có thể CONFIRMED. retryCount là bộ đếm chung, không đồng nhất với số lần broadcast.
2. **Nhiều job cùng signer:** khóa PostgreSQL advisory theo chain + lowercase signer, dùng chung giữa mọi process. Khóa giữ trên connection auto-commit riêng suốt RPC; không giữ SQL transaction mở. Claim không nhận job khác khi signer còn PREPARED/SUBMITTED. V13 bổ sung unique partial indexes cho một open attempt/signer và một open attempt/job, làm lớp bảo vệ khi session mất hoặc writer vi phạm protocol.
3. **Lease hết hạn / stale worker:** phiên đang giữ signer lock không bị thay thế chỉ vì lease của job hết hạn. Kiểm tra session trước ký, persist, broadcast và sau RPC; mất phiên thì fail-closed, không tiếp tục cập nhật hoặc gửi ở bước tiếp theo. Recovery dùng journal PREPARED, exact bytes/hash, không tạo nonce/event mới. Một RPC đã phát ra không thể bị thu hồi bằng DB fencing; nếu mất session giữa kiểm tra và RPC, recovery chỉ được gửi lại cùng bytes đã commit. Không tuyên bố có thể ngăn một RPC đang bay tới node.
4. **Nonce node lỗi thời:** lấy max(pendingNonce, max journaled nonce + 1), bao gồm cả CONFIRMED/REVERTED; không tái sử dụng nonce lịch sử khi node trả nonce cũ. UNIQUE(chain, signer, nonce) vẫn giữ.
5. **Binding và thứ tự:** recovery kiểm tra signer/chain/registry và hash trước broadcast; poller không xử lý attempt của signer/registry khác. CASE_VERIFIED bắt buộc sequence 1. Claim không vượt job trước cùng case chưa CONFIRMED; worker vẫn chỉ hỗ trợ CASE_VERIFIED, không lấy các milestone C06.

## Điều kiện vận hành và migration

- Tất cả worker dùng chung database và protocol signer lock; pooler phải dùng **session pooling**, không transaction pooling. Pool cần đủ connection: ít nhất một connection giữ lock và một connection cho short transaction/SQL của worker; không giữ nhiều signer locks lồng nhau.
- V13 không xóa/sửa attempt có sẵn. Nếu DB có nhiều open attempts cùng signer/job, migration phải fail; Dũng dừng worker, đối chiếu receipts/attempts và xử lý rõ nguyên nhân trước khi chạy lại. Không đổi status hoặc xóa journal chỉ để migration thành công.
- Attempt SUBMITTED có job FAILED do integrity mismatch tiếp tục chặn signer để điều tra; không tự giải phóng nonce/signer khi chưa rõ outcome.
- AES-GCM key phải ổn định qua restart. Không chạy hai code version cũ/mới đồng thời; dừng worker cũ trước rollout. Session lock không bảo vệ signer bị sử dụng bên ngoài ứng dụng bằng cùng private key.

## Regression và cách kiểm thử

Tests giữ từ lượt trước: late receipt sau restart; RPC outage/duplicate broadcast; hai workers tranh cùng job. Tests mới: hai cases cùng signer chờ CONFIRMED; nonce node bị lùi; lease hết hạn khi thread ký còn chạy; terminate đúng PostgreSQL lock session để fence worker cũ; registry mismatch khi recovery; CASE_VERIFIED sai sequence; unique open signer/job tại DB. Tests disabled configuration xác nhận không tạo adapter/worker/scheduler khi không opt-in hoặc enabled=false.

Test double chỉ ký offline với public Hardhat test accounts. Fixture retirement chỉ xảy ra trong schema test biệt lập, không phải quy trình sửa DB vận hành.

Workflow `.github/workflows/c07-worker-safety.yml`: Ubuntu runner, JDK21, PostgreSQL16 tạm thời, Hardhat localhost chain31337; không Fuji, DPAPI hoặc secret thật. Chạy compile + unit tests liên quan và CaseVerifiedWorkerIT, BlockchainTransactionPersistenceIT, CaseVerifiedHardhatE2EIT; kiểm tra XML để không coi skipped IT là PASS. Chỉ lưu test reports, không upload ví, signed bytes thật hoặc cache. Mật khẩu PostgreSQL trong workflow chỉ là giá trị test của container dùng một lần.

**Kết quả thực chạy:** [CI run43191039984](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43191039984), commit903f952: BUILD SUCCESS, unit22/22, worker IT14/14, persistence IT3/3, Hardhat local E2E1/1; tất cả không skip/error/failure. Bước kiểm tra XML no-skips và toàn job PASS. Java/DB chạy trên GitHub Ubuntu, không cài môi trường Windows; local Java NOT RUN. `git diff --check` PASS trước commit. Các kết quả lượt trước (không chạy lại vô ích): 31 client tests PASS, Hardhat Mocha147 PASS/1 pending, C04 readback và F02 checker PASS; không dùng các kết quả này thay Java worker tests.

## Fuji và bàn giao Dũng

- Chain43113, registry `0x015D941c679b0c402c8262BE237adAD36cEf6726`, ABI D49/schemaVersion1.
- orgRef `0x2395bfe7f91467046a39bd8440313164c6442839b2be6698832b7b4941b80a46`.
- Admin `0x131DD6d404f8D0B118D6DB122194bc15b4E4601E`; old Relayer `0x559994839f19F40ac704918C3FF1a913AC02dC01`; new Relayer `0x95a9564899849CF566DE5713205C4B747F361f55`.
- Readback lượt trước: registered/active, assigned old Relayer; balances Admin0.348775352, old0.149700527, new0 AVAX; latest=pending nonce2/1/0. Đây là lịch sử, không phải bằng chứng DB trên máy Dũng không có pending obligation.
- **Người dùng yêu cầu hoãn hoàn toàn rotation/funding sang cuối ngày; không phải blocker C07.** Chưa rotate/fund ở lượt này. Thiếu bằng chứng worker cũ đã dừng và không còn PREPARED/SUBMITTED/signed attempts của old signer trên DB Dũng hoặc nghĩa vụ organization khác. Khoa không có DB được phép để tự lấy. Không suy từ nonce, số dư hoặc local E2E rằng đủ an toàn; không yêu cầu Dũng gửi secret.
- Sau khi đủ bằng chứng, Admin updateOrganizationRelayer đúng org, kiểm receipt/roles/org/Admin; sau đó chuyển phần test AVAX còn lại từ old signer, chừa gas. Không cần tạo ví hoặc redeploy. Dũng phải bind org public_ref, signer D54, registry, AES key và job B05 thật trước khi bật worker; chỉ receipt/event đúng mới CONFIRMED. Minh dùng API audit thật, không gắn pilot độc lập vào job chưa xác minh.

## Readiness F07/F08

Chỉ đánh giá, không triển khai UI trong lượt này. F07 client/session đã ở main qua PR11 và B07 đã vào main qua PR10, đủ nền code để bắt đầu ghép API. Kiểm thử real UI vẫn cần donor dev grant D52, published campaign và DB/API được phép, chưa xác minh trên máy Khoa. F08 đủ fixtures F02 R01/D01 để bắt đầu mock; receipt/delivery API thật vẫn là dependency riêng. Không đánh dấu task Dũng/Minh Done.


## CI lần đầu và sửa fixture

PR bổ sung [#12](https://github.com/chidungbrvt-bit/AidBridge/pull/12), commit 0b5d6bb: compile PASS, 22 unit tests PASS; CaseVerifiedWorkerIT 14/14 PASS; Hardhat E2E 1/1 PASS; persistence IT 2/3 PASS. Run [43190514630](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43190514630) FAIL do fixture persistence cố dùng nonce0 đã được test worker trước đó ghi trong cùng schema. Không phải lỗi runtime signer locking; unique nonce đã chặn đúng. Sửa fixture lấy nonce base từ journal, không xóa lịch sử hoặc nới constraint. CI tiếp theo phải xác nhận toàn bộ suite và no-skips trước kết luận PASS. Thêm concurrency cho workflow để tránh chạy trùng push/PR.

Readback Fuji mới lưu tại [c07-relayer-preflight.public.json](../../contracts/deployment/c07-relayer-preflight.public.json): đúng runtime C03/chain43113, old Relayer vẫn assigned/active, pilot receipt status1; không có rotation/funding. Trạng thái DB Dũng vẫn NOT_VERIFIED.


## Kết quả sau khi sửa fixture và điều kiện tiếp tục

Run43191039984 đã PASS toàn bộ 40 tests và compile sau sửa fixture. Không nới constraint, không xóa journal, không thay production code để che lỗi. C07 technical gate PASS cho phạm vi worker hiện hỗ trợ CASE_VERIFIED. Các milestone C06, stress test dài hạn, Fuji DB end-to-end và cross-owner acceptance chưa được suy ra từ kết quả này.

Dũng tiếp nhận V13 + SignerSessionLock, dừng worker cũ trước rollout; kiểm tra duplicates/open attempts và stable AES key, áp dụng session pooling. Cần xác nhận riêng worker/DB không còn nghĩa vụ old signer trước rotation/funding. Chỉ sau đó mới chạy job B05 thật, đối chiếu receipt/event và công bố C05 CONFIRMED. Khoa không có DB được phép để thay Dũng xác minh bước này.


## Nghiệm thu C07 trước tích hợp GitHub

[CI run43191763706](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43191763706) áp dụng đúng SHA `4795c9a368fd33c725ad3b182b8cb0eb5fc78dc7`: Java compile/BUILD SUCCESS, unit22, worker IT14, persistence IT3, Hardhat/PostgreSQL local E2E1, tất cả PASS và 0 skips/errors/failures. Các tests chạy trên merge ref PR sau khi baseline PR10 đã vào main. Bỏ trigger push bị trùng với pull_request để mỗi head chỉ có một run CI chính thức; không bỏ test hoặc required check.

| Acceptance C07 | Bằng chứng thực chạy | Kết quả |
|---|---|---|
| Signer serialization / one in-flight | Hai case cùng signer phải chờ CONFIRMED; concurrent claim; unique open signer/job | PASS |
| Nonce journal / reuse | Stale pending nonce không tái dùng journaled nonce; unique chain/signer/nonce | PASS |
| Signed bytes/hash committed trước send | Persistence IT kiểm tra PREPARED/ciphertext/hash/nonce; worker recovery không ký lại | PASS |
| Retry, restart, unknown outcome | Missing receipt quá budget vẫn SUBMITTED; late receipt sau restart; outage và duplicate broadcast cùng bytes | PASS |
| Lease expiry và fencing | Worker giữ phiên không bị supersede bởi lease expiry; mất đúng DB session chặn persist/broadcast; recovery sau lease expiry | PASS |
| Signer/chain/registry và receipt | Adapter/verifier unit tests, recovery binding mismatch, local on-chain envelope/receipt/getter parity | PASS |
| chain_sequence | CASE_VERIFIED sequence1, query chặn previous chưa CONFIRMED; V12 unique case/sequence; không claim unsupported milestones | PASS cho CASE_VERIFIED; C06 milestone execution ngoài phạm vi |
| Bảo mật/default-off | AES-GCM tests và 2 config tests không tạo signer/scheduler khi chưa opt-in | PASS |

Không có blocker kỹ thuật C07 còn biết trong phạm vi đã kiểm thử. Rollout vẫn phải dùng cùng DB/session pooling, giữ AES key, reconcile duplicates trước V13 và dừng version cũ; đây là điều kiện vận hành, không phải tuyên bố Fuji end-to-end đã chạy. Task giữ In Review theo quy ước nhóm. F07 có code B07/client trên main để bắt đầu ghép, còn môi trường DB/donor/campaign cho real UI tests. F08 mock có thể bắt đầu độc lập. Không triển khai UI trong lượt này.
