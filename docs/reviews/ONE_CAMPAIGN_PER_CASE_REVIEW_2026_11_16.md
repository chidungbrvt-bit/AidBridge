# Task #5 — Cardinality campaign/case, 16/11/2026

**PROPOSED — PENDING APPROVAL cho việc chính thức hóa D15/phương án A; invariant kỹ thuật A đã enforce.** Giữ một case có **0 hoặc 1 campaign**, mỗi campaign bắt buộc thuộc đúng1case và cùng organization. Không phải mỗi case luôn phải có campaign. Không phát hiện thiếu UNIQUE cần migration mới. Đề xuất giữ A cho MVP, không tự nâng D15 thành USER_CONFIRMED. Task5 hoàn tất khảo sát/đề xuất; formal approval D15 vẫn pending, không có blocker implementation mới cho Task7.

## Phiên bản và bằng chứng

Fetch main `15bcba4ec5cce8bf07b929353e68d26b006a0668`, dung `c288f4409023e9f2352082ff91428f324d7445bb`; khoa `27bae6b` trước báo cáo. Đọc source/migrations/test từ remote main, không merge code vào khoa. Giữ cda5360/e98ac9d/27bae6b. Không kiểm tra DB live hoặc chạy runtime.

Các đường dẫn Java tương đối `backend/aidbridge-backend/src/main/java/com/aidbridge/`; migrations tương đối `backend/aidbridge-backend/src/main/resources/db/migration/`.

| Layer | Bằng chứng actual | Kết luận |
|---|---|---|
| DB | `V3__create_campaign_contribution_tables.sql:1–4`: aid_case_id UUID NOT NULL UNIQUE REFERENCES aid_cases(id) | DB theo migrations không cho hai campaign cùng case, bất kể status/org. |
| Upgrade/scope | V10 thêm FK(aid_case_id,organization_id) → aid_cases(id,organization_id), không bỏ UNIQUE V3; V15 thêm UQ(id,aid_case_id) cho delivery FK, không thay UNIQUE(aid_case_id) | Đã rà V1–V18: không có migration bỏ uniqueness theo case. Không nhầm composite UQ V15 là constraint duy nhất. |
| JPA | `campaign/entity/Campaign.java:46–47` @OneToOne, @JoinColumn(unique=true,nullable=false,updatable=false); constructor kiểm cùng org | Mapping thống nhất schema; không chỉ dựa annotation để kết luận DB. |
| Application | `campaign/service/CampaignService.java:59–86` khóa case trước check/write, create callback kiểm existsByAidCase_Id và throw BusinessException.state | POST thứ hai, key mới →409 INVALID_STATE; cùng key/body replay bản gốc. Hai create song song serialize bằng case lock, DB UNIQUE là safety net. |
| Query/public URI | `campaign/repository/CampaignRepository.java:19–20` Optional<Campaign> findByPublicCaseRef; V9 aid_cases.public_ref UNIQUE NOT NULL | Một publicCaseRef → một case → tối đa một campaign; không phát sinh non-unique result trên DB đúng schema. |
| Other dependencies | lockByCaseAndOrganization/findIdByCaseAndOrganization/findScopedCampaignId; StaffCaseService; confirm/dispute/close services | Đều dùng cardinality một campaign/case; phù hợp constraints hiện có, không phải giả định DB chưa bảo đảm. |
| Existing test | CampaignApiIT.java:140–149 secondCampaignForTheSameCaseIsRejected | Assert409 INVALID_STATE, campaigns count1, items2, CAMPAIGN_CREATED audit1. Đã đọc assertions, không chạy lại. |

**Trả lời trực tiếp:** DB đúng migrations **không cho phép** hai campaigns cùng case; application **cũng ngăn**. Chỉ khi schema bị drift/bỏ constraint/restore dữ liệu sai thì có thể tồn tại duplicates; lúc đó Optional query có thể ném NonUniqueResult/IncorrectResultSize (thường thành lỗi5xx), không tự chọn campaign. Đây là rủi ro có điều kiện, không phải finding duplicate đang xảy ra. Chưa đọc DB thực tế nên không khẳng định mọi DB cá nhân đều đúng schema.

## Lựa chọn A so với B

**Giữ A**, phù hợp đồng thời: D15 hiện đề xuất one case/one campaign; DATABASE_DESIGN.md:60 ghi case FK UQ; backlog B06 acceptance có one campaign/case; implementation và test rõ như trên. D49 public audit dùng publicCaseRef, D53 sequence theo case, D55 disputes/delivery refs và D56 không có quyết định nới cardinality. Không tìm thấy quyết định phê duyệt cho nhiều campaigns/case. Căn cứ này đủ khuyến nghị giữ behavior hiện hành, nhưng D15 còn PROPOSED nên không coi merge code là bằng chứng người dùng phê duyệt chính thức decision đó.

A áp dụng mọi status DRAFT/PUBLISHED/FULFILLED/CLOSED, không phải chỉ một campaign đang active. Closing/reopen-dispute cập nhật cùng campaign, không giải phóng slot tạo campaign mới. Không có luồng được duyệt cho xóa campaign để tái sử dụng case; không thêm permanent-delete/partial-unique theo status. Không hiểu invariant này là cấm nhiều commitments/donors/receipts/items trong campaign.

**Không chọn B trong MVP:** cần quyết định nghiệp vụ riêng cho phân bổ nhu cầu/capacity qua campaigns, API public visibility và aggregation audit, xác định campaign khi confirm/dispute/close hoặc staff read. Optional queries phải thiết kế lại; audit D49 vẫn là case-level chứ không được âm thầm đổi thành URI campaign cũ. Chain refs và sequence vẫn case-level nên nhiều campaigns không được reset sequence hoặc dùng campaign làm caseRef; cần ledger/snapshot và reconciliation rõ. Không tự triển khai B hoặc đổi contract blockchain ở Task5.

## Ảnh hưởng / handoff Dũng

- Giữ nguyên findByPublicCaseRef và D49 `/api/public/cases/{publicCaseRef}/audit`; service visibility kiểm campaign PUBLISHED/FULFILLED/CLOSED + org APPROVED. Case chưa có campaign hoặc campaign draft vẫn không công khai audit. CampaignMapper dùng case.public_ref (mapper:57), không dùng campaign UUID làm publicCaseRef.
- Donor routes theo campaignId/commitmentId, totals theo campaign items giữ nguyên. Case-level timeline/job sequence không mơ hồ vì chỉ một campaign. Không đổi quantity semantics, delivery refs, replay hay proof guards.
- **Không có yêu cầu sửa migration/backend mới:** UNIQUE V3 đã enforce cả dữ liệu mới và cũ đã áp migration; V10–V18 giữ constraint. Không sửa checksum V3/V10 hoặc thêm UNIQUE trùng lặp. Existing accepted rows không cần backfill/ref regeneration.
- Nếu kiểm tra DB demo khi đến công việc môi trường mà thấy drift: Dũng xác minh constraints/history và SELECT aid_case_id,count(*) FROM campaigns GROUP BY aid_case_id HAVING count(*)>1. Không có duplicates mới được cân nhắc forward migration khôi phục constraint nếu thật sự thiếu; nếu có duplicates thì dừng, đối soát campaign/items/contributions/receipts/deliveries/audit/jobs trước, không tự xóa/gộp/ref rewrite. Đây là hướng xử lý có điều kiện, không khẳng định hiện có drift hoặc tạo task QA mới.
- API tạo campaign thứ hai tiếp tục409 INVALID_STATE theo guard đã có; không thay mã lỗi hoặc dựa raw SQL unique exception làm HTTP contract. Truy cập SQL ngoài application nếu trùng sẽ bị DB từ chối. Cần approval nếu muốn đổi mô hình sang B, không tự giảm constraint.

## Trạng thái / kiểm chứng

Chỉ source/schema/query/assertion review và diff/static checks; không chạy Java/JUnit/Flyway/PostgreSQL/CI. Không có finding code/schema mới cần sửa. Giữ D15 PROPOSED và link bằng chứng; backlog chỉ bổ sung note cardinality, không đánh B06/C09/C10 hoặc runtime tests Done/PASS. Dũng có đủ invariant kỹ thuật để làm Task7; formal approval cho D15 là dependency quyết định còn lại, không phải thiếu migration. Không làm Task8/review6, không sửa worker/frontend, không push/PR/merge/deploy/transaction.
