"""Procedural CAS-verified generators for 2BAC PC Numerical Sequences (pure calculation & speed)."""

from __future__ import annotations

import math
import random
from typing import Callable

import sympy as sp

from ..bricks import _nonzero_int
from ..formatting import (
    N,
    X,
    poly_latex,
    root_latex,
    signed_combo_latex,
    value_latex,
)
from ..models import (
    AnswerKind,
    ApproachSide,
    BilingualText,
    Chapter,
    Difficulty,
    GeneratedExercise,
    LimitPoint,
    SolutionStep,
    Technique,
)
from ..verifier import _answers_equal


def _verified_seq_generate(
    seed: str,
    builder: Callable[[random.Random], tuple[GeneratedExercise, Callable[[], bool]]],
    max_attempts: int = 25,
) -> GeneratedExercise:
    """Run sequence builder deterministically from seed and enforce CAS verification callback."""
    rng = random.Random(seed)
    for _ in range(max_attempts):
        ex, verify_cb = builder(rng)
        try:
            if verify_cb():
                ex.verified_by_cas = True
                return ex
        except Exception:
            continue
    raise RuntimeError(f"Failed to generate verified sequence exercise for seed={seed}")


def _fmt_frac_or_int(val: sp.Rational | int) -> str:
    r = sp.Rational(val)
    if r.q == 1:
        return str(r.p)
    if r.p < 0:
        return rf"-\frac{{{-r.p}}}{{{r.q}}}"
    return rf"\frac{{{r.p}}}{{{r.q}}}"


def _fmt_pow_base_n(base: sp.Rational | int, shift: int = 0) -> str:
    r = sp.Rational(base)
    exp_tex = "n" if shift == 0 else (f"n+{shift}" if shift > 0 else f"n-{abs(shift)}")
    if r.q == 1 and r.p > 0:
        return rf"{r.p}^{{{exp_tex}}}"
    return rf"\left({_fmt_frac_or_int(r)}\right)^{{{exp_tex}}}"


def _fmt_linear_un(coeff: sp.Rational, const: sp.Rational) -> str:
    """Format a*u_n + b cleanly in LaTeX."""
    if coeff == 1:
        left = "u_n"
    elif coeff == -1:
        left = "-u_n"
    else:
        left = rf"{_fmt_frac_or_int(coeff)} u_n"
    if const == 0:
        return left
    if const > 0:
        return rf"{left} + {_fmt_frac_or_int(const)}"
    return rf"{left} - {_fmt_frac_or_int(-const)}"


def _fmt_affine_un_int(a: int, b: int) -> str:
    """Format integer a*u_n + b cleanly."""
    if a == 0:
        return str(b)
    if a == 1:
        left = "u_n"
    elif a == -1:
        left = "-u_n"
    else:
        left = f"{a}u_n"
    if b == 0:
        return left
    if b > 0:
        return f"{left} + {b}"
    return f"{left} - {-b}"


def _fmt_un_minus_alpha(alpha: int) -> str:
    if alpha == 0:
        return "u_n"
    if alpha > 0:
        return f"u_n - {alpha}"
    return f"u_n + {-alpha}"


# ===========================================================================
# 1. SEQ_LIMITS: Limits of sequences (n -> +oo, powers a^n, squeeze, roots)
# ===========================================================================


def _build_seq_lim_rational(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    p = rng.choice([1, 2, 3])
    q = p if rng.random() < 0.6 else rng.choice([1, 2, 3])
    a = _nonzero_int(rng, -6, 6)
    b = rng.randint(-5, 5)
    c = rng.randint(-5, 5)
    d = _nonzero_int(rng, 1, 6)
    e = rng.randint(1, 6)

    num_e = sp.Integer(a) * N**p + sp.Integer(b) * N + sp.Integer(c)
    den_e = sp.Integer(d) * N**q + sp.Integer(e)
    expr = num_e / den_e

    if p == q:
        expected = sp.Rational(a, d)
    elif p > q:
        expected = sp.oo if a > 0 else -sp.oo
    else:
        expected = sp.Integer(0)

    num_tex = poly_latex(num_e, N)
    den_tex = poly_latex(den_e, N)
    full_tex = rf"\lim_{{n \to +\infty}} \frac{{{num_tex}}}{{{den_tex}}}"
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    steps = [
        SolutionStep(
            title=BilingualText(ar="التعميل بالحد الأكبر درجة في $n$", fr="Factorisation par le terme de plus haut degré en $n$"),
            explanation=BilingualText(
                ar=rf"نأخذ نسبة الحدين الأكبر درجة عند $n \to +\infty$: $\frac{{{a}n^{{{p}}}}}{{{d}n^{{{q}}}}}$.",
                fr=rf"En $+\infty$, la limite est celle du quotient des termes dominants : $\frac{{{a}n^{{{p}}}}}{{{d}n^{{{q}}}}}$.",
            ),
            latex=rf"{full_tex} = \lim_{{n \to +\infty}} \frac{{{a}n^{{{p}}}}}{{{d}n^{{{q}}}}} = {value_latex(expected)}",
        )
    ]

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_LIMITS,
        sub_pattern="seq_lim_rational",
        difficulty=difficulty,
        answer_kind=AnswerKind.CONSTANT,
        prompt=BilingualText(ar="احسب نهاية المتتالية التالية:", fr="Calculer la limite de la suite suivante :"),
        point=point,
        expr_latex=rf"\frac{{{num_tex}}}{{{den_tex}}}",
        full_limit_latex=full_tex,
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo/oo",
        hints=[
            BilingualText(
                ar="عمّل بأكبر قوة لـ $n$ في البسط والمقام ثم استعمل $\\lim_{n \\to +\\infty} \\frac{1}{n^k} = 0$.",
                fr="Factorisez par la plus grande puissance de $n$ au numérateur et au dénominateur.",
            )
        ],
        steps=steps,
        tags=["sequences", "rational_n"],
    )

    def _verify() -> bool:
        return _answers_equal(sp.limit(expr, N, sp.oo), expected)

    return ex, _verify


def _build_seq_lim_geo_power(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    q_choices = [
        sp.Rational(1, 2),
        sp.Rational(2, 3),
        sp.Rational(3, 4),
        sp.Rational(-1, 2),
        sp.Rational(3, 2),
        sp.Rational(5, 4),
        sp.Rational(2, 1),
    ]
    q = rng.choice(q_choices)
    a = _nonzero_int(rng, -5, 5)
    c = rng.randint(-6, 6)

    expr = sp.Integer(a) * (q**N) + sp.Integer(c)
    if abs(q) < 1:
        expected = sp.Integer(c)
        rule_ar = rf"بما أن $-1 < {_fmt_frac_or_int(q)} < 1$ فإن $\lim_{{n \to +\infty}} {_fmt_pow_base_n(q)} = 0$."
        rule_fr = rf"Comme $-1 < {_fmt_frac_or_int(q)} < 1$, on a $\lim_{{n \to +\infty}} {_fmt_pow_base_n(q)} = 0$."
    else:
        expected = sp.oo if a > 0 else -sp.oo
        rule_ar = rf"بما أن ${_fmt_frac_or_int(q)} > 1$ فإن $\lim_{{n \to +\infty}} {_fmt_pow_base_n(q)} = +\infty$ (ومع المعامل ${a}$ تكون النهاية ${value_latex(expected)}$)."
        rule_fr = rf"Comme ${_fmt_frac_or_int(q)} > 1$, on a $\lim_{{n \to +\infty}} {_fmt_pow_base_n(q)} = +\infty$ (avec le signe de ${a}$, la limite est ${value_latex(expected)}$)."

    a_pre = "" if a == 1 else ("-" if a == -1 else rf"{a} \cdot ")
    core = f"{a_pre}{_fmt_pow_base_n(q)}"
    expr_tex = core if c == 0 else signed_combo_latex(core, "+" if c > 0 else "-", str(abs(c)))
    full_tex = rf"\lim_{{n \to +\infty}} \left({expr_tex}\right)" if c != 0 else rf"\lim_{{n \to +\infty}} {expr_tex}"
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    steps = [
        SolutionStep(
            title=BilingualText(ar="نهاية المتتالية الهندسية $q^n$", fr="Limite de la suite géométrique $q^n$"),
            explanation=BilingualText(ar=rule_ar, fr=rule_fr),
            latex=rf"{full_tex} = {value_latex(expected)}",
        )
    ]

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_LIMITS,
        sub_pattern="seq_lim_geo_power",
        difficulty=difficulty,
        answer_kind=AnswerKind.CONSTANT,
        prompt=BilingualText(ar="احسب نهاية المتتالية التالية:", fr="Calculer la limite de la suite suivante :"),
        point=point,
        expr_latex=expr_tex,
        full_limit_latex=full_tex,
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="direct",
        hints=[
            BilingualText(
                ar="قارن الأساس $q$ بـ $1$: إذا كان $|q| < 1$ فإن $q^n \\to 0$، وإذا كان $q > 1$ فإن $q^n \\to +\\infty$.",
                fr="Comparez la raison $q$ à $1$ : si $|q| < 1$ alors $q^n \\to 0$, si $q > 1$ alors $q^n \\to +\\infty$.",
            )
        ],
        steps=steps,
        tags=["sequences", "geometric_power"],
    )

    def _verify() -> bool:
        return _answers_equal(sp.limit(expr, N, sp.oo), expected)

    return ex, _verify


def _build_seq_lim_dominant_exp(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    """(a*p^(n+s1) + b*q^n) / (c*p^n + d*q^(n+s2)) with p > q >= 2, OR hidden base trap 2^(2n) = 4^n."""
    hidden_trap = difficulty == Difficulty.CHALLENGE and rng.random() < 0.55

    if hidden_trap:
        base_root = rng.choice([2, 3])
        p = base_root * base_root  # 4 or 9
        q = rng.choice([2, 3]) if p == 4 else rng.choice([5, 7])
        while q == p:
            q = 3
        s1 = rng.choice([0, 1])
        a = _nonzero_int(rng, -4, 4)
        b = _nonzero_int(rng, -5, 5)
        c = _nonzero_int(rng, 1, 5)
        d = _nonzero_int(rng, -5, 5)

        expr = (sp.Integer(a) * sp.Integer(p) ** (N + s1) + sp.Integer(b) * sp.Integer(q) ** N) / (
            sp.Integer(c) * sp.Integer(base_root) ** (2 * N) + sp.Integer(d) * sp.Integer(q) ** N
        )
        expected = sp.Rational(a * (p**s1), c)

        a_pre = "" if a == 1 else ("-" if a == -1 else rf"{a}\cdot ")
        b_abs = "" if abs(b) == 1 else rf"{abs(b)}\cdot "
        c_pre = "" if c == 1 else rf"{c}\cdot "
        d_abs = "" if abs(d) == 1 else rf"{abs(d)}\cdot "

        num_tex = signed_combo_latex(f"{a_pre}{_fmt_pow_base_n(p, s1)}", "+" if b > 0 else "-", f"{b_abs}{q}^n")
        den_tex = signed_combo_latex(rf"{c_pre}{base_root}^{{2n}}", "+" if d > 0 else "-", f"{d_abs}{q}^n")
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        full_tex = rf"\lim_{{n \to +\infty}} {expr_tex}"

        steps = [
            SolutionStep(
                title=BilingualText(ar=rf"كشف الفخ: ${base_root}^{{2n}} = {p}^n$", fr=rf"Déjouer le piège : ${base_root}^{{2n}} = {p}^n$"),
                explanation=BilingualText(
                    ar=rf"نلاحظ أن ${base_root}^{{2n}} = ({base_root}^2)^n = {p}^n$ وأن ${p}^{{n+{s1}}} = {p**s1}\cdot {p}^n$." if s1 else rf"نلاحظ أن ${base_root}^{{2n}} = ({base_root}^2)^n = {p}^n$.",
                    fr=rf"On remarque que ${base_root}^{{2n}} = ({base_root}^2)^n = {p}^n$ et ${p}^{{n+{s1}}} = {p**s1}\cdot {p}^n$." if s1 else rf"On remarque que ${base_root}^{{2n}} = ({base_root}^2)^n = {p}^n$.",
                ),
                latex=rf"{expr_tex} = \frac{{{a * (p**s1)} {'+' if b > 0 else '-'} {abs(b)}\left(\frac{{{q}}}{{{p}}}\right)^n}}{{{c} {'+' if d > 0 else '-'} {abs(d)}\left(\frac{{{q}}}{{{p}}}\right)^n}}",
            ),
            SolutionStep(
                title=BilingualText(ar="حساب النهاية", fr="Passage à la limite"),
                explanation=BilingualText(
                    ar=rf"بما أن $0 < \frac{{{q}}}{{{p}}} < 1$ فإن $\left(\frac{{{q}}}{{{p}}}\right)^n \to 0$، ومنه:",
                    fr=rf"Comme $0 < \frac{{{q}}}{{{p}}} < 1$, $\left(\frac{{{q}}}{{{p}}}\right)^n \to 0$, d'où :",
                ),
                latex=rf"{full_tex} = {value_latex(expected)}",
            ),
        ]
        sub_pat = "seq_lim_hidden_base_trap"
        hint_ar = rf"انتبه! اكتب ${base_root}^{{2n}}$ على شكل $({base_root}^2)^n = {p}^n$ ثم عمّل بالحد الأسي الأكبر ${p}^n$."
        hint_fr = rf"Attention ! Écrivez ${base_root}^{{2n}} = {p}^n$ puis factorisez par le terme dominant ${p}^n$."
    else:
        p, q = rng.choice([(3, 2), (5, 3), (4, 3), (5, 2), (7, 4)])
        s1 = rng.choice([0, 1, 2]) if difficulty != Difficulty.BASIC else 0
        s2 = rng.choice([0, 1])
        a = _nonzero_int(rng, -4, 4)
        b = _nonzero_int(rng, -5, 5)
        c = _nonzero_int(rng, 1, 5)
        d = _nonzero_int(rng, -5, 5)

        expr = (sp.Integer(a) * sp.Integer(p) ** (N + s1) + sp.Integer(b) * sp.Integer(q) ** N) / (
            sp.Integer(c) * sp.Integer(p) ** N + sp.Integer(d) * sp.Integer(q) ** (N + s2)
        )
        expected = sp.Rational(a * (p**s1), c)

        a_pre = "" if a == 1 else ("-" if a == -1 else rf"{a}\cdot ")
        b_abs = "" if abs(b) == 1 else rf"{abs(b)}\cdot "
        c_pre = "" if c == 1 else rf"{c}\cdot "
        d_abs = "" if abs(d) == 1 else rf"{abs(d)}\cdot "

        num_tex = signed_combo_latex(f"{a_pre}{_fmt_pow_base_n(p, s1)}", "+" if b > 0 else "-", f"{b_abs}{q}^n")
        den_tex = signed_combo_latex(f"{c_pre}{p}^n", "+" if d > 0 else "-", f"{d_abs}{_fmt_pow_base_n(q, s2)}")
        expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
        full_tex = rf"\lim_{{n \to +\infty}} {expr_tex}"

        steps = [
            SolutionStep(
                title=BilingualText(ar=rf"التعميل بالأساس الأكبر ${p}^n$", fr=rf"Factorisation par la base dominante ${p}^n$"),
                explanation=BilingualText(
                    ar=rf"نُعمّل البسط والمقام بـ ${p}^n$ لأن ${p} > {q}$:",
                    fr=rf"On factorise numérateur et dénominateur par ${p}^n$ car ${p} > {q}$ :",
                ),
                latex=rf"{expr_tex} = \frac{{{a * (p**s1)} {'+' if b > 0 else '-'} {abs(b)}\left(\frac{{{q}}}{{{p}}}\right)^n}}{{{c} {'+' if d > 0 else '-'} {abs(d * (q**s2))}\left(\frac{{{q}}}{{{p}}}\right)^n}}",
            ),
            SolutionStep(
                title=BilingualText(ar="المرور إلى النهاية", fr="Passage à la limite"),
                explanation=BilingualText(
                    ar=rf"بما أن $\frac{{{q}}}{{{p}}} \in ]0, 1[$ فإن $\left(\frac{{{q}}}{{{p}}}\right)^n \to 0$:",
                    fr=rf"Comme $\frac{{{q}}}{{{p}}} \in ]0, 1[$, on a $\left(\frac{{{q}}}{{{p}}}\right)^n \to 0$ :",
                ),
                latex=rf"{full_tex} = {value_latex(expected)}",
            ),
        ]
        sub_pat = "seq_lim_dominant_exp"
        hint_ar = rf"فكّك القوى مثل ${p}^{{n+k}} = {p}^k \cdot {p}^n$ ثم عمّل البسط والمقام بأكبر أساس (${p}^n$)."
        hint_fr = rf"Écrivez ${p}^{{n+k}} = {p}^k \cdot {p}^n$ puis factorisez par la plus grande base (${p}^n$)."

    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)
    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_LIMITS,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        answer_kind=AnswerKind.CONSTANT,
        prompt=BilingualText(ar="احسب نهاية المتتالية التالية:", fr="Calculer la limite de la suite suivante :"),
        point=point,
        expr_latex=expr_tex,
        full_limit_latex=full_tex,
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oo/oo",
        hints=[BilingualText(ar=hint_ar, fr=hint_fr)],
        steps=steps,
        tags=["sequences", "dominant_base"],
    )

    def _verify() -> bool:
        return _answers_equal(sp.limit(expr, N, sp.oo), expected)

    return ex, _verify


def _build_seq_lim_squeeze(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    """(a*n + b*(-1)^n + c) / (d*n + e) or (a*n^2 + b*cos(n)) / (d*n^2 + e)."""
    osc_kind = rng.choice(["alt", "cos", "sin"])
    a = _nonzero_int(rng, -5, 5)
    b = _nonzero_int(rng, -6, 6)
    d = _nonzero_int(rng, 1, 5)
    e = rng.randint(1, 6)
    p = 1 if osc_kind == "alt" else rng.choice([1, 2])

    osc_tex = "(-1)^n" if osc_kind == "alt" else (r"\cos(n)" if osc_kind == "cos" else r"\sin(n)")
    osc_expr = (-1) ** N if osc_kind == "alt" else (sp.cos(N) if osc_kind == "cos" else sp.sin(N))

    num_expr = sp.Integer(a) * (N**p) + sp.Integer(b) * osc_expr
    den_expr = sp.Integer(d) * (N**p) + sp.Integer(e)
    expr = num_expr / den_expr
    expected = sp.Rational(a, d)

    np_tex = "n" if p == 1 else f"n^{{{p}}}"
    a_np = poly_latex(sp.Integer(a) * (N**p), N)
    b_abs = "" if abs(b) == 1 else str(abs(b))
    num_tex = signed_combo_latex(a_np, "+" if b > 0 else "-", f"{b_abs}{osc_tex}")
    den_tex = poly_latex(den_expr, N)
    expr_tex = rf"\frac{{{num_tex}}}{{{den_tex}}}"
    full_tex = rf"\lim_{{n \to +\infty}} {expr_tex}"
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    steps = [
        SolutionStep(
            title=BilingualText(ar="التأطير واستعمال مصاديق التقارب (الحصر)", fr="Encadrement et théorème des gendarmes"),
            explanation=BilingualText(
                ar=rf"بما أن $-1 \le {osc_tex} \le 1$ لكل $n \in \mathbb{{N}}$، نقسم البسط والمقام على ${np_tex}$ ونؤطّر الحد $\frac{{{osc_tex}}}{{{np_tex}}}$:",
                fr=rf"Comme $-1 \le {osc_tex} \le 1$ pour tout $n \in \mathbb{{N}}$, on divise par ${np_tex}$ et on encadre $\frac{{{osc_tex}}}{{{np_tex}}}$ :",
            ),
            latex=rf"-\frac{{1}}{{{np_tex}}} \le \frac{{{osc_tex}}}{{{np_tex}}} \le \frac{{1}}{{{np_tex}}} \implies \lim_{{n \to +\infty}} \frac{{{osc_tex}}}{{{np_tex}}} = 0",
        ),
        SolutionStep(
            title=BilingualText(ar="استنتاج النهاية", fr="Conclusion"),
            explanation=BilingualText(
                ar=r"بالمرور للنهاية نحصل على:",
                fr=r"En passant à la limite, il vient :",
            ),
            latex=rf"{full_tex} = \frac{{{a}}}{{{d}}} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_LIMITS,
        sub_pattern="seq_lim_squeeze",
        difficulty=difficulty,
        answer_kind=AnswerKind.CONSTANT,
        prompt=BilingualText(ar="احسب نهاية المتتالية التالية:", fr="Calculer la limite de la suite suivante :"),
        point=point,
        expr_latex=expr_tex,
        full_limit_latex=full_tex,
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="oscillation",
        hints=[
            BilingualText(
                ar=rf"استعمل التأطير $-1 \le {osc_tex} \le 1$ بعد القسمة على ${np_tex}$ لتطبيق مبرهنة الحصر.",
                fr=rf"Utilisez $-1 \le {osc_tex} \le 1$ après division par ${np_tex}$ (théorème des gendarmes).",
            )
        ],
        steps=steps,
        tags=["sequences", "squeeze"],
    )

    def _verify() -> bool:
        exp_f = float(expected)
        for k in (500, 5000):
            v = float(expr.subs(N, k).evalf(20))
            if abs(v - exp_f) > 5e-2:
                return False
        return True

    return ex, _verify


def _build_seq_lim_composition(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    """v_n = f(u_n) where f is ln, exp, or sqrt applied to a convergent sequence."""
    fn_kind = rng.choice(["ln", "exp", "sqrt"])
    p = rng.choice([2, 3, 5])
    a = rng.choice([2, 3, 4, 6])
    c = rng.choice([1, 2, 3])
    b = rng.randint(1, 7)
    d = rng.randint(1, 7)
    while a == c:
        a = rng.choice([2, 3, 4, 5, 6])

    inner = (sp.Integer(a) * (sp.Integer(p) ** N) + sp.Integer(b)) / (
        sp.Integer(c) * (sp.Integer(p) ** N) + sp.Integer(d)
    )
    inner_tex = rf"\frac{{{a}\cdot {p}^n + {b}}}{{{c}\cdot {p}^n + {d}}}" if c > 1 else rf"\frac{{{a}\cdot {p}^n + {b}}}{{{p}^n + {d}}}"
    inner_lim = sp.Rational(a, c)

    if fn_kind == "ln":
        expr = sp.log(inner)
        expr_tex = rf"\ln\left({inner_tex}\right)"
        expected = sp.simplify(sp.log(inner_lim))
        fn_ar, fn_fr = r"$\ln$", r"$\ln$"
    elif fn_kind == "exp":
        expr = sp.exp(inner)
        expr_tex = rf"\exp\left({inner_tex}\right)"
        expected = sp.simplify(sp.exp(inner_lim))
        fn_ar, fn_fr = r"$\exp$", r"$\exp$"
    else:
        expr = sp.sqrt(inner)
        expr_tex = rf"\sqrt{{{inner_tex}}}"
        expected = sp.simplify(sp.sqrt(inner_lim))
        fn_ar, fn_fr = r"$\sqrt{\cdot}$", r"$\sqrt{\cdot}$"

    full_tex = rf"\lim_{{n \to +\infty}} {expr_tex}"
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    steps = [
        SolutionStep(
            title=BilingualText(ar="حساب نهاية المتتالية الداخلية", fr="Limite de la suite intérieure"),
            explanation=BilingualText(
                ar=rf"نُعمّل بـ ${p}^n$ داخل التعبير فنجد:",
                fr=rf"En factorisant par ${p}^n$ à l'intérieur, on obtient :",
            ),
            latex=rf"\lim_{{n \to +\infty}} {inner_tex} = {value_latex(inner_lim)}",
        ),
        SolutionStep(
            title=BilingualText(ar="تطبيق خاصية الاتصال $v_n = f(u_n)$", fr="Composition par une fonction continue $v_n = f(u_n)$"),
            explanation=BilingualText(
                ar=rf"بما أن الدالة {fn_ar} متصلة في ${value_latex(inner_lim)}$ فإن:",
                fr=rf"Comme la fonction {fn_fr} est continue en ${value_latex(inner_lim)}$, on a :",
            ),
            latex=rf"{full_tex} = {value_latex(expected)}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_LIMITS,
        sub_pattern=f"seq_lim_comp_{fn_kind}",
        difficulty=difficulty,
        answer_kind=AnswerKind.CONSTANT,
        prompt=BilingualText(ar="احسب نهاية المتتالية التالية:", fr="Calculer la limite de la suite suivante :"),
        point=point,
        expr_latex=expr_tex,
        full_limit_latex=full_tex,
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="composition",
        hints=[
            BilingualText(
                ar=rf"احسب أولاً نهاية الكسر الداخلي بالتعميل بـ ${p}^n$، ثم استعمل اتصال الدالة {fn_ar}.",
                fr=rf"Calculez d'abord la limite du quotient intérieur en factorisant par ${p}^n$, puis utilisez la continuité de {fn_fr}.",
            )
        ],
        steps=steps,
        tags=["sequences", "composition", fn_kind],
    )

    def _verify() -> bool:
        return _answers_equal(sp.limit(expr, N, sp.oo), expected)

    return ex, _verify


def generate_seq_limits(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    """Generate a CAS-verified sequence limit exercise."""

    def _build(rng: random.Random):
        if difficulty == Difficulty.BASIC:
            builder = rng.choices([_build_seq_lim_rational, _build_seq_lim_geo_power], weights=[0.5, 0.5])[0]
            return builder(rng, seed, difficulty)
        if difficulty == Difficulty.INTERMEDIATE:
            builder = rng.choices(
                [_build_seq_lim_dominant_exp, _build_seq_lim_squeeze, _build_seq_lim_geo_power],
                weights=[0.45, 0.35, 0.20],
            )[0]
            return builder(rng, seed, difficulty)
        builder = rng.choices(
            [_build_seq_lim_dominant_exp, _build_seq_lim_composition, _build_seq_lim_squeeze],
            weights=[0.45, 0.35, 0.20],
        )[0]
        return builder(rng, seed, difficulty)

    return _verified_seq_generate(seed, _build)


# ===========================================================================
# 2. SEQ_ARITH_GEO: Linear recursive u_{n+1} = a*u_n + b
# ===========================================================================


def _build_arith_geo_exercise(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    q_pool_conv = [sp.Rational(1, 2), sp.Rational(1, 3), sp.Rational(2, 3), sp.Rational(3, 4), sp.Rational(1, 4)]
    q_pool_div = [sp.Rational(2, 1), sp.Rational(3, 1), sp.Rational(3, 2)]

    if difficulty == Difficulty.BASIC:
        q = rng.choice(q_pool_conv)
        ask = rng.choice(["ratio", "v0"])
    elif difficulty == Difficulty.INTERMEDIATE:
        ask = rng.choices(["general_un", "limit_un"], weights=[0.65, 0.35])[0]
        q = rng.choice(q_pool_conv + (q_pool_div if ask == "limit_un" else []))
    else:
        ask = rng.choices(["sum_vn", "general_un"], weights=[0.65, 0.35])[0]
        q = rng.choice(q_pool_conv + [sp.Rational(2, 1)])

    alpha = _nonzero_int(rng, -4, 5)
    b = sp.Rational(alpha) * (1 - q)
    v0 = _nonzero_int(rng, -4, 4)
    u0 = alpha + v0

    vn_expr = sp.Integer(v0) * (q**N)
    un_expr = sp.Integer(alpha) + vn_expr
    sum_vn_expr = sp.simplify(sp.Integer(v0) * (1 - q ** (N + 1)) / (1 - q))

    rec_tex = _fmt_linear_un(q, b)
    vn_def_tex = _fmt_un_minus_alpha(alpha)
    system_tex = (
        rf"\begin{{cases}} u_0 = {u0} \\ u_{{n+1}} = {rec_tex} \end{{cases}}, \qquad v_n = {vn_def_tex}"
    )
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    if ask == "ratio":
        expected = q
        ans_kind = AnswerKind.CONSTANT
        prompt = BilingualText(
            ar="المتتالية $(v_n)$ هندسية. احسب أساسها $q$:",
            fr="La suite $(v_n)$ est géométrique. Calculer sa raison $q$ :",
        )
        hints = [
            BilingualText(
                ar=rf"احسب $v_{{n+1}} = u_{{n+1}} - ({alpha})$ وعوّض $u_{{n+1}}$ ثم عمّل لتظهر $v_n$.",
                fr=rf"Calculez $v_{{n+1}} = u_{{n+1}} - ({alpha})$ en remplaçant $u_{{n+1}}$, puis factorisez par $v_n$.",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="حساب $v_{n+1}$ بدلالة $v_n$", fr="Expression de $v_{n+1}$ en fonction de $v_n$"),
                explanation=BilingualText(
                    ar="نعوّض $u_{n+1}$ ونُعمّل بمعامل $u_n$:",
                    fr="On remplace $u_{n+1}$ et on factorise par le coefficient de $u_n$ :",
                ),
                latex=rf"v_{{n+1}} = ({rec_tex}) - ({alpha}) = {_fmt_frac_or_int(q)}({vn_def_tex}) = {_fmt_frac_or_int(q)} v_n \implies q = {_fmt_frac_or_int(q)}",
            )
        ]
        sub_pat = "seq_ag_ratio"
    elif ask == "v0":
        expected = sp.Integer(v0)
        ans_kind = AnswerKind.CONSTANT
        prompt = BilingualText(
            ar="احسب الحد الأول $v_0$ للمتتالية المساعدة $(v_n)$:",
            fr="Calculer le premier terme $v_0$ de la suite auxiliaire $(v_n)$ :",
        )
        hints = [
            BilingualText(
                ar=rf"عوّض $n = 0$ في العلاقة $v_n = {vn_def_tex}$.",
                fr=rf"Remplacez $n = 0$ dans $v_n = {vn_def_tex}$.",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="التعويض المباشر بـ $n = 0$", fr="Calcul direct pour $n = 0$"),
                explanation=BilingualText(
                    ar=f"لدينا $u_0 = {u0}$ ومنه:",
                    fr=f"On a $u_0 = {u0}$, d'où :",
                ),
                latex=rf"v_0 = {u0} - ({alpha}) = {v0}",
            )
        ]
        sub_pat = "seq_ag_v0"
    elif ask == "general_un":
        expected = un_expr
        ans_kind = AnswerKind.EXPRESSION_N
        prompt = BilingualText(
            ar="علماً أن $(v_n)$ هندسية، عبّر عن $u_n$ بدلالة $n$:",
            fr="Sachant que $(v_n)$ est géométrique, exprimer $u_n$ en fonction de $n$ :",
        )
        hints = [
            BilingualText(
                ar=rf"أوجد الأساس $q = {_fmt_frac_or_int(q)}$ و $v_0 = u_0 - ({alpha})$، ثم اكتب $v_n = v_0 \cdot q^n$ واستنتج $u_n = v_n + ({alpha})$.",
                fr=rf"Trouvez la raison $q$ et $v_0$, écrivez $v_n = v_0 q^n$, puis déduisez $u_n = v_n + ({alpha})$.",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="تحديد عناصر المتتالية الهندسية $(v_n)$", fr="Éléments de la suite géométrique $(v_n)$"),
                explanation=BilingualText(
                    ar=rf"نجد $v_{{n+1}} = {_fmt_frac_or_int(q)} v_n$ و $v_0 = {u0} - ({alpha}) = {v0}$، إذن:",
                    fr=rf"On obtient $v_{{n+1}} = {_fmt_frac_or_int(q)} v_n$ et $v_0 = {v0}$, donc :",
                ),
                latex=rf"v_n = {v0} \cdot {_fmt_pow_base_n(q)}",
            ),
            SolutionStep(
                title=BilingualText(ar="استنتاج $u_n$ بدلالة $n$", fr="Déduction de $u_n$ en fonction de $n$"),
                explanation=BilingualText(
                    ar=rf"بما أن $v_n = {vn_def_tex}$ فإن $u_n = {alpha} + v_n$:",
                    fr=rf"Comme $v_n = {vn_def_tex}$, on a $u_n = {alpha} + v_n$ :",
                ),
                latex=rf"u_n = {value_latex(expected)}",
            ),
        ]
        sub_pat = "seq_ag_general_un"
    elif ask == "limit_un":
        expected = sp.Integer(alpha) if abs(q) < 1 else (sp.oo if v0 > 0 else -sp.oo)
        ans_kind = AnswerKind.CONSTANT
        prompt = BilingualText(
            ar="استنتج نهاية المتتالية $(u_n)$ عندما يؤول $n$ إلى $+\\infty$:",
            fr="En déduire $\\lim_{n \\to +\\infty} u_n$ :",
        )
        hints = [
            BilingualText(
                ar="عبّر أولاً عن $u_n = \\alpha + v_0 q^n$ ثم قارن $q$ بـ $1$ (انتبه لإشارة $v_0$ إذا كان $q > 1$!).",
                fr="Exprimez $u_n = \\alpha + v_0 q^n$, puis comparez $q$ à $1$ (attention au signe de $v_0$ si $q > 1$ !).",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="تعبير $u_n$ بدلالة $n$", fr="Expression explicite de $u_n$"),
                explanation=BilingualText(
                    ar=rf"$(v_n)$ هندسية أساسها $q = {_fmt_frac_or_int(q)}$ وحدها الأول $v_0 = {v0}$:",
                    fr=rf"$(v_n)$ est géométrique de raison $q = {_fmt_frac_or_int(q)}$ et de premier terme $v_0 = {v0}$ :",
                ),
                latex=rf"u_n = {value_latex(un_expr)}",
            ),
            SolutionStep(
                title=BilingualText(ar="حساب النهاية عند $+\\infty$", fr="Calcul de la limite en $+\\infty$"),
                explanation=BilingualText(
                    ar=rf"بما أن $|q| < 1$ فإن $q^n \to 0$ ومنه $u_n \to {alpha}$." if abs(q) < 1 else rf"بما أن $q > 1$ فإن $q^n \to +\infty$ ومع إشارة $v_0 = {v0}$ نجد:",
                    fr=rf"Comme $|q| < 1$, $q^n \to 0$ donc $u_n \to {alpha}$." if abs(q) < 1 else rf"Comme $q > 1$, $q^n \to +\infty$ et selon le signe de $v_0 = {v0}$ :",
                ),
                latex=rf"\lim_{{n \to +\infty}} u_n = {value_latex(expected)}",
            ),
        ]
        sub_pat = "seq_ag_limit"
    else:  # sum_vn
        expected = sum_vn_expr
        ans_kind = AnswerKind.EXPRESSION_N
        prompt = BilingualText(
            ar="احسب المجموع $S_n = v_0 + v_1 + \\dots + v_n$ بدلالة $n$:",
            fr="Calculer la somme $S_n = v_0 + v_1 + \\dots + v_n$ en fonction de $n$ :",
        )
        hints = [
            BilingualText(
                ar=r"استعمل صيغة مجموع حدود متتالية هندسية: $S_n = v_0 \cdot \frac{1 - q^{n+1}}{1 - q}$ (انتبه أن عدد الحدود هو $n+1$!).",
                fr=r"Utilisez la formule de somme géométrique : $S_n = v_0 \cdot \frac{1 - q^{n+1}}{1 - q}$ (attention : $n+1$ termes !).",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="عناصر $(v_n)$", fr="Caractéristiques de $(v_n)$"),
                explanation=BilingualText(
                    ar=rf"$(v_n)$ متتالية هندسية أساسها $q = {_fmt_frac_or_int(q)}$ وحدها الأول $v_0 = {v0}$.",
                    fr=rf"$(v_n)$ est géométrique de raison $q = {_fmt_frac_or_int(q)}$ et $v_0 = {v0}$.",
                ),
                latex=rf"S_n = v_0 \cdot \frac{{1 - q^{{n+1}}}}{{1 - q}} = {v0} \cdot \frac{{1 - {_fmt_pow_base_n(q, 1)}}}{{1 - ({_fmt_frac_or_int(q)})}}",
            ),
            SolutionStep(
                title=BilingualText(ar="التبسيط النهائي", fr="Simplification finale"),
                explanation=BilingualText(
                    ar="بعد تبسيط المقام نجد:",
                    fr="Après simplification du dénominateur :",
                ),
                latex=rf"S_n = {value_latex(expected)}",
            ),
        ]
        sub_pat = "seq_ag_sum_vn"

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_ARITH_GEO,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        answer_kind=ans_kind,
        prompt=prompt,
        point=point,
        expr_latex=system_tex,
        full_limit_latex=system_tex,
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="recurrence",
        hints=hints,
        steps=steps,
        tags=["sequences", "arith_geo", ask],
    )

    def _verify() -> bool:
        if sp.simplify(un_expr.subs(N, 0) - u0) != 0:
            return False
        if sp.simplify(un_expr.subs(N, N + 1) - (q * un_expr + b)) != 0:
            return False
        if ask == "limit_un":
            return _answers_equal(sp.limit(un_expr, N, sp.oo), expected)
        if ask == "sum_vn":
            k = sp.Symbol("k", integer=True, nonnegative=True)
            cas_sum = sp.summation(sp.Integer(v0) * (q**k), (k, 0, N))
            return _answers_equal(cas_sum, expected)
        return True

    return ex, _verify


def generate_seq_arith_geo(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    return _verified_seq_generate(seed, lambda rng: _build_arith_geo_exercise(rng, seed, difficulty))


# ===========================================================================
# 3. SEQ_HOMOGRAPHIC: National Exam Classic u_{n+1} = (a*u_n + b)/(c*u_n + d)
# ===========================================================================


def _build_homographic_geo(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    """Homographic recurrence with strictly geometric auxiliary v_n = (u_n - alpha)/(u_n - beta)."""
    alpha = rng.choice([-2, -1, 1, 2, 3])
    beta = rng.choice([v for v in (-3, -2, -1, 0, 1, 2, 3, 4) if v != alpha])
    p_q, q_q = rng.choice([(1, 2), (1, 3), (2, 3), (2, 5), (3, 4), (1, 4)])
    q = sp.Rational(p_q, q_q)

    # From (u_{n+1}-alpha)/(u_{n+1}-beta) = (p_q/q_q)*(u_n-alpha)/(u_n-beta):
    a_raw = q_q * alpha - p_q * beta
    b_raw = alpha * beta * (p_q - q_q)
    c_raw = q_q - p_q
    d_raw = p_q * alpha - q_q * beta

    g = math.gcd(math.gcd(abs(a_raw), abs(b_raw)), math.gcd(abs(c_raw), abs(d_raw)))
    a, b, c, d = a_raw // g, b_raw // g, c_raw // g, d_raw // g
    if c < 0:
        a, b, c, d = -a, -b, -c, -d

    # Choose u0 so v0 < 0 (guarantees 1 - v0*q^n > 1 > 0 for ALL n >= 0, zero pole risk!)
    # Pick midpoint or integer strictly between alpha and beta, or clean u0 with v0 < 0
    lo, hi = min(alpha, beta), max(alpha, beta)
    interior_ints = [k for k in range(lo + 1, hi)]
    if interior_ints:
        u0 = sp.Integer(rng.choice(interior_ints))
    else:
        u0 = sp.Rational(alpha + beta, 2)

    v0 = sp.Rational(u0 - alpha, u0 - beta)
    # Double check pole safety: 1 - v0*q^n != 0 for all n >= 0 (always true when v0 < 0 and q > 0)
    vn_expr = v0 * (q**N)
    un_expr = sp.simplify((sp.Integer(alpha) - sp.Integer(beta) * vn_expr) / (1 - vn_expr))

    num_rec = _fmt_affine_un_int(a, b)
    den_rec = _fmt_affine_un_int(c, d)
    vn_num = _fmt_un_minus_alpha(alpha)
    vn_den = _fmt_un_minus_alpha(beta)

    system_tex = (
        rf"\begin{{cases}} u_0 = {_fmt_frac_or_int(u0)} \\ u_{{n+1}} = \frac{{{num_rec}}}{{{den_rec}}} \end{{cases}}, "
        rf"\qquad v_n = \frac{{{vn_num}}}{{{vn_den}}}"
    )
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    if difficulty == Difficulty.BASIC:
        ask = "ratio"
    elif difficulty == Difficulty.INTERMEDIATE:
        ask = "general_un"
    else:
        ask = rng.choice(["general_un", "limit_un"])

    if ask == "ratio":
        expected = q
        ans_kind = AnswerKind.CONSTANT
        prompt = BilingualText(
            ar="المتتالية $(v_n)$ هندسية. احسب أساسها $q$:",
            fr="La suite $(v_n)$ est géométrique. Déterminer sa raison $q$ :",
        )
        hints = [
            BilingualText(
                ar=rf"احسب $v_{{n+1}} = \frac{{u_{{n+1}} - ({alpha})}}{{u_{{n+1}} - ({beta})}}$ بتوحيد المقامين في البسط والمقام ثم الاختزال.",
                fr=rf"Calculez $v_{{n+1}} = \frac{{u_{{n+1}} - ({alpha})}}{{u_{{n+1}} - ({beta})}}$ en réduisant au même dénominateur puis simplifiez.",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="تعبير $v_{n+1}$ بدلالة $v_n$", fr="Calcul de $v_{n+1}$ en fonction de $v_n$"),
                explanation=BilingualText(
                    ar="بتعويض $u_{n+1}$ وتوحيد المقام في كل من البسط والمقام نحصل على:",
                    fr="En remplaçant $u_{n+1}$ et en réduisant au même dénominateur, on obtient :",
                ),
                latex=rf"v_{{n+1}} = {_fmt_frac_or_int(q)} \cdot \frac{{{vn_num}}}{{{vn_den}}} = {_fmt_frac_or_int(q)} v_n \implies q = {_fmt_frac_or_int(q)}",
            )
        ]
        sub_pat = "seq_homo_geo_ratio"
    elif ask == "general_un":
        expected = un_expr
        ans_kind = AnswerKind.EXPRESSION_N
        prompt = BilingualText(
            ar="علماً أن $(v_n)$ هندسية، عبّر عن $u_n$ بدلالة $n$:",
            fr="Sachant que $(v_n)$ est géométrique, exprimer $u_n$ en fonction de $n$ :",
        )
        hints = [
            BilingualText(
                ar=rf"أولاً احسب الأساس $q$ و $v_0$ لتكتب $v_n = v_0 q^n$.",
                fr=rf"Calculez d'abord la raison $q$ et $v_0$ pour obtenir $v_n = v_0 q^n$.",
            ),
            BilingualText(
                ar=rf"من العلاقة $v_n({vn_den}) = {vn_num}$ اعزل $u_n$: $u_n = \frac{{{alpha} - ({beta}) v_n}}{{1 - v_n}}$.",
                fr=rf"Depuis $v_n({vn_den}) = {vn_num}$, isolez $u_n$ : $u_n = \frac{{{alpha} - ({beta}) v_n}}{{1 - v_n}}$.",
            ),
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="تعبير المتتالية المساعدة $v_n$", fr="Expression de la suite auxiliaire $v_n$"),
                explanation=BilingualText(
                    ar=rf"نجد $v_{{n+1}} = {_fmt_frac_or_int(q)} v_n$ و $v_0 = {_fmt_frac_or_int(v0)}$، إذن:",
                    fr=rf"On trouve $v_{{n+1}} = {_fmt_frac_or_int(q)} v_n$ et $v_0 = {_fmt_frac_or_int(v0)}$, d'où :",
                ),
                latex=rf"v_n = {_fmt_frac_or_int(v0)} \cdot {_fmt_pow_base_n(q)}",
            ),
            SolutionStep(
                title=BilingualText(ar="عزل $u_n$ بدلالة $v_n$ ثم $n$", fr="Isolement de $u_n$ en fonction de $v_n$ puis de $n$"),
                explanation=BilingualText(
                    ar=rf"لدينا $v_n({vn_den}) = {vn_num} \iff u_n(1 - v_n) = {alpha} - ({beta})v_n$:",
                    fr=rf"On a $v_n({vn_den}) = {vn_num} \iff u_n(1 - v_n) = {alpha} - ({beta})v_n$ :",
                ),
                latex=rf"u_n = {value_latex(expected)}",
            ),
        ]
        sub_pat = "seq_homo_geo_isolate"
    else:
        expected = sp.Integer(alpha)
        ans_kind = AnswerKind.CONSTANT
        prompt = BilingualText(
            ar="باستعمال $(v_n)$، احسب النهاية $\\lim_{n \\to +\\infty} u_n$:",
            fr="À l'aide de $(v_n)$, calculer $\\lim_{n \\to +\\infty} u_n$ :",
        )
        hints = [
            BilingualText(
                ar=r"أوجد أساس $(v_n)$؛ إذا كان $|q| < 1$ فإن $v_n \to 0$، وبما أن $u_n = \frac{\alpha - \beta v_n}{1 - v_n}$ استنتج النهاية!",
                fr=r"Déterminez la raison $q$ de $(v_n)$ ; comme $|q| < 1$, $v_n \to 0$, puis déduisez la limite de $u_n$ !",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="نهاية $(v_n)$ ثم استنتاج نهاية $(u_n)$", fr="Limite de $(v_n)$ puis de $(u_n)$"),
                explanation=BilingualText(
                    ar=rf"$(v_n)$ هندسية أساسها $q = {_fmt_frac_or_int(q)} \in ]0, 1[$، إذن $\lim_{{n \to +\infty}} v_n = 0$. ومن علاقة عزل $u_n$:",
                    fr=rf"$(v_n)$ est géométrique de raison $q = {_fmt_frac_or_int(q)} \in ]0, 1[$, donc $\lim_{{n \to +\infty}} v_n = 0$. Ainsi :",
                ),
                latex=rf"u_n = \frac{{{alpha} - ({beta})v_n}}{{1 - v_n}} \xrightarrow[n \to +\infty]{{}} {alpha}",
            )
        ]
        sub_pat = "seq_homo_geo_limit"

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_HOMOGRAPHIC,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        answer_kind=ans_kind,
        prompt=prompt,
        point=point,
        expr_latex=system_tex,
        full_limit_latex=system_tex,
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="homographic",
        hints=hints,
        steps=steps,
        tags=["sequences", "homographic", "bac_national"],
    )

    def _verify() -> bool:
        if sp.simplify(un_expr.subs(N, 0) - u0) != 0:
            return False
        rhs = (sp.Integer(a) * un_expr + sp.Integer(b)) / (sp.Integer(c) * un_expr + sp.Integer(d))
        if sp.simplify(un_expr.subs(N, N + 1) - rhs) != 0:
            return False
        return _answers_equal(sp.limit(un_expr, N, sp.oo), sp.Integer(alpha))

    return ex, _verify


def _build_homographic_arith(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    """Homographic recurrence with arithmetic auxiliary v_n = 1/(u_n - alpha) (double fixed point!)."""
    alpha = rng.choice([-2, -1, 1, 2, 3])
    p_r, q_r = rng.choice([(1, 1), (1, 2), (1, 3), (2, 1), (2, 3), (3, 2)])
    r = sp.Rational(p_r, q_r)

    # 1/(u_{n+1}-alpha) = 1/(u_n-alpha) + p_r/q_r => u_{n+1} = ((q_r + p_r*alpha)*u_n - p_r*alpha^2) / (p_r*u_n + q_r - p_r*alpha)
    a_raw = q_r + p_r * alpha
    b_raw = -p_r * (alpha**2)
    c_raw = p_r
    d_raw = q_r - p_r * alpha
    g = math.gcd(math.gcd(abs(a_raw), abs(b_raw)), math.gcd(abs(c_raw), abs(d_raw)))
    a, b, c, d = a_raw // g, b_raw // g, c_raw // g, d_raw // g
    if c < 0:
        a, b, c, d = -a, -b, -c, -d

    u0 = alpha + 1  # v0 = 1 > 0 => v_n = 1 + n*r > 0 for all n >= 0!
    v0 = sp.Integer(1)
    vn_expr = v0 + N * r
    un_expr = sp.simplify(sp.Integer(alpha) + sp.Integer(1) / vn_expr)

    num_rec = _fmt_affine_un_int(a, b)
    den_rec = _fmt_affine_un_int(c, d)
    vn_den = _fmt_un_minus_alpha(alpha)
    system_tex = (
        rf"\begin{{cases}} u_0 = {u0} \\ u_{{n+1}} = \frac{{{num_rec}}}{{{den_rec}}} \end{{cases}}, "
        rf"\qquad v_n = \frac{{1}}{{{vn_den}}}"
    )
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    if difficulty == Difficulty.BASIC:
        expected = r
        ans_kind = AnswerKind.CONSTANT
        prompt = BilingualText(
            ar="المتتالية $(v_n)$ حسابية. احسب أساسها $r$:",
            fr="La suite $(v_n)$ est arithmétique. Calculer sa raison $r$ :",
        )
        hints = [
            BilingualText(
                ar=rf"احسب $v_{{n+1}} - v_n = \frac{{1}}{{u_{{n+1}} - ({alpha})}} - \frac{{1}}{{{vn_den}}}$ وستجد عدداً ثابتاً $r$.",
                fr=rf"Calculez $v_{{n+1}} - v_n = \frac{{1}}{{u_{{n+1}} - ({alpha})}} - \frac{{1}}{{{vn_den}}}$ : le résultat est constant.",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="حساب الفرق $v_{n+1} - v_n$", fr="Calcul de la différence $v_{n+1} - v_n$"),
                explanation=BilingualText(
                    ar="بتعويض $u_{n+1}$ وتبسيط المقلوب نحصل على:",
                    fr="En substituant $u_{n+1}$ et en inversant la fraction, on obtient :",
                ),
                latex=rf"v_{{n+1}} = \frac{{1}}{{{vn_den}}} + {_fmt_frac_or_int(r)} = v_n + {_fmt_frac_or_int(r)} \implies r = {_fmt_frac_or_int(r)}",
            )
        ]
        sub_pat = "seq_homo_arith_diff"
    else:
        expected = un_expr
        ans_kind = AnswerKind.EXPRESSION_N
        prompt = BilingualText(
            ar="علماً أن $(v_n)$ حسابية، عبّر عن $u_n$ بدلالة $n$:",
            fr="Sachant que $(v_n)$ est arithmétique, exprimer $u_n$ en fonction de $n$ :",
        )
        hints = [
            BilingualText(
                ar=rf"أوجد الأساس $r$ و $v_0 = 1$ لتكتب $v_n = 1 + n r$، ثم استنتج $u_n = {alpha} + \frac{{1}}{{v_n}}$.",
                fr=rf"Trouvez la raison $r$ et $v_0 = 1$ pour écrire $v_n = 1 + nr$, puis déduisez $u_n = {alpha} + \frac{{1}}{{v_n}}$.",
            )
        ]
        steps = [
            SolutionStep(
                title=BilingualText(ar="تعبير المتتالية الحسابية $v_n$", fr="Expression de la suite arithmétique $v_n$"),
                explanation=BilingualText(
                    ar=rf"$(v_n)$ حسابية أساسها $r = {_fmt_frac_or_int(r)}$ وحدها الأول $v_0 = 1$، إذن:",
                    fr=rf"$(v_n)$ est arithmétique de raison $r = {_fmt_frac_or_int(r)}$ et $v_0 = 1$, donc :",
                ),
                latex=rf"v_n = {value_latex(vn_expr)}",
            ),
            SolutionStep(
                title=BilingualText(ar="استنتاج $u_n$ بدلالة $n$", fr="Déduction de $u_n$"),
                explanation=BilingualText(
                    ar=rf"بما أن $v_n = \frac{{1}}{{{vn_den}}}$ فإن $u_n = {alpha} + \frac{{1}}{{v_n}}$:",
                    fr=rf"Comme $v_n = \frac{{1}}{{{vn_den}}}$, on a $u_n = {alpha} + \frac{{1}}{{v_n}}$ :",
                ),
                latex=rf"u_n = {value_latex(expected)}",
            ),
        ]
        sub_pat = "seq_homo_arith_isolate"

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_HOMOGRAPHIC,
        sub_pattern=sub_pat,
        difficulty=difficulty,
        answer_kind=ans_kind,
        prompt=prompt,
        point=point,
        expr_latex=system_tex,
        full_limit_latex=system_tex,
        answer_latex=value_latex(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="homographic_arith",
        hints=hints,
        steps=steps,
        tags=["sequences", "homographic", "arithmetic_aux", "bac_national"],
    )

    def _verify() -> bool:
        if sp.simplify(un_expr.subs(N, 0) - u0) != 0:
            return False
        rhs = (sp.Integer(a) * un_expr + sp.Integer(b)) / (sp.Integer(c) * un_expr + sp.Integer(d))
        if sp.simplify(un_expr.subs(N, N + 1) - rhs) != 0:
            return False
        return _answers_equal(sp.limit(un_expr, N, sp.oo), sp.Integer(alpha))

    return ex, _verify


def generate_seq_homographic(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    def _build(rng: random.Random):
        if rng.random() < 0.65:
            return _build_homographic_geo(rng, seed, difficulty)
        return _build_homographic_arith(rng, seed, difficulty)

    return _verified_seq_generate(seed, _build)


# ===========================================================================
# 4. SEQ_FIXED_POINT: u_{n+1} = f(u_n) -> solve f(l) = l in I, and threshold rank n_0
# ===========================================================================


def _build_fixed_point_sqrt(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    """u_{n+1} = sqrt(a*u_n + b) convergent on [0, +oo[ with clean integer fixed point l > 0."""
    l_root = rng.choice([2, 3, 4, 5, 6])
    neg_mag = rng.choice([1, 2, 3, 4])
    while neg_mag == l_root:
        neg_mag = rng.choice([1, 2, 3, 4])

    # (l - l_root)(l + neg_mag) = l^2 - (l_root - neg_mag)*l - l_root*neg_mag = 0
    a = l_root - neg_mag
    b = l_root * neg_mag
    if a <= 0:
        # Ensure a > 0 so f is increasing on [0, +oo[
        neg_mag = rng.randint(1, l_root - 1)
        a = l_root - neg_mag
        b = l_root * neg_mag

    inner_tex = f"{a}u_n + {b}" if a > 1 else f"u_n + {b}"
    system_tex = rf"u_{{n+1}} = {root_latex(inner_tex, 2)}, \quad \forall n \in \mathbb{{N}}:\; u_n \in [0, +\infty["
    expected = sp.Integer(l_root)
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    steps = [
        SolutionStep(
            title=BilingualText(ar="حل المعادلة $f(l) = l$", fr="Résolution de l'équation $f(l) = l$"),
            explanation=BilingualText(
                ar="بما أن $(u_n)$ متقاربة و $f$ متصلة على $[0, +\\infty[$، فإن نهايتها $l \\ge 0$ تحقق $f(l) = l$:",
                fr="Comme $(u_n)$ converge et $f$ est continue sur $[0, +\\infty[$, sa limite $l \\ge 0$ vérifie $f(l) = l$ :",
            ),
            latex=rf"\sqrt{{{a}l + {b}}} = l \iff l^2 - {a}l - {b} = 0 \iff (l - {l_root})(l + {neg_mag}) = 0",
        ),
        SolutionStep(
            title=BilingualText(ar="اختيار الحل المنتمي للمجال $[0, +\\infty[$", fr="Sélection de la solution dans $[0, +\\infty[$"),
            explanation=BilingualText(
                ar=f"بما أن $l \\ge 0$ فإن الحل السالب $-{neg_mag}$ مرفوض، ومنه:",
                fr=f"Comme $l \\ge 0$, la racine négative $-{neg_mag}$ est écartée, d'où :",
            ),
            latex=rf"\lim_{{n \to +\infty}} u_n = {l_root}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_FIXED_POINT,
        sub_pattern="seq_fp_sqrt",
        difficulty=difficulty,
        answer_kind=AnswerKind.CONSTANT,
        prompt=BilingualText(
            ar="نفترض أن المتتالية $(u_n)$ متقاربة. حدّد نهايتها $l$:",
            fr="On admet que $(u_n)$ est convergente. Déterminer sa limite $l$ :",
        ),
        point=point,
        expr_latex=system_tex,
        full_limit_latex=system_tex,
        answer_latex=str(l_root),
        answer_sympy_str=str(l_root),
        indeterminate_form="fixed_point",
        hints=[
            BilingualText(
                ar="حلّ المعادلة $f(l) = l$ بالرفع إلى المربع، ثم احتفظ بالحل الموجب المنتمي للمجال $[0, +\\infty[$.",
                fr="Résolvez $f(l) = l$ en élevant au carré, puis gardez uniquement la solution dans $[0, +\\infty[$.",
            )
        ],
        steps=steps,
        tags=["sequences", "fixed_point", "continuity"],
    )

    def _verify() -> bool:
        sols = sp.solve(sp.sqrt(sp.Integer(a) * X + sp.Integer(b)) - X, X)
        pos_sols = [s for s in sols if s.is_real and s >= 0]
        return len(pos_sols) == 1 and pos_sols[0] == expected

    return ex, _verify


def _build_fixed_point_interval_trap(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    """u_{n+1} = (a*u_n + b)/(c*u_n + d) with two roots r1 < r2, only ONE in given interval I!"""
    r1 = rng.choice([-3, -2, -1, 0, 1])
    r2 = rng.choice([r1 + 2, r1 + 3, r1 + 4])
    keep_upper = rng.choice([True, False])
    expected = sp.Integer(r2 if keep_upper else r1)

    # Choose c = 1, build (x - r1)(x - r2) = x^2 - (r1+r2)x + r1*r2 = 0
    # Rewrite as x*(x + d) = a*x + b  =>  a - d = r1 + r2, b = -r1*r2
    d = rng.choice([2, 3, 4])
    a = r1 + r2 + d
    b = -r1 * r2
    num_tex = _fmt_affine_un_int(a, b)
    den_tex = _fmt_affine_un_int(1, d)

    if keep_upper:
        lo_bound = r1 + 1
        interval_tex = rf"[{lo_bound}, +\infty["
    else:
        hi_bound = r2 - 1
        interval_tex = rf"[{r1 - 2}, {hi_bound}]"

    system_tex = rf"u_{{n+1}} = \frac{{{num_tex}}}{{{den_tex}}}, \quad \forall n \in \mathbb{{N}}:\; u_n \in {interval_tex}"
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    steps = [
        SolutionStep(
            title=BilingualText(ar="حل المعادلة $f(l) = l$", fr="Résolution de $f(l) = l$"),
            explanation=BilingualText(
                ar="نحل المعادلة $\\frac{" + num_tex.replace("u_n", "l") + "}{" + den_tex.replace("u_n", "l") + "} = l$:",
                fr="On résout l'équation $\\frac{" + num_tex.replace("u_n", "l") + "}{" + den_tex.replace("u_n", "l") + "} = l$ :",
            ),
            latex=rf"l^2 - ({r1 + r2})l + ({r1 * r2}) = 0 \iff (l - ({r1}))(l - ({r2})) = 0 \implies l \in \{{{r1},\; {r2}\}}",
        ),
        SolutionStep(
            title=BilingualText(ar="استعمال التأطير لاختيار النهاية الصحيحة", fr="Sélection grâce à l'intervalle d'encadrement"),
            explanation=BilingualText(
                ar=rf"بما أن $u_n \in {interval_tex}$ لكل $n$، فإن $l \in {interval_tex}$؛ ومنه الحل الوحيد المقبول هو:",
                fr=rf"Comme $u_n \in {interval_tex}$ pour tout $n$, on a $l \in {interval_tex}$, donc l'unique limite possible est :",
            ),
            latex=rf"\lim_{{n \to +\infty}} u_n = {expected}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_FIXED_POINT,
        sub_pattern="seq_fp_interval_trap",
        difficulty=difficulty,
        answer_kind=AnswerKind.CONSTANT,
        prompt=BilingualText(
            ar="نفترض أن المتتالية $(u_n)$ متقاربة. حدّد نهايتها $l$ بالانتباه للمجال:",
            fr="On admet que $(u_n)$ est convergente. Déterminer sa limite $l$ en tenant compte de l'intervalle :",
        ),
        point=point,
        expr_latex=system_tex,
        full_limit_latex=system_tex,
        answer_latex=str(expected),
        answer_sympy_str=str(expected),
        indeterminate_form="fixed_point",
        hints=[
            BilingualText(
                ar=rf"حلّ $f(l) = l$ (معادلة من الدرجة الثانية) ثم اختر الجذر الوحيد الذي ينتمي إلى ${interval_tex}$.",
                fr=rf"Résolvez $f(l) = l$ (équation du second degré) puis retenez la seule racine appartenant à ${interval_tex}$.",
            )
        ],
        steps=steps,
        tags=["sequences", "fixed_point", "interval_trap"],
    )

    def _verify() -> bool:
        eq = (sp.Integer(a) * X + sp.Integer(b)) / (X + sp.Integer(d)) - X
        sols = set(sp.solve(eq, X))
        return sols == {sp.Integer(r1), sp.Integer(r2)} and expected in sols

    return ex, _verify


def _build_threshold_rank(
    rng: random.Random,
    seed: str,
    difficulty: Difficulty,
) -> tuple[GeneratedExercise, Callable[[], bool]]:
    """Find smallest integer n_0 >= 0 such that |u_n - alpha| < 1/M for u_n = alpha + c*(1/b)^n."""
    alpha = rng.randint(-3, 5)
    b = rng.choice([2, 3])
    c = rng.choice([1, 2, 3, 4])
    target_n0 = rng.randint(3, 6) if b == 2 else rng.randint(2, 4)

    # We want c / b^n < 1 / M  <=>  b^n > c * M
    # Choose M so b^(target_n0 - 1) <= c * M < b^target_n0!
    lo_cm = b ** (target_n0 - 1)
    hi_cm = (b ** target_n0) - 1
    valid_ms = [m for m in range(1, hi_cm + 1) if lo_cm <= c * m <= hi_cm]
    if not valid_ms:
        c = 1
        valid_ms = [m for m in range(lo_cm, hi_cm + 1)]
    M = rng.choice(valid_ms)

    un_expr = sp.Integer(alpha) + sp.Integer(c) * (sp.Rational(1, b) ** N)
    c_pre = "" if c == 1 else rf"{c}\cdot "
    un_tex = rf"{alpha} + {c_pre}\left(\frac{{1}}{{{b}}}\right)^n" if alpha != 0 else rf"{c_pre}\left(\frac{{1}}{{{b}}}\right)^n"
    eps_tex = rf"\frac{{1}}{{{M}}}" if M > 1 else "1"
    system_tex = rf"u_n = {un_tex}, \qquad \left|u_n - ({alpha})\right| < {eps_tex}" if alpha < 0 else rf"u_n = {un_tex}, \qquad |u_n - {alpha}| < {eps_tex}"
    expected = sp.Integer(target_n0)
    point = LimitPoint(kind="+oo", side=ApproachSide.POS_INF)

    steps = [
        SolutionStep(
            title=BilingualText(ar="تبسيط المتراجحة بدلالة $n$", fr="Simplification de l'inéquation en $n$"),
            explanation=BilingualText(
                ar=rf"نعوّض $u_n$ فنجد المتراجحة المكافئة:",
                fr=rf"En remplaçant $u_n$, on obtient l'inéquation équivalente :",
            ),
            latex=rf"{c_pre}\frac{{1}}{{{b}^n}} < {eps_tex} \iff {b}^n > {c * M}",
        ),
        SolutionStep(
            title=BilingualText(ar="تحديد أصغر عدد صحيح طبيعي $n_0$", fr="Détermination du plus petit entier naturel $n_0$"),
            explanation=BilingualText(
                ar=rf"بما أن ${b}^{{{target_n0 - 1}}} = {b**(target_n0 - 1)} \le {c * M} < {b**target_n0} = {b}^{{{target_n0}}}$ (أو باستعمال $\ln$):",
                fr=rf"Comme ${b}^{{{target_n0 - 1}}} = {b**(target_n0 - 1)} \le {c * M} < {b**target_n0} = {b}^{{{target_n0}}}$ (ou avec $\ln$) :",
            ),
            latex=rf"n \ge {target_n0} \implies n_0 = {target_n0}",
        ),
    ]

    ex = GeneratedExercise(
        seed=seed,
        chapter=Chapter.SEQUENCES,
        technique=Technique.SEQ_FIXED_POINT,
        sub_pattern="seq_fp_threshold_rank",
        difficulty=difficulty,
        answer_kind=AnswerKind.CONSTANT,
        prompt=BilingualText(
            ar="حدّد أصغر عدد صحيح طبيعي $n_0$ بحيث تتحقق المتراجحة لكل $n \\ge n_0$:",
            fr="Déterminer le plus petit entier naturel $n_0$ tel que l'inégalité soit vérifiée pour tout $n \\ge n_0$ :",
        ),
        point=point,
        expr_latex=system_tex,
        full_limit_latex=system_tex,
        answer_latex=str(target_n0),
        answer_sympy_str=str(target_n0),
        indeterminate_form="threshold_rank",
        hints=[
            BilingualText(
                ar=rf"حوّل المتراجحة إلى الشكل ${b}^n > {c * M}$ ثم أوجد أصغر قوة لـ ${b}$ أكبر قطعا من ${c * M}$.",
                fr=rf"Transformez l'inéquation en ${b}^n > {c * M}$ puis trouvez la plus petite puissance de ${b}$ strictement supérieure à ${c * M}$.",
            )
        ],
        steps=steps,
        tags=["sequences", "threshold_rank"],
    )

    def _verify() -> bool:
        val_at = abs(un_expr.subs(N, target_n0) - alpha)
        val_prev = abs(un_expr.subs(N, target_n0 - 1) - alpha)
        eps = sp.Rational(1, M)
        return bool(val_at < eps and val_prev >= eps)

    return ex, _verify


def generate_seq_fixed_point(seed: str, difficulty: Difficulty) -> GeneratedExercise:
    def _build(rng: random.Random):
        if difficulty == Difficulty.BASIC:
            return _build_fixed_point_sqrt(rng, seed, difficulty)
        if difficulty == Difficulty.INTERMEDIATE:
            builder = rng.choices([_build_fixed_point_interval_trap, _build_fixed_point_sqrt], weights=[0.7, 0.3])[0]
            return builder(rng, seed, difficulty)
        builder = rng.choices([_build_threshold_rank, _build_fixed_point_interval_trap], weights=[0.65, 0.35])[0]
        return builder(rng, seed, difficulty)

    return _verified_seq_generate(seed, _build)
