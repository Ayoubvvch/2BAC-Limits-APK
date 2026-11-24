"""Symbolic expression primitives and clean Moroccan-style LaTeX formatting."""

from __future__ import annotations

import sympy as sp

from .models import LimitPoint

X = sp.Symbol("x", real=True)
N = sp.Symbol("n", integer=True, positive=True)


class RootN(sp.Function):
    """Real n-th root placeholder avoiding SymPy complex branch cuts and Piecewise clutter."""

    nargs = 2

    def _latex(self, printer) -> str:
        inner_tex = printer._print(self.args[0])
        n = int(self.args[1])
        if n == 2:
            return rf"\sqrt{{{inner_tex}}}"
        return rf"\sqrt[{n}]{{{inner_tex}}}"


def has_top_level_add_sub(tex: str) -> bool:
    """Return True if a LaTeX string has a '+' or binary '-' at brace/paren depth 0."""
    brace_depth = 0
    paren_depth = 0
    s = tex.strip()
    i = 0
    while i < len(s):
        ch = s[i]
        if ch == "{":
            brace_depth += 1
        elif ch == "}":
            brace_depth = max(0, brace_depth - 1)
        elif ch == "(":
            paren_depth += 1
        elif ch == ")":
            paren_depth = max(0, paren_depth - 1)
        elif brace_depth == 0 and paren_depth == 0:
            if ch == "+":
                return True
            if ch == "-" and i > 0:
                return True
        i += 1
    return False


def wrap_parens(tex: str) -> str:
    """Wrap LaTeX expression in parentheses when needed for multiplication/powers."""
    s = tex.strip()
    if has_top_level_add_sub(s) or s.startswith("-"):
        return rf"\left({s}\right)"
    return s


def poly_latex(expr: sp.Expr, var: sp.Symbol = X) -> str:
    """Format a polynomial expression cleanly in descending powers of var."""
    expanded = sp.expand(expr)
    if expanded.is_Number:
        return sp.latex(expanded)
    try:
        poly = sp.Poly(expanded, var)
    except sp.PolynomialError:
        return sp.latex(expanded)

    terms: list[str] = []
    for (deg,), coeff in poly.terms():
        if coeff == 0:
            continue
        sign = "-" if coeff < 0 else "+"
        abs_c = abs(coeff)

        if deg == 0:
            body = sp.latex(abs_c)
        else:
            var_part = str(var) if deg == 1 else f"{var}^{{{deg}}}"
            if abs_c == 1:
                body = var_part
            else:
                c_tex = wrap_parens(sp.latex(abs_c)) if has_top_level_add_sub(sp.latex(abs_c)) else sp.latex(abs_c)
                body = f"{c_tex}{var_part}"

        if not terms:
            terms.append(f"-{body}" if sign == "-" else body)
        else:
            terms.append(f" - {body}" if sign == "-" else f" + {body}")

    return "".join(terms) if terms else "0"


def root_latex(inner_tex: str, index: int) -> str:
    """Render an n-th root in standard LaTeX."""
    if index == 2:
        return rf"\sqrt{{{inner_tex}}}"
    return rf"\sqrt[{index}]{{{inner_tex}}}"


def linear_factor_latex(var_tex: str, x0: int) -> str:
    """Format (x - x0) cleanly."""
    if x0 == 0:
        return var_tex
    if x0 > 0:
        return f"{var_tex} - {x0}"
    return f"{var_tex} + {-x0}"


def value_latex(val: sp.Expr | int) -> str:
    """Format exact SymPy answers (including +oo, -oo, rationals, radicals)."""
    if val is sp.oo:
        return r"+\infty"
    if val is -sp.oo:
        return r"-\infty"
    if val is sp.zoo:
        return r"\infty"
    expr = sp.sympify(val)
    if expr == sp.oo:
        return r"+\infty"
    if expr == -sp.oo:
        return r"-\infty"
    tex = sp.latex(sp.simplify(expr), ln_notation=True)
    return tex


def expr_latex(expr: sp.Expr) -> str:
    """Format a general SymPy expression containing RootN cleanly."""
    return sp.latex(expr, ln_notation=True)


def limit_statement_latex(expr_tex: str, point: LimitPoint) -> str:
    """Format the full limit expression '\\lim_{x -> ...} ...'."""
    body = expr_tex.strip()
    if has_top_level_add_sub(body):
        body = rf"\left({body}\right)"
    return rf"\lim_{{x \to {point.point_latex()}}} {body}"


def signed_combo_latex(left_tex: str, op: str, right_tex: str) -> str:
    """Combine 'left (+|-) right' cleanly avoiding '+ -' or '- -'."""
    r = right_tex.strip()
    if op == "+":
        if r.startswith("-"):
            return f"{left_tex} - {r[1:].lstrip()}"
        return f"{left_tex} + {r}"
    if op == "-":
        if r.startswith("-"):
            return f"{left_tex} + {r[1:].lstrip()}"
        if has_top_level_add_sub(r):
            return rf"{left_tex} - \left({r}\right)"
        return f"{left_tex} - {r}"
    return f"{left_tex} {op} {r}"
