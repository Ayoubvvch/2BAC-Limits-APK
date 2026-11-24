"""Shared assembly, wrapper, and verification helpers for limit generators."""

from __future__ import annotations

import random
from typing import Callable

import sympy as sp

from ..bricks import VanishingBrick
from ..formatting import (
    RootN,
    X,
    limit_statement_latex,
    linear_factor_latex,
    poly_latex,
    root_latex,
    value_latex,
    wrap_parens,
)
from ..models import (
    ApproachSide,
    BilingualText,
    Difficulty,
    GeneratedExercise,
    LimitPoint,
    SolutionStep,
    Technique,
)
from ..verifier import verify_limit_cas, verify_numeric_trend


def make_linear_brick(x0: int, coeff: int = 1) -> VanishingBrick:
    """Trivial linear brick c*(x - x0) with slope c."""
    expr = sp.expand(sp.Integer(coeff) * (X - sp.Integer(x0)))
    tex = poly_latex(expr)
    return VanishingBrick(
        x0=x0,
        expr=expr,
        latex=tex,
        slope_limit=sp.Integer(coeff),
        family="linear",
        sub_pattern="linear",
        hint_ar="",
        hint_fr="",
        steps=[],
    )


def maybe_nonvanishing_wrapper(
    rng: random.Random,
    x0: int,
    allow_radical: bool = True,
) -> tuple[sp.Expr, str, sp.Expr] | None:
    """Create a non-zero multiplier W(x) near x0 for extra algebraic variety."""
    kind = rng.choice(["poly", "sqrt"] if allow_radical else ["poly"])
    if kind == "sqrt":
        val_sq = rng.choice([2, 3, 4, 5, 9])
        shift = val_sq - x0
        inner = X + sp.Integer(shift)
        inner_tex = poly_latex(inner)
        return RootN(inner, 2), root_latex(inner_tex, 2), sp.sqrt(sp.Integer(val_sq))

    c1 = rng.choice([1, 2])
    c0 = rng.randint(1, 5)
    expr = sp.Integer(c1) * (X**2) + sp.Integer(c0)
    tex = wrap_parens(poly_latex(expr))
    val = sp.simplify(expr.subs(X, x0))
    return expr, tex, val


def assemble_vanishing_fraction(
    seed: str,
    technique: Technique,
    sub_pattern: str,
    difficulty: Difficulty,
    num: VanishingBrick,
    den: VanishingBrick,
    extra_den: tuple[sp.Expr, str, sp.Expr] | None = None,
    extra_num: tuple[sp.Expr, str, sp.Expr] | None = None,
    tags: list[str] | None = None,
) -> GeneratedExercise:
    """Assemble N(x)/D(x) from two VanishingBricks at x0 with complete bilingual trace."""
    x0 = num.x0
    side = ApproachSide.BOTH
    if num.requires_side != ApproachSide.BOTH:
        side = num.requires_side
    elif den.requires_side != ApproachSide.BOTH:
        side = den.requires_side

    point = LimitPoint(kind="finite", value=x0, side=side)

    expr_num = num.expr
    tex_num = num.latex
    slope_num = num.slope_limit

    if extra_num is not None:
        w_e, w_t, w_v = extra_num
        expr_num = expr_num * w_e
        tex_num = rf"{wrap_parens(tex_num)} \cdot {w_t}"
        slope_num = sp.simplify(slope_num * w_v)

    expr_den = den.expr
    tex_den = den.latex
    slope_den = den.slope_limit

    if extra_den is not None:
        w_e, w_t, w_v = extra_den
        expr_den = expr_den * w_e
        tex_den = rf"{wrap_parens(tex_den)}{w_t}"
        slope_den = sp.simplify(slope_den * w_v)

    full_expr = expr_num / expr_den
    full_tex = rf"\frac{{{tex_num}}}{{{tex_den}}}"
    expected = sp.simplify(slope_num / slope_den)
    lin_tex = linear_factor_latex("x", x0)
    order = max(num.order, den.order)
    if order == 1:
        factor_tex = f"({lin_tex})"
    else:
        factor_tex = f"x^{{{order}}}" if x0 == 0 else rf"({lin_tex})^{{{order}}}"

    steps: list[SolutionStep] = [
        SolutionStep(
            title=BilingualText(ar="التعويض المباشر", fr="Substitution directe"),
            explanation=BilingualText(
                ar=f"بتعويض $x$ بـ ${x0}$ في البسط والمقام نحصل على الشكل غير المحدد $\\frac{{0}}{{0}}$.",
                fr=f"En remplaçant $x$ par ${x0}$ au numérateur et au dénominateur, on obtient une forme indéterminée $\\frac{{0}}{{0}}$.",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} {full_tex} \quad \left(\text{{FI }} \frac{{0}}{{0}}\right)",
        )
    ]

    steps.extend(num.steps)
    steps.extend(den.steps)

    num_calc_tex = value_latex(slope_num)
    if extra_num is not None:
        num_calc_tex = rf"{value_latex(num.slope_limit)} \cdot {value_latex(extra_num[2])}"
    den_calc_tex = value_latex(slope_den)
    if extra_den is not None:
        den_calc_tex = rf"{value_latex(den.slope_limit)} \cdot {value_latex(extra_den[2])}"

    # Final combination step
    steps.append(
        SolutionStep(
            title=BilingualText(ar="الجمع وحساب النهاية", fr="Conclusion et calcul final"),
            explanation=BilingualText(
                ar=f"بقسمة البسط والمقام على ${factor_tex}$ (أو اختزاله مباشرة) ثم المرور للنهاية عند ${x0}$:",
                fr=f"En divisant numérateur et dénominateur par ${factor_tex}$ puis en passant à la limite en ${x0}$ :",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} {full_tex} = \frac{{{num_calc_tex}}}{{{den_calc_tex}}} = {value_latex(expected)}",
        )
    )

    hints: list[BilingualText] = [
        BilingualText(
            ar=f"بالتعويض المباشر نجد شكلاً غير محدد $\\frac{{0}}{{0}}$؛ يجب إظهار العامل ${factor_tex}$ في البسط والمقام للاختزال.",
            fr=f"La substitution directe donne $\\frac{{0}}{{0}}$ ; il faut faire apparaître le facteur ${factor_tex}$ pour simplifier.",
        )
    ]
    if num.hint_ar:
        hints.append(BilingualText(ar=f"في البسط: {num.hint_ar}", fr=f"Au numérateur : {num.hint_fr}"))
    if den.hint_ar:
        hints.append(BilingualText(ar=f"في المقام: {den.hint_ar}", fr=f"Au dénominateur : {den.hint_fr}"))

    merged_tags = sorted(set((tags or []) + num.tags + den.tags))

    return GeneratedExercise(
        seed=seed,
        technique=technique,
        sub_pattern=sub_pattern,
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="0/0",
        hints=hints,
        steps=steps,
        tags=merged_tags,
    )


def verified_generate(
    seed: str,
    builder: Callable[[random.Random], tuple[GeneratedExercise, sp.Expr, sp.Expr]],
    max_attempts: int = 30,
) -> GeneratedExercise:
    """Run builder deterministically from seed and enforce domain + CAS oracle checks."""
    rng = random.Random(seed)
    last_err = ""
    for _ in range(max_attempts):
        ex, sympy_expr, expected_val = builder(rng)
        ok_cas, err = verify_limit_cas(sympy_expr, ex.point, expected_val)
        if not ok_cas:
            last_err = err or "CAS verification failed"
            continue
        if not verify_numeric_trend(sympy_expr, ex.point, expected_val):
            last_err = "Numeric trend sanity check failed"
            continue
        ex.verified_by_cas = True
        return ex
    raise RuntimeError(f"Failed to generate verified exercise for seed={seed}: {last_err}")
