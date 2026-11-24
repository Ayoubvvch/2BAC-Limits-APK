"""Generator for 2BAC PC natural logarithm (ln) limits with SymPy CAS verification."""

from __future__ import annotations

import random

import sympy as sp

from ..bricks import (
    _nonzero_int,
    make_ln_brick,
    make_poly_brick,
    make_sqrt_conj_brick,
)
from ..formatting import (
    X,
    limit_statement_latex,
    linear_factor_latex,
    poly_latex,
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


def _build_ln_croissance_inf(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """(a*ln(b*x + c) + d*x^p) / (e*x^p + f) or a*ln(b*x+c)/x^p at +oo."""
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    a = _nonzero_int(rng, -5, 5)
    b = rng.choice([1, 2, 3])
    c = rng.randint(0, 6)
    p = rng.choice([1, 2]) if difficulty == Difficulty.BASIC else rng.choice([1, 2, 3])

    arg_expr = sp.Integer(b) * X + sp.Integer(c)
    arg_tex = poly_latex(arg_expr)
    ln_tex = rf"\ln({arg_tex})"

    if difficulty == Difficulty.BASIC and rng.random() < 0.55:
        d = rng.randint(-5, 5)
        num_expr = sp.Integer(a) * sp.log(arg_expr) + sp.Integer(d)
        den_expr = X**p
        expr = num_expr / den_expr

        a_prefix = "" if a == 1 else ("-" if a == -1 else str(a))
        num_tex = f"{a_prefix}{ln_tex}"
        if d != 0:
            num_tex = signed_combo_latex(num_tex, "+" if d > 0 else "-", str(abs(d)))
        den_tex = "x" if p == 1 else f"x^{{{p}}}"
        full_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.Integer(0)

        steps = [
            SolutionStep(
                title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Forme indéterminée"),
                explanation=BilingualText(
                    ar=r"عند $+\infty$ لدينا $\ln(" + arg_tex + r") \to +\infty$ و $" + den_tex + r" \to +\infty$، إذن شكل غير محدد $\frac{\infty}{\infty}$.",
                    fr=r"En $+\infty$, $\ln(" + arg_tex + r") \to +\infty$ et $" + den_tex + r" \to +\infty$, d'où la FI $\frac{\infty}{\infty}$.",
                ),
                latex=rf"\lim_{{x \to +\infty}} {full_tex} \quad \left(\text{{FI }} \frac{{\infty}}{{\infty}}\right)",
            ),
            SolutionStep(
                title=BilingualText(ar="استعمال النهاية الأساسية للمقارنة", fr="Croissance comparée logarithmique"),
                explanation=BilingualText(
                    ar=rf"نستعمل النهاية الأساسية $\lim_{{t \to +\infty}} \frac{{\ln t}}{{t^{{{p}}}}} = 0$ (بعد وضع $t = {arg_tex} \to +\infty$):",
                    fr=rf"On utilise la limite usuelle $\lim_{{t \to +\infty}} \frac{{\ln t}}{{t^{{{p}}}}} = 0$ (en posant $t = {arg_tex} \to +\infty$) :",
                ),
                latex=rf"\lim_{{x \to +\infty}} {full_tex} = 0",
            ),
        ]
        sub_pat = "ln_croissance_inf_zero"
    else:
        # (d*x^p + a*ln(bx + c)) / (e*x^p + f) -> d / e
        d = _nonzero_int(rng, -4, 4)
        e = _nonzero_int(rng, -4, 4)
        f = rng.randint(-5, 5)
        num_expr = sp.Integer(d) * X**p + sp.Integer(a) * sp.log(arg_expr)
        den_expr = sp.Integer(e) * X**p + sp.Integer(f)
        expr = num_expr / den_expr

        xp_tex = "x" if p == 1 else f"x^{{{p}}}"
        d_xp = poly_latex(sp.Integer(d) * X**p)
        a_abs = "" if abs(a) == 1 else str(abs(a))
        num_tex = signed_combo_latex(d_xp, "+" if a > 0 else "-", f"{a_abs}{ln_tex}")
        den_tex = poly_latex(den_expr)
        full_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.Rational(d, e)

        den_sign = "+" if f > 0 else ("-" if f < 0 else "")
        den_tail = "" if f == 0 else rf"\frac{{{abs(f)}}}{{{xp_tex}}}"
        steps = [
            SolutionStep(
                title=BilingualText(ar="التعميل بالحد الأكبر درجة", fr="Factorisation par le terme dominant"),
                explanation=BilingualText(
                    ar=rf"نُعمّل البسط والمقام بـ ${xp_tex}$ لاستغلال $\lim_{{x \to +\infty}} \frac{{\ln({arg_tex})}}{{{xp_tex}}} = 0$:",
                    fr=rf"On factorise numérateur et dénominateur par ${xp_tex}$ pour utiliser $\lim_{{x \to +\infty}} \frac{{\ln({arg_tex})}}{{{xp_tex}}} = 0$ :",
                ),
                latex=rf"{full_tex} = \frac{{{d} {'+' if a > 0 else '-'} {a_abs}\frac{{{ln_tex}}}{{{xp_tex}}}}}{{{e} {den_sign} {den_tail}}}",
            ),
            SolutionStep(
                title=BilingualText(ar="المرور إلى النهاية", fr="Passage à la limite"),
                explanation=BilingualText(
                    ar=rf"بما أن $\frac{{\ln({arg_tex})}}{{{xp_tex}}} \to 0$ فإن النهاية تساوي:",
                    fr=rf"Comme $\frac{{\ln({arg_tex})}}{{{xp_tex}}} \to 0$, on obtient :",
                ),
                latex=rf"\lim_{{x \to +\infty}} {full_tex} = {value_latex(expected)}",
            ),
        ]
        sub_pat = "ln_croissance_inf_ratio"

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.LN_LIMITS,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo/oo",
        hints=[
            BilingualText(
                ar=r"عمّل بأكبر قوة لـ $x$ واستعمل النهاية الأساسية $\lim_{x \to +\infty} \frac{\ln x}{x^n} = 0$.",
                fr=r"Factorisez par la plus grande puissance de $x$ et utilisez $\lim_{x \to +\infty} \frac{\ln x}{x^n} = 0$.",
            )
        ],
        steps=steps,
        tags=["ln", "croissance_comparee"],
    )
    return ex, expr, expected


def _build_ln_zero_plus(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """x^p * (a*ln(b*x) + c) + d as x -> 0+."""
    point = LimitPoint(kind="finite", value=0, side=ApproachSide.RIGHT)
    a = _nonzero_int(rng, -5, 5)
    b = rng.choice([1, 2, 3])
    c = rng.randint(-4, 4)
    d = rng.randint(-5, 5)
    p = 1 if difficulty == Difficulty.BASIC else rng.choice([1, 2])
    ln_pwr = 1 if difficulty != Difficulty.CHALLENGE else rng.choice([1, 2])

    bx_tex = "x" if b == 1 else f"{b}x"
    xp_tex = "x" if p == 1 else f"x^{{{p}}}"

    if ln_pwr == 2:
        expr = sp.Integer(a) * (X**p) * (sp.log(X) ** 2) + sp.Integer(d)
        a_pre = "" if a == 1 else ("-" if a == -1 else str(a))
        core_tex = rf"{a_pre}{xp_tex}(\ln x)^2"
        full_tex = core_tex if d == 0 else signed_combo_latex(core_tex, "+" if d > 0 else "-", str(abs(d)))
        expected = sp.Integer(d)
        hint_ar = r"اكتب $x(\ln x)^2 = 4\left(\sqrt{x}\ln(\sqrt{x})\right)^2$ واستعمل $\lim_{t \to 0^+} t\ln t = 0$."
        hint_fr = r"Écrivez $x(\ln x)^2 = 4\left(\sqrt{x}\ln(\sqrt{x})\right)^2$ et utilisez $\lim_{t \to 0^+} t\ln t = 0$."
        step_expl_ar = r"بوضع $t = \sqrt{x} \to 0^+$ نجد $x(\ln x)^2 = 4(t \ln t)^2 \to 0$، ومنه:"
        step_expl_fr = r"En posant $t = \sqrt{x} \to 0^+$, on a $x(\ln x)^2 = 4(t \ln t)^2 \to 0$, d'où :"
        sub_pat = "ln_zero_plus_sq"
    else:
        inner = sp.Integer(a) * sp.log(sp.Integer(b) * X) + sp.Integer(c)
        expr = (X**p) * inner + sp.Integer(d)
        a_pre = "" if a == 1 else ("-" if a == -1 else str(a))
        inner_tex = f"{a_pre}\\ln({bx_tex})" if c == 0 else signed_combo_latex(f"{a_pre}\\ln({bx_tex})", "+" if c > 0 else "-", str(abs(c)))
        core_tex = rf"{xp_tex}\left({inner_tex}\right)" if c != 0 else rf"{a_pre}{xp_tex}\ln({bx_tex})"
        full_tex = core_tex if d == 0 else signed_combo_latex(core_tex, "+" if d > 0 else "-", str(abs(d)))
        expected = sp.Integer(d)
        hint_ar = r"انشر التعبير واستعمل النهاية الأساسية $\lim_{x \to 0^+} x^n \ln x = 0$."
        hint_fr = r"Développez puis utilisez la limite usuelle $\lim_{x \to 0^+} x^n \ln x = 0$."
        step_expl_ar = rf"نستعمل $\ln({bx_tex}) = \ln {b} + \ln x$ والنهاية الأساسية $\lim_{{x \to 0^+}} {xp_tex}\ln x = 0$:" if b > 1 else rf"ننشر ونستعمل النهاية الأساسية $\lim_{{x \to 0^+}} {xp_tex}\ln x = 0$:"
        step_expl_fr = rf"On écrit $\ln({bx_tex}) = \ln {b} + \ln x$ puis on utilise $\lim_{{x \to 0^+}} {xp_tex}\ln x = 0$ :" if b > 1 else rf"On développe puis on utilise $\lim_{{x \to 0^+}} {xp_tex}\ln x = 0$ :"
        sub_pat = "ln_zero_plus_basic"

    steps = [
        SolutionStep(
            title=BilingualText(ar="التحقق من الشكل غير المحدد عند $0^+$", fr="Forme indéterminée en $0^+$"),
            explanation=BilingualText(
                ar=r"عند $0^+$ لدينا $x \to 0^+$ و $\ln x \to -\infty$، ما يعطي شكلاً غير محدد $0 \times (-\infty)$.",
                fr=r"En $0^+$, $x \to 0^+$ et $\ln x \to -\infty$, soit une FI $0 \times (-\infty)$.",
            ),
            latex=rf"\lim_{{x \to 0^+}} \left({full_tex}\right) \quad (\text{{FI }} 0 \times \infty)",
        ),
        SolutionStep(
            title=BilingualText(ar="تطبيق النهاية اللوغاريتمية الأساسية", fr="Application de la limite usuelle"),
            explanation=BilingualText(ar=step_expl_ar, fr=step_expl_fr),
            latex=rf"\lim_{{x \to 0^+}} \left({full_tex}\right) = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.LN_LIMITS,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="0 * oo",
        hints=[BilingualText(ar=hint_ar, fr=hint_fr)],
        steps=steps,
        tags=["ln", "zero_plus"],
    )
    return ex, expr, expected


def _build_ln_taux_accroissement(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Finite 0/0 limit using ln VanishingBrick over linear/poly/sqrt brick."""
    x0 = rng.choice([0, 1]) if difficulty == Difficulty.BASIC else rng.choice([-2, -1, 0, 1, 2, 3])
    poly_deg = 1 if difficulty == Difficulty.BASIC else rng.choice([1, 2])
    num = make_ln_brick(rng, x0, poly_deg=poly_deg)

    if difficulty == Difficulty.BASIC:
        den = make_linear_brick(x0, coeff=_nonzero_int(rng, -3, 3))
        sub_pat = "ln_taux_basic"
    elif difficulty == Difficulty.INTERMEDIATE:
        den = make_poly_brick(rng, x0, min_deg=2, max_deg=2, coeff_bound=3)
        sub_pat = "ln_taux_poly"
    else:
        den = make_sqrt_conj_brick(rng, x0, two_roots=False, poly_deg=1)
        sub_pat = "ln_over_sqrt"

    if difficulty != Difficulty.BASIC and rng.random() < 0.35:
        num, den = den, num

    ex = assemble_vanishing_fraction(
        seed=seed,
        technique=Technique.LN_LIMITS,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        num=num,
        den=den,
        tags=["ln", "taux_accroissement"],
    )
    return ex, num.expr / den.expr, sp.sympify(ex.answer_sympy_str)


def _build_ln_inf_minus_inf(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """a*x^p - b*ln(c*x + d) at +oo (factor by x^p -> +-oo)."""
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    a = _nonzero_int(rng, -4, 4)
    # Choose b with same sign as a so a*x^p - b*ln(...) is truly oo - oo!
    b = rng.choice([1, 2, 3, 4]) * (1 if a > 0 else -1)
    c = rng.choice([1, 2, 3])
    d = rng.randint(1, 5)
    p = rng.choice([1, 2])

    arg_expr = sp.Integer(c) * X + sp.Integer(d)
    arg_tex = poly_latex(arg_expr)
    expr = sp.Integer(a) * (X**p) - sp.Integer(b) * sp.log(arg_expr)

    xp_tex = "x" if p == 1 else f"x^{{{p}}}"
    left_tex = poly_latex(sp.Integer(a) * (X**p))
    b_abs = "" if abs(b) == 1 else str(abs(b))
    full_tex = signed_combo_latex(left_tex, "-" if b > 0 else "+", rf"{b_abs}\ln({arg_tex})")
    expected = sp.oo if a > 0 else -sp.oo

    steps = [
        SolutionStep(
            title=BilingualText(ar="التحقق من الشكل غير المحدد", fr="Forme indéterminée"),
            explanation=BilingualText(
                ar=r"عند $+\infty$ نحصل على الشكل غير المحدد $\infty - \infty$.",
                fr=r"En $+\infty$, on obtient une forme indéterminée $\infty - \infty$.",
            ),
            latex=rf"\lim_{{x \to +\infty}} \left({full_tex}\right) \quad (\text{{FI }} \infty - \infty)",
        ),
        SolutionStep(
            title=BilingualText(ar=rf"التعميل بـ ${xp_tex}$ والمقارنة", fr=rf"Factorisation par ${xp_tex}$ et croissance comparée"),
            explanation=BilingualText(
                ar=rf"نُعمّل بـ ${xp_tex}$ ونستعمل $\lim_{{x \to +\infty}} \frac{{\ln({arg_tex})}}{{{xp_tex}}} = 0$:",
                fr=rf"On factorise par ${xp_tex}$ et on utilise $\lim_{{x \to +\infty}} \frac{{\ln({arg_tex})}}{{{xp_tex}}} = 0$ :",
            ),
            latex=rf"{full_tex} = {xp_tex}\left({a} {'-' if b > 0 else '+'} {b_abs}\frac{{\ln({arg_tex})}}{{{xp_tex}}}\right) \to {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.LN_LIMITS,
        sub_pattern="ln_inf_minus_inf",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo",
        hints=[
            BilingualText(
                ar=rf"عمّل بـ ${xp_tex}$ لتحويل $\infty - \infty$ إلى جداء ثم طبّق $\frac{{\ln x}}{{{xp_tex}}} \to 0$.",
                fr=rf"Factorisez par ${xp_tex}$ puis utilisez $\lim_{{x \to +\infty}} \frac{{\ln x}}{{{xp_tex}}} = 0$.",
            )
        ],
        steps=steps,
        tags=["ln", "inf_minus_inf"],
    )
    return ex, expr, expected


def _build_ln_ratio_of_logs(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """ln(a*x^p + b*x + c) / ln(d*x^q + e) as x -> +oo -> p / q."""
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    p = rng.choice([2, 3, 4])
    q = rng.choice([1, 2, 3])
    while p == q:
        q = rng.choice([1, 2, 3])
    a = rng.choice([1, 2, 3])
    b = rng.randint(-4, 4)
    c = rng.randint(1, 5)
    d = rng.choice([1, 2, 3])
    e = rng.randint(1, 6)

    p1 = sp.Integer(a) * X**p + sp.Integer(b) * X + sp.Integer(c)
    p2 = sp.Integer(d) * X**q + sp.Integer(e)
    expr = sp.log(p1) / sp.log(p2)

    p1_tex, p2_tex = poly_latex(p1), poly_latex(p2)
    full_tex = rf"\frac{{\ln({p1_tex})}}{{\ln({p2_tex})}}"
    expected = sp.Rational(p, q)

    steps = [
        SolutionStep(
            title=BilingualText(ar="التعميل بالحد الأكبر داخل كل لوغاريتم", fr="Factorisation par le terme dominant dans chaque ln"),
            explanation=BilingualText(
                ar=rf"عند $+\infty$ نكتب $\ln({p1_tex}) = {p}\ln x + \ln\left({a} + \dots\right)$ و $\ln({p2_tex}) = {q}\ln x + \ln\left({d} + \dots\right)$.",
                fr=rf"En $+\infty$, on factorise par $x^{{{p}}}$ et $x^{{{q}}}$ dans chaque $\ln$ : $\ln({p1_tex}) = {p}\ln x + \ln({a} + \dots)$ et $\ln({p2_tex}) = {q}\ln x + \ln({d} + \dots)$.",
            ),
            latex=rf"{full_tex} = \frac{{{p} + \frac{{\ln({a} + \dots)}}{{\ln x}}}}{{{q} + \frac{{\ln({d} + \dots)}}{{\ln x}}}}",
        ),
        SolutionStep(
            title=BilingualText(ar="الاختزال بـ $\\ln x$ وحساب النهاية", fr="Simplification par $\\ln x$ et conclusion"),
            explanation=BilingualText(
                ar=r"بما أن $\ln x \to +\infty$ فإن الكسور الإضافية تؤول إلى $0$ ونحصل على:",
                fr=r"Comme $\ln x \to +\infty$, les termes résiduels tendent vers $0$ et il vient :",
            ),
            latex=rf"\lim_{{x \to +\infty}} {full_tex} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.LN_LIMITS,
        sub_pattern="ln_ratio_of_logs",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo/oo",
        hints=[
            BilingualText(
                ar=r"عمّل بأكبر قوة لـ $x$ داخل كل $\ln$ واستعمل $\ln(A \cdot B) = \ln A + \ln B$ ثم اختزل بـ $\ln x$.",
                fr=r"Factorisez par le terme de plus haut degré dans chaque $\ln$, utilisez $\ln(AB)=\ln A + \ln B$ puis simplifiez par $\ln x$.",
            )
        ],
        steps=steps,
        tags=["ln", "oo_over_oo"],
    )
    return ex, expr, expected


def _build_ln_x_diff_inf(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """x * (ln(k*x + a) - ln(k*x + b)) as x -> +oo -> (a - b) / k (classic 2BAC DS challenge!)."""
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    k = rng.choice([1, 2, 3])
    a = rng.randint(-5, 6)
    b = rng.randint(-5, 6)
    while a == b:
        b = rng.randint(-5, 6)

    u1 = sp.Integer(k) * X + sp.Integer(a)
    u2 = sp.Integer(k) * X + sp.Integer(b)
    expr = X * (sp.log(u1) - sp.log(u2))

    u1_tex, u2_tex = poly_latex(u1), poly_latex(u2)
    full_tex = rf"x\left(\ln({u1_tex}) - \ln({u2_tex})\right)"
    expected = sp.Rational(a - b, k)
    diff_ab = a - b

    steps = [
        SolutionStep(
            title=BilingualText(ar="تحويل فرق اللوغاريتمين إلى لوغاريتم خارج", fr="Propriété $\\ln A - \\ln B = \\ln(A/B)$"),
            explanation=BilingualText(
                ar=r"عند $+\infty$ لدينا $\infty \times 0$. نجمع اللوغاريتمين ثم نُظهر الشكل $\ln(1 + u)$:",
                fr=r"En $+\infty$, on a une FI $\infty \times 0$. On regroupe les logarithmes pour faire apparaître $\ln(1 + u)$ :",
            ),
            latex=rf"{full_tex} = x\ln\left(\frac{{{u1_tex}}}{{{u2_tex}}}\right) = x\ln\left(1 + \frac{{{diff_ab}}}{{{u2_tex}}}\right)",
        ),
        SolutionStep(
            title=BilingualText(ar=r"استعمال $\lim_{u \to 0} \frac{\ln(1+u)}{u} = 1$", fr=r"Utilisation de $\lim_{u \to 0} \frac{\ln(1+u)}{u} = 1$"),
            explanation=BilingualText(
                ar=rf"نضع $u = \frac{{{diff_ab}}}{{{u2_tex}}} \to 0$ عند $x \to +\infty$ فنكتب:",
                fr=rf"En posant $u = \frac{{{diff_ab}}}{{{u2_tex}}} \to 0$ lorsque $x \to +\infty$ :",
            ),
            latex=rf"\lim_{{x \to +\infty}} \frac{{{diff_ab}x}}{{{u2_tex}}} \cdot \frac{{\ln(1 + u)}}{{u}} = \frac{{{diff_ab}}}{{{k}}} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.LN_LIMITS,
        sub_pattern="ln_x_times_diff",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo * 0",
        hints=[
            BilingualText(
                ar=r"استعمل الخاصية $\ln A - \ln B = \ln\left(\frac{A}{B}\right)$ واكتب الكسر على شكل $1 + u(x)$ حيث $u(x) \to 0$.",
                fr=r"Utilisez $\ln A - \ln B = \ln(A/B)$ puis écrivez le quotient sous la forme $1 + u(x)$ avec $u(x) \to 0$.",
            )
        ],
        steps=steps,
        tags=["ln", "oo_times_0", "devoir_surveille"],
    )
    return ex, expr, expected


def generate_ln_limits(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a SymPy-verified 2BAC PC natural logarithm limit exercise."""

    def _build(rng: random.Random):
        if difficulty == Difficulty.BASIC:
            builder = rng.choices(
                [_build_ln_croissance_inf, _build_ln_zero_plus, _build_ln_taux_accroissement],
                weights=[0.38, 0.32, 0.30],
            )[0]
            return builder(rng, seed, difficulty)
        if difficulty == Difficulty.INTERMEDIATE:
            builder = rng.choices(
                [
                    _build_ln_inf_minus_inf,
                    _build_ln_ratio_of_logs,
                    _build_ln_taux_accroissement,
                    _build_ln_croissance_inf,
                ],
                weights=[0.28, 0.27, 0.25, 0.20],
            )[0]
            return builder(rng, seed, difficulty)
        builder = rng.choices(
            [
                _build_ln_x_diff_inf,
                _build_ln_taux_accroissement,
                _build_ln_zero_plus,
                _build_ln_ratio_of_logs,
            ],
            weights=[0.34, 0.26, 0.22, 0.18],
        )[0]
        return builder(rng, seed, difficulty)

    return verified_generate(seed, _build)
