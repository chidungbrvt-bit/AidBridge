# Chốt auditUrl theo bằng chứng — Task2, 15/11/2026

**Chuẩn hiện hành: `/api/public/cases/{publicCaseRef}/audit` theo D49/F02-A04, D56.1 và yêu cầu hiện tại.** publicCaseRef là ref bytes32 công khai ngẫu nhiên, `0x` +64 lowercase hex, đúng một prefix; không UUID case/campaign, không hash UUID nội bộ. Không tạo D57 hoặc endpoint/alias mới. Chuẩn URI đã kết luận; implementation owner và endpoint là phần chưa xong, không cần chờ người dùng đi hỏi thay.

## Bằng chứng đã đọc (không chạy tests/HTTP backend)

- main9c24630/dung0fc3e3d: D49/D56.1 chọn URI case; không có D57. API table T02 cũ chọn campaign URI, không có quyết định supersede D49. Đã đồng bộ bảng T02 trên khoa thành URI case, vẫn ghi planned/not implemented.
- minh/minh-d01 cùng0595149: `API_SPECIFICATION.md:178`, `MINH_F08_F09_2026-11-14.md:26` viện dẫn D57; Decision Log cùng snapshot không có D57. C64bb4e trước đó dùng D49,0595149 đổi sang campaign URI.
- Đọc [PR23](https://github.com/chidungbrvt-bit/AidBridge/pull/23) description/comments/reviews: OPEN, head0595149; không thấy approval đổi URI. Comment E2E trênc64bb4e chứng minh test ở phiên bản cũ, không phê duyệt decision D57. [PR26](https://github.com/chidungbrvt-bit/AidBridge/pull/26) MERGED vào minh, cùng head0595149, description vẫn nói link chờ route được chốt; không có comments/reviews phê duyệt D57. Chưa có implementation này trên main. GitHub search D57 trong repo chưa có issue/PR hit; giới hạn: không chứng minh mọi trao đổi ngoài GitHub chưa từng tồn tại.
- Main `DeliveryService.java:172` đã trả `/api/public/cases/0x` + case.publicRef hex + `/audit`. `CampaignMapper.java:55` còn trả campaign URI. Không thấy controller public audit/T01/T02 trên main/dung. Response có URL không chứng minh endpoint live.

## Hướng sửa Minh — trên PR23/nhánh của Minh, chưa áp dụng ở khoa

Vị trí dưới đây theo0595149, tìm bằng tên symbol nếu dòng dịch. Giữ một implementation UI chính; không cherry-pick lại R01 của Khoa.

| File/vị trí | Thay đổi cần thiết |
|---|---|
| `docs/fixtures/f02/goods-flow.types.ts:4,15` | Đổi CampaignAuditUrl sang kiểu phản ánh URI public case, ví dụ PublicCaseAuditUrl=`/api/public/cases/0x${string}/audit`; runtime validator vẫn bắt buộc64 lowercase hex. Không dùng UUID template cho publicCaseRef. |
| `frontend/src/lib/goods.ts:53–56` | DeliveryResponse nhận PublicCaseAuditUrl hoặc null; đổi isCampaignAuditUrl sang isPublicCaseAuditUrl với regex `^/api/public/cases/0x[0-9a-f]{64}/audit$`. Không coi URI D01 đúng D49 là legacy cần loại. Reject foreign/absolute URL, prefix kép, UUID hoặc sai độ dài. |
| `frontend/src/lib/api-client.ts:2,150` | Import/call validator mới; giữ URI D49 đúng dạng, null khi URI sai; FAILED luôn null. Shape hợp lệ chỉ giữ làm metadata, không bật link trước endpoint thực thi. |
| `frontend/tests/api-workflow.test.ts:31–44` | Đảo test hiện tại: giữ URI D49 đúng dạng, reject URI campaign/absolute/malformed; thêm FAILED null. Test này hiện giữ T02 và loại D49, cần sửa cùng validator. |
| `docs/fixtures/f02/goods-flow.mock.json`, `frontend/features/goods-mock/f02-snapshot.json`, `docs/fixtures/f02/verify-f02.mjs:39` | Khôi phục publicCaseRef fixture ngẫu nhiên ổn định và URI case ở mọi snapshot. Có thể tham khảo snapshotc64bb4e trước0595149; sửa chọn lọc, không restore cả file làm mất D01 mới. Checker so URI với publicCaseRef, không campaignId. |
| `frontend/features/goods-mock/delivery-review.ts:88` | D01 DELIVERED dùng cùng publicCaseRef của case fixture; FAILED vẫn null, không tạo URI từ delivery/receipt/campaign UUID. |
| `frontend/src/lib/mock-backend.ts:237` | Campaign mock trả URI cùng ref của owning case. Nếu case mock chưa có ref, lưu một random public ref ổn định trong process-memory riêng theo case; không dùng caseId/campaignId trực tiếp hoặc hash UUID. Không tạo endpoint mới để hợp thức hóa link. |
| `frontend/tests/e2e/campaign-audit.spec.ts`, `delivery-api.spec.ts` | Đồng bộ expected metadata theo D49; vẫn giữ guard link chưa available. Test fixture URI không được gọi là evidence endpoint thật hoạt động. |
| `docs/architecture/API_SPECIFICATION.md:178`, `docs/handoffs/MINH_F08_F09_2026-11-14.md:26` | Bỏ khẳng định “D57 chọn T02” chưa có evidence; đồng bộ D49/D56 và endpoint pending. Bảng T02 đã có bản sửa trên khoa để tham khảo khi tích hợp. |

Chạy unit validator/client + F02 parity/typecheck sau khi Minh áp dụng; chỉ test browser bị ảnh hưởng khi cần. Không chạy test cho code chưa sửa và không coi hướng sửa này là đã PASS PR23. Lượt hiện tại không sửa/push/comment vào PR23/26.

## Phần Dũng và trạng thái bàn giao

- Dũng đồng bộ `backend/aidbridge-backend/src/main/java/com/aidbridge/campaign/mapper/CampaignMapper.java:55` sang case.publicRef theo cùng cách D01 đang dùng, không chuyển ngược D01 sang campaign URI. Bổ sung regression DTO CampaignResponse/DeliveryResponse cùng owning-case ref khi triển khai; khoa không sửa backend ở lượt này.
- Dũng triển khai public audit projection/controller theo canonical URI và privacy/ownership/publication/pagination guards trong task API riêng. Không thêm hai endpoints để dung hòa; legacy T02 chỉ là spec lệch đang được sửa. Minh chỉ bật link khi có endpoint thực và được kiểm chứng; không dùng txHash hay URI string làm xác nhận chain.
- Đã cập nhật Decision Log/spec ở khoa; không sửa smart contract, UI Minh, DB hoặc blockchain. Không có blocker để commit kết luận này. Các việc còn lại là owner implementation, không phải thiếu căn cứ chọn URI; nếu có tài liệu phê duyệt D57 mới được cung cấp thì review riêng trước đổi chuẩn.
