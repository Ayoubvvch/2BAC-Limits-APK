"""Seeded selector and adaptive exercise routing for 2BAC PC Limits."""

from __future__ import annotations

import random
import re
import secrets
from typing import Callable

from .generators.add_sub_zero import generate_add_sub_zero
from .generators.composer import generate_hybrid
from .generators.nth_root import generate_nth_root
from .generators.poly_factor import generate_poly_factor
from .generators.sqrt_conjugate import generate_sqrt_conjugate
from .generators.trig_limits import generate_trig
from .models import Difficulty, GeneratedExercise, Technique

_TECH_TO_CODE: dict[Technique, str] = {
    Technique.POLY_FACTOR: "POLY",
    Technique.SQRT_CONJUGATE: "SQRT",
    Technique.NTH_ROOT: "NTHR",
    Technique.ADD_SUB_ZERO: "ADD0",
    Technique.TRIG: "TRIG",
    Technique.HYBRID: "HYBR",
}

_CODE_TO_TECH: dict[str, Technique] = {v: k for k, v in _TECH_TO_CODE.items()}

_GENERATORS: dict[Technique, Callable[[str, Difficulty], GeneratedExercise]] = {
    Technique.POLY_FACTOR: generate_poly_factor,
    Technique.SQRT_CONJUGATE: generate_sqrt_conjugate,
    Technique.NTH_ROOT: generate_nth_root,
    Technique.ADD_SUB_ZERO: generate_add_sub_zero,
    Technique.TRIG: generate_trig,
    Technique.HYBRID: generate_hybrid,
}

_SEED_RE = re.compile(r"^2BAC-([A-Z0-9]{4})-L([123])-([A-Z0-9_-]+)$")


def encode_seed(technique: Technique, difficulty: Difficulty, hex_token: str | None = None) -> str:
    """Create a deterministic, shareable seed encoding technique and difficulty."""
    token = (hex_token or secrets.token_hex(5)).upper()
    return f"2BAC-{_TECH_TO_CODE[technique]}-L{int(difficulty)}-{token}"


def parse_seed(seed: str) -> tuple[Technique, Difficulty] | None:
    """Decode technique and difficulty from a formatted seed, if matching."""
    m = _SEED_RE.match(seed.strip().upper())
    if not m:
        return None
    code, diff_str, _ = m.groups()
    tech = _CODE_TO_TECH.get(code)
    if tech is None:
        return None
    return tech, Difficulty(int(diff_str))


def _pick_adaptive_technique(
    mastery: dict[str, float] | None,
    rng: random.Random,
) -> Technique:
    """Choose a technique giving higher weight to areas where student mastery is lower."""
    all_techs = list(Technique)
    if not mastery:
        return rng.choice(all_techs)

    weights: list[float] = []
    for tech in all_techs:
        score = float(mastery.get(tech.value, 0.5))
        score = max(0.0, min(1.0, score))
        # Lower mastery -> higher probability (range ~0.2 to ~1.2)
        weights.append(1.2 - score)
    return rng.choices(all_techs, weights=weights, k=1)[0]


def generate_exercise(
    technique: Technique | str | None = None,
    difficulty: Difficulty | int | None = None,
    seed: str | None = None,
    mastery: dict[str, float] | None = None,
    exclude_seeds: set[str] | None = None,
) -> GeneratedExercise:
    """Generate (or deterministically replay via seed) a verified 2BAC PC limit exercise."""
    if seed is not None:
        parsed = parse_seed(seed)
        if parsed is not None:
            tech, diff = parsed
        else:
            tech = Technique(technique) if technique is not None else Technique.HYBRID
            diff = Difficulty(int(difficulty)) if difficulty is not None else Difficulty.INTERMEDIATE
        return _GENERATORS[tech](seed, diff)

    rng = random.Random()
    if technique is not None:
        tech = Technique(technique)
    else:
        tech = _pick_adaptive_technique(mastery, rng)

    diff = Difficulty(int(difficulty)) if difficulty is not None else rng.choice(list(Difficulty))

    excluded = exclude_seeds or set()
    for _ in range(20):
        candidate_seed = encode_seed(tech, diff)
        if candidate_seed not in excluded:
            return _GENERATORS[tech](candidate_seed, diff)

    return _GENERATORS[tech](encode_seed(tech, diff), diff)
