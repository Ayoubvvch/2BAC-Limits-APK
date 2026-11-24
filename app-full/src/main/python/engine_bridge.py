"""Android Chaquopy bridge for the 2BAC PC Moroccan Math Procedural CAS Engine (Limits & Sequences)."""

from __future__ import annotations

import json
import traceback
from typing import Any

from engine import (
    AnswerKind,
    Chapter,
    Difficulty,
    Technique,
    generate_adaptive_exercise,
    generate_exercise,
    grade_student_answer,
    parse_seed,
)


def generate_exercise_json(
    technique: str | None,
    difficulty: int,
    explicit_seed: str | None,
    mastery_json: str | None = None,
    chapter: str | None = "limits",
    total_solved: int = 0,
    solved_by_chapter_json: str | None = None,
    recent_history_json: str | None = None,
    sub_pattern_json: str | None = None,
    exclude_seeds_json: str | None = None,
) -> str:
    """Generate or retrieve a procedurally verified exercise matching the server API."""
    try:
        clean_seed = (explicit_seed or "").strip().upper()
        plan_dict: dict[str, Any] | None = None

        if clean_seed:
            ex = generate_exercise(seed=clean_seed)
        else:
            tech = None
            if technique and technique != "adaptive":
                try:
                    tech = Technique(technique)
                except Exception:
                    tech = None

            diff = Difficulty(difficulty) if difficulty in (1, 2, 3) else None

            try:
                chap = Chapter(chapter) if chapter else Chapter.LIMITS
            except Exception:
                chap = Chapter.LIMITS

            mastery_rows: dict[str, dict[str, Any]] = {}
            if mastery_json:
                try:
                    raw_map = json.loads(mastery_json)
                    if isinstance(raw_map, dict):
                        for k, v in raw_map.items():
                            if isinstance(v, dict):
                                mastery_rows[k] = v
                            else:
                                mastery_rows[k] = {"mastery": float(v), "attempts": 1, "correct": 0}
                except Exception:
                    mastery_rows = {}

            sub_pattern_rows: list[dict[str, Any]] = []
            if sub_pattern_json:
                try:
                    parsed_sp = json.loads(sub_pattern_json)
                    if isinstance(parsed_sp, list):
                        sub_pattern_rows = parsed_sp
                except Exception:
                    sub_pattern_rows = []

            recent_history: list[dict[str, Any]] = []
            if recent_history_json:
                try:
                    parsed_hist = json.loads(recent_history_json)
                    if isinstance(parsed_hist, list):
                        recent_history = parsed_hist
                except Exception:
                    recent_history = []

            solved_by_chapter: dict[str, int] | None = None
            if solved_by_chapter_json:
                try:
                    parsed_sc = json.loads(solved_by_chapter_json)
                    if isinstance(parsed_sc, dict):
                        solved_by_chapter = {str(k): int(v) for k, v in parsed_sc.items()}
                except Exception:
                    solved_by_chapter = None

            exclude_seeds: set[str] | None = None
            if exclude_seeds_json:
                try:
                    parsed_ex = json.loads(exclude_seeds_json)
                    if isinstance(parsed_ex, list):
                        exclude_seeds = {str(s) for s in parsed_ex}
                except Exception:
                    exclude_seeds = None

            ex, plan = generate_adaptive_exercise(
                mastery_rows=mastery_rows,
                sub_pattern_rows=sub_pattern_rows,
                recent_history=recent_history,
                total_solved=int(total_solved or 0),
                chapter=chap,
                solved_by_chapter=solved_by_chapter,
                requested_technique=tech,
                requested_difficulty=diff,
                exclude_seeds=exclude_seeds,
            )
            plan_dict = {
                "mode": plan.mode,
                "reason": plan.reason.to_dict(),
            }

        ex_dict = ex.to_dict()
        if plan_dict is not None:
            ex_dict["coach"] = plan_dict

        return json.dumps({
            "status": "ok",
            "exercise": ex_dict,
        })
    except Exception as exc:
        return json.dumps({
            "status": "error",
            "error": str(exc),
            "traceback": traceback.format_exc(),
        })


def grade_answer_json(
    student_answer: str,
    expected_sympy_str: str,
    answer_kind: str = "constant",
) -> str:
    """Grade student math answer using SymPy symbolic equivalence."""
    try:
        try:
            kind = AnswerKind(answer_kind)
        except Exception:
            kind = AnswerKind.CONSTANT

        res = grade_student_answer(
            student_answer,
            expected_sympy_str,
            answer_kind=kind,
        )
        return json.dumps({
            "status": "ok",
            "grade": res.to_dict(),
        })
    except Exception as exc:
        return json.dumps({
            "status": "error",
            "error": str(exc),
            "traceback": traceback.format_exc(),
        })


def is_valid_seed(seed: str | None) -> bool:
    """Check if the seed string matches standard 2BAC limits/sequences seed structure."""
    if not seed:
        return False
    return parse_seed(seed.strip().upper()) is not None


def get_hint_json(seed: str, hint_index: int) -> str:
    """Retrieve bilingual hint for given exercise seed."""
    try:
        clean_seed = seed.strip().upper()
        ex = generate_exercise(seed=clean_seed)
        if not ex.hints:
            return json.dumps({"status": "error", "error": "No hints available"})
        idx = min(hint_index, len(ex.hints) - 1)
        h = ex.hints[idx]
        return json.dumps({
            "status": "ok",
            "hint": {"ar": h.ar, "fr": h.fr},
        })
    except Exception as exc:
        return json.dumps({"status": "error", "error": str(exc)})


def get_solution_json(seed: str) -> str:
    """Retrieve verified step-by-step bilingual solution for given exercise seed."""
    try:
        clean_seed = seed.strip().upper()
        ex = generate_exercise(seed=clean_seed)
        return json.dumps({
            "status": "ok",
            "solution": {
                "answer_latex": ex.answer_latex,
                "answer_sympy_str": ex.answer_sympy_str,
                "indeterminate_form": ex.indeterminate_form,
                "steps": [s.to_dict() for s in ex.steps],
            },
        })
    except Exception as exc:
        return json.dumps({"status": "error", "error": str(exc)})
