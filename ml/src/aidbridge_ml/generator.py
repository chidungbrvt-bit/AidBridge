"""Synthetic case generator for the AidBridge ML demo (D51).

Every value is drawn from a seeded numpy Generator passed in by the caller; nothing reads or writes
numpy's global random state. Same seed + same pinned library versions -> identical frame.

Pipeline (one call to generate_cases):
  1. split n rows across org profiles A/B/C with fixed shares (exact counts, not random);
  2. per org, draw observed features from org-specific distributions;
  3. compute a hidden latent need from the TRUE values (before any masking);
  4. label high_need = 1 for the top TOP_SHARE of latent within each org, then flip LABEL_NOISE;
  5. hide some income_band / evidence_completeness values and set their *_missing flags;
  6. shuffle rows, then number case_id SYN-000001.. so the id carries no org/label order;
  7. validate against schema.py (fail fast).

The latent formula deliberately differs from rules.py (continuous waiting time, a children x
low-income interaction, a disaster x large-household interaction, continuous noise) so the rule
baseline is an honest but imperfect proxy.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

from . import schema
from .validate import validate_frame

GENERATOR_VERSION = "generator-v2"     # v2 (14/11): org B income missing 4.5% -> 4.0%
MIN_ROWS = 50                     # below this the per-org quantile labels become meaningless
MAX_ROWS = 999_999                # case_id has six digits

LABEL_NOISE = 0.05                # share of labels flipped at random (both directions)
LATENT_NOISE_SD = 0.8             # sd of continuous noise added to latent need
EVIDENCE_MISSING_RATE = 0.03      # missing completely at random, all orgs
ZONES = ("Z1", "Z2", "Z3", "Z4", "Z5")


@dataclass(frozen=True)
class OrgProfile:
    """Distribution parameters for one synthetic organization profile."""
    code: str
    share: float                          # share of all rows
    household_extra_mean: float           # household_size = 1 + Poisson(mean)
    child_prob: float                     # per non-head member, chance of being under five
    elderly_prob: float                   # per remaining member, chance of being elderly
    disability_prob: float                # base chance of a member with a disability
    income_weights: tuple[float, ...]     # bands 0..4
    disaster_prob: float
    waiting_scale: float                  # waiting_days ~ Exponential(scale), capped at 60
    evidence_beta: tuple[float, float]    # evidence_completeness ~ Beta(a, b)
    zone_weights: tuple[float, ...]       # Z1..Z5
    top_share: float                      # share labelled high_need before label noise
    income_missing_rate: float            # missing depends on org (MAR by org)


ORG_PROFILES: tuple[OrgProfile, ...] = (
    OrgProfile("A", 0.40, 2.0, 0.12, 0.08, 0.08, (0.10, 0.20, 0.30, 0.25, 0.15), 0.05, 8.0,
               (8.0, 2.0), (0.45, 0.30, 0.15, 0.05, 0.05), 0.08, 0.03),
    OrgProfile("B", 0.35, 3.8, 0.15, 0.18, 0.14, (0.25, 0.30, 0.25, 0.15, 0.05), 0.10, 14.0,
               (5.0, 2.0), (0.05, 0.15, 0.30, 0.30, 0.20), 0.11, 0.04),
    OrgProfile("C", 0.25, 3.0, 0.14, 0.12, 0.12, (0.20, 0.30, 0.25, 0.15, 0.10), 0.60, 5.0,
               (3.0, 2.0), (0.10, 0.10, 0.20, 0.25, 0.35), 0.14, 0.03),
)


def org_counts(n: int) -> dict[str, int]:
    """Exact rows per org: floor(n * share), remainder goes to the first org. Deterministic."""
    counts = {p.code: int(np.floor(n * p.share)) for p in ORG_PROFILES}
    counts[ORG_PROFILES[0].code] += n - sum(counts.values())
    return counts


def _draw_org(rng: np.random.Generator, profile: OrgProfile, size: int) -> pd.DataFrame:
    """Observed features (true values, nothing masked yet) for one org."""
    household = np.clip(1 + rng.poisson(profile.household_extra_mean, size), 1, 12)
    children = np.minimum(rng.binomial(household - 1, profile.child_prob), 4)
    elderly = np.minimum(rng.binomial(household - children, profile.elderly_prob), 4)
    disability_prob = np.clip(profile.disability_prob + 0.05 * (elderly > 0), 0.0, 1.0)
    disability = (rng.random(size) < disability_prob).astype("int64")
    income = rng.choice(5, size=size, p=profile.income_weights)
    disaster = (rng.random(size) < profile.disaster_prob).astype("int64")
    need_items = np.clip(1 + rng.poisson(1.0 + 0.5 * household + 1.5 * disaster), 1, 20)
    per_item = 1 + rng.poisson(2.0 + 0.6 * household)
    requested = np.clip(need_items * per_item, need_items, 500)
    evidence = np.round(rng.beta(*profile.evidence_beta, size), 2)
    waiting = np.minimum(np.floor(rng.exponential(profile.waiting_scale, size)), 60)
    zone = rng.choice(len(ZONES), size=size, p=profile.zone_weights)
    return pd.DataFrame({
        "household_size": household.astype("int64"),
        "children_under5": children.astype("int64"),
        "elderly_count": elderly.astype("int64"),
        "has_disability_member": disability,
        "income_band": income.astype("float64"),
        "recent_disaster_flag": disaster,
        "need_item_count": need_items.astype("int64"),
        "requested_qty_total": requested.astype("int64"),
        "evidence_completeness": evidence.astype("float64"),
        "waiting_days": waiting.astype("int64"),
        "org_profile": profile.code,
        "zone": np.array(ZONES)[zone],
    })


def latent_need(frame: pd.DataFrame, rng: np.random.Generator) -> np.ndarray:
    """Hidden need score from TRUE feature values plus continuous noise. Never written to disk.

    evidence_completeness, zone and org_profile are intentionally absent: evidence is data
    quality, not need; zone and org must not drive need directly (org shifts the label rate
    through OrgProfile.top_share instead).
    """
    income = frame["income_band"].to_numpy()
    low_income = (4.0 - income) / 4.0                                   # 1 = poorest band
    children = frame["children_under5"].to_numpy()
    household = frame["household_size"].to_numpy()
    disaster = frame["recent_disaster_flag"].to_numpy()
    return (
        1.2 * low_income
        + 0.5 * children
        + 0.35 * frame["elderly_count"].to_numpy()
        + 0.9 * frame["has_disability_member"].to_numpy()
        + 1.0 * disaster
        + 0.12 * household
        + 0.25 * np.log1p(frame["waiting_days"].to_numpy())
        + 0.8 * ((children > 0) & (income <= 1))                       # young children + low income
        + 0.4 * (disaster * (household >= 5))                          # disaster hits large homes
        + rng.normal(0.0, LATENT_NOISE_SD, len(frame))
    )


def generate_cases(n: int, rng: np.random.Generator) -> pd.DataFrame:
    """Return a schema-valid synthetic dataset of exactly n rows (columns in schema order)."""
    if not isinstance(rng, np.random.Generator):
        raise TypeError("rng must be a numpy.random.Generator (use numpy.random.default_rng(seed))")
    if not MIN_ROWS <= n <= MAX_ROWS:
        raise ValueError(f"n must be between {MIN_ROWS} and {MAX_ROWS}, got {n}")

    counts = org_counts(n)
    parts = []
    for profile in ORG_PROFILES:                      # fixed order A, B, C keeps draws reproducible
        part = _draw_org(rng, profile, counts[profile.code])
        latent = latent_need(part, rng)
        cutoff = np.quantile(latent, 1.0 - profile.top_share)
        part["high_need"] = (latent >= cutoff).astype("int64")
        income_hidden = rng.random(len(part)) < profile.income_missing_rate
        part.loc[income_hidden, "income_band"] = np.nan
        parts.append(part)
    frame = pd.concat(parts, ignore_index=True)

    flip = rng.random(n) < LABEL_NOISE
    frame["high_need"] = (frame["high_need"] ^ flip).astype("int64")

    evidence_hidden = rng.random(n) < EVIDENCE_MISSING_RATE
    frame.loc[evidence_hidden, "evidence_completeness"] = np.nan
    for value_column, flag_column in schema.MISSING_FLAG_FOR.items():
        frame[flag_column] = frame[value_column].isna().astype("int64")

    frame = frame.iloc[rng.permutation(n)].reset_index(drop=True)
    frame["case_id"] = [f"SYN-{i:06d}" for i in range(1, n + 1)]
    frame = frame[list(schema.COLUMN_NAMES)]

    validate_frame(frame)
    return frame
