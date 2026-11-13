"""Rule baseline tests: edge values, missing data, monotonicity, deterministic ranking."""
from __future__ import annotations

import itertools
import math

import pytest

from aidbridge_ml import rules
from aidbridge_ml.validate import SchemaError
from conftest import make_frame

NAN = float("nan")


def score_of(**override) -> int:
    return int(rules.score_frame(make_frame(override)).iloc[0])


def test_minimum_case_scores_zero():
    assert score_of() == 0


def test_maximum_case_scores_max_score():
    assert rules.MAX_SCORE == 12
    assert score_of(household_size=6, children_under5=1, elderly_count=1, has_disability_member=1,
                    income_band=0.0, recent_disaster_flag=1, waiting_days=14) == 12


@pytest.mark.parametrize("band, points", [(0.0, 3), (1.0, 2), (2.0, 1), (3.0, 0), (4.0, 0)])
def test_income_band_points(band, points):
    assert score_of(income_band=band) == points


@pytest.mark.parametrize("field, below, at_threshold, points", [
    ("household_size", 5, 6, 1),
    ("waiting_days", 13, 14, 1),
    ("children_under5", 0, 1, 2),
    ("elderly_count", 0, 1, 1),
])
def test_threshold_edges(field, below, at_threshold, points):
    assert score_of(**{field: at_threshold}) - score_of(**{field: below}) == points


def test_missing_income_gets_neutral_point():
    assert score_of(income_band=NAN, income_band_missing=1) == rules.POINTS_INCOME_MISSING == 1


def test_missing_evidence_changes_nothing():
    complete = score_of(evidence_completeness=1.0)
    missing = score_of(evidence_completeness=NAN, evidence_completeness_missing=1)
    low = score_of(evidence_completeness=0.0)
    assert complete == missing == low


def test_adding_a_heavier_factor_never_lowers_the_score():
    """Monotonicity over a grid: worsening any single factor must not decrease the score."""
    worsen = {
        "children_under5": lambda r: {**r, "children_under5": r["children_under5"] + 1},
        "elderly_count": lambda r: {**r, "elderly_count": r["elderly_count"] + 1},
        "has_disability_member": lambda r: {**r, "has_disability_member": 1},
        "recent_disaster_flag": lambda r: {**r, "recent_disaster_flag": 1},
        "household_size": lambda r: {**r, "household_size": r["household_size"] + 1},
        "waiting_days": lambda r: {**r, "waiting_days": r["waiting_days"] + 7},
        "income_band": lambda r: {**r, "income_band": max(r["income_band"] - 1, 0.0)},
    }
    grid = itertools.product([0, 1], [0, 1], [0, 1], [0, 1], [3, 6], [0, 13], [0.0, 2.0, 4.0])
    for children, elderly, disability, disaster, size, wait, band in grid:
        base = {"household_size": size, "children_under5": children, "elderly_count": elderly,
                "has_disability_member": disability, "recent_disaster_flag": disaster,
                "waiting_days": wait, "income_band": band}
        for name, change in worsen.items():
            worse = change(base)
            if worse["children_under5"] + worse["elderly_count"] > worse["household_size"]:
                continue
            assert score_of(**worse) >= score_of(**base), (name, base)


def test_ranking_is_deterministic_with_tie_breaks():
    frame = make_frame(
        {"case_id": "SYN-000003", "income_band": 0.0, "waiting_days": 5},   # score 3, wait 5
        {"case_id": "SYN-000001", "income_band": 0.0, "waiting_days": 5},   # score 3, wait 5 -> id wins
        {"case_id": "SYN-000002", "income_band": 0.0, "waiting_days": 9},   # score 3, wait 9 -> first
        {"case_id": "SYN-000004", "income_band": 4.0, "waiting_days": 30},  # score 1 (long wait)
    )
    ranked = rules.rank_frame(frame)
    assert list(ranked["case_id"]) == ["SYN-000002", "SYN-000001", "SYN-000003", "SYN-000004"]
    assert list(ranked["rule_rank"]) == [1, 2, 3, 4]
    assert ranked.equals(rules.rank_frame(frame.sample(frac=1.0, random_state=7)))


def test_top_share_and_threshold_flags():
    frame = make_frame(*[{"waiting_days": day} for day in range(20)])
    ranked = rules.rank_frame(frame)
    assert int(ranked["rule_flag_top"].sum()) == math.ceil(0.15 * 20) == 3
    assert int(ranked["rule_flag_threshold"].sum()) == 0


def test_input_frame_is_not_modified():
    frame = make_frame({"income_band": NAN, "income_band_missing": 1})
    before = frame.copy()
    rules.rank_frame(frame)
    assert frame.equals(before)


def test_rules_refuse_invalid_data():
    with pytest.raises(SchemaError, match="Unknown columns"):
        rules.score_frame(make_frame().assign(latent=1.0))
    with pytest.raises(SchemaError, match="income_band"):
        rules.score_frame(make_frame({"income_band": 9.0}))
