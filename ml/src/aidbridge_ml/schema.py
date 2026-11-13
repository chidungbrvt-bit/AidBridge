"""Column contract for the AidBridge ML demo dataset (D51).

One row = one synthetic submitted case. Every pipeline step (generator, rules, ML-2 training,
ML-4 display) reads columns through this module so that a change happens in one place only.
The dataset is synthetic: no column may describe a real person.
"""
from __future__ import annotations

from dataclasses import dataclass

SCHEMA_VERSION = "1"

# Mandatory notice on every place that shows a score (D51, criterion 7).
DEMO_NOTICE = (
    "DEMO - prototype on synthetic data. Advisory only; a human reviewer decides. "
    "An official version needs real consented data and model training."
)

# Column groups.
META = "meta"        # identifiers, never used for learning
FEATURE = "feature"  # model / rule inputs
SLICE = "slice"      # fairness and robustness slices only, NOT model inputs by default
LABEL = "label"      # target

# Value kinds.
ID = "id"
INT = "int"
FLOAT = "float"
BINARY = "binary"
CATEGORY = "category"


@dataclass(frozen=True)
class Column:
    name: str
    group: str
    kind: str
    min_value: float | None = None
    max_value: float | None = None
    categories: tuple[str, ...] | None = None
    nullable: bool = False
    description: str = ""


COLUMNS: tuple[Column, ...] = (
    Column("case_id", META, ID, description="Synthetic case id, SYN-000001 style"),
    Column("household_size", FEATURE, INT, 1, 12, description="People in the household"),
    Column("children_under5", FEATURE, INT, 0, 4, description="Children under five"),
    Column("elderly_count", FEATURE, INT, 0, 4, description="Elderly members"),
    Column("has_disability_member", FEATURE, BINARY, description="At least one member with a disability"),
    Column("income_band", FEATURE, INT, 0, 4, nullable=True, description="Coarse income band, 0 = lowest"),
    Column("income_band_missing", FEATURE, BINARY, description="1 when income_band is missing"),
    Column("recent_disaster_flag", FEATURE, BINARY, description="Recently affected by a disaster"),
    Column("need_item_count", FEATURE, INT, 1, 20, description="Need lines in the case (API allows 1..20)"),
    Column("requested_qty_total", FEATURE, INT, 1, 500, description="Sum of requested quantities"),
    Column("evidence_completeness", FEATURE, FLOAT, 0.0, 1.0, nullable=True,
           description="Share of expected evidence present; data quality, not need"),
    Column("evidence_completeness_missing", FEATURE, BINARY, description="1 when evidence_completeness is missing"),
    Column("waiting_days", FEATURE, INT, 0, 60, description="Days waiting as of scoring time"),
    Column("org_profile", SLICE, CATEGORY, categories=("A", "B", "C"), description="Synthetic organization profile"),
    Column("zone", SLICE, CATEGORY, categories=("Z1", "Z2", "Z3", "Z4", "Z5"),
           description="Coarse zone; slice only, kept out of features to avoid a location proxy"),
    Column("high_need", LABEL, BINARY, description="1 = high need (maps to URGENT after human review)"),
)

COLUMNS_BY_NAME: dict[str, Column] = {column.name: column for column in COLUMNS}
COLUMN_NAMES: tuple[str, ...] = tuple(column.name for column in COLUMNS)
META_COLUMNS: tuple[str, ...] = tuple(c.name for c in COLUMNS if c.group == META)
FEATURE_COLUMNS: tuple[str, ...] = tuple(c.name for c in COLUMNS if c.group == FEATURE)
SLICE_COLUMNS: tuple[str, ...] = tuple(c.name for c in COLUMNS if c.group == SLICE)
LABEL_COLUMN = "high_need"

# Each nullable feature has a companion flag: flag == 1 exactly when the value is missing.
MISSING_FLAG_FOR: dict[str, str] = {
    "income_band": "income_band_missing",
    "evidence_completeness": "evidence_completeness_missing",
}

CASE_ID_PATTERN = r"^SYN-\d{6}$"

# Column-name tokens that suggest personal data. A column name is split on "_" and each token is
# compared exactly, so "latent" or "population" do not match "lat"/"lon" by accident.
PII_NAME_TOKENS: frozenset[str] = frozenset({
    "name", "fullname", "firstname", "lastname", "surname",
    "phone", "mobile", "tel", "email", "mail",
    "address", "street", "ward", "postcode", "zipcode",
    "dob", "birth", "birthday", "birthdate", "age",
    "lat", "latitude", "lon", "lng", "longitude", "gps", "coordinate", "coordinates",
    "national", "passport", "ssn", "cccd", "cmnd", "idcard",
})

# Value patterns that suggest personal data in any text cell.
PII_VALUE_PATTERNS: dict[str, str] = {
    "email": r"[^@\s]+@[^@\s]+\.[A-Za-z]{2,}",
    "phone_like": r"\+?\d[\d\s.-]{8,}\d",
}
