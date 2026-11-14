# Task Backlog

Ngày tạo: 03/11/2026. Cập nhật: 07/11/2026. Người duy trì: Dũng (B). Status: **32 tasks Not Started, B01 In Review, B02/B03/B04 In Progress**. USER_CONFIRMED: từng phần user/security, B01 documentation, B02 phần1 PostgreSQL setup, B03 scoped fixes/tests và B04 Beneficiary entity/annotations; quyền này không tự mở rộng sang full CRUD/deploy/purchase. Status dựa trên bằng chứng hiện có, không đổi owner/estimate/deadline.

Cập nhật riêng Minh 13/11/2026: F03/F04 đã merge qua PR #11, F05 fixture và F06 qua PR #17. F05 live còn chờ T01/T02 và mapping case từ bằng chứng Fuji PR #18. F09 D02/S01/S03 đang In Review trong phạm vi fixture nối F08. Xem [bàn giao Minh](../handoffs/MINH_F09_2026-11-13.md). Đoạn tổng hợp 07/11 phía trên là lịch sử.

## Thành viên và phối hợp tích hợp

- B = **Dũng — Backend**; C = **Khoa — Blockchain & Integration**; F = **Minh — Frontend & Product**. Các mã task không đổi.
- C05/C07/C08/C09/C10 vẫn có Khoa là người chịu trách nhiệm chính, nhưng Dũng trực tiếp làm/hỗ trợ phần Java, database, transaction, DTO và REST theo [phân công chi tiết](TEAM_RESPONSIBILITIES.md).
- Dũng bàn giao đầu ra B05/B07/B08–B11 cho Khoa theo từng tính năng, không chờ toàn bộ backend xong. Khoa chịu phần contract/Fuji/signing/nonce/receipt/event semantics; Minh ghép UI và E2E.
- Không cộng cứng giờ hỗ trợ ngoài lịch đang kín. Công chung ghi người/phần việc/giờ một lần; hỗ trợ sâu hơn dùng giờ thực tế tiết kiệm hoặc điều chỉnh allocation trước. Không tự lấy toàn bộ8h reserve như thể rảnh sớm trước18/11.
- Không đổi primary owner, estimates, deadlines, dependencies, test/acceptance gates hoặc trạng thái task trong lần cập nhật phân công này.

## Early-completion policy - user confirmed03/11/2026

18/11 đủ sáu P0;20/11 stable demo;22/11 package/video/runbooks sẵn sàng;25/11 chỉ external submission limit. Xong task sớm thì handoff ngay và pull task tiếp theo đã đủ dependencies, không đợi đến ngày trong lịch. Khi blocked, giúp critical path/review/tests; không tự thêm P1/ML (ngoại lệ: bản demo ML theo D51, tính riêng bên dưới). WIP1 task chính/người.

Giữ nguyên56h work+8h reserve/người.03-20/11 weekday2.5h/weekend5h=55h;21-22/11 x3h=6h;23-25/11 x1h=3h reserve; tổng64h/người không đổi. D26/D27 thay cách phân bổ và deadlines cũ, không giảm estimate/quality hoặc ghi lùi completion. Tiến độ thực tế cập nhật trong bảng và progress log; chưa có bằng chứng deploy.

## Issue conventions và estimates

Mỗi task dưới đây P0 hoặc P0 enabler; không cộng P1/P2 vào168h. Complexity S/M/L gồm security/integration uncertainty, không chỉ effort. Estimates gồm JIT learning, feature tests/review/docs; support không được double-book ngoài capacity. B/C/F mỗi12 tasks/56h +8h reserve.

Dependencies là gate cho integrated Done; mock/design/scaffold prework có thể overlap nhưng không đánh dấu Done khi chỉ có mocks. Planned integrated finish dưới đây là forecast từ daily allocation, không actual completion. State: Not Started -> In Progress -> In Review -> Testing -> Done; Blocked cần reason/owner/next action.

| ID | Feature | Phụ trách chính | Hỗ trợ | Complexity | Hours | Dependencies | Deadline | Planned finish | Status |
|---|---|---|---|---|---:|---|---|---|---|
| B01 | Design | Dũng (B) | Khoa / Minh | M | 2 | Planning review | 2026-11-03 | 2026-11-03 | In Review |
| B02 | Foundation | Dũng (B) | Khoa / Minh | M | 3 | B01 | 2026-11-04 | 2026-11-04 | In Progress |
| B03 | P0-01 | Dũng (B) | Khoa / Minh | L | 5 | B02 | 2026-11-06 | 2026-11-06 | In Progress |
| B04 | P0-02 | Dũng (B) | Khoa / Minh | M | 4 | B03 | 2026-11-07 | 2026-11-07 | In Progress |
| B05 | P0-03 | Dũng (B) | Khoa / Minh | L | 4 | B04, C01 | 2026-11-08 | 2026-11-08 | In Review |
| B06 | P0-04 | Dũng (B) | Khoa / Minh | M | 6 | B05 | 2026-11-10 | 2026-11-10 | In Review |
| B07 | P0-04 | Dũng (B) | Khoa / Minh | L | 7 | B06 | 2026-11-13 | 2026-11-13 | In Review |
| B08 | P0-05 | Dũng (B) | Khoa / Minh | M | 5 | B07 | 2026-11-14 | 2026-11-14 | Testing |
| B09 | P0-05 | Dũng (B) | Khoa / Minh | L | 5 | B08 | 2026-11-15 | 2026-11-15 | Not Started |
| B10 | P0-05 | Dũng (B) | Khoa / Minh | L | 6 | B09 | 2026-11-18 | 2026-11-17 | Not Started |
| B11 | Quality | Dũng (B) | Khoa / Minh | L | 6 | B03, B04, B05, B06, B07, B08, B09, B10 | 2026-11-20 | 2026-11-20 | Not Started |
| B12 | Submission | Dũng (B) | Khoa / Minh | M | 3 | B11 | 2026-11-22 | 2026-11-21 | Not Started |
| C01 | Design | Khoa (C) | Dũng / Minh | L | 4 | B01 | 2026-11-04 | 2026-11-04 | In Review |
| C02 | Foundation | Khoa (C) | Dũng / Minh | M | 3 | C01 | 2026-11-05 | 2026-11-05 | In Review |
| C03 | P0-03/P0-06 | Khoa (C) | Dũng / Minh | L | 5 | C01, C02 | 2026-11-07 | 2026-11-07 | In Review |
| C04 | P0-06 | Khoa (C) | Dũng / Minh | M | 2 | C03 | 2026-11-07 | 2026-11-07 | In Review |
| C05 | P0-03/P0-06 | Khoa (C) | Dũng / Minh | L | 6 | B05, C04 | 2026-11-09 | 2026-11-08 | Blocked |
| C06 | P0-04/P0-05/P0-06 | Khoa (C) | Dũng / Minh | L | 6 | C03 | 2026-11-11 | 2026-11-11 | In Progress |
| C07 | P0-06 | Khoa (C) | Dũng / Minh | L | 7 | C05 | 2026-11-14 | 2026-11-14 | In Review |
| C08 | P0-06 | Khoa (C) | Dũng / Minh | L | 7 | C06, C07 | 2026-11-15 | 2026-11-15 | In Progress |
| C09 | P0-06 | Khoa (C) | Dũng / Minh | M | 3 | C08 | 2026-11-16 | 2026-11-16 | Not Started |
| C10 | P0-05/P0-06 | Khoa (C) | Dũng / Minh | L | 4 | B08, B09, C08 | 2026-11-17 | 2026-11-17 | Not Started |
| C11 | Quality | Khoa (C) | Dũng / Minh | L | 6 | C06, C07, C08, C09, C10 | 2026-11-20 | 2026-11-20 | Not Started |
| C12 | Submission | Khoa (C) | Dũng / Minh | M | 3 | C11 | 2026-11-22 | 2026-11-21 | Not Started |
| F01 | Validation | Minh (F) | Dũng / Khoa | M | 2 | Planning review | 2026-11-03 | 2026-11-03 | In Review |
| F02 | Design | Minh (F) | Dũng / Khoa | M | 4 | B01 | 2026-11-05 | 2026-11-05 | In Review |
| F03 | P0-01 | Minh (F) | Dũng / Khoa | M | 4 | F02, B03 | 2026-11-06 | 2026-11-06 | In Review |
| F04 | P0-02/P0-03 | Minh (F) | Dũng / Khoa | L | 6 | F03, B04, B05 | 2026-11-08 | 2026-11-08 | In Review |
| F05 | P0-06 | Minh (F) | Dũng / Khoa | L | 4 | F04, C05 | 2026-11-09 | 2026-11-08 | In Review (fixture) |
| F06 | P0-04 | Minh (F) | Dũng / Khoa | M | 6 | F02, B06 | 2026-11-11 | 2026-11-11 | In Review |
| F07 | P0-04 | Minh (F) | Dũng / Khoa | M | 5 | F06, B07 | 2026-11-13 | 2026-11-13 | In Review |
| F08 | P0-05 | Minh (F) | Dũng / Khoa | M | 5 | F04, B08, B09 | 2026-11-15 | 2026-11-15 | In Review |
| F09 | P0-05/P0-06 | Minh (F) | Dũng / Khoa | L | 5 | F08, B10, C09 | 2026-11-18 | 2026-11-17 | In Review (fixture) |
| F10 | Deployment | Minh (F) | Dũng / Khoa | L | 4 | F09, C10 | 2026-11-18 | 2026-11-18 | Not Started |
| F11 | Quality | Minh (F) | Dũng / Khoa | L | 6 | F10, B11, C11 | 2026-11-20 | 2026-11-20 | Not Started |
| F12 | Submission | Minh (F) | Dũng / Khoa | L | 5 | F11 | 2026-11-22 | 2026-11-22 | Not Started |

**Total36 tasks/168h; B56h, C56h, F56h;24h reserve against192h capacity.**

**Gói demo ML (D51, 11/11/2026) tính riêng:** ML-1..ML-5 gồm 9h, ngoài 36 task/168h và ngoài Definition of Done của P0; giờ thêm lấy từ dự phòng. Xem [DAILY_ROADMAP](DAILY_ROADMAP.md#gói-demo-ml-d51--tính-riêng-ngoài-168h).

## Issue-ready specifications

### B01 - Khóa RBAC/ERD/API

- **Feature / priority:** Design / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** M / 2h.
- **Dependencies:** Planning/source review; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-03 through 2026-11-03; deadline 2026-11-03, Asia/Bangkok.
- **Description / expected behavior:** Khóa RBAC/ERD/API; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Output files:** docs/architecture/B01_BASELINE.md; API_SPECIFICATION.md; DATABASE_DESIGN.md; BUSINESS_WORKFLOWS.md; SYSTEM_DESIGN.md; initial mapping trong BLOCKCHAIN_ARCHITECTURE.md.
- **Acceptance criteria:** B/C/F đồng ý case/need/consent ownership, DTO/error/idempotency và initial event contract.
- **Required tests:** BR01-P/N, BR02-P/N, BR03-P/N; design review.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review,04/11/2026. USER_CONFIRMED: user yêu cầu thống nhất RBAC/ERD/API. B01_BASELINE tổng hợp field/role/status canonical theo V1/User, giữ explicit donor grant theo thiết kế trước; DTO/error/idempotency/ownership và initial audit/contract event mapping đồng bộ trong các design docs. Đối chiếu source PDF pp.4-5 và text/code hiện có; chỉ thay Markdown, không sửa Java/SQL. Các tình huống BR01/02/03 positive/negative đã viết để review, chưa phải runtime tests Pass. Dũng/Khoa/Minh chưa có cross-owner acceptance; human actual effort và integrated completion date chưa ghi nhận.
- **Findings handed off:** V1 thiếu donor_enabled/public_ref/relayer/approval metadata theo design target; V2–V5 chỉ placeholders0 bytes. B02 implement/verify migrations và startup/health; B03 bổ sung donor mapping, auth/me, membership/state guard, full error envelope/fieldErrors array; C01/C03/C05 review/golden vector/ABI. Giữ ownership/identity/transaction boundaries, không thay scope/estimate/deadline để che gaps.
- **Static verification04/11/2026:** bundled Python `-X utf8 backend/aidbridge-backend/target/verify-b01.py` từ repo root:15 consistency checks OK;16 relative file links resolved trong11 docs; SQL/Java enums, identity/MeResponse naming, donor gap, V2–V5 placeholders và36 task statuses khớp. `git diff --check` cho affected docs không có whitespace errors. Đây là document consistency verification; không ghi runtime BR tests Pass hoặc thay cross-owner review.

### B02 - Spring/PostgreSQL/Flyway skeleton

- **Feature / priority:** Foundation / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** B01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-03 through 2026-11-04; deadline 2026-11-04, Asia/Bangkok.
- **Description / expected behavior:** Spring/PostgreSQL/Flyway skeleton; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files:** backend/aidbridge-backend/pom.xml; mvnw/mvnw.cmd; src/main/resources/application.properties và db/migration; hướng dẫn environment/database user quản lý. Compose chỉ khi được chọn riêng, không là điều kiện bắt buộc cho native setup.
- **Acceptance criteria:** Sau coding approval: health+DB, clean migration; no ddl-auto update, no secrets in files.
- **Required tests:** Clean/upgrade migration smoke; safe errors/health access.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Progress,04/11/2026: Spring Boot/Maven skeleton, dependencies, V1 SQL và một phần config có. Lượt trước UserRepositoryTest chạy PostgreSQL18.6 + Flyway target1/ddl-auto validate,7 focused tests pass tổng cộng (3 entity,4 repo), log target/user-repository-verification/maven-test.log. Đây không phải full B02 clean/upgrade acceptance: V2–V5 là0-byte placeholders; chưa có full-app DB startup/health evidence hoặc infra/compose.yml; target runtime17/18 cần pin/verify. Identity forward migration theo B01 còn thiếu. Không coi việc có file là đã có schema; actual human effort, review và completion date chưa ghi nhận.
- **Local DB hiện tại04/11/2026:** user yêu cầu xóa phần tạo PostgreSQL riêng. Đã gỡ instance5433, toàn bộ scripts/version pin/data/credentials trong infra/postgres và10 artifacts test được tạo riêng cho phần này; rule Git ignore riêng cũng đã gỡ. Không còn target DB/account Ready trong workspace; cần xác nhận database/account/endpoint trên installation user quản lý trước khi nối datasource.
- **Lịch sử đã kết thúc:** trước khi gỡ, native18.6 setup đã có16 isolated checks,14 lifecycle checks và7 default-directory regression checks pass. Đây chỉ là evidence lịch sử; generated scripts/logs liên quan đã xóa theo yêu cầu. Không dùng các kết quả này để đánh dấu database mới hoặc B02 Done. V1 repository test evidence của feature user được giữ riêng.
- **Removal verification04/11/2026:** kiểm tra absolute path/reparse points và postmaster PID/command/data/port5433 rồi stop sạch trước recursive removal. Không còn infra/postgres hoặc process/listener của instance đó. PostgreSQL binaries trên máy còn nguyên; sau thao tác service postgresql-x64-18 Running và cổng5432 accepting. Không sửa Java/SQL, không chạy migration hoặc reset password server khác. Evidence local/ignored: target/postgres-removal-verification.json. B02 In Progress; human actual effort/cross-owner review chưa ghi nhận.

- **SQL update04/11/2026 (D38):** V2 hiện đã có SQL do user điền; V3/V4/V5 đã ghi theo SQL user cung cấp, ghép phần đầu/cuối V5 không trùng bảng/index. Những ghi chú placeholders0 bytes phía trên là hiện trạng trước lượt này. Bundled Python inline static checker đọc V1–V5:26 tables/29 explicit indexes, ngoặc cân bằng, tên không trùng, REFERENCES tới bảng/cột đã khai báo; V3=13/V4=7/V5=18 statements. Chỉ kiểm tra cấu trúc tĩnh, không phải PostgreSQL parser hoặc migration test; chưa chạy Flyway/database. B02 vẫn In Progress; clean/upgrade, guards, review và actual human effort chưa ghi nhận. Không đánh dấu B06–B10/C05–C08 Done chỉ vì đã có SQL.

- **B02 config07/11/2026:** user cho phép bổ sung các cấu hình còn thiếu trong `application.properties`: giới hạn Actuator exposure ở health, ẩn health components, explicit Flyway clean-disabled và ẩn message/stacktrace/binding errors của error endpoint. Dùng `spring.web.error.include-*` theo metadata Spring Boot4.1.2 đang có trên máy; các tên `server.error.include-*` trong hướng dẫn chat cũ đã bị thay thế từ Boot4. Kiểm tra bằng bundled Python inline (`-X utf8 -`):9 required properties đúng giá trị, xuất hiện một lần và có metadata không deprecated; `git diff --check -- backend/aidbridge-backend/src/main/resources/application.properties` pass. Đây là kiểm tra cấu hình tĩnh; không chạy application/database hoặc HTTP health trong lượt này. B02 vẫn In Progress; runtime evidence và cross-owner review còn cần xác nhận, actual human effort chưa ghi nhận.
- **Latest dependency report07/11/2026:** user báo B02 hoàn tất khi giao B04; trước đó báo UserRepositoryTest4 tests, DatabaseMigrationTest2 tests và application/health UP trên installed PostgreSQL5432/aidbridge/aidbridge_app. Đây là user-reported completion, không phải agent chạy lại. Local surefire còn UserRepositoryTest4/4 pass nhưng không có DatabaseMigrationTest report trong thư mục reports hiện tại. Không tự restart B02; B04 chỉ regression clean/upgrade/validation cho migration mới khi có test environment. Không bổ sung actual effort/reviewer identity chưa được cung cấp; các trạng thái/bằng chứng cũ ở trên là lịch sử.

### B03 - Session auth và tenant isolation

- **Feature / priority:** P0-01 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** B02; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-05 through 2026-11-06; deadline 2026-11-06, Asia/Bangkok.
- **Description / expected behavior:** Session auth và tenant isolation; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend auth/organization controllers/services/repositories; SecurityConfig; AuthApiIT.
- **Acceptance criteria:** Login/logout/me/csrf, rotate session, own active membership; no shared staff account; separate seed secrets.
- **Required tests:** BR01-P/N; IT09, IT15; invalid login/CSRF/logout/fixation.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Progress, 04/11/2026: user cho phép hoàn thiện `user/entity/User.java` và `user/repository/UserRepository.java` trong `backend/aidbridge-backend`. Entity ánh xạ migration V1 hiện có; UUID, timestamps, validation, mặc định ACTIVE/USER, chuẩn hóa email và ẩn password hash khỏi JSON. Repository hỗ trợ CRUD và tìm/kiểm tra email không phân biệt hoa thường. Đây là prework; B02 chưa được xác nhận Done. Session, CSRF, membership authorization và tenant isolation chưa triển khai trong phạm vi này; cross-owner review chưa thực hiện. Actual effort và completion date của B03 chưa có.
- **Verification 04/11/2026:** Maven 3.9.16 chạy `mvn -B -ntp -s target/user-repository-verification/settings.xml -Dmaven.repo.local=target/user-repository-verification/repository -Dtest=UserTest,UserRepositoryTest test`: **BUILD SUCCESS, 7 tests, 0 failures/errors/skipped** (3 entity, 4 repository). PostgreSQL 18.6 riêng, Flyway target V1, Hibernate `ddl-auto=validate`; database tạm đã dừng. Đã kiểm tra insert/read UUID/defaults/timestamps, lookup/existence có và không có email, index từ chối email trùng khác hoa/thường, persist DISABLED/update timestamp, validation và password hash không xuất JSON. Artifacts: `backend/aidbridge-backend/target/surefire-reports/` và `target/user-repository-verification/maven-test.log` (ignored, local). Chưa kiểm tra toàn bộ application hoặc migration V2–V5.
- **Rerun repository tests:** dùng database PostgreSQL dành riêng cho test; đặt `AIDBRIDGE_TEST_DATABASE_URL` (JDBC URL), `AIDBRIDGE_TEST_DATABASE_USERNAME`, `AIDBRIDGE_TEST_DATABASE_PASSWORD` trong environment rồi chạy Maven với `-Dtest=UserTest,UserRepositoryTest test`. Không có URL thì lớp repository test được skip; không coi kết quả đó là đã kiểm tra PostgreSQL. Không dùng database production cho các test này.
- **User response/mapping 04/11/2026:** theo yêu cầu tiếp theo, thêm `user/dto/response/UserResponse.java` (record gồm id/fullName/email/status/platformRole/createdAt) và `user/mapper/UserMapper.java` (Spring component, `toResponse(User)`, từ chối null). Mapping thủ công, không thêm dependency. DTO dành cho response đã được authorization; không phải public profile, không có password hash. Chưa có endpoint hoặc authorization service trong phạm vi này. Command: `mvn -B -ntp -s target/user-repository-verification/settings.xml -Dmaven.repo.local=target/user-mapper-verification/repository -Dtest=UserTest,UserMapperTest test`, Maven 3.9.16: **BUILD SUCCESS, 6 tests, 0 failures/errors/skipped** (3 UserTest, 3 UserMapperTest). Test kiểm tra giữ nguyên identity/status/role, JSON đúng sáu trường với UTC timestamp và không có credentials, null bị từ chối. Log: `backend/aidbridge-backend/target/user-mapper-test.log`; reports: `target/surefire-reports/` (local/ignored). Không chạy lại repository tests trong lượt chỉ thêm DTO/mapper này; chưa có cross-owner review.

- **UserService 04/11/2026:** theo yêu cầu user, hoàn thiện `user/service/UserService.java`: constructor injection, `@Service`, `@Transactional(readOnly = true)`, `getUserById(UUID)` và `getUserByEmail(String)` trả `UserResponse` qua mapper. Email được strip/lowercase bằng `Locale.ROOT`; null ID hoặc email rỗng bị từ chối trước repository. Không tìm thấy ném `user/exception/UserNotFoundException` với thông báo chung không chứa PII. Đây là helper nội bộ: caller phải authorization trước khi trả dữ liệu; chưa có endpoint/error handler HTTP, không xác thực mật khẩu hoặc membership. Đọc user DISABLED vẫn trả đúng status; auth phải từ chối login tương ứng khi được triển khai.
- **UserService verification 04/11/2026:** `mvn -B -ntp -s target/user-repository-verification/settings.xml -Dmaven.repo.local=target/user-service-verification/repository -Dtest=UserTest,UserMapperTest,UserServiceTest test` với Maven 3.9.16: **BUILD SUCCESS, 15 tests, 0 failures/errors/skipped**, gồm 9 service tests mới và 6 entity/mapper tests. Repository được mock trong service tests; không chạy lại PostgreSQL/full application trong lượt này. Log: `backend/aidbridge-backend/target/user-service-test.log`; reports: `target/surefire-reports/` (local/ignored). Cross-owner review, actual effort của Dũng và integrated completion B03 chưa có.

- **SecurityConfig 04/11/2026:** hoàn thiện config đang viết dở; thêm PasswordEncoder BCrypt(12), DAO AuthenticationManager dùng CustomUserDetailsService hiện tại, HttpSession CSRF/context repositories, composite session strategy (change session ID + clear CSRF) cho controller login tương lai. Filter chain dùng IF_REQUIRED/explicit context save, request cache tắt, không form login/HTTP Basic, enable method security; chỉ permit đúng public GET routes và POST login, còn lại authenticated. Public campaign draft/ownership checks vẫn phải ở service; platform role không thay org membership checks. JSON security errors:401 UNAUTHENTICATED,403 CSRF_INVALID/FORBIDDEN; logout POST yêu cầu CSRF + session, success204 và hủy session/JSESSIONID. Session properties: timeout30m, cookie-only/HttpOnly/SameSite=Lax/Secure mặc định; SESSION_COOKIE_SECURE=false chỉ cho local HTTP; health details không public.
- **SecurityConfig verification 04/11/2026:** `mvn -B -ntp -s target/user-repository-verification/settings.xml -Dmaven.repo.local=target/security-verification/repository -Dtest=SecurityConfigTest,UserTest,UserMapperTest,UserServiceTest test`, Maven 3.9.16: **BUILD SUCCESS, 36 tests, 0 failures/errors/skipped**, gồm21 security tests (MockMvc/real filter chain + DAO provider, repository mock) và15 entity/mapper/service tests. Kiểm tra public/private HTTP methods, JSON401/403 không redirect, không lưu anonymous request session, login/logout/write CSRF missing/invalid, platform role rejection, basic/CORS không mở truy cập, mật khẩu sai/unknown/disabled, credential clearing, session strategy rotate ID + clear old CSRF + save context, logout invalidates session và cookie. Lượt đầu có2 test fixture assumptions sai (Spring7 thêm FACTOR_PASSWORD; Mock request thiếu valid requested session ID); đã sửa fixtures, lượt cuối36/36 pass. Log: `backend/aidbridge-backend/target/security-test.log`; reports: `target/surefire-reports/` (ignored/local).
- **SecurityConfig scope / remaining:** production AuthController/AuthService vẫn rỗng; csrf/login probes trong test chỉ kiểm tra config. Chưa có HTTP login/MeResponse/membership isolation/revocation, PostgreSQL auth integration, cookie flags qua servlet/browser/proxy hoặc cross-owner review. Không coi bean/session strategy tests là đã hoàn tất endpoint login hay B03. Giờ làm thực tế của Dũng và completion date B03 chưa ghi nhận.

- **Auth decision04/11/2026 (D39):** user xác nhận giữ session cookie sau khi cân nhắc JWT. AuthResponse dùng DTO tài khoản, không JWT fields; tiếp tục explicit context save, session ID rotation, CSRF và logout invalidation theo config hiện có. Lượt này chỉ ghi nhận lựa chọn; không sửa Java hoặc chạy thêm tests, B03 vẫn In Progress. Bước tiếp theo là hoàn thiện/kiểm thử production csrf/login/me và membership guards trong approved scope.

- **AuthApiIntegrationTest07/11/2026:** user yêu cầu tạo trực tiếp test cho B03. Thêm `src/test/java/com/aidbridge/auth/AuthApiIntegrationTest.java`: SpringBootTest RANDOM_PORT + JDK HttpClient, production csrf/login/me/logout, PostgreSQL/Flyway V1–V5 trong schema `auth_it_<random>` riêng. Chỉ chấp nhận localhost/127.0.0.1:5432/aidbridge_test và role aidbridge_app; datasource/Flyway đều override vào schema test, không clean/drop hoặc dùng database development. Fixture gồm user,hai org và membership/role giả; mỗi method tạo IDs riêng, không dựa vào rollback của HTTP thread. Bao phủ session persistence/rotation, old-cookie rejection, CSRF renewal/rejection, logout invalidation, disabled user, live membership/org/role revocation, platform-admin cross-org rejection, registration không mở và health không lộ DB details. HTTP dùng endpoint thực; guard theo tổ chức được gọi trực tiếp qua OrganizationService vì chưa có organization HTTP operation để kiểm tra, không thêm probe endpoint.
- **Verification07/11/2026:** Maven3.9.16: `mvn -o -B -ntp -s target/user-repository-verification/settings.xml -Dmaven.repo.local=target/security-verification/repository -Dtest=SecurityConfigTest,AuthApiIntegrationTest test`: BUILD SUCCESS;21 SecurityConfig tests pass,14 integration test methods SKIPPED vì terminal không có ba AIDBRIDGE_TEST_DATABASE_* variables. Main/test sources compile target21; không ghi nhận PostgreSQL/real-HTTP integration Pass. Đánh dấu ProbeController cũ bằng TestComponent để tránh bị component scan vào full-app test; slice tests vẫn21/21 pass. Reports local/ignored: `target/surefire-reports/TEST-com.aidbridge.auth.AuthApiIntegrationTest.xml` và `TEST-com.aidbridge.auth.security.SecurityConfigTest.xml`. Sandbox write lần đầu bị từ chối; rerun với quyền ghi target thành công. B03 vẫn In Progress; chạy JUnit với test connection, review contract/error gaps và cross-owner acceptance còn pending; actual human effort chưa ghi nhận.
- **Contract alignment07/11 (D40):** user xác nhận giữ login AuthResponse(user), /me MeResponse; API/B01 baseline đã đồng bộ. GlobalExceptionHandler chuẩn hóa ProblemDetail đủ type/title/status/detail/instance/code/traceId, no-store; validation trả fieldErrors array `{field,code}` không rejected values. Framework errors dùng safe messages, không parser/exception details. Thêm11 MockMvc tests qua production HTTP/filter/controller/service với repository mocks; không thêm endpoint test vào application. AuthApiIntegrationTest bổ sung hai request-error cases và kiểm tra đủ envelope.
- **Evidence mới07/11:** user báo16/16 invocations của AuthApiIntegrationTest bản trước pass qua IntelliJ; agent không tự chạy được PostgreSQL integration để xác nhận báo cáo này. Lệnh từ backend module: `mvn -o -B -ntp -s target/user-repository-verification/settings.xml -Dmaven.repo.local=target/security-verification/repository -Dtest=GlobalExceptionHandlerTest,SecurityConfigTest,AuthApiIntegrationTest test`, Maven3.9.16, BUILD SUCCESS:11 GlobalExceptionHandlerTest +21 SecurityConfigTest pass;16 integration methods SKIPPED trước parameter expansion vì terminal thiếu AIDBRIDGE_TEST_DATABASE_URL/USERNAME/PASSWORD. Compile33 main sources và10 test sources target21. Reports local/ignored: `target/surefire-reports/TEST-com.aidbridge.exception.GlobalExceptionHandlerTest.xml`, `TEST-com.aidbridge.auth.security.SecurityConfigTest.xml`, `TEST-com.aidbridge.auth.AuthApiIntegrationTest.xml`. Không cài dependency hoặc chạy database migration trong lượt này.
- **Remaining07/11:** chạy lại AuthApiIntegrationTest bản mới bằng JUnit config có test connection, dự kiến18 invocations; HTTPS/proxy Secure-cookie verification, account/membership authorization cho các implemented operations và cross-owner review còn cần acceptance. Guard theo tổ chức hiện được test trực tiếp ở service vì chưa có organization HTTP operation; không tuyên bố đã kiểm tra business endpoints chưa triển khai. B03 vẫn In Progress; B01 vẫn In Review; actual human effort và completion date chưa ghi nhận.
- **Latest dependency report07/11/2026:** sau bản test mới, user báo tất cả tests pass và đã xong phần kiểm tra/review; request B04 tiếp tục xác nhận B02/B03 completed. Ghi nhận USER_REPORTED_COMPLETE, không tự nhận đã kiểm chứng HTTPS/reviewer hoặc18 executions qua agent. Local surefire còn bản16 methods SKIPPED vì thiếu env;11 error-contract và21 security tests pass là evidence agent đã chạy trước đó. Không restart B03; chạy auth regression phù hợp sau thay đổi B04. B01 vẫn In Review, không tự suy ra nhóm đã review toàn bộ B01.

### B04 - Case/needs/consent/evidence APIs

- **Feature / priority:** P0-02 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** M / 4h.
- **Dependencies:** B03; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-07 through 2026-11-07; deadline 2026-11-07, Asia/Bangkok.
- **Description / expected behavior:** Case/needs/consent/evidence APIs; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend casefile package; case/evidence migrations; CaseApiIT; EvidenceAccessIT.
- **Acceptance criteria:** Atomic SUBMITTED case, relational need lines, consent; editable case versioning; private fixture authorization; old evidence immutable.
- **Required tests:** BR01-P/N, BR08-P/N, BR09-P/N; missing consent/stale/cross-org tests.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Progress,06/11/2026, entity prework được user yêu cầu: bổ sung 9 fields, JPA/Bean Validation annotations, enum HOUSEHOLD/INDIVIDUAL, getter/protected no-args constructor và unique mapping theo V2 vào `casefile/entity/Beneficiary.java`. Chưa có constructor nghiệp vụ, repository/API tạo beneficiary hoặc integrated case workflow. Giữ dependency B03 cho integrated acceptance; chưa ghi nhận actual human effort hoặc completion date.
- **Verification06/11/2026:** Maven3.9.16 từ backend root: `mvn -B -ntp -s target/user-repository-verification/settings.xml -Dmaven.repo.local=target/security-verification/repository -Dtest=BeneficiaryTest test`: BUILD SUCCESS,4 tests,0 failures/errors/skipped; compile toàn bộ main/test sources target Java21. Kiểm tra simulated household hợp lệ tại giới hạn50/150 ký tự, thiếu organization/creator/type, nhãn/mã trống và vượt độ dài. Report: `backend/aidbridge-backend/target/surefire-reports/TEST-com.aidbridge.casefile.entity.BeneficiaryTest.xml` (ignored/local). Lượt đầu bị sandbox repository write, lượt offline tiếp theo thiếu parent4.1.2; lượt cuối dùng file mirror đọc cache đã có và pass. Không kết nối PostgreSQL, chạy migration hoặc kiểm thử permission/concurrency/HTTP; mapping DB/cross-owner review còn pending.
- **B04 reconciliation07/11/2026:** user cho phép scoped B04 implementation/tests/docs, không B05/blockchain/frontend/P1. Đã đọc actual entities/repositories, V2/V5, API/workflow/security/test specs và dependency evidence. Existing constructors/repos đã có, cập nhật nhận định prework cũ phía trên. D41 trong DECISION_LOG đề xuất cụ thể API-canonical forward V6 và alternative SQL-canonical; chờ user quyết định cho unresolved status/priority/consent/private-public/evidence-model differences trước dependent implementation. Chưa sửa Java, viết/chạy migration hoặc chạy tests B04. Test connection env hiện không có trong terminal; không provision DB hoặc đọc IDE credentials. Estimate gốc4h giữ nguyên; actual human effort/overrun chưa có dữ liệu, không tự ghi giờ giả.
- **Decision/implementation07/11:** user đã chọn D41/A, supersede pending-decision note phía trên. Thêm V6 (không sửa V1–V5), @Version/current case fields và retained need history; consent/evidence/audit/revision/idempotency repositories; DTOs/mapper/CaseService, CaseController/EvidenceController cho C01–C05/E01–E03. Atomic create SUBMITTED + consent/two-or-more relational needs/fixtures/local audit/idempotency; scoped reads, FV creator editable guards, version locks, resubmit sau revision mới hơn latest NEEDS_INFO review fixture, immutable evidence versions/snapshots và audited authorized content downloads. Server fixture allowlists theo org default deny, actual bytes/hash metadata; platform role không bypass private access. No outbox/RPC/B05/frontend/P1. Implementation/privacy/config/test details tại [B04 Case Workflow](../architecture/B04_CASE_WORKFLOW.md).
- **Tests actually executed07/11:** từ backend module bằng installed Maven3.9.16: `mvn -o -B -ntp -s target/user-repository-verification/settings.xml -Dmaven.repo.local=target/security-verification/repository -Dtest=CaseRequestValidationTest,EvidenceFixtureCatalogTest,GlobalExceptionHandlerTest,SecurityConfigTest,CaseApiIT,EvidenceAccessIT,B04MigrationIT,AuthApiIntegrationTest test`. BUILD SUCCESS;87 reported cases/methods,0 failures/errors,46 SKIPPED;41 executed tests pass =4 DTO validation +4 catalog/hash/config binding +12 global-error +21 security. Skips là CaseApiIT18 methods, EvidenceAccessIT8, B04MigrationIT4, AuthApiIntegrationTest16; parameterized invocations chưa expand. Compiled55 main/16 test sources target21 trên host JDK26.0.1. Lượt đầu compile lỗi LocalServerPort import đã sửa. Default sandbox không ghi được existing target/classes; rerun với quyền ghi target thành công, không cài dependencies.
- **Final focused regression07/11:** bổ sung xử lý CannotCreateTransactionException thành503; rerun cùng Maven options với `-Dtest=GlobalExceptionHandlerTest test`: BUILD SUCCESS,12/12 pass,0 skipped. Không double-count12 rerun thành tests mới. Reports local/ignored ở `backend/aidbridge-backend/target/surefire-reports/TEST-com.aidbridge.casefile.*.xml`, `TEST-com.aidbridge.auth.security.SecurityConfigTest.xml`, `TEST-com.aidbridge.exception.GlobalExceptionHandlerTest.xml`, `TEST-com.aidbridge.auth.AuthApiIntegrationTest.xml`. Không chạy PostgreSQL migration hoặc main application trong lượt B04.
- **Static verification07/11:** `git diff --check` cho affected tracked Java/tests/docs pass (chỉ có LF/CRLF notices). Bundled Python inline kiểm tra17 local links trong9 affected docs đều resolve và37 B04 source/test/new SQL/runbook files không trailing whitespace. Đây không thay PostgreSQL parser/schema validation hoặc HTTP acceptance.
- **Remaining acceptance07/11:** user chọn tự chạy IntelliJ với ba AIDBRIDGE_TEST_DATABASE_* variables. Cần CaseApiIT dự kiến20 invocations, EvidenceAccessIT8, B04MigrationIT5 (33 B04 total), rồi AuthApiIntegrationTest18 regression; verify không SKIPPED và gửi kết quả. Clean/upgrade/Hibernate startup, HTTP first checkpoint, concurrency/rollback/immutability/log privacy vẫn chưa có runtime evidence trong lượt này; không gọi test đã viết là Pass. B04 giữ In Progress, B01 In Review; cross-owner B04 review còn pending, không tự ghi reviewer/human effort/completion hoặc thay estimate4h. Nếu measured effort vượt4h phải record/reforecast thực tế, không giảm scope/tests. B05 chỉ nhận handoff state/version/snapshot/retained evidence sau acceptance, chưa bắt đầu.

### B05 - Independent case review

- **Feature / priority:** P0-03 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** B04, C01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-07 through 2026-11-08; deadline 2026-11-08, Asia/Bangkok.
- **Description / expected behavior:** Independent case review; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend verification package; snapshot/audit/job creation; VerificationApiIT.
- **Acceptance criteria:** Other CV approves; creator dual-role403; reject/request-changes/resubmit; immutable reviewed snapshot; DB/audit/job atomic.
- **Required tests:** BR02-P/N, BR03-N, BR09-P; IT01, IT08; rollback and competing review.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng bàn giao review/audit/job atomic và snapshot cho Khoa ở C05; phối hợp API đọc audit tối thiểu. Khoa nối Web3j/Fuji và xác minh receipt/event; không đợi B06–B10.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C. Chưa ghi nhận actual human effort.
- **Status / evidence:** In Review (Backend B05 code + PostgreSQL HTTP/outbox tests hoàn tất 09/11/2026; chờ cross-owner review của Khoa/Minh và tích hợp Fuji ở C05). V7 chuẩn hóa decision và truy vấn resubmit. V8 liên kết decision với revision đã review. `VerificationOutboxService` ghi audit và pending job trong transaction của caller; `VerificationController` / `VerificationService` / `LocalAuditService` hoàn tất cả 3 endpoints `POST /api/cases/{id}/verify`, `POST /api/cases/{id}/reject` và `POST /api/cases/{id}/request-changes`. Giữ nguyên baseline estimate 4h và deadline gốc 08/11/2026 (hoàn tất thực tế ngày 09/11/2026, trễ 1 ngày lịch so với baseline; giờ làm thực tế của Dũng chưa được cung cấp). ABI, commitment vector và việc chuẩn bị giao dịch thuộc C01/C05; trạng thái SQL `VERIFIED` tách biệt với `blockchain_jobs(PENDING)`.
- **Kiểm chứng D42.** Dũng báo cả bốn lớp `B04MigrationIT`, `CaseApiIT`, `EvidenceAccessIT` và `AuthApiIntegrationTest` pass trên IntelliJ sau V7. Đây là kết quả do Dũng cung cấp.
- **Kiểm chứng D43.** Maven offline `-Dtest=B04MigrationIT,CaseApiIT,CaseRequestValidationTest test` với cache `target/security-verification/repository` và settings `target/user-repository-verification/settings.xml` đã compile và pass 4 validation tests. Các lớp PostgreSQL bị skip trong terminal vì thiếu biến `AIDBRIDGE_TEST_DATABASE_*`. Sau đó Dũng báo `B04MigrationIT` pass trên IntelliJ sau V8.
- **Kiểm chứng D44.** Maven offline với cùng cache/settings và `-Dtest=VerificationOutboxIT,CaseRequestValidationTest test` đã compile và pass 4 validation tests. Bốn test `VerificationOutboxIT` bị skip trong terminal vì thiếu biến PostgreSQL. Dũng chạy lớp này trên `aidbridge_test` để xác nhận lưu audit/job, chống trùng, rollback và các guard.
- **Refactor D45 (08/11/2026).** Consent, evidence, local audit, outbox và idempotency chuyển sang JPA. `EvidenceService` và `VerificationOutboxService` điều phối các thao tác trong transaction của caller. Chạy Maven offline với cache/settings trên và `-Dtest=B04MigrationIT,CaseApiIT,EvidenceAccessIT,VerificationOutboxIT,CaseRequestValidationTest,EvidenceFixtureCatalogTest test`. Build thành công, 8 unit tests pass, 36 test methods tích hợp bị skip vì terminal thiếu cấu hình `aidbridge_test`. Kiểm tra offline khởi tạo Hibernate cho 15 entity và phân tích 7 JPQL thành công. Bước kế tiếp là Dũng chạy các lớp integration để xác nhận PostgreSQL, JSONB, retry đồng thời và rollback.
- **D45 chuẩn hóa Spring Data (08/11/2026).** Consent, evidence, audit, outbox và idempotency dùng `JpaRepository` cho thao tác trên entity. Native query của khóa PostgreSQL nằm trong `IdempotencyLock`. Chạy lại cùng lệnh Maven offline, 8 unit tests pass và 36 test methods tích hợp bị skip vì thiếu cấu hình `aidbridge_test`. Kiểm tra offline khởi tạo thành công 15 repository của Spring Data. Cần chạy các integration tests trên PostgreSQL để kiểm chứng hành vi.
- **Unit 2 (09/11/2026).** Thêm `VerificationApiIT` cho `POST /api/cases/{id}/verify`. Sáu test bao phủ duyệt đúng revision và replay, tự duyệt và truy cập khác tổ chức, version/bằng chứng sai, thu hồi membership trước replay, hai reviewer cạnh tranh và rollback sau khi ghi outbox. Chạy Maven offline với settings/cache ở trên và `-Dtest=VerificationApiIT,VerificationOutboxIT,CaseRequestValidationTest test`. Main/test compile thành công, 4 validation tests pass. Cả 6 API tests và 4 outbox tests bị skip trong terminal vì thiếu `AIDBRIDGE_TEST_DATABASE_*`. Sau đó Dũng báo đã chạy cả `VerificationApiIT` (6 tests) và `VerificationOutboxIT` (4 tests) trên PostgreSQL `aidbridge_test` trong IntelliJ: **10/10 pass**. Đồng thời đã bổ sung gói mẫu `CASE_VERIFIED Design Fixture & ABI Review Bundle` vào `BLOCKCHAIN_ARCHITECTURE.md` và chốt `D48` (Phương án A) trong `DECISION_LOG.md` để bàn giao cho Khoa (`C01`/`C03`/`C05`). B05 vẫn In Progress (còn phần `reject` / `request-changes`).
- **Unit 3 & Unit 4 (09/11/2026).** Hoàn thiện `POST /api/cases/{id}/reject` và `POST /api/cases/{id}/request-changes` trong `VerificationController`, `VerificationService`, `AidCase`, `Verification` và `LocalAuditService.appendVerification`. Ghi quyết định bất biến (`REJECTED` / `CHANGES_REQUESTED`), snapshot bằng chứng và local audit (`CASE_REJECTED` / `CASE_CHANGES_REQUESTED`, không tạo `blockchain_jobs`, trả `blockchainStatus = "NOT_APPLICABLE"`), tương thích trọn vòng đời `request-changes -> creator revise -> resubmit -> independent verify`. Bổ sung kiểm tra `409 IDEMPOTENCY_CONFLICT` cho `/verify`, dọn `Mockito.reset(outboxSpy)` trong `finally`, thêm 1 unit test (`CaseRequestValidationTest`) và 4 integration tests (`VerificationApiIT`, nâng tổng lên 10 HTTP tests). Chạy Maven offline: `mvn -o -B -ntp -s target/user-repository-verification/settings.xml "-Dmaven.repo.local=target/security-verification/repository" "-Dtest=VerificationApiIT,VerificationOutboxIT,CaseRequestValidationTest,EvidenceFixtureCatalogTest,GlobalExceptionHandlerTest,SecurityConfigTest" test` -> `BUILD SUCCESS`, 42 executed unit/slice tests pass (`21 SecurityConfigTest + 12 GlobalExceptionHandlerTest + 5 CaseRequestValidationTest + 4 EvidenceFixtureCatalogTest`), 14 DB tests skipped trong terminal. Sau đó Dũng chạy `VerificationApiIT` trên PostgreSQL `aidbridge_test` trong IntelliJ: **10/10 pass** (cùng `VerificationOutboxIT` **4/4 pass**). Cập nhật đồng bộ `API_SPECIFICATION.md`, `BUSINESS_WORKFLOWS.md`, `BLOCKCHAIN_ARCHITECTURE.md`, `B04_CASE_WORKFLOW.md`, `TEST_STRATEGY.md` và bàn giao sang C05.

### B06 - Multi-item campaign draft/publish

- **Feature / priority:** P0-04 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** M / 6h.
- **Dependencies:** B05; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-08 through 2026-11-10; deadline 2026-11-10, Asia/Bangkok.
- **Description / expected behavior:** Multi-item campaign draft/publish; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend campaign package; campaign/item migrations; CampaignApiIT.
- **Acceptance criteria:** Copy every approved need line and exact unit/qty; one campaign/case; unverified publish blocked; sanitized public DTO.
- **Required tests:** BR03-P/N, BR08-P/N; stale review/draft privacy/duplicate campaign.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (11/11/2026). Forward `V10__reconcile_campaign_schema.sql` + `campaign` package (P01–P04, local audit `CAMPAIGN_CREATED`/`CAMPAIGN_PUBLISHED`, không tạo blockchain job). Dũng chạy trên `aidbridge_test` (IntelliJ): `CampaignApiIT` 15/15 pass, `CampaignRequestValidationTest` 4/4 pass. Test phát hiện và đã sửa 2 lỗi trong `CampaignService`: (1) điều kiện review hiện hành phải là `reviewedCaseVersion + 1 == case.version` vì approve tự tăng version; (2) publish so sánh item theo thứ tự không ổn định gây `409` ngẫu nhiên, đã đổi sang so sánh tập hợp và thêm test hồi quy 5 need line. Trễ 1 ngày so với deadline 10/11; actual human effort chưa được cung cấp. Còn chờ: cross-owner review (Khoa payload/chain boundary, Minh API/UI), integration evidence với F06, quyết định D50 về thứ tự `items[]`.

### B07 - Commitments/cancel/idempotency

- **Feature / priority:** P0-04 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** L / 7h.
- **Dependencies:** B06; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-10 through 2026-11-13; deadline 2026-11-13, Asia/Bangkok.
- **Description / expected behavior:** Commitments/cancel/idempotency; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend contribution package; idempotency journal; commitment migrations/tests.
- **Acceptance criteria:** Atomic multi-line pledge, donor history, cancel only unreceived remainder, per-item row locks and event IDs; concurrent no overflow.
- **Required tests:** BR04-P/N, BR11-P/N; IT10; PostgreSQL last-unit/cancellation races.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng giữ quantity/idempotency và event ID nghiệp vụ; Khoa dùng đầu ra này trong C07/C06, không làm lại business validation trong adapter. Hai bạn kiểm tra retry không tạo thêm pledge/event.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** In Review (12/11/2026). Forward `V11__reconcile_contribution_schema.sql` (D52 donor grant, schema contribution, composite FK) và `V12__link_blockchain_jobs_to_case.sql` (D53); package `contribution` gồm G01 `POST /api/campaigns/{id}/commitments`, G02 `GET /api/me/commitments`, G03 `POST /api/commitments/{id}/cancellations`; outbox `AID_PLEDGED`/`COMMITMENT_REDUCED` + `blockchain_jobs(PENDING)` với `chain_sequence` liên tục theo case; `CampaignResponse.pledgedQty/remainingPledgeQty` lấy số thật. Dũng xác nhận pass trên `aidbridge_test`: `ContributionSchemaIT` (9), `ContributionPledgeIT` (9), `ContributionOutboxIT` (5), `ContributionCancelIT` (10), `ContributionConcurrencyIT` (4), `CommitmentRequestValidationTest` (5), `CancellationRequestValidationTest` (4) và hồi quy toàn bộ `-Dtest=*Test,*Tests,*IT`. Trong quá trình làm, test phát hiện và đã sửa lỗi `pledgedAt` lệch 1 µs giữa G01 và G02 do driver làm tròn. Actual human effort chưa được cung cấp. Còn chờ: Khoa review D53 (constructor `BlockchainJob`, worker lọc `event_type`), Minh review shape G01–G03 cho F07 và integration evidence.

### B08 - Partial goods receipts

- **Feature / priority:** P0-05 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** M / 5h.
- **Dependencies:** B07; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-13 through 2026-11-14; deadline 2026-11-14, Asia/Bangkok.
- **Description / expected behavior:** Partial goods receipts; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Output:** R01 trong `ContributionController`, `GoodsReceiptService` và các receipt DTO/entity/repository, staff read projection, migration V14, `ReceiptApiIT`, `ReceiptSchemaIT` và `ReceiptRequestValidationTest`.
- **Acceptance criteria:** Immutable receipt per contribution line; received<=active pledge; repeat once; same org/line; not delivered.
- **Required tests:** BR05-P/N, BR11-P/N; IT05, IT10; receipt/cancel concurrency.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng giữ receipt quantities/immutable records; bàn giao snapshot nhận hàng cho Khoa map C10. Minh hiển thị received riêng pledged/delivered.
- **Ghi công hỗ trợ:** Chưa ghi nhận số giờ thực tế của Dũng, Khoa hoặc Minh.
- **Status / evidence (13/11/2026):** Testing. Dũng duyệt triển khai. V14 giữ receipt lịch sử, ràng buộc sở hữu và bảo vệ bất biến. R01 nối transaction, replay và outbox `GOODS_RECEIVED`. Staff đọc `GET /api/org/cases/{id}` với `outstandingCommitments`. P03/P04 và G02 đọc tổng thực nhận. [B08 R01 handoff](../reviews/B08_R01_HANDOFF.md) có ví dụ, mã lỗi, luồng refresh và contract C10 cần review.
- **Kiểm chứng 13/11 (Maven):** `mvn -o -B -ntp "-Dtest=ReceiptRequestValidationTest,ReceiptApiIT,ReceiptSchemaIT,ContributionCancelIT,ContributionConcurrencyIT,ContributionSchemaIT,CampaignApiIT" test` build thành công, 5 validation tests pass, 45 test entries PostgreSQL skip vì thiếu `AIDBRIDGE_TEST_DATABASE_*`. Hibernate repository bootstrap và shape staff JSON pass offline. `node docs/fixtures/f02/verify-f02.mjs` pass 5 snapshots và 10 mock scenarios.
- **Kiểm chứng 13/11 (IntelliJ):** Dũng xác nhận toàn bộ test đã chạy đều pass sau khi chạy bộ B08 với `aidbridge_test`, gồm `ReceiptApiIT` và `ReceiptSchemaIT`. Nguồn kết quả là xác nhận của Dũng trong chat, chưa có báo cáo IntelliJ để đối chiếu số test hoặc các lớp hồi quy đã chạy. Kết quả skip của lượt Maven ghi ở trên vẫn là lịch sử của lượt đó.
- **Còn lại trước khi Done:** Khoa review payload/refs/sequence `GOODS_RECEIVED`. Minh xác nhận F08 gọi R01 và refresh staff view với backend thật. F08 browser E2E và Fuji receipt sync chưa có kết quả cho R01. Fuji receipt sync thuộc C10.

### B09 - Delivery records/failed attempts

- **Feature / priority:** P0-05 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** B08; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-14 through 2026-11-15; deadline 2026-11-15, Asia/Bangkok.
- **Description / expected behavior:** Delivery records/failed attempts; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend delivery package; delivery migrations; DeliveryConcurrencyIT.
- **Acceptance criteria:** Reserve all nonvoid recorded delivery against received, even unconfirmed; FAILED zero quantity; immutable evidence snapshot; safe retry.
- **Required tests:** BR05-P/N, BR09-P/N, BR11-P/N; IT11, IT16.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng giữ stock reservation/failed-attempt/evidence snapshot; Khoa map delivery milestone và causal refs ở C10; Minh dùng đúng outcome/review status.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B10 - Confirm/dispute/resolve/close

- **Feature / priority:** P0-05 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** B09; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-15 through 2026-11-17; deadline 2026-11-18, Asia/Bangkok.
- **Description / expected behavior:** Confirm/dispute/resolve/close; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend delivery confirmation/dispute services; campaign close; migrations; DisputeIT.
- **Acceptance criteria:** Separate DC; self403; dispute blocks/reopens close; all item targets confirmed; void preserves old rows; PA cannot auto-confirm.
- **Required tests:** BR06-P/N, BR07-P/N; IT06, IT07, IT12; concurrent flag+close.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng giữ self-confirm/dispute/close guards; Khoa kiểm tra confirmation/dispute event và chain order; hai bạn kiểm tra void/replacement không xóa lịch sử.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B11 - Backend cross-feature regression

- **Feature / priority:** Quality / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** B03, B04, B05, B06, B07, B08, B09, B10; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-17 through 2026-11-20; deadline 2026-11-20, Asia/Bangkok.
- **Description / expected behavior:** Backend cross-feature regression; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend/src/test/java/com/aidbridge; Test Strategy actual results.
- **Acceptance criteria:** Real PostgreSQL tenant/state/quantity/idempotency/upgrade tests; no critical integrity/privacy/auth bug; feature tests already embedded earlier.
- **Required tests:** BR01-P/N..BR09-P/N as applicable; BR11-P/N; IT05-12/15/16.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng chịu API/PostgreSQL/permission/quantity assertions, phối hợp Khoa C08/C11 trên cùng failure fixture; Khoa chịu EVM/RPC/receipt/log assertions, Minh E2E.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### B12 - Critical fixes/final handover

- **Feature / priority:** Submission / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Dũng (B) / Khoa / Minh / Khoa kiểm tra payload/chain boundary; Minh kiểm tra API/UI.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** B11; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-21 through 2026-11-21; deadline 2026-11-22, Asia/Bangkok.
- **Description / expected behavior:** Critical fixes/final handover; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend affected fixes/tests; API/runbook/documentation updates.
- **Acceptance criteria:** Final smoke, actual gaps and evidence, no new features; regression only affected changes.
- **Required tests:** Final auth/quantity/dispute smoke; relevant regressions after fixes.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C01 - EVM/event/commitment agreement

- **Feature / priority:** Design / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** B01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-03 through 2026-11-04; deadline 2026-11-04, Asia/Bangkok.
- **Description / expected behavior:** EVM/event/commitment agreement; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Blockchain Architecture; event/manifest/ABI specification.
- **Acceptance criteria:** JIT learning, random refs, stable event IDs, salt/private manifest, sequence and custody boundary; B/F review.
- **Required tests:** BR08-P/N design, BR11/12 design; Java/TS golden-vector expectations.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng chốt schema/DTO/audit/job/privacy; Khoa chốt ABI/events/random refs/commitment/chain sequence. Minh xác nhận DTO/status UI. Review chung trước triển khai.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** In Review (09–10/11/2026). Dũng hoàn thành sample `CASE_VERIFIED` trong `BLOCKCHAIN_ARCHITECTURE.md` (commit `97bb1be`); Khoa hoàn thành bộ tài liệu review được giao theo `9_11.pdf`: [C01 handoff](../reviews/C01_BLOCKCHAIN_REVIEW.md), ABI draft, reviewed sample, golden-vector input và test expectations tại `docs/fixtures/c01/` (commit `2365378`). Ngày 10/11/2026, Dũng chạy `node docs/fixtures/c01/verify-c01.mjs --ethers` (`PASS` static + ethers ABI encode/decode candidate golden vector) và chốt toàn bộ `C01-O01..O05` tại `D49` trong `DECISION_LOG.md`. Actual effort chưa ghi nhận đủ tin cậy; không sửa estimate/lịch gốc. Ngày hoàn thành task: chưa có (chờ Minh cross-owner review và đối chiếu Java tại `C05`).

### C02 - Hardhat local setup

- **Feature / priority:** Foundation / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** C01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-04 through 2026-11-05; deadline 2026-11-05, Asia/Bangkok.
- **Description / expected behavior:** Hardhat local setup; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** contracts/package.json; package-lock; hardhat.config.ts; local harness.
- **Acceptance criteria:** After coding approval: pinned compatible toolchain/compiler/optimizer/EVM target, distinct test accounts, deterministic compile/test, no keys.
- **Required tests:** Local compile/test smoke; wrong chain/secret configuration checks.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (09/11/2026, Dũng hỗ trợ setup). Commit `84a042f` (PR `#3`): Hardhat `3.21.2`, Solidity `0.8.28`, `evmVersion: paris`, optimizer 200 runs, `ToolchainSmokeProbe.sol` + `test/ToolchainSmoke.test.ts` (3 tests). Chạy lại ngày 10/11/2026 cùng C03 Part 2 trong `contracts/`: `npm run compile` PASS ("No contracts to compile" — artifact hiện có đã khớp source); `npm test` 44 passing, trong đó `ToolchainSmoke.test.ts` 3/3. Not Run: cấu hình Fuji thật (`REQUIRE_FUJI_CONFIG=true`), thuộc C04. Actual effort chưa ghi nhận. Chờ Khoa review trước khi Done.

### C03 - Authorized verification contract slice

- **Feature / priority:** P0-03/P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** C01, C02; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-05 through 2026-11-07; deadline 2026-11-07, Asia/Bangkok.
- **Description / expected behavior:** Authorized verification contract slice; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files:** contracts/contracts/AidRegistry.sol; contracts/test/AidRegistry.test.ts; contracts/test/AidRegistry.negative.test.ts.
- **Acceptance criteria:** Org registration, scoped relayer, verification event/sequence, unique IDs; unknown/revoked/wrong-org callers rejected.
- **Required tests:** BR12-P/N, BR11-N; SC01-04/09-11.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng làm Part 1 (3h dự kiến): `AidRegistry.sol` và test happy-path. Khoa làm các phần còn lại của C03 và review contract.
- **Status / evidence:** In Review (10/11/2026, Part 1 + Part 2 do Dũng hỗ trợ; chờ Khoa review). `AidRegistry.sol` không đổi ở Part 2 (guard order D49 `C01-O05`). Test: `AidRegistry.test.ts` 5 tests (SC01, C01-T01, 3 tests C01-T13 privacy/ABI allowlist) và `AidRegistry.negative.test.ts` 36 tests (C01-T02..T12 + vòng đời relayer). Lệnh chạy trong `contracts/`: `npm run compile` PASS ("No contracts to compile" — artifact hiện có đã khớp source); `npm test` **44 passing** (ToolchainSmoke 3 + AidRegistry 5 + negative 36). Từ repo root: `node docs/fixtures/c01/verify-c01.mjs --ethers` PASS. ABI parity: 4/4 entry trong `docs/fixtures/c01/AidRegistry.abi.draft.json` (`recordCaseVerification`, `CaseVerified`, `getEventRecord`, `getCaseSummary`) khớp tên/type/indexed/stateMutability/outputs với `contracts/artifacts/contracts/AidRegistry.sol/AidRegistry.json`; artifact không có hàm payable, receive hoặc fallback. Lệch so với yêu cầu: `C01-T12` với `orgRef = 0` revert `OrganizationNotActive(0x0)` thay vì `InvalidZeroInput`, vì D49 kiểm tra organization trước zero-input; giữ D49, không sửa contract. Not Run: Khoa cross-owner review, đối chiếu golden vector Java/TS (C05), C06 milestone functions, Fuji (C04). Chưa deploy Fuji. Actual effort chưa ghi nhận. Estimate/deadline gốc giữ nguyên. Ngày hoàn thành: chưa có.

### C04 - Fuji pilot deployment

- **Feature / priority:** P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** M / 2h.
- **Dependencies:** C03; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-07 through 2026-11-07; deadline 2026-11-07, Asia/Bangkok.
- **Description / expected behavior:** Fuji pilot deployment; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** contracts/scripts/deploy.ts; public deployment manifest.
- **Acceptance criteria:** Actual43113 deployment receipt/code/address/block/compiler/ABI saved, org role readback; explorer source status accurate.
- **Required tests:** Wrong-chain preflight, authorization readback, actual deploy receipt.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (11/11/2026), actual Fuji C04 runtime PASS: registry 0x015D941c679b0c402c8262BE237adAD36cEf6726; deploy/registration receipts status=1, bytecode/admin/org/relayer readback PASS; 61 local tests và C01 --ethers PASS. ABI D49/schema1. Explorer source VERIFIED Exact Match (Snowtrace/Routescan, API + UI). Console Wallet NOT_DELETED vì inventory X/P/token chưa đầy đủ; tách khỏi runtime acceptance. Xem [C04 deployment](../reviews/C04_DEPLOYMENT.md). Giữ estimate/deadline, actual effort chưa có dữ liệu; chờ review nhóm, không tự Done.


### C05 - Java adapter/minimal audit slice

- **Feature / priority:** P0-03/P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** B05, C04; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-07 through 2026-11-08; deadline 2026-11-09, Asia/Bangkok.
- **Description / expected behavior:** Java adapter/minimal audit slice; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend blockchain adapter/worker; minimal audit controller; AdapterContractIT.
- **Acceptance criteria:** Persistent attempt before broadcast, no RPC wait in SQL/HTTP; exact verification receipt/event; pending/failed/confirmed API.
- **Required tests:** BR10-P/N, BR12-P; IT01/03/13; ABI parity and actual Fuji smoke.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng làm phần backend verify+SQL audit/job và hỗ trợ trực tiếp DTO/repository/REST/Java glue; Khoa làm Web3j/ABI/signing/Fuji/exact receipt-event verification. Ghép ngay sau B03–B05, không chờ backend hoàn tất.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence (11/11/2026):** Blocked cho nghiệm thu full C05; kết quả ngày 11/11 **PARTIAL**, proof contract Fuji độc lập PASS. [C05 receipt review](../reviews/C05_FUJI_RECEIPT_REVIEW.md) và [proof JSON](../fixtures/c05/fuji-case-verified.proof.json): tx `0xc3b02934afb1f29816836fb22146dbc350202d3b2a9e8dbbb19c2fd2e7e8c00f`, block60893112/status1, đúng chain43113/registry/Relayer/CaseVerified envelope, calldata/canonical block/getters khớp. Compile và61 Hardhat tests PASS;46 verifier tests PASS; C01 --ethers và C04 readback PASS. Không sửa contract/backend hoặc ghi SQL CONFIRMED. `backendJobId=null`: chưa xác minh số job B05 thật vì không có DB env; worker/API projection chưa có trong checkout, organization binding chưa xác nhận. Dũng xác nhận DB/job/refs và phối hợp Java/DB/REST; Khoa tiếp tục adapter/receipt integration khi đầu vào sẵn sàng, Minh giữ trạng thái UI theo API thật. Chưa Java parity/DB crash/concurrency/HTTP E2E, chưa full acceptance/cross-owner review hoặc Done. Estimate/deadline gốc giữ nguyên; actual human effort chưa được cung cấp. Commit local được user cho phép, không push/PR/F07.
- **Status / evidence (13/11/2026, Dũng chạy):** In Review. Job B05 thật trên `aidbridge_demo` (Flyway V13, một instance, signer D54 `0x95a9564899849CF566DE5713205C4B747F361f55`, registry `0x015D941c679b0c402c8262BE237adAD36cEf6726`): tạo hồ sơ (VOLUNTEER) → verify độc lập (VERIFIER) → job `CASE_VERIFIED` PENDING → worker → CONFIRMED, 1 attempt, nonce 0, tx `0x465140011495f85743088ada113097204a891e41bc0b985b15e653366cb8566a`, block 60959263. Kiểm chứng độc lập `contracts/scripts/c05-proof.mjs` (`collectProof`, runtime C03): PASS 17 checks; orgRef/caseRef/eventId/sequence 1/commitment/schema 1, `getEventRecord`/`payloadDigest` và `getCaseSummary` (verified, lastChainSequence 1) khớp DB. Not Run: API audit-trail T01/T02, worker cho mốc C06, Java decode event mới (C10). Chờ Khoa nghiệm thu.

### C06 - Full milestone contract

- **Feature / priority:** P0-04/P0-05/P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** C03; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-09 through 2026-11-11; deadline 2026-11-11, Asia/Bangkok.
- **Description / expected behavior:** Full milestone contract; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** AidRegistry milestone functions; contract tests; exported ABI.
- **Acceptance criteria:** Pledge/reduction/receipt/delivery/confirm/dispute/resolve; same org/case refs, no duplicate/absent/void/disputed confirm, sequence guards.
- **Required tests:** BR08-P/N, BR11-P/N, BR12-P/N; SC04-11.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Progress (11/11/2026, phần 1/2 do Dũng hỗ trợ; Khoa primary owner và reviewer). Thêm vào `AidRegistry.sol` (chỉ thêm; `git diff --numstat` 0 dòng xóa; code C03 giữ nguyên): `recordAidCommitment`, `recordCommitmentReduction`, `recordGoodsReceipt`, `recordDelivery`; event `AidCommitted`, `CommitmentReduced`, `GoodsReceived`, `DeliveryRecorded`; error `CaseNotVerified`, `CaseHasOpenDispute`, `RefAlreadyUsed`, `RefNotFound`, `RefOrganizationMismatch`, `RefCaseMismatch`. Guard order D49 `C01-O05`; `payloadDigest` gồm selector + envelope + ref riêng. Quyết định user 11/11: reduction không có ref riêng (phân biệt bằng `eventId`); reduction/receipt không bị chặn khi có dispute mở; `DeliveryStatus { None, Recorded }`, nối thêm `Confirmed`/`Voided` ngày 12/11. Test mới `contracts/test/AidRegistry.milestones.test.ts`: happy path, BR12-N, SC09, BR11-N/SC04, C01-T17, SC05 refs, SC10 input, ABI allowlist. Lệnh trong `contracts/`: `npm run compile` PASS; `npx hardhat test test/AidRegistry.milestones.test.ts` 86 passing, 1 pending; `npm test` 130 passing, 1 pending (44 test C03/C02 vẫn xanh). ABI xuất từ compiler artifact: `docs/fixtures/c06/AidRegistry.milestones.abi.json` (14 entry). Not Run: `CaseHasOpenDispute` (cần `flagDispute`); `confirmDelivery`, `flagDispute`, `resolveDispute` (12/11); Java/Web3j decode (C05); Fuji (C04); Khoa review. Actual effort chưa ghi nhận. Chưa Done.
- **Phần 2 (12/11/2026, Dũng hỗ trợ; Khoa review):** thêm `confirmDelivery`, `flagDispute`, `resolveDispute`; event `DeliveryConfirmed`, `DisputeFlagged`, `DisputeResolved`, `DeliveryVoided`; 5 error mới; `DeliveryStatus` nối thêm `Confirmed`, `Voided` (`git diff --numstat` 441/1, dòng duy nhất đổi là enum). Test mới `contracts/test/AidRegistry.disputes.test.ts` (SC06–SC11, BR11-N, BR12-N/P). `npm run compile` OK; `npm test` 194 passing, 1 pending (it.skip SC07 cũ trong milestones test, SC07 đầy đủ nằm ở file mới); bytecode deployed 11.693 byte. ABI: `docs/fixtures/c06/AidRegistry.c06.abi.json`. Quyết định: D55. Not Run: decode Java/Web3j hai log VOID (C10), redeploy Fuji, Khoa review phần 1 và 2. Chưa Done.

### C07 - Durable signing/submission worker

- **Feature / priority:** P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** L / 7h.
- **Dependencies:** C05; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-11 through 2026-11-14; deadline 2026-11-14, Asia/Bangkok.
- **Description / expected behavior:** Durable signing/submission worker; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend submission job/attempt repositories; chain migrations; CrashRecoveryIT.
- **Acceptance criteria:** Lease+signer serialization+nonce journal; encrypted signed bytes/hash committed before send; restart same tx, one in-flight/signer.
- **Required tests:** BR10-N, BR11-P/N; IT01/02/04.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng làm hoặc hỗ trợ entity/repository/migration, SQL transaction/query job và kiểm tra persistence/idempotency; Khoa phụ trách signer/nonce/signed bytes/broadcast/worker chain lifecycle. Không RPC trong SQL transaction; không viết trùng repository.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence (12/11/2026):** In Review; technical gate PASS tại 903f952, CI compile + 22 unit + 18 integration tests PASS, 0 skip. Đã sửa receipt timeout, signer session serialization/fencing, one open attempt/signer/job (V13), nonce journal và recovery/binding/sequence guards. [C07 review](../reviews/C07_WORKER_SAFETY_REVIEW.md) chứa bằng chứng và điều kiện rollout. C05 Fuji end-to-end vẫn PARTIAL; chờ cross-owner acceptance, không Done. Actual effort chưa được cung cấp.
- **Status / evidence (13/11/2026, Dũng chạy trên Fuji):** Kịch bản restart 1 PASS: `BLOCKCHAIN_POLL_INTERVAL_MS=60000`, verify hồ sơ `e31ce4d6-9163-4e4f-aee1-a06698ee7ce2`, giết cứng backend khi job/attempt SUBMITTED (nonce 1, tx `0x97104a33730c05210da67a21c96b271876d744fcb61fbab46a5d87cff925f98e`), khởi động lại → CONFIRMED cùng tx hash, cùng nonce, 1 attempt; `c05-proof.mjs` PASS. Kịch bản 2 (dừng giữa PREPARED và broadcast): Not Run trên Fuji, chỉ có bằng chứng CI C07. Not Run: stress dài hạn. In Review, chờ Khoa nghiệm thu.

### C08 - Receipt/retry/log reconciliation

- **Feature / priority:** P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** L / 7h.
- **Dependencies:** C06, C07; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-14 through 2026-11-15; deadline 2026-11-15, Asia/Bangkok.
- **Description / expected behavior:** Receipt/retry/log reconciliation; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend receipt/log jobs; cursor/observation migrations; LogReplayIT.
- **Acceptance criteria:** Exact receipt/context, timeout remains pending, reverts failed; durable bounded cursor repairs lost DB update without duplicate event.
- **Required tests:** BR10-P/N, BR11-P/N; IT02-04/13; SC09.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng chuẩn bị DB fixtures/assertions và hỗ trợ Java polling/retry/DB update khi có giờ; Khoa quyết định receipt/log/context hợp lệ, nonce/attempt và reconciliation. Hai bạn cùng kiểm tra null/revert/mismatch/restart.
- **Ghi công hỗ trợ:** Dũng phụ trách kiểm thử Java/HTTP/PostgreSQL của Task 6, slot 2h theo lịch 13/11. Giờ làm thực tế chưa ghi nhận.
- **Status / evidence (14/11/2026):** In Progress. Chạy lại Task 6 trên snapshot `f225385` cùng bốn file Java C08 trong working tree. `CaseVerifiedWorkerIT`, `CaseVerifiedReceiptVerifierTest`, `Web3jBlockchainAdapterTest` và `VerificationApiIT` đạt 40/40, gồm 31 test trên PostgreSQL biệt lập với Flyway V1–V14. IT03 xác nhận attempt gốc sau timeout và rebroadcast khớp signed bytes trong journal. IT13 xác nhận các mã lỗi khi receipt sai context hoặc revert. IT04 khôi phục đúng một lần qua receipt polling sau SQL rollback. **Not Run:** log replay end-to-end do thiếu scanner/cursor/kho observation và kiểm tra API đọc projection live. Khoa review chain/context và chốt đường ingest, Dũng hỗ trợ SQL transaction và `LogReplayIT` khi có đường ingest. Chờ Khoa nghiệm thu trong Task 7. [Bằng chứng và bàn giao C08](../reviews/C08_DUNG_RECOVERY_2026_11_13.md)

### C09 - Full sanitized audit API

- **Feature / priority:** P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** C08; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-15 through 2026-11-16; deadline 2026-11-16, Asia/Bangkok.
- **Description / expected behavior:** Full sanitized audit API; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend audit projections/DTOs; AuditPrivacyIT.
- **Acceptance criteria:** Paginated internal/public timelines; business/evidence/chain axes; correct explorer network; no salt/evidence/identity leaks.
- **Required tests:** BR08-P/N, BR10-P/N; IT09/14; E2E05/06 support.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng làm hoặc hỗ trợ controller/query/DTO/pagination/ownership/public projection; Khoa cung cấp verified chain status/hash/block/event/explorer context. Mở rộng API slice có sẵn, không làm API song song.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C10 - Goods-to-chain integration

- **Feature / priority:** P0-05/P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** B08, B09, C08; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-16 through 2026-11-17; deadline 2026-11-17, Asia/Bangkok.
- **Description / expected behavior:** Goods-to-chain integration; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** backend receipt/delivery event mappers; integration tests.
- **Acceptance criteria:** Anchor exact immutable multi-item snapshots and causal refs, DB quantities not altered by chain replay; pair B on confirm/dispute wiring.
- **Required tests:** BR05-P, BR09-P, BR11-P/N; IT10-12/14.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng cung cấp approved immutable goods/delivery/confirmation/dispute snapshots từ B08–B10; Khoa map contract functions/events/refs/sequence; cả hai kiểm tra retry không tăng số lượng.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C11 - Contract/failure regression

- **Feature / priority:** Quality / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** C06, C07, C08, C09, C10; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-17 through 2026-11-20; deadline 2026-11-20, Asia/Bangkok.
- **Description / expected behavior:** Contract/failure regression; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** contracts/test; backend chain integration tests; actual test ledger.
- **Acceptance criteria:** All required SC/chain BR/IT pass; actual Fuji smoke, recovery/revoke tested, no fake tx success; auth/privacy errors release blocker.
- **Required tests:** BR08/10/11/12-P/N; SC01-11; IT01-04/12-14.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Dũng phối hợp DB/API assertions và chẩn đoán Java; Khoa chịu contract/RPC/receipt/log/Fuji regression; Minh E2E/public states. Cùng tái hiện một scenario để phân biệt business error và chain sync error.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### C12 - Deployment/key recovery runbook

- **Feature / priority:** Submission / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Khoa (C) / Dũng hỗ trợ Java/DB/REST; Minh hỗ trợ UI/TypeScript tests khi có capacity / Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra thông điệp và trạng thái UI.
- **Complexity / estimate:** M / 3h.
- **Dependencies:** C11; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-21 through 2026-11-21; deadline 2026-11-22, Asia/Bangkok.
- **Description / expected behavior:** Deployment/key recovery runbook; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Public deployment manifest; blockchain/key recovery documentation.
- **Acceptance criteria:** Actual address/ABI/block/tx evidence, dedicated key custodians/balance, no secrets; honest outage fallback.
- **Required tests:** Final actual Fuji receipt/readback; recovery/rotation review.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F01 - Interview recruitment/product assumptions

- **Feature / priority:** Validation / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** M / 2h.
- **Dependencies:** Planning/source review; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-03 through 2026-11-03; deadline 2026-11-03, Asia/Bangkok.
- **Description / expected behavior:** Interview recruitment/product assumptions; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Product Requirements validation notes; Risk Register.
- **Acceptance criteria:** Identify2-3 charity-experienced candidates; actual consented notes or explicit no-contact; no invented partner/impact, no agent unsolicited messages.
- **Required tests:** Workflow research review; R01/R02 status.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (10/11/2026). Khoa/Codex hoàn thành thiết kế F01 theo bảng phân công 10/11: [Product/Validation](../product/validation/F01_DEMO_AND_ASSUMPTIONS.md), checklist JSON và checker; full fictional lifecycle/multi-item/independent roles, 10 assumptions, explicit no-contact. Chưa có interview candidates/consented notes hoặc actual HTTP/demo/Fuji; không Done. Owner/estimate/lịch gốc giữ nguyên; actual effort chưa ghi nhận đủ tin cậy, completion date chưa có.

### F02 - Wireframes/mock API states

- **Feature / priority:** Design / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** M / 4h.
- **Dependencies:** B01; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-03 through 2026-11-05; deadline 2026-11-05, Asia/Bangkok.
- **Description / expected behavior:** Wireframes/mock API states; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Screen contracts; typed mock DTO fixtures planned.
- **Acceptance criteria:** B/C/F agree combined case/goods/audit screens, loading/empty/error/403/409 states, accessibility, no beneficiary wallet.
- **Required tests:** API design review; E2E07 specification.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (09/11/2026). Khoa/Codex bàn giao phần F02 được giao trong `9_11.pdf`: [three wireframes and handoff](../design/F02_WIREFRAMES_AND_STATES.md), shared mock/typed declarations/state map/checker tại `docs/fixtures/f02/`. Fixture arithmetic, ID/DTO-shape and 403/409 atomic-rejection checks PASS; backend HTTP/frontend render/keyboard E2E Not Run. Cần Minh review UI và Dũng review F02-A01..A05; chưa Done; Khoa cho phép commit/push/PR sau final QA ngày 10/11. Owner/estimate/date gốc giữ nguyên; actual effort chưa ghi nhận đủ tin cậy, ngày hoàn thành task chưa có.

### F03 - Next shell/session client

- **Feature / priority:** P0-01 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** M / 4h.
- **Dependencies:** F02, B03; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-05 through 2026-11-06; deadline 2026-11-06, Asia/Bangkok.
- **Description / expected behavior:** Next shell/session client; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Output files:** frontend package/config/lock; app/login; typed API/session client; tests và handoff 12/11.
- **Acceptance criteria:** After coding approval, same-origin proxy/CSRF/login/logout/nav; no browser secrets/localStorage auth; handle401/403.
- **Required tests:** Auth UI smoke, invalid login/logout, E2E06/07 basics.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** **In Review (12/11/2026)**. Nối tiếp Minh `79b78b3`, merge main `051e40f` bằng `dbeda4e`; real mode mặc định, same-origin `/api`, CSRF/session cookie và menu từ active `/me`. `npm test` F03/F04 chung **31 pass/0 fail**; `npm run typecheck` PASS; `npm run build` Webpack PASS ở mock và real mode. `npm run test:e2e -- --max-failures=1` **13 mock pass/0 fail, 1 real skipped** (Chrome, production mock server). `node scripts/smoke-real.mjs`: **Not Run** vì thiếu môi trường/accounts; user xác nhận backend/PostgreSQL chỉ ở máy Dũng, probe local8081 connection refused. Các lỗi môi trường ở lần chạy trước được ghi riêng trong [verification](../handoffs/VERIFICATION_2026-11-12.json), không tính là pass. Chờ Dũng review auth/API và real acceptance; actual human effort/ngày hoàn thành chưa có; chưa Done.

### F04 - Case and verifier UI

- **Feature / priority:** P0-02/P0-03 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** F03, B04, B05; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-07 through 2026-11-08; deadline 2026-11-08, Asia/Bangkok.
- **Description / expected behavior:** Case and verifier UI; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Output files:** frontend case list/new/detail/edit forms/review panels; shared client/mock; workflow unit và browser tests.
- **Acceptance criteria:** Case/consent/multi-need/evidence inputs, other-user review; self rejection/stale version handled; private error data absent.
- **Required tests:** BR02-N, BR09-P; E2E02/07; loading/empty/400/403/409.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** **In Review (12/11/2026)**. C01–C05 + V01–V03 UI/client theo DTO B04/B05 main `051e40f`: minimal summary, creator.fullName, fixtures.title, expectedVersion0, local chain NOT_APPLICABLE; thiếu chain/history ghi UNKNOWN/unavailable. Creator edit → resubmit → người khác review; 403 self-review;409 refresh; giữ payload/key qua unknown response và CSRF retry. `npm test` F03/F04 chung **31/31** (không cộng lặp với F03); typecheck/build mock+real PASS; Playwright **13 mock pass,0 fail,1 real skipped**. Regression F07 không sửa source: `node --import tsx --test features/donor/model.test.ts` **18/18**. Real smoke/DB/outbox/Fuji **Not Run**; không sửa backend. [Bàn giao và điểm cần Dũng review](../handoffs/MINH_F03_F04_2026-11-12.md); chờ real HTTP/cross-owner approval. F05 fixture đã merge qua PR #17; live audit chờ API. Actual human effort/ngày hoàn thành chưa có; chưa Done.

### F05 - Audit vertical slice UI

- **Feature / priority:** P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** F04, C05; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-08 through 2026-11-08; deadline 2026-11-09, Asia/Bangkok.
- **Description / expected behavior:** Audit vertical slice UI; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Output:** AuditTimeline và ba badge fixture tại `/audit-preview`; test trong `campaign-audit.spec.ts`. ExplorerLink cho live proof chờ T01/T02.
- **Acceptance criteria:** Hash alone not confirmed; exact backend proof shown; business verification distinct from pending/reverted/mismatch; actual Fuji link.
- **Required tests:** BR10-P/N; E2E05; honest record-integrity wording.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Minh ghép API/status UI; Dũng hỗ trợ DTO/auth/errors, Khoa xác nhận chain status/hash/link. Hash vừa gửi chưa được hiển thị confirmed.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** In Review (fixture, 12/11/2026). `npm run test:e2e -- --project=mock` 15/15 pass chung với F03/F04/F06. Ba badge tách business/evidence và chain; confirmed minh họa bằng proof C05 độc lập. PR #18 bổ sung bằng chứng hai CaseVerified trên Fuji và xác nhận SQL từ Dũng; F05 live vẫn chờ mapping DB/case và T01/T02. Chưa có số giờ thực tế hoặc ngày Done.
- **Hỗ trợ Dũng (12/11/2026):** [Ma trận audit](../reviews/F05_AUDIT_STATUS_EXPECTATIONS.md) mô tả 8 tình huống và các khoảng trống đọc chain. Proof C05 độc lập không phải job B05.

### F06 - Campaign multi-item UI

- **Feature / priority:** P0-04 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** M / 6h.
- **Dependencies:** F02, B06; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-09 through 2026-11-11; deadline 2026-11-11, Asia/Bangkok.
- **Description / expected behavior:** Campaign multi-item UI; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Output:** CampaignPanel trên case detail, GoodsProgressTable, P01/P02 trong API client chung, P03/P04 qua routes F07 có sẵn, fixtures và Playwright smoke.
- **Acceptance criteria:** Sanitized browse, fixed units and five progress columns per item, draft private, no PII; publish UI in case detail.
- **Required tests:** BR03-N, BR08-P/N; E2E06/07; loading/empty/retry.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (12/11/2026). Admin dùng case đã duyệt để tạo nháp nhiều mặt hàng, công bố và mở cùng campaign qua routes F07. P01–P04 dùng client same-origin, không phụ thuộc thứ tự item. `npm test` 57/57, typecheck và Playwright mock 15/15 pass. PR #17 đã merge; F06 real staff smoke còn chờ URL môi trường test và tài khoản ORG_ADMIN, chưa tạo campaign thật trong lượt Minh. Chi tiết ở [bàn giao F05/F06](../handoffs/MINH_F05_F06_2026-11-12.md). Chưa có số giờ thực tế hoặc ngày Done.

### F07 - Donor pledge/history/cancel

- **Feature / priority:** P0-04 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** M / 5h.
- **Dependencies:** F06, B07; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-11 through 2026-11-13; deadline 2026-11-13, Asia/Bangkok.
- **Description / expected behavior:** Donor pledge/history/cancel; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend contribution form/history/cancel; client methods.
- **Acceptance criteria:** Atomic multi-line form, same-key network retry, overflow refresh, own history, unreceived cancel; no provenance claim.
- **Required tests:** BR04-N, BR11-P/N; E2E03, donor privacy.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence (12/11/2026):** In Review; **F07 PASS trong phạm vi CI thật**: Next production → Spring Boot HTTP → PostgreSQL16. Run43200835548, SHA80e0b88:57 frontend unit +9 backend validation +5 browser E2E +1 Java host test PASS,0 skip/error/failure. [Bàn giao/JSON evidence](../reviews/F07_B07_API_INTEGRATION.md) có G01/G02/G03, session/CSRF, received409, commit504/exact replay, SQL audit/outbox và grant revocation. Received data là SQL fixture test, không phải R01/B08 E2E. PR13 OPEN, không merge; team review/dev provisioning còn riêng, không Done. Owner/estimate/lịch baseline giữ nguyên, actual human effort chưa có số liệu.

### F08 - Receipt/delivery UI

- **Khoa Task4 ngày14/11:** In Review, R01 API integration implemented tại `/cases/{id}/receipts`, shared session/CSRF/idempotency và staff projection/refetch; D01/F09 vẫn fixture độc lập. PASS WITH LIMITATIONS: frontend unit/HTTP-fixture browser đã kiểm chứng, Spring Boot/PostgreSQL E2E chưa xác minh vì backend local chưa chạy. Xem [F08 evidence](../reviews/F08_MOCK_GOODS_WORKSPACE.md). Không đổi owner/estimate hoặc nghiệm thu Done toàn F08.

- **Bổ sung Khoa ngày13/11 (Task5):** giữ In Review; hoàn thiện mock R01/D01 từ PR14, không viết lại phép tính đúng. Feedback 400/409 theo dòng hàng và kết quả mock có outcome/reason; tests tập trung partial receipt, delivery success/over-limit/FAILED và bất biến stock. Bằng chứng cập nhật tại [F08 mock workspace](../reviews/F08_MOCK_GOODS_WORKSPACE.md). Chưa B08/B09/DB thật, không thay owner/estimate/lịch baseline. [Kết luận Task4](../reviews/C06_PART2_D55_REVIEW.md) chỉ lưu review PASS đã thực hiện, không chạy lại C06.

- **Feature / priority:** P0-05 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** M / 5h.
- **Dependencies:** F04, B08, B09; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-13 through 2026-11-15; deadline 2026-11-15, Asia/Bangkok.
- **Description / expected behavior:** Receipt/delivery UI; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend case receipt/delivery/failed-attempt panels.
- **Acceptance criteria:** Partial OA receipt distinct from FV handover and review; stock limits and safe errors; FAILED zero effect; no PII in public.
- **Required tests:** BR05-P/N; IT10/16 UI; E2E01/07.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review — Khoa thực hiện khung mock R01/D01 ngày 12/11 theo phân công, giữ owner/estimate/lịch baseline. `/goods-demo` dùng F02 trong shell chung, role mô phỏng, partial receipt/delivery/FAILED và 403/409 không sai stock; không gọi B08/B09. 8 unit + 5 browser (desktop/mobile/keyboard), lint/typecheck và Turbopack build PASS; Webpack production build lỗi nội bộ WasmHash trên máy hiện tại, đã ghi rõ. [Bằng chứng và bàn giao F08](../reviews/F08_MOCK_GOODS_WORKSPACE.md). Chưa integrated acceptance/Done, còn B08/B09 và review Minh/Dũng; actual effort con người chưa đo.

### F09 - Confirmation/dispute/full timeline

- **Feature / priority:** P0-05/P0-06 / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** F08, B10, C09; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-15 through 2026-11-17; deadline 2026-11-18, Asia/Bangkok.
- **Description / expected behavior:** Confirmation/dispute/full timeline; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Output hiện tại:** D02/S01/S03 trong Goods demo của F08, dùng chung dữ liệu fixture và totals. P05 close và timeline đọc API còn chờ backend.
- **Acceptance criteria:** Separate reviewer, self blocked, dispute guard, all item target completion, public/internal audit separation; no false chain claim.
- **Required tests:** BR06-N, BR07-N, BR10-P/N; E2E04/05/06.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** In Review (fixture, 13/11/2026). F09 nối luồng D01 với xác nhận độc lập, mở/giải quyết tranh chấp và VOID_DELIVERY. Xem [bàn giao và kết quả kiểm thử](../handoffs/MINH_F09_2026-11-13.md). Live HTTP/SQL/Fuji của F09 chưa chạy.

### F10 - Public hosting/recovery

- **Feature / priority:** Deployment / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** L / 4h.
- **Dependencies:** F09, C10; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-16 through 2026-11-18; deadline 2026-11-18, Asia/Bangkok.
- **Description / expected behavior:** Public hosting/recovery; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** infra deployment config; health/smoke/budget notes.
- **Acceptance criteria:** Deploy only with authorization, provider eligibility/forecast under$20, HTTPS/CSRF/protected files/private DB; backup/rollback ready.
- **Required tests:** Deployment smoke, cookie/CSRF/CORS/file guards, cost/recovery checklist.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F11 - E2E/accessibility/privacy regression

- **Feature / priority:** Quality / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** L / 6h.
- **Dependencies:** F10, B11, C11; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-18 through 2026-11-20; deadline 2026-11-20, Asia/Bangkok.
- **Description / expected behavior:** E2E/accessibility/privacy regression; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** frontend/e2e; actual Test Strategy results; fixes.
- **Acceptance criteria:** E2E01-07 actual pass on integrated build, no critical auth/privacy/quantity/status issue, keyboard/error states usable.
- **Required tests:** BR08-N, BR10-N; E2E01-07 and relevant IT UI.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Phối hợp cụ thể:** Minh chạy E2E/accessibility/public privacy; Dũng xử lý API/DB failures, Khoa xử lý EVM/RPC/receipt failures; không gom mọi lỗi tích hợp cho một mình Khoa.
- **Ghi công hỗ trợ:** ghi phần việc/người/giờ trong task này, không tính cùng một giờ vào cả task B và C; chưa có giờ hoặc bằng chứng thực thi.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

### F12 - Demo/video/pitch/submission

- **Feature / priority:** Submission / P0.
- **Người chịu trách nhiệm / hỗ trợ / kiểm tra:** Minh (F) / Dũng / Khoa / Dũng kiểm tra auth/API; Khoa kiểm tra chain status.
- **Complexity / estimate:** L / 5h.
- **Dependencies:** F11; mock prework allowed, integrated acceptance waits.
- **Schedule:** allocated 2026-11-21 through 2026-11-22; deadline 2026-11-22, Asia/Bangkok.
- **Description / expected behavior:** Demo/video/pitch/submission; implement only interfaces and state/quantity rules in API Specification and Business Workflows.
- **Expected output files (future, not created):** Demo artifacts after authorization; README/checklist actual state.
- **Acceptance criteria:** Fictional household multi-item flow, actual Fuji evidence+negative test, no fake social results; organizer rules/links verified.
- **Required tests:** Full rehearsal, public+local fallback, final submission checklist.
- **Definition of Done:** behavior + allowed/forbidden paths + persisted DB/audit/job effects + validation/error UI + relevant passing tests + cross-owner review + integration evidence + affected docs updated. Blockchain tasks also actual verified Fuji proof where specified.
- **Status / evidence:** Not Started; actual effort, command/result/build/tx evidence and completion date not yet available.

## ML demo package (D51)

Không thuộc sáu P0 và không cộng vào 168h. Mục đích: một bản demo ML offline nộp kèm BTC, chạy ổn định trên dữ liệu giả lập, ghi rõ chỉ là bản demo. Quy tắc chung: thư mục `ml/` riêng (đề xuất), không import vào backend, không nối vào luồng duyệt/phân bổ, không ghi lên chain, không PII, mọi nơi hiển thị ghi DEMO. Tiêu chí "chạy ổn" xem [DAILY_ROADMAP](DAILY_ROADMAP.md#gói-demo-ml-d51--tính-riêng-ngoài-168h). Môi trường: scikit-learn chưa có trong môi trường cloud của Claude; chưa kiểm tra máy thành viên, ML-1 phải kiểm tra trước.

| ID | Phụ trách chính | Hỗ trợ / kiểm tra | Giờ | Thời gian | Dependencies | Acceptance | Status |
|---|---|---|---:|---|---|---|---|
| ML-1 | Dũng (B) | Minh kiểm tra schema và đặc trưng (chuyển từ Minh sang Dũng 12/11) | 3 | 12–13/11 | D51 | Đặc tả; bộ sinh seed cố định ~10.000 hồ sơ, 3 hồ sơ tổ chức giả lập, nhiễu nhãn và thiếu giá trị; luật cơ sở; test bộ sinh và schema | Not Started |
| ML-2 | Dũng (B) | Minh kiểm tra thước đo | 3 | 13–15/11 | ML-1 | Logistic Regression so với luật cơ sở; 5 seed x 5 fold, trung bình ± độ lệch chuẩn; ca sai; chỉ số theo nhóm; một lệnh chạy lại; smoke test pipeline | Not Started |
| ML-3 | Dũng (B) | Minh kiểm tra wording | 1 | 20/11 | ML-2 | Giới hạn trung thực; phần "để thành bản chính thức cần gì" (dữ liệu thật có đồng ý, gán nhãn, đánh giá độc lập, thời gian huấn luyện, người duyệt quyết định và khiếu nại, kiểm tra thiên lệch) | Not Started |
| ML-4 | Minh (F) | Khoa kiểm tra wording DEMO | 1,5 | 16–17/11 | ML-2 | Trang tĩnh đọc điểm tính sẵn, có biểu ngữ DEMO; không gọi backend; cắt nếu P0 trễ tại 17/11 | Not Started |
| ML-5 | Khoa (C) | Dũng kiểm tra BR-02 | 0,5 | 20/11 | ML-2, ML-4 | Không PII, không dữ liệu ML lên chain, ML không thể duyệt hay đổi trạng thái hồ sơ; có bước trong kịch bản demo | Not Started |

Status / evidence: tất cả Not Started; chưa có code, giờ thực tế hay kết quả kiểm thử.


## First ten immediate actions

1. Review scope/capacity and documentation vs coding authorization boundary.
2. Verify source PDF hash and19-document index; preserve original.
3. B01: validate ERD/RBAC, need/consent ownership and initial DTOs.
4. F01: identify2-3 interview candidates; record actual results or unvalidated assumptions.
5. C01: freeze event/commitment schema, golden vector and custody limits.
6. F02: agree wireframes and create/verify/audit mocks, errors/pending states.
7. Confirm compatible versions/env inventory and approved coding tasks before installs/scaffold.
8. B02/B03: backend/auth only after coding approval.
9. C02/C03/C04: local access tests before authorized actual Fuji pilot.
10. B04/B05+C05+F04/F05: one create->independent approval->Fuji receipt/event->UI slice.

## Progress log

| Task | Date | State | Actual hours | Evidence | Next action |
|---|---|---|---|---|---|
| All36 | 03/11/2026 | Not Started | Not recorded | Planning specification only | Await application task authorization |
| B01 (consolidation started) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | User yêu cầu thống nhất thiết kế; đọc SQL/Java/PDF và tìm drift | Chuẩn hóa docs và ghi implementation gaps |
| B01 (consolidated baseline) | 04/11/2026 | In Review | Chưa ghi nhận giờ làm thực tế của Dũng | B01_BASELINE + DB/API/workflows/system/events/decision docs đồng bộ; static checks, không runtime tests | Dũng/Minh/Khoa review quyền/DTO/schema/events; chưa có cross-owner sign-off |
| B02 (evidence reconciliation) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | Skeleton + V1 focused PostgreSQL test có evidence; V2–V5 rỗng, full startup/health chưa verified | Hoàn thiện B02 trong approved task sau B01 review; không đánh dấu Done |
| B02 (phần1 PostgreSQL preparation, historical) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | Setup scripts/isolated checks từng được tạo; đã gỡ theo yêu cầu D37 | Không chạy script cũ; xác nhận DB/account user quản lý |
| B02 (phần1 provisioning actual5433, historical) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | Instance5433 từng Ready/tested; đã stop và xóa data/credentials/scripts theo D37 | Không chuyển trạng thái Ready cũ sang database mới |
| B03 (User/entity + repository prework) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | User request cho hai class; 7/7 focused tests pass, PostgreSQL 18.6/V1; implementation có thể review | Dũng review hai class; tiếp tục auth/membership theo task được cho phép; chưa đạt integrated Done |
| B03 (UserResponse + UserMapper prework) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | User request cho DTO/mapper theo cấu trúc đã đề xuất; 6/6 focused unit tests pass (3 mới), BUILD SUCCESS | Dũng review DTO/mapping; tích hợp vào service/controller khi được yêu cầu; B03 chưa integrated Done |
| B03 (UserService prework) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | User cho phép UserService; 15/15 focused unit tests pass (9 service tests mới), BUILD SUCCESS | Dũng review service; tích hợp auth/authorization và HTTP error mapping khi được yêu cầu; B03 chưa integrated Done |
| B03 (SecurityConfig prework) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | User yêu cầu SecurityConfig; 36/36 focused tests pass (21 security tests mới), BUILD SUCCESS; session config + API docs cập nhật | Dũng review auth config; nối csrf/JSON login/me và active memberships trong bước được cho phép; B03 chưa integrated Done |
| B02 (remove dedicated PostgreSQL) | 04/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | User yêu cầu gỡ; path/PID verified, instance/data/credentials/scripts removed; installed PostgreSQL còn nguyên | Xác nhận DB/account/port user quản lý rồi tiếp tục B02 khi được yêu cầu |
| B04 (Beneficiary entity prework) | 06/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | User yêu cầu bổ sung class;9 fields/annotations theo V2, compile thành công,4 validation tests pass | Dũng review entity; constructor nghiệp vụ/repository/service và PostgreSQL integration trong scope tiếp theo; B03/integrated acceptance chưa Done |
| B03 (production auth integration tests added) | 07/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng | Main/test compile;21 existing security tests pass;14 integration methods skipped do thiếu test environment trong terminal | Chạy AuthApiIntegrationTest bằng cấu hình JUnit có test connection; đối chiếu login/error contract và review độc lập trước Done |
| B03 (global error contract và D40) | 07/11/2026 | In Progress | Chưa ghi nhận giờ làm thực tế của Dũng |11 new error-contract +21 security tests pass;16 integration methods skipped do thiếu env. User báo bản integration trước16/16 pass; chưa rerun hai cases mới | Rerun AuthApiIntegrationTest dự kiến18 invocations; HTTPS/proxy và cross-owner review; không bắt đầu B04/B05 |
| B04 (D41/A implementation) | 07/11/2026 | In Progress | Estimate gốc4h; actual human effort/overrun chưa được cung cấp | Forward V6 + C01–C05/E01–E03 code;41 unique executed tests pass,46 DB-dependent methods skipped; final12 error tests rerun pass | User chạy33 B04 PostgreSQL/HTTP invocations +18 auth regression trong IntelliJ; cross-owner review và B05 handoff pending |
| B05 (Units 1–4 implementation & handoff) | 09/11/2026 | In Review | Estimate gốc 4h (deadline gốc 08/11/2026); actual human effort chưa được cung cấp | V7/V8 + `/verify`, `/reject`, `/request-changes` + snapshot/audit/outbox; 42 unit/slice tests pass trong terminal; Dũng xác nhận `VerificationApiIT` (10/10) và `VerificationOutboxIT` (4/4) pass trên `aidbridge_test` trong IntelliJ; docs & D48 C05 handoff cập nhật | Khoa/Minh cross-owner review; Khoa nhận `blockchain_jobs(PENDING)` để triển khai C05 trên Fuji; sẵn sàng điều kiện phụ thuộc cho B06 khi được phê duyệt |
| C02 (Hardhat 3 local toolchain & smoke tests) | 09/11/2026 | In Review | Chưa ghi nhận giờ làm thực tế của Dũng | Commit `84a042f` (PR `#3` merged vào `main`): `contracts/` với Solidity `0.8.28`, `evmVersion: paris`, `ToolchainSmokeProbe.sol` & `ToolchainSmoke.test.ts` (`3 passing`) | Khoa review và dùng làm nền triển khai `C03` (`AidRegistry.sol`) |
| C01 & F02 (D49 cross-owner review & protocol freeze) | 10/11/2026 | In Review | Chưa ghi nhận giờ làm thực tế của Dũng | Dũng chạy `node docs/fixtures/c01/verify-c01.mjs --ethers` và `node docs/fixtures/f02/verify-f02.mjs` (`PASS`); chốt toàn bộ `C01-O01..O05` và `F02-A01..A05` tại `D49` | Bàn giao lại cho Khoa (`C03`/`C05`) và Minh (`F03`/`F04`/`F07`/`F08`) |
| C03 (AidRegistry slice, Part 1 + Part 2 negative/privacy tests) | 10/11/2026 | In Review | Chưa ghi nhận giờ làm thực tế của Dũng | `AidRegistry.sol` + 2 file test; `npm run compile` PASS ("No contracts to compile" — artifact hiện có đã khớp source); `npm test` 44 passing (3 + 5 + 36); `verify-c01.mjs --ethers` PASS; ABI draft 4/4 khớp artifact | Khoa review contract, guard order và C01-T12 deviation trước C04 Fuji pilot (11/11); C06 mở rộng milestone khi được giao |
| ML-1..ML-5 (D51 planning) | 11/11/2026 | Not Started | Chưa ghi nhận | User chọn bản demo ML offline trên dữ liệu giả lập quy mô rộng; chỉ cập nhật tài liệu (D51, AGENTS, MVP_SCOPE, DAILY_ROADMAP, TASK_BACKLOG); chưa có code hoặc test | Dũng bắt đầu ML-1 (kiểm tra Python/thư viện trên máy; chuyển từ Minh 12/11); Minh duyệt schema; theo dõi quy tắc cắt tại 17/11 |
| B06 (P01–P04 implementation & tests) | 11/11/2026 | In Review | Estimate gốc 6h (deadline gốc 10/11/2026); actual human effort chưa được cung cấp | V10 reconcile + `Campaign`/`CampaignItem` + `CampaignService`/`CampaignController`; sửa 2 lỗi do test phát hiện (off-by-one reviewed version, so sánh item phụ thuộc thứ tự); Dũng xác nhận `CampaignApiIT` 15/15 và `CampaignRequestValidationTest` 4/4 pass trên `aidbridge_test` | Khoa/Minh cross-owner review; bàn giao P01–P04 cho F06; chốt D50; B07 sẵn sàng khi được phê duyệt |
| B07 (G01–G03 implementation & tests, T1–T5) | 12/11/2026 | In Review | Estimate gốc 7h (deadline 13/11/2026); actual human effort chưa được cung cấp | V11/V12 + package `contribution` + outbox AID_PLEDGED/COMMITMENT_REDUCED + số liệu pledge công khai; 37 IT + 9 unit B07 và hồi quy toàn bộ được Dũng xác nhận pass trên `aidbridge_test`; sửa lỗi làm tròn `pledgedAt` | Khoa review D53 + worker lọc `event_type`; Minh nối F07 với G01–G03; B08 sẵn sàng khi được phê duyệt |

### C01 review handoff - 09/11/2026

C01: Not Started -> In Progress -> In Review trong phiên làm việc C01; không thay trạng thái các task khác. Bản ghi All36 ngày 03/11 ở trên là baseline lịch sử, không phải tiến độ hiện tại. Bộ handoff đã chuẩn bị; chưa có human/cross-owner approval, contract implementation hoặc Fuji proof. Xem `docs/reviews/C01_BLOCKCHAIN_REVIEW.md` để biết checks, OPEN owners và bước tiếp theo.

### F02 review handoff - 09/11/2026

F02: Not Started -> In Progress -> In Review trong phiên F02. Chỉ hoàn thành design/mock handoff G01/R01/D01 được giao cho Khoa; không claim frontend/API đã chạy hoặc full F02 acceptance đã được nhóm duyệt. C01 đã lưu ở commit `2365378`; C01 còn In Review. Xem `docs/design/F02_WIREFRAMES_AND_STATES.md` và các F02-Axx về response items/read projections chưa freeze. Không sửa task khác, không tự đổi owner hoặc lịch sử estimate.

Final QA 10/11/2026: C01 commit `2365378` giữ nguyên; F02 static checkers/negative cases và review contracts PASS, thêm idempotencyConflict UI state bị thiếu. Khoa chấp nhận handoff ở mức review và cho phép commit F02/push khoa/PR main, không merge. C01/F02 còn In Review chờ Dũng/Minh; application/contract/Java/TypeScript runtime checks Not Run. Chi tiết ở final quality review trong `docs/design/F02_WIREFRAMES_AND_STATES.md`.

### F01 - phạm vi theo phân công 10/11/2026

Not Started -> In Progress -> In Review trong phiên F01. Khoa được giao product scenario/assumptions 2h theo ảnh phân công 10/11; không thay primary owner F hoặc estimate/lịch baseline 03/11. Chưa hoàn thành research ngoài đời của acceptance cũ; ghi no-contact/no evidence và R01/R02 chưa xác thực. Kết quả chỉ tài liệu, chưa chạy feature; không thay status task Dũng/Minh/F07, chưa commit/push/PR. Xem Product/Validation để có checklist, nguồn/API, role-name mismatch và hướng dẫn tiếp nhận.

Nghiệm thu nội bộ F01 10/11: đạt đủ bộ tài liệu theo phân công ngày 10/11 (scenario multi-item, independent roles, checklist, assumptions, Product/Validation). Giữ In Review, cho phép commit local theo yêu cầu Khoa; chưa Done/cross-owner approved. Baseline tuyển 2-3 người chưa có evidence, dùng explicit no-contact và giữ research R01/R02 OPEN; không claim đã tuyển người. Role alias và donorEnabled=false hiện tại là điểm tích hợp cần phối hợp, không phải code đã sửa. Xem ma trận nghiệm thu cuối tài liệu F01; không push/PR/F07 trong lượt này.

### F07 - nghiệm thu nội bộ G01/G02 ngày 10/11/2026

Not Started -> In Progress -> In Review. User giao implementation mock G01/G02 ngày10 và commit local riêng sau QA; không push/PR. Bộ React feature ở frontend/features/donor và harness dev riêng frontend/dev/donor-preview, không sửa shell/client của Minh hay backend Dũng. G03 unreceived cancellation là acceptance baseline còn lại, chưa thuộc phạm vi lượt này; không ghi Done hoặc tự đổi dependencies/owner/estimate. Xem F07 implementation để biết lệnh chạy, test results, UI routes, mock labels và các OPEN integration. F01 commit cd5d88d được bảo toàn.

### QA độc lập F01/F07 - 10/11/2026

Giữ F01/F07 In Review và owner/estimate/dependencies cũ. Đối chiếu scope ngày10 đạt ở mức F01 tài liệu/F07 mock G01/G02. [QA report](../reviews/F01_F07_QA_REVIEW.md): đã tái hiện/sửa history/campaign races, optional ProblemDetail.fieldErrors, reset pending mock, single-line409; checker F01 exact enum. Lint/typecheck/build PASS; 7 unit +10 browser E2E PASS; real HTTP/Next/Fuji/NGO Not Run. Các sửa QA chỉ nằm working tree, chưa commit/push/PR. C01/F02 không đổi status.

### C03 Part 2 handoff cho Khoa - 10/11/2026

C03 vẫn In Review; Dũng hỗ trợ Part 2, Khoa là primary owner và reviewer. Bằng chứng là chạy local Hardhat (EDR), không phải Fuji; chưa có deployment hay transaction thật.

Khoa cần kiểm tra trước khi bắt đầu C04 (Fuji pilot, 11/11):

1. Chạy lại trong `contracts/`: `npm run compile`, `npm test` (kỳ vọng 44 passing); từ repo root: `node docs/fixtures/c01/verify-c01.mjs --ethers`.
2. Review guard order D49 `C01-O05` qua các test: caller không có quyền luôn nhận `UnauthorizedRelayer` / `OrganizationNotActive` trước mọi lookup `eventId` (C01-T02, T03, T04, T06, T11); `EventAlreadyRecorded` / `EventIdConflict` trước `CaseAlreadyVerified` / `InvalidChainSequence` (C01-T09, T10).
3. Quyết định về C01-T12: `orgRef = 0` hiện revert `OrganizationNotActive(0x0)`, không phải `InvalidZeroInput`. Vẫn fail-closed; đổi tên lỗi cần decision mới vì thay D49.
4. Xem xét: `AccessControl` cho admin gọi trực tiếp `grantRole`/`revokeRole` với `RELAYER_ROLE`. Việc này không cho quyền ghi (contract còn kiểm tra `org.relayer == msg.sender`) nhưng có thể làm lệch bộ đếm `_activeOrgCountByRelayer`. Chưa sửa; Khoa quyết định có chặn hay ghi nhận là giới hạn đã biết.
5. Decoder Java/Web3j (C05) dùng ABI đã khớp draft: 3 topic indexed `orgRef/caseRef/eventId`, data 128 bytes `uint64, bytes32, uint16, uint64`.

Not Run: Fuji, golden vector Java/TS, C06. Không đánh dấu C03 Done trước khi Khoa review xong.

### C03 review gate - 11/11/2026

C03 giữ In Review. Khoa review tại baseline 053b0f5, contract 51d97be. Gate BLOCKED: chưa có dependencies/runtime để compile và chạy độc lập Hardhat, chưa có compiler artifact, chưa đọc được PR #7 conversation qua các kênh hiện có; cần xác nhận/test lifecycle direct AccessControl revoke/renounce so với org relayer counters. Static C01 checker PASS; ethers/Hardhat/Fuji NOT RUN. Xem [C03 review gate](../reviews/C03_REVIEW_GATE.md) để có checklist, bằng chứng và điều kiện chuyển C04. Không đổi status task Dũng/Minh, không deploy/commit/push/PR.


### C03 review gate — cập nhật tiếp nối 11/11/2026

C03 giữ In Review; gate hiện tại BLOCKED chỉ còn R1 lifecycle authorization chờ Dũng/nhóm chốt policy hoặc sửa contract. Đã cài dependencies đúng package-lock với ủy quyền user (không đổi lockfile), compile mới PASS, 51 tests PASS (44 cũ + 7 characterization review), C01 --ethers PASS, compiler ABI parity 4/4 và source/settings build-info PASS. Đã đối chiếu 3 ảnh bàn giao PR #7 do Khoa cung cấp; không cần truy cập PR lại. Lifecycle direct revoke/renounce được tái hiện: active assigned multi-org relayer không tự phục hồi qua một org, single-org có thể tự regrant; không có bypass org scope trong tests. PASS characterization không phê duyệt policy. Điều kiện C04 và phản hồi từng điểm Dũng tại [C03 review gate](../reviews/C03_REVIEW_GATE.md). Không sửa contract/task khác; Fuji/Java/C06 NOT RUN; chưa commit/push/PR. Actual human effort chưa có dữ liệu. Ghi chú lượt trước được giữ làm lịch sử, không phải trạng thái môi trường hiện tại.


### C03 — nghiệm thu kỹ thuật lifecycle fix 11/11/2026

Gate PASS, R1 RESOLVED theo policy USER_CONFIRMED: revoke/renounce bền vững qua lifecycle, chỉ explicit authorized grant khôi phục. C03 giữ In Review cho review nhóm bản sửa, chưa Done/Fuji. Đã sửa AidRegistry.sol và chuyển review tests sang regression: compile PASS, toàn suite 55 passing (44 cũ + 11 review); C01 --ethers/SHA-256/Keccak PASS, full ABI trước/sau không đổi, draft 4/4 và current compiler source/settings PASS; diff check PASS. Chi tiết [C03 review gate](../reviews/C03_REVIEW_GATE.md). Đủ điều kiện contract chuẩn bị C04 khi được giao; Fuji/Java/C06 NOT RUN. Commit local C03 theo yêu cầu user; không push/PR/merge hoặc đổi task khác. Actual human effort chưa có số liệu. Các ghi chú BLOCKED trước đó là lịch sử đã được thay thế bởi mục này.


### C04 — preflight 11/11/2026

In Progress, preflight readiness BLOCKED; baseline 61cc738 bảo toàn. Official Fuji read-only RPC chainId 43113 tại block 60883114; compile/current artifact/source/settings và D49 ABI PASS, suite 56/56 (55 cũ + 1 local deployment dry-run) PASS, C01 --ethers PASS. Chưa public wallet addresses nên balance NOT RUN; chưa deploy runner/config Fuji. Chi tiết fingerprint, lệnh chạy, giới hạn và checklist tại [C04 preflight](../reviews/C04_PREFLIGHT.md). Không ký/gửi Fuji, không commit/push/PR, không C05/F07 hoặc đổi task khác.


### C04 preparation — runner/signers hoàn thiện 11/11/2026

C04 In Progress; software preparation PASS (61 tests, compile/checker/DPAPI roundtrip PASS), deploy readiness BLOCKED chỉ funding. Runner mặc định read-only, explicit Fuji chain guard/bytecode pin/gas-fee caps/nonce journal, local deploy+org registration dry-run PASS. Hai ví riêng DPAPI ngoài repo đã tạo, public addresses/plan ở contracts/deployment; balances 0, live gas estimate 1134741. User cần test AVAX vào deployer >=0.1; relayer funding cho business events sau. Faucet hiện cần account/wallet user quản lý. Chưa ký/broadcast; chưa full C04 acceptance/Done. Xem [C04 preflight](../reviews/C04_PREFLIGHT.md). Cho phép commit local preparation theo yêu cầu user, không push/PR/merge/C05/F07.


### C04 — actual Fuji pilot 11/11/2026

In Progress -> In Review; runtime PASS trên Fuji43113, manifest/ABI/receipts/independent authorization readback tại contracts/deployment và [C04 deployment](../reviews/C04_DEPLOYMENT.md). Không tự cấu hình backend UUID binding/C05. Explorer NOT_VERIFIED; Console Wallet chưa xóa do asset inventory bị RPC rate-limit, không xóa dữ liệu khác. User cho phép commit local completion; không push/PR/C05/F07.


### C04 — source verification và đối chiếu acceptance 11/11/2026

In Review, **4/4 đầu ra phân công 11/11 đạt**, source VERIFIED Exact Match trên Fuji Snowtrace/Routescan sau user cho phép dịch vụ; compiler/input/ABI parity PASS, independent readback PASS, suite61 PASS. Evidence tại contracts/deployment/fuji-verification.*.json; mapping DB chưa triển khai và thuộc Dũng, hướng dẫn cụ thể tại [C04 backend handoff](../reviews/C04_BACKEND_HANDOFF.md). Console Wallet NOT_DELETED do chưa đủ asset inventory, không ghi PASS cleanup. Không redeploy, không đọc keys, không C05/F07; commit local bổ sung theo user, không push/PR. Còn group review trước Done.


### F07 G03 — receipt refresh bugfix 11/11/2026

Giữ In Review. Browser regression FAIL trước sửa; thêm host dataRevision để refetch history/campaign sau mock receipt, giữ draft/pending thay vì reload/remount/điều hướng. Hai regression mới: history cập nhật tại chỗ và form đang mở nhận bounds mới; in-flight receipt race409 vẫn fail-closed. Lint/typecheck/build PASS,17 unit +19 browser tests PASS (G01/G02 regression gồm trong suite). Chi tiết tại [F07 implementation](../design/F07_DONOR_IMPLEMENTATION.md). Không sửa backend/contract/dependencies; commit local theo user, không push/PR. Chưa group review/real integration, không Done.

### C07 — safety review và receipt-timeout fix, 12/11/2026

In Review, kết luận kỹ thuật **PARTIAL**: review nonce/signer/persistence/retry/D53 và sửa nhánh hết retry budget thành SUBMITTED+operator attention/tiếp tục polling, không FAILED khi receipt chưa rõ. Thêm 3 IT cho late receipt/restart, RPC outage/duplicate broadcast và concurrent workers; **Java compile/IT NOT RUN** vì máy Khoa thiếu JDK21/DB env. 31 client tests PASS; Hardhat Mocha 147 pass/1 pending (không coi pending là PASS); C04/F02 read-only checks PASS. [C07 review](../reviews/C07_WORKER_SAFETY_REVIEW.md) ghi rõ gate, lệnh Dũng chạy và điều kiện nhận job thật. PR11 đã merge main `8005917`; PR10 giữ open: cần kiểm chứng safety fix và xử lý gate signer serialization / one in-flight cùng lease fencing. Chưa rotate/fund vì thiếu bằng chứng worker/DB trên máy Dũng không còn nghĩa vụ signer cũ. C05 vẫn PARTIAL; F07 real chưa đủ B07 main/DB seed/campaign, F08 fixture ready. Không triển khai F07/F08, không secret/Fuji transaction. Estimate/owner/lịch baseline giữ nguyên, actual human effort chưa có dữ liệu; không Done.


### C07 — signer serialization và fencing, lượt tiếp 12/11/2026

In Review, kết luận **PARTIAL chờ CI/review**. Giữ receipt fix 8ce40cec; bổ sung signer session lock không giữ SQL transaction qua RPC, one open signer/job (V13 fail-closed, không sửa journal cũ), nonce từ toàn journal, binding/sequence guards và tests cho lease expiry/session loss/restart/concurrency. Không cài JDK/DB trên Windows. [C07 review](../reviews/C07_WORKER_SAFETY_REVIEW.md) ghi điều kiện rollout, CI, migration và giới hạn. PR10 chưa merge do auto-review từ chối trước khi có test evidence; không bypass hoặc sửa nhánh Dũng. Chưa rotate/fund vì DB old-signer obligations chưa kiểm chứng; C05 vẫn PARTIAL. Chưa F07/F08 hoặc Done; không thay owner/estimate/lịch baseline.


### C07 — CI PASS và PR10 vào main, 12/11/2026

In Review, technical gate **PASS** tại 903f952: GitHub Actions run43191039984 compile + 22 unit + 18 IT PASS, không skip. Lỗi fixture nonce ở CI đầu đã sửa theo journal, không nới constraint/xóa dữ liệu. PR10 đã merge main d38fadb, PR12 bàn giao riêng 15 files C07; B07/API client đã ở main, F07 đủ nền code nhưng real test cần DB/donor/published campaign, F08 mock fixture ready. Chưa triển khai F07/F08. [C07 review](../reviews/C07_WORKER_SAFETY_REVIEW.md) thay kết luận PARTIAL kỹ thuật trước đây; các mục trước là lịch sử. Chưa rotate/fund do thiếu DB old-signer obligations; C05 vẫn PARTIAL, không Done hoặc đổi task của người khác.


### C07 — xác minh lại CI và tách việc Fuji, 12/11/2026

Giữ In Review, **technical PASS** được xác minh lại tại SHA4795c9a, run43191763706: compile + 22 unit + 18 integration tests PASS, 0 skip/error/failure. Acceptance matrix tại [C07 review](../reviews/C07_WORKER_SAFETY_REVIEW.md). PR10 đã merge d38fadb; PR12 là bản sửa C07 độc lập, chỉ merge sau checks head hiện hành và branch protection. Người dùng hoãn hoàn toàn rotation/funding sang cuối ngày, giữ nguyên ví/chain; việc đó không chặn C07. C05 Fuji E2E vẫn PARTIAL. Chưa Done, chưa triển khai F07/F08.


### F07 — tích hợp B07 thật vào frontend chung, 12/11/2026

Giữ In Review; Khoa thực hiện theo phân công ngày12, không đổi owner/estimate/deadline baseline. G01/G02/G03 đã dùng ApiClient/session chung qua routes `/campaigns`, `/campaigns/{id}`, `/me/commitments`, không mock fallback trong production. Lint/typecheck/Next build PASS;57 unit +14 browser HTTP-contract (production build) +21 preview regressions PASS. **PARTIAL về backend/DB E2E** vì máy Khoa không có backend8081/DB env được cấp, donor dev seed/campaign published chưa xác minh; CI không chạy code F07 chưa push theo user. [Bàn giao](../reviews/F07_B07_API_INTEGRATION.md). Cần Dũng cung cấp env/seed dev, Minh review shell/client và proxy/session thật; không giả backend PASS, không task khác/chain transaction/F08, chưa push/PR.


### F07 — kiểm chứng backend thật PASS trên CI, 12/11/2026

Giữ In Review, **PASS** theo [CI run43200835548](https://github.com/chidungbrvt-bit/AidBridge/actions/runs/43200835548), SHA80e0b88:57 unit frontend,9 Java validation,5 browser E2E và1 host IT, không skips/errors/failures. PostgreSQL assertions xác minh multi-item pledge, own history, partial cancel, received409/no-write, G01/G03 commit504/replay chỉ ghi một lần và CSRF/validation/revoked donor403 không ghi DB. [Review/evidence](../reviews/F07_B07_API_INTEGRATION.md). Thay thế kết luận PARTIAL trước đó cho CI; Windows vẫn không cài/chạy JDK/DB. Receipt seed chỉ là fixture như B07 tests (B08/R01 chưa kiểm), worker/chain tắt. PR13 đã tạo, chưa merge, không thay task khác/owner/estimate hoặc làm F08.

### Bàn giao ngày12/11 — F08 mock và Fuji Relayer D54

- **F08 In Review**, Khoa thực hiện phạm vi ngày12: [R01/D01 mock workspace](../reviews/F08_MOCK_GOODS_WORKSPACE.md), commit b387309. 8 unit +5 browser desktop/mobile/keyboard, lint/typecheck/Turbopack build PASS; Webpack production build lỗi nội bộ WasmHash trên Windows, không giấu kết quả. Chưa B08/B09/production E2E, giữ owner/estimate/lịch baseline.
- **C04 operational handoff PASS**, giữ In Review chờ nhóm review. Dũng xác nhận worker/DB không có nghĩa vụ signer cũ qua người dùng. Đã rotation orgRef sang D54 và funding0,149679527 test AVAX, receipts/current authorization readback PASS. [Hashes, bằng chứng và điều kiện worker](../reviews/FUJI_RELAYER_HANDOFF_2026_11_12.md). Ví cũ0AVAX/không RELAYER_ROLE, Admin nguyên quyền; không redeploy/đổi ABI. Faucet chưa claim thêm vì Console Wallet khóa và UI chưa có đường gửi an toàn, mục tiêu khoảng0,5 chưa đạt; không lấy funding từ Admin.
- **C05 vẫn PARTIAL**, không gửi job B05/worker nghiệp vụ trong lượt này. Implementation worker/C07 đã có trên main, không còn blocker thiếu code; phần DB seed/binding D54/job thật/receipt projection do Dũng xác minh và chạy tiếp. Không dùng independent smoke, rotation hay funding thay B05 E2E. Không đổi trạng thái task của Dũng/Minh hoặc Done.
- Hai recovery CLIXML mới trong contracts/deployment bị ignore, chỉ chứa signed bytes DPAPI; hai ví gốc/Console Wallet giữ nguyên. Không tạo output/source ngoài repo, không cleanup/push/PR/merge.
