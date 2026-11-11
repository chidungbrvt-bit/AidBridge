# AidBridge engineering guidance

Ngày tạo: 03/11/2026. Owner duy trì: Backend Engineer. Status: documentation baseline; application chưa triển khai.

## Objective và nguồn sự thật

Deliver một workflow hỗ trợ nhu yếu phẩm có kiểm soát và audit trên Avalanche Fuji: đủ P0 trước hoặc trong18/11/2026, demo ổn định20/11, video/hồ sơ sẵn sàng22/11;25/11 chỉ là hạn nộp ngoài cùng. Người thụ hưởng không cần thiết bị hoặc wallet. Đọc `docs/planning/MASTER_PLAN.md`, `MVP_SCOPE.md`, `DECISION_LOG.md` và task liên quan trước khi thay đổi. PDF nguồn tại `docs/source/AidBridge_Product_Blueprint_v1.1_Complete.pdf` là product source of truth; user instructions và các lựa chọn được xác nhận trong Decision Log có ưu tiên hiện hành.

Không mô tả thiết kế là tính năng đã chạy. Phân biệt SOURCE, USER_CONFIRMED, PROPOSED, ASSUMPTION và OPEN. Hiện chỉ documentation preparation được phê duyệt. Chưa có quyền scaffold, cài dependencies, init Git, chạy containers, tạo remote, commit/push, deploy hoặc mua dịch vụ. Chỉ bắt đầu các việc đó khi có yêu cầu tương ứng của người dùng; authorization đã có không cần hỏi lại.

## Thành viên và phối hợp

Dũng = B/Backend; Khoa = C/Blockchain & Integration; Minh = F/Frontend & Product. Dũng và Khoa cùng làm integration theo TEAM_RESPONSIBILITIES: Dũng nghiệp vụ/SQL/audit/outbox/REST và hỗ trợ Java; Khoa contract/Fuji/ABI/events/signing/nonce/receipt/log; Minh UI/E2E. Không giao mọi đoạn Java liên quan blockchain cho một mình Khoa. Giữ một primary owner/task, ghi rõ phần/người/giờ hỗ trợ và không double-count hoặc tự cộng việc vào lịch kín. Hỗ trợ sâu hơn dùng actual saved time hoặc điều chỉnh capacity trước. Chia việc không phải coding/deploy authorization mới.

## Approved MVP boundaries

- Organization-first; simulated household data; nhiều campaign item; integer quantities và fixed unit.
- Sáu P0: organization login/RBAC, create case, independent verification, campaign/commitment, goods receipt/delivery/independent confirmation, Fuji audit.
- Mỗi actor là tài khoản riêng. Không beneficiary login, self-registration, money processing, escrow, token, NFT hoặc custom Avalanche L1.
- Evidence dùng fixtures riêng tư có version. UI upload, reports, organization onboarding và member-management UI thuộc P1.
- Không mở rộng sang AI/ML, mobile offline, hotline hoặc multi-organization trust infrastructure.

## Architecture và folders

Modular monolith: Next.js/TypeScript/Tailwind -> Spring Boot/Spring Security -> PostgreSQL/Flyway. Solidity/Hardhat trên Fuji; Java Web3j adapter, database-backed worker. Không microservices, Kafka, RabbitMQ, Redis, Kubernetes hoặc abstraction layer không có nhu cầu cụ thể.

- `backend`: `com.aidbridge.auth`, `organization`, `casefile`, `verification`, `campaign`, `contribution`, `delivery`, `audit`, `blockchain`, `shared`.
- Mỗi feature chứa controller/service/repository/entity/DTO cần dùng. Không global packages gom mọi DTO/controllers/services, không demo package trong `src/main`.
- Repository của entity dùng Spring Data `JpaRepository` theo D45. Service điều phối nghiệp vụ và transaction. Khóa idempotency dùng native query của JPA vì cần cơ chế riêng của PostgreSQL. Test tích hợp có thể dùng JDBC để dựng và kiểm tra dữ liệu.
- Controller chỉ xử lý HTTP; service thực thi ownership, state, identity checks và transaction boundary; repository luôn query theo organization khi resource private.
- `frontend`: App Router; components theo feature; một API client; không business rule chỉ tồn tại trong UI.
- `contracts`: một registry không upgradeable, reusable authorization từ OpenZeppelin; không tự viết cryptography.
- `infra`: cấu hình phát triển/deploy; không private keys hay data thật.
- `docs`: cập nhật contract, acceptance và tiến độ khi hành vi thay đổi.

## Naming và interface conventions

All future application string literals, identifiers, API messages and UI labels use English. Documentation có thể dùng tiếng Việt. Java classes PascalCase, methods/fields camelCase; SQL snake_case; TS components PascalCase; environment variables UPPER_SNAKE_CASE. Không dùng `case` làm Java package; dùng `casefile`. API JSON camelCase, UUID, UTC ISO-8601; quantities là integers; statuses UPPER_SNAKE_CASE. Lịch làm việc theo Asia/Bangkok.

## Rules và integrity

Thực thi BR-01..BR-12 như `BUSINESS_WORKFLOWS.md` và `TEST_STRATEGY.md`. Creator không approve own case, delivery recorder không confirm own delivery dù có nhiều roles. Kiểm tra identity, active membership, ownership và state tại backend. Platform role không tự cho phép truy cập PII/evidence.

Không hợp nhất PLEDGED/RECEIVED/DELIVERED/CONFIRMED. Quantity limits áp dụng từng campaign item; sử dụng transaction/locks, không chỉ frontend arithmetic. Business status, evidence review và blockchain sync là ba trục riêng. Completion cần đủ confirmed quantities và không dispute.

Audit append-only; sửa evidence tạo version mới; không sửa record được review để che thay đổi. Business update + audit + outbox trong cùng SQL transaction. Không giữ SQL transaction trong lúc chờ RPC/receipt. Retry cùng logical event ID; signed bytes và tx hash phải được lưu trước broadcast. Receipt/event phải hợp lệ mới confirmed.

## Security/privacy

Dùng simulated beneficiaries. Không PII, evidence gốc, private URL, credentials hoặc salt trong calldata/events/logs/public DTO. Private fixtures qua authenticated authorization endpoint. Session cookie HttpOnly/Secure, CSRF enabled, same-origin API. Dùng framework password hashing, secrets qua environment/secret store, không log request bodies có dữ liệu nhạy cảm. Backend relayer authorization không phải chữ ký độc lập của người dùng; không tuyên bố blockchain chứng minh delivery vật lý hoặc loại bỏ thông đồng.

## Testing và review

Mỗi implementation task cần meaningful positive + relevant negative test, authorization và state guards. Test concurrency với PostgreSQL thật; contract tests kiểm tra callers, org scope, duplicate IDs, transitions, emitted events. E2E core lifecycle và pending/failed blockchain states. Không viết test chỉ mirror implementation hoặc tests vô nghĩa cho đổi Markdown nhỏ. Không coi Fuji transaction hash là confirmed. Không đánh dấu test Pass khi chưa chạy.

Trước khi Done: expected behavior + DB effects + permission rejection + error handling + tests + cross-owner review + integration evidence + docs update. Auth/authorization/signing/contract/concurrency cần review nghiêm túc; AI output không tự được chấp nhận.

## Task progress và thay đổi kiến trúc

Chỉ làm approved task; đọc dependencies và acceptance trước khi sửa. Xong task sớm thì handoff/pull task tiếp theo đã đủ dependencies ngay, không đợi ngày trong roadmap. Khi blocked, hỗ trợ critical path/review/tests thay vì thêm scope. Giữ56h task+8h reserve/người với phân bổ dồn giờ đã được user chọn tại D27; không giảm estimates/quality để tạo cảm giác hoàn thành nhanh. Cập nhật `TASK_BACKLOG.md` theo Not Started -> In Progress -> In Review -> Testing -> Done; Blocked phải có reason/owner/next action. Record test command, result, artifact/transaction evidence, actual effort và ngày hoàn thành; roadmap dùng dữ liệu này để điều chỉnh.

Một primary owner/task; support và reviewer không thay owner. Không tự sửa estimate/date để che trễ. Khi nguồn task chưa được phê duyệt coding, chỉ làm documentation/review đã được yêu cầu.

Với thay đổi kiến trúc hoặc scope ảnh hưởng API, trust boundary, data model, key custody, budget hay deadline: ghi decision proposal với alternatives, trade-offs, affected tasks/tests và recommendation trong Decision Log; xin quyết định người dùng trước khi thực hiện thay đổi chưa được ủy quyền. Routine implementation choices trong approved design không cần xin lại.

Giữ tất cả thay đổi trong workspace. Không xóa/ghi đè tài liệu chưa đọc. Không gửi messages cho NGO hoặc người khác nếu chưa được yêu cầu cụ thể; kế hoạch phỏng vấn chỉ là checklist cho nhóm.
