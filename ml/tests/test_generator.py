"""Generator tests: reproducibility, size, ranges, rates, no PII, org differences, CLI smoke run."""
from __future__ import annotations

import hashlib
import json
import math
import re
import time

import numpy as np
import pandas as pd
import pytest

from aidbridge_ml import generate, generator, schema
from aidbridge_ml.validate import validate_frame

N = 10_000
SEED = 42

# Tolerances from DAILY_ROADMAP D51 criterion 1 (positive 10-20%, missing 2-5%).
POSITIVE_RANGE = (0.10, 0.20)
MISSING_RANGE = (0.02, 0.05)


@pytest.fixture(scope="module")
def cases() -> pd.DataFrame:
    return generator.generate_cases(N, np.random.default_rng(SEED))


def digest(frame: pd.DataFrame) -> str:
    return hashlib.sha256(generate.csv_bytes(frame)).hexdigest()


def welch_t(a: pd.Series, b: pd.Series) -> float:
    """Welch's t statistic for a difference in means (no scipy needed)."""
    standard_error = math.sqrt(a.var(ddof=1) / len(a) + b.var(ddof=1) / len(b))
    return (a.mean() - b.mean()) / standard_error


# --- Reproducibility -------------------------------------------------------------------------

def test_same_seed_gives_identical_bytes(cases):
    again = generator.generate_cases(N, np.random.default_rng(SEED))
    assert digest(again) == digest(cases)


def test_different_seed_gives_different_bytes(cases):
    other = generator.generate_cases(N, np.random.default_rng(SEED + 1))
    assert digest(other) != digest(cases)


def test_generator_does_not_touch_global_random_state():
    np.random.seed(123)
    expected = np.random.random(3)
    np.random.seed(123)
    generator.generate_cases(500, np.random.default_rng(1))
    assert np.array_equal(np.random.random(3), expected)


def test_rejects_bad_arguments():
    with pytest.raises(TypeError, match="numpy.random.Generator"):
        generator.generate_cases(500, 42)
    with pytest.raises(ValueError, match="between"):
        generator.generate_cases(generator.MIN_ROWS - 1, np.random.default_rng(1))


# --- Shape, ids, ranges ----------------------------------------------------------------------

def test_row_count_and_unique_ids(cases):
    assert len(cases) == N
    assert cases["case_id"].is_unique
    assert cases["case_id"].str.fullmatch(schema.CASE_ID_PATTERN).all()


def test_columns_follow_schema_and_values_are_in_domain(cases):
    assert list(cases.columns) == list(schema.COLUMN_NAMES)
    validate_frame(cases)  # raises on any kind, range, null or cross-column problem
    for column in schema.COLUMNS:
        if column.min_value is not None:
            values = cases[column.name].dropna()
            assert values.min() >= column.min_value and values.max() <= column.max_value, column.name
        if column.categories is not None:
            assert set(cases[column.name]) <= set(column.categories), column.name


def test_rows_per_org_are_exact(cases):
    assert cases["org_profile"].value_counts().to_dict() == generator.org_counts(N) == {
        "A": 4000, "B": 3500, "C": 2500}


def test_case_id_does_not_encode_org_order(cases):
    """Rows are shuffled before numbering, so the first block of ids is not a single org."""
    first_block = cases.head(generator.org_counts(N)["A"])
    assert first_block["org_profile"].nunique() == 3


# --- Rates -----------------------------------------------------------------------------------

def test_positive_rate_per_org_in_range(cases):
    rates = cases.groupby("org_profile")[schema.LABEL_COLUMN].mean()
    low, high = POSITIVE_RANGE
    assert rates.between(low, high).all(), rates.to_dict()


def test_positive_rate_differs_by_org(cases):
    """Spec d: org A < B < C by design (top share 8/11/14% before noise)."""
    rates = cases.groupby("org_profile")[schema.LABEL_COLUMN].mean()
    assert rates["A"] < rates["B"] < rates["C"], rates.to_dict()


@pytest.mark.parametrize("column", [c.name for c in schema.COLUMNS if c.nullable])
def test_missing_rate_in_range_overall_and_per_org(cases, column):
    low, high = MISSING_RANGE
    assert low <= cases[column].isna().mean() <= high
    per_org = cases.groupby("org_profile")[column].apply(lambda s: s.isna().mean())
    assert per_org.between(low, high).all(), per_org.to_dict()


def test_missing_flags_match_missing_values(cases):
    for value_column, flag_column in schema.MISSING_FLAG_FOR.items():
        assert (cases[value_column].isna() == (cases[flag_column] == 1)).all()


# --- Privacy ---------------------------------------------------------------------------------

def test_no_denylisted_column_names(cases):
    from aidbridge_ml.validate import pii_column_names
    assert pii_column_names(cases.columns) == []


def test_no_value_looks_like_email_or_phone(cases):
    # str() on every cell: pandas 3 keeps NaN as NaN under astype(str), which regex cannot read.
    text = [str(value) for value in cases.to_numpy().ravel()]
    for label, pattern in schema.PII_VALUE_PATTERNS.items():
        regex = re.compile(pattern)
        hits = [value for value in text if regex.search(value)]
        assert hits == [], f"{label}: {hits[:5]}"


def test_pii_patterns_do_catch_examples():
    """Guard against a regex that silently matches nothing."""
    assert re.search(schema.PII_VALUE_PATTERNS["email"], "someone@example.org")
    assert re.search(schema.PII_VALUE_PATTERNS["phone_like"], "+84 912 345 678")
    assert not re.search(schema.PII_VALUE_PATTERNS["phone_like"], "SYN-000123")


# --- Org profiles really differ ---------------------------------------------------------------

@pytest.mark.parametrize("org_a, org_b", [("A", "B"), ("A", "C"), ("B", "C")])
def test_household_size_differs_between_orgs(cases, org_a, org_b):
    size = cases.groupby("org_profile")["household_size"]
    t = welch_t(size.get_group(org_a), size.get_group(org_b))
    assert abs(t) > 5, f"{org_a} vs {org_b}: t = {t:.2f}"


def test_disaster_rate_is_highest_in_org_c(cases):
    rates = cases.groupby("org_profile")["recent_disaster_flag"].mean()
    assert rates["C"] > 0.4 > max(rates["A"], rates["B"]), rates.to_dict()


# --- CLI smoke test --------------------------------------------------------------------------

def test_cli_smoke_run_small(tmp_path, capsys):
    started = time.perf_counter()
    exit_code = generate.main(["--seed", "3", "--n", "500", "--out", str(tmp_path)])
    elapsed = time.perf_counter() - started
    assert exit_code == 0 and elapsed < 10
    assert "DEMO" in capsys.readouterr().out

    names = {p.name for p in tmp_path.iterdir()}
    assert names == {generate.CASES_FILE, generate.SAMPLE_FILE, generate.CHECKSUM_FILE, generate.MANIFEST_FILE}

    written = pd.read_csv(tmp_path / generate.CASES_FILE)
    validate_frame(written)                                   # what lands on disk is still valid
    assert len(written) == 500 and len(pd.read_csv(tmp_path / generate.SAMPLE_FILE)) == 200
    assert (tmp_path / generate.CASES_FILE).read_bytes().count(b"\r") == 0

    manifest = json.loads((tmp_path / generate.MANIFEST_FILE).read_text(encoding="utf-8"))
    actual_hash = hashlib.sha256((tmp_path / generate.CASES_FILE).read_bytes()).hexdigest()
    assert manifest["sha256"][generate.CASES_FILE] == actual_hash
    assert f"{actual_hash}  {generate.CASES_FILE}" in (tmp_path / generate.CHECKSUM_FILE).read_text()
    assert manifest["seed"] == 3 and manifest["n"] == 500
    assert manifest["schema_version"] == schema.SCHEMA_VERSION
    assert sum(manifest["rows_by_org"].values()) == 500
    assert "zone" not in manifest["feature_columns"] and "org_profile" not in manifest["feature_columns"]


def test_cli_rerun_same_seed_same_files(tmp_path):
    for folder in ("first", "second"):
        generate.main(["--seed", "9", "--n", "500", "--out", str(tmp_path / folder)])
    for name in (generate.CASES_FILE, generate.SAMPLE_FILE, generate.CHECKSUM_FILE):
        assert (tmp_path / "first" / name).read_bytes() == (tmp_path / "second" / name).read_bytes()
