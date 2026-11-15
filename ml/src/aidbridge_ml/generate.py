"""Command line entry point: generate the synthetic dataset and its manifest.

    python -m aidbridge_ml.generate --seed 42 --n 10000 --out data

Writes into --out (created if needed):
  cases.csv         full dataset (git-ignored)
  sample_200.csv    first 200 rows, small enough to commit and eyeball
  checksums.sha256  SHA-256 of both CSV files, `sha256sum -c` format
  manifest.json     seed, n, schema/generator versions, per-org rates, missing rates, hashes

Byte-for-byte reproducibility rules (same seed -> same SHA-256 on any OS):
  * CSV text is built in memory with "\n" line endings and a fixed float format, then written
    with write_bytes (write_text would turn "\n" into "\r\n" on Windows);
  * the manifest holds no timestamp or machine path, only inputs and derived numbers.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import platform
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd

from . import schema
from .generator import GENERATOR_VERSION, generate_cases

DEFAULT_SEED = 42
DEFAULT_N = 10_000
SAMPLE_ROWS = 200
FLOAT_FORMAT = "%.6g"            # 3.0 -> "3", 0.73 -> "0.73", NaN -> empty cell

CASES_FILE = "cases.csv"
SAMPLE_FILE = "sample_200.csv"
CHECKSUM_FILE = "checksums.sha256"
MANIFEST_FILE = "manifest.json"


def csv_bytes(frame: pd.DataFrame) -> bytes:
    """Canonical CSV encoding used for every file we hash."""
    return frame.to_csv(index=False, lineterminator="\n", float_format=FLOAT_FORMAT).encode("utf-8")


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build_manifest(frame: pd.DataFrame, seed: int, n: int, hashes: dict[str, str]) -> dict:
    """Summary numbers ML-2 needs without opening the CSV. Rates rounded to 4 decimals."""
    by_org = frame.groupby("org_profile", sort=True)
    nullable = [c.name for c in schema.COLUMNS if c.nullable]
    return {
        "dataset": "AidBridge ML demo synthetic cases (D51)",
        "demo_notice": schema.DEMO_NOTICE,
        "seed": seed,
        "n": n,
        "schema_version": schema.SCHEMA_VERSION,
        "generator_version": GENERATOR_VERSION,
        "rows_by_org": {org: int(len(g)) for org, g in by_org},
        "positive_rate": round(float(frame[schema.LABEL_COLUMN].mean()), 4),
        "positive_rate_by_org": {org: round(float(g[schema.LABEL_COLUMN].mean()), 4) for org, g in by_org},
        "missing_rate_by_column": {c: round(float(frame[c].isna().mean()), 4) for c in nullable},
        "missing_rate_by_org": {
            c: {org: round(float(g[c].isna().mean()), 4) for org, g in by_org} for c in nullable},
        "feature_columns": list(schema.FEATURE_COLUMNS),
        "slice_columns": list(schema.SLICE_COLUMNS),
        "label_column": schema.LABEL_COLUMN,
        "sha256": hashes,
        "library_versions": {
            "python": platform.python_version(),
            "numpy": np.__version__,
            "pandas": pd.__version__,
        },
    }


def write_dataset(frame: pd.DataFrame, out_dir: Path, seed: int) -> dict:
    """Write the four output files and return the manifest. Frame must already be validated."""
    out_dir.mkdir(parents=True, exist_ok=True)
    files = {CASES_FILE: csv_bytes(frame), SAMPLE_FILE: csv_bytes(frame.head(SAMPLE_ROWS))}
    hashes = {name: sha256_hex(data) for name, data in files.items()}
    for name, data in files.items():
        (out_dir / name).write_bytes(data)
    checksums = "".join(f"{hashes[name]}  {name}\n" for name in files)
    (out_dir / CHECKSUM_FILE).write_bytes(checksums.encode("utf-8"))
    manifest = build_manifest(frame, seed, len(frame), hashes)
    manifest_text = json.dumps(manifest, indent=2, sort_keys=True, ensure_ascii=True) + "\n"
    (out_dir / MANIFEST_FILE).write_bytes(manifest_text.encode("utf-8"))
    return manifest


def run(seed: int, n: int, out_dir: Path) -> dict:
    """Generate, validate (inside generate_cases) and write. Nothing is written if validation fails."""
    frame = generate_cases(n, np.random.default_rng(seed))
    return write_dataset(frame, out_dir, seed)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate the AidBridge ML demo synthetic dataset.")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED, help=f"random seed (default {DEFAULT_SEED})")
    parser.add_argument("--n", type=int, default=DEFAULT_N, help=f"number of cases (default {DEFAULT_N})")
    parser.add_argument("--out", type=Path, required=True, help="output directory, e.g. data or data/seed_7")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    started = time.perf_counter()
    manifest = run(args.seed, args.n, args.out)
    elapsed = time.perf_counter() - started
    print(schema.DEMO_NOTICE)
    print(f"Wrote {manifest['n']} cases to {args.out} in {elapsed:.2f}s (seed {args.seed})")
    print(f"Positive rate by org: {manifest['positive_rate_by_org']}")
    print(f"Missing rate by column: {manifest['missing_rate_by_column']}")
    print(f"SHA-256 {CASES_FILE}: {manifest['sha256'][CASES_FILE]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
