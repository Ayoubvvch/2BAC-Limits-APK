"""Generator for n-th root conjugates, algebraic root rules, and change of variable."""

from __future__ import annotations

import math
import random

import sympy as sp

from ..bricks import (
    _nonzero_int,
    make_nth_conj_brick,
    make_poly_brick,
    make_subst_root_brick,
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


def _build_nth_finite_conj(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    x0 = _nonzero_int(rng, -4, 4)
    if difficulty == Difficulty.BASIC:
        num = make_nth_conj_brick(rng, x0, n=3, two_roots=False)
        den = make_linear_brick(x0, coeff=1) if rng.random() < 0.5 else make_poly_brick(rng, x0, min_deg=1, max_deg=2, coeff_bound=3)
        if rng.random() < 0.25:
            num, den = den, num
        return _finish(seed, "nth_finite_basic", difficulty, num, den)

    if difficulty == Difficulty.INTERMEDIATE:
        n = rng.choice([3, 4])
        two_roots = rng.choice([False, True]) if n == 3 else False
        num = make_nth_conj_brick(rng, x0, n=n, two_roots=two_roots)
        den = make_poly_brick(rng, x0, min_deg=2, max_deg=3, coeff_bound=3)
        if rng.random() < 0.35:
            num, den = den, num
        return _finish(seed, f"nth_finite_order{n}", difficulty, num, den)

    # Challenge: double n-th root conjugate (deg 3 over deg 3 or deg 4)
    num = make_nth_conj_brick(rng, x0, n=rng.choice([3, 4]), two_roots=rng.choice([False, True]))
    den = make_nth_conj_brick(rng, x0, n=3, two_roots=False)
    return _finish(seed, "nth_double_conjugate", difficulty, num, den)


def _finish(seed: str, sub_pattern: str, difficulty: Difficulty, num, den) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    ex = assemble_vanishing_fraction(
        seed=seed,
        technique=Technique.NTH_ROOT,
        sub_pattern=sub_pattern,
        difficulty=difficulty,
        num=num,
        den=den,
    )
    return ex, num.expr / den.expr, sp.sympify(ex.answer_sympy_str)


def _build_nth_inf_cube(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Cube-root conjugate at +oo or -oo: root_3(a^3 x^3 + bx^2 + cx + d) - (ax + e)."""
    inf_kind = rng.choice(["+oo", "-oo"])
    point = LimitPoint(kind=inf_kind, side=ApproachSide.POS_INF if inf_kind == "+oo" else ApproachSide.NEG_INF)

    a = rng.choice([1, 2])
    b = _nonzero_int(rng, -6, 6)
    c = rng.randint(-5, 5)
    d = rng.randint(-5, 5)
    e = rng.randint(-2, 2) if difficulty != Difficulty.BASIC else 0

    p_expr = sp.Integer(a**3) * X**3 + sp.Integer(b) * X**2 + sp.Integer(c) * X + sp.Integer(d)
    lin_expr = sp.Integer(a) * X + sp.Integer(e)
    expr = RootN(p_expr, 3) - lin_expr

    rad_tex = root_latex(poly_latex(p_expr), 3)
    lin_tex = poly_latex(lin_expr)
    full_tex = signed_combo_latex(rad_tex, "-", lin_tex)
    lin_wrapped = wrap_parens(lin_tex)
    conj_tex = rf"\left({rad_tex}\right)^2 + {rad_tex} \cdot {lin_wrapped} + {lin_wrapped}^2"

    diff_poly = sp.expand(p_expr - lin_expr**3)
    expected = sp.Rational(b - 3 * a * a * e, 3 * a * a)

    steps = [
        SolutionStep(
            title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Forme indéterminée"),
            explanation=BilingualText(
                ar=f"عند ${point.point_latex()}$ نحصل على الشكل غير المحدد $\\infty - \\infty$.",
                fr=f"En ${point.point_latex()}$, on obtient la forme indéterminée $\\infty - \\infty$.",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} \left({full_tex}\right) \quad (\text{{FI }} \infty - \infty)",
        ),
        SolutionStep(
            title=BilingualText(ar="المرافق التكعيبي (من الرتبة 3)", fr="Expression conjuguée d'ordre 3"),
            explanation=BilingualText(
                ar="نستعمل المتطابقة $u - v = \\frac{u^3 - v^3}{u^2 + uv + v^2}$:",
                fr="On utilise l'identité $u - v = \\frac{u^3 - v^3}{u^2 + uv + v^2}$ :",
            ),
            latex=rf"{full_tex} = \frac{{{poly_latex(diff_poly)}}}{{{conj_tex}}}",
        ),
        SolutionStep(
            title=BilingualText(ar="التعميل بـ x² وحساب النهاية", fr="Factorisation par x² et calcul final"),
            explanation=BilingualText(
                ar="بما أن الجذر التكعيبي يوافق $ax$ في كلا اللانهايتين (دون قيمة مطلقة)، نُعمّل البسط والمقام بـ $x^2$:",
                fr="Comme la racine cubique se comporte comme $ax$ aux deux infinis, on factorise par $x^2$ :",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} \frac{{{poly_latex(diff_poly)}}}{{{conj_tex}}} = \frac{{{b - 3*a*a*e}}}{{3 \cdot {a*a}}} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.NTH_ROOT,
        sub_pattern="nth_inf_cube_conj",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo",
        hints=[
            BilingualText(
                ar="استعمل المرافق من الدرجة 3 ($u^3 - v^3$) ثم عمّل بـ $x^2$.",
                fr="Utilisez l'expression conjuguée d'ordre 3 ($u^3 - v^3$) puis factorisez par $x^2$.",
            )
        ],
        steps=steps,
        tags=["order_3", "inf_minus_inf"],
    )
    return ex, expr, expected


def _build_nth_subst(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Change of variable t = root_k(ax + b) reducing mixed-index roots to polynomials."""
    x0 = _nonzero_int(rng, -4, 4)
    k = rng.choice([4, 6])
    num = make_subst_root_brick(rng, x0, k=k)
    if difficulty == Difficulty.INTERMEDIATE:
        den = make_linear_brick(x0, coeff=1) if rng.random() < 0.5 else make_poly_brick(rng, x0, min_deg=1, max_deg=2, coeff_bound=2)
    else:
        den = make_poly_brick(rng, x0, min_deg=2, max_deg=2, coeff_bound=3)
    if rng.random() < 0.35:
        num, den = den, num
    return _finish(seed, f"nth_subst_order{k}", difficulty, num, den)


# Pre-verified nested radical recipes: (p, q, r, s) -> exponent (r*q + s)/(p*q)
_NESTED_RECIPES: list[tuple[int, int, int, int, sp.Rational]] = [
    (2, 3, 1, 1, sp.Rational(2, 3)),   # sqrt(x * root_3(x)) = x^(2/3)
    (2, 3, 1, 2, sp.Rational(5, 6)),   # sqrt(x * root_3(x^2)) = x^(5/6)
    (3, 2, 1, 1, sp.Rational(1, 2)),   # root_3(x * sqrt(x)) = x^(1/2)
    (3, 2, 2, 1, sp.Rational(5, 6)),   # root_3(x^2 * sqrt(x)) = x^(5/6)
    (2, 2, 1, 1, sp.Rational(3, 4)),   # sqrt(x * sqrt(x)) = x^(3/4)
    (2, 2, 1, 3, sp.Rational(5, 4)),   # sqrt(x * sqrt(x^3)) = x^(5/4)
    (3, 3, 1, 1, sp.Rational(4, 9)),   # root_3(x * root_3(x)) = x^(4/9)
    (2, 4, 1, 1, sp.Rational(5, 8)),   # sqrt(x * root_4(x)) = x^(5/8)
    (3, 2, 1, 3, sp.Rational(5, 6)),   # root_3(x * sqrt(x^3)) = x^(5/6)
]


def _render_nested_term(a: int, p: int, q: int, r: int, s: int, c_lower: int) -> tuple[sp.Expr, str]:
    """Build RootN(a*x^r * RootN(x^s, q) + c_lower, p) or with inner perturbation."""
    inner_x_s = X if s == 1 else X**s
    inner_q_tex = "x" if s == 1 else f"x^{{{s}}}"
    q_rad = RootN(inner_x_s, q)
    q_rad_tex = root_latex(inner_q_tex, q)

    x_r = X if r == 1 else X**r
    x_r_tex = "x" if r == 1 else f"x^{{{r}}}"
    a_prefix = "" if a == 1 else f"{a}"

    main_inside = sp.Integer(a) * x_r * q_rad
    main_inside_tex = f"{a_prefix}{x_r_tex}{q_rad_tex}"

    if c_lower != 0:
        inside = main_inside + sp.Integer(c_lower)
        inside_tex = signed_combo_latex(main_inside_tex, "+", str(c_lower))
    else:
        inside = main_inside
        inside_tex = main_inside_tex

    return RootN(inside, p), root_latex(inside_tex, p)


def _build_nth_nested_rules(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Algebraic rules of n-th roots: nested radicals at +oo forcing exponent unification."""
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    # Pick two distinct nested recipes that share the SAME effective exponent, OR different exponents!
    by_exp: dict[sp.Rational, list[tuple[int, int, int, int, sp.Rational]]] = {}
    for rec in _NESTED_RECIPES:
        by_exp.setdefault(rec[4], []).append(rec)

    same_order = rng.random() < 0.75
    if same_order:
        alpha = sp.Rational(5, 6)  # has 3 distinct nested recipes!
        r1, r2 = rng.sample(by_exp[alpha], 2)
    else:
        r1, r2 = rng.sample(_NESTED_RECIPES, 2)

    p1, q1, rr1, s1, exp1 = r1
    p2, q2, rr2, s2, exp2 = r2

    a1 = rng.choice([1, 2, 3, 4])
    a2 = rng.choice([1, 2, 3])
    c1 = rng.randint(1, 5)
    c2 = rng.randint(1, 5)

    num_rad, num_rad_tex = _render_nested_term(a1, p1, q1, rr1, s1, c1)
    den_rad, den_rad_tex = _render_nested_term(a2, p2, q2, rr2, s2, c2)

    # Optionally add a simpler lower-order root term like + k * sqrt[m](x)
    k_top = rng.randint(-3, 3) if difficulty != Difficulty.BASIC else 0
    if k_top != 0:
        # Ensure lower power < exp1
        low_idx = 3 if exp1 > sp.Rational(1, 3) else 4
        top_expr = num_rad + sp.Integer(k_top) * RootN(X, low_idx)
        low_tex = root_latex("x", low_idx)
        k_tex = low_tex if abs(k_top) == 1 else f"{abs(k_top)}{low_tex}"
        top_tex = f"{num_rad_tex} + {k_tex}" if k_top > 0 else f"{num_rad_tex} - {k_tex}"
    else:
        top_expr = num_rad
        top_tex = num_rad_tex

    full_expr = top_expr / den_rad
    full_tex = rf"\frac{{{top_tex}}}{{{den_rad_tex}}}"

    lead_num = sp.Pow(sp.Integer(a1), sp.Rational(1, p1))
    lead_den = sp.Pow(sp.Integer(a2), sp.Rational(1, p2))
    if exp1 == exp2:
        expected = sp.simplify(lead_num / lead_den)
    elif exp1 > exp2:
        expected = sp.oo
    else:
        expected = sp.Integer(0)

    steps = [
        SolutionStep(
            title=BilingualText(
                ar="تحويل الجذور المتداخلة باستعمال القواعد الجبرية",
                fr="Transformation des radicaux imbriqués par les règles des puissances",
            ),
            explanation=BilingualText(
                ar=r"نستعمل خاصيتي $\sqrt[p]{\sqrt[q]{x}} = \sqrt[pq]{x}$ و $\sqrt[p]{x^r} = x^{r/p}$ لتحديد الأس المكافئ لكل حد عند $+\infty$:",
                fr=r"On utilise $\sqrt[p]{\sqrt[q]{x}} = \sqrt[pq]{x}$ et $\sqrt[p]{x^r} = x^{r/p}$ pour déterminer la puissance équivalente de chaque terme en $+\infty$ :",
            ),
            latex=(
                rf"{num_rad_tex} \sim {value_latex(lead_num)}\, x^{{{sp.latex(exp1)}}}, \quad "
                rf"{den_rad_tex} \sim {value_latex(lead_den)}\, x^{{{sp.latex(exp2)}}}"
            ),
        ),
        SolutionStep(
            title=BilingualText(ar="التعميل بالأس الأكبر والاختزال", fr="Factorisation par la plus grande puissance"),
            explanation=BilingualText(
                ar="بالتعميل بالحد الأكبر أساً في البسط والمقام ثم الاختزال عند $+\\infty$:",
                fr="En factorisant numérateur et dénominateur par les termes dominants puis en simplifiant en $+\\infty$ :",
            ),
            latex=rf"\lim_{{x \to +\infty}} {full_tex} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.NTH_ROOT,
        sub_pattern="nth_nested_algebra",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo/oo",
        hints=[
            BilingualText(
                ar=r"حوّل الجذور المتداخلة إلى قوى كسرية باستعمال $\sqrt[p]{x^r\sqrt[q]{x^s}} = x^{\frac{rq+s}{pq}}$.",
                fr=r"Convertissez les radicaux imbriqués en puissances rationnelles via $\sqrt[p]{x^r\sqrt[q]{x^s}} = x^{\frac{rq+s}{pq}}$.",
            )
        ],
        steps=steps,
        tags=["nested_roots", "algebraic_rules"],
    )
    return ex, full_expr, expected


def generate_nth_root(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a verified n-th root limit exercise."""

    def _build(rng: random.Random):
        if difficulty == Difficulty.BASIC:
            choice = rng.choices(["finite", "nested"], weights=[0.7, 0.3])[0]
        elif difficulty == Difficulty.INTERMEDIATE:
            choice = rng.choices(["finite", "inf_cube", "subst", "nested"], weights=[0.35, 0.25, 0.2, 0.2])[0]
        else:
            choice = rng.choices(["finite", "inf_cube", "subst", "nested"], weights=[0.25, 0.25, 0.25, 0.25])[0]

        if choice == "finite":
            return _build_nth_finite_conj(rng, seed, difficulty)
        if choice == "inf_cube":
            return _build_nth_inf_cube(rng, seed, difficulty)
        if choice == "subst":
            return _build_nth_subst(rng, seed, difficulty)
        return _build_nth_nested_rules(rng, seed, difficulty)

    return verified_generate(seed, _build)
