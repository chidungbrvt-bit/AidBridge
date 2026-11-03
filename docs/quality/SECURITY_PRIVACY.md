# Security & Privacy

Ngày tạo: 03/11/2026. Owner: B, reviewer C/F. Status: control specification, chưa được triển khai/test. PDF pp.8-14 yêu cầu privacy và trust separation; demo chỉ dùng dữ liệu giả lập.

## Assets, adversaries và trust boundaries

Assets: private beneficiary/contact/consent records, evidence bytes/versions, user sessions/password hashes, signing/admin keys, operational quantities, audit history và public claims. Threat actors: unauthenticated visitor, donor tò mò, cross-org staff, dishonest insiders, compromised relayer/operator, accidental developer log leak.

Boundaries: browser->same-origin API; API->org-scoped DB; API->private fixtures; worker->RPC; deploy admin->contract role management. Public chain là permanent metadata disclosure. RBAC không ngăn collusion và database superuser có thể sửa SQL; chain anchors tăng khả năng đối chiếu những mốc đã ghi.

## Controls và evidence cần kiểm tra

| Area | P0 technical control | Test / evidence | Future operational requirement |
|---|---|---|---|
| Authentication | Separate seeded accounts; BCrypt via Spring; disabled account; generic login error; bounded login attempts | Login/CSRF/session tests, secrets scan | Identity verification, offboarding, password recovery trước real pilot |
| Session | HttpOnly/Secure/SameSite=Lax, session rotation,30m idle timeout, logout invalidation | Cookie inspection, fixation test, old session401 | Session incident monitoring |
| CSRF/CORS | CSRF for mutations incl login; refreshed token after auth; same-origin API; external origins denied | Missing/bad token403, hostile Origin denied | Approved origin inventory |
| Authorization | Role + active membership + ownership + business state + identity guards | BR01/02/03/06/07, cross-org IDOR tests | Staff training, conflict-of-interest policies |
| Data minimization | Simulated alias/approximate area only; optional encrypted private details; no true IDs/health/address | Public DTO and log scan | Purpose-specific fields reviewed with NGO |
| Consent | Store basis/scope/time/recorder, public summary consent separate; no forced public hardship story | Missing consent validation; no public consent artifact | Valid consent/representative/legal basis analysis |
| Evidence | Allowlisted fixtures, immutable versions, private authenticated downloads, no-store; no user upload P0 | Access/traversal/version integrity tests | Evidence collection SOP, authenticity review, scanning if uploads added |
| Encryption | TLS public traffic; DB provider encryption configuration verified; private details/salts/signed blobs encrypted with versioned application key | HTTPS and encryption config review; no plaintext fixture secrets | Key custody/backup/rotation policy |
| Secrets | Env/secret store; deploy/admin separate relayer; no NEXT_PUBLIC secrets | Repo/build/log/video inspection | Authorized keeper, least privilege and revoke procedure |
| Logging | Structured requestId/eventId/action/result; no passwords/cookies/raw tx/private manifest/salt/PII | Capture log output in negative integration tests | Restricted retention and access |
| Audit | Append-only decisions/evidence versions; access audit for evidence; receipt validation separate | Immutable record and chain-reconcile tests | Independent sample audits and complaint handling |
| Privacy on chain | Random refs, salted event commitments, no evidence URLs/PII/low-entropy direct hashes | BR08 payload/event tests | Disclosure policy; public timing/org links remain |
| Errors | Problem+json without stack traces/resource existence leaks | 404 scope, sanitized500 test | Incident triage owner |
| Retention | Simulated demo DB/private files reset within30 days after submission; backups expire on same schedule; document actual cleanup | Checklist/cleanup record, not claim already deleted | Real retention/deletion schedule approved before pilot |

No legal compliance certification is asserted. PDF nhắc luật dữ liệu; trước dùng người thật cần kiểm tra nguồn pháp luật chính thức và chuyên môn phù hợp. Không cần thu thập dữ liệu thật để chứng minh demo.

## Public/internal field policy

Public: organization display name/publicRef, sanitized title/summary/approximate area khi allowed, goods targets/totals, milestone type/server timestamp, role label, chain proof. Internal: beneficiary alias/private fields, staff identity, consent scope/evidence, reasons/internal notes, receipt/delivery evidence. Secret: password hashes, session tokens, signing/admin keys, encryption keys, manifest salt và signed raw transaction bytes. Secret không được serialize qua API hoặc chụp video.

Public campaign DTO và audit projection được allowlist field; không serialize JPA entity hoặc sanitize bằng cách xóa vài field sau đó. Private fixture filenames không encode beneficiary. Public summary không nhận HTML/links tự do; review trước publish. Coarse area vẫn có thể re-identify trong cộng đồng nhỏ; demo fictional, real pilot cần additional disclosure review.

## Threat scenarios và response

- Staff thay URL sang org khác: repository/service scope trả404; không load file trước authorization.
- Creator có thêm CV role: identity check vẫn403; admin không tự bypass.
- Donor guesses evidence/consent ID: no access; campaign audit không tiết lộ IDs đó.
- Insider nhập delivery giả: independent review/disputes/audit giúp accountability; hệ thống không phát hiện được mọi collusion. Không claim fraud-proof.
- Operator key leak: stop worker, revoke relayer by separate admin, rotate, reconcile unknown tx; incidents audit; public old records vẫn tồn tại.
- Evidence mới sau review: giữ old snapshot, không replace silently; mutable case revisions only pre-review. Correction của delivery qua dispute/void/new delivery.
- Dependency/API errors: không log full body hoặc secrets; safe code/traceId; UI không expose stack trace.

## Safe demo provisioning

Một org chính và tối thiểu accounts OA, FV, CV, DC, donor, PA riêng. Có test fixtures cho second org và multi-role user để chứng minh identity guard. Mật khẩu unique/random từ secret provisioning, không copy vào tracked README. Không public credential có quyền admin/relayer; live demo dùng operator-controlled accounts. Open public app chỉ public read, staff credentials không chia sẻ đại trà. Seed profile từ chối chạy trên môi trường không được đánh dấu demo.

## Review/incident checklist

B review auth/tenant/privacy; C review keys/contract/calldata; F review UI claims and screenshots. Trước public: verify env boundary, sanitize logs, protect swagger/health detail, test HTTP/no-store/file ownership, cost controls, backup and restore, relayer balance/revoke. Khi incident: suspend writes, preserve logs/tx evidence, notify user/team owner; không tự gửi messages cho bên ngoài khi chưa được yêu cầu. Real beneficiary notification/legal response là procedure cần được xác lập trước pilot thật.
