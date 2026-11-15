"""Schema and validate tests: the gatekeeper must reject exactly what it promises to reject."""
from __future__ import annotations

import pandas as pd
import pytest

from aidbridge_ml import schema
from aidbridge_ml.validate import SchemaError, assert_no_pii_columns, pii_column_names, validate_frame
from conftest import make_frame

NAN = float("nan")


# --- Schema self-consistency -----------------------------------------------------------------

def test_feature_list_excludes_ids_slices_and_label():
    assert "case_id" not in schema.FEATURE_COLUMNS
    assert "zone" not in schema.FEATURE_COLUMNS and "org_profile" not in schema.FEATURE_COLUMNS
    assert schema.LABEL_COLUMN not in schema.FEATURE_COLUMNS


def test_every_nullable_column_has_a_missing_flag():
    nullable = {c.name for c in schema.COLUMNS if c.nullable}
    assert nullable == set(schema.MISSING_FLAG_FOR)
    for flag in schema.MISSING_FLAG_FOR.values():
        assert schema.COLUMNS_BY_NAME[flag].kind == schema.BINARY


def test_schema_names_contain_no_pii_tokens():
    assert pii_column_names(schema.COLUMN_NAMES) == []


def test_demo_notice_is_the_approved_text():
    assert schema.DEMO_NOTICE == ("DEMO - prototype on synthetic data. Advisory only; a human reviewer decides. "
                                  "An official version needs real consented data and model training.")


# --- Accepts valid data ----------------------------------------------------------------------

def test_valid_frame_passes():
    validate_frame(make_frame({}, {"income_band": NAN, "income_band_missing": 1}))


def test_scoring_frame_without_label_passes_only_when_allowed():
    frame = make_frame().drop(columns=schema.LABEL_COLUMN)
    validate_frame(frame, require_label=False)
    with pytest.raises(SchemaError, match="Missing required columns"):
        validate_frame(frame)


# --- Rejects unknown and PII columns ---------------------------------------------------------

def test_rejects_unknown_column():
    with pytest.raises(SchemaError, match="Unknown columns.*latent"):
        validate_frame(make_frame().assign(latent=0.5))


@pytest.mark.parametrize("column", ["full_name", "phone", "email", "home_address", "dob", "lat", "lon",
                                    "national_id", "beneficiary_name", "gps_lng"])
def test_rejects_pii_column_names(column):
    with pytest.raises(SchemaError, match="PII-like column names"):
        validate_frame(make_frame().assign(**{column: "x"}))
    with pytest.raises(SchemaError):
        assert_no_pii_columns([column])


@pytest.mark.parametrize("column", ["latent", "population", "waiting_days", "elderly_count"])
def test_pii_check_matches_whole_tokens_only(column):
    assert pii_column_names([column]) == []


# --- Rejects bad values ----------------------------------------------------------------------

@pytest.mark.parametrize("override, message", [
    ({"household_size": 13}, "household_size"),
    ({"household_size": 0}, "household_size"),
    ({"income_band": 5.0}, "income_band"),
    ({"evidence_completeness": 1.2}, "evidence_completeness"),
    ({"waiting_days": -1}, "waiting_days"),
    ({"need_item_count": 21}, "need_item_count"),
    ({"has_disability_member": 2}, "has_disability_member"),
    ({"household_size": 2.5}, "whole numbers"),
    ({"zone": "Z9"}, "zone"),
    ({"org_profile": "D"}, "org_profile"),
    ({"case_id": "CASE-1"}, "case_id"),
    ({"household_size": 2, "children_under5": 2, "elderly_count": 1}, "must not exceed household_size"),
])
def test_rejects_out_of_domain_values(override, message):
    with pytest.raises(SchemaError, match=message):
        validate_frame(make_frame(override))


def test_rejects_wrong_type():
    frame = make_frame()
    frame["waiting_days"] = frame["waiting_days"].astype(str)
    with pytest.raises(SchemaError, match="must be numeric"):
        validate_frame(frame)


def test_rejects_boolean_posing_as_binary():
    frame = make_frame()
    frame["recent_disaster_flag"] = frame["recent_disaster_flag"].astype(bool)
    with pytest.raises(SchemaError, match="must be numeric"):
        validate_frame(frame)


def test_rejects_missing_value_in_non_nullable_column():
    with pytest.raises(SchemaError, match="must not be missing"):
        validate_frame(make_frame({"household_size": NAN}))


@pytest.mark.parametrize("override", [
    {"income_band": NAN, "income_band_missing": 0},
    {"income_band": 2.0, "income_band_missing": 1},
    {"evidence_completeness": NAN, "evidence_completeness_missing": 0},
])
def test_rejects_missing_flag_mismatch(override):
    with pytest.raises(SchemaError, match="must be 1 exactly when"):
        validate_frame(make_frame(override))


def test_rejects_duplicate_ids():
    with pytest.raises(SchemaError, match="unique"):
        validate_frame(make_frame({"case_id": "SYN-000001"}, {"case_id": "SYN-000001"}))


def test_rejects_empty_frame():
    with pytest.raises(SchemaError, match="no rows"):
        validate_frame(make_frame().iloc[0:0])


def test_rejects_non_dataframe():
    with pytest.raises(SchemaError, match="DataFrame"):
        validate_frame([{"case_id": "SYN-000001"}])
