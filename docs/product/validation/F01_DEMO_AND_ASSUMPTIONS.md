# F01 - Kịch bản thử và giả định nghiệp vụ ngày 10/11/2026

Người thực hiện: Khoa/Codex. Status: **In Review**. Chỉ tài liệu Product/Validation và checklist, chưa triển khai F07, chưa thực hiện demo/API end-to-end. Tài liệu này giúp Codex của Dũng/Minh tiếp tục mà không cần lịch sử chat.

## Phạm vi và nguồn

Theo bảng phân công 10/11 do Khoa cung cấp: F01 (2h) chốt kịch bản Demo Household A, nhiều mặt hàng, vai trò độc lập, checklist và giả định cần xác thực. F01 trong backlog 03/11 lại thiên về tìm người phỏng vấn; giữ nguyên owner/estimate/lịch sử và ghi riêng bằng chứng của phạm vi mới, không tự đánh dấu việc phỏng vấn đã xong.

Nguồn: [Product Requirements](../../planning/PRODUCT_REQUIREMENTS.md), [MVP Scope](../../planning/MVP_SCOPE.md), [Decision Log](../../planning/DECISION_LOG.md), [Business Workflows](../../architecture/BUSINESS_WORKFLOWS.md), [API Specification](../../architecture/API_SPECIFICATION.md), [Test Strategy](../../quality/TEST_STRATEGY.md), [Demo Scenario gốc](../../demo/DEMO_SCENARIO.md), blueprint v1.1 (đặc biệt pp.8,14-16,19-20). SOURCE là nguồn thiết kế; không phải bằng chứng runtime. ASSUMPTION/PROPOSED/OPEN không được coi là quyết định mới đã phê duyệt.

## Repository và tình trạng thực tế

Ngày kiểm tra 10/11, repo D:/Project/AidBridge, nhánh khoa. Ban đầu HEAD a28917d và working tree sạch. Fetch xác nhận origin/main f676e41c3363f1908b7050e30cfbee5b98a24f06 là merge PR #4, chứa cả C01/F02; đã fast-forward khoa bằng git merge --ff-only origin/main. Không reset/rebase/force push, không tạo commit mới. origin/khoa vẫn a28917d: ahead 3 là các commit đã tồn tại trên main, không phải công việc F01 đã commit.

| Phần | Quan sát trong nguồn đã fetch | Giới hạn |
|---|---|---|
| Backend/auth | AuthController, memberships, V1 identity migration, source tests đã có | Có code không đồng nghĩa HTTP/test đã chạy trong lượt F01 |
| Dũng/C02 | Main nhận ToolchainSmokeProbe và Hardhat setup qua PR #3 | Không phải AidRegistry/C03, không có CaseVerified runtime proof |
| C01/F02 | Đã merge PR #4; review/design/mock/checkers có sẵn | Những mục OPEN và mock labels còn hiệu lực; merge không biến mock thành feature |
| Minh/F04/F05 | origin/minh ở d6efa49; chưa có frontend trong main/nhánh remote đã fetch | Không suy rằng Minh chưa làm ở máy riêng; chỉ biết phần chưa có trên refs này |
| Goods/verification | Không thấy implementation B04-B10 hoặc AidRegistry trên baseline | Toàn bộ checklist lifecycle bên dưới là kỳ vọng theo tài liệu, chưa chạy |

Không cần sửa C01/F02 hoặc source của thành viên khác. F01 là bản kể chuyện sản phẩm độc lập, không mở rộng công việc của Dũng/Minh.

## Vai trò và quy tắc quyền

Đã đọc `backend/aidbridge-backend/src/main/java/com/aidbridge/organization/entity/OrganizationMembership.java` và migration V1. Enum thực tế là ORG_ADMIN, VOLUNTEER, VERIFIER, DELIVERY_CONFIRMER. Docs workflows/F02 lại dùng ORGANIZATION_ADMIN/FIELD_VOLUNTEER/CASE_VERIFIER. Bảng dưới là **đối chiếu ý nghĩa**, không phải đổi enum hoặc bằng chứng có alias runtime.

| Nhân vật giả / tài khoản logic | Enum hiện có | Tên trong workflows | Hành động / giới hạn |
|---|---|---|---|
| field-volunteer | VOLUNTEER | FIELD_VOLUNTEER (FV) | Tạo hồ sơ, ghi giao hàng; không duyệt hồ sơ mình tạo hoặc xác nhận delivery mình ghi |
| case-verifier | VERIFIER | CASE_VERIFIER (CV) | Review hồ sơ của người khác, bằng chứng/version đúng; không tự xác minh dù có nhiều roles |
| delivery-confirmer | DELIVERY_CONFIRMER | DELIVERY_CONFIRMER (DC) | Xác nhận delivery do user khác ghi; phải có membership/role, đúng trạng thái và evidence, không dispute |
| org-admin | ORG_ADMIN | ORGANIZATION_ADMIN (OA) | Tạo/publish campaign, nhận hàng, close; không tự có quyền VERIFY/CONFIRM hay đọc org khác |
| donor | donorEnabled capability | Donor | Cam kết và lịch sử của chính mình; public summary, không private case/evidence |
| Demo Household A | Không account/wallet | Beneficiary | Được hỗ trợ thông qua organization; không ép công khai hoàn cảnh để nhận hỗ trợ |

Mỗi actor tài khoản riêng, active membership owning organization, org APPROVED. Donor không cần role trong organization. Tên role khác nhau cần Dũng/Minh thống nhất mapping trước live integration (F01-A01); không seed bằng các tên docs không tồn tại trong enum. Credentials/UUID thực do provisioning/runtime cung cấp, không ghi mật khẩu trong docs.

Independent nghĩa là khác **user identity**, không chỉ đổi role hoặc login lại. Creator có thêm VERIFIER vẫn 403 SELF_APPROVAL. Delivery recorder có thêm DELIVERY_CONFIRMER vẫn 403 SELF_CONFIRMATION. Người khác nhưng thiếu role/membership, sai org, thiếu evidence, state không hợp lệ hoặc dispute mở vẫn không được xác nhận. Platform Admin không bypass identity/privacy guards. Backend phải enforce; nút UI bị ẩn chưa đủ bằng chứng.

## Dữ liệu giả và kịch bản chính

Organization: AidBridge Demo Organization. Beneficiary alias: Demo Household A. Area: Demo Commune A. Internal/public title giả: Essential supplies for a rural household. Hai need lines: Food package, unit package, target2; Hygiene kit, unit kit, target2. Evidence fixtures v1 và consent hoàn toàn giả lập, chỉ dùng allowlisted fixtures do backend provision; không bịa fixture keys/UUID đã tồn tại. Nhãn UI: `Simulated beneficiary and goods data`.

Đây là bộ 2/2 theo Demo Scenario gốc, **không phải** snapshot nối tiếp của F02 Rice20kg/Water4box. Không đổi units/targets của campaign đã publish, không trộn IDs giữa hai bộ. Chỉ tái dùng các quy tắc/F02 UI states; seed hoặc mock mới thuộc task triển khai tương ứng khi được giao.

Mở đầu: Một hộ gia đình không cần smartphone/wallet; volunteer nhập nhu cầu thay. Một người khác xem bằng chứng rồi duyệt. Donor cam kết hàng, organization ghi nhận tiếp nhận, volunteer ghi bàn giao, một người khác kiểm tra kết quả. Không kể đây là trường hợp đã được giúp thật.

| Bước / API theo specification | Actor | Thao tác và kết quả mong đợi |
|---|---|---|
| 1 C01 POST /api/cases | VOLUNTEER | Consent, 2 needs, fixture evidence; 201 SUBMITTED; ghi creator |
| 2 V01 POST /api/cases/{id}/verify | VERIFIER khác creator | expectedVersion và exact reviewedEvidenceVersionIds; 200 VERIFIED, PENDING_ON_CHAIN riêng biệt |
| 3 P01 POST /api/campaigns | ORG_ADMIN | Copy nhu cầu đã duyệt, 201 campaign DRAFT còn private |
| 4 P02 POST /api/campaigns/{id}/publish | ORG_ADMIN | 200 PUBLISHED, case IN_PROGRESS; targets/units cố định |
| 5 G01 POST /api/campaigns/{id}/commitments | Donor | Cam kết2/2 với campaignItemId; 201, nhận contributionItemId từ response |
| 6 R01 POST /api/commitments/{id}/receipts | ORG_ADMIN | Nhận food2/hygiene1 bằng contributionItemId; 201 partial, còn thiếu hygiene1 |
| 7 R01 lần hai | ORG_ADMIN | Chỉ nhận hygiene1 còn lại; receipt/key mới; 201, chưa phải delivery |
| 8 D01 POST /api/cases/{id}/deliveries | VOLUNTEER | outcome DELIVERED2/2 với campaignItemId/evidenceVersionIds; 201, confirmed0/0 |
| 9 D02 POST /api/deliveries/{id}/confirm | DELIVERY_CONFIRMER khác recorder | decision CONFIRMED, đúng evidence snapshot/no dispute; 200; campaign FULFILLED, case còn IN_PROGRESS |
| 10 P05 POST /api/campaigns/{id}/close | ORG_ADMIN | 200, đủ mọi item confirmed và không dispute; campaign CLOSED/case COMPLETED |
| 11 T02 GET /api/campaigns/{id}/audit-trail | Donor/public | 200 sanitized timeline; chỉ dẫn explorer thật nếu có verified receipt/event |

C01 trong bảng là **API ID tạo case**, không phải task C01 Blockchain Review. Định danh `{id}` trong từng route là resource tương ứng; không dùng caseId thay campaignId hoặc contributionItemId thay campaignItemId.

| Mốc | Pledged food/hygiene | Received | Delivered | Confirmed |
|---|---|---|---|---|
| Trước cam kết | 0/0 | 0/0 | 0/0 | 0/0 |
| Cam kết | 2/2 | 0/0 | 0/0 | 0/0 |
| Nhận từng phần | 2/2 | 2/1 | 0/0 | 0/0 |
| Nhận đủ | 2/2 | 2/2 | 0/0 | 0/0 |
| Ghi giao hàng | 2/2 | 2/2 | 2/2 | 0/0 |
| Xác nhận độc lập / close | 2/2 | 2/2 | 2/2 | 2/2 |

Không cancel trong kịch bản nên pledged bằng activePledged. Mọi item giữ 0<=confirmed<=delivered<=received<=activePledged<=target. Đạt pledged/received/delivered chưa cho phép close. Không claim donor lot riêng đã tới household; campaign totals là pool chung.

Preflight khi có implementation: tài khoản/browser contexts riêng, csrf/session hiện hành, data giả sạch, role mapping đã được review, evidence version đúng, API read model sẵn sàng. Browser mutation dùng same-origin cookie + CSRF; mỗi ý định mới có Idempotency-Key mới; timeout chưa rõ kết quả retry cùng body/key. Review dùng expectedVersion. SQL success không đồng nghĩa blockchain success.

## Checklist tình huống và cách ghi bằng chứng

[f01-demo-checklist.json](f01-demo-checklist.json) chứa 11 bước chính (F01-P01..P11) và 16 nhánh kiểm tra (F01-N01..N16), tất cả executionStatus=NOT_RUN. Đây là product expectations, không HTTP DTO, seed hay fixtures chứng minh feature đã chạy.

Nhánh chính kiểm tra riêng từng bước/bảng totals. Các nhánh âm phải reset về trạng thái chỉ định hoặc dùng case riêng: self-verify, self-confirm kể cả nhiều roles; thiếu role; cross-org404; unverified campaign409; pledge/receipt/delivery overflow409 atomic; close khi chưa confirmed/dispute; stale version; evidence không khớp; same-key replay và changed-body conflict; FAILED delivery zero goods effect; chain pending/failed không false-confirm. Nhánh evidence400 là kỳ vọng tổng quát cần đối chiếu B10, chưa khóa chi tiết error mapping. Nhánh mở dispute phải tạo dispute bằng API được phép, không sửa DB; resolution thuộc B10, không thực hiện F01.

Trường hợp lỗi trước commit không đổi business records/totals, không successful audit/job; security log có thể ghi attempt. Replay thành công trả record cũ, **không thêm** effects. FAILED attempt là thành công ghi nhận một lần thử thất bại: có record/local audit nhưng không quantity effect/chain job; không gọi đó là rejected write. UI bất kỳ async error cần giữ draft/focus/error summary theo F02; keyboard E2E chưa chạy.

Khi thực sự chạy, ghi: test ID, ngày, actor/user identity giả, build/commit, môi trường mock/real HTTP/local chain/Fuji, expected/actual, HTTP status/code, resource/tx evidence đã làm sạch, người review. Không ghi credentials/cookie/salt/raw evidence. HTTP expected trong checklist không phải observed status. Receipt null/status0/wrong event không được confirmed; nếu chưa có Fuji proof thì ghi Not anchored/Pending/Failed đúng thực tế, không chế hash hoặc đổi DB để diễn demo.

## Giả định sản phẩm cần xác thực

| ID / nhãn | Giả định / ảnh hưởng | Default trong demo / cách xác thực / người phối hợp |
|---|---|---|
| F01-A01 OPEN (interface) | Tên role giữa enum backend và docs khác nhau; nguy cơ UI seed/authorization sai | Dùng enum hiện tại để giải thích; Dũng/Minh chốt mapping ở task của họ trước tích hợp; không sửa enum ở F01 |
| F01-A02 ASSUMPTION | NGO có đủ người khác nhau để review case/delivery; nếu không workflow bị nghẽn | Provision 5 identities giả. Khoa hỏi NGO ai có thể review và khi vắng mặt làm sao; không bỏ BR-02/06 để giải quyết thiếu người |
| F01-A03 ASSUMPTION | NGO chấp nhận nhận hàng tập trung và ghi partial receipt; phát sinh thao tác/đối soát | Demo goods integer/fixed units; hỏi quy trình kho/biên nhận hiện tại, không thêm warehouse/lot provenance |
| F01-A04 ASSUMPTION | Evidence/consent đại diện phù hợp và người xác nhận có đủ thông tin độc lập | Chỉ versioned private fixtures. Hỏi ai xem gì, bằng chứng tối thiểu và cách phản đối; chưa dùng người thật hoặc tự kết luận tuân thủ pháp lý |
| F01-A05 ASSUMPTION | Workflow giảm thất lạc/double entry và donor hiểu 4 mốc | Quan sát người dùng thử case giả, ghi điểm nhầm pledged/received/delivered/confirmed; chưa đặt % cải thiện hoặc impact |
| F01-A06 ASSUMPTION | External audit có ích đủ để bù vận hành blockchain | Demo integrity/receipt khi có thật; hỏi giá trị đối chiếu so với audit SQL, không claim chống thông đồng/physical truth |
| F01-A07 OPEN (operational) | Ai chịu trách nhiệm dispute/void và chuyện hàng thực ngoài đời | Demo PA là vai trò mô phỏng, theo O07/O08; Khoa/NGO xác định thẩm quyền trước pilot, không mặc định PA có quyền pháp lý |
| F01-A08 OPEN (implementation) | B04-B10/F04/F05 chưa có trên baseline; C03/Fuji cũng chưa được chứng minh | Checklist là thiết kế, Codex Dũng/Minh cập nhật actual evidence khi merge; không claim đã chạy từ ảnh phân công |
| F01-A09 ASSUMPTION (demo) | Food2/Hygiene2 là bộ dễ diễn giải, khác Rice/Water của F02 | Giữ riêng fixture contexts/IDs; bộ này theo Demo Scenario gốc, không thay existing fixture của nhóm |
| F01-A10 OPEN (research) | Chưa có interview/partner evidence trong repo | Chưa xác thực, R01/R02 còn mở. Chỉ nhóm/người dùng chủ động tiếp cận người thật khi được đồng ý; agent không gửi tin nhắn |

## Trạng thái validation và việc còn thiếu

Không có tên ứng viên thật, lịch hẹn hoặc ghi chú đã có consent được cung cấp trong repo ở baseline; phiên này không liên hệ ai. Không suy rằng có interview chỉ từ việc soạn checklist. R01: chưa có bằng chứng tiếp cận NGO. R02: vấn đề/adoption chưa xác thực; không có phản hồi thật hoặc impact metrics.

Ba vị trí **đề xuất tìm người, không phải ứng viên đã xác định**: (1) người điều phối NGO/quỹ thiện nguyện, (2) người từng nhập hồ sơ/giao hàng thực địa, (3) người từng review/tiếp nhận hoặc xác nhận hỗ trợ. Khoa/nhóm cung cấp ứng viên/lịch/consent khi có; không đặt tên tổ chức hay đối tác giả.

Khi được đồng ý: hỏi công cụ đang dùng và bước mất thời gian; ai tạo/duyệt/xác nhận; cách partial receipt, no-show/dispute; ai xem evidence; cho thử case giả và quan sát lỗi; hỏi phần nào tăng double entry. Ghi anonymized notes với mã interview, ngày, consent, vai trò, câu trả lời thực, quan sát và kết luận có giới hạn. Chưa có thì để trống/chưa xác thực; không ghi synthetic feedback như dữ liệu khảo sát.

## Đối chiếu nghiệm thu và cách tiếp nhận

| Yêu cầu | Bằng chứng trong bộ F01 | Trạng thái |
|---|---|---|
| Phân công 10/11: household/multi-items/full lifecycle | Bảng vai trò, 11 bước, totals và JSON | Sẵn sàng review thiết kế |
| Checklist và kết quả mong đợi | 11 bước +16 nhánh, IDs/API/state trace | Đã soạn, chưa chạy demo |
| Giả định Product/Validation | F01-A01..A10 + R01/R02 + research plan | Chưa xác thực bằng người dùng thật |
| Backlog cũ: 2-3 interview candidates/notes hoặc explicit no-contact | Ghi rõ không có evidence; ba profiles chỉ là kế hoạch | Chưa có candidates/interviews thực; không đánh dấu Done |
| Cross-owner review | Dũng xác nhận interfaces/guards; Minh review khả năng kể chuyện/UI | Chờ review, không tự thay trạng thái task khác |

Codex của Dũng: kiểm tra F01-A01 và các API/negative expectations, đối chiếu source guards thực khi B04-B10/C03 có; giữ business status và chain sync riêng. Codex của Minh: dùng kịch bản để đối chiếu F04/F05/F07/F08/F09 khi được giao, hiển thị mock/real rõ, không bắt đầu các task đó từ tài liệu F01. Khoa: review cách kể chuyện/giả định và bổ sung research evidence khi có.

Chạy kiểm tra tài liệu: `node docs/product/validation/verify-f01.mjs`. Đây là kiểm tra arithmetic/ID/API-reference/role enum của dữ liệu thiết kế, không chứng minh authorization/backend/contract thực tế. HTTP/E2E/NGO research/Fuji đều **Not Run** trong F01. Không cài deps, không scaffold, không commit/push/PR.

## Kết quả kiểm tra thực tế ngày 10/11

| Kiểm tra / lệnh | Kết quả |
|---|---|
| git status; git fetch origin; git log origin/main; git merge --ff-only origin/main | PASS: PR #4 merge f676e41, working tree sạch trước viết; khoa fast-forward đúng origin/main, không tạo commit mới |
| node docs/product/validation/verify-f01.mjs | PASS: 11 bước, 16 nhánh, API references, role enums/source, identities riêng, arithmetic, honest NOT_RUN labels |
| node --check docs/product/validation/verify-f01.mjs | PASS syntax |
| JSON parsing, liên kết Markdown, whitespace, giới hạn file sửa | PASS: đúng 6 file Product/Validation/backlog/risk; product/risk chỉ thêm ghi chú, status của task khác giữ nguyên, không staged changes |
| git diff --check | PASS; Git cảnh báo LF -> CRLF theo cấu hình, không có whitespace error |
| Demo/HTTP/backend authorization, browser E2E, contract/Fuji, NGO interviews | Not Run; không ghi Pass dựa trên fixture checker |

Hai lỗi kỹ thuật của checker ở lần chạy đầu (đường dẫn relative source và marker '+' trong bảng API) đã sửa và chạy lại PASS; không cần đổi application/API. Không có lỗi hoặc xung đột Git còn chặn việc review tài liệu. F01 In Review, chưa Done vì chưa có xác thực người dùng/cross-owner review. Actual effort chưa được ghi nhận đủ tin cậy, không đổi estimate/lịch baseline.

## Nghiệm thu nội bộ trước commit local F01 - 10/11/2026

Theo yêu cầu hiện hành của Khoa, hoàn thành tài liệu task thì kiểm tra và commit local, nhưng giữ In Review khi chưa có cross-owner review. Những câu "chưa commit" ở ledger trước là trạng thái tại thời điểm soạn, không phải giới hạn của lần nghiệm thu này. Chưa push/PR và không bắt đầu F07.

| Tiêu chí bắt buộc theo phân công F01 ngày 10/11 | Đối chiếu / bằng chứng | Nghiệm thu nội bộ |
|---|---|---|
| Demo Household A, nhiều mặt hàng giả lập | Food package2 + Hygiene kit2, fixed units; không PII/tiền thật | Đạt ở mức tài liệu |
| Đầy đủ tạo hồ sơ -> duyệt độc lập -> pledge -> receipt -> delivery -> confirm | 11 bước API và 9 snapshots, có publish/partial receipt/explicit close | Đạt ở mức tài liệu |
| VOLUNTEER, VERIFIER, người giao và DELIVERY_CONFIRMER riêng; không self-approve/self-confirm | Bảng enum/alias docs; identities khác nhau; N01..N04 và các state/evidence guards | Đạt ở mức thiết kế; không claim guard runtime |
| Checklist tình huống và kết quả mong đợi | 11 positive +16 alternative cases; HTTP/state/quantity expectations, tất cả NOT_RUN | Đạt; chạy demo thật không phải đầu ra bắt buộc của F01 hôm nay |
| Danh sách giả định chưa xác thực và ảnh hưởng/đề xuất | F01-A01..A10, R01/R02 mở, người phối hợp và bước xác thực | Đạt việc ghi nhận; không đồng nghĩa assumptions đã validated |
| Lưu Product/Validation và cập nhật bằng chứng chung | Markdown/JSON/checker; Product Requirements, Risk Register, riêng F01 backlog | Đạt; không đổi task/owner/estimate của người khác |

Acceptance baseline 03/11 về tìm 2-3 ứng viên vẫn được giữ nguyên trong backlog. Lần này chỉ nghiệm thu phạm vi kịch bản/giả định của phân công 10/11; ghi rõ nhánh **explicit no-contact** của yêu cầu evidence, chưa có ứng viên/interview thật. Ba profiles trong tài liệu không được coi là người đã tuyển. Vì vậy không tuyên bố hoàn thành toàn bộ chương trình research của baseline, không đóng R01/R02 và không đánh dấu F01 Done. Chưa có candidate hoặc kết quả interview không phải deliverable bắt buộc trong ảnh phân công F01 ngày 10/11, nên không chặn commit bộ tài liệu để review.

Khác tên role đã được ghi F01-A01: ý nghĩa tạo/duyệt/giao/xác nhận nhất quán, backend không có alias mapping được chứng minh. Các vai trò của kịch bản dùng enum hiện tại; không seed tên dài của workflows. Đây là điểm cần thống nhất trước live integration, không phải mâu thuẫn sản phẩm buộc dừng tài liệu F01.

Bổ sung quan sát để không làm Codex khác hiểu sai khả năng demo: AuthService.java hiện gọi `toMeResponse(user, memberships, false)`, nên donorEnabled của API auth hiện false; donor trong scenario chỉ là capability kỳ vọng/giả lập, chưa được provisioning thực tế. Liên hệ F01-A08 (OPEN implementation): Dũng xác nhận donor capability, Minh xử lý shared client trước tích hợp thật. Không thay backend hoặc fixtures của người khác, không tự bỏ guard donorEnabled. Vấn đề không chặn kịch bản F01, cũng không chứng minh F07/API đã hoạt động.

Kết luận: đủ đầu ra tài liệu theo phân công 10/11 để commit local và review. Trạng thái task **In Review**, không Done. NGO validation, thống nhất interfaces/role mapping, cấp donor capability và thử HTTP/UI/Fuji còn OPEN hoặc chưa chạy; không đánh dấu chúng PASS để đạt nghiệm thu giả.
