# Bàn giao danh mục mặt hàng EN/VI mức 1

Ngày kiểm chứng 18/11/2026, Asia/Bangkok. Nhánh `codex/item-catalog`, tách từ `feat/ui-language-toggle` tại `bf8dbab1fb4c33f096c11f42c9f20970e69c42ed`. Implementation và các kiểm tra bên dưới đã hoàn tất, sẵn sàng review. Thay đổi đang ở working tree, chưa commit hoặc push.

Danh mục frontend gồm 33 mặt hàng, 12 nhóm, nhãn cho 13 mã đơn vị gồm mã lịch sử và ba mức ưu tiên cứu trợ có nhãn EN/VI. Tra cứu hỗ trợ trim, chữ hoa/thường, bỏ dấu tiếng Việt, `đ` và aliases của seed `Food`, `Hygiene`, `Water`. Tên hàng sử dụng `item()` và đơn vị sử dụng `unit()`. `t()` tiếp tục xử lý câu giao diện.

Form tạo và sửa dùng chung `NeedFields`, chọn theo nhóm bằng optgroup. Chọn một mục trong danh mục đặt `nameEn` và mã đơn vị vào state, hiển thị đơn vị theo ngôn ngữ và đặt readOnly. Payload JSON lấy từ state. Mặc định form tạo giữ Rice/kg/2 và Hygiene kit/kit/2. Nhánh Other cho phép nhập tên và đơn vị tự do. Validation phát hiện trùng tên chuẩn hoặc alias trước khi POST/PATCH, sử dụng FieldError và focusFirstError. Retry tiếp tục gửi body và idempotency key đã lưu.

Theo cập nhật của người dùng, gạo cố định kg, mì ăn liền và cháo ăn liền cố định box/thùng khi chọn từ danh mục. Nhóm EDUCATION chỉ có School supplies combo/Combo đồ dùng học tập, mã đơn vị combo. Bốn lựa chọn Notebooks, Pens, School bag và Textbooks được gỡ khỏi danh sách mới. Bản ghi cũ dùng các tên này tiếp tục hiển thị tên đã lưu qua fallback, tránh coi một vật dụng riêng lẻ là một combo. Form tạo mới mặc định Rice/kg/2. Hai browser tests EN/VI bổ sung xác minh payload cho cả mì, cháo và combo cùng đơn vị readOnly.

Lượt rà toàn bộ danh mục theo yêu cầu tiếp theo gộp vật dụng theo bộ cứu trợ. Gia vị gộp dầu ăn, nước mắm, muối và đường thành Seasonings/set, hiển thị Gia vị/bộ. Nước uống chỉ có Drinking water/box, hiển thị Nước uống/thùng. Các phần tử lẻ được bỏ khỏi lựa chọn mới. Tên lịch sử của phần tử lẻ dùng fallback giữ tên và đơn vị đã lưu, thay vì coi một chai nước mắm hay một bánh xà phòng là một bộ.

Cách gộp bổ sung được chọn trong lượt triển khai này để người dùng review gồm các nhóm sau.

| Bộ mới hoặc bộ được tinh gọn | Phần tử được gộp |
|---|---|
| Thực phẩm đóng hộp | Cá hộp và thịt hộp |
| Bộ xử lý nước | Vật tư khử khuẩn và thiết bị lọc nước |
| Bộ vệ sinh cá nhân | Xà phòng, kem/bàn chải, giấy vệ sinh, nước rửa tay |
| Bộ vệ sinh phụ nữ | Nhóm đồ vệ sinh phụ nữ từ lựa chọn băng vệ sinh cũ |
| Bộ quần áo ấm | Nhóm quần áo giữ ấm từ lựa chọn áo khoác cũ |
| Bộ chăn, chiếu, màn | Chăn, chiếu và màn |
| Bộ đồ dùng sinh hoạt | Xô và bình đựng nước |
| Bộ sơ cứu | Bộ sơ cứu, băng gạc và nước muối sinh lý |
| Bộ chăm sóc trẻ em | Tã/bỉm và khăn ướt |
| Bộ chăm sóc người cao tuổi | Đồ vệ sinh chăm sóc từ lựa chọn tã người lớn cũ |
| Bộ đèn pin, pin và còi cứu hộ | Đèn pin, pin và còi |
| Bộ vật tư che chắn | Bạt và dây thừng |
| Bộ đồ bảo hộ | Ủng và găng tay |
| Bộ dụng cụ nấu ăn | Nồi và bộ bát, đũa, thìa |

Giày dép được đặt tên chung Footwear, tính theo đôi. Áo mưa, áo phao, bếp mini, sạc dự phòng và đèn năng lượng mặt trời tính theo cái. Sữa công thức đúng độ tuổi giữ lựa chọn riêng theo lon. Thực phẩm mềm và lương khô chuyển sang thùng cho lựa chọn mới, bánh mì tính theo gói. Hai mã kit và set đều có nhãn tiếng Việt bộ. Các mức ưu tiên của bộ dùng mức khẩn hơn trong các phần tử được gộp.

Danh sách hiện hành đầy đủ được ghi dưới đây. Đây là đơn vị lựa chọn mới, bản ghi lịch sử giữ đơn vị đã lưu.

| Mặt hàng | Đơn vị |
| Gạo | kg |
| Mì ăn liền | thùng |
| Cháo ăn liền | thùng |
| Lương khô | thùng |
| Bánh mì | gói |
| Thực phẩm đóng hộp | thùng |
| Sữa hộp | thùng |
| Gia vị | bộ |
| Nước uống | thùng |
| Bộ xử lý nước | bộ |
| Bộ vệ sinh cá nhân | bộ |
| Bộ vệ sinh phụ nữ | bộ |
| Bộ quần áo (mới) | bộ |
| Áo mưa | cái |
| Bộ quần áo ấm | bộ |
| Giày dép | đôi |
| Bộ chăn, chiếu, màn | bộ |
| Bộ đồ dùng sinh hoạt | bộ |
| Bộ sơ cứu | bộ |
| Khẩu trang | thùng |
| Bộ chăm sóc trẻ em | bộ |
| Sữa công thức (đúng độ tuổi) | lon |
| Bộ chăm sóc người cao tuổi | bộ |
| Thực phẩm mềm | thùng |
| Bộ đèn pin, pin và còi cứu hộ | bộ |
| Sạc dự phòng | cái |
| Đèn năng lượng mặt trời | cái |
| Áo phao | cái |
| Bộ vật tư che chắn | bộ |
| Bộ đồ bảo hộ | bộ |
| Bộ dụng cụ nấu ăn | bộ |
| Bếp gas mini | cái |
| Combo đồ dùng học tập | combo |

Quyết định tương thích dữ liệu cũ dùng tên và đơn vị đã lưu làm giá trị gốc. Trong form sửa, alias được nhận diện và chọn sẵn mục tương ứng. Chỉ chọn mặt hàng mới mới thay tên và đơn vị bằng giá trị danh mục. Ví dụ Food/PACKAGE được hiển thị thành Lương khô/gói ở VI, giữ Food/PACKAGE ở EN và trong state cũ. Cách này giữ ý nghĩa quantity khi đơn vị lịch sử khác đơn vị mặc định của danh mục. Other giữ nguyên chuỗi người dùng nhập. Backfill dữ liệu thuộc mức 2.

Trang donor nhóm bảng tiến độ theo nhóm danh mục và hiển thị nhãn ưu tiên. Các bảng chi tiết, campaign, receipt, delivery, review, dispute, lịch sử donor và cancellation sử dụng helpers EN/VI. campaign-panel tiếp tục render qua GoodsProgressTable đã cập nhật. Aria-label Item {n} name/unit/quantity và Remove item {n} được giữ ở cả hai ngôn ngữ. Aria-label nghiệp vụ ở EN tiếp tục chứa tên và mã đơn vị gốc, kể cả Food, PACKAGE và KIT.

Các file implementation mới và thay đổi nằm trong frontend.

- `src/lib/item-catalog.ts` mới, chứa dữ liệu, nhãn và hàm tra cứu.
- `src/components/cases/need-fields.tsx` mới, chứa fields dùng chung và validation trùng.
- `src/components/i18n-provider.tsx` bổ sung helpers item/unit.
- `src/lib/i18n/vi.ts` bổ sung năm câu giao diện cho lựa chọn, Other và validation.
- `src/components/cases/create-case.tsx` và `src/components/cases/edit-case.tsx` dùng fields chung và validation.
- `src/components/cases/case-detail.tsx` hiển thị hàng và đơn vị theo ngôn ngữ.
- `src/components/campaigns/goods-progress-table.tsx` hiển thị hàng và đơn vị theo ngôn ngữ.
- `src/components/goods/receipt-panel.tsx` và `src/components/goods/delivery-panel.tsx` cập nhật tên, đơn vị và aria-label hiển thị.
- `src/components/goods/delivery-review-panel.tsx` và `src/components/goods/dispute-review-panel.tsx` cập nhật bảng review.
- `features/donor/DonorApp.tsx` thêm nhóm hàng, nhãn ưu tiên và hiển thị EN/VI.
- `features/donor/CancellationForm.tsx` cập nhật nhãn hàng và đơn vị.
- `src/app/globals.css` sửa selector mobile từ first-of-type sang nth-child(2), để chỉ field chọn mặt hàng chiếm cả hàng sau khi field này chuyển thành div.

Các file test mới và thay đổi gồm những file sau.

- `tests/item-catalog.test.ts` mới, mười một unit tests gồm lookup, aliases, đơn vị, fallback và tính hợp lệ của toàn bộ danh mục.
- `tests/e2e/item-catalog.spec.ts` mới, mười hai browser tests gồm hai ngôn ngữ, payload chuẩn, các bộ cứu trợ với đơn vị cố định, trùng/alias, Other, lỗi field từ API, dữ liệu lịch sử, nhóm hàng donor và bố cục mobile VI.
- `tests/e2e/session-cases.spec.ts` cập nhật thao tác chọn và các assert được liệt kê dưới đây.
- `tests/e2e/edit-resubmit.spec.ts` cập nhật thao tác chọn và assert chặn trùng.
- `tests/e2e/campaign-audit.spec.ts` cập nhật thứ tự nhóm hàng donor.
- `tests/e2e/delivery-api.spec.ts` cập nhật cách xác minh delivery ID hiện có.
- `tests/e2e/donor-real.spec.ts` chuyển fixture Food/PACKAGE và Hygiene/KIT sang Other rồi nhập tên/đơn vị cũ.
- `ITEM_CATALOG_HANDOFF.md` là file bàn giao này.

Các assert cũ được chỉnh với lý do cụ thể.

| File và assert | Điều chỉnh và lý do |
|---|---|
| session-cases, trạng thái Request changes | CHANGES REQUESTED thành Changes requested, khớp nhãn readable đã có ở labels.ts trên nhánh gốc. |
| session-cases, trạng thái Reject | REJECTED thành Rejected, khớp nhãn readable đã có trên nhánh gốc. |
| session-cases, create trùng cần HTTP 400 | Thay bằng số POST bằng 0, vì yêu cầu mới chặn trùng tại frontend. Giữ assert focus và duplicate need. Test mới server field validation vẫn kiểm tra HTTP 400 và focus từ lỗi API. |
| edit-resubmit, edit trùng cần HTTP 400 | Thay bằng số PATCH bằng 0. Giữ assert focus, duplicate need, version và needs cũ không đổi, cùng lần sửa thành công sau đó. |
| campaign-audit, rowheader trên trang donor | Thứ tự Hygiene kit/Rice thành Rice/Hygiene kit theo nhóm Food trước Hygiene. Bảng draft của staff tiếp tục kiểm tra thứ tự cũ. |
| delivery-api, success chứa deliveryId trong text | Chuyển sang toHaveAttribute(data-delivery-id, deliveryId). Nhánh gốc đã ẩn ID khỏi text và giữ data attribute này. Các assert payload, số lần đọc, số lần ghi, CSRF, key/body replay và stock vẫn giữ. |

Test danh mục chọn bộ đèn pin, pin và còi cứu hộ thay cho đèn pin lẻ, rồi chọn lại gạo để kiểm tra thay đổi đơn vị. Các assert mới kiểm tra những phần tử đã gộp vắng khỏi danh sách lựa chọn và tên lịch sử vẫn được giữ nguyên khi hiển thị.

Thao tác fill tên ở session-cases và edit-resubmit chuyển sang selectOption. Đơn vị được autofill nên bỏ thao tác fill đơn vị. Trong donor-real, chọn Other rồi điền tên/đơn vị để giữ fixture và các assert Food/PACKAGE, Hygiene/KIT hiện hữu.

Kết quả kiểm chứng thực tế dùng Node 24.19.0, dependencies có sẵn và Chrome cài trên máy. Server production cục bộ ở 127.0.0.1:3106, Playwright một worker. Kiểu CRLF của các file cũ được giữ nguyên. Package manifests và lockfiles giữ nguyên.

| Lệnh hoặc project | Kết quả |
|---|---|
| `npm run typecheck` | PASS, exit 0 |
| `npm test` | 88 pass, 0 fail, 0 skipped, gồm 11 test danh mục mới |
| `npm run build`, NEXT_PUBLIC_API_MODE=mock | PASS, exit 0, bản cuối gồm CSS mobile |
| `npm run build`, NEXT_PUBLIC_API_MODE=real | PASS, exit 0 |
| `npx --no-install playwright test --project=mock` | 36 pass, 0 fail, 0 skipped, gồm 12 test danh mục mới |
| Playwright receipt-api | 1 pass, 0 fail, 0 skipped |
| Playwright delivery-api | 4 pass, 0 fail, 0 skipped |
| Playwright audit-api | 2 pass, 0 fail, 0 skipped |
| Playwright donor-api | 14 pass, 0 fail, 0 skipped |
| `git diff --check` | PASS |

Bốn project real-mode được chạy cùng lệnh dưới đây trên production server real-mode. Tổng 21 pass. Cộng mock là 57 browser tests pass.

```powershell
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:3106'
$env:PLAYWRIGHT_CHROME_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
npx --no-install playwright test --project=receipt-api --project=delivery-api --project=audit-api --project=donor-api
```

Để tái hiện, build và start cùng API mode trong thư mục frontend, chạy Playwright ở terminal thứ hai. Dừng server trước khi đổi mode và build lại.

```powershell
$env:NEXT_PUBLIC_API_MODE = 'mock'
npm run build
npm start -- --port 3106
```

Lượt thử đầu trên dev server real-mode phát sinh GET lặp do React Strict Mode, làm fail hai assert số lần đọc của delivery-api. Chạy production server giải quyết khác biệt đó với nguyên các assert số lần đọc. Các assert cũ về thứ tự nhóm, nhãn trạng thái và delivery ID được sửa theo bảng trên. Lượt đầy đủ sau cùng của từng project đều PASS.

HTTP contract projects dùng browser client real-mode cùng API stubs của Playwright. Spring Boot/PostgreSQL/Fuji E2E và project donor-real chưa chạy trong task này. File donor-real đã typecheck, cần môi trường backend riêng để chạy. Cross-owner review còn chờ nhóm.

Phạm vi giao là mức 1 frontend. Backend, migration, API contract và dependency giữ nguyên. Mức 2 gồm item_types/item_categories trong DB, Flyway V19, alias backfill, expiration_date và condition được hoãn theo yêu cầu.
