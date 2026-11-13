"""Hand-written rule baseline for the advisory priority score (D51).

Transparent integer points on observed features only. It deliberately differs from the generator
(integer points, its own cut-offs, no children x low-income interaction, no use of
evidence_completeness), so comparing ML-2's model against it is meaningful.
"""
from __future__ import annotations

import math

import pandas as pd

from .validate import validate_frame

RULE_VERSION = "rules-v1"

# Points table (also documented in ml/docs/ML_SPEC.md). Max total = 12.
POINTS_CHILDREN_UNDER5 = 2       # children_under5 >= 1
POINTS_ELDERLY = 1               # elderly_count >= 1
POINTS_DISABILITY = 2            # has_disability_member == 1
POINTS_INCOME_BAND = {0: 3, 1: 2, 2: 1, 3: 0, 4: 0}
POINTS_INCOME_MISSING = 1        # unknown income: neutral middle value, neither rewarded nor punished
POINTS_DISASTER = 2              # recent_disaster_flag == 1
LARGE_HOUSEHOLD_MIN = 6
POINTS_LARGE_HOUSEHOLD = 1       # household_size >= 6
LONG_WAIT_MIN_DAYS = 14
POINTS_LONG_WAIT = 1             # waiting_days >= 14
MAX_SCORE = (POINTS_CHILDREN_UNDER5 + POINTS_ELDERLY + POINTS_DISABILITY + max(POINTS_INCOME_BAND.values())
             + POINTS_DISASTER + POINTS_LARGE_HOUSEHOLD + POINTS_LONG_WAIT)

TOP_SHARE = 0.15                 # flag the top 15% of the ranking
FIXED_THRESHOLD = 6              # reference cut-off, reported alongside the top-share flag


def score_frame(frame: pd.DataFrame) -> pd.Series:
    """Integer rule score per case (0..MAX_SCORE), indexed like `frame`.

    Missing values: income_band missing gets POINTS_INCOME_MISSING; evidence_completeness is not
    used at all, so its absence changes nothing. Every other input is non-nullable by schema.
    """
    validate_frame(frame, require_label=False)
    income_points = frame["income_band"].map(
        lambda band: POINTS_INCOME_MISSING if pd.isna(band) else POINTS_INCOME_BAND[int(band)])
    score = (
        (frame["children_under5"] >= 1) * POINTS_CHILDREN_UNDER5
        + (frame["elderly_count"] >= 1) * POINTS_ELDERLY
        + (frame["has_disability_member"] == 1) * POINTS_DISABILITY
        + income_points
        + (frame["recent_disaster_flag"] == 1) * POINTS_DISASTER
        + (frame["household_size"] >= LARGE_HOUSEHOLD_MIN) * POINTS_LARGE_HOUSEHOLD
        + (frame["waiting_days"] >= LONG_WAIT_MIN_DAYS) * POINTS_LONG_WAIT
    )
    return score.astype("int64").rename("rule_score")


def rank_frame(frame: pd.DataFrame) -> pd.DataFrame:
    """Score, rank and flag cases without modifying `frame`.

    Returns case_id, rule_score, rule_rank (1 = review first), rule_flag_top (top TOP_SHARE of
    the ranking) and rule_flag_threshold (score >= FIXED_THRESHOLD), sorted by rule_rank.
    Ties: higher score first, then longer waiting_days, then case_id ascending (deterministic).
    """
    result = pd.DataFrame({
        "case_id": frame["case_id"].astype(str),
        "rule_score": score_frame(frame),
        "waiting_days": frame["waiting_days"],
    })
    result = result.sort_values(
        ["rule_score", "waiting_days", "case_id"], ascending=[False, False, True], kind="mergesort")
    result["rule_rank"] = range(1, len(result) + 1)
    top_count = math.ceil(TOP_SHARE * len(result))
    result["rule_flag_top"] = (result["rule_rank"] <= top_count).astype("int64")
    result["rule_flag_threshold"] = (result["rule_score"] >= FIXED_THRESHOLD).astype("int64")
    return result.drop(columns="waiting_days")
