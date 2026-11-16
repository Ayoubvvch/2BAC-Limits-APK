"""Generator for quadratic conjugate limits (finite 0/0, +-oo traps, nested sqrt)."""

from __future__ import annotations

import random

import sympy as sp

from ..bricks import (
    _nonzero_int,
    make_poly_brick,
    make_sqrt_conj_brick,
)
from ..formatting import (
    RootN,
    X,
    limit_statement_latex,
    poly_latex,
    root_latex,
    signed_combo_latex,
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
from .common import assemble_vanishing_fraction, make_linear_brick, verified_generate


def _build_finite_sqrt(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    x0 = _nonzero_int(rng, -5, 5)
    if difficulty == Difficulty.BASIC:
        num = make_sqrt_conj_brick(rng, x0, two_roots=False, poly_deg=1)
        den = make_linear_brick(x0, coeff=1) if rng.random() < 0.6 else make_poly_brick(rng, x0, min_deg=1, max_deg=2, coeff_bound=3)
        if rng.random() < 0.25:
            num, den = den, num
        return _finish(seed, "sqrt_finite_basic", difficulty, num, den)

    if difficulty == Difficulty.INTERMEDIATE:
        if rng.random() < 0.5:
            num = make_sqrt_conj_brick(rng, x0, two_roots=True, poly_deg=1)
            den = make_poly_brick(rng, x0, min_deg=2, max_deg=2, coeff_bound=4)
        else:
            num = make_sqrt_conj_brick(rng, x0, two_roots=False, poly_deg=2)
            den = make_poly_brick(rng, x0, min_deg=2, max_deg=3, coeff_bound=3)
        if rng.random() < 0.35:
            num, den = den, num
        return _finish(seed, "sqrt_finite_inter", difficulty, num, den)

    # Challenge: Double conjugate (sqrt_conj over sqrt_conj)
    num = make_sqrt_conj_brick(rng, x0, two_roots=rng.choice([False, True]), poly_deg=rng.choice([1, 2]))
    den = make_sqrt_conj_brick(rng, x0, two_roots=rng.choice([False, True]), poly_deg=1)
    return _finish(seed, "sqrt_double_conjugate", difficulty, num, den)


def _finish(seed: str, sub_pattern: str, difficulty: Difficulty, num, den) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    ex = assemble_vanishing_fraction(
        seed=seed,
        technique=Technique.SQRT_CONJUGATE,
        sub_pattern=sub_pattern,
        difficulty=difficulty,
        num=num,
        den=den,
    )
    return ex, num.expr / den.expr, sp.sympify(ex.answer_sympy_str)


def _build_inf_sqrt_linear(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    if difficulty == Difficulty.BASIC:
        inf_kind = "+oo"
        a = rng.choice([1, 2])
        d = 0
    else:
        inf_kind = rng.choice(["+oo", "-oo", "-oo"])  # Emphasize -oo |x|=-x trap
        a = rng.choice([1, 2, 3])
        d = rng.randint(-3, 3)

    b = _nonzero_int(rng, -6, 6)
    c = rng.randint(-5, 5)
    p_expr = sp.Integer(a * a) * (X**2) + sp.Integer(b) * X + sp.Integer(c)
    p_tex = poly_latex(p_expr)
    rad_tex = root_latex(p_tex, 2)
    point = LimitPoint(kind=inf_kind, side=ApproachSide.POS_INF if inf_kind == "+oo" else ApproachSide.NEG_INF)

    if inf_kind == "+oo":
        lin_expr = sp.Integer(a) * X + sp.Integer(d)
        expr = RootN(p_expr, 2) - lin_expr
        lin_tex = poly_latex(lin_expr)
        full_tex = signed_combo_latex(rad_tex, "-", lin_tex)
        conj_tex = signed_combo_latex(rad_tex, "+", lin_tex)
        diff_poly = sp.expand(p_expr - lin_expr**2)
        expected = sp.Rational(b - 2 * a * d, 2 * a)
        abs_note_ar = rf"بما أن $x \to +\infty$ فإن $\sqrt{{x^2}} = |x| = x$."
        abs_note_fr = rf"Comme $x \to +\infty$, on a $\sqrt{{x^2}} = |x| = x$."
    else:
        # At -oo, sqrt(a^2 x^2 + ...) ~ -ax, so cancel with +ax + d
        lin_expr = sp.Integer(a) * X + sp.Integer(d)
        expr = RootN(p_expr, 2) + lin_expr
        lin_tex = poly_latex(lin_expr)
        full_tex = signed_combo_latex(rad_tex, "+", lin_tex)
        conj_tex = signed_combo_latex(rad_tex, "-", lin_tex)
        diff_poly = sp.expand(p_expr - (-lin_expr) ** 2)
        expected = sp.Rational(2 * a * d - b, 2 * a)
        abs_note_ar = rf"انتبه للإشارة! بما أن $x \to -\infty$ فإن $\sqrt{{x^2}} = |x| = -x$."
        abs_note_fr = rf"Attention au signe ! Comme $x \to -\infty$, on a $\sqrt{{x^2}} = |x| = -x$."

    steps = [
        SolutionStep(
            title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Forme indéterminée"),
            explanation=BilingualText(
                ar=f"عند ${point.point_latex()}$ نحصل على الشكل غير المحدد $\\infty - \\infty$.",
                fr=f"En ${point.point_latex()}$, on obtient une forme indéterminée $\\infty - \\infty$.",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} \left({full_tex}\right) \quad (\text{{FI }} \infty - \infty)",
        ),
        SolutionStep(
            title=BilingualText(ar="الضرب في المرافق", fr="Multiplication par le conjugué"),
            explanation=BilingualText(
                ar=f"نضرب ونقسم على المرافق $({conj_tex})$:",
                fr=f"On multiplie et on divise par l'expression conjuguée $({conj_tex})$ :",
            ),
            latex=rf"{full_tex} = \frac{{{poly_latex(diff_poly)}}}{{{conj_tex}}}",
        ),
        SolutionStep(
            title=BilingualText(ar="التعميل بالحد الأكبر درجة والاختزال", fr="Factorisation par le terme dominant"),
            explanation=BilingualText(ar=abs_note_ar, fr=abs_note_fr),
            latex=rf"\lim_{{x \to {point.point_latex()}}} \frac{{{poly_latex(diff_poly)}}}{{{conj_tex}}} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.SQRT_CONJUGATE,
        sub_pattern="sqrt_inf_linear",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo",
        hints=[
            BilingualText(
                ar=f"شكل غير محدد $\\infty - \\infty$: اضرب في المرافق $({conj_tex})$ ثم عمّل بـ $x$ (تذكر $|x|$ عند ${point.point_latex()}$).",
                fr=f"Forme indéterminée $\\infty - \\infty$ : multipliez par $({conj_tex})$ puis factorisez par $x$ (attention à $|x|$ en ${point.point_latex()}$).",
            )
        ],
        steps=steps,
        tags=["inf_minus_inf", "abs_x_trap" if inf_kind == "-oo" else "pos_inf"],
    )
    return ex, expr, expected


def _build_inf_sqrt_sqrt(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    inf_kind = rng.choice(["+oo", "-oo"])
    point = LimitPoint(kind=inf_kind, side=ApproachSide.POS_INF if inf_kind == "+oo" else ApproachSide.NEG_INF)
    a = rng.choice([1, 2, 3])
    b1 = rng.randint(-6, 6)
    b2 = _nonzero_int(rng, -6, 6)
    while b1 == b2:
        b2 = _nonzero_int(rng, -6, 6)
    c1, c2 = rng.randint(-4, 4), rng.randint(-4, 4)

    p1 = sp.Integer(a * a) * X**2 + sp.Integer(b1) * X + sp.Integer(c1)
    p2 = sp.Integer(a * a) * X**2 + sp.Integer(b2) * X + sp.Integer(c2)
    expr = RootN(p1, 2) - RootN(p2, 2)
    r1_tex, r2_tex = root_latex(poly_latex(p1), 2), root_latex(poly_latex(p2), 2)
    full_tex = f"{r1_tex} - {r2_tex}"
    conj_tex = f"{r1_tex} + {r2_tex}"
    diff_poly = sp.expand(p1 - p2)
    expected = sp.Rational(b1 - b2, 2 * a) if inf_kind == "+oo" else sp.Rational(b2 - b1, 2 * a)

    steps = [
        SolutionStep(
            title=BilingualText(ar="المرافق التربيعي عند اللانهاية", fr="Expression conjuguée à l'infini"),
            explanation=BilingualText(
                ar=f"لدينا شكل غير محدد $\\infty - \\infty$ لأن الجذرين لهما نفس الحد المهيمن. نضرب في المرافق $({conj_tex})$:",
                fr=f"Forme indéterminée $\\infty - \\infty$ ; on multiplie par l'expression conjuguée $({conj_tex})$ :",
            ),
            latex=rf"{full_tex} = \frac{{{poly_latex(diff_poly)}}}{{{conj_tex}}}",
        ),
        SolutionStep(
            title=BilingualText(ar="التعميل بـ |x| وحساب النهاية", fr="Factorisation par |x|"),
            explanation=BilingualText(
                ar=f"بإخراج $|x| = {'x' if inf_kind == '+oo' else '-x'}$ من المقام ثم الاختزال بـ $x$:",
                fr=f"En factorisant le dénominateur par $|x| = {'x' if inf_kind == '+oo' else '-x'}$ puis en simplifiant par $x$ :",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} \frac{{{poly_latex(diff_poly)}}}{{{conj_tex}}} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.SQRT_CONJUGATE,
        sub_pattern="sqrt_inf_sqrt",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo",
        hints=[
            BilingualText(
                ar=f"استعمل المرافق $({conj_tex})$ ثم عمّل بـ $x$ مع الانتباه لإشارة $|x|$ عند ${point.point_latex()}$.",
                fr=f"Multipliez par $({conj_tex})$ puis factorisez par $x$ en prenant garde au signe de $|x|$ en ${point.point_latex()}$.",
            )
        ],
        steps=steps,
        tags=["inf_minus_inf", "two_roots"],
    )
    return ex, expr, expected


def _build_inf_nested_sqrt(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    a = rng.choice([1, 4, 9, 2])
    b = _nonzero_int(rng, -5, 5)
    c = rng.randint(0, 6)
    d = rng.randint(-4, 4)

    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    inner_right = sp.Integer(a) * X + sp.Integer(d)
    inner_nested = sp.Integer(a) * X + sp.Integer(b) * RootN(X + sp.Integer(c), 2)
    expr = RootN(inner_nested, 2) - RootN(inner_right, 2)

    b_rad_tex = (
        f"{'' if b == 1 else '-' if b == -1 else b}"
        + root_latex(poly_latex(X + sp.Integer(c)), 2)
    )
    nested_inner_tex = signed_combo_latex(poly_latex(sp.Integer(a) * X), "+" if b > 0 else "-", b_rad_tex.lstrip("-"))
    r1_tex = root_latex(nested_inner_tex, 2)
    r2_tex = root_latex(poly_latex(inner_right), 2)
    full_tex = f"{r1_tex} - {r2_tex}"
    conj_tex = f"{r1_tex} + {r2_tex}"
    diff_tex = signed_combo_latex(b_rad_tex, "-", str(d))
    expected = sp.simplify(sp.Integer(b) / (2 * sp.sqrt(sp.Integer(a))))

    steps = [
        SolutionStep(
            title=BilingualText(ar="المرافق مع جذر متداخل", fr="Expression conjuguée avec radical imbriqué"),
            explanation=BilingualText(
                ar="عند $+\\infty$ نحصل على $\\infty - \\infty$. نضرب في المرافق:",
                fr="En $+\\infty$, on a une forme indéterminée $\\infty - \\infty$. On multiplie par l'expression conjuguée :",
            ),
            latex=rf"{full_tex} = \frac{{{diff_tex}}}{{{conj_tex}}}",
        ),
        SolutionStep(
            title=BilingualText(ar=r"التعميل بـ \sqrt{x}", fr=r"Factorisation par \sqrt{x}"),
            explanation=BilingualText(
                ar=r"نُعمّل البسط والمقام بـ $\sqrt{x}$ ونختزل:",
                fr=r"On factorise le numérateur et le dénominateur par $\sqrt{x}$ et on simplifie :",
            ),
            latex=rf"\lim_{{x \to +\infty}} \frac{{{diff_tex}}}{{{conj_tex}}} = \frac{{{b}}}{{2\sqrt{{{a}}}}} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.SQRT_CONJUGATE,
        sub_pattern="sqrt_nested_inf",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo",
        hints=[
            BilingualText(
                ar=r"اضرب في المرافق ثم عمّل البسط والمقام بـ $\sqrt{x}$.",
                fr=r"Multipliez par l'expression conjuguée puis factorisez par $\sqrt{x}$.",
            )
        ],
        steps=steps,
        tags=["nested_sqrt", "inf_minus_inf"],
    )
    return ex, expr, expected


def _build_inf_no_conj_trap(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Trap where dominant coefficients don't cancel: factoring by |x| solves it directly."""
    inf_kind = rng.choice(["+oo", "-oo"])
    point = LimitPoint(kind=inf_kind, side=ApproachSide.POS_INF if inf_kind == "+oo" else ApproachSide.NEG_INF)
    a = rng.choice([1, 2, 3])
    k = _nonzero_int(rng, -4, 4)
    while abs(k) == a:
        k = _nonzero_int(rng, -4, 4)

    b = _nonzero_int(rng, -5, 5)
    c = rng.randint(-4, 4)
    p_expr = sp.Integer(a * a) * X**2 + sp.Integer(b) * X + sp.Integer(c)
    d = rng.randint(-4, 4)
    lin_expr = sp.Integer(k) * X + sp.Integer(d)
    expr = RootN(p_expr, 2) + lin_expr

    rad_tex = root_latex(poly_latex(p_expr), 2)
    full_tex = signed_combo_latex(rad_tex, "+", poly_latex(lin_expr))

    # Dominant term inside factor x: if +oo -> (a + k)*x; if -oo -> (-a + k)*x
    eff_coeff = (a + k) if inf_kind == "+oo" else (-a + k)
    if inf_kind == "+oo":
        expected = sp.oo if eff_coeff > 0 else -sp.oo
    else:
        expected = -sp.oo if eff_coeff > 0 else sp.oo

    sign_rad = "+" if inf_kind == "+oo" else "-"
    a_rad_tex = "" if a == 1 else str(a)
    k_str = f" + {k}" if k > 0 else f" - {-k}"
    d_str = "" if d == 0 else (f" + {d}/x" if d > 0 else f" - {-d}/x")
    b_str = f" + \\frac{{{b}}}{{{a*a}x}}" if b > 0 else f" - \\frac{{{-b}}}{{{a*a}x}}"
    c_str = "" if c == 0 else (f" + \\frac{{{c}}}{{{a*a}x^2}}" if c > 0 else f" - \\frac{{{-c}}}{{{a*a}x^2}}")

    steps = [
        SolutionStep(
            title=BilingualText(ar="فخّ المرافق: التعميل مباشرة دون مرافق", fr="Piège : factorisation directe sans conjugué"),
            explanation=BilingualText(
                ar=(
                    f"انتبه! الحدان المهيمنان لا يختزلان بعضهما ($\\sqrt{{{a*a}x^2}} = {a}|x|$ و ${k}x$). "
                    f"لذا لا حاجة للمرافق؛ يكفي التعميل بـ $x$!"
                ),
                fr=(
                    f"Attention ! Les termes dominants ne s'annulent pas ($\\sqrt{{{a*a}x^2}} = {a}|x|$ et ${k}x$). "
                    f"Inutile d'utiliser le conjugué : il suffit de factoriser par $x$ !"
                ),
            ),
            latex=rf"{full_tex} = x\left({sign_rad}{a_rad_tex}\sqrt{{1{b_str}{c_str}}}{k_str}{d_str}\right) \to {value_latex(expected)}",
        )
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.SQRT_CONJUGATE,
        sub_pattern="sqrt_no_conj_trap",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo" if ((inf_kind == "+oo" and k < 0) or (inf_kind == "-oo" and k > 0)) else "oo + oo",
        hints=[
            BilingualText(
                ar="تحقق من المعاملين المهيمنين أولاً! هل يختزلان أم يكفي التعميل بـ $x$؟",
                fr="Vérifiez d'abord les coefficients dominants ! S'annulent-ils ou suffit-il de factoriser par $x$ ?",
            )
        ],
        steps=steps,
        tags=["trap_no_conjugate"],
    )
    return ex, expr, expected


def generate_sqrt_conjugate(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a verified quadratic conjugate limit exercise."""

    def _build(rng: random.Random):
        if difficulty == Difficulty.BASIC:
            choice = rng.choices(["finite", "inf_lin"], weights=[0.65, 0.35])[0]
        elif difficulty == Difficulty.INTERMEDIATE:
            choice = rng.choices(["finite", "inf_lin", "inf_sqrt", "trap"], weights=[0.35, 0.35, 0.2, 0.1])[0]
        else:
            choice = rng.choices(["finite", "inf_lin", "inf_sqrt", "nested"], weights=[0.35, 0.25, 0.2, 0.2])[0]

        if choice == "finite":
            return _build_finite_sqrt(rng, seed, difficulty)
        if choice == "inf_lin":
            return _build_inf_sqrt_linear(rng, seed, difficulty)
        if choice == "inf_sqrt":
            return _build_inf_sqrt_sqrt(rng, seed, difficulty)
        if choice == "nested":
            return _build_inf_nested_sqrt(rng, seed, difficulty)
        return _build_inf_no_conj_trap(rng, seed, difficulty)

    return verified_generate(seed, _build)
