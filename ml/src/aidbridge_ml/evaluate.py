"""Offline evaluation of the advisory score (ML-2, D51): Logistic Regression vs rules-v1.

Protocol constants below were fixed on 15/11/2026 BEFORE any result was seen (ML_SPEC section g).
Changing one after looking at results invalidates the comparison; record any change in ML_SPEC.

Core idea per fold: fit the model on the train part only, then score the SAME test cases with the
model and with rules-v1, rank both with the same tie-break, and compute the same metrics.
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
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
import sklearn
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from . import generator, rules, schema
from .queue_metrics import SCORERS, compare_queues, top_share_flags
from .validate import validate_frame

EVAL_VERSION = "eval-v2"

# --- Protocol (ML_SPEC g) ---------------------------------------------------------------------
TOP_SHARE = rules.TOP_SHARE                 # 0.15, same share as the rule flag
MODEL_THRESHOLD = 0.5                       # fixed probability cut-off for the model
RULE_THRESHOLD = rules.FIXED_THRESHOLD      # 6 points, fixed in ML-1
MIN_GROUP_ROWS = 100                        # below this a slice is reported as too small
MIN_GROUP_POSITIVES = 20
FEATURES: tuple[str, ...] = schema.FEATURE_COLUMNS
LABEL = schema.LABEL_COLUMN
DEFAULT_SEEDS = (1, 2, 3, 4, 5)
DEFAULT_N = 10_000
DEFAULT_FOLDS = 5

# Metrics summarised per scorer, and the subset compared model-minus-rule fold by fold.
SUMMARY_METRICS = ("pr_auc", "roc_auc", "precision_top", "recall_top",
                   "precision_threshold", "recall_threshold", "flagged_threshold", "brier", "brier_reference")
PAIRED_METRICS = ("pr_auc", "roc_auc", "precision_top", "recall_top")

LABEL_NOTE = "high_need is produced by the synthetic generator, not by human reviewers"


def build_model() -> Pipeline:
    """Imputer -> scaler -> logistic regression. No tuning, no class weights (keeps probabilities honest)."""
    return Pipeline([
        ("impute", SimpleImputer(strategy="median")),
        ("scale", StandardScaler()),
        ("model", LogisticRegression(C=1.0, max_iter=1000)),
    ])


def score_test_cases(model: Pipeline, test: pd.DataFrame) -> pd.DataFrame:
    """One row per test case with both scores and all flags. `model` must already be fitted."""
    proba = model.predict_proba(test[list(FEATURES)])[:, 1]
    rule_score = rules.score_frame(test).to_numpy()
    out = pd.DataFrame({
        "case_id": test["case_id"].to_numpy(),
        "org_profile": test["org_profile"].to_numpy(),
        "zone": test["zone"].to_numpy(),
        LABEL: test[LABEL].to_numpy(),
        "waiting_days": test["waiting_days"].to_numpy(),
        "model_score": proba,
        "rule_score": rule_score,
    })
    for scorer in SCORERS:
        out[f"{scorer}_top"] = top_share_flags(out[f"{scorer}_score"], out["waiting_days"], out["case_id"])
    out["model_threshold"] = (out["model_score"] >= MODEL_THRESHOLD).astype("int64")
    out["rule_threshold"] = (out["rule_score"] >= RULE_THRESHOLD).astype("int64")
    return out


def _share(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else float("nan")


def compute_metrics(scored: pd.DataFrame, scorer: str, train_prevalence: float | None = None) -> dict:
    """Metrics for one scorer on already-flagged cases (a whole fold or one slice of it)."""
    y = scored[LABEL].to_numpy()
    score = scored[f"{scorer}_score"].to_numpy()
    positives = int(y.sum())
    result = {"scorer": scorer, "n": len(y), "positives": positives}
    both_classes = 0 < positives < len(y)
    result["pr_auc"] = average_precision_score(y, score) if both_classes else float("nan")
    result["roc_auc"] = roc_auc_score(y, score) if both_classes else float("nan")
    for flag in ("top", "threshold"):
        flagged = scored[f"{scorer}_{flag}"].to_numpy() == 1
        hits = int(y[flagged].sum())
        result[f"flagged_{flag}"] = int(flagged.sum())
        result[f"precision_{flag}"] = _share(hits, int(flagged.sum()))
        result[f"recall_{flag}"] = _share(hits, positives)
    if scorer == "model":
        result["brier"] = brier_score_loss(y, score)
        if train_prevalence is not None:
            result["brier_reference"] = brier_score_loss(y, np.full(len(y), train_prevalence))
    return result


def evaluate_split(train: pd.DataFrame, test: pd.DataFrame) -> tuple[pd.DataFrame, list[dict], Pipeline]:
    """Fit on train, score test with model and rules. Returns (scored test cases, metric rows, fitted model)."""
    validate_frame(train)
    validate_frame(test)
    model = build_model().fit(train[list(FEATURES)], train[LABEL])
    scored = score_test_cases(model, test)
    prevalence = float(train[LABEL].mean())
    return scored, [compute_metrics(scored, scorer, prevalence) for scorer in SCORERS], model


# --- Cross-validation -------------------------------------------------------------------------

def cross_validate(seeds=DEFAULT_SEEDS, n: int = DEFAULT_N, folds: int = DEFAULT_FOLDS
                   ) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """seeds x folds stratified CV.

    Returns (scored test cases of every fold, one metric row per fold+scorer, standardized model
    coefficients per fold). The seed drives both the generated dataset and the fold split, so a
    (seed, fold) pair always names the same train/test cases.
    """
    predictions, rows, coefficients = [], [], []
    for seed in seeds:
        data = generator.generate_cases(n, np.random.default_rng(seed))
        splitter = StratifiedKFold(n_splits=folds, shuffle=True, random_state=seed)
        for fold, (train_idx, test_idx) in enumerate(splitter.split(data, data[LABEL]), start=1):
            train = data.iloc[train_idx].reset_index(drop=True)
            test = data.iloc[test_idx].reset_index(drop=True)
            scored, metrics, model = evaluate_split(train, test)
            scored.insert(0, "fold", fold)
            scored.insert(0, "seed", seed)
            predictions.append(scored)
            rows.extend({"seed": seed, "fold": fold, **metric} for metric in metrics)
            weights = model.named_steps["model"].coef_[0]
            coefficients.append({"seed": seed, "fold": fold, **dict(zip(FEATURES, weights))})
    return pd.concat(predictions, ignore_index=True), pd.DataFrame(rows), pd.DataFrame(coefficients)


def summarize(fold_metrics: pd.DataFrame) -> pd.DataFrame:
    """Mean, sd (ddof=1), min, max over folds per scorer+metric, plus paired model-minus-rule rows.

    Paired rows (scorer = "model_minus_rule") also count the folds where the model is strictly better.
    """
    records = []
    for scorer, group in fold_metrics.groupby("scorer", sort=True):
        for metric in SUMMARY_METRICS:
            if metric not in group or group[metric].isna().all():
                continue
            values = group[metric].astype(float)
            records.append({"scorer": scorer, "metric": metric, "folds": int(values.count()),
                            "mean": values.mean(), "sd": values.std(ddof=1),
                            "min": values.min(), "max": values.max()})
    keys = ["seed", "fold"]
    model = fold_metrics[fold_metrics["scorer"] == "model"].set_index(keys)
    rule = fold_metrics[fold_metrics["scorer"] == "rule"].set_index(keys)
    for metric in PAIRED_METRICS:
        diff = (model[metric] - rule[metric]).astype(float)
        records.append({"scorer": "model_minus_rule", "metric": metric, "folds": int(diff.count()),
                        "mean": diff.mean(), "sd": diff.std(ddof=1), "min": diff.min(), "max": diff.max(),
                        "model_better_folds": int((diff > 0).sum())})
    return pd.DataFrame(records)



# --- Slices, leave-one-org-out, coefficients, error cases (step 4) ------------------------------

SLICE_COLUMNS = schema.SLICE_COLUMNS          # ("org_profile", "zone")
SLICE_METRICS = ("pr_auc", "roc_auc", "precision_top", "recall_top", "flag_rate_top")


def slice_fold_metrics(predictions: pd.DataFrame) -> pd.DataFrame:
    """Metrics per (seed, fold, slice, group, scorer).

    Flags come from the ranking of the WHOLE fold (one shared review queue), then are measured per
    group. flag_rate_top = share of the group's cases placed in the top 15%. Groups below
    MIN_GROUP_ROWS cases or MIN_GROUP_POSITIVES positives in a fold get too_small = 1 and no metrics.
    """
    rows = []
    for (seed, fold), fold_cases in predictions.groupby(["seed", "fold"], sort=True):
        for slice_column in SLICE_COLUMNS:
            for group, cases in fold_cases.groupby(slice_column, sort=True):
                positives = int(cases[LABEL].sum())
                too_small = len(cases) < MIN_GROUP_ROWS or positives < MIN_GROUP_POSITIVES
                for scorer in SCORERS:
                    row = {"seed": seed, "fold": fold, "slice": slice_column, "group": group,
                           "scorer": scorer, "n": len(cases), "positives": positives, "too_small": int(too_small)}
                    if not too_small:
                        metric = compute_metrics(cases, scorer)
                        row.update({k: metric[k] for k in ("pr_auc", "roc_auc", "precision_top", "recall_top")})
                        row["flag_rate_top"] = cases[f"{scorer}_top"].mean()
                    rows.append(row)
    return pd.DataFrame(rows)


def summarize_slices(slice_metrics: pd.DataFrame) -> pd.DataFrame:
    """Mean and sd over folds per slice+group+scorer, with mean group size and positives per fold."""
    records = []
    for (slice_column, group, scorer), rows in slice_metrics.groupby(["slice", "group", "scorer"], sort=True):
        usable = rows[rows["too_small"] == 0]
        record = {"slice": slice_column, "group": group, "scorer": scorer,
                  "folds_used": len(usable), "folds_too_small": int(rows["too_small"].sum()),
                  "n_per_fold": rows["n"].mean(), "positives_per_fold": rows["positives"].mean()}
        for metric in SLICE_METRICS:
            values = usable[metric].astype(float) if len(usable) else pd.Series(dtype=float)
            record[f"{metric}_mean"] = values.mean()
            record[f"{metric}_sd"] = values.std(ddof=1)
        records.append(record)
    return pd.DataFrame(records)


def leave_one_org_out(seeds=DEFAULT_SEEDS, n: int = DEFAULT_N) -> pd.DataFrame:
    """Train on two org profiles, test on the third; top 15% is taken within the test org."""
    rows = []
    for seed in seeds:
        data = generator.generate_cases(n, np.random.default_rng(seed))
        for org in sorted(data["org_profile"].unique()):
            train = data[data["org_profile"] != org].reset_index(drop=True)
            test = data[data["org_profile"] == org].reset_index(drop=True)
            _, metrics, _ = evaluate_split(train, test)
            rows.extend({"seed": seed, "test_org": org, **metric} for metric in metrics)
    return pd.DataFrame(rows)


def summarize_lofo(lofo: pd.DataFrame, slice_summary: pd.DataFrame) -> pd.DataFrame:
    """LOFO mean/sd per test org and scorer, next to the same org's in-distribution CV result."""
    records = []
    in_dist = slice_summary[slice_summary["slice"] == "org_profile"].set_index(["group", "scorer"])
    for (org, scorer), rows in lofo.groupby(["test_org", "scorer"], sort=True):
        record = {"test_org": org, "scorer": scorer, "seeds": len(rows)}
        for metric in ("pr_auc", "roc_auc", "precision_top", "recall_top"):
            record[f"{metric}_lofo_mean"] = rows[metric].mean()
            record[f"{metric}_lofo_sd"] = rows[metric].std(ddof=1)
            record[f"{metric}_cv_mean"] = in_dist.loc[(org, scorer), f"{metric}_mean"]
        if scorer == "model":
            record["brier_lofo_mean"] = rows["brier"].mean()
        records.append(record)
    return pd.DataFrame(records)


def summarize_coefficients(coefficients: pd.DataFrame) -> pd.DataFrame:
    """Standardized logistic weights: mean, sd, and how many folds share the mean's sign."""
    records = []
    for feature in FEATURES:
        values = coefficients[feature]
        sign = np.sign(values.mean())
        records.append({"feature": feature, "mean": values.mean(), "sd": values.std(ddof=1),
                        "same_sign_folds": int((np.sign(values) == sign).sum()), "folds": len(values)})
    return pd.DataFrame(records).sort_values("mean", key=abs, ascending=False, ignore_index=True)


def error_cases(predictions: pd.DataFrame, n: int = DEFAULT_N, per_kind: int = 2) -> pd.DataFrame:
    """A few illustrative model errors from the first evaluated (seed, fold), with their features.

    missed_high_need = labelled 1 but ranked lowest by the model; false_alarm = labelled 0 but
    ranked highest. Selection is deterministic (score, then case_id).
    """
    seed, fold = predictions[["seed", "fold"]].iloc[0]
    fold_cases = predictions[(predictions["seed"] == seed) & (predictions["fold"] == fold)]
    missed = fold_cases[(fold_cases[LABEL] == 1) & (fold_cases["model_top"] == 0)]
    missed = missed.sort_values(["model_score", "case_id"]).head(per_kind).assign(error_kind="missed_high_need")
    alarm = fold_cases[(fold_cases[LABEL] == 0) & (fold_cases["model_top"] == 1)]
    alarm = alarm.sort_values(["model_score", "case_id"], ascending=[False, True]).head(per_kind)
    alarm = alarm.assign(error_kind="false_alarm")
    picked = pd.concat([missed, alarm], ignore_index=True)
    picked["model_rank"] = [int((fold_cases["model_score"] > s).sum()) + 1 for s in picked["model_score"]]
    data = generator.generate_cases(n, np.random.default_rng(int(seed)))
    features = data[["case_id", *FEATURES]]
    keep = ["error_kind", "seed", "fold", "case_id", "org_profile", "zone", LABEL, "model_score",
            "model_rank", "rule_score", "rule_top"]
    return picked[keep].merge(features, on="case_id", how="left")

# --- Output ----------------------------------------------------------------------------------

FLOAT_FORMAT = "%.6g"


def _csv_bytes(frame: pd.DataFrame) -> bytes:
    return frame.to_csv(index=False, lineterminator="\n", float_format=FLOAT_FORMAT).encode("utf-8")


def run_config(seeds, n: int, folds: int, hashes: dict[str, str]) -> dict:
    """Everything needed to rerun and to read the outputs honestly. No timestamp (keeps reruns identical)."""
    return {
        "demo_notice": schema.DEMO_NOTICE,
        "label_note": LABEL_NOTE,
        "seeds": list(seeds), "n": n, "folds": folds,
        "versions": {"eval": EVAL_VERSION, "generator": generator.GENERATOR_VERSION,
                     "schema": schema.SCHEMA_VERSION, "rules": rules.RULE_VERSION},
        "protocol": {"top_share": TOP_SHARE, "model_threshold": MODEL_THRESHOLD,
                     "rule_threshold": RULE_THRESHOLD, "min_group_rows": MIN_GROUP_ROWS,
                     "min_group_positives": MIN_GROUP_POSITIVES, "features": list(FEATURES),
                     "queue_comparison": "same CV scores, same fold budget, proportional org quotas",
                     "model": "SimpleImputer(median) -> StandardScaler -> LogisticRegression(C=1.0, max_iter=1000)"},
        "library_versions": {"python": platform.python_version(), "numpy": np.__version__,
                             "pandas": pd.__version__, "scikit-learn": sklearn.__version__},
        "sha256": hashes,
    }


def run(seeds, n: int, folds: int, out_dir: Path) -> dict:
    """Run the evaluation and write every output file into out_dir. Returns the run config."""
    predictions, fold_metrics, coefficients = cross_validate(seeds, n, folds)
    slice_summary = summarize_slices(slice_fold_metrics(predictions))
    lofo = leave_one_org_out(seeds, n)
    files = {
        "predictions.csv": _csv_bytes(predictions.assign(notice="DEMO")),
        "fold_metrics.csv": _csv_bytes(fold_metrics),
        "summary.csv": _csv_bytes(summarize(fold_metrics)),
        "slice_summary.csv": _csv_bytes(slice_summary),
        "queue_policy_metrics.csv": _csv_bytes(compare_queues(predictions)),
        "lofo_metrics.csv": _csv_bytes(lofo),
        "lofo_summary.csv": _csv_bytes(summarize_lofo(lofo, slice_summary)),
        "coefficients.csv": _csv_bytes(summarize_coefficients(coefficients)),
        "error_cases.csv": _csv_bytes(error_cases(predictions, n).assign(notice="DEMO")),
    }
    out_dir.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for name, data in files.items():
        (out_dir / name).write_bytes(data)
        hashes[name] = hashlib.sha256(data).hexdigest()
    config = run_config(seeds, n, folds, hashes)
    text = json.dumps(config, indent=2, sort_keys=True, ensure_ascii=True) + "\n"
    (out_dir / "run_config.json").write_bytes(text.encode("utf-8"))
    return config


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate Logistic Regression vs rules-v1 on synthetic data (DEMO).")
    parser.add_argument("--seeds", type=int, nargs="+", default=list(DEFAULT_SEEDS))
    parser.add_argument("--n", type=int, default=DEFAULT_N)
    parser.add_argument("--folds", type=int, default=DEFAULT_FOLDS)
    parser.add_argument("--out", type=Path, required=True, help="output directory, e.g. data/eval")
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    started = time.perf_counter()
    config = run(args.seeds, args.n, args.folds, args.out)
    print(schema.DEMO_NOTICE)
    print(f"Evaluated seeds {args.seeds} x {args.folds} folds (n={args.n}) in "
          f"{time.perf_counter() - started:.1f}s -> {args.out}")
    print(f"SHA-256 fold_metrics.csv: {config['sha256']['fold_metrics.csv']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
