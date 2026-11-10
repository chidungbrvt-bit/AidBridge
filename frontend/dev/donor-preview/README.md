# F07 developer preview

Preview chỉ để chạy module donor khi shell Minh chưa có; không phải production frontend shell.
Xem [F07 implementation](../../../docs/design/F07_DONOR_IMPLEMENTATION.md) để chạy, kiểm tra, routes, giới hạn mock/real và cách tích hợp App Router/shared API client.

Trong thư mục này: `pnpm install --frozen-lockfile`, `pnpm dev`.
G01: http://127.0.0.1:5173/campaigns/10000000-0000-4000-8000-000000000001
G02: http://127.0.0.1:5173/me/commitments

Không có HTTP backend, auth production hoặc blockchain trong preview. Reset/reload xóa dữ liệu mock.
