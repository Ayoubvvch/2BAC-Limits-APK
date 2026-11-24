"""Core data models for the 2BAC PC multi-chapter procedural exercise engine."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any

import sympy as sp


class Chapter(str, Enum):
    """Supported 2BAC PC curriculum chapters."""

    LIMITS = "limits"
    SEQUENCES = "sequences"


class AnswerKind(str, Enum):
    """Expected symbolic structure of the student's answer."""

    CONSTANT = "constant"          # Real constant, radical, pi, e, ln(...), +-oo
    EXPRESSION_N = "expression_n"  # Algebraic/exponential expression in natural integer n


class Technique(str, Enum):
    """Supported procedural exercise techniques across chapters."""

    # Chapter 1: Limits (including ln & exp)
    POLY_FACTOR = "poly_factor"
    SQRT_CONJUGATE = "sqrt_conjugate"
    TRIG = "trig"
    NTH_ROOT = "nth_root"
    LN_LIMITS = "ln_limits"
    EXP_LIMITS = "exp_limits"
    ADD_SUB_ZERO = "add_sub_zero"
    HYBRID = "hybrid"

    # Chapter 2: Numerical Sequences (Pure Calculation & Speed)
    SEQ_LIMITS = "seq_limits"
    SEQ_ARITH_GEO = "seq_arith_geo"
    SEQ_FIXED_POINT = "seq_fixed_point"
    SEQ_HOMOGRAPHIC = "seq_homographic"


TECHNIQUE_TO_CHAPTER: dict[Technique, Chapter] = {
    Technique.POLY_FACTOR: Chapter.LIMITS,
    Technique.SQRT_CONJUGATE: Chapter.LIMITS,
    Technique.TRIG: Chapter.LIMITS,
    Technique.NTH_ROOT: Chapter.LIMITS,
    Technique.LN_LIMITS: Chapter.LIMITS,
    Technique.EXP_LIMITS: Chapter.LIMITS,
    Technique.ADD_SUB_ZERO: Chapter.LIMITS,
    Technique.HYBRID: Chapter.LIMITS,
    Technique.SEQ_LIMITS: Chapter.SEQUENCES,
    Technique.SEQ_ARITH_GEO: Chapter.SEQUENCES,
    Technique.SEQ_FIXED_POINT: Chapter.SEQUENCES,
    Technique.SEQ_HOMOGRAPHIC: Chapter.SEQUENCES,
}


def chapter_of_technique(tech: Technique | str) -> Chapter:
    t_enum = tech if isinstance(tech, Technique) else Technique(tech)
    return TECHNIQUE_TO_CHAPTER[t_enum]


class Difficulty(int, Enum):
    """Progressive difficulty levels calibrated for Moroccan 2BAC PC."""

    BASIC = 1        # Direct single-technique application
    INTERMEDIATE = 2 # Classic Bac traps (sign at -oo, double roots, dominant powers, isolation)
    CHALLENGE = 3    # Hybrid multi-technique / Olympiad & DS-level problems


class ApproachSide(str, Enum):
    """Direction of limit approach."""

    BOTH = "both"
    RIGHT = "right"   # x -> x0+
    LEFT = "left"     # x -> x0-
    POS_INF = "+oo"   # x -> +oo (or n -> +oo)
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
    """One pedagogical step in solving the exercise."""

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
    """Complete generated exercise with hints, verified answer, and bilingual steps."""

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
    chapter: Chapter = Chapter.LIMITS
    answer_kind: AnswerKind = AnswerKind.CONSTANT
    prompt: BilingualText | None = None

    def __post_init__(self) -> None:
        self.chapter = chapter_of_technique(self.technique)

    def to_dict(self) -> dict[str, Any]:
        return {
            "seed": self.seed,
            "chapter": self.chapter.value,
            "technique": self.technique.value,
            "sub_pattern": self.sub_pattern,
            "difficulty": int(self.difficulty),
            "answer_kind": self.answer_kind.value,
            "prompt": self.prompt.to_dict() if self.prompt else None,
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
