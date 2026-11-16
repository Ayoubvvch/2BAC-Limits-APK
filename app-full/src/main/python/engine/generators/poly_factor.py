"""Generator for polynomial & rational limits (factoring, oo/oo, one-sided k/0)."""

from __future__ import annotations

import random

import sympy as sp

from ..bricks import _nonzero_int, _random_poly_nonzero_at, make_poly_brick
from ..formatting import (
    X,
    limit_statement_latex,
    linear_factor_latex,
    poly_latex,
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


def _build_poly_0_0(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    x0 = _nonzero_int(rng, -5, 5)
    if difficulty == Difficulty.BASIC:
        num = make_poly_brick(rng, x0, min_deg=2, max_deg=2, coeff_bound=4)
        den = make_linear_brick(x0, coeff=1) if rng.random() < 0.4 else make_poly_brick(rng, x0, min_deg=1, max_deg=2, coeff_bound=3)
        return _assemble_and_return(seed, "poly_0_0", difficulty, num, den)

    if difficulty == Difficulty.INTERMEDIATE:
        num = make_poly_brick(rng, x0, min_deg=3, max_deg=3, coeff_bound=4)
        den = make_poly_brick(rng, x0, min_deg=2, max_deg=3, coeff_bound=3)
        return _assemble_and_return(seed, "poly_0_0_cubic", difficulty, num, den)

    # Challenge: either double root (x - x0)^2 or reciprocal rational difference
    if rng.random() < 0.5:
        q_num = _random_poly_nonzero_at(rng, x0, rng.randint(0, 1), coeff_bound=4)
        q_den = _random_poly_nonzero_at(rng, x0, rng.randint(0, 1), coeff_bound=4)
        num_expr = sp.expand((X - sp.Integer(x0)) ** 2 * q_num)
        den_expr = sp.expand((X - sp.Integer(x0)) ** 2 * q_den)
        point = LimitPoint(kind="finite", value=x0, side=ApproachSide.BOTH)
        num_tex, den_tex = poly_latex(num_expr), poly_latex(den_expr)
        full_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify(q_num.subs(X, x0) / q_den.subs(X, x0))
        lin_tex = linear_factor_latex("x", x0)

        steps = [
            SolutionStep(
                title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Identification de la forme indéterminée"),
                explanation=BilingualText(
                    ar=f"بتعويض $x={x0}$ نجد $\\frac{{0}}{{0}}$.",
                    fr=f"La substitution en $x={x0}$ donne la forme indéterminée $\\frac{{0}}{{0}}$.",
                ),
                latex=rf"\lim_{{x \to {x0}}} {full_tex} \quad \left(\text{{FI }} \frac{{0}}{{0}}\right)",
            ),
            SolutionStep(
                title=BilingualText(ar="جذر مزدوج (الاختزال مرتين)", fr="Racine double (double factorisation)"),
                explanation=BilingualText(
                    ar=f"بعد القسمة الأولى على $({lin_tex})$ تبقى الحالة $\\frac{{0}}{{0}}$، لذا نُعمّل بـ $({lin_tex})^2$:",
                    fr=f"Après une première division par $({lin_tex})$, on retrouve $\\frac{{0}}{{0}}$ ; on factorise donc par $({lin_tex})^2$ :",
                ),
                latex=rf"{full_tex} = \frac{{({lin_tex})^2({poly_latex(q_num)})}}{{({lin_tex})^2({poly_latex(q_den)})}} = \frac{{{poly_latex(q_num)}}}{{{poly_latex(q_den)}}}",
            ),
            SolutionStep(
                title=BilingualText(ar="المرور إلى النهاية", fr="Passage à la limite"),
                explanation=BilingualText(
                    ar=f"نعوّض $x={x0}$ في الكسر المختزل:",
                    fr=f"On remplace $x={x0}$ dans l'expression simplifiée :",
                ),
                latex=rf"\lim_{{x \to {x0}}} {full_tex} = {value_latex(expected)}",
            ),
        ]

        ex = GeneratedExercise(
            seed=seed,
            technique=Technique.POLY_FACTOR,
            sub_pattern="poly_double_root",
            difficulty=difficulty,
            point=point,
            expr_latex=full_tex,
            full_limit_latex=limit_statement_latex(full_tex, point),
            answer_latex=value_latex(expected),
            answer_sympy_str=str(expected),
            indeterminate_form="0/0",
            hints=[
                BilingualText(
                    ar=f"العدد ${x0}$ هو جذر مزدوج للبسط والمقام؛ عمّل بـ $({lin_tex})^2$.",
                    fr=f"${x0}$ est une racine double ; factorisez numérateur et dénominateur par $({lin_tex})^2$.",
                )
            ],
            steps=steps,
            tags=["double_root"],
        )
        return ex, num_expr / den_expr, expected

    # Reciprocal rational difference: a/P1(x) - b/P2(x) where P1(x0)=P2(x0)=0
    # Actually: c1/(x - x0) - c2/(Q(x)*(x - x0)) -> common denominator leaves (c1*Q(x) - c2) / ((x-x0)*Q(x))
    # Build Q(x) such that c1*Q(x0) - c2 == 0 -> i.e. c1*Q(x) - c2 = (x - x0)*H(x)
    q0 = _nonzero_int(rng, -4, 4)
    c1 = rng.choice([1, 2, 3])
    c2 = c1 * q0
    h_expr = _random_poly_nonzero_at(rng, x0, rng.choice([0, 1]), coeff_bound=3)
    # Q(x) = ((x - x0)*H(x) + c2) / c1 -> choose H(x) = c1 * H0(x) so Q has integer coeffs!
    q_expr = sp.expand((X - sp.Integer(x0)) * h_expr + sp.Integer(q0))
    den1 = X - sp.Integer(x0)
    den2 = sp.expand(den1 * q_expr)
    full_expr = sp.Integer(c1) / den1 - sp.Integer(c2) / den2
    full_tex = rf"\frac{{{c1}}}{{{poly_latex(den1)}}} - \frac{{{c2}}}{{{poly_latex(den2)}}}"
    point = LimitPoint(kind="finite", value=x0, side=ApproachSide.BOTH)
    expected = sp.simplify((sp.Integer(c1) * h_expr.subs(X, x0)) / q0)
    lin_tex = linear_factor_latex("x", x0)

    steps = [
        SolutionStep(
            title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Forme indéterminée"),
            explanation=BilingualText(
                ar=f"عند $x \\to {x0}$ يؤول كل من الحدين إلى $\\pm\\infty$، فنحصل على شكل غير محدد $\\infty - \\infty$.",
                fr=f"En $x \\to {x0}$, on obtient une forme indéterminée de type $\\infty - \\infty$.",
            ),
            latex=rf"\lim_{{x \to {x0}}} \left({full_tex}\right) \quad (\text{{FI }} \infty - \infty)",
        ),
        SolutionStep(
            title=BilingualText(ar="توحيد المقامات والتعميل", fr="Réduction au même dénominateur"),
            explanation=BilingualText(
                ar=f"بملاحظة أن ${poly_latex(den2)} = ({lin_tex})({poly_latex(q_expr)})$، نوحّد المقامات ونختزل بـ $({lin_tex})$:",
                fr=f"Comme ${poly_latex(den2)} = ({lin_tex})({poly_latex(q_expr)})$, on réduit au même dénominateur puis on simplifie par $({lin_tex})$ :",
            ),
            latex=rf"\frac{{{c1}\left({poly_latex(q_expr)}\right) - {wrap_parens(str(c2))}}}{{({lin_tex})({poly_latex(q_expr)})}} = \frac{{({lin_tex})({poly_latex(sp.Integer(c1) * h_expr)})}}{{({lin_tex})({poly_latex(q_expr)})}} = \frac{{{poly_latex(sp.Integer(c1) * h_expr)}}}{{{poly_latex(q_expr)}}}",
        ),
        SolutionStep(
            title=BilingualText(ar="النتيجة النهائية", fr="Calcul de la limite"),
            explanation=BilingualText(
                ar=f"بالتعويض بـ $x={x0}$ نجد:",
                fr=f"Par substitution en $x={x0}$ :",
            ),
            latex=rf"\lim_{{x \to {x0}}} \left({full_tex}\right) = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.POLY_FACTOR,
        sub_pattern="rational_common_denom",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo",
        hints=[
            BilingualText(
                ar=f"حلّل المقام الثاني ووحّد المقامات لتختزل العامل $({lin_tex})$.",
                fr=f"Factorisez le second dénominateur et réduisez au même dénominateur pour simplifier par $({lin_tex})$.",
            )
        ],
        steps=steps,
        tags=["common_denominator"],
    )
    return ex, full_expr, expected


def _assemble_and_return(
    seed: str,
    sub_pattern: str,
    difficulty: Difficulty,
    num,
    den,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    ex = assemble_vanishing_fraction(
        seed=seed,
        technique=Technique.POLY_FACTOR,
        sub_pattern=sub_pattern,
        difficulty=difficulty,
        num=num,
        den=den,
    )
    return ex, num.expr / den.expr, sp.sympify(ex.answer_sympy_str)


def _build_poly_inf(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    inf_kind = rng.choice(["+oo", "-oo"])
    point = LimitPoint(kind=inf_kind, side=ApproachSide.POS_INF if inf_kind == "+oo" else ApproachSide.NEG_INF)

    if difficulty == Difficulty.BASIC:
        deg_n = rng.choice([2, 3])
        deg_d = deg_n
    else:
        mode = rng.choice(["equal", "top_heavy", "bottom_heavy"])
        if mode == "equal":
            deg_n = deg_d = rng.choice([2, 3, 4])
        elif mode == "top_heavy":
            deg_n = rng.choice([3, 4])
            deg_d = deg_n - rng.choice([1, 2])
        else:
            deg_d = rng.choice([3, 4])
            deg_n = deg_d - rng.choice([1, 2])

    def _rand_poly(d: int) -> tuple[sp.Expr, int]:
        lead = _nonzero_int(rng, -5, 5)
        expr = sp.Integer(lead) * X**d
        for k in range(d):
            expr += sp.Integer(rng.randint(-6, 6)) * X**k
        return sp.expand(expr), lead

    p_expr, a_n = _rand_poly(deg_n)
    q_expr, b_d = _rand_poly(deg_d)
    p_tex, q_tex = poly_latex(p_expr), poly_latex(q_expr)
    full_tex = rf"\frac{{{p_tex}}}{{{q_tex}}}"
    full_expr = p_expr / q_expr

    if deg_n == deg_d:
        expected = sp.Rational(a_n, b_d)
        ratio_tex = rf"\frac{{{a_n}x^{{{deg_n}}}}}{{{b_d}x^{{{deg_d}}}}}"
    elif deg_n < deg_d:
        expected = sp.Integer(0)
        ratio_tex = rf"\frac{{{a_n}x^{{{deg_n}}}}}{{{b_d}x^{{{deg_d}}}}} = \frac{{{a_n}}}{{{b_d}x^{{{deg_d - deg_n}}}}}"
    else:
        diff = deg_n - deg_d
        sign_coeff = 1 if (a_n * b_d > 0) else -1
        sign_x = 1 if (inf_kind == "+oo" or diff % 2 == 0) else -1
        expected = sp.oo if (sign_coeff * sign_x > 0) else -sp.oo
        ratio_tex = rf"\frac{{{a_n}x^{{{deg_n}}}}}{{{b_d}x^{{{deg_d}}}}} = {poly_latex(sp.Rational(a_n, b_d) * X**diff)}"

    steps = [
        SolutionStep(
            title=BilingualText(ar="قاعدة الحد الأكبر درجة", fr="Règle des termes de plus haut degré"),
            explanation=BilingualText(
                ar=f"عند ${point.point_latex()}$ نحصل على الشكل غير المحدد $\\frac{{\\infty}}{{\\infty}}$. بما أن الدالة جذرية، نهايتها هي نهاية خارج الحدين الأكبر درجة:",
                fr=f"En ${point.point_latex()}$, on obtient une forme indéterminée $\\frac{{\\infty}}{{\\infty}}$. La limite d'une fraction rationnelle est égale à celle du quotient de ses termes de plus haut degré :",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} {full_tex} = \lim_{{x \to {point.point_latex()}}} {ratio_tex} = {value_latex(expected)}",
        )
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.POLY_FACTOR,
        sub_pattern="poly_inf_inf",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo/oo",
        hints=[
            BilingualText(
                ar="الدالة حدودية كسرية عند اللانهاية: خذ خارج الحدين ذوي أكبر أس (أو عمّل بأكبر أس).",
                fr="Fraction rationnelle à l'infini : considérez le quotient des monômes de plus haut degré.",
            )
        ],
        steps=steps,
        tags=["highest_degree"],
    )
    return ex, full_expr, expected


def _build_one_sided(rng: random.Random, seed: str, difficulty: Difficulty) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    x0 = _nonzero_int(rng, -4, 4)
    side = rng.choice([ApproachSide.RIGHT, ApproachSide.LEFT])
    point = LimitPoint(kind="finite", value=x0, side=side)

    p_expr = _random_poly_nonzero_at(rng, x0, rng.choice([1, 2]), coeff_bound=4)
    r_expr = _random_poly_nonzero_at(rng, x0, rng.choice([0, 1]), coeff_bound=3)
    q_expr = sp.expand((X - sp.Integer(x0)) * r_expr)

    p0 = int(p_expr.subs(X, x0))
    r0 = int(r_expr.subs(X, x0))
    lin_sign = 1 if side == ApproachSide.RIGHT else -1
    den_zero_sign = lin_sign * (1 if r0 > 0 else -1)
    total_sign = (1 if p0 > 0 else -1) * den_zero_sign
    expected = sp.oo if total_sign > 0 else -sp.oo

    p_tex, q_tex = poly_latex(p_expr), poly_latex(q_expr)
    full_tex = rf"\frac{{{p_tex}}}{{{q_tex}}}"
    zero_tex = "0^+" if den_zero_sign > 0 else "0^-"
    lin_tex = linear_factor_latex("x", x0)

    steps = [
        SolutionStep(
            title=BilingualText(ar="حساب نهاية البسط والمقام", fr="Limites du numérateur et du dénominateur"),
            explanation=BilingualText(
                ar=f"عند $x \\to {point.point_latex()}$: البسط يؤول إلى ${p0}$ والمقام ينعدم عند ${x0}$، إذن النهاية هي $\\pm\\infty$ حسب إشارة المقام.",
                fr=f"En $x \\to {point.point_latex()}$ : le numérateur tend vers ${p0}$ et le dénominateur s'annule ; il faut étudier le signe du dénominateur.",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} ({p_tex}) = {p0}, \quad {q_tex} = ({lin_tex})({poly_latex(r_expr)}) \to {zero_tex}",
        ),
        SolutionStep(
            title=BilingualText(ar="تحديد الإشارة النهائية", fr="Détermination du signe final"),
            explanation=BilingualText(
                ar=f"بما أن البسط يؤول إلى ${p0}$ والمقام يؤول إلى ${zero_tex}$:",
                fr=f"Comme le numérateur tend vers ${p0}$ et le dénominateur vers ${zero_tex}$ :",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} {full_tex} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.POLY_FACTOR,
        sub_pattern="poly_one_sided_inf",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form=f"{p0}/0",
        hints=[
            BilingualText(
                ar=f"هذا ليس شكلاً غير محدد ($k/0$)؛ ادرس إشارة المقام على {'يمين' if side == ApproachSide.RIGHT else 'يسار'} ${x0}$.",
                fr=f"Ce n'est pas une forme indéterminée ($k/0$) ; étudiez le signe du dénominateur à {'droite' if side == ApproachSide.RIGHT else 'gauche'} de ${x0}$.",
            )
        ],
        steps=steps,
        tags=["one_sided", "sign_table"],
    )
    return ex, p_expr / q_expr, expected


def generate_poly_factor(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a verified polynomial/rational limit exercise."""

    def _build(rng: random.Random):
        if difficulty == Difficulty.BASIC:
            choice = rng.choices(["0_0", "inf"], weights=[0.6, 0.4])[0]
        elif difficulty == Difficulty.INTERMEDIATE:
            choice = rng.choices(["0_0", "inf", "one_sided"], weights=[0.5, 0.25, 0.25])[0]
        else:
            choice = rng.choices(["0_0", "one_sided"], weights=[0.75, 0.25])[0]

        if choice == "0_0":
            return _build_poly_0_0(rng, seed, difficulty)
        if choice == "inf":
            return _build_poly_inf(rng, seed, difficulty)
        return _build_one_sided(rng, seed, difficulty)

    return verified_generate(seed, _build)
