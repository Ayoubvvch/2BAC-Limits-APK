"""Procedural generator for 2BAC PC trigonometric limits (sin, cos, tan)."""

from __future__ import annotations

import random

import sympy as sp

from ..bricks import _nonzero_int
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
from .common import verified_generate


def _coeff_var_tex(c: int, unit_tex: str, first: bool = True) -> str:
    """Format c * unit_tex cleanly without '+ -' or redundant '1*'."""
    if c == 1:
        return unit_tex if first else f" + {unit_tex}"
    if c == -1:
        return f"-{unit_tex}" if first else f" - {unit_tex}"
    if c > 0:
        return f"{c}{unit_tex}" if first else f" + {c}{unit_tex}"
    return f"-{-c}{unit_tex}" if first else f" - {-c}{unit_tex}"


def _lin_arg_tex(a: int, var_name: str = "x") -> str:
    if a == 1:
        return var_name
    if a == -1:
        return f"-{var_name}"
    return f"{a}{var_name}"


def _pi_mult_tex(k: int, var_name: str = "x") -> str:
    """Format k * pi * x cleanly (k >= 1)."""
    if k == 1:
        return rf"\pi {var_name}" if var_name else r"\pi"
    if var_name:
        return rf"{k}\pi {var_name}"
    return rf"{k}\pi"


def _trig_term(fn_name: str, a: int) -> tuple[sp.Expr, str, sp.Expr]:
    """Return (expr, latex, slope_at_0) for sin(ax) or tan(ax) or x."""
    if fn_name == "lin":
        return sp.Integer(a) * X, _lin_arg_tex(a), sp.Integer(a)
    sym_fn = sp.sin if fn_name == "sin" else sp.tan
    return sym_fn(sp.Integer(a) * X), rf"\{fn_name}({_lin_arg_tex(a)})", sp.Integer(a)


def _signed_sum_latex(terms: list[tuple[int, str]]) -> str:
    """Build cleanly spaced sum of (coeff, unit_tex) pairs without '+ -'."""
    parts: list[str] = []
    for c, u in terms:
        if c == 0:
            continue
        parts.append(_coeff_var_tex(c, u, first=(len(parts) == 0)))
    return "".join(parts) if parts else "0"


def _nonvanishing_cos_factor(rng: random.Random) -> tuple[sp.Expr, str, sp.Expr]:
    """Build (c + d*cos(m*x)) where c + d != 0 as a non-zero multiplier at x=0."""
    m = rng.randint(1, 5)
    while True:
        c = rng.choice([1, 2, 3])
        d = rng.choice([-2, -1, 1, 2])
        if c + d != 0:
            break
    expr = sp.Integer(c) + sp.Integer(d) * sp.cos(sp.Integer(m) * X)
    cos_tex = rf"\cos({_lin_arg_tex(m)})"
    if d == 1:
        tex = rf"({c} + {cos_tex})"
    elif d == -1:
        tex = rf"({c} - {cos_tex})"
    elif d > 0:
        tex = rf"({c} + {d}{cos_tex})"
    else:
        tex = rf"({c} - {-d}{cos_tex})"
    return expr, tex, sp.Integer(c + d)


def _finalize_trig_exercise(
    seed: str,
    sub_pattern: str,
    difficulty: Difficulty,
    point: LimitPoint,
    full_expr: sp.Expr,
    expr_tex: str,
    expected: sp.Expr,
    indeterminate_form: str,
    hints: list[BilingualText],
    steps: list[SolutionStep],
    tags: list[str],
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.TRIG,
        sub_pattern=sub_pattern,
        difficulty=difficulty,
        point=point,
        expr_latex=expr_tex,
        full_limit_latex=limit_statement_latex(expr_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(sp.simplify(expected)),
        indeterminate_form=indeterminate_form,
        hints=hints,
        steps=steps,
        tags=tags,
    )
    return ex, full_expr, sp.simplify(expected)


# ---------------------------------------------------------------------------
# Level 1 — BASIC: Fundamental usual trig limits at x -> 0
# ---------------------------------------------------------------------------


def _build_basic_sin_tan_ratio(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    while True:
        k1 = rng.choice(["sin", "tan", "lin"])
        k2 = rng.choice(["sin", "tan", "lin"])
        if k1 != "lin" or k2 != "lin":
            break

    a = _nonzero_int(rng, -9, 9)
    b = _nonzero_int(rng, -9, 9)
    c_num = rng.choice([1, 1, 1, 2, 3, -1, -2]) if k1 != "lin" else 1
    c_den = rng.choice([1, 1, 1, 2, 3]) if k2 != "lin" else 1

    e1, t1, s1 = _trig_term(k1, a)
    e2, t2, s2 = _trig_term(k2, b)

    num_e = sp.Integer(c_num) * e1
    den_e = sp.Integer(c_den) * e2
    num_t = _coeff_var_tex(c_num, t1, True)
    den_t = _coeff_var_tex(c_den, t2, True)

    slope_num = sp.Integer(c_num) * s1
    slope_den = sp.Integer(c_den) * s2

    extra_desc_ar = ""
    extra_desc_fr = ""
    if rng.random() < 0.35:
        w_e, w_t, w_v = _nonvanishing_cos_factor(rng)
        if rng.random() < 0.5:
            num_e = num_e * w_e
            num_t = rf"{num_t}\,{w_t}"
            slope_num *= w_v
        else:
            den_e = den_e * w_e
            den_t = rf"{den_t}\,{w_t}"
            slope_den *= w_v
        extra_desc_ar = rf" مع تعويض $\cos(0)=1$ في العامل ${w_t}={value_latex(w_v)}$"
        extra_desc_fr = rf" avec $\cos(0)=1$ dans le facteur ${w_t}={value_latex(w_v)}$"

    full_expr = num_e / den_e
    expr_tex = rf"\frac{{{num_t}}}{{{den_t}}}"
    expected = sp.simplify(slope_num / slope_den)
    point = LimitPoint(kind="finite", value=0, side=ApproachSide.BOTH)

    hints = [
        BilingualText(
            ar=r"بالتعويض المباشر في $0$ نجد $\frac{0}{0}$. فكّر في القسمة على $x$ لاستعمال $\lim_{x\to 0}\frac{\sin(ax)}{ax}=1$ و $\lim_{x\to 0}\frac{\tan(ax)}{ax}=1$.",
            fr=r"La substitution directe en $0$ donne $\frac{0}{0}$. Divisez numérateur et dénominateur par $x$ pour faire apparaître $\frac{\sin(ax)}{ax}$ ou $\frac{\tan(ax)}{ax}$.",
        ),
        BilingualText(
            ar=rf"تذكّر أن $\lim_{{x\to 0}}\frac{{\sin(ax)}}{{x}} = a$ و $\lim_{{x\to 0}}\frac{{\tan(ax)}}{{x}} = a${extra_desc_ar}.",
            fr=rf"Rappel : $\lim_{{x\to 0}}\frac{{\sin(ax)}}{{x}} = a$ et $\lim_{{x\to 0}}\frac{{\tan(ax)}}{{x}} = a${extra_desc_fr}.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="التعويض المباشر", fr="Forme indéterminée"),
            explanation=BilingualText(
                ar=r"بما أن $\sin(0)=0$ و $\tan(0)=0$، نحصل بالتعويض على الشكل غير المحدد $\frac{0}{0}$.",
                fr=r"Comme $\sin(0)=0$ et $\tan(0)=0$, on obtient la forme indéterminée $\frac{0}{0}$.",
            ),
            latex=rf"\lim_{{x \to 0}} {expr_tex} \quad \left(\text{{FI }} \frac{{0}}{{0}}\right)",
        ),
        SolutionStep(
            title=BilingualText(ar="إظهار النهايات المثلثية الاعتيادية", fr="Apparition des limites usuelles"),
            explanation=BilingualText(
                ar=rf"بقسمة البسط والمقام على $x$ (أو بتعويض $\frac{{\{k1 if k1!='lin' else k2}(ax)}}{{x}} \to a$) نحسب نهاية كل عامل:",
                fr=r"En divisant le numérateur et le dénominateur par $x$ puis en passant à la limite en $0$ :",
            ),
            latex=rf"\lim_{{x \to 0}} {expr_tex} = \frac{{{value_latex(slope_num)}}}{{{value_latex(slope_den)}}} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, f"trig_basic_{k1}_{k2}", Difficulty.BASIC, point,
        full_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", k1 if k1 != "lin" else k2],
    )


def _build_basic_one_minus_cos(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    a = rng.randint(1, 9)
    b = _nonzero_int(rng, -8, 8)
    negate_top = rng.choice([False, True])
    invert = rng.choice([False, False, True])

    a_tex = _lin_arg_tex(a)
    if negate_top:
        cos_part_e = sp.cos(sp.Integer(a) * X) - 1
        cos_part_t = rf"\cos({a_tex}) - 1"
        cos_coeff = -sp.Rational(a * a, 2)
    else:
        cos_part_e = 1 - sp.cos(sp.Integer(a) * X)
        cos_part_t = rf"1 - \cos({a_tex})"
        cos_coeff = sp.Rational(a * a, 2)

    poly_part_e = sp.Integer(b) * (X**2)
    poly_part_t = _coeff_var_tex(b, "x^{2}", True)

    if invert:
        full_expr = poly_part_e / cos_part_e
        expr_tex = rf"\frac{{{poly_part_t}}}{{{cos_part_t}}}"
        expected = sp.simplify(sp.Integer(b) / cos_coeff)
    else:
        full_expr = cos_part_e / poly_part_e
        expr_tex = rf"\frac{{{cos_part_t}}}{{{poly_part_t}}}"
        expected = sp.simplify(cos_coeff / sp.Integer(b))

    point = LimitPoint(kind="finite", value=0, side=ApproachSide.BOTH)
    hints = [
        BilingualText(
            ar=r"استعمل النهاية الاعتيادية $\lim_{t \to 0} \frac{1 - \cos(t)}{t^2} = \frac{1}{2}$.",
            fr=r"Utilisez la limite usuelle $\lim_{t \to 0} \frac{1 - \cos(t)}{t^2} = \frac{1}{2}$.",
        ),
        BilingualText(
            ar=rf"اضرب واقسم على $({a_tex})^2 = {a*a}x^2$ لإبراز $\frac{{1 - \cos({a_tex})}}{{({a_tex})^2}}$.",
            fr=rf"Faites apparaître $\frac{{1 - \cos({a_tex})}}{{({a_tex})^2}}$ avec $({a_tex})^2 = {a*a}x^2$.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Forme indéterminée"),
            explanation=BilingualText(
                ar=r"عند $x = 0$ لدينا $\cos(0)=1$، فنحصل على الشكل غير المحدد $\frac{0}{0}$.",
                fr=r"En $x = 0$, $\cos(0)=1$, donc on obtient la forme indéterminée $\frac{0}{0}$.",
            ),
            latex=rf"\lim_{{x \to 0}} {expr_tex} \quad \left(\text{{FI }} \frac{{0}}{{0}}\right)",
        ),
        SolutionStep(
            title=BilingualText(
                ar=r"تطبيق النهاية الاعتيادية $\frac{1-\cos(ax)}{(ax)^2}$",
                fr=r"Utilisation de la limite usuelle en $1 - \cos(ax)$",
            ),
            explanation=BilingualText(
                ar=rf"نعلم أن $\lim_{{x\to 0}} \frac{{{cos_part_t}}}{{x^2}} = {'-' if negate_top else ''}\frac{{{a}^2}}{{2}} = {value_latex(cos_coeff)}$.",
                fr=rf"On sait que $\lim_{{x\to 0}} \frac{{{cos_part_t}}}{{x^2}} = {value_latex(cos_coeff)}$.",
            ),
            latex=rf"\lim_{{x \to 0}} {expr_tex} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, "trig_basic_one_minus_cos", Difficulty.BASIC, point,
        full_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", "cos", "order_2"],
    )


def _build_basic_poly_trig_sum(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    p = rng.randint(-5, 5)
    q = _nonzero_int(rng, -4, 4)
    fn_num = rng.choice(["sin", "tan"])
    a = _nonzero_int(rng, 1, 7)

    while True:
        r = rng.randint(-5, 5)
        s = _nonzero_int(rng, -4, 4)
        fn_den = rng.choice(["sin", "tan"])
        b = _nonzero_int(rng, 1, 7)
        den_slope = r + s * b
        if den_slope != 0 and (p != 0 or r != 0):
            break

    e_n_trig, t_n_trig, _ = _trig_term(fn_num, a)
    e_d_trig, t_d_trig, _ = _trig_term(fn_den, b)

    num_expr = sp.Integer(p) * X + sp.Integer(q) * e_n_trig
    den_expr = sp.Integer(r) * X + sp.Integer(s) * e_d_trig
    num_tex = _signed_sum_latex([(p, "x"), (q, t_n_trig)])
    den_tex = _signed_sum_latex([(r, "x"), (s, t_d_trig)])

    num_slope = sp.Integer(p + q * a)
    expected = sp.Rational(p + q * a, den_slope)

    full_expr = num_expr / den_expr
    expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
    point = LimitPoint(kind="finite", value=0, side=ApproachSide.BOTH)

    hints = [
        BilingualText(
            ar=r"اقسم البسط والمقام معاً على $x$ (لأن $x \neq 0$ بجوار $0$).",
            fr=r"Divisez le numérateur et le dénominateur par $x$ (au voisinage de $0$, $x \neq 0$).",
        ),
        BilingualText(
            ar=rf"بسطاً نجد ${p} + {q}\cdot\frac{{{t_n_trig}}}{{x}} \to {p + q*a}$، ومقاماً نجد ${r} + {s}\cdot\frac{{{t_d_trig}}}{{x}} \to {den_slope}$.",
            fr=rf"Au numérateur : ${p} + {q}\cdot\frac{{{t_n_trig}}}{{x}} \to {p + q*a}$, et au dénominateur : ${r} + {s}\cdot\frac{{{t_d_trig}}}{{x}} \to {den_slope}$.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="القسمة على x", fr="Factorisation et division par x"),
            explanation=BilingualText(
                ar=r"بالتعويض المباشر نجد $\frac{0}{0}$. نقسم كلاً من البسط والمقام على $x$:",
                fr=r"La substitution en $0$ donne $\frac{0}{0}$. On divise numérateur et dénominateur par $x$ :",
            ),
            latex=rf"{expr_tex} = \frac{{\frac{{{num_tex}}}{{x}}}}{{\frac{{{den_tex}}}{{x}}}}",
        ),
        SolutionStep(
            title=BilingualText(ar="المرور إلى النهاية", fr="Passage à la limite"),
            explanation=BilingualText(
                ar=r"باستعمال النهايات الاعتيادية عند $0$:",
                fr=r"À l'aide des limites usuelles en $0$ :",
            ),
            latex=rf"\lim_{{x \to 0}} {expr_tex} = \frac{{{value_latex(num_slope)}}}{{{den_slope}}} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, "trig_basic_sum_over_sum", Difficulty.BASIC, point,
        full_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", "linear_division"],
    )


# ---------------------------------------------------------------------------
# Level 2 — INTERMEDIATE: Order-2 combinations, Squeeze Theorem, Shifts
# ---------------------------------------------------------------------------


def _order2_block(rng: random.Random, family: str) -> tuple[sp.Expr, str, sp.Expr, str, str]:
    """Build an order-2 vanishing expression at x=0: (expr, tex, c2, hint_ar, hint_fr)."""
    if family == "1_minus_cos":
        a = rng.randint(1, 7)
        t = _lin_arg_tex(a)
        return (
            1 - sp.cos(sp.Integer(a) * X),
            rf"1 - \cos({t})",
            sp.Rational(a * a, 2),
            rf"\frac{{1 - \cos({t})}}{{x^2}} \to \frac{{{a*a}}}{{2}}",
            rf"\frac{{1 - \cos({t})}}{{x^2}} \to \frac{{{a*a}}}{{2}}",
        )
    if family == "cos_minus_cos":
        a = rng.randint(1, 7)
        b = rng.choice([v for v in range(1, 8) if v != a])
        ta, tb = _lin_arg_tex(a), _lin_arg_tex(b)
        return (
            sp.cos(sp.Integer(a) * X) - sp.cos(sp.Integer(b) * X),
            rf"\cos({ta}) - \cos({tb})",
            sp.Rational(b * b - a * a, 2),
            rf"\text{{اكتب }} \cos({ta}) - \cos({tb}) = (1 - \cos({tb})) - (1 - \cos({ta}))",
            rf"\text{{Écrire }} \cos({ta}) - \cos({tb}) = (1 - \cos({tb})) - (1 - \cos({ta}))",
        )
    if family == "sin_sin":
        a = _nonzero_int(rng, -5, 5)
        b = _nonzero_int(rng, 1, 5)
        return (
            sp.sin(sp.Integer(a) * X) * sp.sin(sp.Integer(b) * X),
            rf"\sin({_lin_arg_tex(a)})\,\sin({_lin_arg_tex(b)})",
            sp.Integer(a * b),
            rf"\frac{{\sin({_lin_arg_tex(a)})}}{{x}}\cdot\frac{{\sin({_lin_arg_tex(b)})}}{{x}} \to {a*b}",
            rf"\frac{{\sin({_lin_arg_tex(a)})}}{{x}}\cdot\frac{{\sin({_lin_arg_tex(b)})}}{{x}} \to {a*b}",
        )
    if family == "sin_tan":
        a = _nonzero_int(rng, -5, 5)
        b = _nonzero_int(rng, 1, 5)
        return (
            sp.sin(sp.Integer(a) * X) * sp.tan(sp.Integer(b) * X),
            rf"\sin({_lin_arg_tex(a)})\,\tan({_lin_arg_tex(b)})",
            sp.Integer(a * b),
            rf"\frac{{\sin({_lin_arg_tex(a)})}}{{x}}\cdot\frac{{\tan({_lin_arg_tex(b)})}}{{x}} \to {a*b}",
            rf"\frac{{\sin({_lin_arg_tex(a)})}}{{x}}\cdot\frac{{\tan({_lin_arg_tex(b)})}}{{x}} \to {a*b}",
        )
    if family == "x_tan":
        k = _nonzero_int(rng, -5, 5)
        a = rng.randint(1, 7)
        t = rf"x\tan({_lin_arg_tex(a)})"
        return (
            sp.Integer(k) * X * sp.tan(sp.Integer(a) * X),
            _coeff_var_tex(k, t, True),
            sp.Integer(k * a),
            rf"\frac{{{_coeff_var_tex(k, t, True)}}}{{x^2}} \to {k*a}",
            rf"\frac{{{_coeff_var_tex(k, t, True)}}}{{x^2}} \to {k*a}",
        )
    # "kx2"
    k = _nonzero_int(rng, -6, 6)
    return (
        sp.Integer(k) * (X**2),
        _coeff_var_tex(k, "x^{2}", True),
        sp.Integer(k),
        rf"\text{{المعامل }} {k}",
        rf"\text{{coefficient }} {k}",
    )


def _build_inter_order2_combo(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    top_fams = ["1_minus_cos", "cos_minus_cos", "sin_sin", "sin_tan"]
    bot_fams = ["1_minus_cos", "sin_tan", "x_tan", "kx2"]
    while True:
        f_num = rng.choice(top_fams)
        f_den = rng.choice(bot_fams)
        if f_num != f_den:
            break

    num_e, num_t, c2_num, h_ar_n, h_fr_n = _order2_block(rng, f_num)
    den_e, den_t, c2_den, h_ar_d, h_fr_d = _order2_block(rng, f_den)

    full_expr = num_e / den_e
    expr_tex = rf"\frac{{{num_t}}}{{{den_t}}}"
    expected = sp.simplify(c2_num / c2_den)
    point = LimitPoint(kind="finite", value=0, side=ApproachSide.BOTH)

    hints = [
        BilingualText(
            ar=r"البسط والمقام ينعدمان من الرتبة 2 عند $0$. اقسم البسط والمقام على $x^2$.",
            fr=r"Numérateur et dénominateur s'annulent à l'ordre 2 en $0$. Divisez par $x^2$.",
        ),
        BilingualText(
            ar=rf"استفد من: ${h_ar_n}$ وكذلك ${h_ar_d}$.",
            fr=rf"Utilisez : ${h_fr_n}$ et ${h_fr_d}$.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="القسمة على x²", fr="Division par x²"),
            explanation=BilingualText(
                ar=r"نحصل بالتعويض في $0$ على $\frac{0}{0}$. نقسم البسط والمقام معاً على $x^2$:",
                fr=r"En $0$, on a une FI $\frac{0}{0}$. On divise numérateur et dénominateur par $x^2$ :",
            ),
            latex=rf"{expr_tex} = \frac{{\frac{{{num_t}}}{{x^2}}}}{{\frac{{{den_t}}}{{x^2}}}}",
        ),
        SolutionStep(
            title=BilingualText(ar="حساب نهاية البسط والمقام", fr="Calcul des deux limites"),
            explanation=BilingualText(
                ar=rf"لدينا $\lim_{{x\to 0}} \frac{{{num_t}}}{{x^2}} = {value_latex(c2_num)}$ و $\lim_{{x\to 0}} \frac{{{den_t}}}{{x^2}} = {value_latex(c2_den)}$، إذن:",
                fr=rf"On a $\lim_{{x\to 0}} \frac{{{num_t}}}{{x^2}} = {value_latex(c2_num)}$ et $\lim_{{x\to 0}} \frac{{{den_t}}}{{x^2}} = {value_latex(c2_den)}$, d'où :",
            ),
            latex=rf"\lim_{{x \to 0}} {expr_tex} = \frac{{{value_latex(c2_num)}}}{{{value_latex(c2_den)}}} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, f"trig_order2_{f_num}_over_{f_den}", Difficulty.INTERMEDIATE, point,
        full_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", "order_2", "bac_trap"],
    )


def _build_inter_squeeze_or_recip(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    variant = rng.choice(["squeeze_inf_ratio", "squeeze_inf_zero", "squeeze_at_zero", "recip_inf"])

    if variant == "squeeze_inf_ratio":
        side_kind = rng.choice(["+oo", "-oo"])
        point = LimitPoint(kind=side_kind)
        a = _nonzero_int(rng, -7, 7)
        b = _nonzero_int(rng, -6, 6)
        c = rng.randint(1, 6)
        d = rng.randint(1, 6)
        e = rng.randint(1, 9)
        fn = rng.choice(["sin", "cos"])
        sym_fn = sp.sin if fn == "sin" else sp.cos
        trig_tex = rf"\{fn}({_lin_arg_tex(c)})"

        num_expr = sp.Integer(a) * X + sp.Integer(b) * sym_fn(sp.Integer(c) * X)
        den_expr = sp.Integer(d) * X + sp.Integer(e)
        num_tex = _signed_sum_latex([(a, "x"), (b, trig_tex)])
        den_tex = poly_latex(den_expr)
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.Rational(a, d)

        hints = [
            BilingualText(
                ar=rf"الدالة $\{fn}$ محدودة: $-1 \le {trig_tex} \le 1$ مهما كان $x \in \mathbb{{R}}$.",
                fr=rf"La fonction $\{fn}$ est bornée : $-1 \le {trig_tex} \le 1$ pour tout $x \in \mathbb{{R}}$.",
            ),
            BilingualText(
                ar=r"أطّر البسط ثم اقسم على المقام (مع الانتباه لإشارة $x$ عند $-\infty$) وطبق مبرهنة التأطير.",
                fr=r"Encadrez le numérateur, divisez par le dénominateur (attention au signe si $x \to -\infty$), puis appliquez le théorème des gendarmes.",
            ),
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="التأطير (مبرهنة الحصر)", fr="Encadrement (Théorème des gendarmes)"),
                explanation=BilingualText(
                    ar=rf"لكل $x \in \mathbb{{R}}$ لدينا $|{b}\,{trig_tex}| \le {abs(b)}$، وبما أن $\lim_{{x\to{point.point_latex()}}} \frac{{{abs(b)}}}{{{den_tex}}} = 0$:",
                    fr=rf"Pour tout $x \in \mathbb{{R}}$, $|{b}\,{trig_tex}| \le {abs(b)}$, et $\lim_{{x\to{point.point_latex()}}} \frac{{{abs(b)}}}{{{den_tex}}} = 0$ :",
                ),
                latex=rf"\frac{{{poly_latex(sp.Integer(a)*X)} - {abs(b)}}}{{{den_tex}}} \le {expr_tex} \le \frac{{{poly_latex(sp.Integer(a)*X)} + {abs(b)}}}{{{den_tex}}}",
            ),
            SolutionStep(
                title=BilingualText(ar="النتيجة بمبرهنة التأطير", fr="Conclusion par encadrement"),
                explanation=BilingualText(
                    ar=rf"بما أن حدي التأطير يؤولان معاً إلى $\frac{{{a}}}{{{d}}} = {value_latex(expected)}$ عند ${point.point_latex()}$:",
                    fr=rf"Comme les deux bornes tendent vers $\frac{{{a}}}{{{d}}} = {value_latex(expected)}$ en ${point.point_latex()}$ :",
                ),
                latex=rf"\lim_{{x \to {point.point_latex()}}} {expr_tex} = {value_latex(expected)}",
            ),
        ]
        return _finalize_trig_exercise(
            seed, "trig_squeeze_inf_ratio", Difficulty.INTERMEDIATE, point,
            num_expr / den_expr, expr_tex, expected, "bounded_inf", hints, steps,
            ["trig", "squeeze_theorem", "at_infinity"],
        )

    if variant == "squeeze_inf_zero":
        side_kind = rng.choice(["+oo", "-oo"])
        point = LimitPoint(kind=side_kind)
        a = _nonzero_int(rng, -5, 5)
        b = rng.randint(1, 5)
        c = _nonzero_int(rng, -5, 5)
        d = rng.randint(1, 5)
        e = rng.randint(1, 6)
        f = rng.randint(1, 9)
        power = rng.choice([1, 2])

        num_expr = sp.Integer(a) * sp.cos(sp.Integer(b) * X) + sp.Integer(c) * sp.sin(sp.Integer(d) * X)
        den_expr = sp.Integer(e) * (X**power) + sp.Integer(f)
        num_tex = _signed_sum_latex([(a, rf"\cos({_lin_arg_tex(b)})"), (c, rf"\sin({_lin_arg_tex(d)})")])
        den_tex = poly_latex(den_expr)
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        bound = abs(a) + abs(c)
        expected = sp.Integer(0)

        hints = [
            BilingualText(
                ar=rf"استعمل $|\cos(u)| \le 1$ و $|\sin(v)| \le 1$ لتأطير البسط: $|{num_tex}| \le {bound}$.",
                fr=rf"Utilisez $|\cos(u)| \le 1$ et $|\sin(v)| \le 1$ pour majorer le numérateur par ${bound}$.",
            ),
            BilingualText(
                ar=rf"بما أن $\lim_{{x\to{point.point_latex()}}} \frac{{{bound}}}{{|{den_tex}|}} = 0$ نستنتج النهاية.",
                fr=rf"Comme $\lim_{{x\to{point.point_latex()}}} \frac{{{bound}}}{{|{den_tex}|}} = 0$, on conclut par comparaison.",
            ),
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="حصر البسط", fr="Majoration du numérateur"),
                explanation=BilingualText(
                    ar=rf"بالمثلثية لدينا لكل $x \in \mathbb{{R}}$: $-{bound} \le {num_tex} \le {bound}$.",
                    fr=rf"Pour tout $x \in \mathbb{{R}}$, on a $-{bound} \le {num_tex} \le {bound}$.",
                ),
                latex=rf"\left|{expr_tex}\right| \le \frac{{{bound}}}{{|{den_tex}|}}",
            ),
            SolutionStep(
                title=BilingualText(ar="تطبيق مبرهنة التأطير", fr="Application du théorème des gendarmes"),
                explanation=BilingualText(
                    ar=rf"ولما كان $|{den_tex}| \to +\infty$ فإن $\frac{{{bound}}}{{|{den_tex}|}} \to 0$، ومنه:",
                    fr=rf"Comme $|{den_tex}| \to +\infty$, on a $\frac{{{bound}}}{{|{den_tex}|}} \to 0$, d'où :",
                ),
                latex=rf"\lim_{{x \to {point.point_latex()}}} {expr_tex} = 0",
            ),
        ]
        return _finalize_trig_exercise(
            seed, "trig_squeeze_inf_zero", Difficulty.INTERMEDIATE, point,
            num_expr / den_expr, expr_tex, expected, "bounded_div_inf", hints, steps,
            ["trig", "squeeze_theorem", "at_infinity"],
        )

    if variant == "squeeze_at_zero":
        point = LimitPoint(kind="finite", value=0, side=ApproachSide.BOTH)
        k = rng.choice([1, 2])
        a = _nonzero_int(rng, -8, 8)
        b = _nonzero_int(rng, -5, 5)
        fn = rng.choice(["sin", "cos"])
        sym_fn = sp.sin if fn == "sin" else sp.cos
        frac_arg = rf"\frac{{{a}}}{{x}}"
        xk_expr = sp.Integer(b) * (X**k)
        xk_tex = _coeff_var_tex(b, "x" if k == 1 else "x^{2}", True)
        full_expr = xk_expr * sym_fn(sp.Integer(a) / X)
        expr_tex = rf"{xk_tex}\,\{fn}\left({frac_arg}\right)"
        expected = sp.Integer(0)

        hints = [
            BilingualText(
                ar=rf"لا تبحث عن نهاية $\{fn}\left({frac_arg}\right)$ وحدها عند $0$ (لا نهاية لها)، بل أطّرها بـ $[-1, 1]$.",
                fr=rf"$\{fn}\left({frac_arg}\right)$ n'a pas de limite en $0$ : utilisez l'encadrement $-1 \le \{fn}\left({frac_arg}\right) \le 1$.",
            ),
            BilingualText(
                ar=rf"اضرب المتفاوتة في $|{xk_tex}| \to 0$ وطبق مبرهنة التأطير.",
                fr=rf"Multipliez par $|{xk_tex}| \to 0$ et concluez par le théorème des gendarmes.",
            ),
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="التأطير حول 0", fr="Encadrement au voisinage de 0"),
                explanation=BilingualText(
                    ar=rf"لكل $x \neq 0$ لدينا $\left|\{fn}\left({frac_arg}\right)\right| \le 1$، وبالتالي:",
                    fr=rf"Pour tout $x \neq 0$, $\left|\{fn}\left({frac_arg}\right)\right| \le 1$, donc :",
                ),
                latex=rf"\left|{expr_tex}\right| \le {abs(b)}|x|" + ("" if k == 1 else "^{2}"),
            ),
            SolutionStep(
                title=BilingualText(ar="النتيجة", fr="Conclusion"),
                explanation=BilingualText(
                    ar=r"بما أن الطرف الأيمن يؤول إلى $0$ عند $x \to 0$، فإن:",
                    fr=r"Comme le majorant tend vers $0$ lorsque $x \to 0$, on obtient :",
                ),
                latex=rf"\lim_{{x \to 0}} {expr_tex} = 0",
            ),
        ]
        return _finalize_trig_exercise(
            seed, "trig_squeeze_zero", Difficulty.INTERMEDIATE, point,
            full_expr, expr_tex, expected, "0*oscillating", hints, steps,
            ["trig", "squeeze_theorem", "at_zero"],
        )

    # variant == "recip_inf": t = 1/x -> 0
    side_kind = rng.choice(["+oo", "-oo"])
    point = LimitPoint(kind=side_kind)
    a = _nonzero_int(rng, -8, 8)
    b = _nonzero_int(rng, -5, 5)
    sub_kind = rng.choice(["x_sin", "x_tan", "x2_cos"])
    frac_arg = rf"\frac{{{a}}}{{x}}"

    if sub_kind in ("x_sin", "x_tan"):
        fn_name = "sin" if sub_kind == "x_sin" else "tan"
        sym_fn = sp.sin if sub_kind == "x_sin" else sp.tan
        full_expr = sp.Integer(b) * X * sym_fn(sp.Integer(a) / X)
        bx_tex = _coeff_var_tex(b, "x", True)
        expr_tex = rf"{bx_tex}\,\{fn_name}\left({frac_arg}\right)"
        expected = sp.Integer(a * b)
        t_tex = rf"{b}\,\frac{{\{fn_name}({poly_latex(sp.Integer(a)*sp.Symbol('t'), sp.Symbol('t'))})}}{{t}}"
    else:
        a_pos = abs(a)
        full_expr = sp.Integer(b) * (X**2) * (1 - sp.cos(sp.Integer(a_pos) / X))
        bx2_tex = _coeff_var_tex(b, "x^{2}", True)
        expr_tex = rf"{bx2_tex}\left(1 - \cos\left(\frac{{{a_pos}}}{{x}}\right)\right)"
        expected = sp.Rational(b * a_pos * a_pos, 2)
        t_tex = rf"{b}\,\frac{{1 - \cos({poly_latex(sp.Integer(a_pos)*sp.Symbol('t'), sp.Symbol('t'))})}}{{t^2}}"

    hints = [
        BilingualText(
            ar=rf"تواجه شكلاً غير محدد $\infty \times 0$. ضع تغيير المتغير $t = \frac{{1}}{{x}}$ بحيث عندما $x \to {point.point_latex()}$ فإن $t \to 0$.",
            fr=rf"Forme indéterminée $\infty \times 0$. Posez $t = \frac{{1}}{{x}}$ ; lorsque $x \to {point.point_latex()}$, on a $t \to 0$.",
        ),
        BilingualText(
            ar=r"ستعود مباشرة لإحدى النهايات المثلثية الاعتيادية في $0$.",
            fr=r"On se ramène directement à une limite trigonométrique usuelle en $0$.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="تغيير المتغير t = 1/x", fr="Changement de variable t = 1/x"),
            explanation=BilingualText(
                ar=rf"نضع $t = \frac{{1}}{{x}}$؛ عند $x \to {point.point_latex()}$ لدينا $t \to 0$ وتصبح العبارة:",
                fr=rf"On pose $t = \frac{{1}}{{x}}$ ; quand $x \to {point.point_latex()}$, $t \to 0$ et l'expression devient :",
            ),
            latex=rf"{expr_tex} = {t_tex}",
        ),
        SolutionStep(
            title=BilingualText(ar="حساب النهاية عند t -> 0", fr="Calcul de la limite en t -> 0"),
            explanation=BilingualText(
                ar=r"بتطبيق النهاية الاعتيادية عند $t \to 0$:",
                fr=r"En appliquant la limite usuelle lorsque $t \to 0$ :",
            ),
            latex=rf"\lim_{{t \to 0}} \left({t_tex}\right) = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, f"trig_recip_inf_{sub_kind}", Difficulty.INTERMEDIATE, point,
        full_expr, expr_tex, expected, "oo*0", hints, steps,
        ["trig", "change_of_variable", "at_infinity"],
    )


def _build_inter_shift_pi_or_x0(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    variant = rng.choice(["shift_pi_int", "shift_pi_point", "shift_pi_half"])

    if variant == "shift_pi_int":
        x0 = _nonzero_int(rng, -3, 3)
        k = rng.randint(1, 3)
        c = _nonzero_int(rng, -2, 2)
        while True:
            d = rng.randint(-4, 4)
            if c * x0 + d != 0:
                break
        point = LimitPoint(kind="finite", value=x0, side=ApproachSide.BOTH)
        fn = rng.choice(["sin", "tan"])
        sym_fn = sp.sin if fn == "sin" else sp.tan
        pi_arg_tex = _pi_mult_tex(k, "x")
        den_poly = sp.expand((X - sp.Integer(x0)) * (sp.Integer(c) * X + sp.Integer(d)))
        den_tex = poly_latex(den_poly)
        num_expr = sym_fn(sp.Integer(k) * sp.pi * X)
        expr_tex = rf"\frac{{\{fn}({pi_arg_tex})}}{{{den_tex}}}"

        sign_factor = ((-1) ** (k * x0)) if fn == "sin" else 1
        expected = sp.simplify(sp.Integer(sign_factor * k) * sp.pi / sp.Integer(c * x0 + d))
        lin_tex = linear_factor_latex("x", x0)

        id_note_ar = (
            rf"$\sin({pi_arg_tex}) = \sin({k}\pi({x0} + t)) = {'(-1)^{'+str(k*x0)+'}' if sign_factor < 0 else ''}\sin({k}\pi t)$"
            if fn == "sin"
            else rf"$\tan({pi_arg_tex}) = \tan({k}\pi({x0} + t)) = \tan({k}\pi t)$ (للدورية $\pi$)"
        )
        id_note_fr = (
            rf"$\sin({pi_arg_tex}) = \sin({k}\pi({x0} + t)) = {'-' if sign_factor < 0 else ''}\sin({k}\pi t)$"
            if fn == "sin"
            else rf"$\tan({pi_arg_tex}) = \tan({k}\pi({x0} + t)) = \tan({k}\pi t)$ ($\pi$-périodique)"
        )
        hints = [
            BilingualText(
                ar=rf"ضع تغيير المتغير $t = {lin_tex} \to 0$ وعوّض $x = {x0} + t$ في $\{fn}({pi_arg_tex})$.",
                fr=rf"Posez $t = {lin_tex} \to 0$ et remplacez $x = {x0} + t$ dans $\{fn}({pi_arg_tex})$.",
            ),
            BilingualText(
                ar=rf"عمّل المقام بـ $({lin_tex})$: ${den_tex} = ({lin_tex})({poly_latex(sp.Integer(c)*X + d)})$.",
                fr=rf"Factorisez le dénominateur : ${den_tex} = ({lin_tex})({poly_latex(sp.Integer(c)*X + d)})$.",
            ),
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="تعميل المقام وتغيير المتغير", fr="Factorisation et changement de variable"),
                explanation=BilingualText(
                    ar=rf"بوضع $t = {lin_tex} \to 0$ نجد {id_note_ar}:",
                    fr=rf"Avec $t = {lin_tex} \to 0$, on a {id_note_fr} :",
                ),
                latex=rf"\lim_{{x \to {x0}}} {expr_tex} = \lim_{{t \to 0}} \frac{{{'-' if sign_factor < 0 else ''}\{fn}({k}\pi t)}}{{t}} \cdot \frac{{1}}{{{poly_latex(sp.Integer(c)*X + d)}}}",
            ),
            SolutionStep(
                title=BilingualText(ar="الحساب النهائي", fr="Conclusion"),
                explanation=BilingualText(
                    ar=r"نطبّق النهاية الاعتيادية عند $0$ مع $\lim \frac{1}{" + poly_latex(sp.Integer(c) * X + d) + rf"}} = \frac{{1}}{{{c*x0 + d}}}$:",
                    fr=r"En utilisant la limite usuelle en $0$ :",
                ),
                latex=rf"\lim_{{x \to {x0}}} {expr_tex} = \frac{{{'-' if sign_factor < 0 else ''}{_pi_mult_tex(k, '')}}}{{{c * x0 + d}}} = {value_latex(expected)}",
            ),
        ]
        return _finalize_trig_exercise(
            seed, "trig_shift_pi_int", Difficulty.INTERMEDIATE, point,
            num_expr / den_poly, expr_tex, expected, "0/0", hints, steps,
            ["trig", "change_of_variable", "periodicity"],
        )

    if variant == "shift_pi_point":
        k = rng.randint(1, 5)
        a = _nonzero_int(rng, -5, 5)
        point = LimitPoint(kind="finite", sympy_str="pi")
        num_expr = sp.sin(sp.Integer(k) * X)
        den_expr = sp.Integer(a) * (X - sp.pi)
        num_tex = rf"\sin({_lin_arg_tex(k)})"
        den_tex = rf"{_coeff_var_tex(a, 'x', True)} - {abs(a) if abs(a) > 1 else ''}\pi" if a > 0 else rf"{_coeff_var_tex(a, 'x', True)} + {abs(a) if abs(a) > 1 else ''}\pi"
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.Rational(((-1) ** k) * k, a)

        hints = [
            BilingualText(
                ar=r"ضع $t = x - \pi \to 0$ أي $x = \pi + t$.",
                fr=r"Posez $t = x - \pi \to 0$, c'est-à-dire $x = \pi + t$.",
            ),
            BilingualText(
                ar=rf"استعمل الصيغة المثلثية $\sin({k}\pi + {k}t) = (-1)^{{{k}}}\sin({k}t)$.",
                fr=rf"Utilisez la formule $\sin({k}\pi + {k}t) = (-1)^{{{k}}}\sin({k}t)$.",
            ),
        ]
        sign_str = "-" if (k % 2 == 1) else ""
        steps = [
            SolutionStep(
                title=BilingualText(ar="تغيير المتغير t = x - π", fr="Changement de variable t = x - π"),
                explanation=BilingualText(
                    ar=rf"بوضع $t = x - \pi \to 0$ لدينا ${den_tex} = {a}t$ و $\sin({k}(\pi + t)) = {sign_str}\sin({k}t)$.",
                    fr=rf"En posant $t = x - \pi \to 0$, on a ${den_tex} = {a}t$ et $\sin({k}(\pi + t)) = {sign_str}\sin({k}t)$.",
                ),
                latex=rf"\lim_{{x \to \pi}} {expr_tex} = \lim_{{t \to 0}} \frac{{{sign_str}\sin({_lin_arg_tex(k, 't')})}}{{{_lin_arg_tex(a, 't')}}}",
            ),
            SolutionStep(
                title=BilingualText(ar="النتيجة", fr="Résultat"),
                explanation=BilingualText(
                    ar=r"باستعمال النهاية الاعتيادية عند $0$:",
                    fr=r"Par limite usuelle en $0$ :",
                ),
                latex=rf"= {value_latex(expected)}",
            ),
        ]
        return _finalize_trig_exercise(
            seed, "trig_shift_at_pi", Difficulty.INTERMEDIATE, point,
            num_expr / den_expr, expr_tex, expected, "0/0", hints, steps,
            ["trig", "change_of_variable", "pi_point"],
        )

    # variant == "shift_pi_half": x -> pi/2, cos(kx) / cos(mx) with odd k, m or cos(kx) / (2x - pi)
    k = rng.choice([1, 3, 5])
    m = rng.choice([v for v in [1, 3, 5] if v != k])
    point = LimitPoint(kind="finite", sympy_str="pi/2")
    if rng.random() < 0.55:
        c = _nonzero_int(rng, -4, 4)
        a = 2 * c
        num_expr = sp.cos(sp.Integer(k) * X)
        den_expr = sp.Integer(a) * X - sp.Integer(c) * sp.pi
        num_tex = rf"\cos({_lin_arg_tex(k)})"
        # a*x - c*pi cleanly without fractions
        pi_part = rf"{abs(c)}\pi" if abs(c) > 1 else r"\pi"
        den_tex = rf"{a}x - {pi_part}" if c > 0 else rf"{a}x + {pi_part}"
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify((-sp.Integer(k) * sp.sin(sp.Integer(k) * sp.pi / 2)) / sp.Integer(a))
    else:
        num_expr = sp.cos(sp.Integer(k) * X)
        den_expr = sp.cos(sp.Integer(m) * X)
        expr_tex = rf"\frac{{\cos({_lin_arg_tex(k)})}}{{\cos({_lin_arg_tex(m)})}}"
        expected = sp.simplify(
            (sp.Integer(k) * sp.sin(sp.Integer(k) * sp.pi / 2))
            / (sp.Integer(m) * sp.sin(sp.Integer(m) * sp.pi / 2))
        )

    hints = [
        BilingualText(
            ar=r"ضع تغيير المتغير $t = x - \frac{\pi}{2} \to 0$ أي $x = \frac{\pi}{2} + t$.",
            fr=r"Posez $t = x - \frac{\pi}{2} \to 0$, soit $x = \frac{\pi}{2} + t$.",
        ),
        BilingualText(
            ar=r"تذكّر أن $\cos\left(\frac{\pi}{2} + u\right) = -\sin(u)$ و $\cos\left(\frac{3\pi}{2} + u\right) = +\sin(u)$.",
            fr=r"Rappel : $\cos\left(\frac{\pi}{2} + u\right) = -\sin(u)$ et $\cos\left(\frac{3\pi}{2} + u\right) = +\sin(u)$.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="تغيير المتغير عند π/2", fr="Changement de variable en π/2"),
            explanation=BilingualText(
                ar=r"نضع $t = x - \frac{\pi}{2} \to 0$ ونستعمل $\cos\left(k\left(\frac{\pi}{2}+t\right)\right) = -\sin\left(\frac{k\pi}{2}\right)\sin(kt)$.",
                fr=r"On pose $t = x - \frac{\pi}{2} \to 0$ et on utilise $\cos\left(k\left(\frac{\pi}{2}+t\right)\right) = -\sin\left(\frac{k\pi}{2}\right)\sin(kt)$.",
            ),
            latex=rf"\lim_{{x \to \frac{{\pi}}{{2}}}} {expr_tex} \quad (t = x - \tfrac{{\pi}}{{2}} \to 0)",
        ),
        SolutionStep(
            title=BilingualText(ar="المرور إلى النهاية", fr="Passage à la limite"),
            explanation=BilingualText(
                ar=r"باستعمال $\lim_{t \to 0} \frac{\sin(kt)}{t} = k$ نحصل على:",
                fr=r"À l'aide de $\lim_{t \to 0} \frac{\sin(kt)}{t} = k$, on obtient :",
            ),
            latex=rf"\lim_{{x \to \frac{{\pi}}{{2}}}} {expr_tex} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, "trig_shift_at_pi_half", Difficulty.INTERMEDIATE, point,
        num_expr / den_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", "change_of_variable", "pi_point"],
    )


# ---------------------------------------------------------------------------
# Level 3 — CHALLENGE: tan(x)-sin(x), sqrt(1-cos(x)), radicals, special angles
# ---------------------------------------------------------------------------


def _build_challenge_tan_minus_sin(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    a = _nonzero_int(rng, 1, 5)
    b = _nonzero_int(rng, -5, 5)
    den_variant = rng.choice(["bx3", "bx_cos", "bx2_sin"])
    swap = rng.choice([False, False, True])

    a_tex = _lin_arg_tex(a)
    num_expr = sp.tan(sp.Integer(a) * X) - sp.sin(sp.Integer(a) * X)
    num_tex = rf"\tan({a_tex}) - \sin({a_tex})"
    c3_num = sp.Rational(a**3, 2)

    if den_variant == "bx3":
        den_expr = sp.Integer(b) * (X**3)
        den_tex = _coeff_var_tex(b, "x^{3}", True)
        c3_den = sp.Integer(b)
    elif den_variant == "bx_cos":
        d = rng.randint(1, 5)
        den_expr = sp.Integer(b) * X * (1 - sp.cos(sp.Integer(d) * X))
        den_tex = rf"{_coeff_var_tex(b, 'x', True)}\left(1 - \cos({_lin_arg_tex(d)})\right)"
        c3_den = sp.Rational(b * d * d, 2)
    else:
        d = rng.randint(1, 5)
        den_expr = sp.Integer(b) * (X**2) * sp.sin(sp.Integer(d) * X)
        den_tex = rf"{_coeff_var_tex(b, 'x^{2}', True)}\,\sin({_lin_arg_tex(d)})"
        c3_den = sp.Integer(b * d)

    if swap:
        full_expr = den_expr / num_expr
        expr_tex = rf"\frac{{{den_tex}}}{{{num_tex}}}"
        expected = sp.simplify(c3_den / c3_num)
    else:
        full_expr = num_expr / den_expr
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify(c3_num / c3_den)

    point = LimitPoint(kind="finite", value=0, side=ApproachSide.BOTH)
    hints = [
        BilingualText(
            ar=rf"عمّل بـ $\tan({a_tex})$ أو بـ $\sin({a_tex})$ في $\tan({a_tex}) - \sin({a_tex})$: تذكّر أن $\tan u - \sin u = \tan u(1 - \cos u)$.",
            fr=rf"Factorisez $\tan({a_tex}) - \sin({a_tex}) = \tan({a_tex})\left(1 - \cos({a_tex})\right)$.",
        ),
        BilingualText(
            ar=rf"هذا المقدار ينعدم من الرتبة 3: $\frac{{\tan({a_tex})}}{{x}} \cdot \frac{{1 - \cos({a_tex})}}{{x^2}} \to \frac{{{a}^3}}{{2}}$.",
            fr=rf"Le terme est d'ordre 3 : $\frac{{\tan({a_tex})}}{{x}} \cdot \frac{{1 - \cos({a_tex})}}{{x^2}} \to \frac{{{a}^3}}{{2}}$.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="التعميل بالمتطابقة المثلثية", fr="Factorisation trigonométrique clé"),
            explanation=BilingualText(
                ar=r"نكتب $\tan u = \frac{\sin u}{\cos u}$ فنجد $\tan u - \sin u = \tan u(1 - \cos u)$:",
                fr=r"En écrivant $\tan u = \frac{\sin u}{\cos u}$, on factorise :",
            ),
            latex=rf"\tan({a_tex}) - \sin({a_tex}) = \tan({a_tex})\left(1 - \cos({a_tex})\right)",
        ),
        SolutionStep(
            title=BilingualText(ar="القسمة على x³ والمرور للنهاية", fr="Division par x³ et conclusion"),
            explanation=BilingualText(
                ar=rf"لدينا $\lim_{{x\to 0}}\frac{{\tan({a_tex})(1 - \cos({a_tex}))}}{{x^3}} = {a}\cdot\frac{{{a}^2}}{{2}} = {value_latex(c3_num)}$، وبالتالي:",
                fr=rf"Comme $\lim_{{x\to 0}}\frac{{\tan({a_tex})(1 - \cos({a_tex}))}}{{x^3}} = {value_latex(c3_num)}$, on conclut :",
            ),
            latex=rf"\lim_{{x \to 0}} {expr_tex} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, "trig_challenge_tan_minus_sin", Difficulty.CHALLENGE, point,
        full_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", "order_3", "tan_minus_sin", "devoir_surveille"],
    )


def _build_challenge_sqrt_one_minus_cos(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    side = rng.choice([ApproachSide.RIGHT, ApproachSide.LEFT])
    point = LimitPoint(kind="finite", value=0, side=side)
    a = rng.randint(2, 8)
    b = _nonzero_int(rng, -5, 5)
    c = rng.randint(-4, 4)
    d = rng.randint(1, 5)
    while b + c * d == 0:
        b = _nonzero_int(rng, -5, 5)

    den_slope = sp.Integer(b + c * d)
    a_tex = _lin_arg_tex(a)
    num_expr = RootN(1 - sp.cos(sp.Integer(a) * X), 2)
    num_tex = root_latex(rf"1 - \cos({a_tex})", 2)

    if c == 0:
        den_expr = sp.Integer(b) * X
        den_tex = _coeff_var_tex(b, "x", True)
    else:
        den_expr = sp.Integer(b) * X + sp.Integer(c) * sp.sin(sp.Integer(d) * X)
        den_tex = _signed_sum_latex([(b, "x"), (c, rf"\sin({_lin_arg_tex(d)})")])

    full_expr = num_expr / den_expr
    expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"

    abs_slope_top = sp.Rational(a, 1) / sp.sqrt(2)
    expected = sp.simplify((abs_slope_top if side == ApproachSide.RIGHT else -abs_slope_top) / den_slope)

    sign_word_ar = "موجب ($x > 0$)" if side == ApproachSide.RIGHT else "سالب ($x < 0$)"
    sign_word_fr = "positif ($x > 0$)" if side == ApproachSide.RIGHT else "négatif ($x < 0$)"
    abs_sign = "" if side == ApproachSide.RIGHT else "-"
    half_arg = rf"\frac{{{a}x}}{{2}}" if (a % 2 != 0) else _lin_arg_tex(a // 2)

    hints = [
        BilingualText(
            ar=rf"استعمل العلاقة $1 - \cos({a_tex}) = 2\sin^2\left({half_arg}\right)$ لإخراج الجذر المربع.",
            fr=rf"Utilisez l'identité $1 - \cos({a_tex}) = 2\sin^2\left({half_arg}\right)$ pour simplifier la racine.",
        ),
        BilingualText(
            ar=rf"انتبه للفخ! $\sqrt{{\sin^2(u)}} = |\sin(u)|$؛ وبما أن $x$ {sign_word_ar} فإن $\left|\sin\left({half_arg}\right)\right| = {abs_sign}\sin\left({half_arg}\right)$.",
            fr=rf"Attention au piège ! $\sqrt{{\sin^2(u)}} = |\sin(u)|$ ; comme $x$ est {sign_word_fr}, $\left|\sin\left({half_arg}\right)\right| = {abs_sign}\sin\left({half_arg}\right)$.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="تبسيط الجذر باستعمال القيمة المطلقة", fr="Simplification avec valeur absolue"),
            explanation=BilingualText(
                ar=rf"لدينا $1 - \cos({a_tex}) = 2\sin^2\left({half_arg}\right)$، ومنه بجوار ${point.point_latex()}$ حيث $\sin\left({half_arg}\right)$ {sign_word_ar}:",
                fr=rf"On a $1 - \cos({a_tex}) = 2\sin^2\left({half_arg}\right)$, et au voisinage de ${point.point_latex()}$ :",
            ),
            latex=rf"{num_tex} = \sqrt{{2}}\left|\sin\left({half_arg}\right)\right| = {abs_sign}\sqrt{{2}}\sin\left({half_arg}\right)",
        ),
        SolutionStep(
            title=BilingualText(ar="القسمة على x وحساب النهاية", fr="Division par x et conclusion"),
            explanation=BilingualText(
                ar=r"نقسم البسط والمقام على $x$ ونستعمل $\lim_{x\to 0}\frac{\sin(kx)}{x} = k$:",
                fr=r"En divisant numérateur et dénominateur par $x$ :",
            ),
            latex=rf"\lim_{{x \to {point.point_latex()}}} {expr_tex} = \frac{{{abs_sign}{value_latex(abs_slope_top)}}}{{{value_latex(den_slope)}}} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, "trig_challenge_sqrt_one_minus_cos", Difficulty.CHALLENGE, point,
        full_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", "abs_sign_trap", "one_sided", "devoir_surveille"],
    )


def _build_challenge_trig_radical_or_pi_cos(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    variant = rng.choice(["trig_of_sqrt", "pi_cos_one_plus", "trig_over_sqrt_conj"])

    if variant == "trig_of_sqrt":
        point = LimitPoint(kind="finite", value=0, side=ApproachSide.RIGHT)
        a = rng.randint(1, 7)
        b = _nonzero_int(rng, -6, 6)
        c = rng.randint(-4, 4)
        root_x = RootN(X, 2)
        sqrt_x_str = r"\sqrt{x}"
        num_expr = 1 - sp.cos(sp.Integer(a) * root_x)
        num_tex = rf"1 - \cos({_lin_arg_tex(a, sqrt_x_str)})"
        den_expr = sp.Integer(b) * X + sp.Integer(c) * (X**2)
        den_tex = poly_latex(den_expr)
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.Rational(a * a, 2 * b)

        hints = [
            BilingualText(
                ar=r"ضع تغيير المتغير $t = \sqrt{x} \to 0^+$ فتصبح $x = t^2$.",
                fr=r"Posez $t = \sqrt{x} \to 0^+$, donc $x = t^2$.",
            ),
            BilingualText(
                ar=rf"تتحول النهاية إلى $\lim_{{t \to 0^+}} \frac{{1 - \cos({a}t)}}{{{b}t^2 + ({c})t^4}}$.",
                fr=rf"La limite devient $\lim_{{t \to 0^+}} \frac{{1 - \cos({a}t)}}{{{b}t^2 + ({c})t^4}}$.",
            ),
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="تغيير المتغير t = √x", fr="Changement de variable t = √x"),
                explanation=BilingualText(
                    ar=r"بوضع $t = \sqrt{x}$ لدينا $x = t^2$ وعند $x \to 0^+$ يؤول $t \to 0^+$:",
                    fr=r"En posant $t = \sqrt{x}$, on a $x = t^2$ et $t \to 0^+$ :",
                ),
                latex=rf"\lim_{{x \to 0^+}} {expr_tex} = \lim_{{t \to 0^+}} \frac{{1 - \cos({a}t)}}{{t^2({poly_latex(sp.Integer(b) + sp.Integer(c)*sp.Symbol('t')**2, sp.Symbol('t'))})}} = {value_latex(expected)}",
            ),
        ]
        return _finalize_trig_exercise(
            seed, "trig_challenge_of_sqrt", Difficulty.CHALLENGE, point,
            num_expr / den_expr, expr_tex, expected, "0/0", hints, steps,
            ["trig", "change_of_variable", "radical"],
        )

    if variant == "pi_cos_one_plus":
        x0 = rng.choice([-3, -1, 1, 3])
        k = rng.choice([1, 2, 3])
        c = rng.choice([1, 2])
        d = rng.randint(1, 5)
        # Ensure (c*x0 + d) is simple non-zero factor
        if c * x0 + d == 0:
            d += 2
        point = LimitPoint(kind="finite", value=x0, side=ApproachSide.BOTH)
        num_expr = 1 + sp.cos(sp.pi * X)
        num_tex = r"1 + \cos(\pi x)"
        den_poly = sp.expand(sp.Integer(k) * ((X - sp.Integer(x0)) ** 2))
        w_e = sp.Integer(c) * X + sp.Integer(d)
        den_expr = den_poly * w_e
        if k == 1 and c == 1 and d == x0:
            # Classic (x^2 - x0^2)^2 clean form!
            den_tex = rf"({poly_latex(sp.expand(X**2 - x0**2))})^{{2}}"
            den_expr = (X**2 - sp.Integer(x0) ** 2) ** 2
            w_val = sp.Integer((2 * x0) ** 2)
        else:
            den_tex = rf"{poly_latex(den_poly)}({poly_latex(w_e)})"
            w_val = sp.Integer(k) * sp.Integer(c * x0 + d)

        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify((sp.pi**2) / (sp.Integer(2) * w_val))
        lin_tex = linear_factor_latex("x", x0)

        hints = [
            BilingualText(
                ar=rf"ضع $t = {lin_tex} \to 0$. بما أن ${x0}$ عدد فردي فإن $\cos(\pi({x0} + t)) = -\cos(\pi t)$.",
                fr=rf"Posez $t = {lin_tex} \to 0$. Comme ${x0}$ est impair, $\cos(\pi({x0} + t)) = -\cos(\pi t)$.",
            ),
            BilingualText(
                ar=r"إذن يصبح البسط $1 - \cos(\pi t)$ وهو ينعدم من الرتبة 2: $\lim_{t\to 0}\frac{1 - \cos(\pi t)}{t^2} = \frac{\pi^2}{2}$.",
                fr=r"Le numérateur devient $1 - \cos(\pi t)$, avec $\lim_{t\to 0}\frac{1 - \cos(\pi t)}{t^2} = \frac{\pi^2}{2}$.",
            ),
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="تغيير المتغير واستغلال الدورية", fr="Changement de variable et parité en π"),
                explanation=BilingualText(
                    ar=rf"بوضع $t = {lin_tex} \to 0$ وبما أن ${x0}$ فردي، $\cos(\pi({x0} + t)) = \cos({x0}\pi + \pi t) = -\cos(\pi t)$.",
                    fr=rf"Avec $t = {lin_tex} \to 0$, comme ${x0}$ est impair, $\cos(\pi({x0}+t)) = -\cos(\pi t)$.",
                ),
                latex=rf"1 + \cos(\pi x) = 1 - \cos(\pi t)",
            ),
            SolutionStep(
                title=BilingualText(ar="النتيجة النهائية", fr="Calcul final"),
                explanation=BilingualText(
                    ar=r"باستعمال $\lim_{t\to 0}\frac{1-\cos(\pi t)}{t^2} = \frac{\pi^2}{2}$:",
                    fr=r"À l'aide de $\lim_{t\to 0}\frac{1-\cos(\pi t)}{t^2} = \frac{\pi^2}{2}$ :",
                ),
                latex=rf"\lim_{{x \to {x0}}} {expr_tex} = \frac{{\pi^2}}{{2 \cdot ({value_latex(w_val)})}} = {value_latex(expected)}",
            ),
        ]
        return _finalize_trig_exercise(
            seed, "trig_challenge_one_plus_cos_pi", Difficulty.CHALLENGE, point,
            num_expr / den_expr, expr_tex, expected, "0/0", hints, steps,
            ["trig", "periodicity", "order_2", "devoir_surveille"],
        )

    # variant == "trig_over_sqrt_conj": sin(ax) / (sqrt(mx + k^2) - k) at x -> 0
    point = LimitPoint(kind="finite", value=0, side=ApproachSide.BOTH)
    a = _nonzero_int(rng, -7, 7)
    fn = rng.choice(["sin", "tan"])
    sym_fn = sp.sin if fn == "sin" else sp.tan
    k = rng.randint(1, 5)
    m = _nonzero_int(rng, -5, 5)

    num_expr = sym_fn(sp.Integer(a) * X)
    num_tex = rf"\{fn}({_lin_arg_tex(a)})"
    inner_expr = sp.Integer(m) * X + sp.Integer(k * k)
    den_expr = RootN(inner_expr, 2) - sp.Integer(k)
    inner_tex = poly_latex(inner_expr)
    den_tex = rf"{root_latex(inner_tex, 2)} - {k}"

    full_expr = num_expr / den_expr
    expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
    expected = sp.Rational(2 * a * k, m)

    hints = [
        BilingualText(
            ar=rf"هنا نجمع بين المرافق التربيعي في المقام (${root_latex(inner_tex, 2)} + {k}$) والنهاية المثلثية في البسط.",
            fr=rf"Combinez l'expression conjuguée au dénominateur (${root_latex(inner_tex, 2)} + {k}$) et la limite usuelle en $\{fn}$.",
        ),
        BilingualText(
            ar=r"بعد الضرب في المرافق يظهر العامل $x$ في المقام، فاستعمل $\frac{\sin(ax)}{x}$ أو $\frac{\tan(ax)}{x}$.",
            fr=r"Après multiplication par le conjugué, $x$ apparaît au dénominateur.",
        ),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar="الضرب في المرافق التربيعي", fr="Expression conjuguée au dénominateur"),
            explanation=BilingualText(
                ar=rf"نضرب البسط والمقام في مرافق المقام $({root_latex(inner_tex, 2)} + {k})$:",
                fr=rf"On multiplie numérateur et dénominateur par $({root_latex(inner_tex, 2)} + {k})$ :",
            ),
            latex=rf"{expr_tex} = \frac{{{num_tex}\left({root_latex(inner_tex, 2)} + {k}\right)}}{{{_lin_arg_tex(m)}}}",
        ),
        SolutionStep(
            title=BilingualText(ar="النهاية المثلثية والنتيجة", fr="Limite trigonométrique et conclusion"),
            explanation=BilingualText(
                ar=rf"لدينا $\lim_{{x\to 0}}\frac{{{num_tex}}}{{x}} = {a}$ و $\lim_{{x\to 0}}\left({root_latex(inner_tex, 2)} + {k}\right) = {2*k}$:",
                fr=rf"Comme $\lim_{{x\to 0}}\frac{{{num_tex}}}{{x}} = {a}$ et $\lim_{{x\to 0}}\left({root_latex(inner_tex, 2)} + {k}\right) = {2*k}$ :",
            ),
            latex=rf"\lim_{{x \to 0}} {expr_tex} = \frac{{{a} \cdot {2*k}}}{{{m}}} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, "trig_challenge_over_sqrt_conj", Difficulty.CHALLENGE, point,
        full_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", "sqrt_conjugate", "hybrid_style"],
    )


def _build_challenge_special_angles(rng: random.Random, seed: str) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    sub = rng.choice(["pi_3_linear", "pi_3_sin3x", "pi_6_linear", "pi_6_cos3x", "pi_4_tan_minus_1", "pi_4_sin_minus_cos"])

    if sub == "pi_3_linear":
        k = _nonzero_int(rng, -4, 4)
        a = 3 * k
        point = LimitPoint(kind="finite", sympy_str="pi/3")
        num_expr = 2 * sp.cos(X) - 1
        den_expr = sp.Integer(a) * X - sp.Integer(k) * sp.pi
        num_tex = r"2\cos(x) - 1"
        pi_part = rf"{abs(k)}\pi" if abs(k) > 1 else r"\pi"
        den_tex = rf"{a}x - {pi_part}" if k > 0 else rf"{a}x + {pi_part}"
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify(-sp.sqrt(3) / sp.Integer(a))
        id_ar = r"$2\cos\left(\frac{\pi}{3} + t\right) - 1 = (\cos t - 1) - \sqrt{3}\sin t$"
        id_fr = r"$2\cos\left(\frac{\pi}{3} + t\right) - 1 = (\cos t - 1) - \sqrt{3}\sin t$"
    elif sub == "pi_3_sin3x":
        b = _nonzero_int(rng, -4, 4)
        fn = rng.choice(["sin", "tan"])
        sym_fn = sp.sin if fn == "sin" else sp.tan
        point = LimitPoint(kind="finite", sympy_str="pi/3")
        num_expr = 2 * sp.cos(X) - 1
        den_expr = sp.Integer(b) * sym_fn(3 * X)
        num_tex = r"2\cos(x) - 1"
        den_tex = _coeff_var_tex(b, rf"\{fn}(3x)", True)
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify(sp.sqrt(3) / sp.Integer(3 * b))
        id_ar = rf"$2\cos\left(\frac{{\pi}}{{3}} + t\right) - 1 = (\cos t - 1) - \sqrt{{3}}\sin t$ و $\{fn}(\pi + 3t) = {'-' if fn=='sin' else ''}\{fn}(3t)$"
        id_fr = rf"$2\cos\left(\frac{{\pi}}{{3}} + t\right) - 1 = (\cos t - 1) - \sqrt{{3}}\sin t$ et $\{fn}(\pi + 3t) = {'-' if fn=='sin' else ''}\{fn}(3t)$"
    elif sub == "pi_6_linear":
        k = _nonzero_int(rng, -4, 4)
        a = 6 * k
        point = LimitPoint(kind="finite", sympy_str="pi/6")
        num_expr = 2 * sp.sin(X) - 1
        den_expr = sp.Integer(a) * X - sp.Integer(k) * sp.pi
        num_tex = r"2\sin(x) - 1"
        pi_part = rf"{abs(k)}\pi" if abs(k) > 1 else r"\pi"
        den_tex = rf"{a}x - {pi_part}" if k > 0 else rf"{a}x + {pi_part}"
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify(sp.sqrt(3) / sp.Integer(a))
        id_ar = r"$2\sin\left(\frac{\pi}{6} + t\right) - 1 = (\cos t - 1) + \sqrt{3}\sin t$"
        id_fr = r"$2\sin\left(\frac{\pi}{6} + t\right) - 1 = (\cos t - 1) + \sqrt{3}\sin t$"
    elif sub == "pi_6_cos3x":
        b = _nonzero_int(rng, -4, 4)
        point = LimitPoint(kind="finite", sympy_str="pi/6")
        num_expr = 2 * sp.sin(X) - 1
        den_expr = sp.Integer(b) * sp.cos(3 * X)
        num_tex = r"2\sin(x) - 1"
        den_tex = _coeff_var_tex(b, r"\cos(3x)", True)
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify(-sp.sqrt(3) / sp.Integer(3 * b))
        id_ar = r"$2\sin\left(\frac{\pi}{6} + t\right) - 1 = (\cos t - 1) + \sqrt{3}\sin t$ و $\cos\left(\frac{\pi}{2} + 3t\right) = -\sin(3t)$"
        id_fr = r"$2\sin\left(\frac{\pi}{6} + t\right) - 1 = (\cos t - 1) + \sqrt{3}\sin t$ et $\cos\left(\frac{\pi}{2} + 3t\right) = -\sin(3t)$"
    elif sub == "pi_4_tan_minus_1":
        a = _nonzero_int(rng, -5, 5)
        point = LimitPoint(kind="finite", sympy_str="pi/4")
        num_expr = sp.sin(X) - sp.cos(X)
        den_expr = sp.Integer(a) * (sp.tan(X) - 1)
        num_tex = r"\sin(x) - \cos(x)"
        den_tex = rf"{a}(\tan(x) - 1)" if abs(a) > 1 else (r"\tan(x) - 1" if a == 1 else r"-(\tan(x) - 1)")
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify(sp.sqrt(2) / sp.Integer(2 * a))
        id_ar = r"$\tan(x) - 1 = \frac{\sin(x) - \cos(x)}{\cos(x)}$!"
        id_fr = r"$\tan(x) - 1 = \frac{\sin(x) - \cos(x)}{\cos(x)}$ !"
    else:  # pi_4_sin_minus_cos
        k = _nonzero_int(rng, -4, 4)
        a = 4 * k
        point = LimitPoint(kind="finite", sympy_str="pi/4")
        num_expr = sp.sin(X) - sp.cos(X)
        den_expr = sp.Integer(a) * X - sp.Integer(k) * sp.pi
        num_tex = r"\sin(x) - \cos(x)"
        pi_part = rf"{abs(k)}\pi" if abs(k) > 1 else r"\pi"
        den_tex = rf"{a}x - {pi_part}" if k > 0 else rf"{a}x + {pi_part}"
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.simplify(sp.sqrt(2) / sp.Integer(a))
        id_ar = r"$\sin(x) - \cos(x) = \sqrt{2}\sin\left(x - \frac{\pi}{4}\right)$"
        id_fr = r"$\sin(x) - \cos(x) = \sqrt{2}\sin\left(x - \frac{\pi}{4}\right)$"

    pt_tex = point.point_latex()
    hints = [
        BilingualText(
            ar=rf"ضع تغيير المتغير $t = x - {pt_tex} \to 0$ (أو استعمل المتطابقة المناسبة للزاوية الخاصة ${pt_tex}$).",
            fr=rf"Posez $t = x - {pt_tex} \to 0$ ou factorisez à l'aide des formules d'addition en ${pt_tex}$.",
        ),
        BilingualText(ar=f"استفد من: {id_ar}", fr=f"Utilisez : {id_fr}"),
    ]
    steps = [
        SolutionStep(
            title=BilingualText(ar=f"تغيير المتغير عند الزاوية {pt_tex}", fr=f"Changement de variable en {pt_tex}"),
            explanation=BilingualText(
                ar=rf"نستعمل صيغ الجمع المثلثية: {id_ar}.",
                fr=rf"À l'aide des formules d'addition : {id_fr}.",
            ),
            latex=rf"\lim_{{x \to {pt_tex}}} {expr_tex} \quad \left(\text{{FI }} \frac{{0}}{{0}}\right)",
        ),
        SolutionStep(
            title=BilingualText(ar="تبسيط والمرور إلى النهاية", fr="Simplification et conclusion"),
            explanation=BilingualText(
                ar=r"نختزل أو نقسم على $t \to 0$ باستعمال النهايات الاعتيادية $\frac{\sin t}{t} \to 1$ و $\frac{\cos t - 1}{t} \to 0$:",
                fr=r"En passant à la limite lorsque $t \to 0$ (avec $\frac{\sin t}{t} \to 1$ et $\frac{\cos t - 1}{t} \to 0$) :",
            ),
            latex=rf"\lim_{{x \to {pt_tex}}} {expr_tex} = {value_latex(expected)}",
        ),
    ]
    return _finalize_trig_exercise(
        seed, f"trig_challenge_{sub}", Difficulty.CHALLENGE, point,
        num_expr / den_expr, expr_tex, expected, "0/0", hints, steps,
        ["trig", "special_angle", "addition_formula", "devoir_surveille"],
    )


def generate_trig(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a SymPy-verified 2BAC PC trigonometric limit exercise."""

    def _build(rng: random.Random) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
        if difficulty == Difficulty.BASIC:
            builder = rng.choice([
                _build_basic_sin_tan_ratio,
                _build_basic_one_minus_cos,
                _build_basic_poly_trig_sum,
            ])
            return builder(rng, seed)

        if difficulty == Difficulty.INTERMEDIATE:
            builder = rng.choice([
                _build_inter_order2_combo,
                _build_inter_squeeze_or_recip,
                _build_inter_shift_pi_or_x0,
            ])
            return builder(rng, seed)

        builder = rng.choice([
            _build_challenge_tan_minus_sin,
            _build_challenge_sqrt_one_minus_cos,
            _build_challenge_trig_radical_or_pi_cos,
            _build_challenge_special_angles,
        ])
        return builder(rng, seed)

    return verified_generate(seed, _build)
