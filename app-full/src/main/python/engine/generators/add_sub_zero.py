"""Generator for 'add and subtract 0' limits (+L - L, +ax - ax, product split, oo*0)."""

from __future__ import annotations

import random

import sympy as sp

from ..bricks import _nonzero_int, make_add_zero_brick, make_poly_brick
from ..formatting import (
    RootN,
    X,
    limit_statement_latex,
    poly_latex,
    root_latex,
    signed_combo_latex,
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
from .common import assemble_vanishing_fraction, make_linear_brick, verified_generate


def _build_add_zero_finite(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
    variant: str,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    x0 = _nonzero_int(rng, -4, 4)
    num = make_add_zero_brick(rng, x0, variant=variant)
    if difficulty == Difficulty.BASIC:
        den = make_linear_brick(x0, coeff=1)
    elif difficulty == Difficulty.INTERMEDIATE:
        den = make_poly_brick(rng, x0, min_deg=2, max_deg=2, coeff_bound=3)
    else:
        den = make_poly_brick(rng, x0, min_deg=2, max_deg=3, coeff_bound=3)
        if rng.random() < 0.35:
            num, den = den, num
    ex = assemble_vanishing_fraction(
        seed=seed,
        technique=Technique.ADD_SUB_ZERO,
        sub_pattern=f"add_zero_{variant}",
        difficulty=difficulty,
        num=num,
        den=den,
    )
    return ex, num.expr / den.expr, sp.sympify(ex.answer_sympy_str)


def _build_add_zero_inf(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Mixed-index roots at +-oo forcing +ax - ax."""
    inf_kind = "+oo" if difficulty == Difficulty.BASIC else rng.choice(["+oo", "-oo"])
    point = LimitPoint(kind=inf_kind, side=ApproachSide.POS_INF if inf_kind == "+oo" else ApproachSide.NEG_INF)

    a = rng.choice([1, 2])
    b = _nonzero_int(rng, -6, 6)
    c = rng.randint(-5, 5)
    d = _nonzero_int(rng, -6, 6)
    e = rng.randint(-4, 4)
    f = rng.randint(-4, 4)

    # Ensure asymptotic terms don't cancel completely if we want interesting nonzero limits
    q_order = 3 if inf_kind == "-oo" else rng.choice([3, 3, 4])

    p1 = sp.Integer(a**2) * X**2 + sp.Integer(b) * X + sp.Integer(c)
    if q_order == 3:
        p2 = sp.Integer(a**3) * X**3 + sp.Integer(d) * X**2 + sp.Integer(e) * X + sp.Integer(f)
    else:
        p2 = sp.Integer(a**4) * X**4 + sp.Integer(d) * X**3 + sp.Integer(e) * X**2 + sp.Integer(f)

    r1_tex = root_latex(poly_latex(p1), 2)
    r2_tex = root_latex(poly_latex(p2), q_order)
    ax_tex = "x" if a == 1 else f"{a}x"

    if inf_kind == "+oo":
        expr = RootN(p1, 2) - RootN(p2, q_order)
        full_tex = f"{r1_tex} - {r2_tex}"
        s1 = sp.Rational(b, 2 * a)
        s2 = sp.Rational(d, q_order * (a ** (q_order - 1)))
        expected = sp.simplify(s1 - s2)
        split_tex = rf"({r1_tex} - {ax_tex}) - ({r2_tex} - {ax_tex})"
        add_note_ar = f"نضيف ونطرح ${ax_tex}$ (الحد السائد المشترك لكلا الجذرين عند $+\\infty$):"
        add_note_fr = f"On ajoute et on retranche ${ax_tex}$ (terme dominant commun en $+\\infty$) :"
        ans_calc_tex = rf"{value_latex(s1)} - ({value_latex(s2)}) = {value_latex(expected)}"
    else:
        # At -oo, sqrt(a^2 x^2 + ...) ~ -ax while root_3(a^3 x^3 + ...) ~ +ax
        expr = RootN(p1, 2) + RootN(p2, 3)
        full_tex = f"{r1_tex} + {r2_tex}"
        s1 = sp.Rational(-b, 2 * a)
        s2 = sp.Rational(d, 3 * (a**2))
        expected = sp.simplify(s1 + s2)
        split_tex = rf"({r1_tex} + {ax_tex}) + ({r2_tex} - {ax_tex})"
        add_note_ar = (
            f"انتبه للإشارة عند $-\\infty$: $\\sqrt{{{a*a}x^2}} = -{ax_tex}$ بينما $\\sqrt[3]{{{a**3}x^3}} = +{ax_tex}$. "
            f"لذا نضيف ونطرح ${ax_tex}$:"
        )
        add_note_fr = (
            f"Attention au signe en $-\\infty$ : $\\sqrt{{{a*a}x^2}} = -{ax_tex}$ tandis que $\\sqrt[3]{{{a**3}x^3}} = +{ax_tex}$. "
            f"On ajoute et on retranche ${ax_tex}$ :"
        )
        ans_calc_tex = rf"{value_latex(s1)} + ({value_latex(s2)}) = {value_latex(expected)}"

    steps = [
        SolutionStep(
            title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Forme indéterminée"),
            explanation=BilingualText(
                ar=f"عند ${point.point_latex()}$ نحصل على $\\infty - \\infty$ مع جذرين من رتبتين مختلفتين ($2$ و ${q_order}$).",
                fr=f"En ${point.point_latex()}$, on obtient $\\infty - \\infty$ avec deux radicaux d'ordres différents ($2$ et ${q_order}$).",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} \left({full_tex}\right) \quad (\text{{FI }} \infty - \infty)",
        ),
        SolutionStep(
            title=BilingualText(
                ar=f"إضافة وطرح ${ax_tex}$",
                fr=f"Ajout et retranchement de ${ax_tex}$",
            ),
            explanation=BilingualText(ar=add_note_ar, fr=add_note_fr),
            latex=rf"{full_tex} = {split_tex}",
        ),
        SolutionStep(
            title=BilingualText(ar="حساب كل جزء بالمرافق الموافق", fr="Calcul de chaque terme par son conjugué"),
            explanation=BilingualText(
                ar=f"نطبق المرافق التربيعي على الجزء الأول، والمرافق من الرتبة ${q_order}$ على الجزء الثاني:",
                fr=f"On applique le conjugué d'ordre 2 au premier terme et celui d'ordre ${q_order}$ au second :",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} \left({full_tex}\right) = {ans_calc_tex}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.ADD_SUB_ZERO,
        sub_pattern=f"add_zero_inf_{inf_kind}",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo",
        hints=[
            BilingualText(
                ar=f"بسبب اختلاف رتبتي الجذرين عند ${point.point_latex()}$، أضف واطرح ${ax_tex}$ لفصل كل جذر.",
                fr=f"Les deux radicaux ayant des ordres différents en ${point.point_latex()}$, ajoutez et retranchez ${ax_tex}$ pour les séparer.",
            )
        ],
        steps=steps,
        tags=["add_zero", "inf_minus_inf"],
    )
    return ex, expr, expected


def _build_add_zero_oo_times_0(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """x * (sqrt(1 + a/x) - root_3(1 + b/x)) as x -> +oo (forces adding & subtracting 1)."""
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    a = _nonzero_int(rng, -6, 6)
    b = _nonzero_int(rng, -6, 6)
    while 3 * a == 2 * b:
        b = _nonzero_int(rng, -6, 6)

    u1 = 1 + sp.Integer(a) / X
    u2 = 1 + sp.Integer(b) / X
    expr = X * (RootN(u1, 2) - RootN(u2, 3))

    a_frac = rf"\frac{{{a}}}{{x}}" if a > 0 else rf"- \frac{{{-a}}}{{x}}"
    b_frac = rf"\frac{{{b}}}{{x}}" if b > 0 else rf"- \frac{{{-b}}}{{x}}"
    u1_tex = f"1 + {a_frac}" if a > 0 else f"1 {a_frac}"
    u2_tex = f"1 + {b_frac}" if b > 0 else f"1 {b_frac}"
    r1_tex = root_latex(u1_tex, 2)
    r2_tex = root_latex(u2_tex, 3)

    full_tex = rf"x\left({r1_tex} - {r2_tex}\right)"
    expected = sp.simplify(sp.Rational(a, 2) - sp.Rational(b, 3))

    steps = [
        SolutionStep(
            title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Forme indéterminée"),
            explanation=BilingualText(
                ar="عند $+\\infty$ نحصل على الشكل غير المحدد $\\infty \\times 0$.",
                fr="En $+\\infty$, on obtient une forme indéterminée $\\infty \\times 0$.",
            ),
            latex=rf"\lim_{{x \to +\infty}} {full_tex} \quad (\text{{FI }} \infty \times 0)",
        ),
        SolutionStep(
            title=BilingualText(ar="إضافة وطرح العدد 1", fr="Ajout et retranchement de 1"),
            explanation=BilingualText(
                ar="نضيف ونطرح $1$ داخل القوسين لفصل الجذر التربيعي عن الجذر التكعيبي:",
                fr="On ajoute et on retranche $1$ à l'intérieur des parenthèses pour séparer les deux radicaux :",
            ),
            latex=rf"x\left[({r1_tex} - 1) - ({r2_tex} - 1)\right]",
        ),
        SolutionStep(
            title=BilingualText(ar="المرافق والاختزال بـ x", fr="Conjugués et simplification par x"),
            explanation=BilingualText(
                ar="نطبق المرافق على كل جزء فنختزل بـ $x$:",
                fr="On applique le conjugué adapté à chaque terme, ce qui simplifie le facteur $x$ :",
            ),
            latex=(
                rf"\lim_{{x \to +\infty}} \left(\frac{{{a}}}{{{r1_tex} + 1}} - "
                rf"\frac{{{b}}}{{\left({r2_tex}\right)^2 + {r2_tex} + 1}}\right) = \frac{{{a}}}{{2}} - \frac{{{b}}}{{3}} = {value_latex(expected)}"
            ),
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.ADD_SUB_ZERO,
        sub_pattern="add_zero_oo_times_0",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo * 0",
        hints=[
            BilingualText(
                ar="أضف واطرح $1$ داخل القوسين لتطبيق المرافق على كل جذر ثم اختزل بـ $x$.",
                fr="Ajoutez et retranchez $1$ dans la parenthèse pour utiliser le conjugué sur chaque radical puis simplifier par $x$.",
            )
        ],
        steps=steps,
        tags=["add_zero", "oo_times_0"],
    )
    return ex, expr, expected


def generate_add_sub_zero(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a verified 'add and subtract 0' limit exercise."""

    def _build(rng: random.Random):
        if difficulty == Difficulty.BASIC:
            choice = rng.choices(["mixed", "inf"], weights=[0.7, 0.3])[0]
        elif difficulty == Difficulty.INTERMEDIATE:
            choice = rng.choices(["mixed", "product", "inf"], weights=[0.4, 0.3, 0.3])[0]
        else:
            choice = rng.choices(["mixed", "product", "inf", "oo_0"], weights=[0.25, 0.25, 0.3, 0.2])[0]

        if choice == "mixed":
            return _build_add_zero_finite(rng, seed, difficulty, variant="mixed_roots")
        if choice == "product":
            return _build_add_zero_finite(rng, seed, difficulty, variant="product_split")
        if choice == "inf":
            return _build_add_zero_inf(rng, seed, difficulty)
        return _build_add_zero_oo_times_0(rng, seed, difficulty)

    return verified_generate(seed, _build)
