# AidBridge frontend F03 F04

Next.js App Router, TypeScript và Tailwind CSS. Đây là phần Minh phụ trách, chạy mock mặc định với dữ liệu giả; không có transaction Fuji thật.

```powershell
npm ci
npm run dev
```

Mở `http://127.0.0.1:3000/login`. Chọn A để tạo hồ sơ, đăng xuất rồi chọn B để review. Mật khẩu mock `Mock-only-123!`; A `minh.a@example.test`, B `minh.b@example.test`.

```powershell
npm test
npm run typecheck
npm run build
```

Kiểm thử UI với dev server đang chạy trong terminal khác:

```powershell
npx playwright install chromium
npm run test:e2e
```

Hoặc dùng Chrome sẵn trên Windows: đặt `$env:PLAYWRIGHT_CHROME_PATH = 'C:\Program Files\Google\Chrome\Application\chrome.exe'` rồi `npm run test:e2e`. Bộ test tạo hồ sơ giả mới mỗi lần; restart server để reset mock.

Real mode: đặt `NEXT_PUBLIC_API_MODE=real`, `API_INTERNAL_BASE_URL` là origin backend, rồi restart/rebuild. Browser vẫn dùng `/api` cùng origin; không fallback sang mock. Backend nguồn đặt port 8081, hiện chưa có B04/B05. Không dùng tài khoản mock để thử API thật.

Xem [bàn giao và từng ca smoke](../docs/handoffs/MINH_F03_F04.md) cho contract gaps, DTO đã đối chiếu, endpoint readiness, CSRF, lỗi, idempotency, và hướng dẫn real smoke có hai danh tính.
