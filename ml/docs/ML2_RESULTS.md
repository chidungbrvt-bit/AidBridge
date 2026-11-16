# ML2_RESULTS: Logistic Regression so với luật cơ sở (bản demo ML offline, D51)

> DEMO - prototype on synthetic data. Advisory only; a human reviewer decides. An official version needs real consented data and model training.

Ngày: 15/11/2026. Owner: Dũng (ML-2). Reviewer cách đo: Minh (**chưa duyệt**). Nhãn `high_need` do bộ sinh giả lập tạo ra, không phải do người duyệt thật. Mọi con số chỉ chứng minh **quy trình**, không chứng minh độ chính xác ngoài đời thật. Điểm chỉ dùng để **gợi ý thứ tự xem xét**.

## Chạy lại

PowerShell, trong `ml/`, với venv `aidbridge-ml` đang bật (xem [README](../README.md)):

```powershell
$env:PYTHONPATH = "src"; python -m aidbridge_ml.evaluate --seeds 1 2 3 4 5 --n 10000 --folds 5 --out data\eval
```

Lệnh chạy khoảng 5–8 giây. `data\eval\fold_metrics.csv` phải có SHA-256 `be8286ef4b100e13d947755fb5e6adb2860c0b5cb1173578aaa4d686fca6cbe0`. Mã này đã khớp trên Windows (numpy 2.5.4, pandas 3.1.1, scikit-learn 1.9.1) và Linux ngày 15/11.

| File | Nội dung |
|---|---|
| `predictions.csv` | 50.000 dòng: điểm và cờ của mô hình và luật cho mọi hồ sơ test, cột `notice=DEMO` |
| `fold_metrics.csv` | 25 fold × 2 bên chấm |
| `summary.csv` | Trung bình ± độ lệch chuẩn, cộng phần so theo cặp mô hình − luật |
| `slice_summary.csv` | Theo `org_profile` và `zone` |
| `queue_policy_metrics.csv` | Cùng điểm CV, so top 15% toàn fold với quota theo org. Có số suất, tỷ lệ hồ sơ được chọn, tỷ trọng suất, precision và recall |
| `lofo_metrics.csv`, `lofo_summary.csv` | Leave-one-org-out |
| `coefficients.csv` | Trọng số chuẩn hóa của mô hình |
| `error_cases.csv` | 4 ca sai minh họa, cột `notice=DEMO` |
| `run_config.json` | Câu DEMO, phiên bản (`eval-v2`, `generator-v2`, schema `1`, `rules-v1`), giao thức đo, mã hash |

Giao thức đo (chốt ngày 15/11, trước khi xem kết quả) nằm ở [ML_SPEC](ML_SPEC.md) mục g.

## So sánh chính (5 seed × 5 fold, mỗi fold khoảng 2.000 hồ sơ test và 290 ca dương)

| Chỉ số | Mô hình (LR) | Luật `rules-v1` | Mô hình − luật, theo cặp | Số fold mô hình hơn |
|---|---|---|---|---|
| **PR-AUC (chính)** | **0,484 ± 0,032** | 0,419 ± 0,028 | **+0,064 ± 0,015** | **25/25** |
| ROC-AUC | 0,775 ± 0,019 | 0,765 ± 0,018 | +0,010 ± 0,005 | 24/25 |
| Precision top 15% | 0,466 ± 0,023 | 0,450 ± 0,024 | +0,016 ± 0,019 | 21/25 |
| Recall top 15% | 0,488 ± 0,024 | 0,471 ± 0,025 | +0,017 ± 0,020 | 21/25 |
| Precision ở ngưỡng cố định | 0,715 (p ≥ 0,5) | 0,430 (≥ 6 điểm) | | |
| Recall ở ngưỡng cố định | 0,208 | 0,502 | | |
| Số hồ sơ gắn cờ ở ngưỡng (trên ~2.000) | 84 | 334 | | |
| Brier (mốc tham chiếu hằng số 0,123) | 0,098 ± 0,003 | | | |

PR-AUC trung bình theo seed: mô hình 0,469–0,494, luật 0,408–0,434.

**Cách đọc:**

- Mô hình xếp hạng tốt hơn luật một cách ổn định (25/25 fold ở PR-AUC). Lợi thế dồn vào **đầu danh sách**. Ở mức "xem trước 15%", mô hình chỉ hơn khoảng 1,6 điểm % và thua ở 4/25 fold.
- Hai ngưỡng cố định phục vụ hai mục đích khác nhau. Ngưỡng p ≥ 0,5 gắn cờ ít, precision cao. Ngưỡng ≥ 6 điểm gắn cờ nhiều, recall cao. Không so "ai tốt hơn" ở dòng này.
- Tiêu chí ổn định (độ lệch chuẩn AUC < 0,02) **đạt nhưng sát biên**: ROC-AUC 0,019 và 0,018. Phần lớn dao động đến từ kích thước fold. Độ lệch chuẩn của PR-AUC là 0,032; tiêu chí không đặt cho chỉ số này.

## Theo nhóm (cờ top 15% lấy từ hàng chờ chung của fold)

Không có nhóm nào dưới 100 hồ sơ hoặc 20 ca dương trong một fold.

| Nhóm | Hồ sơ / ca dương mỗi fold | PR-AUC mô hình / luật | Tỷ lệ được vào nhóm xem trước, mô hình / luật | Recall top 15%, mô hình / luật |
|---|---|---|---|---|
| Org A | 800 / 95 | 0,426 / 0,371 | **2,9% / 2,8%** | **0,17 / 0,17** |
| Org B | 700 / 103 | 0,532 / 0,445 | 23,1% / 24,4% | 0,65 / 0,64 |
| Org C | 500 / 88 | 0,599 / 0,531 | 23,0% / 21,3% | 0,64 / 0,61 |
| Z1 | 446 / 58 | 0,426 / 0,373 | 7,2% / 6,9% | 0,30 / 0,28 |
| Z2 | 391 / 52 | 0,457 / 0,387 | 10,6% / 10,9% | 0,39 / 0,36 |
| Z3 | 431 / 61 | 0,495 / 0,434 | 17,0% / 17,1% | 0,53 / 0,53 |
| Z4 | 375 / 60 | 0,553 / 0,482 | 21,1% / 20,9% | 0,61 / 0,58 |
| Z5 | 358 / 55 | 0,549 / 0,476 | 20,6% / 20,8% | 0,61 / 0,59 |

**Phát hiện chính: hàng chờ chung bất lợi cho org A.** A có khoảng 12% hồ sơ mang nhãn nhu cầu cao, nhưng chỉ khoảng 2,9% hồ sơ của A lọt top 15% theo mô hình (2,8% theo luật). Tỷ trọng suất toàn hàng chờ dành cho A có mẫu số riêng, là tổng số suất của cả fold. Cả mô hình lẫn luật đều như vậy.

Nguyên nhân gốc nằm ở **cách định nghĩa nhãn**: "nhu cầu cao" là *tương đối trong từng org*. Một hồ sơ nhu cầu cao ở A (đô thị, ít thiên tai) có đặc điểm nhẹ hơn hồ sơ nhu cầu cao ở B hay C, nên khi xếp chung thì luôn bị đẩy xuống. Vùng Z1 (45% hồ sơ là của A) chịu hệ quả tương tự.

**Đối chiếu cách xếp hàng.** `queue_policy_metrics.csv` giữ nguyên hồ sơ, nhãn, điểm ngoài mẫu và tổng số suất của từng fold. Phương án theo org phân bổ suất theo tỷ trọng hồ sơ rồi xếp lại trong từng org với cùng quy tắc hòa điểm. So `global_recall` với `within_org_recall` cho cùng seed, fold, org và scorer. Kết quả LOFO bên dưới còn thay đổi tập huấn luyện, nên mức recall khoảng 0,50 của A không được dùng để quy riêng tác động cho cách xếp hàng.

## Leave-one-org-out (train 2 org, test org còn lại, top 15% trong org test, 5 seed)

| Org test | PR-AUC mô hình LOFO / CV | PR-AUC luật | Recall top 15%, mô hình / luật | Brier mô hình |
|---|---|---|---|---|
| A | 0,430 / 0,426 | 0,368 | 0,50 / 0,49 | 0,099 |
| B | 0,505 / 0,532 | 0,443 | 0,52 / 0,49 | **0,126** |
| C | 0,584 / 0,599 | 0,529 | 0,51 / 0,49 | 0,110 |

Khả năng xếp hạng gần như giữ nguyên khi gặp tổ chức chưa từng thấy, và mô hình vẫn hơn luật ở cả ba org. **Xác suất thì bị lệch** (Brier ở B tăng lên 0,126) vì tỷ lệ dương của tổ chức mới khác lúc huấn luyện. Ở tổ chức mới, có thể dùng thứ tự xếp hạng, nhưng chưa dùng được con số xác suất khi chưa hiệu chỉnh lại.

## Mô hình dựa vào gì (trọng số chuẩn hóa, 25 fold)

| Đặc trưng | Trọng số | Cùng dấu |
|---|---|---|
| `children_under5` | +0,59 | 25/25 |
| `income_band` (cao = khá giả) | −0,55 | 25/25 |
| `recent_disaster_flag` | +0,46 | 25/25 |
| `has_disability_member` | +0,32 | 25/25 |
| `elderly_count` | +0,18 | 25/25 |
| `household_size` | +0,17 | 25/25 |
| **`evidence_completeness`** | **+0,12** | **25/25** |
| `waiting_days` | +0,10 | 25/25 |
| `need_item_count`, `evidence_completeness_missing` | ≈ 0 | đổi dấu (13/25, 10/25) |

**Rò rỉ qua đường vòng.** Bộ sinh **không** dùng `evidence_completeness` khi tạo nhãn, nhưng mô hình vẫn dựa vào nó một cách ổn định. Vì nhãn được gán tương đối trong từng org, với cùng đặc điểm thì hồ sơ kiểu org A (bằng chứng đầy đủ) dễ có nhãn dương hơn. Độ đầy đủ bằng chứng vì thế trở thành biến đại diện cho tổ chức. Với dữ liệu thật, cần kiểm tra xem mô hình có đang "thưởng" cho hồ sơ đầy đủ giấy tờ hay không, vì người khó khăn nhất thường khó nộp đủ.

## Ca sai minh họa (seed 1, fold 1)

| Loại | Hồ sơ | Đặc điểm | Mô hình xếp hạng | Điểm luật |
|---|---|---|---|---|
| Nhãn nhu cầu cao bị bỏ qua | SYN-007631 (org C, Z5) | 1 người, thu nhập band 4, không có yếu tố nào | 2000/2000 | 0 |
| Nhãn nhu cầu cao bị bỏ qua | SYN-005487 (org A, Z1) | 2 người, thu nhập band 4, không có yếu tố nào | 1999/2000 | 0 |
| Nhãn âm được xếp lên đầu | SYN-004951 (org C, Z5) | 3 trẻ dưới 5 tuổi, khuyết tật, thiên tai, thu nhập band 3 | 5 | 6 |
| Nhãn âm được xếp lên đầu | SYN-009911 (org B, Z4) | 6 người, 2 người già, khuyết tật, thiên tai, thu nhập band 0 | 9 | 9 |

Hai ca "bỏ qua" không có dấu hiệu nhu cầu nào, nên nhiều khả năng thuộc **5% nhãn bị lật ngẫu nhiên**. Xếp chúng ở cuối là hành vi đúng. Hai ca "xếp nhầm lên đầu" có nhiều dấu hiệu nhu cầu, và luật cũng chấm cao. Một phần sai số của mô hình là sai số của nhãn.

## Giới hạn

- Nhãn sinh từ bộ sinh do nhóm viết, nên mô hình chủ yếu học lại bộ sinh. Không công bố các con số trên như độ chính xác thật. Không dùng cụm "phát hiện gian lận".
- Mô hình tuyến tính không học được các tương tác mà bộ sinh có (trẻ nhỏ × thu nhập thấp). Ở đây không thử mô hình phức tạp hơn, vì ưu tiên trọng số đọc được.
- Giao thức đo chưa được Minh duyệt. Nếu Minh đổi ngưỡng hoặc mức "quá ít mẫu", chỉ cần sửa hằng số trong `evaluate.py` và chạy lại; mọi thay đổi phải ghi vào ML_SPEC.
- Điểm không được backend đọc, không vào luồng duyệt (BR-02), không lên chain.

## Bàn giao cho ML-4 (Minh)

- Điểm tính sẵn nằm trong `predictions.csv` (cột `model_score`, `model_top`, `rule_score`, `rule_top`). Có thể lọc theo `seed = 1` và `fold = 1` để có khoảng 2.000 hồ sơ.
- Trang demo bắt buộc hiển thị câu DEMO (`schema.DEMO_NOTICE`) và ghi rõ "high_need do bộ sinh tạo".
- Nên hiển thị thứ tự **trong từng tổ chức** thay vì một hàng chờ chung (xem phát hiện chính ở trên). Nếu dùng hàng chờ chung, phải ghi rõ hạn chế với org A.
