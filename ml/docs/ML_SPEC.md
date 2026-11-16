# ML_SPEC: Điểm ưu tiên gợi ý (bản demo ML offline, D51)

Ngày: 14/11/2026. Owner: Dũng (ML-1). Trạng thái: bản đặc tả đã được owner duyệt khi làm ML-1, **chờ Minh duyệt schema và đặc trưng**. Nguồn quyết định: [D51](../../docs/planning/DECISION_LOG.md) (USER_CONFIRMED 11/11/2026), tiêu chí "chạy ổn" trong [DAILY_ROADMAP](../../docs/planning/DAILY_ROADMAP.md#gói-demo-ml-d51--tính-riêng-ngoài-168h), thiết kế nền trong [AI_AID_MATCHING_DESIGN](../../docs/architecture/AI_AID_MATCHING_DESIGN.md) mục 6, 7, 13 và 14.

## a. Mục đích, phạm vi, điều không làm

**Mục đích.** Ước lượng xác suất một hồ sơ đã nộp thuộc nhóm "nhu cầu cao". Nhóm này tương ứng với `aid_cases.priority = URGENT` sau khi người duyệt xem xét. Điểm chỉ dùng để **gợi ý thứ tự xem xét**.

**Phạm vi ML-1.** Gồm đặc tả, schema và danh sách cấm PII, luật cơ sở, bộ sinh dữ liệu giả lập, test, tài liệu dữ liệu và bàn giao. ML-1 **không** huấn luyện mô hình; việc đó thuộc ML-2.

**Không làm:**

- Không duyệt, không từ chối, không đổi trạng thái hồ sơ. Người duyệt độc lập quyết định (BR-02).
- Không quyết định phân bổ hàng, không "phát hiện gian lận".
- Không import vào backend, không nối vào luồng duyệt hay phân bổ, không ghi lên chain, không dùng PII.
- Không thuộc sáu P0, nằm ngoài Definition of Done của P0 và ngoài 168h.

**Câu chữ bắt buộc** trên mọi nơi hiển thị điểm. Câu được định nghĩa một lần trong `schema.DEMO_NOTICE`:

> DEMO - prototype on synthetic data. Advisory only; a human reviewer decides. An official version needs real consented data and model training.

## b. Đơn vị dữ liệu

- Mỗi dòng là một hồ sơ giả lập đã nộp.
- `case_id` có dạng `SYN-000001`, là duy nhất và được đánh số **sau khi xáo trộn** dòng.
- Không có tên, địa chỉ, số điện thoại, email, ngày sinh, tọa độ hay số giấy tờ. `validate.py` từ chối tên cột chứa các token PII (danh sách `schema.PII_NAME_TOKENS`), và từ chối giá trị có dạng email hoặc số điện thoại.

## c. Cột dữ liệu (schema v1)

| Cột | Nhóm | Kiểu | Miền | Được thiếu | Ý nghĩa |
|---|---|---|---|---|---|
| `case_id` | meta | id | `SYN-\d{6}` | không | Định danh giả lập; không bao giờ là đặc trưng |
| `household_size` | feature | int | 1–12 | không | Số người trong hộ |
| `children_under5` | feature | int | 0–4 | không | Số trẻ dưới 5 tuổi |
| `elderly_count` | feature | int | 0–4 | không | Số người cao tuổi |
| `has_disability_member` | feature | 0/1 | | không | Có thành viên khuyết tật |
| `income_band` | feature | int | 0–4 | có (~3%) | Nhóm thu nhập thô, 0 = thấp nhất |
| `income_band_missing` | feature | 0/1 | | không | 1 khi `income_band` trống |
| `recent_disaster_flag` | feature | 0/1 | | không | Vừa bị ảnh hưởng thiên tai |
| `need_item_count` | feature | int | 1–20 | không | Số dòng nhu cầu (API cho phép 1–20) |
| `requested_qty_total` | feature | int | 1–500 | không | Tổng số lượng yêu cầu |
| `evidence_completeness` | feature | float | 0–1 | có (~3%) | Tỷ lệ bằng chứng có mặt; là chất lượng hồ sơ, không phải nhu cầu |
| `evidence_completeness_missing` | feature | 0/1 | | không | 1 khi `evidence_completeness` trống |
| `waiting_days` | feature | int | 0–60 | không | Số ngày đã chờ tại thời điểm chấm |
| `org_profile` | slice | A/B/C | | không | Hồ sơ tổ chức giả lập; chỉ dùng để chia lát |
| `zone` | slice | Z1–Z5 | | không | Vùng thô; chỉ dùng để chia lát |
| `high_need` | label | 0/1 | | không | 1 = nhu cầu cao |

Ràng buộc chéo: `children_under5 + elderly_count <= household_size`. Cờ thiếu bằng 1 **đúng khi** giá trị tương ứng trống.

**Vì sao `zone` không phải đặc trưng.** Địa bàn thường gắn với dân tộc, tôn giáo hay mức sống của cả vùng. Đó là một biến đại diện (proxy) dễ dẫn tới phân biệt đối xử. Ta vẫn giữ `zone` để đo chênh lệch kết quả giữa các vùng. Trong dữ liệu giả lập, `zone` tương quan với `org_profile` (ví dụ 45% hồ sơ org A ở Z1), nên nó cũng gián tiếp mang theo tỷ lệ dương của org. Đây thêm một lý do để không đưa `zone` vào mô hình.

## d. Nhãn `high_need`

Bộ sinh tạo nhãn theo ba bước:

1. Tính **latent need** từ giá trị *thật*, tức là trước khi che thiếu:
   - Hiệu ứng tuyến tính: thu nhập thấp, trẻ nhỏ, người già, khuyết tật, thiên tai, cỡ hộ, `log1p(waiting_days)`.
   - Hai tương tác: (trẻ nhỏ × thu nhập band 0–1) và (thiên tai × hộ từ 5 người).
   - Nhiễu Gaussian với độ lệch chuẩn 0,8.
   - Không dùng `evidence_completeness`, `zone` hay `org_profile`.
2. **Trong từng org**, gán `high_need = 1` cho phần latent cao nhất: A 8%, B 11%, C 14%. Độ lệch theo org nằm ở tỷ lệ này.
3. Lật ngẫu nhiên **5%** nhãn theo cả hai chiều.

Tỷ lệ dương kỳ vọng là `top × 0,95 + (1 − top) × 0,05`, tức A ≈ 12,2%, B ≈ 15,0%, C ≈ 17,8%. Cả ba nằm trong khoảng mục tiêu 10–20%. Latent need **không bao giờ được ghi ra file**.

## e. Dữ liệu thiếu

| Cột | Tỷ lệ đặt | Cơ chế |
|---|---|---|
| `income_band` | A 3%, B 4%, C 3% | Phụ thuộc org (org B thiếu nhiều hơn) |
| `evidence_completeness` | 3% mọi org | Hoàn toàn ngẫu nhiên |

Giá trị bị che *sau khi* đã tính latent, nên phần bị thiếu vẫn có nhu cầu thật phía sau. Mỗi cột thiếu có cờ thiếu đi kèm.

Thay đổi ngày 14/11: tỷ lệ thiếu `income_band` của org B hạ từ 4,5% xuống 4,0%. Ở mức 4,5%, khoảng 1 trong 12 seed vượt biên 5%.

## f. Luật cơ sở (`rules.py`, `rules-v1`)

Luật là bảng điểm nguyên viết tay, tối đa 12 điểm. Luật chỉ dùng đặc trưng quan sát được, và **cố ý khác bộ sinh**: không có tương tác, cắt mốc thô, không dùng `evidence_completeness`.

| Yếu tố | Điểm |
|---|---|
| `income_band` 0 / 1 / 2 / 3–4 | 3 / 2 / 1 / 0 |
| `income_band` thiếu | 1 (trung tính) |
| `children_under5 >= 1` | 2 |
| `has_disability_member = 1` | 2 |
| `recent_disaster_flag = 1` | 2 |
| `elderly_count >= 1` | 1 |
| `household_size >= 6` | 1 |
| `waiting_days >= 14` | 1 |

**Xếp hạng.** Điểm cao lên trước. Bằng điểm thì `waiting_days` dài hơn lên trước, sau đó xếp theo `case_id` tăng dần. Kết quả hoàn toàn tất định.

**Cờ.** `rule_flag_top` đánh dấu 15% đầu bảng xếp hạng. `rule_flag_threshold` đánh dấu hồ sơ có điểm từ 6 trở lên, dùng làm mốc tham chiếu.

## g. Thước đo cho ML-2

| Thước đo | Ghi chú |
|---|---|
| PR-AUC | Thước đo chính vì lớp dương chỉ chiếm ~15%. Mốc đoán bừa bằng tỷ lệ dương |
| ROC-AUC | Mốc đoán bừa là 0,5 |
| Brier score | Độ tin cậy của xác suất. Mốc dự đoán hằng số ≈ p(1 − p) ≈ 0,125 |
| Precision và recall | Tại top 15% và tại ngưỡng cố định |

**Cách đánh giá:**

- 5 seed nhân kiểm định chéo 5 fold, phân tầng theo nhãn. Báo trung bình ± độ lệch chuẩn cho cả mô hình và luật, trên cùng các fold.
- Ngưỡng ổn định đề xuất: độ lệch chuẩn của AUC dưới 0,02.
- Leave-one-org-out: học trên hai org, chấm trên org còn lại.
- Chỉ số theo từng `zone` và từng `org_profile`.
- Tiền xử lý (điền thiếu, chuẩn hóa) phải được fit **trong từng fold**.

**Giao thức đo ML-2 (chốt 15/11 trước khi xem kết quả; chờ Minh duyệt).** Hằng số nằm trong `aidbridge_ml/evaluate.py`.

| # | Quyết định | Giá trị |
|---|---|---|
| 1 | Dữ liệu | Seed 1–5, n = 10.000, `generator-v2`, schema `1`, `rules-v1`. Seed 42 chỉ dùng cho smoke test |
| 2 | Chia fold | `StratifiedKFold(5, shuffle=True, random_state=seed)`; mô hình và luật dùng cùng fold |
| 3 | PR-AUC | `average_precision_score` (tổng bậc thang, không nội suy hình thang) |
| 4 | Top 15% | k = ceil(0,15 × số hồ sơ test của fold); xếp điểm giảm dần, hòa thì `waiting_days` giảm dần, rồi `case_id` tăng dần |
| 5 | Ngưỡng cố định | Luật: điểm ≥ 6. Mô hình: xác suất ≥ 0,5 |
| 6 | Brier | Chỉ cho mô hình; mốc tham chiếu = dự đoán hằng số bằng tỷ lệ dương của tập train |
| 7 | Mô hình | `SimpleImputer(median)` → `StandardScaler` → `LogisticRegression(C=1.0, max_iter=1000)`; không dò tham số, không `class_weight`; fit trên phần train của từng fold |
| 8 | Theo nhóm | Cờ top 15% lấy từ xếp hạng chung của fold rồi mới đo theo `org_profile`/`zone`; nhóm dưới 100 hồ sơ hoặc dưới 20 ca dương trong fold ghi "quá ít mẫu" |
| 9 | Leave-one-org-out | Train 2 org, test org còn lại; 5 seed × 3 org; top 15% lấy trong org test |

**Mốc của luật cơ sở**, đo trong ML-1 ngày 14/11. Seed 42 đã chạy trên cả Linux và Windows, kết quả trùng nhau; seed 1–5 đo trên Linux:

| | ROC-AUC | PR-AUC |
|---|---|---|
| Seed 42, toàn bộ | 0,745 | 0,405 |
| Seed 42, org A / B / C | 0,723 / 0,784 / 0,792 | 0,339 / 0,451 / 0,510 |
| Seed 1–5, toàn bộ, trung bình ± độ lệch chuẩn | 0,765 ± 0,003 | 0,418 ± 0,010 |

Mọi giá trị đều nằm trong khoảng mục tiêu 0,70–0,85: dữ liệu không quá dễ, cũng không quá khó.

## h. Cảnh báo trung thực

- Nhãn sinh từ một công thức do chính nhóm viết, nên mô hình chủ yếu học lại bộ sinh. Kết quả chỉ chứng minh **quy trình và phương pháp**, không chứng minh độ chính xác thật.
- Phân phối của các org là giả định do nhóm đặt, không đến từ khảo sát.
- Không dùng cụm "phát hiện gian lận". Không công bố bất kỳ con số nào như kết quả thực tế.
- Để thành bản chính thức cần: dữ liệu thật có sự đồng ý, nhãn do người duyệt độc lập gán, đánh giá độc lập, kiểm tra thiên lệch theo nhóm, thời gian huấn luyện và theo dõi, người duyệt quyết định, và quy trình khiếu nại. Phần này do ML-3 viết chi tiết.

## i. Cấu trúc thư mục

```
ml/
  README.md                 lệnh môi trường, sinh dữ liệu, chạy test
  requirements.txt          phiên bản đã ghim
  pyproject.toml            cấu hình pytest (pythonpath = src)
  .gitignore                venv, pycache, data/* trừ mẫu nhỏ, checksum và manifest
  docs/ML_SPEC.md           tài liệu này
  docs/DATA_CARD.md         thẻ dữ liệu
  src/aidbridge_ml/
    schema.py               hợp đồng cột, DEMO_NOTICE, danh sách cấm PII
    validate.py             kiểm tra fail-fast
    rules.py                luật cơ sở
    generator.py            bộ sinh dữ liệu (generator-v2)
    generate.py             CLI: sinh, kiểm tra, ghi CSV và manifest
  tests/
    conftest.py, test_rules.py, test_schema.py, test_generator.py
  data/                     đầu ra; chỉ commit sample_200.csv, checksums.sha256, manifest.json
```
