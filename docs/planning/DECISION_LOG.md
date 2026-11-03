# Decision Log

Ngày tạo: 03/11/2026. Owner: B. Status: planning baseline. Các approval dưới đây phản ánh lựa chọn trong chat, không phải application đã được triển khai.

## Phân loại và quyền quyết định

- **SOURCE:** PDF/source prompt yêu cầu; ghi trang hoặc mục tương ứng.
- **USER_CONFIRMED:** người dùng đã chọn rõ trong phiên planning.
- **PROPOSED:** design recommendation được đưa vào proposed plan; việc tạo documentation không tự là coding approval.
- **ASSUMPTION:** baseline để tính lịch, cần kiểm tra thực tế.
- **OPEN:** thông tin chưa có, có owner và ngày cần xử lý.

Nếu PDF có một đề xuất và Master Prompt đã yêu cầu dùng nó làm baseline, giữ cả hai nguồn; không sửa nhãn lịch sử thành quyết định có sẵn trong PDF. User instructions mới có thể thay baseline và phải được ghi vào đây.

## Quyết định

| ID | Loại | Nội dung | Nguồn / rationale | Trade-off / ảnh hưởng |
|---|---|---|---|---|
| D01 | SOURCE | Organization-first; beneficiary không cần account/wallet | PDF pp.3,5,7,19 | Staff vận hành thay, phải có consent và accountability |
| D02 | SOURCE | Sáu P0 và BR-01..12 | PDF pp.8,14-15; Master Prompt §§6,9 | Không cắt separation, privacy, goods lifecycle |
| D03 | SOURCE | Essential goods, no real-money/token/NFT/L1 | PDF pp.3,9-11; user constraints | Không fintech settlement |
| D04 | SOURCE | Ba người, deadline 25/11/2026 | PDF pp.1,15-16 | Capacity phải tính cả học/test |
| D05 | SOURCE | Modular monolith; preferred stack | PDF pp.11-13; Master Prompt §4 | Đủ nhỏ cho sinh viên; không message broker |
| D06 | USER_CONFIRMED | 20 giờ/người/tuần | Capacity choice | 64h/người theo lịch 2h weekday/5h weekend; 56h planned + 8h buffer |
| D07 | USER_CONFIRMED | Backend giữ khóa Fuji | Signing choice | Dễ dùng; trust vào backend, không user wallet signatures |
| D08 | USER_CONFIRMED | Evidence fixtures có version | Evidence choice | Không upload UI; private fixture access và BR-09 vẫn bắt buộc |
| D09 | USER_CONFIRMED | Nhiều loại hàng/campaign | Goods choice | Cần line-item targets và locks; không được âm thầm giảm còn một loại |
| D10 | USER_CONFIRMED | Tài khoản riêng provision sẵn | Account choice | Không signup/reset/invitation UI; phải có distinct identities |
| D11 | USER_CONFIRMED | Public demo, tổng budget tối đa $20 | Hosting choice | Chỉ là budget preference; chưa mua hoặc deploy |
| D12 | USER_CONFIRMED | Chưa có NGO interview contacts | Validation choice | Chưa thể nói đã có partner hoặc validated impact |
| D13 | PROPOSED | Hardhat 3 + TypeScript tests; Web3j Java | Shared TypeScript skill, direct Java integration | Một contract toolchain; ABI compatibility test bắt buộc |
| D14 | PROPOSED | Session auth + CSRF, same-origin proxy | Giảm JWT lifecycle/refresh complexity | Một backend replica; restart có thể yêu cầu login lại |
| D15 | PROPOSED | One case/one campaign; fixed item units; integer quantities | Giới hạn multi-goods complexity | Không warehouse/catalog/unit conversion/cross-campaign transfers |
| D16 | PROPOSED | PostgreSQL outbox, persisted signed tx trước broadcast | Crash-safe retry không Kafka | Một in-flight tx/signer có thể làm chậm nhưng đơn giản |
| D17 | PROPOSED | Non-upgradeable registry; admin + org-scoped relayer | Giảm proxy risk và learning | Sửa contract cần deployment version mới |
| D18 | PROPOSED | Random bytes32 refs + salted evidence commitments | BR-08 và chống dictionary attack | Public org/time linkability còn tồn tại |
| D19 | PROPOSED | Business completion độc lập chain sync | Không làm workflow ngoài đời phụ thuộc RPC | UI vẫn phải cho thấy missing/failed anchors; submission cần chain evidence thật |
| D20 | PROPOSED | Minimal dispute API; no full dispute UI | BR-07 không thể bị hoãn cùng P1 interface | Scope nhỏ nhưng phải resolve/void/review có audit |
| D21 | PROPOSED | Railway FE + BE + PostgreSQL | Một provider, private networking | Usage pricing biến động; kiểm tra cost controls trước purchase |
| D22 | PROPOSED | Milestone/receipt business records immutable | Không che sửa đổi evidence/quantities | Correction qua void/replacement trong dispute, không PATCH confirmed records |
| D23 | ASSUMPTION | 56h task/người + 8h buffer | 23 ngày, 17 weekdays + 6 weekend days | Optimistic cho novice; dùng milestone để cắt UI/P1 |
| D24 | ASSUMPTION | Demo chỉ dữ liệu giả, small traffic | User safety constraints | Không production readiness hoặc legal compliance claim |
| D25 | PROPOSED | Dedicated demo environment: auto-expire/reset data sau submission | Data minimization; không PII thật | Final retention duration cho pilot thật vẫn OPEN |

## Các điểm cần xử lý trước coding / trước pilot

| ID | Câu hỏi hoặc gate | Owner | Hạn | Default / nếu chưa giải quyết |
|---|---|---|---|---|
| O01 | Người dùng phê duyệt task coding đầu tiên và các technical proposals | Nhóm + user | Trước scaffold | Dừng ở docs; hiện user đã yêu cầu thực thi proposed planning workspace |
| O02 | Patch versions tương thích Java/Spring/springdoc/Node/Next/Hardhat/Solidity/Web3j | B+C | 06/11 setup | Dùng stable compatible families trong System Design; không prerelease |
| O03 | Có 2-3 người làm thiện nguyện để interview không? | F | 06/11 | Ghi unvalidated; không chặn technical prototype vô thời hạn |
| O04 | Ai giữ deploy-admin secret và billing account? | C/F | Trước deployment | Chưa deploy/mua; không dùng seed phrase cá nhân có tài sản |
| O05 | Provider eligibility, card/tax/usage cap có giữ tổng dưới $20 không? | F | Trước purchase | Local/video fallback; không vượt budget vì cố public |
| O06 | Competition link, deadline giờ cụ thể, timezone, video length, track rules | F | 06/11; recheck 23/11 | Chỉ dùng 25/11 làm planning date, chưa xác nhận giờ đóng cổng |
| O07 | Người chịu trách nhiệm vận hành dispute ngoài đời | F + NGO | Trước real pilot | Demo Platform Admin là mô phỏng, không authority pháp lý |
| O08 | Consent, retention, lawful processing, minors và evidence requirements | NGO + chuyên gia phù hợp | Trước dữ liệu thật | Không nhận dữ liệu thật; không coi privacy checklist là xác nhận pháp lý |

## Nguồn kỹ thuật đã đối chiếu

Truy cập khi planning 03/11/2026; service/version information cần kiểm tra lại trước setup/purchase.

- [Avalanche Primary Network](https://build.avax.network/docs/primary-network): Fuji 43113, RPC, explorer.
- [Avalanche C-Chain RPC](https://docs.avax.network/docs/rpcs/c-chain): RPC semantics; không giả định Ethereum-style block-count finality.
- [Hardhat testing](https://hardhat.org/docs/guides/testing), [Node support](https://hardhat.org/docs/reference/nodejs-support).
- [Web3j transaction managers](https://docs.web3j.io/4.14.0/smart_contracts/interacting_with_smart_contract/).
- [OpenZeppelin AccessControl](https://docs.openzeppelin.com/contracts/5.x/access-control).
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html).
- [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [springdoc compatibility](https://springdoc.org/).
- [Railway pricing](https://docs.railway.com/pricing/plans), [cost controls](https://docs.railway.com/pricing/cost-control).

Không sao chép khẳng định pháp luật trong PDF thành legal advice; technical demo chỉ dùng simulated data.

## Change record

| Ngày | Thay đổi | Trạng thái |
|---|---|---|
| 03/11/2026 | Đọc 21 trang PDF và hai prompts; ghi các lựa chọn user | Hoàn tất khảo sát |
| 03/11/2026 | User yêu cầu thực thi proposed plan chuẩn bị workspace | Documentation authoring được phép; application chưa bắt đầu |
