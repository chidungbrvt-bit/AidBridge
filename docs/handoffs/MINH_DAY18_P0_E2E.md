# Minh — E2E ngày 18/11/2026

Nhánh duy nhất `minh`, tiếp tục PR #31. F09/F05 **In Review**, F10 **In Progress**; không tự merge, deploy hoặc bật worker.

## Đồng bộ và phần việc đã có

Fetch origin trước khi làm. `main@fbf8c3e`, `dung@9f29b76` và `khoa@5267e99` chưa đổi so với lượt bàn giao trước; merge main/dung đều `Already up to date`. Hai điểm F09 trong kế hoạch hôm nay đã có: bảng số lượng theo campaignItemId của delivery được chọn, và đăng nhập trở lại đúng `/disputes/{id}`. Hai ca browser tương ứng vẫn nằm trong bộ hồi quy.

Lượt này bổ sung **một hành trình UI trên cùng hồ sơ mới**: tạo case và consent/evidence giả lập → VERIFIER khác creator duyệt → ORG_ADMIN tạo/publish campaign hai mặt hàng → DONOR cam kết → ORG_ADMIN nhận hai đợt → VOLUNTEER ghi delivery → DELIVERY_CONFIRMER khác recorder xác nhận → ORG_ADMIN đóng → xem audit. Mọi HTTP nghiệp vụ của ca mới đi qua Next → Spring → PostgreSQL; không chèn case/campaign/receipt/delivery bằng SQL để thay các bước UI. SQL chỉ đối chiếu kết quả.

Java fixture chỉ bổ sung email VERIFIER đã có cho browser. Không sửa backend production, DTO, quyền hoặc worker. Test host retry tối đa một lần **chỉ** khi Next build trên Windows gặp `EPERM ... mkdir`; không retry Playwright assertions, timeout, TypeScript hoặc module-not-found errors. Lỗi ban đầu giữ trong log, retry thất bại vẫn fail.

## Kết quả kiểm thử

SHA đã test: **`1578ddfb61d117eb8132218cb10d3455dbfee89e`**. Candidate local dùng Next production real mode, Spring Boot, Java 21, PostgreSQL 16.16/schema IT riêng đến V18; worker tắt. Bộ browser **11 pass, 0 fail/error/skip**, 51,3 giây; ca mới trên cùng hồ sơ 7,8 giây. Java host **1 pass, 0 fail/error/skip** (là lớp chạy 11 browser cases, không cộng thêm một E2E). Request validation **9 pass, 0 fail/error/skip**. Frontend **77 unit pass**, typecheck và Webpack production build PASS.

DB xác nhận chỉ một delivery, case COMPLETED và sequence theo đúng hành trình: CASE_VERIFIED → AID_PLEDGED → GOODS_RECEIVED → GOODS_RECEIVED → DELIVERY_RECORDED → DELIVERY_CONFIRMED. Hai receipts là hai mốc riêng, không bỏ sequence; không tuyên bố đây là prefix bắt buộc cho mọi case hoặc một lượt đã có COMMITMENT_REDUCED. Các job đang pending khi worker tắt, không có Fuji confirmation mới.

- [JUnit gốc 11 ca](../reviews/evidence/day18-p0-e2e/donor-real.junit.xml).
- [JUnit riêng hành trình mới, có testedSha](../reviews/evidence/day18-p0-e2e/same-case-flow.junit.xml).
- [Evidence IDs/counts từ DB giả lập](../reviews/evidence/day18-p0-e2e/flow-evidence.json), không credentials/session/private payload.

Lệnh ở frontend: `npm test`, `npm run typecheck`. Với DB test riêng và các biến `AIDBRIDGE_TEST_DATABASE_*`, `PG*` như workflow F07, bật `CI=true`, `AIDBRIDGE_F07_REAL_E2E=true`, `NEXT_PUBLIC_API_MODE=real`, `BLOCKCHAIN_ENABLED=false`, rồi từ backend chạy:

```text
./mvnw -B -ntp -Dtest=CommitmentRequestValidationTest,CancellationRequestValidationTest -Dit.test=DonorUiE2EIT verify
```

Host build Next và chạy `playwright test --project=donor-real --reporter=list,junit`. Lượt Windows dùng Maven offline/cache và Mockito javaagent, `CIRCLE_NODE_TOTAL=2` để giới hạn một page-generation worker. Các lượt đầu trên stage cũ fail trước browser ở Next build; không tính là E2E pass. Stage sạch ban đầu thiếu fixture C05 đã được bổ sung từ file tracked; sau đó cả build và 11 ca pass trên source nguyên trạng của SHA trên. Không cài/nâng dependencies hoặc sửa quyền Windows. Workflow F07 yêu cầu 11 browser scenarios và không chấp nhận skip.

## Bàn giao cần nhận từ Dũng để chạy môi trường dùng chung

Không chờ C06 để chạy B10. Bộ CI local dùng DB test riêng; không trỏ nguyên bộ có SQL assertions này vào DB demo.

| Cần nhận | Điều kiện trước khi chạy |
| --- | --- |
| Backend candidate | SHA/tree/artifact đã chạy; gồm B10, `reviewCampaign`, route đọc dispute/evidence của PR #31 và T01/T02 D49. Nếu chỉ PR #30 thì ghi rõ API còn thiếu, không coi là candidate đầy đủ. |
| URL và DB demo | Origin frontend/API được phép test, migration V18/decision V16 đã xác nhận, dataset/org dành cho test và chính sách giữ dữ liệu. Đặt API_INTERNAL_BASE_URL trước build Next. |
| Tài khoản | VOLUNTEER, VERIFIER, ORG_ADMIN, DELIVERY_CONFIRMER, DONOR riêng; PLATFORM_ADMIN riêng chỉ cho ca dispute review được cấp. Xác nhận user IDs/roles từ `/me`; không chia sẻ session hoặc gửi mật khẩu vào chat/repo. |
| Evidence/consent | Fixture allowlist đúng org, case giả lập mới, cho phép public summary; không dữ liệu cá nhân thật. |
| Worker/chain | Trạng thái bật/tắt và deployment binding thực tế. Chạy business E2E được khi worker tắt; response PENDING_ON_CHAIN không được nâng thành Fuji confirmed. |
| Quyền public smoke | URL deployment và phê duyệt test. Chưa có thì Not Run, không tạo hosting/provider hoặc thay registry để bù lịch. |

Trong các refs đã kiểm tra chưa có gói bàn giao runtime/tài khoản demo mới đủ các trường trên. Lượt này dùng candidate local với account giả lập của IT, không chứng minh staging/public deployment đã chạy.

## Kịch bản chạy trên môi trường được bàn giao

1. Pin cùng SHA frontend/backend trong biên bản; build real mode với đúng API origin. Kiểm CSRF trả 200, không header mock; đăng nhập từng actor, kiểm `/me` và scope org.
2. VOLUNTEER tạo case hai mặt hàng Food 2 PACKAGE, Hygiene 1 KIT, consent/evidence giả lập. Ghi case ID và version; 201/SUBMITTED.
3. VERIFIER khác creator xem đúng evidence versions rồi duyệt; 200/VERIFIED. Giữ riêng trạng thái chain.
4. ORG_ADMIN tạo draft và publish campaign. DONOR riêng cam kết 2/1. ORG_ADMIN nhận đợt đầu 1/1, kiểm outstanding Food còn 1; nhận tiếp Food 1.
5. VOLUNTEER giao 2/1 với evidence snapshot của case. DELIVERY_CONFIRMER xem đúng delivery ID, số lượng và snapshot rồi xác nhận. Người ghi dù có thêm role confirmer vẫn bị API từ chối tự xác nhận 403; UI cũng chặn.
6. Trên ca test riêng đủ hàng: mở S01, kiểm P05 bị 409 DISPUTE_OPEN. PLATFORM_ADMIN review đúng snapshot rồi resolve; đọc lại C03/P04/T01 sau UPHOLD hoặc VOID_DELIVERY theo ca tương ứng. Không suy review CONFIRMED thành blockchain confirmed.
7. Đóng khi đủ confirmed totals và không còn tranh chấp: P05 200, trang đang mở tự hiện COMPLETED. Kiểm lại sau tải mới, không mất số lượng hoặc tạo bản ghi trùng qua retry.
8. Truy cập direct dispute từ phiên chưa login, login đúng actor và kiểm quay lại đúng URL; giữ ca mất response/401 và retry đúng payload/key của bộ hồi quy.
9. Đọc T01 có quyền, T02 anonymous và public page D49; scope/privacy, event ordering, pending/failed/missing proof và Refresh audit. Link chia sẻ chỉ sau đọc API thành công, link explorer chỉ theo projection hợp lệ.

Ghi từng bước: SHA, URL test, vai trò, HTTP status/error code, ID test và trạng thái trước/sau. Không đưa password, cookie, evidence bytes hoặc payload private vào báo cáo. Khi lỗi, giữ key/payload gốc cho retry hoặc đọc lại kết quả; phối hợp Dũng từ bước/response đã khử dữ liệu riêng.

## F05/F10 và chuẩn bị demo/video

Tái sử dụng [kết quả F05/F09/F10 và JUnit trước đó](MINH_F05_F09_F10_2026-11-18.md): 7 audit backend, 6 HTTP fixtures và diễn tập HTTPS/session/backup/restore/restart local. Đây là kết quả đã chạy ở SHA ghi trong tài liệu, không phải lượt mới trên staging hoặc Fuji. Lượt mới không thay source app/backend production nên không chạy lại các diễn tập không bị ảnh hưởng.

Khoa nhận proof bundle và đối chiếu receipt/log/deployment trước khi Minh kiểm link explorer bằng API thật. Proof CASE_VERIFIED C03 lịch sử phải giữ đúng caseRef/tx/deployment; không gắn vào hồ sơ mới của E2E hoặc dùng làm proof hàng hóa. Hiện chưa có bằng chứng Goods Fuji mới trong refs đã đọc. Hosting public, cutover/recovery signer/worker và public smoke vẫn Not Run nếu chưa được bàn giao/phê duyệt.

Đề xuất bản quay demo: ghi rõ candidate/local và dữ liệu giả lập; quay hành trình UI ở trên, dừng tại business COMPLETED và timeline pending nếu worker chưa chạy. Nếu nhóm chọn CASE_VERIFIED-only Fuji, mở proof lịch sử ở phần riêng có provenance và nhãn giới hạn; hàng hóa chỉ có evidence local, không gọi full-chain Fuji PASS. Chốt với Dũng/Khoa SHA, phạm vi proof, lỗi còn mở trước khi quay; không tạo tx/deployment mới để có ảnh demo.
