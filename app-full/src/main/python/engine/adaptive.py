"""Progressive multi-chapter adaptive curriculum, fluency timing, and spaced-repetition coach."""

from __future__ import annotations

import math
import random
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any

from .models import BilingualText, Chapter, Difficulty, Technique, chapter_of_technique

# Ordered Moroccan 2BAC PC curriculum ladders per chapter (foundational -> advanced)
CHAPTER_CURRICULUM: dict[Chapter, tuple[Technique, ...]] = {
    Chapter.LIMITS: (
        Technique.POLY_FACTOR,
        Technique.SQRT_CONJUGATE,
        Technique.TRIG,
        Technique.NTH_ROOT,
        Technique.LN_LIMITS,
        Technique.EXP_LIMITS,
        Technique.ADD_SUB_ZERO,
        Technique.HYBRID,
    ),
    Chapter.SEQUENCES: (
        Technique.SEQ_LIMITS,
        Technique.SEQ_ARITH_GEO,
        Technique.SEQ_FIXED_POINT,
        Technique.SEQ_HOMOGRAPHIC,
    ),
}

CURRICULUM_ORDER: tuple[Technique, ...] = (
    CHAPTER_CURRICULUM[Chapter.LIMITS] + CHAPTER_CURRICULUM[Chapter.SEQUENCES]
)

# Starting mastery for brand-new accounts (gentle entry at Level 1)
INITIAL_MASTERY = 0.28

# Pen-and-paper target solving times (seconds, excluding typing time thanks to Pause)
TIME_BENCHMARKS: dict[Difficulty, dict[str, float]] = {
    Difficulty.BASIC: {"fast": 45.0, "normal": 120.0, "slow": 210.0},
    Difficulty.INTERMEDIATE: {"fast": 90.0, "normal": 220.0, "slow": 360.0},
    Difficulty.CHALLENGE: {"fast": 150.0, "normal": 340.0, "slow": 540.0},
}

# Natural mastery ceiling achievable at each difficulty tier
_DIFF_CEILING: dict[Difficulty, float] = {
    Difficulty.BASIC: 0.64,
    Difficulty.INTERMEDIATE: 0.86,
    Difficulty.CHALLENGE: 0.98,
}


@dataclass(frozen=True)
class FluencyRating:
    """Human-readable speed/fluency classification for a completed exercise."""

    code: str  # "fast", "good", "slow", "assisted", "revealed", "incorrect"
    label: BilingualText

    def to_dict(self) -> dict[str, Any]:
        return {"code": self.code, "label": self.label.to_dict()}


@dataclass(frozen=True)
class OutcomeEvaluation:
    """Result of evaluating an exercise outcome for mastery + spaced repetition."""

    quality: float
    new_mastery: float
    delta: float
    fluency: FluencyRating
    interval_hours: float
    consecutive_ok: int


@dataclass(frozen=True)
class AdaptivePlan:
    """Next-exercise recommendation produced by the smart coaching engine."""

    technique: Technique
    difficulty: Difficulty
    target_sub_pattern: str | None
    avoid_sub_patterns: tuple[str, ...]
    mode: str  # "warmup", "introduce_new", "review_weak", "recover", "fluency", "challenge", "balanced"
    reason: BilingualText


def _parse_iso(ts: str | None) -> datetime | None:
    if not ts:
        return None
    try:
        dt = datetime.fromisoformat(ts)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except Exception:
        return None


def compute_effective_mastery(
    raw_mastery: float,
    last_practiced_at: str | None,
    now: datetime | None = None,
) -> float:
    """Apply a gentle forgetting curve after 2 days of inactivity."""
    score = max(0.10, min(0.98, float(raw_mastery)))
    last_dt = _parse_iso(last_practiced_at)
    if last_dt is None:
        return score
    ref = now or datetime.now(timezone.utc)
    days_idle = max(0.0, (ref - last_dt).total_seconds() / 86400.0)
    if days_idle <= 2.0 or score <= 0.35:
        return score
    decay = 0.5 ** ((days_idle - 2.0) / 21.0)
    return round(0.35 + (score - 0.35) * decay, 4)


def get_curriculum_status(
    mastery_rows: dict[str, dict[str, Any]],
    total_solved: int,
    solved_by_chapter: dict[str, int] | None = None,
) -> dict[Technique, dict[str, Any]]:
    """Determine which techniques are unlocked in each chapter's progressive curriculum ladder."""

    def m(tech: Technique) -> float:
        row = mastery_rows.get(tech.value)
        return float(row["mastery"]) if row else INITIAL_MASTERY

    def corr(tech: Technique) -> int:
        row = mastery_rows.get(tech.value)
        return int(row.get("correct", 0)) if row else 0

    def att(tech: Technique) -> int:
        row = mastery_rows.get(tech.value)
        return int(row.get("attempts", 0)) if row else 0

    lim_solved = (
        int(solved_by_chapter.get(Chapter.LIMITS.value, 0))
        if solved_by_chapter is not None
        else total_solved
    )
    seq_solved = (
        int(solved_by_chapter.get(Chapter.SEQUENCES.value, 0))
        if solved_by_chapter is not None
        else sum(corr(t) for t in CHAPTER_CURRICULUM[Chapter.SEQUENCES])
    )

    status: dict[Technique, dict[str, Any]] = {}

    # ---------------- CHAPTER 1: LIMITS ----------------
    status[Technique.POLY_FACTOR] = {
        "unlocked": True,
        "stage": 1,
        "hint": BilingualText(ar="متاح منذ البداية", fr="Disponible dès le début"),
    }

    sqrt_open = (
        m(Technique.POLY_FACTOR) >= 0.38
        or corr(Technique.POLY_FACTOR) >= 2
        or att(Technique.SQRT_CONJUGATE) > 0
        or lim_solved >= 2
    )
    status[Technique.SQRT_CONJUGATE] = {
        "unlocked": sqrt_open,
        "stage": 2,
        "hint": BilingualText(
            ar="يُفتح بعد تثبيت أساسيات التعميل (تمرينان صحيحان).",
            fr="Débloqué après les bases de factorisation (2 réussites).",
        ),
    }

    trig_open = (
        m(Technique.POLY_FACTOR) >= 0.42
        or att(Technique.TRIG) > 0
        or lim_solved >= 3
    )
    status[Technique.TRIG] = {
        "unlocked": trig_open,
        "stage": 2,
        "hint": BilingualText(
            ar="يُفتح بعد حل 3 تمارين أساسية بنجاح.",
            fr="Débloqué après 3 exercices réussis.",
        ),
    }

    nth_open = (
        m(Technique.SQRT_CONJUGATE) >= 0.48
        or corr(Technique.SQRT_CONJUGATE) >= 2
        or att(Technique.NTH_ROOT) > 0
        or lim_solved >= 5
    )
    status[Technique.NTH_ROOT] = {
        "unlocked": nth_open,
        "stage": 3,
        "hint": BilingualText(
            ar="يُفتح بعد إتقان أساسيات المرافق التربيعي.",
            fr="Débloqué après les bases de l'expression conjuguée.",
        ),
    }

    ln_open = (
        m(Technique.POLY_FACTOR) >= 0.44
        or att(Technique.LN_LIMITS) > 0
        or lim_solved >= 4
    )
    status[Technique.LN_LIMITS] = {
        "unlocked": ln_open,
        "stage": 3,
        "hint": BilingualText(
            ar="يُفتح بعد إتقان أساسيات التعميل (4 تمارين في النهايات).",
            fr="Débloqué après 4 réussites dans le chapitre Limites.",
        ),
    }

    exp_open = (
        m(Technique.LN_LIMITS) >= 0.42
        or m(Technique.POLY_FACTOR) >= 0.48
        or att(Technique.EXP_LIMITS) > 0
        or lim_solved >= 5
    )
    status[Technique.EXP_LIMITS] = {
        "unlocked": exp_open,
        "stage": 3,
        "hint": BilingualText(
            ar="يُفتح بعد تثبيت أساسيات النهايات واللوغاريتم.",
            fr="Débloqué après les bases des limites et de ln.",
        ),
    }

    add0_open = (
        (m(Technique.SQRT_CONJUGATE) >= 0.52 and m(Technique.NTH_ROOT) >= 0.42)
        or att(Technique.ADD_SUB_ZERO) > 0
        or lim_solved >= 8
    )
    status[Technique.ADD_SUB_ZERO] = {
        "unlocked": add0_open,
        "stage": 4,
        "hint": BilingualText(
            ar="يُفتح بعد التمكن من المرافق التربيعي والجذور النونية.",
            fr="Débloqué après la maîtrise du conjugué et des racines n-ièmes.",
        ),
    }

    core_ready = sum(
        1
        for t in (
            Technique.POLY_FACTOR,
            Technique.SQRT_CONJUGATE,
            Technique.TRIG,
            Technique.NTH_ROOT,
            Technique.LN_LIMITS,
            Technique.EXP_LIMITS,
            Technique.ADD_SUB_ZERO,
        )
        if m(t) >= 0.55
    )
    hybrid_open = core_ready >= 3 or att(Technique.HYBRID) > 0 or lim_solved >= 11
    status[Technique.HYBRID] = {
        "unlocked": hybrid_open,
        "stage": 5,
        "hint": BilingualText(
            ar="يُفتح بعد إتقان 3 تقنيات أساسية (مستوى الفروض المحروسة).",
            fr="Débloqué après 3 techniques maîtrisées (niveau Devoir Surveillé).",
        ),
    }

    # ---------------- CHAPTER 2: SEQUENCES ----------------
    status[Technique.SEQ_LIMITS] = {
        "unlocked": True,
        "stage": 1,
        "hint": BilingualText(ar="متاح منذ البداية", fr="Disponible dès le début"),
    }

    ag_open = (
        m(Technique.SEQ_LIMITS) >= 0.38
        or corr(Technique.SEQ_LIMITS) >= 2
        or att(Technique.SEQ_ARITH_GEO) > 0
        or seq_solved >= 2
    )
    status[Technique.SEQ_ARITH_GEO] = {
        "unlocked": ag_open,
        "stage": 2,
        "hint": BilingualText(
            ar="يُفتح بعد تثبيت نهايات المتتاليات (تمرينان صحيحان).",
            fr="Débloqué après 2 réussites sur les limites de suites.",
        ),
    }

    fp_open = (
        m(Technique.SEQ_ARITH_GEO) >= 0.40
        or att(Technique.SEQ_FIXED_POINT) > 0
        or seq_solved >= 3
    )
    status[Technique.SEQ_FIXED_POINT] = {
        "unlocked": fp_open,
        "stage": 2,
        "hint": BilingualText(
            ar="يُفتح بعد حل 3 تمارين في فصل المتتاليات.",
            fr="Débloqué après 3 exercices réussis en suites.",
        ),
    }

    homo_open = (
        m(Technique.SEQ_ARITH_GEO) >= 0.48
        or corr(Technique.SEQ_ARITH_GEO) >= 2
        or att(Technique.SEQ_HOMOGRAPHIC) > 0
        or seq_solved >= 4
    )
    status[Technique.SEQ_HOMOGRAPHIC] = {
        "unlocked": homo_open,
        "stage": 3,
        "hint": BilingualText(
            ar="يُفتح بعد إتقان المتتاليات الترجعية الخطية (نمط الامتحان الوطني).",
            fr="Débloqué après les suites arithmético-géométriques (style Examen National).",
        ),
    }

    return status


def classify_fluency(
    difficulty: Difficulty,
    is_correct: bool,
    solution_viewed: bool,
    hints_used: int,
    wrong_attempts: int,
    solve_time_sec: float | None,
) -> FluencyRating:
    """Classify the student's fluency based on accuracy, assistance, and thinking time."""
    if solution_viewed:
        return FluencyRating(
            code="revealed",
            label=BilingualText(ar="تم عرض الحل — سنراجعه قريباً", fr="Solution consultée — révision planifiée"),
        )
    if not is_correct:
        return FluencyRating(
            code="incorrect",
            label=BilingualText(ar="محاولة خاطئة — راجع الحساب", fr="Réponse incorrecte — vérifiez vos calculs"),
        )
    if hints_used > 0 or wrong_attempts > 0:
        return FluencyRating(
            code="assisted",
            label=BilingualText(ar="صحيح بعد مساعدة / محاولة ثانية", fr="Exact après aide / second essai"),
        )

    bench = TIME_BENCHMARKS[difficulty]
    if solve_time_sec is None or solve_time_sec <= 0:
        return FluencyRating(
            code="good",
            label=BilingualText(ar="إجابة صحيحة ومتقنة", fr="Réponse exacte et solide"),
        )
    if solve_time_sec <= bench["fast"]:
        return FluencyRating(
            code="fast",
            label=BilingualText(ar="⚡ إتقان وسرعة ممتازة!", fr="⚡ Maîtrise et rapidité excellentes !"),
        )
    if solve_time_sec <= bench["normal"]:
        return FluencyRating(
            code="good",
            label=BilingualText(ar="✓ وقت مثالي وحل متزن", fr="✓ Temps idéal et démarche solide"),
        )
    return FluencyRating(
        code="slow",
        label=BilingualText(ar="صحيح، لكن يحتاج تحسين السرعة", fr="Exact, mais la rapidité reste à travailler"),
    )


def evaluate_outcome(
    *,
    base_mastery: float,
    attempts_in_tech: int,
    difficulty: Difficulty,
    is_correct: bool,
    solution_viewed: bool,
    hints_used: int,
    wrong_attempts_before: int,
    solve_time_sec: float | None,
    current_streak: int,
    prev_consecutive_ok: int = 0,
) -> OutcomeEvaluation:
    """Compute quality score, updated mastery, fluency badge, and next SM-2 review interval."""
    fluency = classify_fluency(
        difficulty=difficulty,
        is_correct=is_correct,
        solution_viewed=solution_viewed,
        hints_used=hints_used,
        wrong_attempts=wrong_attempts_before,
        solve_time_sec=solve_time_sec,
    )

    bench = TIME_BENCHMARKS[difficulty]
    ceiling = _DIFF_CEILING[difficulty]
    calibrating = attempts_in_tech < 4

    if solution_viewed:
        quality = 0.0
        alpha = 0.30 if calibrating else 0.24
        target = 0.12
        new_mastery = base_mastery + alpha * (target - base_mastery)
        new_mastery = max(0.12, min(0.98, new_mastery))
        return OutcomeEvaluation(
            quality=quality,
            new_mastery=round(new_mastery, 4),
            delta=round(new_mastery - base_mastery, 4),
            fluency=fluency,
            interval_hours=0.08,
            consecutive_ok=0,
        )

    if not is_correct:
        quality = 0.10
        alpha = 0.22 if calibrating else 0.16
        target = 0.14
        new_mastery = base_mastery + alpha * (target - base_mastery)
        new_mastery = max(0.12, min(0.98, new_mastery))
        return OutcomeEvaluation(
            quality=quality,
            new_mastery=round(new_mastery, 4),
            delta=round(new_mastery - base_mastery, 4),
            fluency=fluency,
            interval_hours=0.10,
            consecutive_ok=0,
        )

    # Solved without viewing solution
    q = 1.0
    q -= 0.18 * min(wrong_attempts_before, 2)
    q -= 0.14 * min(hints_used, 2)

    if solve_time_sec is not None and solve_time_sec > 0:
        if solve_time_sec <= bench["fast"] and wrong_attempts_before == 0 and hints_used == 0:
            q += 0.08
        elif solve_time_sec <= bench["normal"]:
            pass
        elif solve_time_sec <= bench["slow"]:
            q -= 0.08
        else:
            q -= 0.16

    quality = round(max(0.28, min(1.0, q)), 3)

    if quality >= 0.88:
        alpha = 0.38 if (calibrating or current_streak >= 2) else 0.24
        target = max(base_mastery + 0.02, ceiling)
    elif quality >= 0.65:
        alpha = 0.24 if calibrating else 0.16
        target = min(ceiling, max(base_mastery + 0.01, ceiling * quality + 0.08))
    else:
        alpha = 0.12
        target = min(ceiling, max(base_mastery, 0.55))

    new_mastery = base_mastery + alpha * (target - base_mastery)
    if base_mastery < ceiling and new_mastery <= base_mastery:
        new_mastery = base_mastery + 0.02
    new_mastery = max(0.12, min(0.98, new_mastery))

    if quality >= 0.80:
        consec = prev_consecutive_ok + 1
        interval_hours = min(336.0, 6.0 * (2.5 ** (consec - 1)))
    elif quality >= 0.55:
        consec = max(1, prev_consecutive_ok)
        interval_hours = 2.0
    else:
        consec = 0
        interval_hours = 0.25

    return OutcomeEvaluation(
        quality=quality,
        new_mastery=round(new_mastery, 4),
        delta=round(new_mastery - base_mastery, 4),
        fluency=fluency,
        interval_hours=round(interval_hours, 2),
        consecutive_ok=consec,
    )


def compute_next_review_iso(interval_hours: float, now: datetime | None = None) -> str:
    ref = now or datetime.now(timezone.utc)
    return (ref + timedelta(hours=interval_hours)).isoformat()


def choose_calibrated_difficulty(
    *,
    tech_mastery: float,
    tech_attempts: int,
    global_mastery: float,
    recent_history: list[dict[str, Any]],
    rng: random.Random,
    is_weak_review: bool = False,
) -> Difficulty:
    """Pick an appropriate difficulty calibrated to the student's true skill."""
    last_failed = False
    last_diff = 1
    if recent_history:
        last = recent_history[0]
        last_diff = int(last.get("difficulty") or 1)
        if last.get("solution_viewed") or last.get("is_correct") is False:
            last_failed = True

    if tech_attempts == 0:
        if global_mastery >= 0.72 and not last_failed:
            return Difficulty.INTERMEDIATE
        return Difficulty.BASIC

    if is_weak_review:
        if tech_mastery < 0.48:
            return Difficulty.BASIC
        if tech_mastery < 0.74:
            return Difficulty.INTERMEDIATE
        return Difficulty.INTERMEDIATE if last_failed else Difficulty.CHALLENGE

    if tech_mastery < 0.44:
        diff = Difficulty.BASIC
    elif tech_mastery < 0.66:
        diff = rng.choices([Difficulty.BASIC, Difficulty.INTERMEDIATE], weights=[0.25, 0.75])[0]
    elif tech_mastery < 0.82:
        diff = rng.choices([Difficulty.INTERMEDIATE, Difficulty.CHALLENGE], weights=[0.55, 0.45])[0]
    else:
        diff = rng.choices([Difficulty.INTERMEDIATE, Difficulty.CHALLENGE], weights=[0.25, 0.75])[0]

    if last_failed:
        cap = max(1, last_diff - 1) if last_diff >= 2 else 1
        if int(diff) > cap:
            diff = Difficulty(cap)

    return diff


def select_adaptive_plan(
    *,
    mastery_rows: dict[str, dict[str, Any]],
    sub_pattern_rows: list[dict[str, Any]],
    recent_history: list[dict[str, Any]],
    total_solved: int,
    chapter: Chapter = Chapter.LIMITS,
    solved_by_chapter: dict[str, int] | None = None,
    requested_technique: Technique | None = None,
    requested_difficulty: Difficulty | None = None,
    rng: random.Random | None = None,
    now: datetime | None = None,
) -> AdaptivePlan:
    """Decide the optimal next exercise within the chosen chapter."""
    r = rng or random.Random()
    ref = now or datetime.now(timezone.utc)

    active_chapter = chapter_of_technique(requested_technique) if requested_technique is not None else chapter
    chapter_techs = CHAPTER_CURRICULUM[active_chapter]

    curriculum = get_curriculum_status(mastery_rows, total_solved, solved_by_chapter=solved_by_chapter)
    unlocked_techs = [t for t in chapter_techs if curriculum[t]["unlocked"]]
    if not unlocked_techs:
        unlocked_techs = [chapter_techs[0]]

    eff_map: dict[Technique, float] = {}
    attempts_map: dict[Technique, int] = {}
    for tech in chapter_techs:
        row = mastery_rows.get(tech.value) or {}
        raw_m = float(row.get("mastery", INITIAL_MASTERY))
        att = int(row.get("attempts", 0))
        eff_map[tech] = compute_effective_mastery(raw_m, row.get("last_practiced_at"), now=ref)
        attempts_map[tech] = att

    practiced_scores = [eff_map[t] for t in unlocked_techs if attempts_map[t] > 0]
    global_mastery = sum(practiced_scores) / len(practiced_scores) if practiced_scores else INITIAL_MASTERY

    chapter_history = [
        h for h in recent_history if h.get("technique") in {t.value for t in chapter_techs}
    ]
    chapter_solved = (
        int(solved_by_chapter.get(active_chapter.value, 0))
        if solved_by_chapter is not None
        else total_solved
    )

    last_sub_pattern = chapter_history[0]["sub_pattern"] if chapter_history else None
    avoid_subs: tuple[str, ...] = (last_sub_pattern,) if last_sub_pattern else ()

    # Case A: Brand-new student in this chapter -> Gentle Level 1 start!
    if requested_technique is None and chapter_solved == 0 and len(chapter_history) == 0:
        first_tech = chapter_techs[0]
        diff = requested_difficulty or Difficulty.BASIC
        if active_chapter == Chapter.SEQUENCES:
            reason = BilingualText(
                ar="بداية متدرجة في المتتاليات: نبدأ بحساب نهايات المتتاليات الأساسية لتثبيت القواعد والسرعة.",
                fr="Démarrage progressif en suites : on commence par les limites de suites de base.",
            )
        else:
            reason = BilingualText(
                ar="بداية متدرجة: نبدأ بأساسيات التعميل لقياس مستواك بهدوء وبناء الثقة.",
                fr="Démarrage progressif : on commence par la factorisation pour évaluer ton niveau en douceur.",
            )
        return AdaptivePlan(
            technique=first_tech,
            difficulty=diff,
            target_sub_pattern=None,
            avoid_sub_patterns=(),
            mode="warmup",
            reason=reason,
        )

    # Case B: Manual technique selected by user
    if requested_technique is not None:
        tech = requested_technique
        diff = requested_difficulty or choose_calibrated_difficulty(
            tech_mastery=eff_map[tech],
            tech_attempts=attempts_map[tech],
            global_mastery=global_mastery,
            recent_history=chapter_history,
            rng=r,
        )
        target_sub = _pick_due_sub_pattern(
            sub_pattern_rows=sub_pattern_rows,
            allowed_techs={tech},
            last_sub_pattern=last_sub_pattern,
            now=ref,
        )
        return AdaptivePlan(
            technique=tech,
            difficulty=diff,
            target_sub_pattern=target_sub,
            avoid_sub_patterns=avoid_subs,
            mode="review_weak" if target_sub else "balanced",
            reason=BilingualText(
                ar="تدريب موجّه على التقنية المختارة بمستوى يناسب إتقانك الحالي.",
                fr="Entraînement ciblé sur la technique choisie, calibré à ton niveau actuel.",
            ),
        )

    # Case C: Smart Adaptive Mode scoped to active_chapter
    last_two_failed = (
        len(chapter_history) >= 2
        and all(
            (h.get("solution_viewed") or h.get("is_correct") is False)
            for h in chapter_history[:2]
        )
    )
    if last_two_failed:
        best_tech = max(unlocked_techs, key=lambda t: eff_map[t])
        return AdaptivePlan(
            technique=best_tech,
            difficulty=requested_difficulty or Difficulty.BASIC,
            target_sub_pattern=None,
            avoid_sub_patterns=avoid_subs,
            mode="recover",
            reason=BilingualText(
                ar="تمرين تثبيت أساسي لاستعادة الثقة والتركيز قبل رفع الصعوبة مجدداً.",
                fr="Exercice de consolidation pour reprendre confiance avant d'augmenter la difficulté.",
            ),
        )

    due_candidates = _find_due_sub_patterns(
        sub_pattern_rows=sub_pattern_rows,
        allowed_techs=set(unlocked_techs),
        last_sub_pattern=last_sub_pattern,
        now=ref,
    )
    if due_candidates and r.random() < 0.72:
        chosen = due_candidates[0]
        tech = Technique(chosen["technique"])
        diff = requested_difficulty or choose_calibrated_difficulty(
            tech_mastery=eff_map[tech],
            tech_attempts=attempts_map[tech],
            global_mastery=global_mastery,
            recent_history=chapter_history,
            rng=r,
            is_weak_review=True,
        )
        return AdaptivePlan(
            technique=tech,
            difficulty=diff,
            target_sub_pattern=chosen["sub_pattern"],
            avoid_sub_patterns=avoid_subs,
            mode="review_weak",
            reason=BilingualText(
                ar="مراجعة ذكية متباعدة لنمط واجهت فيه صعوبة سابقاً لترسيخه نهائياً.",
                fr="Révision espacée ciblée sur un type d'exercice où tu as hésité récemment.",
            ),
        )

    new_unlocked = [t for t in unlocked_techs if attempts_map[t] == 0]
    if new_unlocked and r.random() < 0.70:
        tech = new_unlocked[0]
        diff = requested_difficulty or Difficulty.BASIC
        return AdaptivePlan(
            technique=tech,
            difficulty=diff,
            target_sub_pattern=None,
            avoid_sub_patterns=avoid_subs,
            mode="introduce_new",
            reason=BilingualText(
                ar="أحسنت التقدم! فتحنا لك تقنية جديدة بمستوى تمهيدي للتعرف عليها بسلاسة.",
                fr="Beau progrès ! Nouvelle technique débloquée en niveau d'initiation.",
            ),
        )

    slow_techs: list[Technique] = []
    for tech in unlocked_techs:
        row = mastery_rows.get(tech.value) or {}
        avg_t = row.get("avg_time_sec")
        if avg_t and attempts_map[tech] >= 2 and eff_map[tech] >= 0.45:
            est_diff = Difficulty.BASIC if eff_map[tech] < 0.55 else Difficulty.INTERMEDIATE
            if float(avg_t) > TIME_BENCHMARKS[est_diff]["normal"]:
                slow_techs.append(tech)

    last_tech = chapter_history[0]["technique"] if chapter_history else None
    last_two_same = (
        len(chapter_history) >= 2
        and chapter_history[0]["technique"] == chapter_history[1]["technique"]
    )

    if slow_techs and r.random() < 0.30:
        pool = [t for t in slow_techs if not (last_two_same and t.value == last_tech)] or slow_techs
        tech = r.choice(pool)
        diff = requested_difficulty or (Difficulty.BASIC if eff_map[tech] < 0.58 else Difficulty.INTERMEDIATE)
        return AdaptivePlan(
            technique=tech,
            difficulty=diff,
            target_sub_pattern=None,
            avoid_sub_patterns=avoid_subs,
            mode="fluency",
            reason=BilingualText(
                ar="تحسين السرعة والطلاقة: إجاباتك صحيحة، والآن نتدرب على الحل بزمن أسرع.",
                fr="Travail de fluidité : tes réponses sont justes, visons maintenant un rythme plus rapide.",
            ),
        )

    weights: list[float] = []
    for tech in unlocked_techs:
        gap = max(0.12, 1.12 - eff_map[tech])
        w = gap * gap
        if last_two_same and tech.value == last_tech and len(unlocked_techs) > 1:
            w *= 0.25
        elif tech.value == last_tech and len(unlocked_techs) > 1:
            w *= 0.65
        weights.append(w)

    tech = r.choices(unlocked_techs, weights=weights, k=1)[0]
    diff = requested_difficulty or choose_calibrated_difficulty(
        tech_mastery=eff_map[tech],
        tech_attempts=attempts_map[tech],
        global_mastery=global_mastery,
        recent_history=chapter_history,
        rng=r,
    )

    if diff == Difficulty.CHALLENGE:
        mode = "challenge"
        reason = BilingualText(
            ar="مستواك قوي في هذه التقنية — حان وقت تحدّي بمستوى الفروض المحروسة!",
            fr="Niveau solide sur cette technique — place à un défi de niveau Devoir Surveillé !",
        )
    elif eff_map[tech] < 0.48:
        mode = "balanced"
        reason = BilingualText(
            ar="تركيز تكيّفي على نقطة تحتاج تقوية بمستوى متدرج ومناسب لك.",
            fr="Focus adaptatif sur une compétence à renforcer, à un niveau progressif.",
        )
    else:
        mode = "balanced"
        reason = BilingualText(
            ar="اختيار ذكي متوازن لرفع إتقانك وتحضيرك للفخاخ الكلاسيكية للباك.",
            fr="Sélection équilibrée pour consolider ta maîtrise et les pièges classiques du Bac.",
        )

    return AdaptivePlan(
        technique=tech,
        difficulty=diff,
        target_sub_pattern=None,
        avoid_sub_patterns=avoid_subs,
        mode=mode,
        reason=reason,
    )


def _find_due_sub_patterns(
    *,
    sub_pattern_rows: list[dict[str, Any]],
    allowed_techs: set[Technique],
    last_sub_pattern: str | None,
    now: datetime,
) -> list[dict[str, Any]]:
    allowed_vals = {t.value for t in allowed_techs}
    due: list[tuple[float, dict[str, Any]]] = []
    for row in sub_pattern_rows:
        if row.get("technique") not in allowed_vals:
            continue
        sp_name = row.get("sub_pattern")
        if not sp_name or sp_name == last_sub_pattern:
            continue
        m = float(row.get("mastery", INITIAL_MASTERY))
        next_dt = _parse_iso(row.get("next_review_at"))
        is_due = next_dt is not None and next_dt <= now
        if is_due and m < 0.78:
            overdue_hours = max(0.0, (now - next_dt).total_seconds() / 3600.0) if next_dt else 0.0
            priority = (1.0 - m) * 2.0 + min(2.0, math.log1p(overdue_hours) * 0.35)
            due.append((priority, row))
    due.sort(key=lambda pair: pair[0], reverse=True)
    return [row for _, row in due]


def _pick_due_sub_pattern(
    *,
    sub_pattern_rows: list[dict[str, Any]],
    allowed_techs: set[Technique],
    last_sub_pattern: str | None,
    now: datetime,
) -> str | None:
    due = _find_due_sub_patterns(
        sub_pattern_rows=sub_pattern_rows,
        allowed_techs=allowed_techs,
        last_sub_pattern=last_sub_pattern,
        now=now,
    )
    return due[0]["sub_pattern"] if due else None
