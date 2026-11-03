# MVP Scope

Ngày tạo: 03/11/2026. Owner: F. Status: planning baseline. Sources: PDF pp.14-16,21; Master Prompt §9; user-confirmed constraints trong Decision Log.

## P0 capability specification

| ID / feature | Functional requirements / UI | Entities / APIs | Rules / blockchain | Dependencies / tasks | Acceptance và tests / DoD |
|---|---|---|---|---|---|
| P0-01 Organization Login | Individual seeded accounts, login/logout, role navigation, organization context | users, memberships, roles; auth/csrf/login/logout/me | Active membership, tenant isolation; no wallet login | B02,B03,F03 | Valid login + invalid password + CSRF + cross-org rejection; session cookie an toàn |
| P0-02 Create Aid Case | SUBMITTED case, minimal consent, private fixture refs, edit before review; case list/detail/form | beneficiaries, consent_records, aid_cases, evidence_versions; /cases, PATCH, /resubmit, /evidence-versions | BR-01,08,09; tạo case chưa cần on-chain | P0-01; B04,F04 | Org creator valid; outsider rejected; evidence revision bất biến; public DTO không PII |
| P0-03 Case Verification | Approve/reject/request changes; khác creator; reviewed snapshot | verifications, audit_events, blockchain_jobs; /verify,/reject,/request-changes | BR-02,03; approved verification anchored | P0-02; B05,C03-C05,F04-F05 | Self-approval rejected dù multi-role; reason cho rejection; approved snapshot + outbox atomic; verified slice on Fuji |
| P0-04 Campaign & Commitment | Draft/publish, nhiều item, sanitized browse; donor multi-item pledge/history và cancel unreceived quantity | campaigns/items, contributions/items, idempotency; /campaigns,/publish,/commitments,/me/commitments,/cancellations | BR-03,04,05,11; commitment milestone | Verified case; B06,B07,C06,F06,F07 | Không vượt từng target khi concurrent; atomic multi-line request; safe retry; không tạo tiền thật |
| P0-05 Delivery Confirmation | Partial receipt, record successful/failed attempt, independent confirm, minimal dispute/resolve, completion | receipts/items, deliveries/items, confirmations, disputes; receipt/delivery/confirm/dispute/resolve routes | BR-05,06,07,09,11; receipt, delivery, confirmation, dispute milestones | P0-04; B08-B10,C06,C10,F08,F09 | Received<=active pledge; delivered<=received; self-confirm rejected; missing goods hoặc dispute chặn completion |
| P0-06 Avalanche Audit | Worker, tx tracking, receipt/event checks, recovery; timeline with explorer | audit, jobs, tx attempts, cursors; /audit-trail, sanitized campaign audit | BR-08,10,11,12; authenticated relayer, no PII | C01-C12,B05,F05,F09 | Actual Fuji receipt/event, pending/failed honest, crash retry không duplicate, unauthorized contract caller rejected |

Feature Done còn cần tests, code review, UI/API/DB integration, privacy checks và docs update. P0-06 không được thay bằng mock-only blockchain; minimum demo vẫn phải có actual Fuji record kiểm chứng được.

## End-to-end traceability

| Requirement | Stories | BR / API IDs | Implementation tasks | Test IDs | Milestone |
|---|---|---|---|---|---|
| P0-01 | US01,US02 | BR01; A01-A04,O02 | B02,B03,F03,B11 | BR01-P/N,IT09,IT15,E2E06 | M0/M1 |
| P0-02 | US05,US06 | BR01,08,09; C01-C05,E01-E03 | B04,F04,B11 | BR01-P/N,BR08-P/N,BR09-P/N,E2E02/06/07 | M1 |
| P0-03 | US08,US09 | BR02,03; V01-V03 | B05,C03-C05,F04,F05 | BR02-P/N,IT01,IT08,E2E02 | M1 |
| P0-04 | US03,US10-US12 | BR03,04,05,11; P01-P04,G01-G03 | B06,B07,C06,F06,F07 | BR03-P/N,BR04-P/N,BR11-P/N,IT10,E2E03 | M2 |
| P0-05 | US04,US07,US14-US16 | BR05,06,07,09,11; R01,D01-D02,S01-S03,P05 | B08-B10,C06,C10,F08,F09 | BR05-P/N,BR06-P/N,BR07-P/N,IT05-12,IT16,E2E01/04 | M4 |
| P0-06 | US13,US17 | BR08,10,11,12; T01-T03 and milestone effects | C01-C12,F05,F09 | BR08-P/N,BR10-P/N,BR11-P/N,BR12-P/N,SC01-11,IT01-04/13/14,E2E05 | M1/M3/M5 |

API IDs được định nghĩa trong API Specification; test IDs ở Test Strategy. Ranges là danh sách ID trong các tài liệu đó, không claim tests đã chạy.

## Những phần P1 phải có bản tối thiểu để P0 an toàn

| Appendix P1 | P0 minimum | Full feature được hoãn |
|---|---|---|
| Organization Approval | Demo org có approval state, active membership, registered relayer; bootstrap chỉ admin được phép | Public application, review queue, onboarding UI |
| Evidence Upload | Private simulated fixtures, file bytes/manifest hash, immutable versions, authenticated access, audit | Upload UI/API, user-supplied files, virus scanning pipeline |
| Organization Report | Case detail per-item totals và audit timeline | Export, filters, charts và management reports |
| Dispute Flagging | Authenticated API flag/resolve, reason, completion guard; small case action đủ dùng | Full complaints portal, mediation, external governance |

Không lấy việc P1 UI bị hoãn làm lý do bỏ BR-07 hoặc BR-09. Không nhận file upload trá hình qua public URL/fixtureId tùy ý.

## Ba scope levels

| Level | Giữ | Đơn giản hóa |
|---|---|---|
| Minimum Demo | Một approved org, distinct user accounts, một case/campaign **nhiều item**, pledge->receipt->delivery->confirm, BR guards, private fixture evidence, actual verification anchor Fuji và audit status | Số record ít; gộp screens; các milestone khác nếu chưa anchor phải ghi Not anchored/Not implemented rõ, không claim đạt Target MVP |
| Target MVP | Toàn bộ sáu P0; partial quantities, minimal dispute/correction, all planned milestone anchors, retry/reconcile, tests, public demo | Không report/onboarding/upload/member UI |
| Extended MVP | Target MVP đạt gates trước; chọn một enhancement nhỏ qua estimate/review | Evidence upload hoặc report, không làm cả hai nếu chưa có capacity |

User đã chọn nhiều loại hàng; không tự cắt còn một loại để che scope reduction. Nếu Target MVP không đạt, báo thiếu capability rõ ràng thay vì đổi Definition of Done.

## Không thuộc P0

AI matching, ML fraud detection, offline-first mobile, SMS/hotline, cross-organization trust network, real-money escrow, payment processing, tokens/NFTs/custom L1, warehouse/catalog, unit conversion, donor-to-beneficiary inventory provenance, self-registration, reset-password/invitation workflows.

Chỉ reconsider sau hackathon khi NGO pilot chứng minh nhu cầu: offline cần field connectivity observations; matching/ML cần lawful representative data và human appeal; SMS cần verified service/cost/consent; multi-org cần governance và independent participants; escrow cần legal/security/financial review riêng. Không thu phí beneficiary trong MVP.

## Scope-cut triggers

- 11/11 vertical slice trễ >1 ngày: cả ba tập trung slice, dừng screens mới.
- 18/11 reconciliation chưa ổn định: dừng enhancements, giảm data/screen count; không bỏ receipt/event checks.
- Trễ 3 ngày: bỏ dashboard độc lập, polish, P1, filters không thiết yếu.
- Trễ 7 ngày / capacity ~118h: Minimum Demo, ghi feature gaps, ưu tiên simulated multi-item end-to-end.
- Không đủ actual chain evidence: sửa integration hoặc ghi chưa đạt blockchain gate; không làm giả confirmation.

Chi tiết contingency và minimum invariants xem [Risk Register](../quality/RISK_REGISTER.md).
