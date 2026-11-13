# Daily Roadmap

Ngày tạo:03/11/2026. Cập nhật:04/11/2026. Người duy trì:Minh (F). Các allocation dưới đây là kế hoạch, không actual completion; tiến độ thực tại Task Backlog: B01 In Review, B02/B03 In Progress. [B01 baseline](../architecture/B01_BASELINE.md) đã thống nhất tên gọi và implementation gaps, chưa có cross-owner acceptance. D26/D27 user-confirmed:18/11 đủ P0,20/11 stable demo,22/11 assets ready,25/11 submission limit. Timezone Asia/Bangkok.

## Lịch tăng tốc đề xuất đến hết 15/11/2026

**Phạm vi người dùng xác nhận.** Lịch 09–15/11/2026 theo Asia/Bangkok. Dũng tự làm Backend theo lịch riêng. Trong phần việc chung, Dũng góp Blockchain/Fuji và ML. ML do Dũng và Minh thực hiện. Khoa và Minh bắt đầu song song. Đích của lịch là sáu P0 đã ghép, có bằng chứng Fuji thật và smoke test công khai. Full regression và hồ sơ có mốc riêng. Xem [D47](DECISION_LOG.md#d47--lịch-tăng-tốc-trước-1611).

Theo [backlog](TASK_BACKLOG.md), C01–C10 và F01–F10 có tổng estimate 92h, trong đó 15h Java/DB/REST dự kiến chuyển sang lịch Backend riêng của Dũng (C05 3h, C07 4h, C08 4h, C09 2h, C10 2h). Còn 77h việc chung và 4h ML offline, chia **27h/người**. Giữ nguyên estimate gốc và người nghiệm thu mỗi task. Phần chia dưới đây là dự toán.

| Phần việc | Dũng | Khoa | Minh |
|---|---:|---:|---:|
| Blockchain/Fuji | 25h · Contract, test local, hỗ trợ kiểm tra audit/Fuji | 9h · ABI, Fuji pilot, signing/nonce, receipt/log và review chain | |
| Frontend/Product/Release | | 18h · Mock/fixture, UI cam kết và nhận/giao hàng, smoke test và rollback | 25h · App shell, đăng nhập, hồ sơ, campaign, audit, xác nhận và triển khai |
| ML offline | 2h · Mô hình và hướng dẫn chạy lại | | 2h · Dữ liệu giả lập, rule baseline và đánh giá |
| **Tổng việc chung** | **27h** | **27h** | **27h** |

25h Blockchain/Fuji của Dũng gồm 23h task C và 2h kiểm tra bằng chứng, dữ liệu audit trong F05. Minh làm giao diện F05. Để cân giờ, Khoa nhận thêm hai màn frontend riêng là cam kết và nhận/giao hàng. Khoa chịu trách nhiệm nghiệm thu task C, Minh chịu trách nhiệm nghiệm thu task F.

**Cách làm song song.** Hai người lấy DTO, mã lỗi và trạng thái từ [API Specification](../architecture/API_SPECIFICATION.md). Minh bắt đầu app shell, đăng nhập và hồ sơ ngay bằng mock từ hợp đồng có sẵn. Khoa làm fixture dùng chung, màn cam kết và màn nhận/giao hàng theo cùng DTO. Mỗi người làm ở các route/component đã chia. Minh sở hữu API client dùng chung. Khoa dùng mock cục bộ theo DTO để phát triển màn của mình rồi nối client tại phiên ghép. Timeline của Minh có thể kiểm tra `PENDING`, `FAILED` và `CONFIRMED` bằng fixture; Khoa chạy contract/RPC độc lập. Nếu thiếu trường hoặc chưa có endpoint, ghi chỗ vướng cho Dũng và tiếp tục với mock. Kiểm thử luồng thật cần API của Dũng, các màn hình cần thiết và receipt/event Fuji đã được xác minh.

| Ngày | Dũng · Blockchain/Fuji và ML | Khoa · Blockchain, Frontend và Release | Minh · Frontend và ML |
|---|---|---|---|
| T2 09/11 | **6h** · C01 3h làm mẫu sự kiện và đối chiếu ABI, C02 3h dựng Hardhat và chạy smoke local khi đủ điều kiện. | **5h** · C01 1h review mapping và chốt ABI với Dũng, F02 4h chuẩn bị wireframe và fixture theo API spec. | **6h** · F03 4h app shell và đăng nhập, F04 2h dựng hồ sơ/xác minh bằng mock từ API spec. |
| T3 10/11 | **5h** · C03 làm contract xác minh và test sai quyền, sai tổ chức, trùng event. | **5h** · F01 2h ghi giả định sản phẩm, F07 3h dựng màn cam kết/lịch sử trên route riêng với fixture. | **5h** · F04 4h hoàn tất hồ sơ/xác minh và ghép API nếu sẵn sàng, F05 1h dựng timeline với fixture. |
| T4 11/11 | **5h** · C05 1h đối chiếu event và bằng chứng xác minh, C06 3h mở rộng contract, F05 1h so khớp dữ liệu audit với sự kiện. | **5h** · C04 2h Fuji pilot sau khi C03 qua test và đủ điều kiện, C05 2h đối chiếu receipt/event, F07 1h làm thao tác hủy cam kết. | **4h** · F05 1h test timeline bằng fixture và thay bằng bằng chứng Fuji khi có, F06 3h dựng campaign nhiều mặt hàng. |
| T5 12/11 | **4h** · C06 3h hoàn tất contract và test chuyển trạng thái, C07 1h kiểm tra restart/gửi lại cùng giao dịch. | **4h** · C07 2h kiểm tra ký và nonce, F07 1h ghép API cam kết khi sẵn sàng, F08 1h bắt đầu màn nhận/giao hàng với mock. | **4h** · F06 3h hoàn tất campaign, ML 1h chuẩn bị dữ liệu giả lập và rule baseline. |
| T6 13/11 | **3h** · C08 2h test replay, timeout và receipt sai event; ML 1h chạy Logistic Regression offline. | **4h** · C08 1h rà điều kiện receipt/log và khôi phục, F08 3h hoàn tất màn nhận từng phần và ghi giao hàng. | **3h** · F09 2h dựng xác nhận/tranh chấp trên route riêng với mock, ML 1h đối chiếu kết quả mô hình với rule baseline. |
| T7 14/11 | **3h** · C09 1h đối chiếu trạng thái audit với dữ liệu chain, C10 1h ghép mốc hàng hóa với sự kiện, F05 1h rà receipt/explorer hiển thị qua API. | **2h** · F08 1h ghép nhận/giao với API thật, C10 1h kiểm tra receipt/event hàng hóa. | **3h** · F09 ghép API thật, kiểm tra xác nhận độc lập, dispute và quyền đọc audit. |
| CN 15/11 | **1h** · ML ghi kết quả, giới hạn dữ liệu và cách chạy lại sau khi hoàn tất bàn giao P0. | **2h** · F10 kiểm tra luồng demo, thu bằng chứng Fuji và ghi bước rollback. | **2h** · F10 triển khai khi đủ điều kiện và chạy smoke test công khai cùng Khoa. |
| **Tổng việc chung** | **27h** | **27h** | **27h** |

**Các mốc ghép thật.** Đến 11/11, cần API auth/hồ sơ/xác minh, job B05 và receipt Fuji để chạy UI → xác minh độc lập → audit. Đến 12/11, cần API campaign/pledge để ghép luồng cam kết. Đến 14/11, cần API nhận/giao/xác nhận và audit để ghép nhiều mặt hàng → nhận → giao → xác nhận độc lập, kiểm tra dispute. Đến 15/11, chạy smoke test công khai khi đủ điều kiện. Nếu hụt mốc tích hợp, dự báo lại đủ sáu P0. Khi phải giữ mốc demo, ưu tiên [Minimum Demo](MVP_SCOPE.md#ba-scope-levels) và ghi rõ phần P0 còn thiếu. Mỗi phần vẫn cần test và review tương ứng.

**Điều kiện về giờ.** D27 có 22,5h/người từ 09–15/11. Cần thêm 4,5h/người cho việc chung nếu estimate còn nguyên. Theo dự toán, Dũng còn 44h Backend riêng (29h B06–B10 + 15h từ các task C) cộng phần B01–B05 còn lại. Tổng tải của Dũng từ 71h nếu toàn bộ phần đó còn lại. Chốt mốc theo tiến độ và giờ thực tế. ML là thử nghiệm offline, có thể dời khi trễ bàn giao P0. Pilot và tích hợp sản phẩm theo [D46](DECISION_LOG.md#d46--aid-matching-scope-và-pilot-gates).

### Bảng công việc ngày đầu — thứ Hai 09/11/2026

| Phần việc | Người làm | Task | Giờ | Việc cần làm | Đầu ra |
|---|---|---|---:|---|---|
| Blockchain · Sự kiện | Dũng | C01 | 3h | Làm mẫu `CASE_VERIFIED`, đối chiếu event ID, org ref, sequence và dữ liệu được đưa lên chain. Hẹn Khoa review trong ngày. | Mẫu input/output và draft ABI/event cho contract. |
| Blockchain · Môi trường local | Dũng | C02 | 3h | Chuẩn bị Hardhat và tài khoản thử nghiệm. Chạy compile/smoke local khi đã có quyền thực hiện. | Lệnh và kết quả kiểm tra hoặc blocker cụ thể. |
| Blockchain · Review ABI | Khoa | C01 | 1h | Review ABI và mẫu sự kiện với Dũng. | Mapping thống nhất để làm contract và fixture audit. |
| Frontend · Fixture và wireframe | Khoa | F02 | 4h | Chuẩn bị fixture theo API spec cho hồ sơ, audit, cam kết và nhận/giao. Mở đầu bằng trạng thái và mã lỗi dùng trong các màn phụ trách. | Fixture và wireframe dùng cho F07/F08 và phiên ghép. |
| Frontend · Đăng nhập | Minh | F03 | 4h | Dựng app shell, form và API client trong `frontend/`. Tự tạo mock tối thiểu từ API spec, gọi API auth thật khi sẵn sàng và hiển thị quyền từ `/api/auth/me`. | Form login/logout và menu chạy bằng mock hoặc kết quả smoke với API thật. |
| Frontend · Hồ sơ và xác minh | Minh | F04 | 2h | Dựng danh sách, form và giao diện xác minh từ DTO có sẵn. | Giao diện với dữ liệu mẫu, trạng thái tải và lỗi. |

### Bảng công việc ngày 10/11/2026

| Phần việc | Người làm | Task | Giờ | Việc cần làm | Đầu ra và điều kiện bàn giao |
|---|---|---|---:|---|---|
| Blockchain · Contract xác minh | Dũng | C03 | 3h | Viết luồng ghi xác minh với relayer đúng tổ chức, mã sự kiện duy nhất và sequence hợp lệ theo ABI đã chốt. | Contract compile được và test đường thành công kiểm tra event được phát. |
| Blockchain · Test quyền | Dũng | C03 | 2h | Test caller sai tổ chức, quyền relayer bị thu hồi, event trùng hoặc sai thứ tự. | Kết quả test local và ABI gửi Khoa. Khoa dùng kết quả này trước Fuji pilot 11/11. |
| Product · Kịch bản thử | Khoa | F01 | 2h | Chọn kịch bản demo với dữ liệu giả lập. Ghi giả định về vai trò người nhận, người giao và người xác nhận. | Checklist tình huống và danh sách giả định còn chờ xác thực. |
| Frontend · Cam kết đóng góp | Khoa | F07 | 3h | Dựng route cam kết nhiều mặt hàng và lịch sử của người đóng góp theo G01/G02, dùng fixture riêng. | Form, lịch sử và trạng thái nhập sai/thất bại chạy bằng mock. |
| Frontend · Hồ sơ và xác minh | Minh | F04 | 4h | Hoàn thiện danh sách, chi tiết, form và thao tác duyệt/từ chối/yêu cầu bổ sung. Ghép API hồ sơ và xác minh nếu đã sẵn sàng. | Người tạo và người duyệt dùng hai tài khoản trong kịch bản thử. Nếu đang dùng mock, ghi rõ mức đã chạy. |
| Frontend · Audit timeline | Minh | F05 | 1h | Dựng timeline và các trạng thái `PENDING`, `FAILED`, `CONFIRMED` từ dữ liệu mẫu. | Có thể xem cả ba trạng thái bằng fixture trước khi ghép Fuji. |

### Bảng công việc ngày 11/11/2026

| Phần việc | Người làm | Task | Giờ | Việc cần làm | Đầu ra và điều kiện bàn giao |
|---|---|---|---:|---|---|
| Blockchain · Xác minh trên Fuji | Dũng | C05 | 1h | So sánh mẫu `CASE_VERIFIED` với receipt và event từ luồng B05 sau khi Khoa chạy pilot. | Ghi kết quả khớp event ID, org ref và commitment, hoặc lý do luồng chưa ghép được. |
| Blockchain · Contract hàng hóa | Dũng | C06 | 3h | Thêm các mốc cam kết, giảm cam kết, nhận và giao hàng. Giữ liên kết cùng tổ chức và hồ sơ theo ABI. | Các mốc đầu compile được và có test cho đường thành công. |
| Blockchain · Đối chiếu audit | Dũng | F05 | 1h | So sánh trạng thái audit và mã giao dịch trả từ API với bằng chứng của C05. | Bảng trạng thái kỳ vọng để Minh đối chiếu timeline, kèm tx hash khi đã xác minh. |
| Blockchain · Fuji pilot | Khoa | C04 | 2h | Kiểm tra chain ID, cấu hình relayer và kết quả C03. Triển khai pilot khi đã được phép. | Địa chỉ contract, receipt triển khai, phiên bản ABI và kết quả đọc lại quyền relayer. |
| Blockchain · Receipt và event | Khoa | C05 | 2h | Với job B05, kiểm tra receipt thành công, đúng chain/contract và event tương ứng. | Một bằng chứng Fuji cho xác minh hoặc trạng thái chờ/lỗi có lý do. Bàn giao dữ liệu đã kiểm tra cho Minh. |
| Frontend · Hủy cam kết | Khoa | F07 | 1h | Thêm form hủy phần chưa nhận, hiển thị phản hồi xung đột từ G03. | Kịch bản hủy thành công và lỗi khi hủy phần đã nhận chạy với fixture. |
| Frontend · Audit timeline | Minh | F05 | 1h | Kiểm tra hiển thị đủ ba trạng thái chain. Khi có bằng chứng đã xác minh, nối API audit và kiểm tra liên kết explorer. | Timeline hoạt động với fixture, kèm kết quả tích hợp thật nếu API và Fuji sẵn sàng. |
| Frontend · Campaign | Minh | F06 | 3h | Dựng màn nháp, danh sách mặt hàng, công bố và xem campaign dựa trên DTO hiện có. | Có thể xem luồng nháp → công bố và trạng thái lỗi bằng mock. |

### Bảng công việc ngày 12/11/2026

| Phần việc | Người làm | Task | Giờ | Việc cần làm | Đầu ra và điều kiện bàn giao |
|---|---|---|---:|---|---|
| Blockchain · Xác nhận và tranh chấp | Dũng | C06 | 3h | Hoàn thiện mốc xác nhận, mở và giải quyết tranh chấp. Test xác nhận khi thiếu delivery, trùng event hoặc còn tranh chấp. | Contract đủ các mốc theo C06, ABI cập nhật và kết quả test gửi Khoa. |
| Blockchain · Khôi phục giao dịch | Dũng | C07 | 1h | Chạy kịch bản restart sau khi đã lưu giao dịch ký. Đối chiếu event ID và hash trước/sau khôi phục. | Bằng chứng retry dùng cùng giao dịch hoặc log lỗi để Khoa xử lý. |
| Blockchain · Ký và nonce | Khoa | C07 | 2h | Kiểm tra nonce và điều kiện một giao dịch đang gửi trên mỗi signer. Kiểm tra hash và dữ liệu đã ký được lưu trước broadcast. | Kết quả test đường gửi và khôi phục với đầu ra Backend của Dũng. |
| Frontend · Ghép cam kết | Khoa | F07 | 1h | Nối màn cam kết vào client chung khi API sẵn sàng. Kiểm tra gửi lại yêu cầu và thông báo khi quá lượng. | Kết quả G01–G03 qua API thật. Nếu API còn vướng, giữ màn chạy mock và ghi chỗ còn thiếu. |
| Frontend · Nhận và giao | Khoa | F08 | 1h | Tạo khung màn nhận hàng và màn ghi giao hàng với fixture R01/D01. | Hai màn có thể chạy độc lập với trang campaign của Minh. |
| Frontend · Ghép campaign | Minh | F06 | 3h | Nối API tạo nháp/công bố/xem campaign khi sẵn sàng. Kiểm tra nhiều mặt hàng, trạng thái lỗi và thông tin công khai. | Một campaign nhiều mặt hàng để ghép luồng cam kết. Giữ fixture nếu chưa có bản chạy thật. |
| ML · Dữ liệu giả lập | Minh | ML | 1h | Tạo fixture có nhãn và kết quả theo luật để so sánh. Ghi ý nghĩa các trường và cách tạo dữ liệu. | Bộ dữ liệu, schema và rule baseline bàn giao cho Dũng trước 13/11. |

### Bảng công việc ngày 13/11/2026

| Phần việc | Người làm | Task | Giờ | Việc cần làm | Đầu ra và điều kiện bàn giao |
|---|---|---|---:|---|---|
| Blockchain · Retry và replay | Dũng | C08 | 2h | Chạy ca timeout, receipt sai event và phát lại log. So sánh kết quả với trạng thái audit kỳ vọng. | Timeout giữ `PENDING`, revert đi theo trạng thái lỗi, log sai ngữ cảnh bị loại. Ghi bằng chứng replay không tạo sự kiện trùng. |
| ML · Mô hình offline | Dũng | ML | 1h | Chạy Logistic Regression trên fixture Minh bàn giao. Giữ đầu ra để so trên cùng bộ mẫu với rule baseline. | Dự đoán và lệnh chạy bàn giao Minh trong ngày. |
| Blockchain · Review đối soát | Khoa | C08 | 1h | Kiểm tra điều kiện khớp chain, contract, receipt và event khi khôi phục trạng thái từ log. | Kết quả review ca timeout, receipt sai ngữ cảnh và replay. |
| Frontend · Nhận và giao | Khoa | F08 | 3h | Hoàn thiện nhận từng phần, giao thành công và giao thất bại. Tách trạng thái nhận hàng khỏi giao và xác nhận. | Giao diện với fixture thành công, vượt số lượng và giao thất bại. Sẵn sàng ghép API ngày 14/11. |
| Frontend · Xác nhận và tranh chấp | Minh | F09 | 2h | Dựng màn xác nhận bằng ID giao hàng giả lập. Thêm trạng thái bị từ chối khi tự xác nhận hoặc đang tranh chấp. | Màn chạy với fixture D02/S01/S03 trong lúc Khoa hoàn thiện F08. |
| ML · Đánh giá | Minh | ML | 1h | Chuẩn bị bảng đối chiếu trước, sau đó điền kết quả mô hình của Dũng trên cùng bộ mẫu. Ghi các ca sai và giới hạn dữ liệu giả lập. | Kết quả theo luật và theo mô hình để Dũng đưa vào ghi chú ngày 15/11. |

### Bảng công việc ngày 14/11/2026

| Phần việc | Người làm | Task | Giờ | Việc cần làm | Đầu ra và điều kiện bàn giao |
|---|---|---|---:|---|---|
| Blockchain · Audit | Dũng | C09 | 1h | Đối chiếu trạng thái, hash và block từ chain với dữ liệu audit nội bộ/công khai. Rà đầu ra công khai theo hợp đồng API. | Mẫu audit đã kiểm tra cho Minh, kèm chỗ sai cần sửa nếu có. |
| Blockchain · Mốc hàng hóa | Dũng | C10 | 1h | Đối chiếu snapshot nhận/giao/xác nhận/tranh chấp với event tương ứng và kiểm tra replay. | Danh sách event mong đợi, giữ nguyên số lượng nghiệp vụ khi retry. |
| Blockchain · Bằng chứng cho timeline | Dũng | F05 | 1h | Soát tx hash và explorer link mà API cung cấp để hiển thị trên timeline. | Ghi bằng chứng đúng mạng và đúng event cho buổi ghép demo. |
| Frontend · Ghép nhận/giao | Khoa | F08 | 1h | Nối R01/D01 vào API thật. Chạy thử nhận từng phần và tạo delivery để Minh có ID ghép xác nhận. | Một delivery thật đủ điều kiện xác nhận, hoặc blocker và fixture để Minh tiếp tục. |
| Blockchain · Receipt hàng hóa | Khoa | C10 | 1h | Kiểm tra receipt/event trên Fuji cho các mốc đang ghép và đối chiếu với snapshot. | Bộ bằng chứng Fuji cho các mốc đã chạy, hoặc ghi rõ mốc còn thiếu bằng chứng. |
| Frontend · Ghép xác nhận và audit | Minh | F09 | 3h | Nối D02/S01/S03 và timeline với API thật. Kiểm tra tài khoản độc lập, lỗi tự xác nhận, tranh chấp chặn hoàn tất và quyền xem audit. | Kịch bản nhiều mặt hàng chạy từ nhận → giao → xác nhận, kèm kết quả các ca bị từ chối. |

### Bảng công việc ngày 15/11/2026

| Phần việc | Người làm | Task | Giờ | Việc cần làm | Đầu ra và điều kiện bàn giao |
|---|---|---|---:|---|---|
| ML · Ghi lại thử nghiệm | Dũng | ML | 1h | Sau bàn giao P0, gom cấu hình, lệnh chạy, kết quả và giới hạn đánh giá trên dữ liệu giả lập. | Ghi chú để chạy lại thử nghiệm offline. |
| Release · Smoke và rollback | Khoa | F10 | 2h | Chuẩn bị checklist chạy demo và rollback. Khi Minh có bản công khai, chạy kịch bản hàng hóa và lưu bằng chứng Fuji đã xác minh. | Checklist, kết quả từng bước, receipt/event và điều kiện rollback. |
| Release · Demo công khai | Minh | F10 | 2h | Khi đủ điều kiện và đã được phép, triển khai rồi kiểm tra HTTPS/session, truy cập dữ liệu riêng tư và luồng demo chung với Khoa. | URL demo và kết quả smoke, hoặc blocker cùng trạng thái từng P0 nếu chưa thể triển khai. |

## Gói demo ML (D51) — tính riêng, ngoài 168h

Cập nhật 11/11/2026. User yêu cầu một bản demo ML nộp kèm BTC, ghi rõ chỉ là bản demo và cần thời gian huấn luyện để thành bản chính thức. Gói này thay mục "ML offline" 4h của D47: tổng 9h (Dũng 7h, Minh 1,5h, Khoa 0,5h), tức thêm 5h so với D47 (Dũng +5h, Minh −0,5h, Khoa +0,5h). Ngày 12/11 Dũng nhận ML-1 từ Minh để giảm tải cho Minh; quy tắc cắt và tổng giờ không đổi. Giờ thêm lấy từ dự phòng, không tăng quỹ 64h/người; cần xác nhận lại khi chốt actuals. Gói nằm ngoài sáu P0 và ngoài Definition of Done của P0 (xem [D51](DECISION_LOG.md#quyết-định)).

| Task | Người | Giờ | Ngày | Việc cần làm | Đầu ra |
|---|---|---:|---|---|---|
| ML-1 | Dũng | 3h | 12–13/11 | Kiểm tra Python và thư viện trên máy. Viết đặc tả bài toán, đặc trưng không PII, nhãn và thước đo. Dựng bộ sinh dữ liệu giả lập quy mô rộng và luật cơ sở. | Đặc tả, bộ sinh dữ liệu, dữ liệu mẫu, luật cơ sở |
| ML-2 | Dũng | 3h | 13–15/11 | Huấn luyện Logistic Regression, so với luật cơ sở. Chạy nhiều seed và kiểm định chéo, phân tích ca sai, chỉ số theo nhóm giả lập. Đóng gói thành một lệnh chạy lại. | Kết quả, script, test pipeline |
| ML-3 | Dũng | 1h | 20/11 | Ghi giới hạn trung thực và phần "để thành bản chính thức cần gì". | Tài liệu giới hạn và lộ trình |
| ML-4 | Minh | 1,5h | 16–17/11 | Trang demo tĩnh hiển thị điểm gợi ý tính sẵn, có biểu ngữ DEMO, không đọc hay ghi backend. | Trang demo |
| ML-5 | Khoa | 0,5h | 20/11 | Rà quyền riêng tư: không PII, không lên chain, không tự duyệt (BR-02). Thêm bước vào kịch bản demo. | Biên bản rà soát ngắn |

**Tiêu chí "chạy ổn" (mục tiêu đề xuất, chưa chạy lần nào; ngưỡng chỉnh sau lần chạy đầu):**

1. **Quy mô dữ liệu:** khoảng 10.000 hồ sơ giả lập, 3 hồ sơ tổ chức giả lập có phân phối khác nhau, nhóm ưu tiên cao 10–20%, nhiễu nhãn khoảng 5%, thiếu giá trị 2–5%. Seed cố định, không có cột PII.
2. **Tái lập:** cùng seed cho cùng dữ liệu và cùng chỉ số. Một lệnh chạy từ bản checkout sạch. Thời gian chạy mục tiêu dưới 2 phút trên laptop.
3. **Ổn định:** 5 seed nhân kiểm định chéo 5 fold, báo cáo trung bình ± độ lệch chuẩn của mô hình và luật cơ sở. Ngưỡng đề xuất: độ lệch chuẩn AUC dưới 0,02.
4. **Chịu lỗi dữ liệu:** pipeline xử lý thiếu giá trị, mất cân bằng lớp và cột lạ. Schema sai thì dừng với thông báo rõ.
5. **Lát cắt:** chỉ số theo nhóm giả lập (vùng, kiểu hộ) để thấy chênh lệch lớn.
6. **Kiểm thử tự động:** test cho bộ sinh (seed, phạm vi giá trị), schema, không PII và một smoke test cả pipeline.
7. **Trung thực:** mọi nơi hiển thị điểm đều có chữ DEMO. Không dùng cụm "phát hiện gian lận" và không công bố độ chính xác như kết quả thật, vì nhãn sinh từ luật.

**Quy tắc cắt:** đến 17/11 nếu P0 chưa đúng tiến độ thì bỏ ML-4, giữ báo cáo offline (ML-1..ML-3). ML không bao giờ được lấy giờ của task P0 đang trễ.

## Phối hợp Dũng và Khoa

B = Dũng, C = Khoa, F = Minh; mã task/giờ/ngày giữ nguyên. Đầu ra B05/B07/B08–B11 bàn giao cho các task C liên quan theo [phân công](TEAM_RESPONSIBILITIES.md): Dũng nghiệp vụ/Java/DB/REST, Khoa contract/Fuji/signing/nonce/receipt/log, Minh UI/E2E. Hỗ trợ sâu hơn khi Dũng tiết kiệm được giờ thực tế phải ghi task/phần việc/người và không double-book;8h reserve không mặc định đều có sớm.

## Finish early, do not wait

Ngày là latest completion target, không phải ngày đợi mới bắt đầu. Xong task và dependencies ready thì pull tiếp ngay; handoff API/ABI ngay khi có. Mocks chỉ cho prework; integrated Done cần dependencies thực, per-feature positive/negative tests và review. Khi blocked thì giúp critical path/review/tests, không thêm scope. Nếu hoàn tất sớm hơn18/20, chuyển ngay hardening/video/submission preparation. Coding/deploy/purchase vẫn cần authorization tương ứng.

## Capacity and exact allocation

03-20/11:14 weekdays x2.5h+4 weekend days x5h=55h/người.21-22/11:2x3h=6h.23-25/11:3x1h=3h reserve. Total64h/người,192h/team;56h task+8h reserve/người;168h work+24h reserve/team. Task estimates/owners/scope không đổi; user đã chọn dồn thêm30 phút weekday trước20/11 và giảm tải cuối giai đoạn.

Feature-complete18/11 gồm all P0 integrated, feature tests/reviews, real Fuji milestone flow và public smoke. Stable20/11 thêm full regression/security/recovery/E2E, no critical defect. Không claim passing tests hoặc completion khi mới viết lịch.

| Date | Capacity/person | Dũng — Backend | Khoa — Blockchain | Minh — Frontend/Product | Team planned / reserve |
|---|---:|---|---|---|---|
| 2026-11-03 | 2.5h | B01 2h, B02 0.5h | C01 2.5h | F01 2h, F02 0.5h | 7.5h / 0h |
| 2026-11-04 | 2.5h | B02 2.5h | C01 1.5h, C02 1h | F02 2.5h | 7.5h / 0h |
| 2026-11-05 | 2.5h | B03 2.5h | C02 2h, C03 0.5h | F02 1h, F03 1.5h | 7.5h / 0h |
| 2026-11-06 | 2.5h | B03 2.5h | C03 2.5h | F03 2.5h | 7.5h / 0h |
| 2026-11-07 | 5h | B04 4h, B05 1h | C03 2h, C04 2h, C05 1h | F04 5h | 15h / 0h |
| 2026-11-08 | 5h | B05 3h, B06 2h | C05 5h | F04 1h, F05 4h | 15h / 0h |
| 2026-11-09 | 2.5h | B06 2.5h | C06 2.5h | F06 2.5h | 7.5h / 0h |
| 2026-11-10 | 2.5h | B06 1.5h, B07 1h | C06 2.5h | F06 2.5h | 7.5h / 0h |
| 2026-11-11 | 2.5h | B07 2.5h | C06 1h, C07 1.5h | F06 1h, F07 1.5h | 7.5h / 0h |
| 2026-11-12 | 2.5h | B07 2.5h | C07 2.5h | F07 2.5h | 7.5h / 0h |
| 2026-11-13 | 2.5h | B07 1h, B08 1.5h | C07 2.5h | F07 1h, F08 1.5h | 7.5h / 0h |
| 2026-11-14 | 5h | B08 3.5h, B09 1.5h | C07 0.5h, C08 4.5h | F08 2.5h; reserve 2.5h | 12.5h / 2.5h |
| 2026-11-15 | 5h | B09 3.5h, B10 1.5h | C08 2.5h, C09 2.5h | F08 1h, F09 3h; reserve 1h | 14h / 1h |
| 2026-11-16 | 2.5h | B10 2.5h | C09 0.5h, C10 2h | F09 1h, F10 1h; reserve 0.5h | 7h / 0.5h |
| 2026-11-17 | 2.5h | B10 2h, B11 0.5h | C10 2h, C11 0.5h | F09 1h, F10 1.5h | 7.5h / 0h |
| 2026-11-18 | 2.5h | B11 2.5h | C11 2.5h | F10 1.5h, F11 1h | 7.5h / 0h |
| 2026-11-19 | 2.5h | B11 2.5h | C11 2.5h | F11 2.5h | 7.5h / 0h |
| 2026-11-20 | 2.5h | B11 0.5h; reserve 2h | C11 0.5h; reserve 2h | F11 2.5h | 3.5h / 4h |
| 2026-11-21 | 3h | B12 3h | C12 3h | F12 3h | 9h / 0h |
| 2026-11-22 | 3h | No planned implementation; reserve 3h | No planned implementation; reserve 3h | F12 2h; reserve 1h | 2h / 7h |
| 2026-11-23 | 1h | No planned implementation; reserve 1h | No planned implementation; reserve 1h | No planned implementation; reserve 1h | 0h / 3h |
| 2026-11-24 | 1h | No planned implementation; reserve 1h | No planned implementation; reserve 1h | No planned implementation; reserve 1h | 0h / 3h |
| 2026-11-25 | 1h | No planned implementation; reserve 1h | No planned implementation; reserve 1h | No planned implementation; reserve 1h | 0h / 3h |

## Day-by-day execution cards

### 2026-11-03 - Scope/identity và bắt đầu task ready

- **Main objective:** Scope/identity và bắt đầu task ready.
- **Dũng — Backend:** B01 2h, B02 0.5h.
- **Khoa — Blockchain:** C01 2.5h.
- **Minh — Frontend/Product:** F01 2h, F02 0.5h.
- **Prerequisites:** B01; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B01: Khóa RBAC/ERD/API (2h); B02: Spring/PostgreSQL/Flyway skeleton (0.5h); C01: EVM/event/commitment agreement (2.5h); F01: Interview recruitment/product assumptions (2h); F02: Wireframes/mock API states (0.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-04 - Foundation/event schema/wireframes

- **Main objective:** Foundation/event schema/wireframes.
- **Dũng — Backend:** B02 2.5h.
- **Khoa — Blockchain:** C01 1.5h, C02 1h.
- **Minh — Frontend/Product:** F02 2.5h.
- **Prerequisites:** B01, C01; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B02: Spring/PostgreSQL/Flyway skeleton (2.5h); C01: EVM/event/commitment agreement (1.5h); C02: Hardhat local setup (1h); F02: Wireframes/mock API states (2.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-05 - M0 freeze initial interfaces

- **Main objective:** M0 freeze initial interfaces.
- **Dũng — Backend:** B03 2.5h.
- **Khoa — Blockchain:** C02 2h, C03 0.5h.
- **Minh — Frontend/Product:** F02 1h, F03 1.5h.
- **Prerequisites:** B01, B02, B03, C01, C02, F02; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B03: Session auth và tenant isolation (2.5h); C02: Hardhat local setup (2h); C03: Authorized verification contract slice (0.5h); F02: Wireframes/mock API states (1h); F03: Next shell/session client (1.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Scope/ERD/DTO/errors/idempotency/ABI agreed; NGO results or explicit unvalidated status.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-06 - Auth/session/local contract

- **Main objective:** Auth/session/local contract.
- **Dũng — Backend:** B03 2.5h.
- **Khoa — Blockchain:** C03 2.5h.
- **Minh — Frontend/Product:** F03 2.5h.
- **Prerequisites:** B02, B03, C01, C02, F02; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B03: Session auth và tenant isolation (2.5h); C03: Authorized verification contract slice (2.5h); F03: Next shell/session client (2.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-07 - Case và actual Fuji pilot

- **Main objective:** Case và actual Fuji pilot.
- **Dũng — Backend:** B04 4h, B05 1h.
- **Khoa — Blockchain:** C03 2h, C04 2h, C05 1h.
- **Minh — Frontend/Product:** F04 5h.
- **Prerequisites:** B03, B04, B05, C01, C02, C03, C04, F03; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B04: Case/needs/consent/evidence APIs (4h); B05: Independent case review (1h); C03: Authorized verification contract slice (2h); C04: Fuji pilot deployment (2h); C05: Java adapter/minimal audit slice (1h); F04: Case and verifier UI (5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 5h/person; team planned 15h; reserve 0h.
- **Acceptance:** Fuji deployment only after local permission tests and corresponding authorization; no fake address.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-08 - Real vertical slice integration

- **Main objective:** Real vertical slice integration.
- **Dũng — Backend:** B05 3h, B06 2h.
- **Khoa — Blockchain:** C05 5h.
- **Minh — Frontend/Product:** F04 1h, F05 4h.
- **Prerequisites:** B04, B05, C01, C04, C05, F03, F04; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B05: Independent case review (3h); B06: Multi-item campaign draft/publish (2h); C05: Java adapter/minimal audit slice (5h); F04: Case and verifier UI (1h); F05: Audit vertical slice UI (4h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 5h/person; team planned 15h; reserve 0h.
- **Acceptance:** Same-day handoff: B review/job ->C actual receipt/event ->F audit; Done only after dependencies accepted.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-09 - M1 verify slice; continue goods

- **Main objective:** M1 verify slice; continue goods.
- **Dũng — Backend:** B06 2.5h.
- **Khoa — Blockchain:** C06 2.5h.
- **Minh — Frontend/Product:** F06 2.5h.
- **Prerequisites:** B05, B06, C03, F02; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B06: Multi-item campaign draft/publish (2.5h); C06: Full milestone contract (2.5h); F06: Campaign multi-item UI (2.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Distinct creator/verifier, actual receipt+expected event, frontend hash/status; self-approval denied.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-10 - Multi-item campaign/milestone contract

- **Main objective:** Multi-item campaign/milestone contract.
- **Dũng — Backend:** B06 1.5h, B07 1h.
- **Khoa — Blockchain:** C06 2.5h.
- **Minh — Frontend/Product:** F06 2.5h.
- **Prerequisites:** B05, B06, C03, F02; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B06: Multi-item campaign draft/publish (1.5h); B07: Commitments/cancel/idempotency (1h); C06: Full milestone contract (2.5h); F06: Campaign multi-item UI (2.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-11 - Full milestone contract/campaign ready

- **Main objective:** Full milestone contract/campaign ready.
- **Dũng — Backend:** B07 2.5h.
- **Khoa — Blockchain:** C06 1h, C07 1.5h.
- **Minh — Frontend/Product:** F06 1h, F07 1.5h.
- **Prerequisites:** B06, B07, C03, C05, F02, F06; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B07: Commitments/cancel/idempotency (2.5h); C06: Full milestone contract (1h); C07: Durable signing/submission worker (1.5h); F06: Campaign multi-item UI (1h); F07: Donor pledge/history/cancel (1.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-12 - Pledge concurrency/durable signing

- **Main objective:** Pledge concurrency/durable signing.
- **Dũng — Backend:** B07 2.5h.
- **Khoa — Blockchain:** C07 2.5h.
- **Minh — Frontend/Product:** F07 2.5h.
- **Prerequisites:** B06, B07, C05, F06; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B07: Commitments/cancel/idempotency (2.5h); C07: Durable signing/submission worker (2.5h); F07: Donor pledge/history/cancel (2.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-13 - M2 multi-item pledge gate

- **Main objective:** M2 multi-item pledge gate.
- **Dũng — Backend:** B07 1h, B08 1.5h.
- **Khoa — Blockchain:** C07 2.5h.
- **Minh — Frontend/Product:** F07 1h, F08 1.5h.
- **Prerequisites:** B06, B07, B08, B09, C05, F04, F06; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B07: Commitments/cancel/idempotency (1h); B08: Partial goods receipts (1.5h); C07: Durable signing/submission worker (2.5h); F07: Donor pledge/history/cancel (1h); F08: Receipt/delivery UI (1.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Atomic multi-line commitment, concurrent last-unit and retry/cancel tests; pledge is not received.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-14 - Partial receipts/delivery/crash recovery

- **Main objective:** Partial receipts/delivery/crash recovery.
- **Dũng — Backend:** B08 3.5h, B09 1.5h.
- **Khoa — Blockchain:** C07 0.5h, C08 4.5h.
- **Minh — Frontend/Product:** F08 2.5h; reserve 2.5h.
- **Prerequisites:** B07, B08, B09, C05, C06, C07, F04; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B08: Partial goods receipts (3.5h); B09: Delivery records/failed attempts (1.5h); C07: Durable signing/submission worker (0.5h); C08: Receipt/retry/log reconciliation (4.5h); F08: Receipt/delivery UI (2.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 5h/person; team planned 12.5h; reserve 2.5h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-15 - Delivery/confirmation/log reconciliation

- **Main objective:** Delivery/confirmation/log reconciliation.
- **Dũng — Backend:** B09 3.5h, B10 1.5h.
- **Khoa — Blockchain:** C08 2.5h, C09 2.5h.
- **Minh — Frontend/Product:** F08 1h, F09 3h; reserve 1h.
- **Prerequisites:** B08, B09, B10, C06, C07, C08, C09, F04, F08; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B09: Delivery records/failed attempts (3.5h); B10: Confirm/dispute/resolve/close (1.5h); C08: Receipt/retry/log reconciliation (2.5h); C09: Full sanitized audit API (2.5h); F08: Receipt/delivery UI (1h); F09: Confirmation/dispute/full timeline (3h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 5h/person; team planned 14h; reserve 1h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-16 - M3 reliable chain sync/audit gate

- **Main objective:** M3 reliable chain sync/audit gate.
- **Dũng — Backend:** B10 2.5h.
- **Khoa — Blockchain:** C09 0.5h, C10 2h.
- **Minh — Frontend/Product:** F09 1h, F10 1h; reserve 0.5h.
- **Prerequisites:** B08, B09, B10, C08, C09, C10, F08, F09; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B10: Confirm/dispute/resolve/close (2.5h); C09: Full sanitized audit API (0.5h); C10: Goods-to-chain integration (2h); F09: Confirmation/dispute/full timeline (1h); F10: Public hosting/recovery (1h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7h; reserve 0.5h.
- **Acceptance:** Durable signed bytes/hash before send, missing/reverted/mismatch receipts honest, log cursor replay repair.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-17 - Complete goods/confirmation integration

- **Main objective:** Complete goods/confirmation integration.
- **Dũng — Backend:** B10 2h, B11 0.5h.
- **Khoa — Blockchain:** C10 2h, C11 0.5h.
- **Minh — Frontend/Product:** F09 1h, F10 1.5h.
- **Prerequisites:** B03, B04, B05, B06, B07, B08, B09, B10, C06, C07, C08, C09, C10, F08, F09; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B10: Confirm/dispute/resolve/close (2h); B11: Backend cross-feature regression (0.5h); C10: Goods-to-chain integration (2h); C11: Contract/failure regression (0.5h); F09: Confirmation/dispute/full timeline (1h); F10: Public hosting/recovery (1.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-18 - M4 đủ P0, feature freeze/public smoke

- **Main objective:** M4 đủ P0, feature freeze/public smoke.
- **Dũng — Backend:** B11 2.5h.
- **Khoa — Blockchain:** C11 2.5h.
- **Minh — Frontend/Product:** F10 1.5h, F11 1h.
- **Prerequisites:** B03, B04, B05, B06, B07, B08, B09, B10, B11, C06, C07, C08, C09, C10, C11, F09, F10; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus integration/core, pair critical blockers instead of waiting.
- **Deliverables:** B11: Backend cross-feature regression (2.5h); C11: Contract/failure regression (2.5h); F10: Public hosting/recovery (1.5h); F11: E2E/accessibility/privacy regression (1h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** All six P0 integrated, receipt/delivery/separate confirmer/dispute, all targets per item, actual planned milestone anchors and public smoke. Per-feature tests/reviews done; full regression continues.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-19 - Regression/security/failure/accessibility

- **Main objective:** Regression/security/failure/accessibility.
- **Dũng — Backend:** B11 2.5h.
- **Khoa — Blockchain:** C11 2.5h.
- **Minh — Frontend/Product:** F11 2.5h.
- **Prerequisites:** B03, B04, B05, B06, B07, B08, B09, B10, B11, C06, C07, C08, C09, C10, C11, F10; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus regression/stability, pair critical blockers instead of waiting.
- **Deliverables:** B11: Backend cross-feature regression (2.5h); C11: Contract/failure regression (2.5h); F11: E2E/accessibility/privacy regression (2.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 7.5h; reserve 0h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-20 - M4.1 stable public demo

- **Main objective:** M4.1 stable public demo.
- **Dũng — Backend:** B11 0.5h; reserve 2h.
- **Khoa — Blockchain:** C11 0.5h; reserve 2h.
- **Minh — Frontend/Product:** F11 2.5h.
- **Prerequisites:** B03, B04, B05, B06, B07, B08, B09, B10, B11, C06, C07, C08, C09, C10, C11, F10; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus regression/stability, pair critical blockers instead of waiting.
- **Deliverables:** B11: Backend cross-feature regression (0.5h); C11: Contract/failure regression (0.5h); F11: E2E/accessibility/privacy regression (2.5h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 2.5h/person; team planned 3.5h; reserve 4h.
- **Acceptance:** Mandatory DB/contract/recovery/E2E suites+reviews pass; public demo stable, no critical auth/privacy/quantity/false-confirmation defect.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-21 - Runbooks/video/pitch

- **Main objective:** Runbooks/video/pitch.
- **Dũng — Backend:** B12 3h.
- **Khoa — Blockchain:** C12 3h.
- **Minh — Frontend/Product:** F12 3h.
- **Prerequisites:** B11, C11, F11; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus package/contingency, pair critical blockers instead of waiting.
- **Deliverables:** B12: Critical fixes/final handover (3h); C12: Deployment/key recovery runbook (3h); F12: Demo/video/pitch/submission (3h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 3h/person; team planned 9h; reserve 0h.
- **Acceptance:** B/C final handover, F video/pitch; no secret/PII exposure.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-22 - M5 package/rehearsal ready

- **Main objective:** M5 package/rehearsal ready.
- **Dũng — Backend:** No planned implementation; reserve 3h.
- **Khoa — Blockchain:** No planned implementation; reserve 3h.
- **Minh — Frontend/Product:** F12 2h; reserve 1h.
- **Prerequisites:** F11; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus package/contingency, pair critical blockers instead of waiting.
- **Deliverables:** F12: Demo/video/pitch/submission (2h). Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 3h/person; team planned 2h; reserve 7h.
- **Acceptance:** Video/pitch/runbooks/README/submission assets complete and rehearsed.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-23 - Contingency only

- **Main objective:** Contingency only.
- **Dũng — Backend:** No planned implementation; reserve 1h.
- **Khoa — Blockchain:** No planned implementation; reserve 1h.
- **Minh — Frontend/Product:** No planned implementation; reserve 1h.
- **Prerequisites:** Ready inputs / preceding gates; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus package/contingency, pair critical blockers instead of waiting.
- **Deliverables:** preserved stable build/package; actual contingency fixes or submission check only. Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 1h/person; team planned 0h; reserve 3h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-24 - Contingency/final package check

- **Main objective:** Contingency/final package check.
- **Dũng — Backend:** No planned implementation; reserve 1h.
- **Khoa — Blockchain:** No planned implementation; reserve 1h.
- **Minh — Frontend/Product:** No planned implementation; reserve 1h.
- **Prerequisites:** Ready inputs / preceding gates; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus package/contingency, pair critical blockers instead of waiting.
- **Deliverables:** preserved stable build/package; actual contingency fixes or submission check only. Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 1h/person; team planned 0h; reserve 3h.
- **Acceptance:** Demonstrate changed task behavior, relevant success/failure tests and review; accept integrated tasks only after prerequisites. Record actual status/hours/evidence.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

### 2026-11-25 - M6 external submission limit

- **Main objective:** M6 external submission limit.
- **Dũng — Backend:** No planned implementation; reserve 1h.
- **Khoa — Blockchain:** No planned implementation; reserve 1h.
- **Minh — Frontend/Product:** No planned implementation; reserve 1h.
- **Prerequisites:** Ready inputs / preceding gates; mock prework not integrated Done.
- **Collaboration:** owners handoff typed API/ABI/test feedback immediately; focus package/contingency, pair critical blockers instead of waiting.
- **Deliverables:** preserved stable build/package; actual contingency fixes or submission check only. Concrete output paths/acceptance/tests remain in Backlog.
- **Effort:** capacity 1h/person; team planned 0h; reserve 3h.
- **Acceptance:** Official closing time/rules and actual submission receipt verified; can submit earlier when ready/authorized.
- **Risks/blockers:** missing dependency, test failure or RPC/hosting issue must be reported immediately; same-day task completion needs explicit handoff. Never fake chain status or mark partial P0 complete.
- **Can defer:** visual polish, optional filters, reports/upload UI and all P1; never required identity/quantity/privacy/receipt checks. After22/11 no planned implementation.

## Milestones

| Gate | Latest target | Evidence |
|---|---|---|
| M0 |05/11 | Frozen scope/ERD/DTO/events, consent/privacy and capacity; actual interview result or unvalidated status |
| M1 |09/11 | Actual create->other verifier->Fuji receipt/event->UI proof; self-approval denied |
| M2 |13/11 | Multi-item atomic pledge/cancel, per-item concurrency/idempotency tests |
| M3 |16/11 | Durable signing/receipt/reconciliation/full audit verified at feature level |
| M4 |18/11 | All P0 integrated, actual milestone anchors, public first smoke, feature freeze |
| M4.1 |20/11 | Stable public demo, full mandatory DB/contract/recovery/E2E tests/reviews pass, no critical defect |
| M5 |22/11 | Video/pitch/runbooks/README/package complete and rehearsed |
| M6 |25/11 | External submission limit only; may submit earlier when ready/authorized |

## Fallback and escalation

| Trigger | Action | Limit |
|---|---|---|
| Cannot sustain2.5h weekdays | Recalculate actual remaining hours/forecast | Original2h weekday curve does not promise early stable20/11 |
| M1 missing09/11 /dependency blocked one checkpoint | Pair critical path, freeze optional screens, use explicit buffer | No pretending mocks are actual chain integration |
| M3 missing16/11 | Prioritize durable worker/receipt/log repair now, continue operational flow in parallel | No waiting until submission; actual completion risk explicit |
| P0 incomplete18/11 | Transparent reforecast, use hardening window only with stated test impact | Do not relabel partial P0 complete or cut multi-item silently |
| Stable gate fails20/11 | Use23-25/11 contingency for critical fixes and affected tests; tell user new readiness |25/11 stays fallback limit, not default work target |
| Capacity falls to about118h /one-week slip | Explicit Minimum Demo and Target gaps with honest actual Fuji proof | Full unchanged early Target is not guaranteed |

## Baseline and progress history

This revision replaces original2h-weekday dates (functional22/11, finishing tasks25/11) per user choice03/11/2026. Owners,36 estimates and all P0/BR requirements retained. Actual statuses remain Not Started until execution evidence exists. Critical blocker>=one checkpoint or >50% buffer used before16/11 triggers focused help/reforecast. Track hours, accepted behavior and actual tests, not code/commits. No scheduled feature work23-25/11.
