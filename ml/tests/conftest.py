"""Shared helpers for ML-1 tests: build schema-valid rows with small overrides."""
from __future__ import annotations

import pandas as pd

BASE_ROW = {
    "case_id": "SYN-000001", "household_size": 3, "children_under5": 0, "elderly_count": 0,
    "has_disability_member": 0, "income_band": 3.0, "income_band_missing": 0,
    "recent_disaster_flag": 0, "need_item_count": 2, "requested_qty_total": 5,
    "evidence_completeness": 1.0, "evidence_completeness_missing": 0, "waiting_days": 0,
    "org_profile": "A", "zone": "Z1", "high_need": 0,
}


def make_frame(*overrides: dict) -> pd.DataFrame:
    """One row per override dict; case_id is numbered automatically unless given."""
    rows = []
    for position, override in enumerate(overrides or ({},), start=1):
        row = {**BASE_ROW, "case_id": f"SYN-{position:06d}", **override}
        rows.append(row)
    return pd.DataFrame(rows)
