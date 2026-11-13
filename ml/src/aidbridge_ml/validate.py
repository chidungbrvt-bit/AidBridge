"""Fail-fast validation of a dataset against schema.py.

Every check raises SchemaError on the first problem with a message that names the column and the
offending rows, so a broken file stops the pipeline before it can train, score or publish anything.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from . import schema


class SchemaError(ValueError):
    """Raised when a dataset does not match the schema. The message says what to fix."""


def _rows(mask: pd.Series, limit: int = 5) -> str:
    """First few row labels where mask is True, for error messages."""
    index = list(mask[mask].index[:limit])
    return f"{index}{' ...' if int(mask.sum()) > limit else ''}"


def pii_column_names(columns) -> list[str]:
    """Columns whose name contains a PII token (exact token match after splitting on '_')."""
    hits = []
    for column in columns:
        tokens = str(column).lower().replace("-", "_").split("_")
        if any(token in schema.PII_NAME_TOKENS for token in tokens):
            hits.append(str(column))
    return hits


def validate_frame(frame: pd.DataFrame, *, require_label: bool = True) -> None:
    """Raise SchemaError unless `frame` matches the schema exactly.

    Order matters: PII names first (most severe), then unknown and missing columns, then
    per-column kind/range/null checks, then cross-column rules and PII-looking values.
    `require_label=False` allows scoring-only data (no high_need column).
    """
    if not isinstance(frame, pd.DataFrame):
        raise SchemaError(f"Expected a pandas DataFrame, got {type(frame).__name__}")

    # 1. Denylisted (PII-looking) column names.
    pii = pii_column_names(frame.columns)
    if pii:
        raise SchemaError(f"PII-like column names are not allowed: {pii}")

    # 2. Unknown and missing columns.
    expected = [name for name in schema.COLUMN_NAMES if require_label or name != schema.LABEL_COLUMN]
    unknown = [str(c) for c in frame.columns if c not in schema.COLUMNS_BY_NAME]
    if unknown:
        raise SchemaError(f"Unknown columns (not in schema v{schema.SCHEMA_VERSION}): {unknown}")
    missing = [name for name in expected if name not in frame.columns]
    if missing:
        raise SchemaError(f"Missing required columns: {missing}")
    if frame.empty:
        raise SchemaError("Dataset has no rows")

    # 3. Per-column checks.
    for name in frame.columns:
        _check_column(frame[name], schema.COLUMNS_BY_NAME[name])

    # 4. Cross-column rules.
    _check_cross_columns(frame)

    # 5. PII-looking values in text columns (defence in depth; schema text columns are constrained).
    for name in frame.columns:
        values = frame[name]
        if pd.api.types.is_string_dtype(values) or values.dtype == object:
            text = values.dropna().astype(str)
            for label, pattern in schema.PII_VALUE_PATTERNS.items():
                hit = text.str.contains(pattern, regex=True)
                if hit.any():
                    raise SchemaError(f"Column '{name}' has {label} values at rows {_rows(hit)}")


def _check_column(values: pd.Series, column: schema.Column) -> None:
    name = column.name
    nulls = values.isna()
    if nulls.any() and not column.nullable:
        raise SchemaError(f"Column '{name}' must not be missing; missing at rows {_rows(nulls)}")
    present = values[~nulls]

    if column.kind == schema.ID:
        text = present.astype(str)
        bad = ~text.str.fullmatch(schema.CASE_ID_PATTERN)
        if bad.any():
            raise SchemaError(f"Column '{name}' must match {schema.CASE_ID_PATTERN}; bad rows {_rows(bad)}")
        duplicated = text.duplicated(keep=False)
        if duplicated.any():
            raise SchemaError(f"Column '{name}' must be unique; duplicates at rows {_rows(duplicated)}")
        return

    if column.kind == schema.CATEGORY:
        bad = ~present.astype(str).isin(column.categories)
        if bad.any():
            raise SchemaError(f"Column '{name}' must be one of {list(column.categories)}; bad rows {_rows(bad)}")
        return

    # Numeric kinds: INT, BINARY, FLOAT. Booleans are rejected so True/False never pass as 1/0 silently.
    if not pd.api.types.is_numeric_dtype(values) or pd.api.types.is_bool_dtype(values):
        raise SchemaError(f"Column '{name}' must be numeric, got dtype {values.dtype}")
    numbers = present.astype(float)
    not_finite = ~np.isfinite(numbers)
    if not_finite.any():
        raise SchemaError(f"Column '{name}' has non-finite values at rows {_rows(not_finite)}")

    if column.kind in (schema.INT, schema.BINARY):
        fractional = numbers != np.floor(numbers)
        if fractional.any():
            raise SchemaError(f"Column '{name}' must hold whole numbers; bad rows {_rows(fractional)}")
    if column.kind == schema.BINARY:
        bad = ~numbers.isin([0.0, 1.0])
        if bad.any():
            raise SchemaError(f"Column '{name}' must be 0 or 1; bad rows {_rows(bad)}")
        return

    low, high = column.min_value, column.max_value
    out_of_range = (numbers < low) | (numbers > high)
    if out_of_range.any():
        raise SchemaError(f"Column '{name}' must be within [{low}, {high}]; bad rows {_rows(out_of_range)}")


def _check_cross_columns(frame: pd.DataFrame) -> None:
    # Children and elderly are members of the household.
    too_many = (frame["children_under5"] + frame["elderly_count"]) > frame["household_size"]
    if too_many.any():
        raise SchemaError(
            f"children_under5 + elderly_count must not exceed household_size; bad rows {_rows(too_many)}")

    # A missing flag is 1 exactly when its value is missing.
    for value_column, flag_column in schema.MISSING_FLAG_FOR.items():
        mismatch = frame[value_column].isna() != (frame[flag_column] == 1)
        if mismatch.any():
            raise SchemaError(
                f"{flag_column} must be 1 exactly when {value_column} is missing; bad rows {_rows(mismatch)}")


def assert_no_pii_columns(columns) -> None:
    """Small guard for code paths that build frames outside validate_frame (e.g. exports)."""
    pii = pii_column_names(columns)
    if pii:
        raise SchemaError(f"PII-like column names are not allowed: {pii}")


__all__ = ["SchemaError", "validate_frame", "pii_column_names", "assert_no_pii_columns"]

