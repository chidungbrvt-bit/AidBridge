# F07 developer preview

Preview chỉ để chạy module donor khi shell Minh chưa có; không phải production frontend shell.
Xem [F07 implementation](../../../docs/design/F07_DONOR_IMPLEMENTATION.md) để chạy, kiểm tra, routes, giới hạn mock/real và cách tích hợp App Router/shared API client.

Trong thư mục này: `pnpm install --frozen-lockfile`, `pnpm dev`.
G01: http://127.0.0.1:5173/campaigns/10000000-0000-4000-8000-000000000001
G02: http://127.0.0.1:5173/me/commitments

G03 (11/11): trong G02 chọn **Cancel unreceived goods** ở cam kết còn outstanding, chọn hàng/số lượng, nhập reason rồi submit. Để thử nhanh, bấm **Load partial receipt fixture**: Rice pledged8/received5/outstanding3 và Water2/1/1. Nút **Mock receipt: receive remaining goods** giả lập receipt từ bên ngoài và tự refetch history/campaign, giữ form/draft đang mở; gửi cancellation trước rồi bấm receipt để thử race409. Mock response400/403/409/timeout-after-save cũng áp dụng cho G03. Đây là developer controls, không phải thao tác donor hoặc endpoint production.

Không có HTTP backend, auth production hoặc blockchain trong preview. Reset/reload xóa dữ liệu mock.
