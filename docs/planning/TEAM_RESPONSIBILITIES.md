# Phân công và phối hợp nhóm AidBridge

Ngày tạo/cập nhật: **03/11/2026**. Người duy trì: **Minh**. Trạng thái: phân công đã được người dùng xác nhận; application chưa triển khai. Mã B/C/F và task IDs được giữ để không làm đứt liên kết tài liệu.

## 1. Thành viên và trách nhiệm chính

| Thành viên | Mã / vai trò | Chịu trách nhiệm chính | Phối hợp |
|---|---|---|---|
| **Dũng** | B — Backend Engineer | Spring Boot, PostgreSQL/Flyway, auth/RBAC, case/verification, campaign/commitment, receipt/delivery/confirmation, transaction và quantity rules | Làm cùng Khoa ở phần Java/database/REST của tích hợp blockchain; thống nhất API với Minh |
| **Khoa** | C — Blockchain & Integration Engineer | Solidity/Hardhat, AidRegistry, contract permissions/events, Fuji, ABI, signing/nonce, receipt/log verification và reconciliation | Làm cùng Dũng để nối backend với chain; cung cấp trạng thái chain chính xác cho Minh |
| **Minh** | F — Frontend & Product Engineer | Next.js/TypeScript/Tailwind, UI theo quyền, API client, E2E, public demo, video/pitch và xác thực nhu cầu sản phẩm | Phối hợp DTO/lỗi với Dũng, trạng thái on-chain với Khoa; hỗ trợ TypeScript tests khi có capacity |

**Khoa phụ trách kết quả tích hợp blockchain, không phải tự viết toàn bộ backend hoặc mọi đoạn Java liên quan.** Dũng hỗ trợ trực tiếp theo ranh giới bên dưới. Minh tập trung frontend để hai bạn không phải gánh thêm giao diện.

Mỗi task có **một người chịu trách nhiệm chính**. Người hỗ trợ có thể viết mã, tests và sửa lỗi phần đã nhận, không chỉ góp ý. Người phụ trách task kiểm tra đầu ra chung trước khi nghiệm thu.

## 2. Dũng và Khoa chia phần tích hợp

| Hạng mục / task | Dũng thực hiện | Khoa thực hiện | Đầu ra nghiệm thu |
|---|---|---|---|
| Hợp đồng dữ liệu — B01, C01 | Model/DTO, actor/org, audit event/job, business snapshot, ID và privacy boundary | ABI, function/event, random refs, commitment và causal sequence | Cùng event ID/schema, dữ liệu đủ anchor, không PII on-chain |
| Vertical slice — B05, C05 | Luồng verify; review/audit/job atomic trong SQL; chuẩn bị DTO Java và API đọc dữ liệu nghiệp vụ | Web3j/contract/Fuji, ký/gửi tx, exact receipt và CaseVerified event | Người A tạo → người B duyệt → job → actual Fuji proof → UI hash/status |
| Outbox/worker — B05/B07, C07 | Làm hoặc hỗ trợ entity/repository/migration, transaction và query jobs; kiểm tra lưu bền/idempotency | Worker phía chain, signer/nonce, signed bytes/hash trước broadcast, gửi lại và xử lý RPC | Restart không mất job/duplicate event; không giữ SQL transaction chờ RPC |
| Receipt/retry/khôi phục — B11, C08 | Dữ liệu test, DB assertions, rollback/replay, cập nhật trạng thái lưu trữ; hỗ trợ Java polling/retry khi có giờ | Điều kiện receipt/log hợp lệ, chain/contract/event/commitment, attempt/nonce và reconciliation | Null/revert/mismatch không confirmed; chain thành công nhưng DB lỗi được repair |
| Audit REST API — B05, C05, C09 | Làm hoặc hỗ trợ controller/query/DTO/pagination, quyền đọc, public projection và REST errors | Cung cấp verified chain status, txHash/block/contract/event và explorer URL | Một API cho Minh; business/evidence/chain riêng, không leak private data |
| Goods-to-chain — B08/B09/B10, C10 | Quantity/state/identity guards, dispute và immutable DB snapshot; event đúng nghiệp vụ | Map snapshot sang contract function/event, refs và causal order | Không trộn pledged/received/delivered/confirmed; retry không tăng số lượng lần nữa |
| Kiểm thử chung — B11, C11 | API/PostgreSQL/permissions/state/quantity assertions | EVM/contract/RPC/receipt/log assertions và actual Fuji smoke | Cùng một kịch bản có bằng chứng DB, chain và API, không chỉ test từng lớp |

Đây là chia phần trong các task hiện có, không thêm tính năng hoặc viết trùng controller/repository. Audit API đã có từ vertical slice thì C09 mở rộng API đó.

**Khoa nắm blockchain:** permissions/transitions, ABI/events, nonce/signing và điều kiện xác nhận receipt. Dũng hỗ trợ Java nhưng không tự thay các giả định đó. **Dũng nắm nghiệp vụ:** ownership, RBAC, identity separation, state/quantities và SQL boundary. Khoa dùng đầu ra nghiệp vụ này, không viết lại toàn bộ trong adapter.

## 3. Thứ tự phối hợp, không chờ backend xong hết

| Điểm bắt đầu | Dũng | Khoa | Minh / mục tiêu |
|---|---|---|---|
| B01/C01 | Chốt schema/DTO/audit/job và transaction boundary | Chốt ABI/events/commitment và trust model | Nhận mock DTO/errors/chain statuses |
| B03–B05 có login → create → independent verify | Bàn giao backend chạy được, job/snapshot mẫu; cùng ghép C05 ngay, không chờ B06–B10 | Nối contract/Fuji vào flow thật | Ghép F04/F05; vertical slice trước hoặc trong09/11 |
| B06–B10 và C07–C10 đang chạy | Backend chính tiếp tục; bàn giao DTO/jobs/query/mapping cùng từng tính năng | Signing/nonce/receipt/reconcile trên đầu vào đã thống nhất | Campaign/goods/audit ghép dần, không chờ mọi API hoàn tất |
|16–18/11 | Kiểm tra goods/disputes, DB quantities và mapping | Kiểm tra chain ordering/retry/milestones | All P0 integrated18/11 |
|19–20/11 | Backend/DB regression và fixes | Contract/RPC/recovery regression và fixes | E2E/public smoke; stable20/11 |
|21–22/11 | Backend/runbook handover | Deployment/chain/key runbook và actual proof | Video/pitch/package/rehearsal ready22/11 |

Ngày là finish-by target, không phải ngày phải đợi. **Dũng xong phần backend sớm thì kéo ngay phần Java/DB đang chặn Khoa để hỗ trợ.** Nếu backend chưa qua tests bắt buộc, không coi là đã xong để chuyển việc; ưu tiên ghép vertical slice và xử lý blocker chung.

## 4. Ai xử lý lỗi trước?

| Hiện tượng | Xử lý trước | Cùng kiểm tra |
|---|---|---|
| API401/403/404, sai org, self-approval/self-confirm, quantity409 | Dũng | Khoa kiểm tra event tạo đúng lúc; Minh kiểm tra UI |
| Job không tạo, snapshot/ID sai, transaction/DB lỗi | Dũng | Khoa kiểm tra input adapter |
| RPC/gas/nonce/signing, contract revert, ABI decode sai | Khoa | Dũng hỗ trợ Java và test fixture để tái hiện |
| Tx thành công nhưng DB/API chưa cập nhật | Khoa đối chiếu receipt/log; Dũng đối chiếu attempt/job/DB | Khoanh điểm mất đồng bộ cùng nhau; không tạo logical event mới để thử |
| UI confirmed khi API còn pending/failed | Minh | Khoa xác nhận chain state; Dũng xác nhận DTO/query |
| Integration test chưa rõ lỗi lớp nào | Dũng + Khoa | Minh cung cấp flow/request/response đã lọc secrets |

Blocked qua một checkpoint thì báo task ID, việc đã thử, sanitized error, eventId/txHash nếu có, DB state và phần cần người kia giúp. Không mất cả ngày tự mò khi người kia có thể giúp khoanh vùng. Không gửi private key/cookie/salt/PII.

## 5. Quỹ giờ và công việc hỗ trợ

Baseline chưa đổi: **36 task, 12 task/người phụ trách chính; mỗi nhóm task B/C/F dự toán56h; toàn nhóm168h work+24h reserve=192h capacity**. Cân bằng estimate không chứng minh độ khó bằng nhau; Khoa có uncertainty lớn ở EVM/nonce/receipts.

D27: đến20/11 weekday2.5h/weekend5h;21–22/11 x3h;23–25/11 x1h reserve. Tổng64h/người. Mốc18/11 P0,20/11 stable,22/11 package giữ nguyên.

- Đầu ra nghiệp vụ/DB vốn thuộc B05/B07/B08–B11 được tính vào task B đó. Khoa dùng lại, không viết lại rồi tính thêm ở task C.
- Khi Dũng trực tiếp làm một phần C05/C07/C08/C09/C10, ghi phần việc/người/giờ thực tế trong cùng task C. Tổng effort task là công của cả hai; không ghi cùng một giờ ở cả B và C.
- Dũng không nhận thêm toàn bộ task C vào56h backend. Hỗ trợ sâu hơn dùng giờ thực tế tiết kiệm hoặc capacity được điều chỉnh rõ trước khi nhận. **Không mặc định8h dự phòng đều rảnh sớm trước18/11.**
- Khoa hỗ trợ ngược bằng review payload/ABI, giải thích transaction lifecycle, chẩn đoán chain errors và kiểm tra integration tests; không bị chuyển thêm hàng loạt backend tasks để bù giờ.
- Nếu cần hỗ trợ mà Dũng đang kín lịch: báo blocker, xác định ưu tiên và cập nhật phân bổ/forecast trước; không double-book, giảm estimate hoặc bỏ tests để giữ mốc.
- Không đổi primary owners, deadlines, scope hoặc trạng thái Done chỉ vì tài liệu đã chia hỗ trợ. Application tasks hiện vẫn **Not Started**.

## 6. Checklist bàn giao Dũng ↔ Khoa

1. Dũng cung cấp DTO mẫu, owning org, immutable business snapshot, event ID/evidence refs và pending job; không PII trong public payload.
2. Khoa cung cấp ABI/schema version, chain43113, deployment context khi thực sự deploy, mapping function/event và định nghĩa pending/failed/confirmed.
3. Hai bạn chạy cùng một success và một failure path; ghi expected/actual của DB, receipt/log và API.
4. Retry giữ logical ID; không tăng quantities hoặc duplicate milestone; exact successful receipt+event mới confirmed.
5. Dũng review SQL/ownership/state/quantities; Khoa review authorization/nonce/signature/receipt/sequence. Chưa kiểm chứng thì chưa Done.
6. Minh nhận contract chung; đổi DTO/ABI phải báo và sửa mock/test cùng lúc.

Không ai tự coi AI-generated code đã được kiểm chứng. Backend relayer không phải independent human wallet signature; chain không chứng minh physical delivery.

## 7. Học đúng lúc, trong estimates hiện có

| Người / chủ đề | Thời điểm, mục đích | Bài tập / điều kiện sẵn sàng |
|---|---|---|
| Dũng — session/CSRF, khoảng1.5h | B03: auth an toàn | Login/logout, session rotation, thiếu CSRF403 |
| Dũng — RBAC/org isolation, khoảng1h | B01/B03: không truy cập chéo/tự duyệt | OrgA không đọc OrgB; dual-role vẫn bị chặn |
| Dũng — transactions/locking, khoảng1.5h | B07/B09: concurrency/quantities | Hai pledge tranh đơn vị cuối, không partial commit |
| Dũng — Flyway/DTO/errors, khoảng1h | B02/B04: migration và API contract | Empty DB migrate; invalid input400; no entity leak |
| Dũng — outbox/idempotency/integration, khoảng1.5h | B05/B07/B11: bàn giao job cho Khoa | RPC down không mất business commit; replay một lần |
| Khoa — EVM/Fuji/keys/gas, khoảng1.5h | C01: chain và trust model | Phân biệt admin/relayer;43113; test-only keys |
| Khoa — Solidity/state/events/ABI, khoảng2h | C02/C03: contract milestone | Authorized event đúng; caller/org sai revert |
| Khoa — AccessControl/security, khoảng1h | C03/C06: permissions/transitions | Revoke/duplicate/invalid confirm bị chặn |
| Khoa — Hardhat/deploy, khoảng1h | C02/C04: actual deploy proof | Local tests trước; actual Fuji receipt/address sau |
| Khoa + Dũng — Web3j/nonce/receipt, khoảng2.5h trong các task C | C05/C07/C08/C11: bridge và recovery | Persist trước broadcast, null receipt pending, repair DB từ logs |
| Minh — Next/TS/components, khoảng1h | F02/F03: typed UI | Routing đúng, secret không vào client bundle |
| Minh — API/session/CSRF, khoảng1h | F03: login/API | Handle401/403 và token refresh |
| Minh — forms/accessibility, khoảng1h | F04/F06: nhiều dòng hàng | Keyboard/errors/focus rõ |
| Minh — role/async status, khoảng1.5h | F05/F09: honest UX | Hash mới gửi pending; self-actions bị chặn |
| Minh — deployment/E2E, khoảng1.5h | F10/F11: public demo | HTTPS/CSRF/private files/budget/E2E checks |

Không cộng thêm learning budget ẩn; học gắn với task nhỏ, không học full courses trong deadline.

## 8. Tài liệu và báo tiến độ

| Người duy trì | Tài liệu | Người kiểm tra |
|---|---|---|
| Dũng | AGENTS, Decision Log, Master Plan, Backlog, System/Database/Business Workflows/API, Security & Privacy | Khoa kiểm tra chain boundary; Minh kiểm tra API/UX |
| Khoa | Blockchain Architecture, Test Strategy; audit/outbox phần liên quan | Dũng kiểm tra SQL/Java/permissions; Minh kiểm tra hiển thị |
| Minh | README, Product Requirements, MVP Scope, Daily Roadmap, Team Responsibilities, Risks, Demo/Submission | Dũng/Khoa kiểm tra feasibility và technical claims |

Mốc giữ nguyên: contracts05/11, slice09/11, pledge13/11, reliable sync16/11, đủ P018/11, stable20/11, video/package22/11,23–25/11 contingency/submission. Hoàn tất sớm thì chuyển việc ngay.

Báo nhanh hằng ngày: người/task đã làm, bằng chứng, giờ thực tế, việc tiếp, blocker, cần ai hỗ trợ, capacity còn lại. Checkpoint ngắn tại gates hoặc khi blocked, kiểm tra UI/API/DB/contract cùng nhau.

Mẫu ghi công chung: Task ID / phần việc / người thực hiện / người kiểm tra / giờ thực tế / đầu vào-đầu ra / tests-bằng chứng / blocker-next action. Ghi vào Backlog khi thực thi; chưa có actual hours hoặc test results ở giai đoạn này.

Role phát triển Dũng/Khoa/Minh khác role nghiệp vụ trong ứng dụng. Demo có nhiều account, creator/verifier và delivery recorder/confirmer vẫn phải là các identity riêng đúng BR-02/BR-06.
