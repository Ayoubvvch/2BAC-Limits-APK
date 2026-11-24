"""Hybrid multi-technique composer combining distinct vanishing bricks and wrappers."""

from __future__ import annotations

import random

import sympy as sp

from ..bricks import (
    VanishingBrick,
    _nonzero_int,
    _poly_with_value_at,
    make_add_zero_brick,
    make_exp_brick,
    make_ln_brick,
    make_nth_conj_brick,
    make_poly_brick,
    make_poly_order2_brick,
    make_sqrt_conj_brick,
    make_subst_root_brick,
    make_trig_brick,
    make_trig_order2_brick,
)
from ..formatting import (
    RootN,
    X,
    limit_statement_latex,
    linear_factor_latex,
    poly_latex,
    root_latex,
    value_latex,
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
from .common import assemble_vanishing_fraction, maybe_nonvanishing_wrapper, verified_generate


def _make_brick_by_family(rng: random.Random, family: str, x0: int) -> VanishingBrick:
    if family == "poly":
        return make_poly_brick(rng, x0, min_deg=2, max_deg=3, coeff_bound=3)
    if family == "sqrt_conj":
        return make_sqrt_conj_brick(rng, x0, two_roots=rng.choice([False, True]), poly_deg=1)
    if family == "nth_conj":
        return make_nth_conj_brick(rng, x0, n=3, two_roots=False)
    if family == "add_zero":
        return make_add_zero_brick(rng, x0, variant=rng.choice(["mixed_roots", "product_split"]))
    if family == "subst_root":
        return make_subst_root_brick(rng, x0, k=rng.choice([4, 6]))
    if family == "trig":
        return make_trig_brick(rng, x0)
    if family == "ln":
        return make_ln_brick(rng, x0, poly_deg=1)
    if family == "exp":
        return make_exp_brick(rng, x0)
    raise ValueError(f"Unknown brick family: {family}")


def _build_hybrid_pair(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    x0 = _nonzero_int(rng, -4, 4)

    if difficulty == Difficulty.BASIC:
        # Clean pair: radical, trig, ln, or exp brick over polynomial
        num_fam, den_fam = rng.choice([
            ("sqrt_conj", "poly"),
            ("nth_conj", "poly"),
            ("trig", "poly"),
            ("ln", "poly"),
            ("exp", "poly"),
        ])
    elif difficulty == Difficulty.INTERMEDIATE:
        pairs = [
            ("sqrt_conj", "nth_conj"),
            ("nth_conj", "sqrt_conj"),
            ("add_zero", "sqrt_conj"),
            ("subst_root", "poly"),
            ("trig", "sqrt_conj"),
            ("sqrt_conj", "trig"),
            ("ln", "exp"),
            ("exp", "ln"),
            ("ln", "sqrt_conj"),
            ("exp", "trig"),
        ]
        num_fam, den_fam = rng.choice(pairs)
    else:
        families = ["sqrt_conj", "nth_conj", "add_zero", "subst_root", "trig", "ln", "exp"]
        num_fam, den_fam = rng.sample(families, 2)

    num = _make_brick_by_family(rng, num_fam, x0)
    den = _make_brick_by_family(rng, den_fam, x0)

    extra_den = None
    extra_num = None
    if difficulty == Difficulty.CHALLENGE and rng.random() < 0.45:
        if rng.random() < 0.5:
            extra_den = maybe_nonvanishing_wrapper(rng, x0, allow_radical=True)
        else:
            extra_num = maybe_nonvanishing_wrapper(rng, x0, allow_radical=False)

    ex = assemble_vanishing_fraction(
        seed=seed,
        technique=Technique.HYBRID,
        sub_pattern=f"hybrid_{num_fam}_over_{den_fam}",
        difficulty=difficulty,
        num=num,
        den=den,
        extra_den=extra_den,
        extra_num=extra_num,
        tags=["hybrid_composer"],
    )
    full_expr = (num.expr * (extra_num[0] if extra_num else 1)) / (
        den.expr * (extra_den[0] if extra_den else 1)
    )
    return ex, full_expr, sp.sympify(ex.answer_sympy_str)


def _build_reciprocal_wrapper(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """(1/root_n(P(x)) - 1/L) / D(x) forcing common denominator + conjugate + D's method."""
    x0 = _nonzero_int(rng, -4, 4)
    n = rng.choice([2, 3])
    L = rng.choice([1, 2, 3]) if n == 2 else rng.choice([1, 2])
    p_expr, r_expr = _poly_with_value_at(rng, x0, L**n, deg=1, coeff_bound=4)

    den_fam = "poly" if difficulty == Difficulty.BASIC else rng.choice(["poly", "sqrt_conj", "nth_conj"])
    den = _make_brick_by_family(rng, den_fam, x0)

    p_tex = poly_latex(p_expr)
    rad_tex = root_latex(p_tex, n)
    rad_expr = RootN(p_expr, n)

    top_expr = sp.Integer(1) / rad_expr - sp.Rational(1, L)
    full_expr = top_expr / den.expr

    top_tex = rf"\frac{{1}}{{{rad_tex}}} - \frac{{1}}{{{L}}}"
    full_tex = rf"\frac{{{top_tex}}}{{{den.latex}}}"

    # 1/rad - 1/L = (L - rad) / (L * rad), slope of (L - rad) is -r(x0) / (n * L^(n-1))
    num_slope = sp.simplify(-r_expr.subs(X, x0) / (n * (L ** (n - 1))))
    expected = sp.simplify(num_slope / (sp.Integer(L**2) * den.slope_limit))

    point = LimitPoint(kind="finite", value=x0, side=ApproachSide.BOTH)
    lin_tex = linear_factor_latex("x", x0)

    steps = [
        SolutionStep(
            title=BilingualText(ar="توحيد المقامات في البسط", fr="Réduction au même dénominateur au numérateur"),
            explanation=BilingualText(
                ar=f"بالتعويض نجد $\\frac{{0}}{{0}}$. نبدأ بتوحيد المقام في البسط:",
                fr=f"On a une forme indéterminée $\\frac{{0}}{{0}}$. On réduit d'abord le numérateur au même dénominateur :",
            ),
            latex=rf"{full_tex} = \frac{{{L} - {rad_tex}}}{{{L}\,{rad_tex}\left({den.latex}\right)}}",
        ),
        SolutionStep(
            title=BilingualText(
                ar=f"تطبيق المرافق من الرتبة {n}",
                fr=f"Expression conjuguée d'ordre {n}",
            ),
            explanation=BilingualText(
                ar=f"نضرب في مرافق $({L} - {rad_tex})$ من الرتبة {n} لإظهار العامل $({lin_tex})$:",
                fr=f"On utilise l'expression conjuguée d'ordre {n} de $({L} - {rad_tex})$ pour faire apparaître $({lin_tex})$ :",
            ),
            latex=rf"\lim_{{x \to {x0}}} \frac{{{L} - {rad_tex}}}{{{lin_tex}}} = {value_latex(num_slope)}",
        ),
    ]
    steps.extend(den.steps)
    steps.append(
        SolutionStep(
            title=BilingualText(ar="الاختزال وحساب النتيجة النهائية", fr="Conclusion finale"),
            explanation=BilingualText(
                ar=f"بدمج كل العوامل وحساب النهاية عند ${x0}$ (مع $L\\sqrt[{n}]{{P({x0})}} = {L**2}$):",
                fr=f"En regroupant tous les facteurs en ${x0}$ (avec $L\\sqrt[{n}]{{P({x0})}} = {L**2}$) :",
            ),
            latex=rf"\lim_{{x \to {x0}}} {full_tex} = \frac{{{value_latex(num_slope)}}}{{{L**2} \cdot ({value_latex(den.slope_limit)})}} = {value_latex(expected)}",
        )
    )

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.HYBRID,
        sub_pattern=f"reciprocal_order{n}_over_{den_fam}",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="0/0",
        hints=[
            BilingualText(
                ar="ابدأ بتوحيد المقامات في البسط، ثم استعمل المرافق الموافق.",
                fr="Commencez par réduire le numérateur au même dénominateur, puis utilisez le conjugué adapté.",
            ),
            BilingualText(
                ar=f"في المقام: {den.hint_ar}",
                fr=f"Au dénominateur : {den.hint_fr}",
            ) if den.hint_ar else BilingualText(
                ar=f"اختزل بـ $({lin_tex})$.",
                fr=f"Simplifiez par $({lin_tex})$.",
            ),
        ],
        steps=steps,
        tags=["hybrid_composer", "reciprocal_wrapper"],
    )
    return ex, full_expr, expected


def _build_order2_hybrid(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Compose two order-2 vanishing bricks (e.g. 1 - cos(a(x-x0)) over (x-x0)^2 * Q(x))."""
    x0 = rng.choice([-3, -2, -1, 0, 1, 2, 3])
    trig2 = make_trig_order2_brick(rng, x0)
    poly2 = make_poly_order2_brick(rng, x0, coeff_bound=3)
    if rng.random() < 0.5:
        num, den = trig2, poly2
        sub_name = f"hybrid_order2_{trig2.sub_pattern}_over_poly2"
    else:
        num, den = poly2, trig2
        sub_name = f"hybrid_order2_poly2_over_{trig2.sub_pattern}"

    extra_den = maybe_nonvanishing_wrapper(rng, x0, allow_radical=True) if rng.random() < 0.4 else None
    ex = assemble_vanishing_fraction(
        seed=seed,
        technique=Technique.HYBRID,
        sub_pattern=sub_name,
        difficulty=difficulty,
        num=num,
        den=den,
        extra_den=extra_den,
        tags=["hybrid_composer", "order_2"],
    )
    full_expr = num.expr / (den.expr * (extra_den[0] if extra_den else 1))
    return ex, full_expr, sp.sympify(ex.answer_sympy_str)


def generate_hybrid(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a verified multi-technique composite limit exercise."""

    def _build(rng: random.Random):
        if difficulty == Difficulty.CHALLENGE:
            r = rng.random()
            if r < 0.25:
                return _build_order2_hybrid(rng, seed, difficulty)
            if r < 0.55:
                return _build_reciprocal_wrapper(rng, seed, difficulty)
            return _build_hybrid_pair(rng, seed, difficulty)
        if difficulty == Difficulty.INTERMEDIATE and rng.random() < 0.25:
            return _build_reciprocal_wrapper(rng, seed, difficulty)
        return _build_hybrid_pair(rng, seed, difficulty)

    return verified_generate(seed, _build)
