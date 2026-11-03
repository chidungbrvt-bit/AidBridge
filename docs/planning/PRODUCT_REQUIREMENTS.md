# Product Requirements

Ngày tạo: 03/11/2026. Owner: F. Status: proposed implementation specification. Nguồn chính: PDF pp.2-10,14-21; Master Prompt §§3,5,8. Quyết định user xem [Decision Log](DECISION_LOG.md).

## Product summary

AidBridge là nền tảng giúp NGO chuyển từ hồ sơ, chat và spreadsheet rời rạc sang một workflow có phân quyền: tiếp nhận nhu cầu qua tình nguyện viên, người khác xác minh, vận động hàng hóa, ghi nhận receipt và delivery, người khác kiểm tra bàn giao, rồi công khai lịch sử tối thiểu có thể đối chiếu với Fuji.

Khách hàng vận hành là tổ chức thiện nguyện; người thụ hưởng là hộ gia đình thiếu nhu yếu phẩm và không phải system user. Donor cung cấp hàng hóa. Volunteers và community representatives giúp phát hiện nhu cầu; vai trò cộng đồng không tự chứng minh thông tin chính xác.

Đối với developer: một modular monolith với state/quantity rules và asynchronous blockchain anchoring. Đối với NGO: một nơi theo dõi ai đang xử lý, hàng đã nhận/giao bao nhiêu, case còn thiếu gì. Đối với donor: phân biệt pledge với receipt và confirmed result. Đối với judge/investor: technical proof về workflow và record accountability; adoption, unit economics, social impact thực tế còn là giả thuyết.

Blockchain bổ sung một lịch sử chung bên ngoài quyền chỉnh sửa SQL của operator. Database truyền thống cũng có audit controls; giá trị Avalanche mạnh hơn khi nhiều bên độc lập cần đối chiếu. MVP không tuyên bố eliminate fraud hay chứng minh physical delivery. Long-term vision: NGO pilot, cross-organization review, field/offline support và data-informed matching sau khi validation, governance và privacy sẵn sàng.

## Problem-solution mapping

| Problem | Khó khăn hiện tại cần kiểm chứng | Solution / feature | Actors | Chỉ số kỳ vọng, chưa phải kết quả | Giả thuyết cần interview |
|---|---|---|---|---|---|
| Discovery Gap | Hộ gia đình không biết/không thể tự đăng ký | Staff-assisted case creation, consent | Volunteer, household, NGO | Số case qua community referral; thời gian nhập | NGO đã có mạng lưới; nhập liệu không tăng gánh nặng |
| Trust Gap | Không rõ ai cung cấp và kiểm tra thông tin | Independent verification, evidence versions | Creator, verifier | Time to Verification; Verified Case Rate | NGO có đủ người khác nhau để review |
| Delivery Gap | Pledge thường bị nhầm với hỗ trợ hoàn thành | Per-item pledge/receipt/delivery/confirmation | Donor, organization admin, confirmer | Time to Delivery; Successful Delivery Rate | Có thể ghi receipt và thu thập bằng chứng hợp lý |
| Record accountability | Khó đối chiếu báo cáo sau khi thay đổi | Append-only audit + Fuji commitments | NGO, donor, reviewer | Tỷ lệ milestone đối chiếu được chain | Người kiểm tra thấy external anchoring hữu ích |
| Operational fragmentation | Chat/sheets không giữ state guards | Shared case detail và actionable queues | Organization staff | Thời gian tổng hợp hồ sơ/báo cáo | Lợi ích đủ lớn so với switching cost |

Không đặt mục tiêu % cải thiện khi chưa có baseline. Pilot cần đo trước/sau trên workflow tương đương, không suy từ transaction count ra impact.

## Stakeholders

| Stakeholder | Mục tiêu / hoạt động | Động lực tham gia | Adoption barrier | Trust / privacy |
|---|---|---|---|---|
| NGO leadership / Org Admin | Điều phối staff, campaign, receipt và theo dõi tiến độ | Giảm đối chiếu thủ công, accountability với donor | Training, double entry, chi phí, trách nhiệm tranh chấp | Kiểm soát nội bộ, least privilege, không public hồ sơ gốc |
| Field Volunteer | Referral, consent, case creation, delivery record | Ít thất lạc hồ sơ, rõ bước tiếp theo | Kết nối yếu, thời gian tại hiện trường | Không được tự duyệt; chỉ đọc case trong org |
| Case Verifier | Kiểm tra evidence, yêu cầu bổ sung, quyết định | Quy trình nhất quán, lịch sử người chịu trách nhiệm | Thiếu thời gian hoặc evidence chưa đủ | Tài khoản riêng, không approve own case, notes private |
| Donor | Chọn campaign, cam kết, theo dõi kết quả | Thấy thiếu gì và tiến độ thực tế | Phải giao hàng qua tổ chức, nghi ngờ số liệu | Chỉ public summary; không cần biết beneficiary identity |
| Delivery Confirmer | Review record/evidence và kiểm tra ngoài đời khi cần | Tách claims với review, bảo vệ uy tín tổ chức | Khó tìm người thực sự độc lập | Khác recorder; có thể thông đồng nên cần audit và complaint process |
| Platform Admin | Approve org, suspend relayer, review disputes | Platform reliability | Phạm vi trách nhiệm và lạm quyền | Không mặc định xem PII; privileged actions có audit |
| Beneficiary / representative | Nhận hỗ trợ, đồng ý xử lý dữ liệu cần thiết | Tiếp cận mà không phải dùng công nghệ | Lo bị lộ hoàn cảnh hoặc bị ép công khai | Không wallet/login; consent công khai tách khỏi nhận aid |
| Community referrer | Giới thiệu nhu cầu cho volunteer | Hỗ trợ cộng đồng | Không có động lực tham gia mạng lưới mới | Là nguồn thông tin, không tự là verifier |

NGO chỉ nên chuyển khỏi spreadsheets khi flow thử nghiệm chứng minh giảm việc trùng lặp, dễ tìm hồ sơ và không tăng rủi ro. Không trả tiền theo số case được tạo; tránh khuyến khích khai khống.

## User stories và acceptance

Complexity: S <=3h task đơn, M 4-6h, L >6h hoặc nhiều integration boundaries. Story có thể trải nhiều tasks; không cộng story estimate vào task effort.

| ID | User story | Priority / value | Dependencies / tasks | Acceptance | Complexity |
|---|---|---|---|---|---|
| US01 | As an Organization Admin, I want staff to log in individually, so that actions have accountable owners. | P0 / identity trust | B03,F03 | Active account có session; invalid login không tạo session; role nav đúng | M |
| US02 | As an Organization Admin, I want private cases isolated by organization, so that households are protected. | P0 / privacy | B03,B04,B11 | Cross-org GET/PATCH/references đều không lộ dữ liệu | L |
| US03 | As an Organization Admin, I want a verified case published with several goods targets, so that donors see exact needs. | P0 / coordination | B06,F06 | Per-item target/unit fixed; unverified publish bị chặn | M |
| US04 | As an Organization Admin, I want to record partial goods receipts, so that pledges are not counted as stock. | P0 / integrity | B08,F08 | Received được cộng riêng theo commitment item; không vượt pledged | M |
| US05 | As a Field Volunteer, I want to create a case with consent and simulated evidence, so that a household can be represented. | P0 / discovery | B04,F04 | Case SUBMITTED với creator, org, consent, evidence refs | M |
| US06 | As a Field Volunteer, I want to revise an unverified case without overwriting evidence, so that corrections remain traceable. | P0 / integrity | B04,B05 | New evidence version + audit; reviewed snapshot giữ nguyên | M |
| US07 | As a Field Volunteer, I want to record delivery quantities, so that actual handover claims are distinct from receipt. | P0 / delivery | B09,F08 | Per-item available quantity enforced; record DELIVERED chưa CONFIRMED | M |
| US08 | As a Case Verifier, I want to approve another person's case, so that fundraising has a reviewed basis. | P0 / trust | B05,F04 | Approval của khác user; immutable review snapshot và chain job | M |
| US09 | As a Case Verifier, I want to reject or request changes, so that insufficient evidence is not published. | P0 / safety | B05,F04 | Reason bắt buộc; request changes cho phép sửa và submit lại | M |
| US10 | As a Donor, I want to browse sanitized campaigns, so that I can support needs without seeing private records. | P0 / adoption | B06,F06 | Chỉ published public fields; không beneficiary PII | M |
| US11 | As a Donor, I want to pledge several goods items and retry safely, so that my commitment is counted once. | P0 / supply | B07,F07 | Atomic multi-line request; per-item limits; retry không nhân đôi | L |
| US12 | As a Donor, I want to track pledged, received, delivered and confirmed totals separately, so that I understand progress. | P0 / transparency | B07-B10,F07,F09 | Hiển thị per-item campaign totals; không giả donor-to-household lot provenance | M |
| US13 | As a Donor, I want to inspect audit events and explorer links, so that I can independently check record anchoring. | P0 / record integrity | C05,C09,F05 | Pending/failed/confirmed đúng; actual receipt + expected event required | L |
| US14 | As a Delivery Confirmer, I want to review another person's delivery, so that the record gets a separate check. | P0 / trust | B10,F09 | Self-confirm rejected; evidence snapshot và identity logged | M |
| US15 | As a Delivery Confirmer, I want to flag a suspect delivery, so that a disputed case cannot close. | P0 minimal / safety | B10 | Dispute mở chặn confirm/completion; reason auditable | M |
| US16 | As a Platform Admin, I want to resolve a dispute with an auditable outcome, so that corrections do not erase history. | P0 minimal / recovery | B10,C06 | Uphold hoặc void identified record, lock quantities, preserve original | L |
| US17 | As a Platform Admin, I want only approved organization relayers to write on-chain, so that unauthorized records are rejected. | P0 / authorization | C03,C06 | Unauthorized/wrong-org/revoked caller reverts | M |
| US18 | As an Organization Admin, I want reports and member-management screens, so that routine administration is easier. | P1 / convenience | Stable P0 | Post-MVP; bootstrap accounts đủ cho demo | L |

## Validation protocol

F tự tìm 2-3 người đã làm thiện nguyện trong 03-06/11; agent không tự gửi tin nhắn cho bên ngoài. Hỏi họ đang dùng công cụ gì; nơi mất thời gian; ai review/confirm; cách nhận hàng; evidence nào chấp nhận được; ai được xem PII; việc chuyển sang AidBridge có tạo double entry không. Cho xem low-fidelity workflow và yêu cầu thực hiện một case mẫu. Ghi câu trả lời ẩn danh khi được đồng ý, vấn đề quan sát được và những gì chưa kiểm chứng.

Nếu không tiếp cận được: ghi risk R01, tiếp tục technical prototype với assumptions rõ ràng; không nói NGO đã xác nhận nhu cầu. Pilot thật chỉ bắt đầu sau operational/legal/privacy review.

## Metrics definitions

| Metric | Definition | Guard |
|---|---|---|
| Verified Case Rate | Approved cases / submitted cases trong cohort thời gian | Exclude draft/demo khỏi real impact |
| Time to Verification | approvedAt - submittedAt; report median và cohort | Không tính rejected là approved |
| Time to Delivery | confirmed delivery time - first commitment time | Report partial vs complete riêng |
| Successful Delivery Rate | Cases COMPLETED / eligible started cases | Disputed cases không thành công |
| Dispute Rate | Cases từng có dispute / processed cases | Resolved history không biến mất |
| Evidence Verification Rate | Milestones có reviewed version / milestones cần evidence | Không đồng nhất với chain receipt rate |
| Donor Retention | Returning donors / prior-period donors | Chưa có longitudinal data trong demo |
| Underserved Reach | Cases qua đại diện cộng đồng, không tự đăng ký | Không suy từ số wallet hoặc transactions |

Các metric này là measurement plan, không phải dashboard bắt buộc trong P0.
