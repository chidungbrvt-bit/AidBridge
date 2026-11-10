# Demo Scenario - A Verified Aid Journey

Ngày tạo: 03/11/2026. Owner: F, B/C support. Status: script proposal; demo application/transactions chưa được tạo. Sources: PDF pp.16,20; Master Prompt §23.

## Setup / fictional data

Organization: **AidBridge Demo Organization**. Household: **Demo Household A**, approximate area **Demo Commune A**; no real names/addresses/health/IDs. Case title **Essential supplies for a rural household**. Needs: **Food package:2 packages**, **Hygiene kit:2 kits**. Consent/evidence: fictional signed note/photo fixtures, private version1; version2 dùng cho revision test. Nhãn `Simulated beneficiary and goods data` luôn rõ trên demo.

| Account | Role / action | Identity restriction |
|---|---|---|
| org-admin | OA: publish campaign, receive goods, close | Separate user, no shared credentials |
| field-volunteer | FV: create case, record delivery | Cannot verify own case or confirm own delivery |
| case-verifier | CV: review/approve | Different user from creator |
| donor | Donor: pledge2 food +2 hygiene | Sees sanitized campaign only |
| delivery-confirmer | DC: inspect/confirm | Different user from recorder |
| platform-admin | PA: dispute/retry internal tooling if needed | No automatic private evidence access |

Actual credentials provided by secure demo provisioning, not stored in this document. Second org and multi-role negative account used in tests, not necessary for main story. Separate browser contexts/role labels avoid accidental wrong actor in demo. Preflight sessions/CSRF tokens before presentation.

## 30-second problem introduction

"Một hộ gia đình cần nhu yếu phẩm nhưng không có smartphone để tự đăng ký. Tổ chức thiện nguyện có thể biết trường hợp này qua tình nguyện viên, nhưng hồ sơ, cam kết hàng và bằng chứng bàn giao thường nằm ở nhiều nơi. AidBridge thử nghiệm một quy trình thống nhất để biết ai đã kiểm tra thông tin, hàng đã tới bước nào và bản ghi nào có thể đối chiếu độc lập."

Không nói đây là khảo sát định lượng hoặc trường hợp đã được hỗ trợ thật. Nếu chưa có NGO interviews, nói product assumptions chưa validated.

## 60-second product explanation

"Người thụ hưởng không dùng app hoặc ví. Tình nguyện viên nhập hồ sơ thay họ; một người khác xác minh. Sau khi duyệt, tổ chức công bố nhu cầu theo từng loại hàng. Nhà tài trợ cam kết, tổ chức ghi nhận hàng đã nhận, người giao ghi bàn giao và người khác kiểm tra lại. Bốn mốc pledged, received, delivered và confirmed được giữ riêng. Avalanche Fuji lưu các sự kiện và evidence commitments để lịch sử có thể đối chiếu bên ngoài database; blockchain không xác minh sự thật vật lý của ảnh hay việc giao hàng. Đây là demo dùng dữ liệu và hàng hóa mô phỏng, không nhận tiền thật."

## Live sequence (khoảng5-7 phút, điều chỉnh theo rules chính thức)

| Step | Screen / account | Action / expected result | Explain / evidence |
|---|---|---|---|
| 1 | `/org/cases/new`, FV | Nhập consent/needs/fixture; POST C01 ->SUBMITTED | Household represented without account/wallet |
| 2 | `/org/cases/[id]`, CV | Review evidence v1, approve V01 ->VERIFIED; chain initially pending | Separate reviewer identity; không instant-chain fiction |
| 3 | Same case audit, C/F presents | Show verification tx once receipt+CaseVerified validated | Actual Fuji hash/address/block/log; explorer |
| 4 | Same case, OA | Create draft/P01, publish/P02 | Unverified cases cannot publish; fixed targets per item |
| 5 | `/campaigns/[id]`, donor | Pledge food2+hygiene2/G01 | Pledged2/2, received/delivered/confirmed0 |
| 6 | Same case goods section, OA | First receipt food2+hygiene1/R01 | Partial fulfillment: hygiene still short; received differs from pledged |
| 7 | Same goods section, OA | Second receipt hygiene1 | Received meets both targets; still not delivered |
| 8 | Delivery section, FV | Record food2+hygiene2 with private fixture/D01 | DELIVERED; confirmed0, case not complete |
| 9 | Delivery section, DC | Review exact evidence, confirm/D02 | Independent review; confirmed totals now2/2 |
| 10 | Same case, OA | Close campaign/P05 | COMPLETED only when all lines confirmed and no dispute |
| 11 | Public campaign audit, donor | Compare milestones and actual explorer link | No beneficiary PII; all chain statuses honest |

Nếu chain tx đang pending, tiếp tục workflow business và mở một prior actual confirmed transaction từ cùng reviewed deployment. Nói rõ event nào đang chờ, event nào là historical demo record. Không sửa database/status để trình diễn instant success.

## Negative-security demonstration

Preferred: seeded dual-role FV+CV account tạo case riêng rồi gọi verify của chính mình. UI không cho action và API direct request bằng session hợp lệ trả403 SELF_APPROVAL, case vẫn SUBMITTED, không tạo approval/chain job. Alternate: FV+DC self-confirm returns403 SELF_CONFIRMATION; hoặc donor overflow one line409 với entire multi-line request rollback.

Chọn một negative scenario ngắn, không phá main happy case. Show actual result khi ứng dụng đã implement; hiện chưa có result. Không dùng unauthorized tx gây ảnh hưởng production; contract negative proof lấy local tests hoặc dedicated safe Fuji attempt.

## Blockchain evidence cần chuẩn bị

- Deployment manifest: actual chain43113, registry address/version, deploy tx/block và ABI/bytecode hash.
- CaseVerified receipt status1 cùng eventId/caseRef/commitment đúng adapter projection.
- Target MVP thêm AidCommitted, GoodsReceived, DeliveryRecorded, DeliveryConfirmed và dispute sequence khi demo nhánh.
- Explorer source verification nếu thành công; nếu tool unavailable, ghi limitation, không nói verified source.
- Backend submitted txHash có thể pending; chỉ confirmed badge khi matching receipt/log. Evidence authenticity vẫn human review.
- Không hiển thị private keys, session cookies, manifest salts, internal PII hoặc fixture bytes trên public audit.

## Four dimensions và closing

| Dimension | Demo evidence | Claim limit |
|---|---|---|
| Social Impact | Assisted case creation cho household không smartphone | Accessibility pathway, chưa phải measured beneficiaries reached |
| Operational Value | Multi-item pledge/receipt/delivery/confirmation trong một flow | Prototype usefulness cần NGO validation |
| Trust | Separate identities, negative403, evidence versions/dispute/audit | Collusion vẫn có thể xảy ra |
| Technical Execution | FE->Spring->PostgreSQL->outbox->Fuji->receipt->UI | Real testnet integration, không production scale claim |

Closing: "AidBridge đặt công nghệ ở phía tổ chức có khả năng sử dụng, giúp người cần hỗ trợ không phải vượt thêm rào cản số. Prototype cho thấy một quy trình có kiểm soát và lịch sử đối chiếu được. Bước tiếp theo là thử với NGO, đo chi phí thao tác, cải thiện privacy và kiểm chứng giá trị thực tế."

## Pitch outline và backup

Slides: problem/persona -> organization-first workflow -> four distinct goods stages -> trust layers/limits -> architecture/Fuji evidence -> demo -> validation status/next pilot. Không fake social metrics, partner logos hoặc real delivery claims.

Before demo: stable build, accounts, fixture data reset, CSRF/session, test AVAX, RPC health, projected cost, actual confirmed example, explorer tabs, local backup và video. Video captures actual application/testnet state, label recordings date/build; simulation labeled. If public hosting down use local workflow + actual historical Fuji evidence; if RPC down never mark current tx confirmed. Submission video length/format follows official rules once known.

## Readiness dates (D26/D27)

18/11 đủ P0 và public first smoke;19-20/11 full regression/hardening;20/11 stable demo.21-22/11 video/pitch/rehearsal ready.23-25/11 chỉ contingency/submission. Có thể hoàn thành/record/rehearse sớm hơn khi gates đã pass; không đợi đúng ngày và không nói application đã có vì script này tồn tại.
