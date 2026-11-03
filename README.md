# AidBridge v1.1

**Bridging Verified Needs with Transparent Aid**

Organization-First, Community-Driven, Blockchain-Verifiable Social Aid Platform.

AidBridge giúp tổ chức thiện nguyện quản lý nhu cầu, xác minh hồ sơ, tiếp nhận và bàn giao nhu yếu phẩm. Người thụ hưởng không cần tài khoản, smartphone hoặc ví. Avalanche Fuji lưu dấu vết kiểm toán; một transaction thành công không chứng minh hàng hóa đã được giao ngoài đời.

## Trạng thái hiện tại

- Ngày lập baseline: **03/11/2026**, timezone `Asia/Bangkok`.
- Workspace mới có tài liệu planning; **chưa có ứng dụng hoặc Git repository**.
- Application development, dependencies, database migrations, Docker và deployment **chưa được thực hiện**.
- Mục tiêu MVP: **25/11/2026**; ba developers, khoảng 20 giờ/người/tuần.
- Bộ tài liệu là implementation specification đề xuất; không phải bằng chứng các tính năng đã hoàn thành.

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
| `backend` | Dự kiến Spring Boot modular monolith; hiện trống |
| `frontend` | Dự kiến Next.js; hiện trống |
| `contracts` | Dự kiến Solidity/Hardhat; hiện trống |
| `infra` | Dự kiến Compose/deployment configuration; hiện trống |

## Setup dự kiến, chưa phải hướng dẫn chạy đã kiểm chứng

Sau khi coding được phê duyệt: JDK 21, Maven Wrapper, Node.js 22.13.0+ thuộc nhánh được Hardhat hỗ trợ, npm lockfiles, Docker Desktop/Compose và PostgreSQL 17. Chốt patch versions và compatibility trước khi scaffold; không dùng prerelease. Baseline đề xuất: Spring Boot 4.1.x + springdoc 3.x, Next.js 16.x + React 19.x, Tailwind 4.x, Hardhat 3.x + Solidity 0.8.28 + OpenZeppelin 5.x, Web3j 4.14.x. Kiểm tra dependency/security compatibility tại task setup thay vì tự nâng major trong quá trình làm.

Không có build/run command khả dụng ở thời điểm planning. Environment variables và quy trình setup tương lai nằm trong Master Plan; không commit secrets. Git/GitHub workflow chỉ là đề xuất cho giai đoạn sau, không phải Git đã được khởi tạo.

## Quyền thực thi tiếp theo

Bộ tài liệu này được tạo theo phê duyệt chuẩn bị workspace. Chỉ bắt đầu application implementation khi người dùng chỉ định/phê duyệt các task coding; không suy diễn rằng việc duyệt documentation cho phép deploy hoặc mua dịch vụ.
