"""ML-2 evaluation tests: ranking tie-break, no test leakage into preprocessing, and a CLI smoke run.

The smoke run uses tiny data, so it proves the pipeline runs end to end and repeats exactly; it says
nothing about model quality (acceptance needs the full 5 seeds x 5 folds run).
"""
from __future__ import annotations

import json
import time

import numpy as np
import pandas as pd
import pytest

from aidbridge_ml import evaluate, generator, schema

OUTPUT_FILES = {"predictions.csv", "fold_metrics.csv", "summary.csv", "slice_summary.csv",
                "lofo_metrics.csv", "lofo_summary.csv", "coefficients.csv", "error_cases.csv",
                "run_config.json"}


@pytest.fixture(scope="module")
def small_data() -> pd.DataFrame:
    return generator.generate_cases(600, np.random.default_rng(42))


def test_top_share_flags_tie_break():
    """Score desc, then waiting_days desc, then case_id asc; ceil(15%) of 7 = 2 flagged."""
    score = [0.9, 0.5, 0.5, 0.5, 0.1, 0.1, 0.2]
    waiting = [0, 3, 9, 9, 0, 0, 0]
    case_id = ["SYN-000001", "SYN-000002", "SYN-000004", "SYN-000003", "SYN-000005", "SYN-000006", "SYN-000007"]
    flags = evaluate.top_share_flags(score, waiting, case_id)
    assert flags.tolist() == [1, 0, 0, 1, 0, 0, 0]   # SYN-000003 beats SYN-000004 on case_id


def test_model_never_sees_slice_or_id_columns():
    assert set(evaluate.FEATURES) == set(schema.FEATURE_COLUMNS)
    assert not {"case_id", "org_profile", "zone", schema.LABEL_COLUMN} & set(evaluate.FEATURES)


def test_preprocessing_is_fitted_on_train_only(small_data):
    train, test = small_data.iloc[:450].reset_index(drop=True), small_data.iloc[450:].reset_index(drop=True)
    _, _, model = evaluate.evaluate_split(train, test)
    scaler_means = model.named_steps["scale"].mean_
    imputed_train = model.named_steps["impute"].transform(train[list(evaluate.FEATURES)])
    assert np.allclose(scaler_means, imputed_train.mean(axis=0))
    medians = model.named_steps["impute"].statistics_
    assert medians[evaluate.FEATURES.index("income_band")] == train["income_band"].median()


def test_model_and_rule_score_the_same_cases_including_missing_values(small_data):
    train, test = small_data.iloc[:450].reset_index(drop=True), small_data.iloc[450:].reset_index(drop=True)
    assert test["income_band"].isna().any() or train["income_band"].isna().any()
    scored, metrics, _ = evaluate.evaluate_split(train, test)
    assert scored["case_id"].tolist() == test["case_id"].tolist()
    assert not scored[["model_score", "rule_score"]].isna().any().any()
    assert [m["scorer"] for m in metrics] == ["model", "rule"]
    assert {m["n"] for m in metrics} == {len(test)}


def test_evaluate_refuses_invalid_data(small_data):
    from aidbridge_ml.validate import SchemaError
    bad = small_data.assign(phone="x")
    with pytest.raises(SchemaError, match="PII-like"):
        evaluate.evaluate_split(bad, small_data)


def test_cli_smoke_run_is_complete_and_repeatable(tmp_path, capsys):
    args = ["--seeds", "42", "--n", "600", "--folds", "3"]
    started = time.perf_counter()
    assert evaluate.main([*args, "--out", str(tmp_path / "first")]) == 0
    assert time.perf_counter() - started < 60
    assert "DEMO" in capsys.readouterr().out
    evaluate.main([*args, "--out", str(tmp_path / "second")])

    first, second = tmp_path / "first", tmp_path / "second"
    assert {p.name for p in first.iterdir()} == OUTPUT_FILES
    for name in OUTPUT_FILES:
        assert (first / name).read_bytes() == (second / name).read_bytes(), name

    fold_metrics = pd.read_csv(first / "fold_metrics.csv")
    assert len(fold_metrics) == 3 * 2                       # folds x scorers
    for column in ("pr_auc", "roc_auc", "precision_top", "recall_top",
                   "precision_threshold", "recall_threshold", "brier"):
        assert column in fold_metrics
    summary = pd.read_csv(first / "summary.csv")
    paired = summary[summary["scorer"] == "model_minus_rule"]
    assert set(paired["metric"]) == set(evaluate.PAIRED_METRICS)

    slices = pd.read_csv(first / "slice_summary.csv")
    assert set(slices["slice"]) == {"org_profile", "zone"}
    assert slices["folds_too_small"].sum() > 0              # tiny folds must be flagged, not scored

    lofo = pd.read_csv(first / "lofo_metrics.csv")
    assert sorted(lofo["test_org"].unique()) == ["A", "B", "C"]

    assert (pd.read_csv(first / "predictions.csv")["notice"] == "DEMO").all()
    config = json.loads((first / "run_config.json").read_text(encoding="utf-8"))
    assert config["demo_notice"] == schema.DEMO_NOTICE
    assert "synthetic generator" in config["label_note"]
    assert config["versions"]["generator"] == generator.GENERATOR_VERSION
    assert config["protocol"]["model_threshold"] == evaluate.MODEL_THRESHOLD
