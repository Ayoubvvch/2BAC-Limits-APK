"""Core data models for the 2BAC PC Limits procedural exercise engine."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any

import sympy as sp


class Technique(str, Enum):
    """Supported limit-solving technique categories for 2BAC PC."""

    POLY_FACTOR = "poly_factor"
    SQRT_CONJUGATE = "sqrt_conjugate"
    NTH_ROOT = "nth_root"
    ADD_SUB_ZERO = "add_sub_zero"
    TRIG = "trig"
    HYBRID = "hybrid"


class Difficulty(int, Enum):
    """Progressive difficulty levels calibrated for Moroccan 2BAC PC."""

    BASIC = 1        # Direct single-technique application
    INTERMEDIATE = 2 # Classic Bac traps (sign at -oo, double roots, add/sub 0)
    CHALLENGE = 3    # Hybrid multi-technique / Olympiad & DS-level problems


class ApproachSide(str, Enum):
    """Direction of limit approach."""

    BOTH = "both"
    RIGHT = "right"   # x -> x0+
    LEFT = "left"     # x -> x0-
    POS_INF = "+oo"   # x -> +oo
    NEG_INF = "-oo"   # x -> -oo


@dataclass(frozen=True)
class BilingualText:
    """Localized text in Arabic (ar) and French BIOF (fr)."""

    ar: str
    fr: str

    def to_dict(self) -> dict[str, str]:
        return {"ar": self.ar, "fr": self.fr}


@dataclass(frozen=True)
class SolutionStep:
    """One pedagogical step in solving the limit."""

    title: BilingualText
    explanation: BilingualText
    latex: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "title": self.title.to_dict(),
            "explanation": self.explanation.to_dict(),
            "latex": self.latex,
        }


@dataclass(frozen=True)
class LimitPoint:
    """Target point of the limit (finite x0 with side, or +-oo)."""

    kind: str  # "finite", "+oo", "-oo"
    value: int | None = None
    side: ApproachSide = ApproachSide.BOTH
    sympy_str: str | None = None

    def exact_sympy(self) -> sp.Expr | None:
        """Return exact SymPy coordinate when kind == 'finite'."""
        if self.kind != "finite":
            return None
        if self.sympy_str is not None:
            return sp.sympify(self.sympy_str)
        if self.value is not None:
            return sp.Integer(self.value)
        return None

    def point_latex(self) -> str:
        if self.kind == "+oo":
            return r"+\infty"
        if self.kind == "-oo":
            return r"-\infty"
        if self.sympy_str is not None:
            base = sp.latex(sp.sympify(self.sympy_str))
        else:
            assert self.value is not None
            base = str(self.value)
        if self.side == ApproachSide.RIGHT:
            return rf"{{{base}}}^+" if "\\" in base else f"{base}^+"
        if self.side == ApproachSide.LEFT:
            return rf"{{{base}}}^-" if "\\" in base else f"{base}^-"
        return base

    def to_dict(self) -> dict[str, Any]:
        data: dict[str, Any] = {
            "kind": self.kind,
            "value": self.value,
            "side": self.side.value,
            "latex": self.point_latex(),
        }
        if self.sympy_str is not None:
            data["sympy_str"] = self.sympy_str
        return data


@dataclass
class GeneratedExercise:
    """Complete generated limit exercise with hints, verified answer, and steps."""

    seed: str
    technique: Technique
    sub_pattern: str
    difficulty: Difficulty
    point: LimitPoint
    expr_latex: str
    full_limit_latex: str
    answer_latex: str
    answer_sympy_str: str
    indeterminate_form: str
    hints: list[BilingualText] = field(default_factory=list)
    steps: list[SolutionStep] = field(default_factory=list)
    tags: list[str] = field(default_factory=list)
    verified_by_cas: bool = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "seed": self.seed,
            "technique": self.technique.value,
            "sub_pattern": self.sub_pattern,
            "difficulty": int(self.difficulty),
            "point": self.point.to_dict(),
            "expr_latex": self.expr_latex,
            "full_limit_latex": self.full_limit_latex,
            "answer_latex": self.answer_latex,
            "answer_sympy_str": self.answer_sympy_str,
            "indeterminate_form": self.indeterminate_form,
            "hints": [h.to_dict() for h in self.hints],
            "steps": [s.to_dict() for s in self.steps],
            "tags": self.tags,
            "verified_by_cas": self.verified_by_cas,
        }
