# DATA_CARD: AidBridge ML demo synthetic cases

> DEMO - prototype on synthetic data. Advisory only; a human reviewer decides. An official version needs real consented data and model training.

Ngày: 14/11/2026. Owner: Dũng (ML-1). Bộ sinh: `generator-v2`, schema `v1`, luật `rules-v1`. Đặc tả đầy đủ xem [ML_SPEC.md](ML_SPEC.md).

## Tóm tắt

| | |
|---|---|
| Nội dung | 10.000 hồ sơ hỗ trợ **giả lập**, mỗi dòng một hồ sơ đã nộp |
| Nguồn | Sinh hoàn toàn bằng chương trình (`aidbridge_ml.generator`). Không lấy cảm hứng hay sao chép từ người thật hoặc fixture trong repo |
| PII | Không có. Không có tên, địa chỉ, số điện thoại, email, ngày sinh, tọa độ hay số giấy tờ |
| Nhiệm vụ | Phân loại nhị phân `high_need`, dùng để **gợi ý thứ tự xem xét** |
| Dùng được cho | Demo quy trình ML; so mô hình với luật cơ sở; kiểm tra pipeline, độ ổn định và chỉ số theo lát |
| Không dùng cho | Ra quyết định về người thật; công bố độ chính xác; huấn luyện mô hình dùng thật; "phát hiện gian lận" |
| Tái lập | Seed 42 và n = 10.000 cho `cases.csv` có SHA-256 `6b23e5690f9f2681cc47bb7bcaaac6fa76a3540e5f57caeb749565c23d4b49fa`. Đã kiểm trên Linux và Windows ngày 14/11 |

## Cột dữ liệu

Thống kê lấy từ seed 42. "TB" là trung bình, chỉ tính trên các ô có giá trị.

| Cột | Vai trò | Miền | TB | Trung vị | Ý nghĩa |
|---|---|---|---|---|---|
| `case_id` | meta | `SYN-000001`… | | | Định danh, đánh số sau khi xáo trộn |
| `household_size` | feature | 1–12 | 3,88 | 4 | Số người trong hộ |
| `children_under5` | feature | 0–4 | 0,40 | 0 | Trẻ dưới 5 tuổi |
| `elderly_count` | feature | 0–4 | 0,47 | 0 | Người cao tuổi |
| `has_disability_member` | feature | 0/1 | 0,13 | | Có thành viên khuyết tật |
| `income_band` | feature | 0–4, có thể trống | 1,77 | 2 | Nhóm thu nhập, 0 = thấp nhất |
| `income_band_missing` | feature | 0/1 | 0,033 | | Cờ thiếu thu nhập |
| `recent_disaster_flag` | feature | 0/1 | 0,20 | | Vừa gặp thiên tai |
| `need_item_count` | feature | 1–20 | 4,25 | 4 | Số dòng nhu cầu |
| `requested_qty_total` | feature | 1–500 | 23,7 | 20 | Tổng số lượng yêu cầu |
| `evidence_completeness` | feature | 0–1, có thể trống | 0,72 | 0,75 | Độ đầy đủ bằng chứng (chất lượng hồ sơ, **không** phải nhu cầu) |
| `evidence_completeness_missing` | feature | 0/1 | 0,029 | | Cờ thiếu bằng chứng |
| `waiting_days` | feature | 0–60 | 8,7 | 5 | Số ngày đã chờ |
| `org_profile` | slice | A/B/C | | | Hồ sơ tổ chức giả lập; **không** phải đặc trưng |
| `zone` | slice | Z1–Z5 | | | Vùng thô; **không** phải đặc trưng (proxy) |
| `high_need` | label | 0/1 | 0,147 | | 1 = nhu cầu cao (≈ `priority = URGENT` sau khi duyệt) |

## Cách sinh

1. Chia đúng 40/35/25% số dòng cho các org A/B/C.
2. Sinh đặc trưng theo phân phối riêng của từng org.
3. Tính latent need (ẩn, không ghi ra file) từ giá trị thật, gồm hai tương tác và nhiễu.
4. Trong từng org, gán nhãn 1 cho phần latent cao nhất (A 8%, B 11%, C 14%), rồi lật 5% nhãn.
5. Che giá trị thiếu và đặt cờ thiếu.
6. Xáo trộn dòng, đánh số `case_id`, chạy `validate_frame`, rồi mới ghi file.

Chi tiết công thức xem ML_SPEC mục d và e.

## Ba hồ sơ tổ chức (seed 42)

| | A (đô thị) | B (nông thôn) | C (vùng thiên tai) |
|---|---|---|---|
| Số hồ sơ | 4.000 | 3.500 | 2.500 |
| Cỡ hộ TB | 2,97 | 4,82 | 4,03 |
| Trẻ dưới 5 TB | 0,23 | 0,56 | 0,42 |
| Người cao tuổi TB | 0,23 | 0,77 | 0,43 |
| Có người khuyết tật | 9% | 17% | 14% |
| Vừa gặp thiên tai | 5% | 10% | 59% |
| Số ngày chờ TB | 7,5 | 13,2 | 4,3 |
| Độ đầy đủ bằng chứng TB | 0,80 | 0,72 | 0,60 |
| Vùng phổ biến nhất | Z1 (45%) | Z3 (31%) | Z5 (35%) |
| **Tỷ lệ dương `high_need`** | **12,8%** | **15,2%** | **16,9%** |
| Thiếu `income_band` | 3,0% | 3,8% | 3,1% |
| Thiếu `evidence_completeness` | 2,7% | 2,9% | 3,2% |

Tỷ lệ dương theo vùng (seed 42) là Z1 14,5%, Z2 13,5%, Z3 14,3%, Z4 16,3%, Z5 15,0%. Vùng không nằm trong công thức sinh nhãn; chênh lệch đến từ việc mỗi vùng có tỷ lệ các org khác nhau.

## Độ ổn định qua 5 seed (1–5, đo trên Linux)

| Chỉ số | Trung bình | Độ lệch chuẩn |
|---|---|---|
| Tỷ lệ dương, org A / B / C | 11,9% / 14,7% / 17,7% | 0,40 / 0,53 / 0,28 điểm % |
| Thiếu `income_band`, toàn bộ | 3,38% | 0,33 điểm % |
| Thiếu `income_band`, org B | 4,10% | 0,48 điểm % |
| Thiếu `evidence_completeness`, toàn bộ | 3,00% | 0,08 điểm % |
| ROC-AUC luật cơ sở, toàn bộ | 0,765 | 0,003 |
| PR-AUC luật cơ sở, toàn bộ | 0,418 | 0,010 |

ROC-AUC của luật theo org nằm trong khoảng 0,72–0,82 ở mọi seed. Luật làm kém nhất ở org A, nơi ít thiên tai và nhu cầu phân biệt bằng các yếu tố mà luật cắt thô.

## Hạn chế trung thực

- **Nhãn sinh từ luật.** Mô hình chủ yếu học lại công thức do nhóm viết. Mọi chỉ số chỉ chứng minh quy trình, không chứng minh độ chính xác thật.
- **Phân phối là giả định** do nhóm đặt cho hợp lý, không đến từ khảo sát hay dữ liệu tổ chức thật.
- **Nhiễu nhãn 5% là đối xứng và ngẫu nhiên.** Sai sót của người duyệt thật thường có hệ thống và có thể thiên lệch theo nhóm.
- **Thiếu dữ liệu được mô phỏng đơn giản.** Thực tế, thiếu thường phụ thuộc chính nhu cầu: người khó khăn nhất thường khó nộp đủ giấy tờ.
- **Không có yếu tố thời gian.** Mỗi hồ sơ là một ảnh chụp; không có hồ sơ được cập nhật, nộp lại hay trùng.
- **Cỡ mẫu và tỷ lệ org cố định** (40/35/25%). Không mô phỏng một org rất nhỏ.

## Cách chạy lại

PowerShell, trong thư mục `ml/`, với venv `aidbridge-ml` đang bật:

```powershell
$env:PYTHONPATH = "src"
python -m aidbridge_ml.generate --seed 42 --n 10000 --out data
```

Kết quả gồm `data/cases.csv`, `sample_200.csv`, `checksums.sha256` và `manifest.json`. `manifest.json` ghi seed, phiên bản, tỷ lệ theo org và mã hash. Kiểm tra mã hash bằng `Get-FileHash data\cases.csv -Algorithm SHA256`; mã phải trùng với giá trị trong `checksums.sha256`. Hai máy cho cùng mã hash khi dùng đúng phiên bản trong `requirements.txt`.
