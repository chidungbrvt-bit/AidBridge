"""Compare two review queues on the same out-of-fold synthetic predictions."""

from __future__ import annotations

import math

import numpy as np
import pandas as pd

from .rules import TOP_SHARE
from .schema import LABEL_COLUMN

SCORERS = ("model", "rule")


def top_count_flags(score, waiting_days, case_id, count: int) -> np.ndarray:
    score = np.asarray(score, dtype=float)
    order = np.lexsort((np.asarray(case_id, dtype=str), -np.asarray(waiting_days, dtype=float), -score))
    flags = np.zeros(len(score), dtype="int64")
    flags[order[:count]] = 1
    return flags


def top_share_flags(score, waiting_days, case_id, share: float = TOP_SHARE) -> np.ndarray:
    """Rank by score, waiting time and case ID. Return ceil(share * n) flags."""
    return top_count_flags(score, waiting_days, case_id, math.ceil(share * len(score)))


def _quotas(sizes: dict[str, int], budget: int, total: int) -> dict[str, int]:
    """Proportional quotas with largest remainders and deterministic org ties."""
    quotas = {org: size * budget // total for org, size in sizes.items()}
    order = sorted(sizes, key=lambda org: (-(sizes[org] * budget % total), org))
    for org in order[:budget - sum(quotas.values())]:
        quotas[org] += 1
    return quotas


def _rates(labels: pd.Series, flags: np.ndarray, budget: int) -> dict:
    flagged = int(flags.sum())
    hits = int(labels.to_numpy()[flags == 1].sum())
    positives = int(labels.sum())
    return {
        "flagged": flagged,
        "selection_rate": flagged / len(labels),
        "share_of_slots": flagged / budget,
        "precision": hits / flagged if flagged else float("nan"),
        "recall": hits / positives if positives else float("nan"),
    }


def compare_queues(predictions: pd.DataFrame) -> pd.DataFrame:
    """Per fold/org/scorer, change only the queue policy and keep the slot count fixed."""
    rows = []
    for (seed, fold), batch in predictions.groupby(["seed", "fold"], sort=True):
        by_org = dict(tuple(batch.groupby("org_profile", sort=True)))
        budget = math.ceil(TOP_SHARE * len(batch))
        quotas = _quotas({org: len(cases) for org, cases in by_org.items()}, budget, len(batch))

        for org, cases in by_org.items():
            labels = cases[LABEL_COLUMN]
            for scorer in SCORERS:
                within = top_count_flags(
                    cases[f"{scorer}_score"], cases["waiting_days"], cases["case_id"], quotas[org]
                )
                global_rates = _rates(labels, cases[f"{scorer}_top"].to_numpy(), budget)
                within_rates = _rates(labels, within, budget)
                rows.append({
                    "seed": seed, "fold": fold, "org_profile": org, "scorer": scorer,
                    "n": len(cases), "positives": int(labels.sum()), "fold_budget": budget,
                    **{f"global_{key}": value for key, value in global_rates.items()},
                    **{f"within_org_{key}": value for key, value in within_rates.items()},
                })
    return pd.DataFrame(rows)
