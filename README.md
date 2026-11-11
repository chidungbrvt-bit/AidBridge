# AidBridge v1.1

**Bridging Verified Needs with Transparent Aid**

Organization-First, Community-Driven, Blockchain-Verifiable Social Aid Platform.

AidBridge giúp tổ chức thiện nguyện quản lý nhu cầu, xác minh hồ sơ, tiếp nhận và bàn giao nhu yếu phẩm. Người thụ hưởng không cần tài khoản, smartphone hoặc ví. Avalanche Fuji lưu dấu vết kiểm toán; một transaction thành công không chứng minh hàng hóa đã được giao ngoài đời.

## Trạng thái hiện tại

- Ngày lập baseline: **03/11/2026**, timezone `Asia/Bangkok`.
- Workspace có planning, backend skeleton và một phần user/security có focused tests; **chưa có integrated P0 workflow/deployment**.
- B01 In Review; B02/B03 In Progress. V1 đã có focused PostgreSQL test; V2–V5 đã có SQL, chưa chạy kiểm thử migration toàn bộ. V3–V5 ghi theo SQL user cung cấp ngày04/11. Theo yêu cầu user, đã gỡ instance PostgreSQL riêng cùng script/data/credential trong workspace. Database/account trên PostgreSQL do user cài đặt cần được xác nhận lại trước khi nối Spring Boot.
- Mốc nội bộ: **18/11 đủ P0;20/11 demo ổn định;22/11 video/hồ sơ sẵn sàng**.25/11 là hạn nộp ngoài cùng, không phải ngày chờ làm xong.
- Ba developers,56h task+8h reserve/người. Dồn2.5h/ngày thường và5h/cuối tuần đến20/11;21-22/11 x3h;23-25/11 x1h reserve. Tổng64h/người không đổi.
- Bộ tài liệu là implementation specification đề xuất; không phải bằng chứng các tính năng đã hoàn thành.

## Nhóm phát triển

- **Dũng:** Backend.
- **Khoa:** Blockchain & Integration; phối hợp với Dũng ở phần Java/database/REST nối blockchain.
- **Minh:** Frontend & Product.

Xem [phân công chi tiết và checklist bàn giao Dũng ↔ Khoa](docs/planning/TEAM_RESPONSIBILITIES.md). Task IDs B/C/F giữ nguyên; hỗ trợ không làm tăng scope hoặc mặc định tăng quỹ giờ.

## Đọc theo thứ tự

1. [Master Plan](docs/planning/MASTER_PLAN.md): executive roadmap, capacity, milestones, first 48 hours.
2. [Product Requirements](docs/planning/PRODUCT_REQUIREMENTS.md) và [MVP Scope](docs/planning/MVP_SCOPE.md).
3. [Decision Log](docs/planning/DECISION_LOG.md): yêu cầu nguồn, lựa chọn đã xác nhận, đề xuất và câu hỏi mở.
4. [Business Workflows](docs/architecture/BUSINESS_WORKFLOWS.md), [System Design](docs/architecture/SYSTEM_DESIGN.md), [Database Design](docs/architecture/DATABASE_DESIGN.md).
5. [API Specification](docs/architecture/API_SPECIFICATION.md) và [Blockchain Architecture](docs/architecture/BLOCKCHAIN_ARCHITECTURE.md).
6. [Task Backlog](docs/planning/TASK_BACKLOG.md), [Daily Roadmap](docs/planning/DAILY_ROADMAP.md), [Team Responsibilities](docs/planning/TEAM_RESPONSIBILITIES.md).
7. [Test Strategy](docs/quality/TEST_STRATEGY.md), [Security & Privacy](docs/quality/SECURITY_PRIVACY.md), [Risk Register](docs/quality/RISK_REGISTER.md).
8. [Demo Scenario](docs/demo/DEMO_SCENARIO.md), [Submission Checklist](docs/demo/SUBMISSION_CHECKLIST.md), [Engineering Guidance](AGENTS.md).

## Source of truth

[Blueprint v1.1, 21 trang](docs/source/AidBridge_Product_Blueprint_v1.1_Complete.pdf), cập nhật 03/11/2026, đã được đọc toàn bộ khi lập kế hoạch. PDF gốc tại root được giữ nguyên; bản copy có cùng SHA-256. Hai Master Prompt được người dùng cung cấp là yêu cầu nội dung và giới hạn workspace. Các lựa chọn được xác nhận trong chat được ghi lại tại Decision Log.

Ưu tiên khi diễn giải: chỉ dẫn người dùng hiện hành và quyết định đã phê duyệt; blueprint cho định hướng và nghiệp vụ; technical proposals trong docs phải giữ nhãn đề xuất. Không tự thay công nghệ hoặc mở rộng scope.

## MVP sẽ chứng minh gì?

`Create Case -> Independent Verification -> Publish Campaign -> Pledge Goods -> Receive Goods -> Deliver Goods -> Independent Confirmation -> Inspect Audit`

Mỗi campaign có nhiều dòng hàng, số lượng nguyên và đơn vị riêng. `PLEDGED`, `RECEIVED`, `DELIVERED`, `CONFIRMED` là các mốc riêng. Dùng dữ liệu giả lập và tài khoản riêng được provision trước. Backend ký giao dịch bằng khóa Fuji; không có tiền thật, token, NFT, escrow, custom L1, AI hoặc offline application trong P0.

## Cấu trúc

| Thư mục | Vai trò hiện tại / tương lai |
|---|---|
| `docs/source` | PDF bất biến |
| `docs/planning` | Requirements, scope, decisions, roadmap, backlog và team |
| `docs/architecture` | Workflow, schema, APIs và blockchain contracts |
| `docs/quality` | Test specification, security và risks |
| `docs/demo` | Script và submission gates |
| `backend` | Spring Boot modular monolith tại backend/aidbridge-backend; mới triển khai một phần user/auth |
| `frontend` | Dự kiến Next.js; hiện trống |
| `contracts` | Dự kiến Solidity/Hardhat; hiện trống |
| `infra` | Dành cho cấu hình môi trường/deploy; hiện chưa có Compose hoặc PostgreSQL automation |

## Local database và setup còn lại

User quản lý PostgreSQL qua installation/pgAdmin của mình. Không còn instance riêng, script startup hoặc data PostgreSQL trong workspace. B02 tiếp tục từ việc xác nhận version/host/port/database/account trên server user chọn, rồi cấu hình datasource bằng environment, kiểm tra Flyway và full app health. Việc server PostgreSQL đang chạy chưa chứng minh app đã kết nối đúng database/account.

JDK21 là target compile; các technology proposals còn lại ở System Design. Version public-hosted database và compatibility với provider phải chốt riêng trước deploy; không suy ra từ version local. Secrets application dùng environment/secret store; không hardcode/commit credentials. Xong task sớm thì pull task tiếp theo đã đủ dependencies trong approved scope, không chờ mốc ngày.

## Quyền thực thi tiếp theo

User đã cho phép từng phần user/security, B01 consolidation và B02 phần1. Chỉ tiếp tục application work trong scope tương ứng; không suy diễn thành quyền full CRUD/deploy/purchase.
