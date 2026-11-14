# AidBridge ML demo (D51)

> DEMO - prototype on synthetic data. Advisory only; a human reviewer decides. An official version needs real consented data and model training.

Bản demo ML offline: điểm ưu tiên gợi ý cho hồ sơ hỗ trợ, chạy trên **dữ liệu giả lập**. Thư mục này đứng riêng: không được backend import, không nối vào luồng duyệt hay phân bổ, không ghi lên chain, không có PII.

- Đặc tả: [docs/ML_SPEC.md](docs/ML_SPEC.md)
- Thẻ dữ liệu: [docs/DATA_CARD.md](docs/DATA_CARD.md)

## Ba lệnh (Windows PowerShell, chạy trong thư mục `ml/`)

**1. Môi trường.** Cần Python 3.13. Venv đặt **ngoài repo**.

Máy đã có venv `aidbridge-ml`, chỉ cần kích hoạt:

```powershell
& "$HOME\.venvs\aidbridge-ml\Scripts\Activate.ps1"
```

Máy mới, tạo venv và cài đúng phiên bản trong một dòng:

```powershell
python -m venv "$HOME\.venvs\aidbridge-ml"; & "$HOME\.venvs\aidbridge-ml\Scripts\Activate.ps1"; python -m pip install -r requirements.txt
```

Nếu PowerShell báo "running scripts is disabled", chạy `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` rồi chạy lại. Lệnh này chỉ có hiệu lực trong cửa sổ hiện tại.

**2. Sinh dữ liệu** (khoảng 1 giây):

```powershell
$env:PYTHONPATH = "src"; python -m aidbridge_ml.generate --seed 42 --n 10000 --out data
```

Lệnh ghi ra 4 file trong `data/`:

| File | Nội dung |
|---|---|
| `cases.csv` | Toàn bộ dữ liệu; không commit |
| `sample_200.csv` | 200 dòng đầu |
| `checksums.sha256` | Mã SHA-256 của các file CSV |
| `manifest.json` | Seed, phiên bản, tỷ lệ theo org, mã hash |

Seed 42 phải cho `cases.csv` có SHA-256 bắt đầu bằng `6b23e569`. Kiểm bằng `Get-FileHash data\cases.csv -Algorithm SHA256`.

**3. Chạy test** (khoảng 30 giây, 82 test):

```powershell
python -m pytest -q
```

## Tham số `generate`

| Tham số | Mặc định | Ý nghĩa |
|---|---|---|
| `--seed` | 42 | Seed của `numpy.random.default_rng`; cùng seed cho cùng file đến từng byte |
| `--n` | 10000 | Số hồ sơ, từ 50 đến 999.999 |
| `--out` | (bắt buộc) | Thư mục đầu ra; khi chạy nhiều seed, dùng thư mục con như `data\seed_7` |

## Mã nguồn

| File | Vai trò |
|---|---|
| `src/aidbridge_ml/schema.py` | Hợp đồng cột, nhóm cột (feature/slice/label/meta), danh sách cấm PII, `DEMO_NOTICE` |
| `src/aidbridge_ml/validate.py` | Kiểm tra fail-fast: dừng ngay khi gặp cột lạ, cột PII, giá trị ngoài miền hoặc sai kiểu |
| `src/aidbridge_ml/rules.py` | Luật cơ sở `rules-v1`: điểm nguyên 0–12, xếp hạng tất định |
| `src/aidbridge_ml/generator.py` | Bộ sinh dữ liệu `generator-v2` |
| `src/aidbridge_ml/generate.py` | CLI: sinh, kiểm tra, ghi CSV và manifest |
| `tests/` | Test cho luật, schema, bộ sinh, cộng một smoke test CLI |

Chưa có: huấn luyện mô hình (ML-2), trang hiển thị (ML-4).
