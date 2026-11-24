"""Seeded selector, anti-repetition steering, and multi-chapter progressive adaptive routing."""

from __future__ import annotations

import random
import re
import secrets
from typing import Any, Callable

from .adaptive import (
    AdaptivePlan,
    INITIAL_MASTERY,
    choose_calibrated_difficulty,
    select_adaptive_plan,
)
from .generators.add_sub_zero import generate_add_sub_zero
from .generators.composer import generate_hybrid
from .generators.exp_limits import generate_exp_limits
from .generators.ln_limits import generate_ln_limits
from .generators.nth_root import generate_nth_root
from .generators.poly_factor import generate_poly_factor
from .generators.sequences import (
    generate_seq_arith_geo,
    generate_seq_fixed_point,
    generate_seq_homographic,
    generate_seq_limits,
)
from .generators.sqrt_conjugate import generate_sqrt_conjugate
from .generators.trig_limits import generate_trig
from .models import Chapter, Difficulty, GeneratedExercise, Technique

_TECH_TO_CODE: dict[Technique, str] = {
    Technique.POLY_FACTOR: "POLY",
    Technique.SQRT_CONJUGATE: "SQRT",
    Technique.TRIG: "TRIG",
    Technique.NTH_ROOT: "NTHR",
    Technique.LN_LIMITS: "LNLM",
    Technique.EXP_LIMITS: "EXPL",
    Technique.ADD_SUB_ZERO: "ADD0",
    Technique.HYBRID: "HYBR",
    Technique.SEQ_LIMITS: "SQLM",
    Technique.SEQ_ARITH_GEO: "SQAG",
    Technique.SEQ_FIXED_POINT: "SQFP",
    Technique.SEQ_HOMOGRAPHIC: "SQHM",
}

_CODE_TO_TECH: dict[str, Technique] = {v: k for k, v in _TECH_TO_CODE.items()}

_GENERATORS: dict[Technique, Callable[[str, Difficulty], GeneratedExercise]] = {
    Technique.POLY_FACTOR: generate_poly_factor,
    Technique.SQRT_CONJUGATE: generate_sqrt_conjugate,
    Technique.TRIG: generate_trig,
    Technique.NTH_ROOT: generate_nth_root,
    Technique.LN_LIMITS: generate_ln_limits,
    Technique.EXP_LIMITS: generate_exp_limits,
    Technique.ADD_SUB_ZERO: generate_add_sub_zero,
    Technique.HYBRID: generate_hybrid,
    Technique.SEQ_LIMITS: generate_seq_limits,
    Technique.SEQ_ARITH_GEO: generate_seq_arith_geo,
    Technique.SEQ_FIXED_POINT: generate_seq_fixed_point,
    Technique.SEQ_HOMOGRAPHIC: generate_seq_homographic,
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
    """Choose a technique giving quadratic weight to areas where student mastery is lower."""
    all_techs = list(Technique)
    if not mastery:
        return rng.choice(all_techs)

    weights: list[float] = []
    for tech in all_techs:
        score = float(mastery.get(tech.value, INITIAL_MASTERY))
        score = max(0.0, min(1.0, score))
        gap = max(0.12, 1.12 - score)
        weights.append(gap * gap)
    return rng.choices(all_techs, weights=weights, k=1)[0]


def _generate_steered(
    tech: Technique,
    diff: Difficulty,
    *,
    exclude_seeds: set[str] | None = None,
    avoid_sub_patterns: set[str] | None = None,
    avoid_exprs: set[str] | None = None,
    target_sub_pattern: str | None = None,
) -> GeneratedExercise:
    """Generate a verified exercise while avoiding duplicate expressions/sub-patterns and steering toward weak patterns."""
    excluded = exclude_seeds or set()
    avoid_subs = avoid_sub_patterns or set()
    avoid_ex = avoid_exprs or set()

    gen_fn = _GENERATORS[tech]
    best_fallback: GeneratedExercise | None = None
    max_draws = 16 if target_sub_pattern else 8

    for _ in range(max_draws):
        candidate_seed = encode_seed(tech, diff)
        if candidate_seed in excluded:
            continue
        ex = gen_fn(candidate_seed, diff)

        is_fresh_expr = ex.expr_latex not in avoid_ex
        is_fresh_sub = ex.sub_pattern not in avoid_subs

        if target_sub_pattern:
            if ex.sub_pattern == target_sub_pattern and is_fresh_expr:
                return ex
            if best_fallback is None or (is_fresh_expr and is_fresh_sub):
                best_fallback = ex
        else:
            if is_fresh_expr and is_fresh_sub:
                return ex
            if best_fallback is None or (is_fresh_expr and best_fallback.expr_latex in avoid_ex):
                best_fallback = ex

    if best_fallback is not None:
        return best_fallback
    return gen_fn(encode_seed(tech, diff), diff)


def generate_exercise(
    technique: Technique | str | None = None,
    difficulty: Difficulty | int | None = None,
    seed: str | None = None,
    mastery: dict[str, float] | None = None,
    exclude_seeds: set[str] | None = None,
    avoid_sub_patterns: set[str] | None = None,
    avoid_exprs: set[str] | None = None,
    target_sub_pattern: str | None = None,
) -> GeneratedExercise:
    """Generate (or deterministically replay via seed) a verified 2BAC PC exercise."""
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

    if difficulty is not None:
        diff = Difficulty(int(difficulty))
    elif mastery is not None:
        tech_m = float(mastery.get(tech.value, INITIAL_MASTERY))
        diff = choose_calibrated_difficulty(
            tech_mastery=tech_m,
            tech_attempts=3,
            global_mastery=sum(mastery.values()) / max(1, len(mastery)),
            recent_history=[],
            rng=rng,
        )
    else:
        diff = rng.choice(list(Difficulty))

    return _generate_steered(
        tech,
        diff,
        exclude_seeds=exclude_seeds,
        avoid_sub_patterns=avoid_sub_patterns,
        avoid_exprs=avoid_exprs,
        target_sub_pattern=target_sub_pattern,
    )


def generate_adaptive_exercise(
    *,
    mastery_rows: dict[str, dict[str, Any]],
    sub_pattern_rows: list[dict[str, Any]],
    recent_history: list[dict[str, Any]],
    total_solved: int,
    chapter: Chapter = Chapter.LIMITS,
    solved_by_chapter: dict[str, int] | None = None,
    requested_technique: Technique | None = None,
    requested_difficulty: Difficulty | None = None,
    exclude_seeds: set[str] | None = None,
    avoid_exprs: set[str] | None = None,
) -> tuple[GeneratedExercise, AdaptivePlan]:
    """Run the full progressive adaptive coach and generate a steered verified exercise."""
    plan = select_adaptive_plan(
        mastery_rows=mastery_rows,
        sub_pattern_rows=sub_pattern_rows,
        recent_history=recent_history,
        total_solved=total_solved,
        chapter=chapter,
        solved_by_chapter=solved_by_chapter,
        requested_technique=requested_technique,
        requested_difficulty=requested_difficulty,
    )
    ex = _generate_steered(
        plan.technique,
        plan.difficulty,
        exclude_seeds=exclude_seeds,
        avoid_sub_patterns=set(plan.avoid_sub_patterns),
        avoid_exprs=avoid_exprs,
        target_sub_pattern=plan.target_sub_pattern,
    )
    return ex, plan
