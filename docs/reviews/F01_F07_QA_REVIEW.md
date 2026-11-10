# QA độc lập F01 + F07 - 10/11/2026

Status: **In Review**, chưa Done. Vòng QA không commit, push, PR, fetch, pull, merge, rebase hoặc reset. Repository: D:/Project/AidBridge, khoa; HEAD 9932708867156659475065a20c336af5368c03b1, F01 cd5d88d38b971134e3a23ba5ba999b68e2711271 được giữ nguyên. Working tree sạch trước QA, các sửa đổi bên dưới để uncommitted cho Khoa review.

## Cơ sở và phạm vi

Đối chiếu AGENTS, Master Plan/MVP Scope/Decision Log, backlog, API Specification, Business Workflows, thiết kế/types/fixtures F02, tài liệu/JSON/checker F01, toàn bộ feature/model/mock/preview/tests/config/docs F07 và phân công 10/11 trong ngữ cảnh nhiệm vụ. Hai commit ban đầu chỉ chứa Product/Validation, docs/backlog và feature donor/preview; không có thay đổi backend/contracts hoặc task implementation khác.

F01/F07 được nghiệm thu theo phân công ngày10: F01 scenario + assumptions, F07 G01/G02 mock UI. Baseline cũ về tuyển interview candidates và G03 cancellation vẫn ghi thiếu, không tự xóa khỏi backlog hoặc claim hoàn thành. Thiếu NGO research và backend E2E không phải lỗi acceptance của deliverables ngày10.

## Nghiệm thu F01

| Tiêu chí | Đối chiếu độc lập | Kết luận |
|---|---|---|
| Demo Household A, nhiều mặt hàng | Food package2/Hygiene kit2, units cố định, hoàn toàn giả | Đạt |
| Full lifecycle | 11 bước, 9 snapshots, draft/publish/partial receive/delivery/confirm/close rõ; P03 draft chỉ là intermediate record được mô tả, không giả full snapshot | Đạt thiết kế |
| Phân quyền độc lập | userId volunteer/verifier/confirmer khác nhau; no self-approve/self-confirm dù nhiều roles; own org/membership/state/evidence guards | Đạt thiết kế; chưa là runtime proof |
| Checklist/expected results | 11 bước +16 nhánh có expected HTTP/code/state; NOT_RUN labels không đổi | Đạt |
| Assumptions và bàn giao | F01-A01..A10, impact/default/owner, R01/R02 và explicit no-contact giữ nguyên | Đạt việc ghi nhận, chưa validated |

F01-A01 đã giải quyết ở **mức tài liệu** bằng bảng đối chiếu: ORG_ADMIN↔ORGANIZATION_ADMIN, VOLUNTEER↔FIELD_VOLUNTEER, VERIFIER↔CASE_VERIFIER; DELIVERY_CONFIRMER giống nhau. Không có mâu thuẫn ý nghĩa quyền trong kịch bản. Backend enum/AuthMapper trả tên ngắn, không có alias tự động; Dũng/Minh vẫn phải thống nhất interface trước real integration. Donor không phải organization role; AuthService vẫn donorEnabled=false. Không đổi backend, F02 hay định nghĩa sản phẩm.

Checker F01 trước QA dùng substring để kiểm role, có thể nhận tên không đúng enum. Đã siết thành exact enum membership và unique toàn bộ actor identities. Đây là cải thiện kiểm tra, không đổi scenario/JSON/acceptance. Product Requirements và Risk Register của F01 giữ nguyên ở lượt QA; không phát hiện tuyên bố research/feature thật trái nguồn.

## Phát hiện F07 và sửa trong working tree

| Mã / mức độ | Lỗi và cách tái hiện | Xử lý / bằng chứng |
|---|---|---|
| QA-01 / P2 | Mở G02 trước khi pledge chậm hoàn thành: history fetch xong lúc còn rỗng và không tự invalidate khi save xong | Thêm historyRevision khi mutation thành công, hủy hiệu lực request đọc cũ qua effect cleanup. Regression browser FAIL trước sửa, PASS sau sửa |
| QA-02 / P2 | Đổi campaign trong lúc POST chờ: response cũ ghi success và campaign cũ lên UI mới | DonorApp bọc workspace được key bằng campaignId; scope mới không nhận state từ component cũ. Regression browser FAIL trước sửa, PASS sau sửa. Request gửi rồi vẫn có thể hoàn thành trên backend cho campaign cũ; đây không phải hủy business operation |
| QA-03 / P2 | ProblemDetail hợp lệ từ auth guard có thể không có fieldErrors; loop trong catch có thể throw, mất feedback lỗi | ApiError nhận fieldErrors optional và chuẩn hóa thành[]. Unit FAIL trước sửa, PASS sau sửa; adapter vẫn cần validate DTO thực |
| QA-04 / P2 (mock demo) | Reset khi mock pledge đang chờ: request cũ ghi lại history vào context mới | Generation guard từ lúc submit tới sau wait; reset invalidates pending mock writes. Unit FAIL trước sửa, PASS sau sửa |
| QA-05 / P3 | Fault409 cho request một dòng vẫn trỏ items[1], không tồn tại trong body | Chọn index hợp lệ của submitted body. Unit mapping một dòng PASS |
| QA-06 / P3 | Test gaps cho concurrent writes/reset/scope races và exact role enums | Thêm test host chỉ trong tests, hai browser races, ba unit cases, exact F01 role check; không thêm dependencies |

Không có P0/P1 được phát hiện trong phạm vi mock này; không có finding còn mở chặn PR review sau khi commit các sửa QA. Đây không phải chứng nhận production security.

## Kết quả kiểm tra thực sự chạy

| Lệnh / kiểm tra | Kết quả cuối |
|---|---|
| node docs/product/validation/verify-f01.mjs; node --check checker | PASS: source API references, exact role enums, distinct identities, arithmetic và execution labels |
| node docs/fixtures/f02/verify-f02.mjs | PASS: regression fixtures F02, không sửa F02 |
| pnpm lint | PASS, module + preview + tests |
| pnpm typecheck | PASS, TypeScript strict/noEmit |
| pnpm build | PASS, Vite preview exit0; warning use client directive bị bỏ qua vẫn như baseline, không phải Next build |
| pnpm test | PASS 7/7, tăng từ4: validation, history/totals/replay, atomic rejection, unknown recovery/pagination, optional fields, reset generation, concurrent capacity + single-line409 |
| pnpm test:e2e | PASS 10/10 Edge headless, tăng từ8; 8 UI tests cũ +2 race tests mới |
| Mock network isolation | Tất cả 8 test UI chính assert không có request pathname /api/; race host chỉ gọi in-memory mock, không có real transport |
| Desktop/mobile/keyboard | PASS ở mức cơ bản:1440px/390px, no page overflow, field focus link, status feedback; đã xem screenshots mới. Table mobile scroll nội bộ, không redesign |
| JSON/syntax/internal references/Markdown/diff/scope | PASS sau kiểm tra cuối; không dependencies thay đổi, không staged changes |

Trước sửa, 2 unit và2 browser regression mới đã FAIL đúng lỗi nêu trên. Kết quả cuối PASS không xóa bằng chứng failures ban đầu. Test mock concurrency chỉ kiểm JavaScript mock atomicity, không phải PostgreSQL/production concurrency.

**NOT RUN:** real backend HTTP/session/CSRF authorization, persistence/concurrency DB, Next shell integration/build, blockchain/Fuji/contract suite, full accessibility audit, NGO interviews. Không cần chạy các mục này để nghiệm thu scope mock ngày10; vẫn bắt buộc ở các task tích hợp tương ứng.

## Hướng dẫn chạy và tiếp nhận sau QA

Trong frontend/dev/donor-preview: `pnpm install --frozen-lockfile`, `pnpm dev`. G01: http://127.0.0.1:5173/campaigns/10000000-0000-4000-8000-000000000001 ; G02: http://127.0.0.1:5173/me/commitments . Reload/reset xóa mock data; label mock được giữ. `/?mode=real` fail-closed, không fallback sang production giả.

Minh: mount feature vào shell, inject DonorApi từ **shared client hiện có**, quản lý cookie/CSRF/revocation, remount theo userId khi đổi identity/API session. Feature hiện tự remount theo campaignId; không suy đó là đủ isolation giữa hai users. Scope component không hủy POST đã gửi; khi đổi account/campaign hãy reconcile qua lịch sử/session đúng identity. Adapter cần validate success DTO/Page và map ProblemDetail/network errors, không mutate submitted body/key. Session-capability false chặn thao tác và xóa private UI; backend vẫn phải enforce.

Dũng: xác nhận donorEnabled thực (hiện false), role names/capability, real G01/G02 errors/idempotency và metadata projection G02. Tên case/name/unit với campaign ngoài context hiện tại chưa có đủ trong CommitmentResponse; UI fallback ID/Unit unavailable, không bịa private case data. Cần chốt public metadata read model khi tích hợp nhiều campaign.

Không có blocker phụ thuộc Dũng/Minh cho PR **F01 thiết kế + F07 mock**. Đây là blockers cho **real integration**: shared shell/client chưa có trên baseline, donor capability chưa cấp, backend goods endpoints/DTO/projections chưa sẵn sàng. G03 cancellation vẫn ngoài phân công G01/G02 ngày10 và chưa làm. Không biến các điều này thành Done.

C01/F02 trong backlog vẫn In Review, phù hợp trạng thái chờ cross-owner acceptance dù PR #4 đã merge; không đổi chúng thành Done. Header planning cũ có mô tả baseline chưa triển khai, không dùng header đó để phủ nhận source/runtime đã kiểm tra hoặc suy task khác hoàn tất.

## Kết luận

F01 đạt tiêu chí tài liệu ngày10, F07 sau sửa đạt tiêu chí mock G01/G02 ngày10; cả hai giữ **In Review**. Đủ chất lượng để chuẩn bị PR review chung **sau khi Khoa review và commit các sửa QA ở lượt Git tiếp theo**. Hiện chưa thể đưa các sửa mới lên PR vì cố ý để uncommitted. Không đủ cơ sở quảng bá là full F07 baseline/real backend/frontend integrated/Fuji đã hoàn thành.

## Bàn giao cuối lên GitHub - 10/11/2026

Sau vòng QA phía trên, Khoa đã duyệt bàn giao và cho phép commit QA riêng, push khoa và mở/cập nhật một PR chung vào main; không amend F01/F07 và không merge PR. Các phát biểu "uncommitted/không push" phía trên mô tả thời điểm vòng QA trước, không thay thế authorization mới này.

Đã chạy lại F01/F02 checkers, lint, typecheck, Vite build, 7 unit và10 Edge browser E2E: PASS. Các giới hạn real HTTP/Next/DB concurrency/Fuji không đổi. Cả hai task vẫn In Review. Fetch cuối thấy origin/main caf3c12 chỉ thêm archive AidBridge-Minh-F03-F04.zip, chưa tích hợp source shell vào cây frontend; không tự giải nén hoặc merge. Diff PR theo merge-base loại trừ PR #4 và không xóa archive mới của main.
