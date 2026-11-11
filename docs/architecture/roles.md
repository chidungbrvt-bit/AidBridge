# Roles, entities và tính năng thực tế của AidBridge

Ngày đối chiếu 07/11/2026. Tài liệu dành cho người đọc backend, dựa trên source hiện tại, API contract, Business Workflows và D41/A.

## 1. Cách đọc tài liệu

**Role của người dùng** quyết định tài khoản được làm gì, trong tổ chức nào. **Entity** đại diện cho dữ liệu nghiệp vụ được lưu, chẳng hạn một hồ sơ hỗ trợ, một nhu cầu hoặc một người thụ hưởng. Các bảng dưới nối ba phần với nhau theo hướng người dùng thực hiện tính năng, backend dùng class xử lý, rồi ghi/đọc entity tương ứng.

| Nhãn | Ý nghĩa |
|---|---|
| Có code | Source hiện có class/method xử lý tính năng. Cần đối chiếu kết quả test và cấu hình để xác nhận chạy thực tế |
| Theo thiết kế | Tính năng được mô tả trong API/workflow, class xử lý nghiệp vụ còn thuộc task tiếp theo |
| Dữ liệu hỗ trợ | Dữ liệu được ghi/đọc bên trong một tính năng, thường không có nút hay endpoint CRUD riêng |

Evidence hiện có của lượt B04 gồm 41 test đã chạy và pass về validation, fixture catalog, error contract và security regression. Các test PostgreSQL/HTTP B04 còn SKIPPED trong terminal. B02/B03 hoàn tất là kết quả người dùng đã báo. Tài liệu này mô tả khả năng của code và thiết kế, không xác lập thêm kết quả test hoặc chuyển trạng thái task.

## 2. Role được lưu ở đâu

| Cấp quyền | Nơi lưu | Giá trị | Ý nghĩa thực tế |
|---|---|---|---|
| Nền tảng | `User.platformRole` | `USER`, `PLATFORM_ADMIN` | Xác định quyền cấp toàn hệ thống của tài khoản |
| Tổ chức | `OrganizationMembership.roles` | `ORG_ADMIN`, `VOLUNTEER`, `VERIFIER`, `DELIVERY_CONFIRMER` | Xác định công việc tài khoản được làm trong một tổ chức cụ thể |
| Donor capability | `donorEnabled` trong thiết kế và `MeResponse` | Cấp rõ ràng cho tài khoản ACTIVE | Cho phép tham gia các thao tác đóng góp theo thiết kế. Cột `donor_enabled` và mapping grant còn thuộc phần triển khai sau |

Một `User` có thể tham gia nhiều tổ chức. Mỗi `OrganizationMembership` gắn đúng một user với một organization và có tập role riêng. Ví dụ, một tài khoản có thể là `VOLUNTEER` tại tổ chức A và `VERIFIER` tại tổ chức B. Quyền tại A được áp dụng cho dữ liệu của A.

Source xác định các enum và quan hệ tại [User.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/user/entity/User.java) và [OrganizationMembership.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/entity/OrganizationMembership.java).

Một request riêng tư còn phải thỏa mãn trạng thái tài khoản, membership, organization, ownership và trạng thái nghiệp vụ. Trong B04 hiện tại, các API case/evidence yêu cầu user ACTIVE, membership ACTIVE, organization APPROVED và role phù hợp. `creator` là người tạo một case cụ thể, được nhận diện bằng ID user. Nó là điều kiện ownership bổ sung cho role.

## 3. Tính năng chung của tài khoản đăng nhập

| Tính năng người dùng thấy | Dữ liệu liên quan | Class/method thực hiện | Trạng thái |
|---|---|---|---|
| Đăng nhập bằng email/password | `User` | `AuthController.login()`, `AuthService.login()`, `CustomUserDetailsService.loadUserByUsername()` | Có code |
| Duy trì đăng nhập giữa các request | Session và `User` | `SecurityConfig`, `AuthController.login()` lưu security context | Có code |
| Xem thông tin tài khoản và tổ chức mình thuộc về | `User`, `OrganizationMembership`, `Organization` | `AuthController.me()`, `AuthService.me()`, `AuthMapper.toMeResponse()` | Có code |
| Lấy CSRF token để gửi thao tác thay đổi dữ liệu | Session | `AuthController.csrf()`, CSRF configuration trong `SecurityConfig` | Có code |
| Đăng xuất | Session | Logout filter cấu hình trong `SecurityConfig` | Có code |

Các endpoint tương ứng là `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`. Đăng nhập trả `AuthResponse(user)`, còn `/me` trả `MeResponse` với memberships hiện tại. `AuthService.register()` đang tồn tại trong source, nhưng self-registration nằm ngoài P0 và controller hiện không công bố endpoint đăng ký.

Source tại [AuthController.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/auth/controller/AuthController.java), [AuthService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/auth/service/AuthService.java), [CustomUserDetailsService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/auth/security/CustomUserDetailsService.java), [SecurityConfig.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/auth/security/SecurityConfig.java) và [AuthMapper.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/auth/mapper/AuthMapper.java).

## 4. VOLUNTEER — người lập hồ sơ và ghi nhận giao hỗ trợ

Người này tiếp nhận nhu cầu từ hộ gia đình, ghi consent, lập case và chuẩn bị bằng chứng. Khi workflow hàng hóa được triển khai, họ còn ghi nhận việc giao hỗ trợ.

| Tính năng thực tế | Entity/bảng liên quan | Class/method hiện có | Endpoint | Trạng thái |
|---|---|---|---|---|
| Tạo hồ sơ có người thụ hưởng, consent, nhiều nhu cầu và tài liệu | `Beneficiary`, `AidCase`, `CaseNeedItem`, consent/evidence/audit | `CaseController.create()` → `CaseService.create()` | `POST /api/cases` | Có code B04 |
| Xem danh sách hồ sơ của tổ chức | `AidCase` | `CaseController.list()` → `CaseService.list()` | `GET /api/cases` | Có code B04 |
| Mở chi tiết hồ sơ của tổ chức | Case, needs, beneficiary, consent, evidence | `CaseController.get()` → `CaseService.get()` → `CaseMapper.detail()` | `GET /api/cases/{id}` | Có code B04 |
| Sửa hồ sơ do chính mình tạo | `AidCase`, `CaseNeedItem`, `case_revisions` | `CaseController.patch()` → `CaseService.patch()` | `PATCH /api/cases/{id}` | Có code B04 |
| Xem tài liệu mẫu được cấp cho tổ chức | Fixture catalog | `EvidenceController.fixtures()` → `CaseService.fixtureList()` → `EvidenceFixtureCatalog.list()` | `GET /api/evidence-fixtures` | Có code B04 |
| Gắn tài liệu mới hoặc tạo phiên bản kế tiếp | Evidence files/versions/links | `CaseController.evidence()` → `CaseService.appendEvidence()` → `EvidenceService.append()` → `EvidenceRepository` | `POST /api/cases/{id}/evidence-versions` | Có code B04 |
| Tải phiên bản bằng chứng có quyền truy cập | Evidence và access audit | `EvidenceController.content()` → `CaseService.content()` | `GET /api/evidence-versions/{id}/content` | Có code B04 |
| Gửi lại hồ sơ sau yêu cầu bổ sung | `AidCase`, revisions, verification record | `CaseController.resubmit()` → `CaseService.resubmit()` | `POST /api/cases/{id}/resubmit` | Có code B04, bước request-changes thuộc B05 |
| Ghi giao hàng thành công hoặc lần giao thất bại | `deliveries`, `delivery_items`, evidence | Chưa có class xử lý delivery nghiệp vụ | `POST /api/cases/{id}/deliveries` | Theo thiết kế B09 |

Sửa case/evidence/resubmit yêu cầu đồng thời role `VOLUNTEER` và đúng creator. Case phải ở trạng thái được phép chỉnh, request phải có version phù hợp. Trong form sửa hiện tại, các field được hỗ trợ gồm internal/public title, public summary, approximate area, priority và needs. Backend giữ các need lines và evidence versions cũ để truy lại lịch sử.

Source chính tại [CaseController.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/controller/CaseController.java), [CaseService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/service/CaseService.java), [EvidenceController.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/controller/EvidenceController.java) và [EvidenceRepository.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/repository/EvidenceRepository.java).

## 5. VERIFIER — người xác minh hồ sơ độc lập

Người này đối chiếu nhu cầu và tài liệu rồi quyết định hồ sơ có đủ điều kiện tiếp tục hay cần sửa. Người xác minh phải khác người tạo hồ sơ, kể cả khi một tài khoản có cả hai role.

| Tính năng thực tế | Entity/bảng liên quan | Class hiện có hoặc phần còn cần làm | Endpoint | Trạng thái |
|---|---|---|---|---|
| Đọc danh sách/chi tiết case của tổ chức | Case, beneficiary, needs, consent | `CaseController`, `CaseService`, `CaseMapper` | `GET /api/cases`, `GET /api/cases/{id}` | Có code B04 |
| Xem danh sách fixture và tải evidence để kiểm tra | Evidence, access audit | `EvidenceController`, `CaseService.content()`, `EvidenceRepository` | `GET /api/evidence-fixtures`, `GET /api/evidence-versions/{id}/content` | Có code B04 |
| Chấp thuận hồ sơ | `verifications`, `aid_cases`, audit/outbox | Chưa có class thực thi verification action | `POST /api/cases/{id}/verify` | Theo thiết kế B05 |
| Từ chối hồ sơ với lý do | `verifications`, `aid_cases`, audit | Chưa có class thực thi verification action | `POST /api/cases/{id}/reject` | Theo thiết kế B05 |
| Yêu cầu người tạo bổ sung thông tin | `verifications`, `aid_cases`, audit | Chưa có class thực thi request-changes | `POST /api/cases/{id}/request-changes` | Theo thiết kế B05 |
| Mở tranh chấp khi có vấn đề nghiệp vụ | `disputes`, audit | Chưa có class xử lý dispute nghiệp vụ | `POST /api/cases/{id}/disputes` | Theo thiết kế B10 |

Quyết định review trong B05 cần gắn đúng case version và những evidence version đã được xem. Sau V7, resubmit đọc decision `CHANGES_REQUESTED` và thời điểm review gần nhất. Xem [LocalAuditService.changedAfterReview()](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/audit/service/LocalAuditService.java).

## 6. ORG_ADMIN — người điều phối chiến dịch và tiếp nhận hàng

Người này quản lý các bước điều phối hàng hóa trong tổ chức theo workflow được thiết kế. Quyền đọc case/evidence hiện dùng membership hiện tại của chính tổ chức đó.

| Tính năng thực tế | Entity/bảng liên quan | Class hiện có hoặc phần còn cần làm | Endpoint | Trạng thái |
|---|---|---|---|---|
| Xem case, needs, consent và evidence của tổ chức | Các dữ liệu riêng tư của case | `CaseController`, `EvidenceController`, `CaseService` | Các private GET của B04 | Có code B04 |
| Tạo bản nháp chiến dịch từ hồ sơ đã xác minh | `campaigns`, `campaign_items`, case | Chưa có class xử lý campaign nghiệp vụ | `POST /api/campaigns` | Theo thiết kế B06 |
| Công bố chiến dịch để nhận cam kết hàng | Campaign/items | Chưa có class xử lý publish | `POST /api/campaigns/{id}/publish` | Theo thiết kế B06 |
| Ghi số hàng tổ chức thực sự đã nhận | `goods_receipts`, `goods_receipt_items`, contribution | Chưa có class xử lý receipt nghiệp vụ | `POST /api/commitments/{id}/receipts` | Theo thiết kế B08 |
| Mở tranh chấp | `disputes`, case/delivery | Chưa có class xử lý dispute nghiệp vụ | `POST /api/cases/{id}/disputes` | Theo thiết kế B10 |
| Đóng chiến dịch/case khi đủ điều kiện | Campaign, case, confirmed totals, disputes | Chưa có class xử lý close nghiệp vụ | `POST /api/campaigns/{id}/close` | Theo thiết kế B10 |

Đóng case cần đủ confirmed quantities của từng loại hàng và đã xử lý các tranh chấp còn mở. `ORG_ADMIN` là một membership role cụ thể. Tính năng tạo/sửa case vẫn kiểm tra `VOLUNTEER` và creator theo B04 hiện tại. Nếu cùng người cần làm hai công việc, membership phải được cấp các role tương ứng.

Class kiểm quyền hiện có là [OrganizationService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/service/OrganizationService.java). `OrganizationController` hiện là class khung, còn các DTO/mapper organization là prework. Chưa có HTTP flow onboarding/member management được triển khai trong controller đó. Source tại [OrganizationController.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/controller/OrganizationController.java).

## 7. DELIVERY_CONFIRMER — người xác nhận giao hàng độc lập

Người này kiểm tra việc bàn giao và bằng chứng của một delivery. Người xác nhận phải khác người ghi delivery, kể cả khi có nhiều role.

| Tính năng thực tế | Entity/bảng liên quan | Class hiện có hoặc phần còn cần làm | Endpoint | Trạng thái |
|---|---|---|---|---|
| Đọc case và tải evidence được phép của tổ chức | Case/evidence | `CaseController`, `EvidenceController`, `CaseService` | Các private GET của B04 | Có code B04 |
| Xác nhận một lần giao hỗ trợ | `delivery_confirmations`, delivery/items, audit/outbox | Chưa có class xử lý confirmation nghiệp vụ | `POST /api/deliveries/{id}/confirm` | Theo thiết kế B10 |
| Ghi nhận cần kiểm tra lại lần giao | Confirmation, dispute, evidence snapshot | Chưa có class xử lý confirmation/dispute nghiệp vụ | `POST /api/deliveries/{id}/confirm` với decision theo contract | Theo thiết kế B10 |
| Mở tranh chấp | `disputes`, case/delivery | Chưa có class xử lý dispute nghiệp vụ | `POST /api/cases/{id}/disputes` | Theo thiết kế B10 |

Một bản ghi delivery thể hiện staff đã khai báo việc bàn giao. Confirmation là bước xác nhận độc lập tiếp theo. Tiến độ hàng đã cam kết, đã nhận, đã giao và đã xác nhận được tính ở các bước riêng của workflow.

## 8. Donor — người cam kết đóng góp nhu yếu phẩm

Donor chọn một campaign được công bố rồi cam kết cung cấp một hoặc nhiều loại hàng. Hệ thống theo dõi lượng cam kết và phần tổ chức thực nhận.

| Tính năng thực tế | Entity/bảng liên quan | Class hiện có hoặc phần còn cần làm | Endpoint | Trạng thái |
|---|---|---|---|---|
| Xem chiến dịch công khai | Campaign/items và sanitized totals | Chưa có campaign controller/service | `GET /api/campaigns`, `GET /api/campaigns/{id}` | Theo thiết kế B06 |
| Cam kết góp nhiều loại hàng | `contributions`, `contribution_items`, campaign/items | Chưa có contribution controller/service | `POST /api/campaigns/{id}/commitments` | Theo thiết kế B07 |
| Xem các cam kết của chính mình | Contributions/receipts | Chưa có contribution controller/service | `GET /api/me/commitments` | Theo thiết kế B07 |
| Hủy phần hàng còn cam kết nhưng chưa nhận | Contribution items/cancellation audit | Chưa có cancellation implementation | `POST /api/commitments/{id}/cancellations` | Theo thiết kế B07 |
| Mở tranh chấp với campaign mình đã đóng góp | Disputes và quan hệ contribution | Chưa có donor-dispute implementation | `POST /api/campaigns/{id}/disputes` | Theo thiết kế B10 |

`User.PlatformRole` hiện có `USER` và `PLATFORM_ADMIN`. Donor là capability được cấp riêng trong thiết kế. `AuthService.me()` hiện truyền `false` cho `donorEnabled` vì schema/account mapping chưa có grant tương ứng. Cần bổ sung phần này khi triển khai B07. Các rule allow-public trong security chỉ là routing permission, còn tính năng browse campaign cần controller/service thực tế. Source tại [User.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/user/entity/User.java), [AuthService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/auth/service/AuthService.java) và [SecurityConfig.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/auth/security/SecurityConfig.java).

## 9. PLATFORM_ADMIN — người quản trị ở cấp nền tảng

Role này nằm ở `User.platformRole`. Các quyền vận hành dự kiến cần được thực thi bằng endpoint và service riêng theo contract.

| Tính năng thực tế | Entity/bảng liên quan | Class hiện có hoặc phần còn cần làm | Endpoint | Trạng thái |
|---|---|---|---|---|
| Đăng nhập và xem tài khoản của mình | `User` | Auth classes hiện có | `/api/auth/login`, `/api/auth/me` | Có code |
| Provision/quản lý organization demo theo quyền được cấp | `Organization` và các metadata quản trị | Organization entity/repository có sẵn, controller vẫn là khung | `POST /api/organizations` theo contract | Theo thiết kế |
| Giải quyết tranh chấp theo phạm vi được cấp | `disputes`, case/delivery | Chưa có dispute-resolution implementation | `POST /api/disputes/{id}/resolve` | Theo thiết kế B10 |
| Quyết định void delivery khi giải quyết tranh chấp | Dispute resolution, delivery/totals | Chưa có implementation | Cùng resolve endpoint theo contract | Theo thiết kế B10 |
| Retry/reconcile audit event theo quyền vận hành | Audit/jobs/transactions | Chưa có worker/retry API implementation | `POST /api/audit-events/{eventId}/retry` | Theo thiết kế integration |

Trong B04 hiện tại, đọc case/evidence riêng tư vẫn cần active membership và role staff của tổ chức. Một tài khoản chỉ có platform role sẽ bị từ chối truy cập dữ liệu riêng tư của tổ chức. Class đang thực thi là [OrganizationService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/service/OrganizationService.java) và [CaseService.scoped()](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/service/CaseService.java).

## 10. Beneficiary, khách công khai và worker

| Đối tượng | Vai trò trong app | Tính năng/lưu ý thực tế | Class hoặc trạng thái |
|---|---|---|---|
| Người thụ hưởng | Đối tượng nhận hỗ trợ được staff ghi nhận | Staff lập case và consent thay mặt quy trình vận hành. P0 dùng dữ liệu mô phỏng. Người thụ hưởng không cần tài khoản/wallet | `Beneficiary`, `CaseService.create()`, `ConsentRepository` |
| Khách công khai | Người xem thông tin công khai được phép công bố | Theo thiết kế có thể xem campaign và public audit đã lọc dữ liệu | Public routing có trong `SecurityConfig`, campaign/audit handlers còn thuộc task sau |
| Blockchain worker/relayer | Thành phần hệ thống gửi và kiểm tra giao dịch | Theo thiết kế đọc jobs, gửi transaction, đối chiếu receipt/event và cập nhật sync state | Có SQL jobs/transactions, chưa có Java worker/adapter xử lý |

## 11. Vai trò của từng entity/bảng dữ liệu

Sáu entity nền tảng là `User`, `Organization`, `OrganizationMembership`, `Beneficiary`, `AidCase`, `CaseNeedItem`. D45 thêm mapping JPA cho các bảng consent, evidence, verification, audit, revision, outbox và idempotency mà backend đang truy cập. Các bảng còn lại đã có migration SQL hoặc thuộc thiết kế của các task sau.

### 11.1 Tài khoản và tổ chức

| Entity/bảng | Nó đại diện cho gì | Role/người dùng tính năng nào | Class thực hiện |
|---|---|---|---|
| `User` / `users` | Một tài khoản cá nhân có identity, account state và platform role | Mọi người có tài khoản đăng nhập, xem thông tin bản thân, được kiểm tra ACTIVE | `User`, `UserRepository`, auth classes, `OrganizationService.currentUser()` |
| `Organization` / `organizations` | Một tổ chức sở hữu case và điều phối hỗ trợ | Staff làm việc trong scope tổ chức. Organization APPROVED mới được tham gia luồng B04 hiện tại | `Organization`, `OrganizationRepository`, membership repository, `OrganizationService` |
| `OrganizationMembership` / `organization_memberships` | Quan hệ một user tham gia một organization | Xác định tài khoản thuộc org nào và membership còn hiệu lực hay không | `OrganizationMembership`, `OrganizationMembershipRepository`, `OrganizationService.currentMemberships()`/`requireRole()` |
| `membership_roles` | Tập công việc được cấp cho một membership | Chứa các role tổ chức để service quyết định hành động được phép | `OrganizationMembership.roles` với `@ElementCollection`, các guard trong `OrganizationService`/`CaseService` |

Các JPA entities và repositories hiện có tại [User.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/user/entity/User.java), [Organization.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/entity/Organization.java), [OrganizationMembership.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/entity/OrganizationMembership.java), [UserRepository.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/user/repository/UserRepository.java), [OrganizationRepository.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/repository/OrganizationRepository.java) và [OrganizationMembershipRepository.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/repository/OrganizationMembershipRepository.java).

### 11.2 Hồ sơ, nhu cầu và consent

| Entity/bảng | Công dụng thực tế | Role/người được thao tác | Class thực hiện |
|---|---|---|---|
| `Beneficiary` / `beneficiaries` | Lưu đối tượng nhận hỗ trợ, alias/reference và tổ chức sở hữu | Được FV tạo bên trong luồng lập case, staff cùng org đọc qua case detail | `Beneficiary`, `BeneficiaryRepository`, `CaseService.create()`, `CaseMapper.detail()` |
| `AidCase` / `aid_cases` | Hồ sơ tổng hợp nhu cầu, người tạo, trạng thái, priority và version | FV tạo, creator FV sửa/resubmit trong editable states, own staff đọc | `AidCase`, `AidCaseRepository`, `CaseService`, `CaseController` |
| `CaseNeedItem` / `case_need_items` | Một loại hàng và số lượng cần cho một case | Creator FV nhập/sửa needs trong case, staff xem. Dòng cũ được retire để giữ lịch sử | `CaseNeedItem`, `CaseNeedItemRepository`, `CaseService.patch()`, `CaseMapper.detail()` |
| `consent_records` | Ghi nhận căn cứ, phạm vi, thời điểm và lựa chọn công khai của consent | FV ghi lúc tạo case, staff được phép đọc thông tin consent cùng case | `ConsentInput`, `ConsentRepository.append()`/`find()`, `CaseService`, `CaseDetailResponse.Consent` |
| `case_revisions` | Snapshot hồ sơ theo từng case version | Hệ thống ghi khi tạo/sửa/resubmit/thêm evidence để truy lại nội dung trước đó | `LocalAuditService.revision()`, `CaseService.finish()`. Hiện là dữ liệu hỗ trợ, chưa có history endpoint riêng |
| `verifications` | Quyết định review độc lập và bằng chứng đã review theo target design | VERIFIER approve/reject/request-changes, phải khác creator | `Verification` được ánh xạ để phục vụ outbox và điều kiện resubmit qua `LocalAuditService.changedAfterReview()`. API ghi quyết định thuộc Unit 2 |

Source B04 tại [AidCase.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/entity/AidCase.java), [Beneficiary.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/entity/Beneficiary.java), [CaseNeedItem.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/entity/CaseNeedItem.java), [CaseService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/service/CaseService.java), [CaseMapper.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/mapper/CaseMapper.java), [ConsentInput.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/dto/request/ConsentInput.java), [ConsentRepository.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/repository/ConsentRepository.java) và [LocalAuditService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/audit/service/LocalAuditService.java).

### 11.3 Evidence

| Entity/bảng | Công dụng thực tế | Role/người được thao tác | Class thực hiện |
|---|---|---|---|
| `evidence_files` | Danh tính của một tài liệu logic thuộc một case/org | Creator FV thêm tài liệu cho case | `EvidenceService.append()` gọi `EvidenceFileRepository` để tạo file record khi chưa có file ID |
| `evidence_versions` | Một phiên bản cụ thể với bytes được tham chiếu, hash, size và MIME | Creator FV thêm successor, own staff tải version có quyền | `EvidenceRepository`, `EvidenceFixtureCatalog`, `CaseService.appendEvidence()`/`content()` |
| `case_evidence_links` | Liên kết case với từng evidence version | Hệ thống thêm/retire link khi creator cập nhật evidence. Old links giữ lịch sử | `EvidenceService.append()` và `EvidenceRepository.current()` |
| `delivery_evidence_links` | Bằng chứng được gắn vào một lần giao hàng | Theo thiết kế FV ghi delivery, DC xem khi xác nhận | Có SQL, Java delivery/evidence workflow tương ứng chờ B09/B10 |

`EvidenceFixtureCatalog` là danh mục tài liệu mẫu do server kiểm soát, được dùng để chọn nguồn nội dung cho evidence. `EvidenceRepository` lưu phiên bản mà case đã chọn. Khi người dùng thay tài liệu, version mới có ID riêng và version cũ còn được giữ. Source tại [EvidenceFixtureCatalog.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/service/EvidenceFixtureCatalog.java), [EvidenceRepository.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/repository/EvidenceRepository.java) và [EvidenceController.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/controller/EvidenceController.java).

### 11.4 Campaign, cam kết và hàng thực nhận

| Entity/bảng | Công dụng thực tế | Role thao tác theo thiết kế | Class Java nghiệp vụ hiện tại |
|---|---|---|---|
| `campaigns` | Một chiến dịch kêu gọi hỗ trợ dựa trên case đã được xác minh | ORG_ADMIN tạo/công bố/đóng, donor/public xem projection được công bố | Chưa có, thuộc B06 và close workflow B10 |
| `campaign_items` | Từng loại hàng và target của campaign | Được tạo từ reviewed needs, làm căn cứ tính lượng còn cần | Chưa có, thuộc B06 |
| `contributions` | Một lần donor cam kết đóng góp cho campaign | Donor tạo và đọc cam kết của mình, staff xử lý trong đúng scope | Chưa có, thuộc B07 |
| `contribution_items` | Những loại hàng và số lượng trong một cam kết | Donor cam kết/hủy phần chưa nhận, hệ thống kiểm tra capacity từng item | Chưa có, thuộc B07 |
| `goods_receipts` | Một lần tổ chức xác nhận đã thực nhận hàng của contribution | ORG_ADMIN ghi nhận | Chưa có, thuộc B08 |
| `goods_receipt_items` | Số lượng từng dòng hàng thực nhận trong receipt | ORG_ADMIN nhập, hệ thống giới hạn theo phần contribution còn được nhận | Chưa có, thuộc B08 |

Các bảng SQL có sẵn tại [V3__create_campaign_contribution_tables.sql](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/resources/db/migration/V3__create_campaign_contribution_tables.sql). Một số fields/status của SQL còn khác target design. Các task tương ứng cần reconcile trước khi implement theo [DECISION_LOG.md](D:/thi/UniHack2026/AidBridge/docs/planning/DECISION_LOG.md).

### 11.5 Giao hàng, xác nhận và tranh chấp

| Entity/bảng | Công dụng thực tế | Role thao tác theo thiết kế | Class Java nghiệp vụ hiện tại |
|---|---|---|---|
| `deliveries` | Một lần khai báo giao hỗ trợ hoặc lần giao thất bại | VOLUNTEER ghi nhận | Chưa có, thuộc B09 |
| `delivery_items` | Số lượng từng loại hàng trong lần giao | VOLUNTEER nhập, hệ thống kiểm tra hàng đã nhận còn khả dụng | Chưa có, thuộc B09 |
| `delivery_confirmations` | Kết quả xác nhận độc lập của một delivery | DELIVERY_CONFIRMER, phải khác người ghi delivery | Chưa có, thuộc B10 |
| `disputes` | Vấn đề đang tranh chấp, lý do và kết quả xử lý | OA/CV/DC hoặc donor có quan hệ contribution mở, PLATFORM_ADMIN giải quyết theo scope | Chưa có, thuộc B10 |

Các bảng được khai báo tại [V4__create_delivery_tables.sql](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/resources/db/migration/V4__create_delivery_tables.sql) và [V5__create_trust_blockchain_tables.sql](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/resources/db/migration/V5__create_trust_blockchain_tables.sql). Bản SQL hiện tại và workflow target còn các khác biệt đã ghi trong D38.

### 11.6 Audit, retry và blockchain sync

| Entity/bảng | Công dụng thực tế | Ai thực hiện | Class hiện tại |
|---|---|---|---|
| `audit_events` | Ghi hành động nghiệp vụ với actor, time và metadata để truy vết | Backend ghi cùng transaction nghiệp vụ, hoặc ghi access audit khi đọc content | `LocalAuditService.append()`, `CaseService.finish()`/`content()` cho B04 |
| `idempotency_records` | Lưu kết quả request đã xử lý để retry nhận lại kết quả cũ | Backend kiểm tra sau authorization, trước chạy lại mutation | `IdempotencyService.execute()` |
| `blockchain_jobs` | Công việc chờ gửi/kiểm tra milestone trên chain | Worker theo thiết kế | Chưa có worker Java, B04 tạo local audit |
| `blockchain_transactions` | Các lần gửi giao dịch và receipt/status liên quan | Worker/adapter theo thiết kế | Chưa có adapter/worker Java |

Source hiện có tại [LocalAuditService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/audit/service/LocalAuditService.java), [IdempotencyService.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/shared/idempotency/IdempotencyService.java), [V5__create_trust_blockchain_tables.sql](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/resources/db/migration/V5__create_trust_blockchain_tables.sql) và [V6__complete_case_workflow.sql](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/resources/db/migration/V6__complete_case_workflow.sql).

### 11.7 Công dụng chi tiết của sáu entity nền tảng

Phần này giải thích công dụng của sáu entity nền tảng trong app. Mục 11.8 bao gồm cả các bảng hỗ trợ đã được ánh xạ qua JPA theo D45 và các bảng đang chờ triển khai nghiệp vụ.

Các ví dụ dưới đây đều là dữ liệu giả lập. Khi nói đến màn hình, tài liệu đang giải thích cách frontend có thể sử dụng API; không xác nhận màn hình đó đã được triển khai.

#### 1. `User` / `users` — ai đang thao tác?

- **Đại diện cho:** một tài khoản cá nhân, gồm tên, email, mật khẩu đã băm, trạng thái và quyền cấp nền tảng. Mỗi người thao tác dùng tài khoản riêng để truy được ai đã tạo hồ sơ, xác minh hoặc ghi nhận hàng.
- **Tính năng thực tế có code:** đăng nhập; trả thông tin bản thân qua `/api/auth/me`; từ chối tài khoản `DISABLED` khi xác thực và khi service kiểm tra lại tài khoản hiện tại. Đăng xuất được xử lý bằng session, không xóa dòng `User`.
- **Class xử lý:** `CustomUserDetailsService.loadUserByUsername()` tìm tài khoản; `AuthService.login()` xác thực; `AuthService.me()` và `UserMapper` tạo dữ liệu trả về; `OrganizationService.currentUser()` kiểm tra trạng thái hiện tại. `UserRepository` truy vấn bảng `users`.
- **Liên hệ:** cùng một `User` có thể có nhiều `OrganizationMembership`; `createdBy`, `recorded_by`, `confirmed_by` ở các nghiệp vụ tham chiếu danh tính này. Quyền tổ chức lấy từ membership, không chỉ từ `User.platformRole`.
- **Ví dụ và giới hạn:** tài khoản của Dũng đăng nhập được chưa có nghĩa Dũng được sửa mọi case. Muốn sửa case, còn phải là creator và có `VOLUNTEER` trong đúng tổ chức. Có `AuthService.register()` trong source không đồng nghĩa app công bố chức năng tự đăng ký.

#### 2. `Organization` / `organizations` — dữ liệu thuộc đơn vị nào?

- **Đại diện cho:** một tổ chức điều phối hỗ trợ, có tên, mô tả và trạng thái `PENDING`, `APPROVED` hoặc `SUSPENDED`.
- **Tính năng thực tế có code:** chia phạm vi truy cập dữ liệu. Staff của tổ chức A đọc case/evidence thuộc A; API B04 không cho dùng membership A để truy cập dữ liệu riêng của B. Chỉ membership trong tổ chức `APPROVED` được dùng trong luồng hiện tại.
- **Class xử lý:** `Organization`, `OrganizationRepository`, `OrganizationMembershipRepository` và `OrganizationService`; `CaseService` lấy organization từ membership đã kiểm tra rồi dùng cho truy vấn/lưu dữ liệu.
- **Liên hệ:** một tổ chức có các membership, beneficiaries và cases. `organization_id` giúp repository kiểm tra quyền ngay khi tìm tài nguyên, thay vì tìm toàn bộ rồi mới lọc ở frontend.
- **Ví dụ và giới hạn:** khi trạng thái tổ chức A chuyển sang `SUSPENDED`, session cũ của staff không làm cho truy cập case tiếp tục được phép. `OrganizationController` hiện là khung class; chưa có luồng CRUD/onboarding tổ chức hoạt động qua controller này.

#### 3. `OrganizationMembership` / `organization_memberships` — người này làm việc tại đâu?

- **Đại diện cho:** một lần tham gia của một user trong một tổ chức. Dữ liệu gồm user, organization, trạng thái membership, thời điểm tham gia và tập roles.
- **Tính năng thực tế có code:** `/api/auth/me` liệt kê memberships còn hiệu lực; service kiểm tra user có được làm việc trong tổ chức được yêu cầu không. Một user có thể làm tình nguyện viên ở A và người xác minh ở B.
- **Class xử lý:** `OrganizationMembershipRepository` nạp membership cùng organization/roles; `OrganizationService.currentMemberships()` lấy phạm vi hiện tại; `requireRole()` yêu cầu đúng role. `CaseService` dùng kết quả này cho thao tác case/evidence.
- **Liên hệ:** đây là cầu nối `User` ↔ `Organization`; bảng `membership_roles` lưu nhiều roles cho một membership. Cặp organization/user là duy nhất trong bảng membership.
- **Ví dụ và giới hạn:** membership bị `DISABLED` sẽ không còn được dùng để đọc case, dù user vẫn `ACTIVE` và session chưa hết hạn. Chưa có giao diện quản lý thành viên; sự tồn tại của entity không tạo ra tính năng mời/thêm/xóa thành viên qua UI.

#### 4. `Beneficiary` / `beneficiaries` — ai hoặc hộ nào cần được hỗ trợ?

- **Đại diện cho:** đối tượng nhận hỗ trợ. `beneficiaryType` phân biệt `HOUSEHOLD` và `INDIVIDUAL`; `displayLabel` là tên hiển thị/alias; `referenceCode` là mã tham chiếu trong tổ chức. Entity còn lưu organization và người tạo.
- **Tính năng thực tế có code:** khi tình nguyện viên lập case, `CaseService.create()` tạo một beneficiary loại `HOUSEHOLD` từ `beneficiaryAlias`. Khi staff mở chi tiết case, `CaseMapper.detail()` lấy alias từ beneficiary để biết hồ sơ đang hỗ trợ hộ nào.
- **Class xử lý:** `Beneficiary`, `BeneficiaryRepository`, `CaseService.create()` và `CaseMapper.detail()`. Hiện beneficiary được xử lý bên trong nghiệp vụ case; chưa có API CRUD beneficiary riêng.
- **Liên hệ:** `AidCase.beneficiary` tham chiếu người/hộ nhận hỗ trợ; `consent_records` ghi căn cứ sử dụng thông tin của beneficiary đó. Beneficiary và case phải cùng tổ chức.
- **Ví dụ:** `Household A-01` là hộ nhận hỗ trợ; hồ sơ “Hỗ trợ nhu yếu phẩm sau ngập” là `AidCase`; “10 túi gạo” là một `CaseNeedItem`. Ba dữ liệu trả lời ba câu hỏi khác nhau: ai nhận, hồ sơ gì, cần hàng gì.
- **Giới hạn hiện tại:** mỗi request tạo case tạo một beneficiary mới; chưa có tìm kiếm/tái sử dụng beneficiary cũ hoặc chống trùng hộ. Cột `privateDataEncrypted` là chỗ lưu được khai báo, không chứng minh đã có quy trình mã hóa dữ liệu. P0 dùng dữ liệu giả lập; beneficiary không cần tài khoản, thiết bị hay wallet.

#### 5. `AidCase` / `aid_cases` — hồ sơ hỗ trợ đang được xử lý ra sao?

- **Đại diện cho:** một hồ sơ nghiệp vụ tập hợp đối tượng nhận hỗ trợ, người tạo, tổ chức, nội dung, mức ưu tiên, trạng thái và version. Đây là phần trung tâm để đi tiếp sang xác minh, chiến dịch và giao hỗ trợ.
- **Tính năng tạo/xem có code:** `CaseController` nhận `POST /api/cases`, `GET /api/cases`, `GET /api/cases/{id}`; `CaseService.create()`, `list()`, `get()` thực hiện nghiệp vụ. Tạo thành công ghi case ở `SUBMITTED`; danh sách có phân trang/lọc trạng thái; chi tiết gồm needs, consent và evidence metadata.
- **Tính năng cập nhật có code:** creator có role `VOLUNTEER` dùng `PATCH /api/cases/{id}` để sửa nội dung/needs trong `SUBMITTED` hoặc `CHANGES_REQUESTED`. `POST /api/cases/{id}/resubmit` gửi lại case đang `CHANGES_REQUESTED` sau khi có revision mới hơn lần yêu cầu bổ sung. Luồng tạo quyết định yêu cầu bổ sung còn thuộc B05.
- **Các fields có ý nghĩa thực tế:** `internalTitle` dùng cho nội bộ; `publicTitle`/`publicSummary` là nội dung dự kiến công bố nếu consent cho phép; `approximateArea` là khu vực khái quát; `priority` là `NORMAL`/`URGENT`; `createdBy` xác định creator; `supersedesCaseId` liên kết hồ sơ thay thế nếu có. Lưu field công khai không tự tạo public case endpoint.
- **Bảo vệ cập nhật:** client gửi version mình đang xem; service so với `version` hiện tại, khóa dòng khi sửa và từ chối dữ liệu cũ. `@Version` giúp JPA phát hiện xung đột cập nhật; việc đổi evidence cũng làm case thay đổi để tránh tiếp tục dùng version cũ.
- **Class/liên hệ:** `AidCaseRepository` tìm/khóa case trong org được phép; `CaseService` kiểm tra quyền, creator và state; `CaseMapper` gom dữ liệu thành `CaseSummaryResponse`/`CaseDetailResponse`. Một case liên quan beneficiary, nhiều needs, consent, evidence và revisions.
- **Ví dụ và giới hạn:** khi bổ sung từ 10 thành 12 túi gạo, case giữ cùng ID nhưng có version mới và lưu lại needs cũ. Có enum `VERIFIED`/`COMPLETED` chưa có nghĩa B04 đã có chức năng duyệt/hoàn tất; những bước đó cần service của task sau.

#### 6. `CaseNeedItem` / `case_need_items` — cụ thể cần loại hàng nào, bao nhiêu?

- **Đại diện cho:** một dòng nhu cầu, gồm `itemName`, `unit` và `requestedQuantity`, gắn vào một case. Ví dụ `Rice`, `bag`, `10`; một dòng khác là `Drinking water`, `bottle`, `24`.
- **Tính năng thực tế có code:** form tạo/sửa case gửi danh sách `NeedInput`; `CaseService.insertNeeds()` ghi các dòng. `CaseMapper.detail()` trả needs để staff xem. Request giới hạn 1–20 dòng, quantity là số nguyên từ 1 đến 1.000.000; service từ chối tên/đơn vị trùng nhau không phân biệt hoa thường.
- **Khi sửa:** `CaseService.patch()` đánh dấu các dòng cũ bằng `retiredAtVersion` rồi lưu bộ needs mới. `CaseNeedItemRepository` chỉ trả các dòng chưa retire cho nội dung hiện tại. Retire nghĩa là ngừng dùng trong phiên bản hiện tại, vẫn giữ bản ghi để truy vết.
- **Liên hệ:** theo thiết kế, `campaign_items.case_need_item_id` sẽ chỉ tới dòng nhu cầu làm căn cứ kêu gọi. Nhu cầu ban đầu, target chiến dịch và hàng đã nhận/giao là các số liệu riêng.
- **Giới hạn:** đây không phải danh mục sản phẩm hay sổ kho; chưa có đổi đơn vị tự động. Có dòng cần 10 túi không có nghĩa đã nhận hoặc giao được 10 túi.

### 11.8 Công dụng chi tiết của 22 bảng hỗ trợ và nghiệp vụ tiếp theo

Không phải bảng nào cũng cần controller, request, response và service riêng. Có bảng được ghi tự động trong một thao tác lớn; có bảng mới có SQL, chưa có tính năng sử dụng qua API. Các tên bảng sau giữ nguyên theo migration hiện tại.

#### 7. `membership_roles` — những công việc được cấp trong một tổ chức

- **Tính năng:** lưu nhiều roles cho cùng membership, ví dụ vừa `VOLUNTEER` vừa `VERIFIER`. `ORG_ADMIN` không tự bao gồm tất cả các role khác.
- **Class/liên hệ:** `OrganizationMembership.roles` dùng `@ElementCollection` và `@CollectionTable` để JPA lưu tập enum vào bảng phụ; `OrganizationService.requireRole()` và các guard của `CaseService` đọc tập này. Không có entity Java riêng cho mỗi dòng role.
- **Ý nghĩa thực tế:** phân biệt hai người cùng tổ chức nhưng được phép làm việc khác nhau. Theo workflow, có nhiều roles vẫn không được tự duyệt case hoặc tự xác nhận delivery mình ghi.

#### 8. `consent_records` — được phép sử dụng thông tin người nhận trong phạm vi nào?

- **Tính năng có code:** lúc tạo case, tình nguyện viên ghi căn cứ `CONSENT` hoặc `REPRESENTATIVE`, phạm vi, thời điểm thu nhận và `publicSummaryAllowed`. Consent là ghi nhận sự đồng ý/căn cứ đại diện để xử lý thông tin, không phải kết quả xác minh nhu cầu.
- **Hành vi thực tế:** nếu `publicSummaryAllowed=false`, create/patch không được chứa `publicTitle` hoặc `publicSummary`. Ví dụ hộ đồng ý lập hồ sơ nội bộ nhưng không đồng ý phần tóm tắt công khai thì backend chặn nhập hai trường công khai này.
- **Class/liên hệ:** `ConsentInput` nhận dữ liệu; `ConsentRepository.append()` ghi consent `GRANTED`; `find()` tìm theo consent ID, organization và beneficiary; `AidCase.consentRecordId` chỉ tới bản ghi; `CaseDetailResponse.Consent` trả phần thông tin cần xem.
- **Giới hạn:** chưa có endpoint sửa/thu hồi consent. Có giá trị SQL `WITHDRAWN` không đồng nghĩa đã có luồng thu hồi hoàn chỉnh; `find()` hiện kiểm tra bản ghi được tham chiếu là `GRANTED`, không tự tìm một bản ghi thu hồi mới hơn.

#### 9. `case_revisions` — hồ sơ trông như thế nào ở lần cập nhật trước?

- **Tính năng có code:** lưu bản chụp JSON riêng tư của case theo `case_version` khi tạo, sửa, thêm evidence và resubmit. Ví dụ có thể phân biệt bản cần 10 túi gạo với bản sửa thành 12 túi.
- **Class/liên hệ:** `CaseService.finish()` lấy dữ liệu từ `CaseMapper.detail()` rồi gọi `LocalAuditService.revision()`. Snapshot gắn case/org; các revision được giữ lại để phục vụ kiểm tra lịch sử.
- **Giới hạn:** chưa có endpoint/màn hình xem lịch sử. Revision mới chứng minh đã có cập nhật được lưu; kiểm tra resubmit hiện chưa chứng minh nội dung cập nhật đã giải quyết đúng mọi góp ý của reviewer.

#### 10. `verifications` — người độc lập đã đánh giá hồ sơ thế nào?

- **Tính năng theo thiết kế B05:** lưu người review, quyết định và ghi chú để duyệt, từ chối hoặc yêu cầu bổ sung hồ sơ. Người review phải khác creator, dù tài khoản giữ nhiều roles.
- **Liên hệ:** kết quả review gắn `AidCase`; thiết kế cần xác định đúng nội dung/phiên bản evidence đã review để người tạo không thay bằng chứng làm mất dấu quyết định cũ.
- **Class/trạng thái:** `LocalAuditService.changedAfterReview()` đọc quyết định `CHANGES_REQUESTED` và thời điểm review cho điều kiện resubmit. V7 chuẩn hóa các bản ghi decision cũ. Verification service và API approve thuộc phần B05 tiếp theo.

#### 11. `evidence_files` — một tài liệu bằng chứng là tài liệu nào?

- **Tính năng có code:** cấp danh tính ổn định cho một tài liệu logic thuộc case/org, ví dụ “Biên bản khảo sát hộ”. Danh tính này được giữ khi nội dung tài liệu có phiên bản mới.
- **Class/liên hệ:** `EvidenceService.append()` gọi `EvidenceFileRepository` để tạo file record `PRIVATE` khi chưa có `evidenceFileId`. Các dòng `evidence_versions` trỏ tới file này. `CaseService` kiểm tra creator/quyền trước khi thêm.
- **Ý nghĩa thực tế:** phân biệt thêm tài liệu khác với sửa phiên bản của tài liệu đang có. P0 chọn fixtures do server cấp; chưa có UI upload tùy ý. Giá trị `PUBLIC` có trong SQL không làm cho API evidence B04 trở thành công khai.

#### 12. `evidence_versions` — chính xác nội dung bằng chứng ở phiên bản nào?

- **Tính năng có code:** lưu version number, SHA-256, MIME, số bytes, fixture key và version tiền nhiệm. Thay tài liệu tạo ID/version mới, giữ phiên bản cũ. SHA-256 là dấu kiểm tra nội dung bytes, không chứng minh nội dung đó đúng ngoài đời.
- **Class/liên hệ:** `EvidenceFixtureCatalog` cung cấp bytes được server cho phép. `CaseService.appendEvidence()` kiểm tra case/quyền/version rồi gọi `EvidenceService.append()` để điều phối việc lưu metadata qua `EvidenceRepository`. `EvidenceVersionResponse` trả metadata để trang chi tiết hiển thị tài liệu.
- **Tải nội dung:** `EvidenceController` nhận `GET /api/evidence-versions/{id}/content`; `CaseService.content()` kiểm tra quyền hiện tại, fixture grant, hash và size trước khi trả bytes; đồng thời ghi access audit. API không trả đường dẫn lưu trữ riêng tư.
- **Ví dụ:** khảo sát v1 được bổ sung thành v2; case hiện dùng v2, v1 vẫn được lưu và chỉ tải được khi caller còn quyền và fixture còn được cấp. Đây là lịch sử tài liệu, khác với `AidCase.version` của toàn hồ sơ.

#### 13. `case_evidence_links` — case đang dùng những phiên bản bằng chứng nào?

- **Tính năng có code:** nối case với các evidence version cụ thể. Khi có successor, link cũ được retire, link mới trở thành một phần nội dung hiện tại.
- **Class/liên hệ:** `EvidenceRepository.current()` trả metadata của các phiên bản còn liên kết hiện hành. `EvidenceService.append()` cập nhật liên kết qua `CaseEvidenceLinkRepository`. `CaseMapper.detail()` đưa metadata vào case detail.
- **Ý nghĩa thực tế:** staff mở case thấy bộ tài liệu hiện dùng; backend vẫn giữ liên kết lịch sử. Link trỏ version cụ thể giúp tránh hiểu nhầm rằng một review cũ luôn áp dụng cho nội dung file mới nhất.

#### 14. `delivery_evidence_links` — bằng chứng nào chứng minh cho lần giao hàng này?

- **Tính năng theo thiết kế:** gắn phiên bản tài liệu bàn giao vào một delivery để người xác nhận độc lập xem đúng bằng chứng của lần giao đó.
- **Liên hệ:** tham chiếu `deliveries` và `evidence_versions`; mục đích bàn giao khác với tài liệu chứng minh hoàn cảnh của case.
- **Class/trạng thái:** mới có bảng ở V5; chưa có Java workflow delivery/evidence B09/B10 xử lý thao tác này. Không suy ra có upload biên bản giao hàng từ API evidence case hiện tại.

#### 15. `campaigns` — hồ sơ nào được đưa ra kêu gọi hỗ trợ?

- **Tính năng theo thiết kế B06:** `ORG_ADMIN` tạo/công bố chiến dịch từ case đã xác minh; donor/khách xem phần nội dung được phép công bố. Đóng chiến dịch cần điều kiện số lượng đã xác nhận và không còn dispute theo workflow B10.
- **Liên hệ:** SQL hiện gắn một campaign với một `aid_case_id` duy nhất; campaign chứa nhiều `campaign_items`. Case phục vụ xử lý nội bộ, campaign phục vụ kêu gọi hỗ trợ; công khai campaign không cho phép đọc toàn bộ case riêng tư.
- **Class/trạng thái:** có SQL V3, chưa có campaign controller/service nghiệp vụ. Target API và trạng thái còn cần reconcile trước B06; không coi routing công khai trong `SecurityConfig` là campaign API đã triển khai.

#### 16. `campaign_items` — chiến dịch đang kêu gọi bao nhiêu cho từng loại hàng?

- **Tính năng theo thiết kế:** giữ target riêng cho từng dòng hàng để hiển thị lượng còn cần và giới hạn cam kết theo từng item.
- **Liên hệ:** mỗi dòng nối campaign với `case_need_items`; `contribution_items` và `delivery_items` tham chiếu campaign item để đếm cùng loại hàng. Số túi gạo không được bù vào số chai nước.
- **Ví dụ/class:** target 10 túi gạo và 24 chai nước là hai dòng riêng, không phải target tổng 34. Chưa có class Java nghiệp vụ; thuộc B06 và các bước tính số lượng sau đó.

#### 17. `contributions` — một donor đã cam kết đóng góp lần nào?

- **Tính năng theo thiết kế B07:** tạo một cam kết cho campaign; donor xem cam kết của mình và hủy phần chưa nhận theo điều kiện nghiệp vụ. Cam kết là lời hứa đóng góp, chưa phải tổ chức đã nhận hàng.
- **Liên hệ:** giữ donor, campaign, thời điểm, ghi chú/trạng thái; có nhiều `contribution_items` và có thể có nhiều `goods_receipts` do nhận từng phần.
- **Class/trạng thái:** mới có SQL V3; chưa có contribution controller/service. Quyền donor cần được cấp rõ ràng, không tự có chỉ vì `User` đăng nhập thành công.

#### 18. `contribution_items` — trong cam kết đó gồm những hàng gì?

- **Tính năng theo thiết kế B07:** lưu số lượng cam kết cho từng campaign item; backend kiểm tra lượng còn nhận cam kết để tránh vượt target khi nhiều donor thao tác cùng lúc.
- **Ví dụ/liên hệ:** một contribution có 4 túi gạo và 6 chai nước tương ứng hai contribution items; phiếu nhận hàng về sau tham chiếu từng dòng để biết đã thực nhận bao nhiêu của lời cam kết nào.
- **Class/trạng thái:** có SQL, chưa có class Java nghiệp vụ. Hủy phần chưa nhận, locking và kiểm soát cạnh tranh vẫn cần triển khai/test; constraint số dương trong SQL chưa tự bảo đảm toàn bộ các quy tắc này.

#### 19. `goods_receipts` — tổ chức đã thực nhận hàng vào lần nào?

- **Tính năng theo thiết kế B08:** `ORG_ADMIN` ghi nhận đã nhận hàng từ một contribution, gồm người nhận, thời gian và ghi chú. Một cam kết có thể được giao tới tổ chức qua nhiều lần.
- **Liên hệ:** một receipt thuộc contribution, có nhiều `goods_receipt_items`. Đây là hàng tổ chức nhận từ donor; giao hàng cho beneficiary là nghiệp vụ `deliveries` sau đó.
- **Ví dụ/class:** donor hứa 10 túi, hôm nay tổ chức chỉ nhận 6 thì receipt ghi lần nhận 6; không tăng hàng thực nhận lên 10 chỉ vì đã có cam kết. Chưa có class Java nghiệp vụ; thuộc B08.

#### 20. `goods_receipt_items` — thực nhận bao nhiêu của từng dòng cam kết?

- **Tính năng theo thiết kế B08:** lưu số lượng thực nhận của từng contribution item trong một receipt; cho phép nhận từng phần và tính lượng còn chưa nhận.
- **Liên hệ/điều kiện:** tham chiếu receipt và contribution item. Backend cần kiểm tra đúng quan hệ, số lượng còn lại và cạnh tranh trước khi tăng received quantities.
- **Ví dụ/class:** cam kết 10 túi, receipt thứ nhất nhận 6, receipt thứ hai nhận 4. Hai receipt items là hai sự kiện nhận riêng, không sửa lần đầu thành 10 để che lịch sử. Chưa có class Java nghiệp vụ.

#### 21. `deliveries` — đã ghi nhận lần giao hỗ trợ nào cho người nhận?

- **Tính năng theo thiết kế B09:** `VOLUNTEER` ghi lần giao hàng hoặc lần giao thất bại, cùng người ghi nhận, thời gian, ghi chú và bằng chứng bàn giao.
- **Liên hệ:** delivery gắn case, chứa `delivery_items`, liên kết evidence và được review bởi `delivery_confirmations`. Một case có thể có nhiều lần giao.
- **Class/giới hạn:** có SQL V4 nhưng chưa có delivery controller/service. SQL hiện chưa biểu diễn đầy đủ target failed-attempt workflow; cần reconcile trước triển khai. Một record người giao tự nhập chưa được xem là đã xác nhận độc lập.

#### 22. `delivery_items` — mỗi lần giao có những mặt hàng và số lượng nào?

- **Tính năng theo thiết kế B09:** lưu từng campaign item và delivered quantity; kiểm tra lượng tổ chức đã nhận còn khả dụng, tránh giao vượt hàng thực nhận.
- **Ví dụ/liên hệ:** lần giao thứ nhất 6 túi, lần giao thứ hai 4 túi là hai delivery records, mỗi record có item tương ứng. Từng dòng gắn campaign item để cộng số lượng đúng loại/đơn vị.
- **Class/trạng thái:** có SQL, chưa có class Java nghiệp vụ. Ràng buộc số dương không thay cho service/transaction kiểm tra giới hạn và thao tác đồng thời.

#### 23. `delivery_confirmations` — ai độc lập xác nhận lần giao đó?

- **Tính năng theo thiết kế B10:** `DELIVERY_CONFIRMER` xem delivery/bằng chứng và ghi quyết định; người này phải khác người ghi delivery. Kết quả xác nhận là căn cứ tính confirmed quantity theo workflow.
- **Liên hệ:** một delivery có thể có các lần review; SQL V4 giới hạn tối đa một quyết định `CONFIRMED` thành công cho mỗi delivery. Đủ role không thay thế điều kiện độc lập về danh tính.
- **Ví dụ/class:** Dũng ghi giao 6 túi, Khoa dùng tài khoản khác xác nhận; Dũng có thêm role xác nhận vẫn không được tự xác nhận. Chưa có class Java nghiệp vụ; SQL/index hiện tại chưa tự kiểm tra đầy đủ identity/snapshot rules.

#### 24. `disputes` — có vấn đề gì cần điều tra và xử lý?

- **Tính năng theo thiết kế B10:** actor có quan hệ/quyền phù hợp mở tranh chấp; lưu lý do, trạng thái, người giải quyết và kết luận. Ví dụ bằng chứng bàn giao không khớp số hàng được khai báo.
- **Liên hệ:** SQL hiện gắn dispute với case và có thể với delivery. Workflow còn có campaign/donor scope và quy tắc giải quyết; các khác biệt schema/contract cần đối chiếu trước triển khai.
- **Class/ý nghĩa:** chưa có class Java nghiệp vụ. Theo thiết kế, dispute chưa giải quyết ngăn hoàn tất chiến dịch; quyết định xử lý phải được audit, không âm thầm sửa/xóa lịch sử giao hàng.

#### 25. `audit_events` — ai đã làm gì, vào lúc nào?

- **Tính năng có code B04:** ghi sự kiện `CASE_CREATED`, `CASE_UPDATED`, `CASE_RESUBMITTED`, `EVIDENCE_VERSION_CREATED` và `EVIDENCE_CONTENT_READ`. Đây là nhật ký hành động, khác với `case_revisions` là bản chụp nội dung hồ sơ.
- **Class/liên hệ:** `CaseService.finish()`/`content()` gọi `LocalAuditService.append()` trong transaction; event có actor, organization, case, đối tượng bị tác động và thời gian. Payload riêng tư tách khỏi `public_metadata` đang để `{}` trong luồng này.
- **Ví dụ/giới hạn:** khi tài liệu được tải qua endpoint hợp lệ, backend lưu lại lần truy cập để truy vết. Chưa có endpoint timeline audit hoàn chỉnh; B04 ghi local audit, không tự tạo blockchain job cho mọi sự kiện.

#### 26. `idempotency_records` — gửi lại request có tạo trùng dữ liệu không?

- **Tính năng có code:** lưu kết quả một thao tác thành công theo actor, operation và key. Nếu client mất response rồi retry cùng key/nội dung, backend trả lại kết quả đã lưu thay vì tạo thêm case hay version.
- **Class/liên hệ:** `IdempotencyService.execute()` dùng digest nội dung và PostgreSQL advisory lock, ghi kết quả trong transaction nghiệp vụ. `CaseService` kiểm tra quyền hiện tại trước khi gọi; đổi organization/nội dung với key cũ bị trả `409`.
- **Ví dụ/giới hạn:** bấm gửi lại vì mạng chậm không tạo hai hồ sơ khi dùng đúng cùng key. Đây là bảo vệ retry, không phải chống trùng beneficiary khi hai request dùng hai keys khác nhau; dữ liệu response lưu ở cột riêng tư.

#### 27. `blockchain_jobs` — sự kiện nghiệp vụ nào đang chờ đồng bộ Fuji?

- **Tính năng theo thiết kế:** worker lấy công việc từ database, gửi/kiểm tra milestone trên chain, hẹn retry khi RPC lỗi. Job giữ liên kết audit event, trạng thái đồng bộ, số lần thử và lỗi gần nhất.
- **Liên hệ:** job là công việc cho một logical audit event; nhiều lần thử gửi có thể có nhiều `blockchain_transactions`. Trạng thái đồng bộ phải tách khỏi trạng thái nghiệp vụ case/delivery.
- **Class/trạng thái:** có SQL V5, chưa có worker Java. B04 local audit không tạo job. Thiết kế yêu cầu business update + audit + job trong cùng transaction cho các milestone cần lên chain; không giữ transaction SQL khi chờ RPC.

#### 28. `blockchain_transactions` — từng lần gửi giao dịch lên chain ra sao?

- **Tính năng theo thiết kế:** lưu transaction hash, thời điểm gửi, block, receipt status và lỗi của từng lần thử cho một job; giúp xử lý submitted/reverted/confirmed và retry có truy vết.
- **Liên hệ:** nhiều transaction attempts có thể thuộc cùng job, nhưng vẫn là cùng logical business event. Thiết kế yêu cầu lưu signed bytes/hash trước broadcast và kiểm tra receipt cùng expected event trước xác nhận đồng bộ.
- **Class/giới hạn:** mới có SQL V5, chưa có adapter/worker Java; schema hiện cũng chưa biểu diễn đủ yêu cầu lưu signed bytes. Có tx hash chưa chứng minh giao dịch thành công, và blockchain confirmation không chứng minh hàng đã được giao ngoài đời.

### 11.9 Ghép các entity thành một ví dụ dễ hình dung

Giả sử tổ chức A hỗ trợ `Household A-01`. Đoạn đầu sau đây có code B04; các bước từ campaign trở đi là workflow theo thiết kế, chưa có service tương ứng:

1. `User` là tài khoản người lập hồ sơ. `OrganizationMembership` và `membership_roles` cho biết người đó có `VOLUNTEER` tại tổ chức A.
2. `Beneficiary` là `Household A-01`; `AidCase` là hồ sơ hỗ trợ của hộ; hai `CaseNeedItem` là 10 túi gạo và 24 chai nước.
3. `consent_records` ghi phạm vi đồng ý; `evidence_files` xác định tài liệu khảo sát; `evidence_versions` lưu phiên bản cụ thể; `case_evidence_links` nối bộ bằng chứng vào case.
4. Mỗi lần cập nhật tạo `case_revisions` và `audit_events`; `idempotency_records` giúp retry an toàn. Đây là các dữ liệu hỗ trợ, người dùng không cần tự nhập từng bản ghi.
5. Sau review độc lập trong `verifications`, `campaigns`/`campaign_items` sẽ dùng nhu cầu đã review để kêu gọi hỗ trợ.
6. Donor cam kết qua `contributions`/`contribution_items`; tổ chức thực nhận qua `goods_receipts`/`goods_receipt_items`; giao cho hộ qua `deliveries`/`delivery_items` và bằng chứng `delivery_evidence_links`.
7. Người khác xác nhận qua `delivery_confirmations`; sai lệch được xử lý bằng `disputes`. Milestone cần đồng bộ Fuji dùng `audit_events` → `blockchain_jobs` → `blockchain_transactions`.

Vì vậy, một màn hình case có thể đọc nhiều bảng và trả một `CaseDetailResponse`. Nhiều entity/bảng phục vụ cùng một tính năng tổng hợp; không bắt buộc người dùng đi qua một màn hình CRUD riêng cho từng bảng.

## 12. Vì sao case có nhiều request/response

Một màn hình danh sách, một trang chi tiết và một bước chọn tài liệu có nhu cầu dữ liệu khác nhau. Các DTO giúp backend gửi đúng cấu trúc cần cho từng thao tác.

| Class | Tính năng thực tế sử dụng |
|---|---|
| `CreateCaseRequest` | Dữ liệu từ form tạo hồ sơ |
| `PatchCaseRequest` | Những trường muốn sửa và version mà người dùng đang giữ |
| `ResubmitCaseRequest` | Yêu cầu gửi lại hồ sơ sau bổ sung |
| `NeedInput` | Một dòng nhu cầu hàng hóa trong form |
| `ConsentInput` | Phần consent người lập hồ sơ ghi nhận |
| `EvidenceVersionRequest` | Chọn fixture và chỉ ra tài liệu/version tiền nhiệm khi thay bằng chứng |
| `CaseSummaryResponse` | Một hàng trong danh sách hồ sơ |
| `CasePage` | Danh sách các hàng và tổng số hồ sơ/phân trang |
| `CaseDetailResponse` | Toàn bộ thông tin riêng tư cần cho trang chi tiết và kết quả sau tạo/sửa/resubmit |
| `EvidenceFixtureResponse` | Một tài liệu mẫu mà người dùng có thể chọn |
| `EvidenceVersionResponse` | Thông tin một version đã được gắn vào hồ sơ, cũng được dùng trong case detail |

`CaseDetailResponse` còn có các record con `Creator`, `Need`, `Consent` để trình bày từng phần của JSON. `case.version` theo dõi lần cập nhật của toàn bộ hồ sơ. `EvidenceVersionResponse.versionNo` theo dõi phiên bản của một tài liệu riêng lẻ.

Các DTO hiện có được dùng bởi [CaseController.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/controller/CaseController.java), [EvidenceController.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/controller/EvidenceController.java) và [CaseMapper.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/mapper/CaseMapper.java). Chi tiết fields tại [CaseDetailResponse.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/main/java/com/aidbridge/casefile/dto/response/CaseDetailResponse.java).

## 13. Một luồng thao tác điển hình

1. Staff đăng nhập, `AuthController`/`AuthService` xác thực `User`, `SecurityConfig` duy trì session.
2. FV mở form tạo case, frontend gọi fixture-list để lấy tài liệu tổ chức được chọn.
3. FV gửi `POST /api/cases`, `CaseController` validate `CreateCaseRequest`.
4. `CaseService.create()` gọi `OrganizationService.requireRole()` để kiểm tra account, org, membership và VOLUNTEER.
5. `IdempotencyService` kiểm tra key/replay. Service lưu beneficiary, consent, case SUBMITTED, needs, evidence, snapshot và audit trong transaction.
6. `CaseMapper` tạo `CaseDetailResponse`, frontend có thể hiển thị trang chi tiết case mới.
7. VERIFIER của cùng org có thể đọc case và tải evidence bằng các API riêng tư B04.
8. Phần review độc lập B05 tiếp tục từ đây khi được triển khai. Nếu cần bổ sung, creator dùng patch/evidence-version rồi resubmit theo version/state guards.

## 14. Kiểm tra quyền nằm ở class nào

| Class | Vai trò trong việc thực thi quyền/tính năng |
|---|---|
| `SecurityConfig` | Xác định request nào cần đăng nhập, cấu hình session, CSRF và logout |
| `CustomUserDetailsService` | Nạp tài khoản và trạng thái để xác thực |
| `OrganizationService` | Đọc lại user/membership/org hiện tại và kiểm tra role tổ chức |
| `CaseService` | Kiểm tra organization scope, creator, case state/version và điều kiện nghiệp vụ |
| `AidCaseRepository` | Query case trong org được phép, khóa case khi mutation |
| `CaseNeedItemRepository` | Query current need lines theo case/org |
| `ConsentRepository` | Ghi/đọc consent gắn đúng beneficiary/org |
| `EvidenceRepository` | Query version theo scope và lưu phiên bản mới |
| `EvidenceFixtureCatalog` | Cấp quyền dùng fixture key theo org từ server configuration |
| `IdempotencyService` | Bảo vệ retry khỏi tạo lại cùng một mutation sau khi caller đã authorize |
| `LocalAuditService` | Ghi business/access audit và case snapshots trong transaction |
| `GlobalExceptionHandler` | Đổi lỗi thành safe response mà frontend có thể xử lý |

Annotation entity và việc có repository chỉ mô tả lưu trữ/truy vấn. Các điều kiện identity, membership, creator và state được thực thi qua service. Mỗi feature mới cần nối đầy đủ các điều kiện đó vào endpoint thực tế.

## 15. Nguồn đối chiếu và kiểm chứng tiếp theo

| Nội dung | Tài liệu/class test |
|---|---|
| Role và ownership baseline | [B01_BASELINE.md](D:/thi/UniHack2026/AidBridge/docs/architecture/B01_BASELINE.md) |
| Tính năng và API theo thiết kế | [API_SPECIFICATION.md](D:/thi/UniHack2026/AidBridge/docs/architecture/API_SPECIFICATION.md), [BUSINESS_WORKFLOWS.md](D:/thi/UniHack2026/AidBridge/docs/architecture/BUSINESS_WORKFLOWS.md) |
| Phạm vi và quyết định hiện hành | [MVP_SCOPE.md](D:/thi/UniHack2026/AidBridge/docs/planning/MVP_SCOPE.md), [DECISION_LOG.md](D:/thi/UniHack2026/AidBridge/docs/planning/DECISION_LOG.md) |
| B04 implementation, fixture grants và cách chạy tests | [B04_CASE_WORKFLOW.md](D:/thi/UniHack2026/AidBridge/docs/architecture/B04_CASE_WORKFLOW.md) |
| Quyền case qua HTTP, creator/cross-org/revocation | [CaseApiIT.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/test/java/com/aidbridge/casefile/CaseApiIT.java) |
| Quyền evidence/content và version history | [EvidenceAccessIT.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/test/java/com/aidbridge/casefile/EvidenceAccessIT.java) |
| Schema/upgrade V6 và ownership guards | [B04MigrationIT.java](D:/thi/UniHack2026/AidBridge/backend/aidbridge-backend/src/test/java/com/aidbridge/casefile/B04MigrationIT.java) |
| Latest execution evidence và task status | [TASK_BACKLOG.md](D:/thi/UniHack2026/AidBridge/docs/planning/TASK_BACKLOG.md) |

Ba lớp integration B04 phía trên đã có source, còn cần kết quả chạy PostgreSQL/HTTP để xác nhận runtime acceptance. Các chức năng ghi nhãn Theo thiết kế cần class/endpoint/tests tương ứng ở task sau.

## 16. Luồng backend P0 từ đăng nhập đến kết thúc

Ví dụ xuyên suốt là một tổ chức hỗ trợ một hộ bằng gạo và nước. Người lập hồ sơ, người xác minh, người đóng góp và người xác nhận giao hàng tham gia ở các bước khác nhau. Một người có thể có nhiều role, nhưng reviewer hồ sơ phải khác người lập và người xác nhận giao hàng phải khác người ghi nhận lần giao.

**Có code** nghĩa là đã thấy class thực thi trong source. Kết quả chạy thực tế xem ở mục 15 và backlog. **Theo thiết kế** nghĩa là phần đó còn chờ triển khai hoặc nối vào luồng. Tên class dự kiến trong các dòng này là mốc để đối chiếu khi hoàn thành backend. Các trạng thái của bước tương lai mô tả workflow mục tiêu.

### 16.1 Đăng nhập và lập hồ sơ

| Bước | Người dùng thấy gì? | Class đảm nhận và việc class làm | Hiện trạng |
|---|---|---|---|
| 1. Lấy CSRF | Frontend gọi `GET /api/auth/csrf` để lấy token và tên header cần gửi khi đăng nhập. | `AuthController.csrf()` trả token. `SecurityConfig` cấu hình lưu và kiểm tra token trong session. | Có code B03 |
| 2. Đăng nhập | Gửi email và mật khẩu qua `POST /api/auth/login`. Đăng nhập thành công thì trình duyệt dùng cookie session cho các request sau. | `AuthController.login()` nhận request, áp dụng chiến lược đổi session ID và lưu security context. `AuthService.login()` gọi xác thực. `CustomUserDetailsService` dùng `UserRepository` để nạp tài khoản cho Spring Security kiểm tra. | Có code B03 |
| 3. Xem quyền hiện tại | Sau đăng nhập, frontend lấy CSRF mới và gọi `GET /api/auth/me` để biết tài khoản thuộc tổ chức nào và có role gì. | `AuthController.me()` gọi `AuthService.me()` để đọc lại tài khoản và memberships. Khi người dùng thao tác trên dữ liệu tổ chức, `OrganizationService` kiểm tra lại role, membership và trạng thái tổ chức cho thao tác đó. | Có code B03 |
| 4. Tạo hồ sơ | `VOLUNTEER` chọn fixture bằng chứng, nhập thông tin hộ, consent và các mặt hàng cần hỗ trợ, rồi gửi `POST /api/cases`. Hồ sơ mới ở `SUBMITTED`. | `EvidenceController` và `EvidenceFixtureCatalog` cung cấp danh sách fixture được phép. `CaseController.create()` nhận request. `CaseService.create()` kiểm tra quyền và điều phối `BeneficiaryRepository`, `ConsentRepository`, `AidCaseRepository`, `CaseNeedItemRepository`, `EvidenceService` và `LocalAuditService`. `IdempotencyService` giữ kết quả để xử lý gửi lại. `CaseMapper` dựng `CaseDetailResponse`. | Có code B04 |
| 5. Xem hoặc bổ sung hồ sơ | Người có quyền xem chi tiết và tải bằng chứng riêng tư. Người lập có thể cập nhật hoặc thay tài liệu khi trạng thái và phiên bản hồ sơ cho phép. Nếu được yêu cầu bổ sung, người lập sửa rồi gửi lại để hồ sơ về `SUBMITTED`. | `CaseController` xử lý xem, sửa, thêm version và resubmit. `EvidenceController` xử lý tải nội dung. `CaseService` kiểm tra phạm vi, quyền creator, trạng thái, phiên bản và revision mới sau lần review. `EvidenceService` quản lý phiên bản tài liệu. `LocalAuditService` và `CaseRevisionRepository` kiểm tra lịch sử khi gửi lại. | Có code B04. Việc reviewer tạo quyết định yêu cầu bổ sung thuộc bước 6 |

### 16.2 Xác minh, công bố và giao hỗ trợ

| Bước | Người dùng thấy gì? | Class đảm nhận và việc class làm | Hiện trạng |
|---|---|---|---|
| 6. Xác minh độc lập | `VERIFIER` cùng tổ chức xem đúng phiên bản hồ sơ và chọn duyệt, từ chối hoặc yêu cầu bổ sung. Duyệt đưa hồ sơ sang `VERIFIED`. Từ chối kết thúc nhánh hồ sơ đó. Yêu cầu bổ sung quay lại bước 5. | `VerificationController` và `VerificationService` sẽ nhận và xử lý quyết định, kiểm tra reviewer khác creator và ghi đúng revision đã review. `VerificationRepository` ánh xạ quyết định qua `Verification`. `VerificationOutboxService` dùng `AuditEventRepository` và `BlockchainJobRepository` để lưu `CASE_VERIFIED` cùng job `PENDING` trong transaction duyệt. | B05 có mapping, repository tra cứu và outbox. Controller/service quyết định hiện là khung, chưa có luồng HTTP |
| 7. Tạo và công bố chiến dịch | `ORG_ADMIN` lập chiến dịch nhiều mặt hàng từ hồ sơ `VERIFIED`, rồi công bố để donor xem và cam kết. Hồ sơ chuyển sang `IN_PROGRESS`. | Dự kiến `CampaignController` nhận thao tác tạo, công bố và xem. `CampaignService` kiểm tra quyền, hồ sơ đã duyệt và số lượng từng mặt hàng. `CampaignRepository` lưu hoặc đọc chiến dịch cho service. | Theo thiết kế B06 |
| 8. Cam kết góp hàng | Donor chọn số lượng gạo và nước, gửi cam kết, xem tiến độ của mình hoặc hủy phần chưa được nhận. | Dự kiến `CommitmentController` nhận cam kết và hủy. `CommitmentService` kiểm tra quyền donor, khóa dòng chiến dịch cần cập nhật và kiểm tra sức chứa theo từng mặt hàng. `CommitmentRepository` lưu cam kết và hỗ trợ tra cứu cam kết của donor. | Theo thiết kế B07 |
| 9. Ghi nhận hàng thực nhận | `ORG_ADMIN` ghi nhận kho đã nhận bao nhiêu trong từng đợt. Có thể nhận một phần trước rồi nhận tiếp. | Dự kiến `ReceiptService` và repository của receipt trong feature `contribution` kiểm tra đúng cam kết, ghi từng dòng đã nhận và từ chối phần vượt lượng còn chờ nhận. | Theo thiết kế B08 |
| 10. Ghi nhận giao hàng | `VOLUNTEER` ghi lần giao thành công hoặc lần giao thất bại. Lần giao thành công ghi số lượng theo từng mặt hàng và phiên bản bằng chứng liên quan. | Dự kiến `DeliveryController` nhận request. `DeliveryService` kiểm tra đúng tổ chức, số lượng còn có thể giao và lưu qua `DeliveryRepository`. Lần giao thất bại có lý do và không trừ lượng hàng khả dụng. | Theo thiết kế B09 |
| 11. Xác nhận độc lập hoặc mở tranh chấp | `DELIVERY_CONFIRMER` khác người ghi nhận lần giao kiểm tra bằng chứng rồi xác nhận hoặc yêu cầu xem xét. Khi có tranh chấp, người được phép xử lý phải ghi lý do và kết quả giải quyết. | Dự kiến `DeliveryController` và `DeliveryService` thực thi xác nhận và kiểm tra khác người giao. `DisputeService` và `DisputeRepository` xử lý mở/giải quyết tranh chấp và cập nhật số lượng hợp lệ nếu một lần giao bị vô hiệu. | Theo thiết kế B10 |
| 12. Đóng chiến dịch | Khi **từng** mặt hàng đủ số lượng đã xác nhận và không còn tranh chấp hoặc yêu cầu xem xét, `ORG_ADMIN` gọi `POST /api/campaigns/{id}/close`. Campaign sang `CLOSED`, case sang `COMPLETED`. | Dự kiến `CampaignController` nhận yêu cầu đóng. `CampaignService` kiểm tra tổng đã xác nhận, quyền tổ chức và trạng thái tranh chấp trước khi cập nhật campaign, case và ghi audit trong cùng transaction. | Theo thiết kế B10 |

### 16.3 Đồng bộ Fuji và kết thúc phiên

| Bước | Người dùng thấy gì? | Class đảm nhận và việc class làm | Hiện trạng |
|---|---|---|---|
| Đồng bộ các mốc trong lúc xử lý hồ sơ | Timeline cho biết mốc đang chờ, lỗi hay đã xác nhận trên Fuji. Worker xử lý theo từng mốc từ lúc có job. | `VerificationOutboxService` đã có phần ghi job cho approval. Theo thiết kế, worker trong feature `blockchain` sẽ lấy job và gọi `BlockchainAdapter` qua Web3j, lưu từng lần gửi và đối chiếu receipt cùng event mong đợi. `AuditController` và `AuditService` dự kiến phục vụ timeline theo đúng phạm vi quyền. | Có code ghi outbox B05. Worker, adapter và timeline theo thiết kế P0-06 |
| Đăng xuất | Người dùng có thể kết thúc phiên bất kỳ lúc nào qua `POST /api/auth/logout` kèm CSRF. API trả `204`, session cũ hết hiệu lực. | `SecurityConfig` đăng ký đường dẫn logout với bộ lọc Spring Security để hủy session và xóa thông tin xác thực. Frontend xóa dữ liệu riêng của phiên trên giao diện. | Có code backend B03 |

**Hai điểm kết thúc.** `COMPLETED` kết thúc luồng nghiệp vụ thông thường của hồ sơ. Theo thiết kế, tranh chấp hợp lệ phát sinh sau đó có thể mở lại việc xử lý. Logout chỉ kết thúc phiên của người dùng. Những người khác vẫn có thể tiếp tục phần việc của họ, và worker có thể xử lý các job đã xếp hàng.

Khi hoàn thành backend P0, cập nhật các dòng **Theo thiết kế** bằng tên class và method thực tế, thêm link đến file và ghi kết quả test end-to-end trong backlog. Dùng luồng này để kiểm tra đủ đường đi thành công, nhánh yêu cầu bổ sung, tranh chấp và việc session cũ bị từ chối sau logout. [BUSINESS_WORKFLOWS.md](D:/thi/UniHack2026/AidBridge/docs/architecture/BUSINESS_WORKFLOWS.md), [API_SPECIFICATION.md](D:/thi/UniHack2026/AidBridge/docs/architecture/API_SPECIFICATION.md), [SYSTEM_DESIGN.md](D:/thi/UniHack2026/AidBridge/docs/architecture/SYSTEM_DESIGN.md), [TASK_BACKLOG.md](D:/thi/UniHack2026/AidBridge/docs/planning/TASK_BACKLOG.md)
