"""Generator for 2BAC PC exponential (e^x) limits with SymPy CAS verification."""

from __future__ import annotations

import random

import sympy as sp

from ..bricks import (
    _nonzero_int,
    make_exp_brick,
    make_ln_brick,
    make_poly_brick,
    make_trig_brick,
)
from ..formatting import (
    X,
    limit_statement_latex,
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


def _exp_tex(k: int) -> str:
    if k == 1:
        return "e^{x}"
    if k == -1:
        return "e^{-x}"
    return rf"e^{{{k}x}}"


def _build_exp_croissance(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Basic/Inter croissance comparée: e^(kx)/x^p at +oo OR (a*x^p + b)*e^(kx) at -oo."""
    kind = rng.choice(["pos_inf_ratio", "neg_inf_prod"])
    k = rng.choice([1, 2]) if difficulty == Difficulty.BASIC else rng.choice([1, 2, 3])
    p = rng.choice([1, 2]) if difficulty == Difficulty.BASIC else rng.choice([1, 2, 3])

    if kind == "pos_inf_ratio":
        point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
        a = _nonzero_int(rng, -4, 4)
        c = rng.randint(-5, 5)
        expr = (sp.Integer(a) * sp.exp(sp.Integer(k) * X) + sp.Integer(c)) / (X**p)
        a_pre = "" if a == 1 else ("-" if a == -1 else str(a))
        num_tex = f"{a_pre}{_exp_tex(k)}"
        if c != 0:
            num_tex = signed_combo_latex(num_tex, "+" if c > 0 else "-", str(abs(c)))
        den_tex = "x" if p == 1 else f"x^{{{p}}}"
        full_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        expected = sp.oo if a > 0 else -sp.oo

        steps = [
            SolutionStep(
                title=BilingualText(ar="النهاية الأساسية للدالة الأسية", fr="Croissance comparée exponentielle"),
                explanation=BilingualText(
                    ar=rf"عند $+\infty$ لدينا شكل غير محدد $\frac{{\infty}}{{\infty}}$. نستعمل $\lim_{{t \to +\infty}} \frac{{e^t}}{{t^{{{p}}}}} = +\infty$ مع الإشارة ${'+' if a > 0 else '-'}$:",
                    fr=rf"En $+\infty$, on a une FI $\frac{{\infty}}{{\infty}}$. On utilise $\lim_{{t \to +\infty}} \frac{{e^t}}{{t^{{{p}}}}} = +\infty$ avec le signe ${'+' if a > 0 else '-'}$ :",
                ),
                latex=rf"\lim_{{x \to +\infty}} {full_tex} = {value_latex(expected)}",
            )
        ]
        sub_pat = "exp_croissance_pos_inf"
        fi = "oo/oo"
        hint_ar = rf"استعمل النهاية الأساسية $\lim_{{x \to +\infty}} \frac{{e^x}}{{x^{{{p}}}}} = +\infty$ وانتبه لإشارة المعامل ${a}$."
        hint_fr = rf"Utilisez $\lim_{{x \to +\infty}} \frac{{e^x}}{{x^{{{p}}}}} = +\infty$ en faisant attention au signe de ${a}$."
    else:
        point = LimitPoint(kind="-oo", side=ApproachSide.NEG_INF)
        a = _nonzero_int(rng, -4, 4)
        b = rng.randint(-4, 4)
        c = rng.randint(-5, 5)
        poly_e = sp.Integer(a) * (X**p) + sp.Integer(b)
        expr = poly_e * sp.exp(sp.Integer(k) * X) + sp.Integer(c)

        poly_tex = poly_latex(poly_e)
        core_tex = rf"\left({poly_tex}\right){_exp_tex(k)}" if b != 0 else rf"{poly_tex}{_exp_tex(k)}"
        full_tex = core_tex if c == 0 else signed_combo_latex(core_tex, "+" if c > 0 else "-", str(abs(c)))
        expected = sp.Integer(c)

        steps = [
            SolutionStep(
                title=BilingualText(ar="التحقق من الشكل غير المحدد عند $-\\infty$", fr="Forme indéterminée en $-\\infty$"),
                explanation=BilingualText(
                    ar=rf"عند $-\infty$ لدينا ${poly_tex} \to \pm\infty$ و ${_exp_tex(k)} \to 0$ (شكل $\infty \times 0$).",
                    fr=rf"En $-\infty$, on obtient une FI $\infty \times 0$ car ${_exp_tex(k)} \to 0$.",
                ),
                latex=rf"\lim_{{x \to -\infty}} \left({full_tex}\right) \quad (\text{{FI }} \infty \times 0)",
            ),
            SolutionStep(
                title=BilingualText(ar="استعمال $\\lim_{t \\to -\\infty} t^n e^t = 0$", fr="Application de $\\lim_{t \\to -\\infty} t^n e^t = 0$"),
                explanation=BilingualText(
                    ar=r"ننشر ونستعمل النهاية الأساسية $\lim_{t \to -\infty} t^n e^t = 0$:",
                    fr=r"En développant et en utilisant $\lim_{t \to -\infty} t^n e^t = 0$ :",
                ),
                latex=rf"\lim_{{x \to -\infty}} \left({full_tex}\right) = {value_latex(expected)}",
            ),
        ]
        sub_pat = "exp_croissance_neg_inf"
        fi = "oo * 0"
        hint_ar = r"انشر التعبير واستعمل النهاية الأساسية $\lim_{x \to -\infty} x^n e^x = 0$."
        hint_fr = r"Développez puis appliquez la limite usuelle $\lim_{x \to -\infty} x^n e^x = 0$."

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.EXP_LIMITS,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form=fi,
        hints=[BilingualText(ar=hint_ar, fr=hint_fr)],
        steps=steps,
        tags=["exp", "croissance_comparee"],
    )
    return ex, expr, expected


def _build_exp_taux_accroissement(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """Finite 0/0 limit using exp VanishingBrick."""
    x0 = 0 if difficulty == Difficulty.BASIC else rng.choice([-2, -1, 0, 1, 2])
    num = make_exp_brick(rng, x0)
    if difficulty == Difficulty.BASIC:
        den = make_linear_brick(x0, coeff=_nonzero_int(rng, -4, 4))
        sub_pat = "exp_taux_basic"
    elif difficulty == Difficulty.INTERMEDIATE:
        den = make_poly_brick(rng, x0, min_deg=2, max_deg=2, coeff_bound=3)
        sub_pat = "exp_taux_poly"
    else:
        den = rng.choice([make_ln_brick(rng, x0, poly_deg=1), make_trig_brick(rng, x0)])
        sub_pat = f"exp_over_{den.family}"

    if difficulty != Difficulty.BASIC and rng.random() < 0.35:
        num, den = den, num

    ex = assemble_vanishing_fraction(
        seed=seed,
        technique=Technique.EXP_LIMITS,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        num=num,
        den=den,
        tags=["exp", "taux_accroissement"],
    )
    return ex, num.expr / den.expr, sp.sympify(ex.answer_sympy_str)


def _build_exp_rational_inf(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """(a*e^(2x) + b*e^x + c) / (d*e^(2x) + f*e^x + g) at +oo OR -oo (classic Bac trap!)."""
    inf_kind = rng.choice(["+oo", "-oo"])
    point = LimitPoint(kind=inf_kind, side=ApproachSide.POS_INF if inf_kind == "+oo" else ApproachSide.NEG_INF)

    a = _nonzero_int(rng, -5, 5)
    b = rng.randint(-5, 5)
    c = _nonzero_int(rng, -5, 5)
    d = _nonzero_int(rng, -5, 5)
    f = rng.randint(-5, 5)
    g = _nonzero_int(rng, -5, 5)

    num_expr = sp.Integer(a) * sp.exp(2 * X) + sp.Integer(b) * sp.exp(X) + sp.Integer(c)
    den_expr = sp.Integer(d) * sp.exp(2 * X) + sp.Integer(f) * sp.exp(X) + sp.Integer(g)
    expr = num_expr / den_expr

    def _fmt_exp_poly(c2: int, c1: int, c0: int) -> str:
        t2 = f"{'' if c2 == 1 else '-' if c2 == -1 else c2}e^{{2x}}"
        res = t2
        if c1 != 0:
            t1 = f"{'' if abs(c1) == 1 else abs(c1)}e^{{x}}"
            res = signed_combo_latex(res, "+" if c1 > 0 else "-", t1)
        if c0 != 0:
            res = signed_combo_latex(res, "+" if c0 > 0 else "-", str(abs(c0)))
        return res

    num_tex = _fmt_exp_poly(a, b, c)
    den_tex = _fmt_exp_poly(d, f, g)
    full_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"

    if inf_kind == "+oo":
        expected = sp.Rational(a, d)
        fi = "oo/oo"
        steps = [
            SolutionStep(
                title=BilingualText(ar=r"التعميل بـ $e^{2x}$ عند $+\infty$", fr=r"Factorisation par $e^{2x}$ en $+\infty$"),
                explanation=BilingualText(
                    ar=r"بما أن $x \to +\infty$ فإن $e^{-x} \to 0$ و $e^{-2x} \to 0$. نُعمّل البسط والمقام بالحد المهيمن $e^{2x}$:",
                    fr=r"En $+\infty$, $e^{-x} \to 0$ et $e^{-2x} \to 0$. On factorise par le terme dominant $e^{2x}$ :",
                ),
                latex=rf"\lim_{{x \to +\infty}} {full_tex} = \frac{{{a}}}{{{d}}} = {value_latex(expected)}",
            )
        ]
        hint_ar = r"عند $+\infty$ عمّل البسط والمقام بالحد الأسي الأكبر $e^{2x}$."
        hint_fr = r"En $+\infty$, factorisez numérateur et dénominateur par $e^{2x}$."
    else:
        expected = sp.Rational(c, g)
        fi = "direct"
        steps = [
            SolutionStep(
                title=BilingualText(ar=r"فخّ $-\infty$: ليس شكلاً غير محدد!", fr=r"Piège en $-\infty$ : pas de forme indéterminée !"),
                explanation=BilingualText(
                    ar=r"انتبه! عند $-\infty$ لدينا $\lim_{x \to -\infty} e^x = 0$ و $\lim_{x \to -\infty} e^{2x} = 0$، لذا التعويض مباشر دون تعميل:",
                    fr=r"Attention ! En $-\infty$, $e^x \to 0$ et $e^{2x} \to 0$, donc le calcul est direct sans factorisation :",
                ),
                latex=rf"\lim_{{x \to -\infty}} {full_tex} = \frac{{{c}}}{{{g}}} = {value_latex(expected)}",
            )
        ]
        hint_ar = r"انتبه لنقطة النهاية ($-\infty$)! ما هي نهاية $e^x$ عندما يؤول $x$ إلى $-\infty$؟"
        hint_fr = r"Attention à la borne ($-\infty$) ! Quelle est la limite de $e^x$ en $-\infty$ ?"

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.EXP_LIMITS,
        sub_pattern=f"exp_rational_{inf_kind}",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form=fi,
        hints=[BilingualText(ar=hint_ar, fr=hint_fr)],
        steps=steps,
        tags=["exp", "sign_trap" if inf_kind == "-oo" else "dominant_exp"],
    )
    return ex, expr, expected


def _build_exp_inf_minus_poly(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """a*e^(kx) - b*x^p + c at +oo -> +-oo by factoring e^(kx)."""
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    a = _nonzero_int(rng, -4, 4)
    b = rng.choice([1, 2, 3, 4]) * (1 if a > 0 else -1)
    k = rng.choice([1, 2])
    p = rng.choice([1, 2, 3])
    c = rng.randint(-4, 4)

    expr = sp.Integer(a) * sp.exp(sp.Integer(k) * X) - sp.Integer(b) * (X**p) + sp.Integer(c)
    a_pre = "" if a == 1 else ("-" if a == -1 else str(a))
    xp_tex = "x" if p == 1 else f"x^{{{p}}}"
    b_abs = "" if abs(b) == 1 else str(abs(b))
    full_tex = signed_combo_latex(f"{a_pre}{_exp_tex(k)}", "-" if b > 0 else "+", f"{b_abs}{xp_tex}")
    if c != 0:
        full_tex = signed_combo_latex(full_tex, "+" if c > 0 else "-", str(abs(c)))
    expected = sp.oo if a > 0 else -sp.oo

    steps = [
        SolutionStep(
            title=BilingualText(ar=rf"التعميل بـ ${_exp_tex(k)}$ عند $+\infty$", fr=rf"Factorisation par ${_exp_tex(k)}$ en $+\infty$"),
            explanation=BilingualText(
                ar=rf"لدينا شكل غير محدد $\infty - \infty$. نُعمّل بـ ${_exp_tex(k)}$ ونستعمل $\lim_{{x \to +\infty}} \frac{{{xp_tex}}}{{{_exp_tex(k)}}} = 0$:",
                fr=rf"Forme indéterminée $\infty - \infty$. On factorise par ${_exp_tex(k)}$ et on utilise $\lim_{{x \to +\infty}} \frac{{{xp_tex}}}{{{_exp_tex(k)}}} = 0$ :",
            ),
            latex=rf"{full_tex} = {_exp_tex(k)}\left({a} {'-' if b > 0 else '+'} {b_abs}\frac{{{xp_tex}}}{{{_exp_tex(k)}}} \dots\right) \to {value_latex(expected)}",
        )
    ]

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.EXP_LIMITS,
        sub_pattern="exp_inf_minus_poly",
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo - oo",
        hints=[
            BilingualText(
                ar=rf"عمّل بالحد المهيمن ${_exp_tex(k)}$ واستعمل $\lim_{{x \to +\infty}} \frac{{x^{{{p}}}}}{{{_exp_tex(k)}}} = 0$.",
                fr=rf"Factorisez par ${_exp_tex(k)}$ puis utilisez $\lim_{{x \to +\infty}} \frac{{x^{{{p}}}}}{{{_exp_tex(k)}}} = 0$.",
            )
        ],
        steps=steps,
        tags=["exp", "inf_minus_inf"],
    )
    return ex, expr, expected


def _build_exp_ln_combo_challenge(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, sp.Expr, sp.Expr]:
    """SUPER classic 2BAC DS limit: ln(a*e^(kx) + b) - k*x as x -> +oo -> ln(a), OR x*(e^(a/x) - 1) -> a."""
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    if rng.random() < 0.6:
        a = rng.choice([2, 3, 4, 5, 7])
        b = rng.randint(1, 8)
        k = rng.choice([1, 2, 3])
        expr = sp.log(sp.Integer(a) * sp.exp(sp.Integer(k) * X) + sp.Integer(b)) - sp.Integer(k) * X
        kx_tex = "x" if k == 1 else f"{k}x"
        full_tex = rf"\ln\left({a}{_exp_tex(k)} + {b}\right) - {kx_tex}"
        expected = sp.log(sp.Integer(a))

        steps = [
            SolutionStep(
                title=BilingualText(ar=rf"التعميل بـ ${_exp_tex(k)}$ داخل اللوغاريتم", fr=rf"Factorisation par ${_exp_tex(k)}$ dans le logarithme"),
                explanation=BilingualText(
                    ar=rf"لدينا $\infty - \infty$. نُعمّل بـ ${_exp_tex(k)}$ داخل $\ln$ ثم نستعمل $\ln(e^{{{kx_tex}}} \cdot U) = {kx_tex} + \ln(U)$:",
                    fr=rf"FI $\infty - \infty$. On factorise par ${_exp_tex(k)}$ dans $\ln$ puis on utilise $\ln(e^{{{kx_tex}}} U) = {kx_tex} + \ln U$ :",
                ),
                latex=rf"\ln\left({a}{_exp_tex(k)} + {b}\right) - {kx_tex} = \ln\left({_exp_tex(k)}\left({a} + {b}e^{{-{kx_tex}}}\right)\right) - {kx_tex} = \ln\left({a} + {b}e^{{-{kx_tex}}}\right)",
            ),
            SolutionStep(
                title=BilingualText(ar="حساب النهاية عند $+\\infty$", fr="Calcul de la limite en $+\\infty$"),
                explanation=BilingualText(
                    ar=rf"بما أن $e^{{-{kx_tex}}} \to 0$ عند $+\infty$، نجد:",
                    fr=rf"Comme $e^{{-{kx_tex}}} \to 0$ en $+\infty$, on obtient :",
                ),
                latex=rf"\lim_{{x \to +\infty}} \left({full_tex}\right) = \ln({a})",
            ),
        ]
        sub_pat = "exp_ln_asymptote_combo"
        fi = "oo - oo"
        hint_ar = rf"عمّل بـ ${_exp_tex(k)}$ داخل اللوغاريتم لتختزل ${kx_tex}$ مع $-{kx_tex}$!"
        hint_fr = rf"Factorisez par ${_exp_tex(k)}$ dans le $\ln$ pour simplifier ${kx_tex} - {kx_tex}$ !"
    else:
        a = _nonzero_int(rng, -6, 6)
        b = rng.randint(-4, 4)
        expr = X * (sp.exp(sp.Integer(a) / X) - sp.Integer(1)) + sp.Integer(b)
        frac_tex = rf"\frac{{{a}}}{{x}}" if a > 0 else rf"-\frac{{{-a}}}{{x}}"
        core_tex = rf"x\left(e^{{{frac_tex}}} - 1\right)"
        full_tex = core_tex if b == 0 else signed_combo_latex(core_tex, "+" if b > 0 else "-", str(abs(b)))
        expected = sp.Integer(a + b)

        steps = [
            SolutionStep(
                title=BilingualText(ar=r"تغيير المتغير $t = \frac{1}{x} \to 0^+$", fr=r"Changement de variable $t = \frac{1}{x} \to 0^+$"),
                explanation=BilingualText(
                    ar=r"لدينا $\infty \times 0$. نضع $t = \frac{1}{x}$؛ عندما $x \to +\infty$ فإن $t \to 0^+$:",
                    fr=r"FI $\infty \times 0$. En posant $t = \frac{1}{x} \to 0^+$ lorsque $x \to +\infty$ :",
                ),
                latex=rf"\lim_{{x \to +\infty}} {core_tex} = \lim_{{t \to 0^+}} \frac{{e^{{{a}t}} - 1}}{{t}} = {a}",
            ),
            SolutionStep(
                title=BilingualText(ar="النتيجة النهائية", fr="Conclusion"),
                explanation=BilingualText(
                    ar=f"بإضافة الحد الثابت نحصل على:",
                    fr=f"En ajoutant la constante :",
                ),
                latex=rf"\lim_{{x \to +\infty}} \left({full_tex}\right) = {value_latex(expected)}",
            ),
        ]
        sub_pat = "exp_x_times_e_inv_x"
        fi = "oo * 0"
        hint_ar = r"ضع تغيير المتغير $t = \frac{1}{x} \to 0^+$ لاستعمال $\lim_{t \to 0} \frac{e^{at} - 1}{t} = a$."
        hint_fr = r"Posez $t = \frac{1}{x} \to 0^+$ pour utiliser $\lim_{t \to 0} \frac{e^{at} - 1}{t} = a$."

    ex = GeneratedExercise(
        seed=seed,
        technique=Technique.EXP_LIMITS,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        point=point,
        expr_latex=full_tex,
        full_limit_latex=limit_statement_latex(full_tex, point),
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form=fi,
        hints=[BilingualText(ar=hint_ar, fr=hint_fr)],
        steps=steps,
        tags=["exp", "devoir_surveille"],
    )
    return ex, expr, expected


def generate_exp_limits(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a SymPy-verified 2BAC PC exponential limit exercise."""

    def _build(rng: random.Random):
        if difficulty == Difficulty.BASIC:
            builder = rng.choices(
                [_build_exp_croissance, _build_exp_taux_accroissement],
                weights=[0.55, 0.45],
            )[0]
            return builder(rng, seed, difficulty)
        if difficulty == Difficulty.INTERMEDIATE:
            builder = rng.choices(
                [_build_exp_rational_inf, _build_exp_inf_minus_poly, _build_exp_taux_accroissement, _build_exp_croissance],
                weights=[0.35, 0.25, 0.22, 0.18],
            )[0]
            return builder(rng, seed, difficulty)
        builder = rng.choices(
            [_build_exp_ln_combo_challenge, _build_exp_taux_accroissement, _build_exp_rational_inf],
            weights=[0.50, 0.30, 0.20],
        )[0]
        return builder(rng, seed, difficulty)

    return verified_generate(seed, _build)
