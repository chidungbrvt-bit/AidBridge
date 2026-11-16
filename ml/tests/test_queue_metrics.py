"""Tests for the queue comparison without fitting a model."""

import pandas as pd
from pytest import approx

from aidbridge_ml.queue_metrics import compare_queues, top_share_flags


def test_compare_queues_keeps_the_same_budget_and_scores():
    rows = pd.DataFrame({
        "seed": 1,
        "fold": 1,
        "case_id": [f"SYN-{i:06d}" for i in range(13)],
        "org_profile": ["A"] * 6 + ["B"] * 7,
        "waiting_days": 0,
        "high_need": [1, 1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0],
        "model_score": range(13, 0, -1),
        "rule_score": range(1, 14),
    })
    for scorer in ("model", "rule"):
        rows[f"{scorer}_top"] = top_share_flags(rows[f"{scorer}_score"], rows.waiting_days, rows.case_id)
    original = rows.copy()

    result = compare_queues(rows)
    pd.testing.assert_frame_equal(rows, original)
    assert result.groupby("scorer")["global_flagged"].sum().tolist() == [2, 2]
    assert result.groupby("scorer")["within_org_flagged"].sum().tolist() == [2, 2]
    a = result.set_index(["scorer", "org_profile"]).loc[("model", "A")]
    assert a["global_selection_rate"] == approx(2 / 6)
    assert a["global_share_of_slots"] == 1
    assert a["within_org_selection_rate"] == approx(1 / 6)
    assert a["within_org_share_of_slots"] == 0.5
    assert a["global_recall"] == 1
    assert a["within_org_recall"] == 0.5


def test_rounding_never_adds_extra_slots_to_small_orgs():
    rows = pd.DataFrame({
        "seed": 1, "fold": 1, "case_id": ["A1", "B1", "C1"], "org_profile": ["A", "B", "C"],
        "waiting_days": 0, "high_need": [1, 0, 0], "model_score": [0.1, 0.2, 0.3],
        "rule_score": [0, 0, 1], "model_top": [0, 0, 1], "rule_top": [0, 0, 1],
    })
    result = compare_queues(rows)
    for _, scorer in result.groupby("scorer"):
        assert scorer["within_org_flagged"].tolist() == [1, 0, 0]
