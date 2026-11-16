"""Android Chaquopy bridge for the 2BAC PC Moroccan Limits Procedural CAS Engine."""

from __future__ import annotations

import json
import traceback
from typing import Any

from engine import (
    Difficulty,
    GeneratedExercise,
    Technique,
    generate_exercise,
    grade_student_answer,
    parse_seed,
)


def generate_exercise_json(
    technique: str | None,
    difficulty: int,
    explicit_seed: str | None,
    mastery_json: str | None = None,
) -> str:
    """Generate or retrieve a procedurally verified limit exercise matching the server API."""
    try:
        clean_seed = (explicit_seed or "").strip().upper()
        if clean_seed:
            ex = generate_exercise(seed=clean_seed)
        else:
            tech = None
            if technique and technique != "adaptive":
                try:
                    tech = Technique(technique)
                except Exception:
                    tech = None

            diff = None
            if difficulty in (1, 2, 3):
                diff = Difficulty(difficulty)

            mastery: dict[str, float] | None = None
            if mastery_json:
                try:
                    raw_map = json.loads(mastery_json)
                    if isinstance(raw_map, dict):
                        mastery = {k: float(v) for k, v in raw_map.items()}
                except Exception:
                    mastery = None

            ex = generate_exercise(
                technique=tech,
                difficulty=diff,
                mastery=mastery,
            )

        return json.dumps({
            "status": "ok",
            "exercise": ex.to_dict(),
        })
    except Exception as exc:
        return json.dumps({
            "status": "error",
            "error": str(exc),
            "traceback": traceback.format_exc(),
        })


def grade_answer_json(student_answer: str, expected_sympy_str: str) -> str:
    """Grade student math answer using SymPy symbolic equivalence."""
    try:
        res = grade_student_answer(student_answer, expected_sympy_str)
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
    """Check if the seed string matches standard 2BAC limits seed structure."""
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
