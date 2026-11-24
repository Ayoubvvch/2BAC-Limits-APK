"""Domain safety guards, SymPy CAS double-verification, and symbolic answer grading."""

from __future__ import annotations

import math
import re
from dataclasses import dataclass

import sympy as sp
from sympy.parsing.sympy_parser import (
    convert_xor,
    implicit_multiplication_application,
    parse_expr,
    rationalize,
    standard_transformations,
)

from .formatting import N, RootN, X, value_latex
from .models import AnswerKind, ApproachSide, BilingualText, LimitPoint


def _ast_depth(expr: sp.Basic) -> int:
    if not expr.args:
        return 0
    return 1 + max(_ast_depth(a) for a in expr.args)


def _eval_real_node(expr: sp.Basic, x_val: float) -> float | None:
    """Evaluate a SymPy expression containing RootN with real-valued root semantics."""
    if isinstance(expr, sp.Number):
        return float(expr)
    if expr == X:
        return x_val
    if expr == sp.pi:
        return math.pi
    if expr == sp.E:
        return math.e

    if isinstance(expr, RootN):
        inner_val = _eval_real_node(expr.args[0], x_val)
        n = int(expr.args[1])
        if inner_val is None or not math.isfinite(inner_val):
            return None
        if n % 2 == 0:
            if inner_val < -1e-12:
                return None
            return max(0.0, inner_val) ** (1.0 / n)
        # Odd root: real branch for negative inputs
        if inner_val < 0:
            return -((-inner_val) ** (1.0 / n))
        return inner_val ** (1.0 / n)

    if isinstance(expr, sp.Add):
        total = 0.0
        for arg in expr.args:
            v = _eval_real_node(arg, x_val)
            if v is None:
                return None
            total += v
        return total

    if isinstance(expr, sp.Mul):
        prod = 1.0
        for arg in expr.args:
            v = _eval_real_node(arg, x_val)
            if v is None:
                return None
            prod *= v
        return prod

    if isinstance(expr, sp.Pow):
        base_val = _eval_real_node(expr.args[0], x_val)
        exp_node = expr.args[1]
        if base_val is None:
            return None
        if exp_node.is_Integer:
            exp_i = int(exp_node)
            if exp_i < 0 and abs(base_val) < 1e-15:
                return None
            try:
                return base_val ** exp_i
            except OverflowError:
                return None
        exp_val = _eval_real_node(exp_node, x_val)
        if exp_val is None:
            return None
        if base_val < 0:
            # Handle Rational odd denominators cleanly if any slipped through
            if isinstance(exp_node, sp.Rational) and exp_node.q % 2 == 1:
                mag = (-base_val) ** (float(exp_node.p) / float(exp_node.q))
                return -mag if exp_node.p % 2 == 1 else mag
            return None
        if base_val == 0 and exp_val <= 0:
            return None
        try:
            return base_val ** exp_val
        except OverflowError:
            return None

    if isinstance(expr, sp.Abs):
        v = _eval_real_node(expr.args[0], x_val)
        return abs(v) if v is not None else None

    if isinstance(expr, sp.log):
        v = _eval_real_node(expr.args[0], x_val)
        if v is None or v <= 1e-15:
            return None
        return math.log(v)

    if isinstance(expr, sp.exp):
        v = _eval_real_node(expr.args[0], x_val)
        if v is None:
            return None
        try:
            if v > 700:
                return float("inf")
            if v < -700:
                return 0.0
            return math.exp(v)
        except OverflowError:
            return None

    # Fallback for any remaining standard elementary functions (high precision
    # avoids catastrophic cancellation near 1 - cos(x) at small offsets)
    try:
        val = complex(expr.subs(X, sp.Float(x_val, 30)).evalf(30))
        if abs(val.imag) > 1e-9 or not math.isfinite(val.real):
            return None
        return val.real
    except Exception:
        return None


def _collect_roots(expr: sp.Basic) -> list[RootN]:
    """Collect all RootN subexpressions sorted innermost-first."""
    roots = list(expr.atoms(RootN))
    roots.sort(key=_ast_depth)
    return roots


def _probe_points(point: LimitPoint, has_exp: bool = False) -> list[float]:
    if point.kind == "+oo":
        return [8.0, 14.0, 20.0] if has_exp else [500.0, 5_000.0, 50_000.0]
    if point.kind == "-oo":
        return [-8.0, -14.0, -20.0] if has_exp else [-500.0, -5_000.0, -50_000.0]
    x0_sym = point.exact_sympy()
    assert x0_sym is not None
    x0 = float(complex(x0_sym.evalf(30)).real)
    deltas = [1e-2, 1e-4, 1e-5]
    if point.side == ApproachSide.RIGHT:
        return [x0 + d for d in deltas]
    if point.side == ApproachSide.LEFT:
        return [x0 - d for d in deltas]
    return [x0 + d for d in deltas] + [x0 - d for d in deltas]


def check_real_domain(expr: sp.Expr, point: LimitPoint) -> bool:
    """Verify every even root and log is strictly positive and expression is finite near point."""
    has_exp = bool(expr.has(sp.exp))
    probes = _probe_points(point, has_exp=has_exp)
    roots = _collect_roots(expr)
    logs = list(expr.atoms(sp.log))

    for xv in probes:
        for r in roots:
            n = int(r.args[1])
            inner_val = _eval_real_node(r.args[0], xv)
            if inner_val is None:
                return False
            if n % 2 == 0 and inner_val <= 1e-15:
                return False
        for lg in logs:
            inner_val = _eval_real_node(lg.args[0], xv)
            if inner_val is None or inner_val <= 1e-15:
                return False
        full_val = _eval_real_node(expr, xv)
        if full_val is None or not math.isfinite(full_val):
            return False
    return True


def _to_analytic_branch(
    expr: sp.Expr,
    point: LimitPoint,
    side_override: str | None = None,
) -> tuple[sp.Expr, sp.Symbol, sp.Expr]:
    """Convert RootN nodes into positive-radicand SymPy Powers for reliable sp.limit."""
    if point.kind == "+oo":
        t = sp.Symbol("t_pos", positive=True)
        sub_map = {X: t}
        probe_x = 1e6
        target = sp.oo
    elif point.kind == "-oo":
        t = sp.Symbol("t_pos", positive=True)
        sub_map = {X: -t}
        probe_x = -1e6
        target = sp.oo
    else:
        x0 = point.exact_sympy()
        assert x0 is not None
        x0_float = float(complex(x0.evalf(30)).real)
        h = sp.Symbol("h_pos", positive=True)
        direction = side_override or ("-" if point.side == ApproachSide.LEFT else "+")
        if direction == "+":
            sub_map = {X: x0 + h}
            probe_x = x0_float + 1e-4
        else:
            sub_map = {X: x0 - h}
            probe_x = x0_float - 1e-4
        t = h
        target = sp.Integer(0)

    current = expr
    # Replace innermost RootN nodes first so nested roots resolve cleanly
    while True:
        roots = _collect_roots(current)
        if not roots:
            break
        r = roots[0]
        inner, n_expr = r.args
        n = int(n_expr)
        probe_val = _eval_real_node(inner, probe_x)
        if probe_val is None:
            raise ValueError("Expression leaves real domain during branch resolution")
        if n % 2 == 0:
            if probe_val <= 0:
                raise ValueError("Even root radicand non-positive in neighborhood")
            replacement = sp.Pow(inner, sp.Rational(1, n))
        else:
            if probe_val >= 0:
                replacement = sp.Pow(inner, sp.Rational(1, n))
            else:
                replacement = -sp.Pow(-inner, sp.Rational(1, n))
        current = current.subs(r, replacement)

    analytic = current.subs(sub_map)
    return analytic, t, target


def _answers_equal(a: sp.Expr, b: sp.Expr) -> bool:
    """Check exact equality of two SymPy values or expressions in n (including +-oo)."""
    if a in (sp.oo, -sp.oo, sp.zoo) or b in (sp.oo, -sp.oo, sp.zoo):
        return a == b
    try:
        # Normalize free symbol 'n' assumptions if both contain n
        a_norm = a.subs({s: N for s in a.free_symbols if s.name == "n"})
        b_norm = b.subs({s: N for s in b.free_symbols if s.name == "n"})
        diff = sp.simplify(sp.powsimp(a_norm - b_norm, force=True))
        if diff == 0:
            return True
        if sp.radsimp(diff) == 0:
            return True
        if diff.is_number:
            cval = complex(diff.evalf(30))
            return abs(cval) < 1e-18
        if diff.free_symbols == {N}:
            # High-precision multi-point identity check across 6 distinct integer n values
            for k in (1, 2, 3, 5, 8, 13):
                val = complex(diff.subs(N, k).evalf(30))
                if abs(val) > 1e-12:
                    return False
            return True
    except Exception:
        return False
    return False


def verify_limit_cas(
    expr: sp.Expr,
    point: LimitPoint,
    expected: sp.Expr,
) -> tuple[bool, str | None]:
    """Verify domain safety and cross-check the limit using SymPy CAS."""
    if not check_real_domain(expr, point):
        return False, "Domain check failed near limit point"

    try:
        if point.kind == "finite" and point.side == ApproachSide.BOTH:
            sides = ["+", "-"]
        elif point.kind == "finite" and point.side == ApproachSide.LEFT:
            sides = ["-"]
        else:
            sides = ["+"]

        for s in sides:
            analytic, var, target = _to_analytic_branch(expr, point, side_override=s)
            if target == sp.Integer(0):
                cas_val = sp.limit(analytic, var, 0, dir="+")
            else:
                cas_val = sp.limit(analytic, var, target)
            if not _answers_equal(cas_val, expected):
                return False, f"CAS mismatch on side {s}: got {cas_val}, expected {expected}"
    except Exception as exc:
        return False, f"CAS evaluation error: {exc}"

    return True, None


def verify_numeric_trend(
    expr: sp.Expr,
    point: LimitPoint,
    expected: sp.Expr,
) -> bool:
    """Fast numeric trend verification as an independent sanity check."""
    if not check_real_domain(expr, point):
        return False

    has_exp = bool(expr.has(sp.exp))
    if point.kind == "+oo":
        xs = [12.0, 22.0, 32.0] if has_exp else [1e3, 1e5, 1e7]
    elif point.kind == "-oo":
        xs = [-12.0, -22.0, -32.0] if has_exp else [-1e3, -1e5, -1e7]
    else:
        x0_sym = point.exact_sympy()
        assert x0_sym is not None
        x0 = float(complex(x0_sym.evalf(30)).real)
        if point.side == ApproachSide.RIGHT:
            xs = [x0 + 1e-2, x0 + 1e-3, x0 + 1e-5]
        elif point.side == ApproachSide.LEFT:
            xs = [x0 - 1e-2, x0 - 1e-3, x0 - 1e-5]
        else:
            xs = [x0 + 1e-5, x0 - 1e-5]

    vals: list[float] = []
    for xv in xs:
        v = _eval_real_node(expr, xv)
        if v is None:
            return False
        vals.append(v)

    if expected == sp.oo:
        return vals[-1] > 100 and (len(vals) < 2 or vals[-1] >= vals[0])
    if expected == -sp.oo:
        return vals[-1] < -100 and (len(vals) < 2 or vals[-1] <= vals[0])

    exp_float = float(complex(expected.evalf()).real)
    tol = max(1e-2, 1e-3 * abs(exp_float))
    if point.kind == "finite" and point.side == ApproachSide.BOTH:
        # Must agree from BOTH right (x0 + 1e-5) and left (x0 - 1e-5)
        return all(abs(v - exp_float) < tol for v in vals)
    return abs(vals[-1] - exp_float) < tol


# ---------------------------------------------------------------------------
# Symbolic Student Answer Parser & Grader
# ---------------------------------------------------------------------------

_PARSE_TRANSFORMS = standard_transformations + (
    implicit_multiplication_application,
    convert_xor,
    rationalize,
)

_SAFE_GLOBALS: dict[str, object] = {
    "__builtins__": {},
    "Integer": sp.Integer,
    "Float": sp.Float,
    "Rational": sp.Rational,
    "Symbol": sp.Symbol,
}

_SAFE_LOCALS: dict[str, object] = {
    "sqrt": sp.sqrt,
    "root": sp.real_root,
    "abs": sp.Abs,
    "sin": sp.sin,
    "cos": sp.cos,
    "tan": sp.tan,
    "ln": sp.log,
    "log": sp.log,
    "exp": sp.exp,
    "e": sp.E,
    "E": sp.E,
    "oo": sp.oo,
    "inf": sp.oo,
    "pi": sp.pi,
    "Rational": sp.Rational,
}

_SAFE_LOCALS_N: dict[str, object] = {
    **_SAFE_LOCALS,
    "n": N,
}


def _strip_braced_group(s: str, start: int) -> tuple[str, int]:
    """Extract {...} group starting at start (where s[start] == '{')."""
    depth = 0
    i = start
    while i < len(s):
        if s[i] == "{":
            depth += 1
        elif s[i] == "}":
            depth -= 1
            if depth == 0:
                return s[start + 1 : i], i + 1
        i += 1
    raise ValueError("Unmatched '{' in expression")


def _latex_to_python_math(raw: str) -> str:
    """Convert common student math/LaTeX notation into SymPy-parsable string."""
    s = raw.strip()
    s = s.replace("−", "-").replace("÷", "/").replace("×", "*")
    s = s.replace(r"\left", "").replace(r"\right", "").replace(" ", "")
    s = s.replace("∞", "oo").replace(r"\infty", "oo").replace("infinity", "oo")
    s = s.replace("π", "pi")
    s = re.sub(r"\\pi(?![a-zA-Z])", "pi", s)
    s = re.sub(r"\\(sin|cos|tan|ln|log|exp)\b", r"\1", s)
    s = re.sub(r"\\cdot\b", "*", s)

    # Convert \frac{a}{b} iteratively (supports nested braces)
    while r"\frac" in s:
        idx = s.find(r"\frac")
        pos = idx + 5
        if pos >= len(s) or s[pos] != "{":
            raise ValueError("Malformed \\frac")
        num, pos = _strip_braced_group(s, pos)
        if pos >= len(s) or s[pos] != "{":
            raise ValueError("Malformed \\frac denominator")
        den, pos = _strip_braced_group(s, pos)
        s = s[:idx] + f"(({num})/({den}))" + s[pos:]

    # Convert \sqrt[n]{a} and \sqrt{a}
    while r"\sqrt" in s:
        idx = s.find(r"\sqrt")
        pos = idx + 5
        if pos < len(s) and s[pos] == "[":
            end_b = s.find("]", pos)
            if end_b == -1:
                raise ValueError("Malformed \\sqrt[n]")
            n_str = s[pos + 1 : end_b]
            pos = end_b + 1
            if pos >= len(s) or s[pos] != "{":
                raise ValueError("Malformed \\sqrt[n]{...}")
            inner, pos = _strip_braced_group(s, pos)
            s = s[:idx] + f"root(({inner}), {n_str})" + s[pos:]
        elif pos < len(s) and s[pos] == "{":
            inner, pos = _strip_braced_group(s, pos)
            s = s[:idx] + f"sqrt({inner})" + s[pos:]
        else:
            raise ValueError("Malformed \\sqrt")

    # Handle ^{...} exponents
    while "^{" in s:
        idx = s.find("^{")
        inner, pos = _strip_braced_group(s, idx + 1)
        s = s[:idx] + f"^({inner})" + s[pos:]

    return s


def parse_student_answer(
    raw: str,
    answer_kind: AnswerKind | str = AnswerKind.CONSTANT,
) -> sp.Expr:
    """Safely parse student math input into a SymPy expression."""
    cleaned = raw.strip()
    if not cleaned:
        raise ValueError("Empty answer")

    kind = answer_kind if isinstance(answer_kind, AnswerKind) else AnswerKind(answer_kind)

    low = cleaned.lower().replace(" ", "")
    if kind == AnswerKind.CONSTANT:
        if low in {"+inf", "+infinity", "+oo", "+∞", "oo", "inf", "infinity", "∞"}:
            return sp.oo
        if low in {"-inf", "-infinity", "-oo", "-∞"}:
            return -sp.oo

    py_str = _latex_to_python_math(cleaned)
    # Reject unknown identifiers/dunders for safety
    if "__" in py_str or any(ch in py_str for ch in [";", "=", "$", "`", "\\"]):
        raise ValueError("Unsupported characters in answer")

    locals_map = _SAFE_LOCALS_N if kind == AnswerKind.EXPRESSION_N else _SAFE_LOCALS
    expr = parse_expr(
        py_str,
        local_dict=locals_map,
        global_dict=_SAFE_GLOBALS,
        transformations=_PARSE_TRANSFORMS,
    )
    if kind == AnswerKind.CONSTANT:
        if expr.free_symbols:
            raise ValueError("Answer must be a constant or +-oo, not contain variables")
    elif kind == AnswerKind.EXPRESSION_N:
        allowed = {N}
        if not expr.free_symbols.issubset(allowed):
            raise ValueError("Answer may only depend on the integer variable n")
    return sp.simplify(expr)


@dataclass(frozen=True)
class GradeResult:
    """Result of grading a student's symbolic answer."""

    valid_syntax: bool
    is_correct: bool
    student_latex: str
    expected_latex: str
    message: BilingualText

    def to_dict(self) -> dict[str, object]:
        return {
            "valid_syntax": self.valid_syntax,
            "is_correct": self.is_correct,
            "student_latex": self.student_latex,
            "expected_latex": self.expected_latex,
            "message": self.message.to_dict(),
        }


def grade_student_answer(
    student_raw: str,
    expected_sympy_str: str,
    answer_kind: AnswerKind | str = AnswerKind.CONSTANT,
) -> GradeResult:
    """Grade student input symbolically against the verified expected value."""
    kind = answer_kind if isinstance(answer_kind, AnswerKind) else AnswerKind(answer_kind)
    locals_expected = {"n": N} if kind == AnswerKind.EXPRESSION_N else None
    expected = sp.sympify(expected_sympy_str, locals=locals_expected)
    expected_tex = value_latex(expected)

    try:
        student_val = parse_student_answer(student_raw, answer_kind=kind)
    except Exception:
        if kind == AnswerKind.EXPRESSION_N:
            err_msg = BilingualText(
                ar=r"صيغة التعبير غير واضحة. أمثلة: `2 + 3*(1/2)^n` أو `(2^n - 1)/(2^n + 1)` أو `4*(1 - (1/3)^(n+1))`.",
                fr=r"Format d'expression invalide. Exemples : `2 + 3*(1/2)^n`, `(2^n - 1)/(2^n + 1)` ou `4*(1 - (1/3)^(n+1))`.",
            )
        else:
            err_msg = BilingualText(
                ar=r"صيغة الإجابة غير واضحة. أمثلة: `1/2` أو `sqrt(3)/2` أو `ln(2)` أو `e^2` أو `+inf` أو `-inf`.",
                fr=r"Format de réponse invalide. Exemples : `1/2`, `sqrt(3)/2`, `ln(2)`, `e^2`, `+inf` ou `-inf`.",
            )
        return GradeResult(
            valid_syntax=False,
            is_correct=False,
            student_latex="",
            expected_latex=expected_tex,
            message=err_msg,
        )

    correct = _answers_equal(student_val, expected)
    student_tex = value_latex(student_val)

    if correct:
        msg = BilingualText(
            ar="أحسنت! إجابتك صحيحة تماماً.",
            fr="Bravo ! Votre réponse est exacte.",
        )
    else:
        msg = BilingualText(
            ar="إجابة غير صحيحة. راجع التلميحات أو جرّب التحقق من الإشارات والمعاملات.",
            fr="Réponse incorrecte. Consultez les indices ou vérifiez les signes et coefficients.",
        )

    return GradeResult(
        valid_syntax=True,
        is_correct=correct,
        student_latex=student_tex,
        expected_latex=expected_tex,
        message=msg,
    )
