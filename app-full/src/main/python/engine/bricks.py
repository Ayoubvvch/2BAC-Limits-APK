"""Composable Vanishing Bricks (lim -> 0 at x0) used across standalone and hybrid generators."""

from __future__ import annotations

import math
import random
from dataclasses import dataclass, field

import sympy as sp

from .formatting import (
    RootN,
    X,
    linear_factor_latex,
    poly_latex,
    root_latex,
    signed_combo_latex,
    value_latex,
    wrap_parens,
)
from .models import ApproachSide, BilingualText, SolutionStep


@dataclass
class VanishingBrick:
    """An expression B(x) vanishing at x0 such that B(x)/(x - x0) -> slope_limit."""

    x0: int
    expr: sp.Expr
    latex: str
    slope_limit: sp.Expr
    family: str
    sub_pattern: str
    hint_ar: str
    hint_fr: str
    steps: list[SolutionStep] = field(default_factory=list)
    order: int = 1
    requires_side: ApproachSide = ApproachSide.BOTH
    tags: list[str] = field(default_factory=list)


def _nonzero_int(rng: random.Random, lo: int, hi: int) -> int:
    choices = [v for v in range(lo, hi + 1) if v != 0]
    return rng.choice(choices)


def _random_poly_nonzero_at(
    rng: random.Random,
    x0: int,
    deg: int,
    coeff_bound: int = 4,
) -> sp.Expr:
    """Build a polynomial Q(x) of degree `deg` such that Q(x0) != 0."""
    for _ in range(50):
        coeffs = [_nonzero_int(rng, -coeff_bound, coeff_bound)]
        for _ in range(deg):
            coeffs.append(rng.randint(-coeff_bound, coeff_bound))
        expr = sp.Integer(0)
        for power, c in enumerate(reversed(coeffs)):
            expr += sp.Integer(c) * X**power
        if expr.subs(X, x0) != 0:
            return sp.expand(expr)
    return sp.Integer(1)


def _poly_with_value_at(
    rng: random.Random,
    x0: int,
    target_val: int,
    deg: int = 1,
    coeff_bound: int = 4,
) -> tuple[sp.Expr, sp.Expr]:
    """Return (P(x), R(x)) where P(x) = (x - x0)*R(x) + target_val and R(x0) != 0."""
    r_deg = max(0, deg - 1)
    r_expr = _random_poly_nonzero_at(rng, x0, r_deg, coeff_bound=coeff_bound)
    p_expr = sp.expand((X - sp.Integer(x0)) * r_expr + sp.Integer(target_val))
    return p_expr, r_expr


def _conjugate_sum_latex(u_tex: str, v_tex: str, n: int, neg_v: bool = False) -> str:
    """Format the n-th degree conjugate sum: sum_{k=0}^{n-1} u^{n-1-k} * v^k."""
    if n == 2:
        if neg_v:
            return f"{u_tex} - {v_tex}"
        return f"{u_tex} + {v_tex}"
    terms: list[str] = []
    for k in range(n):
        p_u = n - 1 - k
        p_v = k
        parts: list[str] = []
        if p_u == 1:
            parts.append(u_tex)
        elif p_u > 1:
            parts.append(rf"\left({u_tex}\right)^{{{p_u}}}")
        if p_v == 1:
            parts.append(v_tex)
        elif p_v > 1:
            parts.append(rf"\left({v_tex}\right)^{{{p_v}}}")
        term = r" \cdot ".join(parts)
        sign = "-" if (neg_v and p_v % 2 == 1) else "+"
        if not terms:
            terms.append(f"-{term}" if sign == "-" else term)
        else:
            terms.append(f" - {term}" if sign == "-" else f" + {term}")
    return "".join(terms)


def make_poly_brick(
    rng: random.Random,
    x0: int,
    min_deg: int = 2,
    max_deg: int = 3,
    coeff_bound: int = 4,
) -> VanishingBrick:
    """Build an expanded polynomial P(x) = (x - x0)*Q(x) vanishing at x0."""
    deg = rng.randint(min_deg, max_deg)
    q_expr = _random_poly_nonzero_at(rng, x0, deg - 1, coeff_bound=coeff_bound)
    p_expr = sp.expand((X - sp.Integer(x0)) * q_expr)
    p_tex = poly_latex(p_expr)
    q_tex = poly_latex(q_expr)
    lin_tex = linear_factor_latex("x", x0)
    q_at_x0 = sp.simplify(q_expr.subs(X, x0))

    steps = [
        SolutionStep(
            title=BilingualText(ar="التعميل بالحد الخطي", fr="Factorisation par le facteur linéaire"),
            explanation=BilingualText(
                ar=f"بما أن ${p_tex}$ تنعدم عند $x_0 = {x0}$، فإنها تقبل القسمة على $({lin_tex})$.",
                fr=f"Comme ${p_tex}$ s'annule en $x_0 = {x0}$, il est factorisable par $({lin_tex})$.",
            ),
            latex=rf"{p_tex} = ({lin_tex})({q_tex}), \quad \lim_{{x \to {x0}}} \left({q_tex}\right) = {value_latex(q_at_x0)}",
        )
    ]

    return VanishingBrick(
        x0=x0,
        expr=p_expr,
        latex=p_tex,
        slope_limit=q_at_x0,
        family="poly_factor",
        sub_pattern=f"poly_deg{deg}",
        hint_ar=f"عمّل التعبير $({p_tex})$ بـ $({lin_tex})$ (بالقسمة الإقليدية أو المتطابقات الهامة).",
        hint_fr=f"Factorisez $({p_tex})$ par $({lin_tex})$ (division euclidienne ou identités remarquables).",
        steps=steps,
        tags=[f"deg_{deg}"],
    )


def make_sqrt_conj_brick(
    rng: random.Random,
    x0: int,
    two_roots: bool | None = None,
    poly_deg: int = 1,
) -> VanishingBrick:
    """Build sqrt(P(x)) - L or sqrt(P1(x)) - sqrt(P2(x)) vanishing at x0."""
    if two_roots is None:
        two_roots = rng.choice([False, True])

    L = rng.randint(1, 4)
    L_sq = L * L

    if not two_roots:
        p_expr, r_expr = _poly_with_value_at(rng, x0, L_sq, deg=poly_deg, coeff_bound=4)
        flip = rng.choice([False, True])
        p_tex = poly_latex(p_expr)
        r_tex = poly_latex(r_expr)
        lin_tex = linear_factor_latex("x", x0)
        root_tex = root_latex(p_tex, 2)

        if not flip:
            expr = RootN(p_expr, 2) - sp.Integer(L)
            tex = f"{root_tex} - {L}"
            conj_tex = f"{root_tex} + {L}"
            signed_r = r_expr
        else:
            expr = sp.Integer(L) - RootN(p_expr, 2)
            tex = f"{L} - {root_tex}"
            conj_tex = f"{L} + {root_tex}"
            signed_r = -r_expr

        slope = sp.simplify(signed_r.subs(X, x0) / (2 * L))
        num_tex = poly_latex(sp.expand((X - sp.Integer(x0)) * signed_r))

        steps = [
            SolutionStep(
                title=BilingualText(ar="الضرب في المرافق التربيعي", fr="Multiplication par l'expression conjuguée"),
                explanation=BilingualText(
                    ar=f"نضرب ونقسم التعبير $({tex})$ على مرافقه $({conj_tex})$ ثم نُبسّط بـ $({lin_tex})$.",
                    fr=f"On multiplie et on divise $({tex})$ par son conjugué $({conj_tex})$ puis on factorise par $({lin_tex})$.",
                ),
                latex=(
                    rf"{tex} = \frac{{{num_tex}}}{{{conj_tex}}} = \frac{{({lin_tex})({poly_latex(signed_r)})}}{{{conj_tex}}}"
                    rf" \implies \lim_{{x \to {x0}}} \frac{{{tex}}}{{{lin_tex}}} = {value_latex(slope)}"
                ),
            )
        ]

        return VanishingBrick(
            x0=x0,
            expr=expr,
            latex=tex,
            slope_limit=slope,
            family="sqrt_conjugate",
            sub_pattern="sqrt_minus_const",
            hint_ar=f"استعمل المرافق التربيعي للتعبير $({tex})$.",
            hint_fr=f"Utilisez l'expression conjuguée de $({tex})$.",
            steps=steps,
            tags=["sqrt_const"],
        )

    # sqrt(P1) - sqrt(P2)
    p1_expr, r1_expr = _poly_with_value_at(rng, x0, L_sq, deg=poly_deg, coeff_bound=4)
    p2_expr, r2_expr = _poly_with_value_at(rng, x0, L_sq, deg=poly_deg, coeff_bound=4)
    while sp.simplify((r1_expr - r2_expr).subs(X, x0)) == 0:
        p2_expr, r2_expr = _poly_with_value_at(rng, x0, L_sq, deg=poly_deg, coeff_bound=4)

    p1_tex, p2_tex = poly_latex(p1_expr), poly_latex(p2_expr)
    r1_tex, r2_tex = root_latex(p1_tex, 2), root_latex(p2_tex, 2)
    tex = f"{r1_tex} - {r2_tex}"
    conj_tex = f"{r1_tex} + {r2_tex}"
    diff_r = sp.expand(r1_expr - r2_expr)
    slope = sp.simplify(diff_r.subs(X, x0) / (2 * L))
    lin_tex = linear_factor_latex("x", x0)

    steps = [
        SolutionStep(
            title=BilingualText(ar="المرافق التربيعي لجذرين", fr="Expression conjuguée de deux radicaux"),
            explanation=BilingualText(
                ar=f"نضرب ونقسم $({tex})$ على المرافق $({conj_tex})$.",
                fr=f"On multiplie et on divise $({tex})$ par son conjugué $({conj_tex})$.",
            ),
            latex=(
                rf"{tex} = \frac{{({p1_tex}) - ({p2_tex})}}{{{conj_tex}}} = \frac{{({lin_tex})({poly_latex(diff_r)})}}{{{conj_tex}}}"
                rf" \implies \lim_{{x \to {x0}}} \frac{{{tex}}}{{{lin_tex}}} = {value_latex(slope)}"
            ),
        )
    ]

    return VanishingBrick(
        x0=x0,
        expr=RootN(p1_expr, 2) - RootN(p2_expr, 2),
        latex=tex,
        slope_limit=slope,
        family="sqrt_conjugate",
        sub_pattern="sqrt_minus_sqrt",
        hint_ar=f"استعمل المرافق للفرق بين الجذرين $({tex})$.",
        hint_fr=f"Multipliez par l'expression conjuguée de $({tex})$.",
        steps=steps,
        tags=["sqrt_sqrt"],
    )


def make_nth_conj_brick(
    rng: random.Random,
    x0: int,
    n: int | None = None,
    two_roots: bool = False,
) -> VanishingBrick:
    """Build n-th root difference root_n(P) - L or root_n(P1) - root_n(P2)."""
    if n is None:
        n = rng.choice([3, 3, 4])

    if n % 2 == 1:
        L = rng.choice([-2, -1, 1, 2, 3])
    else:
        L = rng.choice([1, 2])
    L_pow = L**n
    lin_tex = linear_factor_latex("x", x0)

    if not two_roots:
        deg = rng.choice([1, 2])
        p_expr, r_expr = _poly_with_value_at(rng, x0, L_pow, deg=deg, coeff_bound=4)
        p_tex = poly_latex(p_expr)
        u_tex = root_latex(p_tex, n)

        if L > 0:
            expr = RootN(p_expr, n) - sp.Integer(L)
            tex = f"{u_tex} - {L}"
            conj_tex = _conjugate_sum_latex(u_tex, str(L), n, neg_v=False)
        else:
            # u - (-|L|) = u + |L|, conjugate alternates signs
            abs_L = -L
            expr = RootN(p_expr, n) + sp.Integer(abs_L)
            tex = f"{u_tex} + {abs_L}"
            conj_tex = _conjugate_sum_latex(u_tex, str(abs_L), n, neg_v=True)

        slope = sp.simplify(r_expr.subs(X, x0) / (n * (L ** (n - 1))))

        l_pow_diff_tex = f"- {L_pow}" if L_pow > 0 else f"+ {-L_pow}"
        steps = [
            SolutionStep(
                title=BilingualText(
                    ar=f"مرافق الجذر من الرتبة {n}",
                    fr=f"Expression conjuguée d'ordre {n}",
                ),
                explanation=BilingualText(
                    ar=rf"نستعمل المتطابقة $u^{{{n}}} - v^{{{n}}} = (u - v)({conj_tex})$ بوضع $u = {u_tex}$ و $v = {L}$.",
                    fr=rf"On utilise l'identité $u^{{{n}}} - v^{{{n}}} = (u - v)({conj_tex})$ avec $u = {u_tex}$ et $v = {L}$.",
                ),
                latex=(
                    rf"{tex} = \frac{{({p_tex}) {l_pow_diff_tex}}}{{{conj_tex}}}"
                    rf" = \frac{{({lin_tex})({poly_latex(r_expr)})}}{{{conj_tex}}}"
                    rf" \implies \lim_{{x \to {x0}}} \frac{{{tex}}}{{{lin_tex}}} = {value_latex(slope)}"
                ),
            )
        ]

        return VanishingBrick(
            x0=x0,
            expr=expr,
            latex=tex,
            slope_limit=slope,
            family="nth_root",
            sub_pattern=f"root{n}_minus_const",
            hint_ar=f"استعمل المرافق من الرتبة {n} للتعبير $({tex})$.",
            hint_fr=f"Utilisez l'expression conjuguée d'ordre {n} pour $({tex})$.",
            steps=steps,
            tags=[f"order_{n}"],
        )

    # root_n(P1) - root_n(P2)
    p1_expr, r1_expr = _poly_with_value_at(rng, x0, L_pow, deg=1, coeff_bound=4)
    p2_expr, r2_expr = _poly_with_value_at(rng, x0, L_pow, deg=1, coeff_bound=4)
    while sp.simplify((r1_expr - r2_expr).subs(X, x0)) == 0:
        p2_expr, r2_expr = _poly_with_value_at(rng, x0, L_pow, deg=1, coeff_bound=4)

    p1_tex, p2_tex = poly_latex(p1_expr), poly_latex(p2_expr)
    u_tex, v_tex = root_latex(p1_tex, n), root_latex(p2_tex, n)
    tex = f"{u_tex} - {v_tex}"
    conj_tex = _conjugate_sum_latex(u_tex, v_tex, n, neg_v=False)
    diff_r = sp.expand(r1_expr - r2_expr)
    slope = sp.simplify(diff_r.subs(X, x0) / (n * (L ** (n - 1))))

    steps = [
        SolutionStep(
            title=BilingualText(
                ar=f"المرافق من الرتبة {n} بين جذرين",
                fr=f"Expression conjuguée d'ordre {n} entre deux radicaux",
            ),
            explanation=BilingualText(
                ar=f"نضرب ونقسم $({tex})$ على المرافق من الدرجة {n}.",
                fr=f"On multiplie et on divise $({tex})$ par le conjugué d'ordre {n}.",
            ),
            latex=rf"{tex} = \frac{{({lin_tex})({poly_latex(diff_r)})}}{{{conj_tex}}} \implies \lim_{{x \to {x0}}} \frac{{{tex}}}{{{lin_tex}}} = {value_latex(slope)}",
        )
    ]

    return VanishingBrick(
        x0=x0,
        expr=RootN(p1_expr, n) - RootN(p2_expr, n),
        latex=tex,
        slope_limit=slope,
        family="nth_root",
        sub_pattern=f"root{n}_minus_root{n}",
        hint_ar=f"استعمل متطابقة $u^{{{n}}} - v^{{{n}}}$ للفرق بين الجذرين.",
        hint_fr=f"Utilisez l'identité $u^{{{n}}} - v^{{{n}}}$ pour la différence des deux radicaux.",
        steps=steps,
        tags=[f"order_{n}", "two_roots"],
    )


def make_add_zero_brick(
    rng: random.Random,
    x0: int,
    variant: str | None = None,
) -> VanishingBrick:
    """Build an expression forcing add/subtract 0 (+L - L or product split)."""
    if variant is None:
        variant = rng.choice(["mixed_roots", "mixed_roots", "product_split"])

    lin_tex = linear_factor_latex("x", x0)

    if variant == "mixed_roots":
        p, q = rng.choice([(2, 3), (3, 2), (2, 4), (3, 4), (2, 5)])
        L = rng.choice([1, 2]) if max(p, q) >= 4 else rng.choice([1, 2, 3])
        alpha = rng.choice([1, 1, 2])

        p1_expr, r1_expr = _poly_with_value_at(rng, x0, L**p, deg=rng.choice([1, 2]), coeff_bound=4)
        p2_expr, r2_expr = _poly_with_value_at(rng, x0, L**q, deg=rng.choice([1, 2]), coeff_bound=4)

        s1 = sp.simplify(r1_expr.subs(X, x0) / (p * (L ** (p - 1))))
        s2 = sp.simplify(r2_expr.subs(X, x0) / (q * (L ** (q - 1))))
        while sp.simplify(s1 - s2) == 0:
            p2_expr, r2_expr = _poly_with_value_at(rng, x0, L**q, deg=1, coeff_bound=4)
            s2 = sp.simplify(r2_expr.subs(X, x0) / (q * (L ** (q - 1))))

        p1_tex, p2_tex = poly_latex(p1_expr), poly_latex(p2_expr)
        u_tex, v_tex = root_latex(p1_tex, p), root_latex(p2_tex, q)
        alpha_prefix = "" if alpha == 1 else f"{alpha}"
        target_L = alpha * L

        expr = sp.Integer(alpha) * (RootN(p1_expr, p) - RootN(p2_expr, q))
        tex = f"{alpha_prefix}{u_tex} - {alpha_prefix}{v_tex}"
        slope = sp.simplify(sp.Integer(alpha) * (s1 - s2))

        conj1 = _conjugate_sum_latex(u_tex, str(L), p)
        conj2 = _conjugate_sum_latex(v_tex, str(L), q)

        steps = [
            SolutionStep(
                title=BilingualText(
                    ar=f"إضافة وطرح العدد {target_L} (توليد الصفر)",
                    fr=f"Ajout et retranchement de {target_L}",
                ),
                explanation=BilingualText(
                    ar=(
                        f"بما أن الجذرين من رتبتين مختلفتين ($p={p}$ و $q={q}$) ويتشاركان القيمة ${target_L}$ عند $x_0={x0}$، "
                        f"نضيف ونطرح ${target_L}$ لفصل كل جذر واستعمال مرافقه الخاص:"
                    ),
                    fr=(
                        f"Les deux radicaux étant d'ordres différents ($p={p}$ et $q={q}$) et prenant la valeur ${target_L}$ en $x_0={x0}$, "
                        f"on ajoute et on retranche ${target_L}$ pour traiter chaque radical séparément :"
                    ),
                ),
                latex=(
                    rf"{tex} = {alpha_prefix}\left[({u_tex} - {L}) - ({v_tex} - {L})\right]"
                    rf" = {alpha_prefix}\left[\frac{{({lin_tex})({poly_latex(r1_expr)})}}{{{conj1}}} - \frac{{({lin_tex})({poly_latex(r2_expr)})}}{{{conj2}}}\right]"
                ),
            ),
            SolutionStep(
                title=BilingualText(ar="تبسيط العامل المشترك", fr="Simplification du facteur commun"),
                explanation=BilingualText(
                    ar=f"بعد الاختزال بـ $({lin_tex})$ والتعويض بـ $x_0={x0}$ نحصل على:",
                    fr=f"Après simplification par $({lin_tex})$ et passage à la limite en $x_0={x0}$, on obtient :",
                ),
                latex=rf"\lim_{{x \to {x0}}} \frac{{{tex}}}{{{lin_tex}}} = {alpha_prefix}\left({value_latex(s1)} - {wrap_parens(value_latex(s2))}\right) = {value_latex(slope)}",
            ),
        ]

        return VanishingBrick(
            x0=x0,
            expr=expr,
            latex=tex,
            slope_limit=slope,
            family="add_sub_zero",
            sub_pattern=f"mixed_roots_{p}_{q}",
            hint_ar=f"أضف واطرح العدد ${target_L}$ في التعبير $({tex})$ لفصل الجذرين المختلفي الرتبة.",
            hint_fr=f"Ajoutez et retranchez ${target_L}$ dans $({tex})$ pour séparer les radicaux d'ordres différents.",
            steps=steps,
            tags=["add_zero", f"roots_{p}_{q}"],
        )

    # product_split: U(x)*root_n(P(x)) - U0*L
    n = rng.choice([2, 3])
    L = rng.choice([1, 2, 3]) if n == 2 else rng.choice([1, 2])
    q_expr = _random_poly_nonzero_at(rng, x0, 0, coeff_bound=3)
    # Build U(x) = (x - x0)*Q(x) + U0 with U0 != 0
    u0 = _nonzero_int(rng, -4, 4)
    u_expr = sp.expand((X - sp.Integer(x0)) * q_expr + sp.Integer(u0))

    p_expr, r_expr = _poly_with_value_at(rng, x0, L**n, deg=1, coeff_bound=4)
    root_slope = sp.simplify(r_expr.subs(X, x0) / (n * (L ** (n - 1))))
    total_slope = sp.simplify(q_expr.subs(X, x0) * L + u0 * root_slope)

    while total_slope == 0:
        p_expr, r_expr = _poly_with_value_at(rng, x0, L**n, deg=1, coeff_bound=4)
        root_slope = sp.simplify(r_expr.subs(X, x0) / (n * (L ** (n - 1))))
        total_slope = sp.simplify(q_expr.subs(X, x0) * L + u0 * root_slope)

    c_prod = u0 * L
    u_tex = wrap_parens(poly_latex(u_expr))
    p_tex = poly_latex(p_expr)
    rad_tex = root_latex(p_tex, n)

    expr = u_expr * RootN(p_expr, n) - sp.Integer(c_prod)
    raw_prod_tex = f"{u_tex}{rad_tex}"
    tex = signed_combo_latex(raw_prod_tex, "-", str(c_prod))

    u0_rad_tex = f"{u0}{rad_tex}" if abs(u0) != 1 else (rad_tex if u0 == 1 else f"-{rad_tex}")
    u_diff_tex = f"{poly_latex(u_expr)} - {u0}" if u0 > 0 else f"{poly_latex(u_expr)} + {-u0}"
    u0_sign_op = "+" if u0 > 0 else "-"
    u0_abs_str = "" if abs(u0) == 1 else str(abs(u0))

    steps = [
        SolutionStep(
            title=BilingualText(
                ar="إضافة وطرح حدّ وسيط لتفكيك الجداء",
                fr="Ajout et retranchement d'un terme intermédiaire",
            ),
            explanation=BilingualText(
                ar=(
                    f"نضيف ونطرح الحد ${u0_rad_tex}$ (حيث $U({x0}) = {u0}$) "
                    f"ثم نُعمّل كل جزء على حدة:"
                ),
                fr=(
                    f"On ajoute et on retranche le terme ${u0_rad_tex}$ (car $U({x0}) = {u0}$), "
                    f"puis on factorise chaque partie :"
                ),
            ),
            latex=(
                rf"{tex} = \left({u_diff_tex}\right){rad_tex} {u0_sign_op} {u0_abs_str}\left({rad_tex} - {L}\right)"
            ),
        ),
        SolutionStep(
            title=BilingualText(ar="التبسيط والمرافق", fr="Factorisation et conjugué"),
            explanation=BilingualText(
                ar=f"الحد الأول يتبسط بـ $({lin_tex})$ مباشرة، والحد الثاني باستعمال مرافق الرتبة {n}.",
                fr=f"Le premier terme se factorise directement par $({lin_tex})$ et le second par le conjugué d'ordre {n}.",
            ),
            latex=rf"\lim_{{x \to {x0}}} \frac{{{tex}}}{{{lin_tex}}} = {value_latex(q_expr.subs(X, x0) * L)} + {wrap_parens(value_latex(u0 * root_slope))} = {value_latex(total_slope)}",
        ),
    ]

    return VanishingBrick(
        x0=x0,
        expr=expr,
        latex=tex,
        slope_limit=total_slope,
        family="add_sub_zero",
        sub_pattern="product_split",
        hint_ar=rf"فكّك الجداء بإضافة وطرح الحد ${u0_rad_tex}$ (أو ${u_tex} \cdot {L}$).",
        hint_fr=rf"Décomposez le produit en ajoutant et retranchant ${u0_rad_tex}$ (ou ${u_tex} \cdot {L}$).",
        steps=steps,
        tags=["add_zero", "product_split"],
    )


def make_subst_root_brick(
    rng: random.Random,
    x0: int,
    k: int | None = None,
) -> VanishingBrick:
    """Build expression in mixed roots of (a*x + b) reducible via t = root_k(a*x + b)."""
    if k is None:
        k = rng.choice([4, 6])

    t0 = rng.choice([1, 2])
    a = rng.choice([1, 2, 3])
    b = (t0**k) - a * x0
    u_expr = sp.Integer(a) * X + sp.Integer(b)
    u_tex = poly_latex(u_expr)

    # Pick two distinct powers m1 > m2 in [1, k-1] producing distinct simplified root orders
    valid_pairs = [
        (m1, m2)
        for m1 in range(2, k)
        for m2 in range(1, m1)
        if (k // math.gcd(m1, k)) != (k // math.gcd(m2, k))
    ]
    m1, m2 = rng.choice(valid_pairs)
    c1 = _nonzero_int(rng, -3, 3)
    c2 = _nonzero_int(rng, -3, 3)

    # Ensure derivative at t0 is non-zero
    while c1 * m1 * (t0 ** (m1 - 1)) + c2 * m2 * (t0 ** (m2 - 1)) == 0:
        c2 = _nonzero_int(rng, -3, 3)

    const_val = c1 * (t0**m1) + c2 * (t0**m2)

    def _power_to_root(m: int) -> tuple[sp.Expr, str]:
        g = math.gcd(m, k)
        idx = k // g
        pwr = m // g
        inner = u_expr if pwr == 1 else (u_expr**pwr)
        inner_tex = u_tex if pwr == 1 else rf"({u_tex})^{{{pwr}}}"
        return RootN(inner, idx), root_latex(inner_tex, idx)

    e1, tex1 = _power_to_root(m1)
    e2, tex2 = _power_to_root(m2)

    expr = sp.Integer(c1) * e1 + sp.Integer(c2) * e2 - sp.Integer(const_val)

    # Format cleanly: c1*tex1 + c2*tex2 - const_val
    def _coeff_term(c: int, t: str, first: bool) -> str:
        if c == 1:
            return t if first else f" + {t}"
        if c == -1:
            return f"-{t}" if first else f" - {t}"
        if c > 0:
            return f"{c}{t}" if first else f" + {c}{t}"
        return f"-{-c}{t}" if first else f" - {-c}{t}"

    tex = _coeff_term(c1, tex1, True) + _coeff_term(c2, tex2, False)
    if const_val > 0:
        tex += f" - {const_val}"
    elif const_val < 0:
        tex += f" + {-const_val}"

    # Slope: lim (S(t) / (x - x0)) = a * S'(t0) / (k * t0^(k-1))
    s_prime_t0 = c1 * m1 * (t0 ** (m1 - 1)) + c2 * m2 * (t0 ** (m2 - 1))
    slope = sp.Rational(a * s_prime_t0, k * (t0 ** (k - 1)))
    t_var = sp.Symbol("t")
    s_t_poly = sp.Integer(c1) * t_var**m1 + sp.Integer(c2) * t_var**m2 - sp.Integer(const_val)
    q_t_poly = sp.simplify(s_t_poly / (t_var - t0))

    steps = [
        SolutionStep(
            title=BilingualText(
                ar="تغيير المتغير وتوحيد رتبة الجذور",
                fr="Changement de variable et mise au même ordre",
            ),
            explanation=BilingualText(
                ar=rf"نضع $t = {root_latex(u_tex, k)}$؛ عند $x \to {x0}$ لدينا $t \to {t0}$ و ${u_tex} = t^{{{k}}}$، وبالتالي:",
                fr=rf"On pose $t = {root_latex(u_tex, k)}$ ; lorsque $x \to {x0}$, on a $t \to {t0}$ et ${u_tex} = t^{{{k}}}$, d'où :",
            ),
            latex=(
                rf"{tex} = {poly_latex(s_t_poly, t_var)} = (t - {t0})\left({poly_latex(q_t_poly, t_var)}\right), "
                rf"\quad {linear_factor_latex('x', x0)} = \frac{{t^{{{k}}} - {t0**k}}}{{{a}}}"
            ),
        ),
        SolutionStep(
            title=BilingualText(ar="الاختزال وحساب النسبة", fr="Simplification en t"),
            explanation=BilingualText(
                ar=f"نختزل بـ $(t - {t0})$ ونعوّض بـ $t = {t0}$.",
                fr=f"On simplifie par $(t - {t0})$ puis on évalue en $t = {t0}$.",
            ),
            latex=rf"\lim_{{x \to {x0}}} \frac{{{tex}}}{{{linear_factor_latex('x', x0)}}} = {value_latex(slope)}",
        ),
    ]

    return VanishingBrick(
        x0=x0,
        expr=expr,
        latex=tex,
        slope_limit=slope,
        family="nth_root",
        sub_pattern=f"subst_root_{k}",
        hint_ar=rf"ضع تغيير المتغير $t = {root_latex(u_tex, k)}$ لتتحول الجذور إلى حدودية بدلالة $t$.",
        hint_fr=rf"Effectuez le changement de variable $t = {root_latex(u_tex, k)}$ pour obtenir un polynôme en $t$.",
        steps=steps,
        tags=["change_of_variable", f"order_{k}"],
    )


def make_trig_brick(rng: random.Random, x0: int) -> VanishingBrick:
    """Create a shifted sin/tan vanishing brick at x0 with slope a."""
    fn_name = rng.choice(["sin", "tan"])
    a = _nonzero_int(rng, -6, 6)
    arg_expr = sp.expand(sp.Integer(a) * (X - sp.Integer(x0)))
    arg_tex = poly_latex(arg_expr)

    trig_fn = sp.sin if fn_name == "sin" else sp.tan
    expr = trig_fn(arg_expr)
    tex = rf"\{fn_name}({arg_tex})"
    slope = sp.Integer(a)
    lin_tex = linear_factor_latex("x", x0)

    if x0 == 0:
        hint_ar = rf"استعمل النهاية الاعتيادية $\lim_{{x \to 0}} \frac{{\{fn_name}({arg_tex})}}{{{arg_tex}}} = 1$."
        hint_fr = rf"Utilisez la limite usuelle $\lim_{{x \to 0}} \frac{{\{fn_name}({arg_tex})}}{{{arg_tex}}} = 1$."
        steps = [
            SolutionStep(
                title=BilingualText(
                    ar=f"استعمال النهاية المثلثية لـ {fn_name}",
                    fr=f"Limite trigonométrique usuelle ({fn_name})",
                ),
                explanation=BilingualText(
                    ar=rf"نكتب $\frac{{\{fn_name}({arg_tex})}}{{x}} = {a} \cdot \frac{{\{fn_name}({arg_tex})}}{{{arg_tex}}}$ عند $x \to 0$:",
                    fr=rf"On écrit $\frac{{\{fn_name}({arg_tex})}}{{x}} = {a} \cdot \frac{{\{fn_name}({arg_tex})}}{{{arg_tex}}}$ au voisinage de $0$ :",
                ),
                latex=rf"\lim_{{x \to 0}} \frac{{{tex}}}{{x}} = {a}",
            )
        ]
    else:
        factored_arg = f"{a}({lin_tex})" if a != 1 else lin_tex
        hint_ar = rf"عمّل بـ ${a}$ داخل $\{fn_name}$ وضع $t = {lin_tex} \to 0$ لاستعمال $\lim_{{t \to 0}} \frac{{\{fn_name}(at)}}{{t}} = a$."
        hint_fr = rf"Factorisez par ${a}$ dans $\{fn_name}$ puis posez $t = {lin_tex} \to 0$ pour utiliser $\lim_{{t \to 0}} \frac{{\{fn_name}(at)}}{{t}} = a$."
        steps = [
            SolutionStep(
                title=BilingualText(
                    ar=rf"تغيير المتغير في $\{fn_name}$ عند ${x0}$",
                    fr=f"Changement de variable trigonométrique en ${x0}$",
                ),
                explanation=BilingualText(
                    ar=rf"نلاحظ أن $\{fn_name}({arg_tex}) = \{fn_name}({factored_arg})$. بوضع $t = {lin_tex} \to 0$ نحصل على:",
                    fr=rf"On a $\{fn_name}({arg_tex}) = \{fn_name}({factored_arg})$. En posant $t = {lin_tex} \to 0$, il vient :",
                ),
                latex=rf"\lim_{{x \to {x0}}} \frac{{{tex}}}{{{lin_tex}}} = \lim_{{t \to 0}} \frac{{\{fn_name}({poly_latex(sp.Integer(a) * sp.Symbol('t'), sp.Symbol('t'))})}}{{t}} = {a}",
            )
        ]

    return VanishingBrick(
        x0=x0,
        expr=expr,
        latex=tex,
        slope_limit=slope,
        family="trig",
        sub_pattern=f"trig_{fn_name}_shift",
        hint_ar=hint_ar,
        hint_fr=hint_fr,
        steps=steps,
        tags=["trig", fn_name, "shifted_point" if x0 != 0 else "at_zero"],
    )
