package com.ayoub.baclimits.data

import com.ayoub.baclimits.math.LimitsCasEngine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class LocalizedMessage(val ar: String, val fr: String) {
    fun get(lang: String): String = if (lang == "fr") fr.ifBlank { ar } else ar.ifBlank { fr }
}

data class TimerState(
    val state: String = "ready", // "ready", "running", "paused", "completed"
    val elapsedSec: Double = 0.0,
    val liveElapsedSec: Double = 0.0,
    val startedAt: String? = null,
    val targetFastSec: Int = 45,
    val targetNormalSec: Int = 120
)

data class CoachInsight(
    val mode: String = "balanced",
    val reason: LocalizedMessage = LocalizedMessage("", "")
)

data class FluencyRating(
    val code: String = "good", // "fast", "good", "slow", "assisted", "revealed", "incorrect"
    val label: LocalizedMessage = LocalizedMessage("", "")
)

data class TechniqueMastery(
    val technique: String,
    val mastery: Double,
    val correct: Int,
    val attempts: Int,
    val chapter: String = LimitsCasEngine.chapterOfTechnique(technique),
    val effectiveMastery: Double = mastery,
    val avgTimeSec: Double? = null,
    val isUnlocked: Boolean = true,
    val stage: Int = 1,
    val unlockHint: LocalizedMessage? = null
) {
    val percent: Int get() = (effectiveMastery * 100.0).roundToInt().coerceIn(0, 100)
}

data class HistoryEntry(
    val seed: String,
    val isCorrect: Boolean?,
    val fullLimitLatex: String,
    val chapter: String = "",
    val techniqueOverride: String = "",
    val difficultyOverride: Int = 0,
    val isRevealed: Boolean = true,
    val elapsedSec: Double = 0.0,
    val fluencyCode: String? = null,
    val solutionViewed: Boolean = false
) {
    val technique: String
        get() = techniqueOverride.ifBlank { RemoteSyncProtocol.inferTechniqueFromSeed(seed) }

    val resolvedChapter: String
        get() = chapter.ifBlank { LimitsCasEngine.chapterOfTechnique(technique) }

    val difficulty: Int
        get() {
            if (difficultyOverride in 1..3) return difficultyOverride
            val lvl = seed.uppercase().split("-").getOrNull(2).orEmpty()
            return lvl.removePrefix("L").toIntOrNull()?.coerceIn(1, 3) ?: 1
        }
}

data class StudentStats(
    val username: String,
    val displayName: String,
    val totalSolved: Int,
    val accuracy: Int,
    val currentStreak: Int,
    val masteryByTechnique: List<TechniqueMastery>,
    val recentHistory: List<HistoryEntry>,
    val bestStreak: Int = currentStreak,
    val totalAttempted: Int = 0,
    val avgSolveTimeSec: Double? = null,
    val overallMastery: Double = 0.28,
    val dueReviewsCount: Int = 0,
    val activeLock: Boolean = false,
    val activeSeed: String? = null,
    val solvedByChapter: Map<String, Int> = emptyMap()
)

data class ExerciseData(
    val seed: String,
    val technique: String,
    val difficulty: Int,
    val fullLimitLatex: String,
    val totalHints: Int,
    val chapter: String = LimitsCasEngine.chapterOfTechnique(technique),
    val answerKind: String = "constant",
    val prompt: LocalizedMessage? = null,
    val isRevealed: Boolean = true,
    val isCompleted: Boolean = false,
    val verifiedByCas: Boolean = true,
    val timer: TimerState = TimerState(state = if (isRevealed) "paused" else "ready"),
    val coach: CoachInsight? = null
)

data class GradeFeedback(
    val isCorrect: Boolean,
    val message: LocalizedMessage,
    val validSyntax: Boolean = true,
    val studentLatex: String = "",
    val expectedLatex: String = ""
)

data class SolutionStepData(
    val title: LocalizedMessage,
    val explanation: LocalizedMessage,
    val latex: String?
)

data class SolutionData(
    val answerLatex: String,
    val steps: List<SolutionStepData>
)

data class SolutionResult(
    val solution: SolutionData,
    val fluency: FluencyRating? = null,
    val timer: TimerState? = null,
    val isCompleted: Boolean = true,
    val stats: StudentStats? = null,
    val fullLimitLatex: String? = null
)

data class PauseResult(
    val seed: String,
    val timer: TimerState,
    val isRevealed: Boolean,
    val isCompleted: Boolean
)

data class ResumeState(
    val hasExercise: Boolean,
    val exercise: ExerciseData?,
    val hints: List<LocalizedMessage>,
    val lastGrade: GradeFeedback?,
    val solution: SolutionData?,
    val studentAnswer: String,
    val fluency: FluencyRating? = null
)

data class SubmitResult(
    val grade: GradeFeedback,
    val stats: StudentStats?,
    val solution: SolutionData?,
    val fluency: FluencyRating? = null,
    val timer: TimerState? = null,
    val isCompleted: Boolean = false
)

internal fun JSONObject.optCleanString(key: String, fallback: String = ""): String {
    if (!has(key) || isNull(key)) return fallback
    val s = optString(key, fallback)
    return if (s == "null") fallback else s
}

internal fun JSONObject.optNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val d = optDouble(key, Double.NaN)
    return if (d.isNaN()) null else d
}

object LimitsJsonParser {

    fun parseApiError(bodyJson: String, status: Int, lang: String = "ar"): String {
        return try {
            val obj = JSONObject(bodyJson)
            val detail = obj.opt("detail")
            when (detail) {
                is JSONObject -> {
                    val primary = detail.optCleanString(lang)
                    val fallback = detail.optCleanString(if (lang == "fr") "ar" else "fr")
                    primary.ifBlank { fallback }.ifBlank { detail.optCleanString("message") }.ifBlank { "HTTP $status" }
                }
                is JSONArray -> {
                    val msgs = mutableListOf<String>()
                    for (i in 0 until detail.length()) {
                        val item = detail.optJSONObject(i)
                        val msg = item?.optCleanString("msg").orEmpty().ifBlank { detail.optString(i) }
                        if (msg.isNotBlank()) msgs.add(msg)
                    }
                    msgs.joinToString(", ").ifBlank { "HTTP $status" }
                }
                is String -> detail.ifBlank { "HTTP $status" }
                else -> obj.optCleanString("detail", "HTTP $status").ifBlank { "HTTP $status" }
            }
        } catch (_: Exception) {
            "HTTP $status"
        }
    }

    fun parseLocalizedMessage(obj: JSONObject?): LocalizedMessage {
        if (obj == null) return LocalizedMessage("", "")
        return LocalizedMessage(
            ar = obj.optCleanString("ar"),
            fr = obj.optCleanString("fr")
        )
    }

    fun parseTimer(obj: JSONObject?, defaultRevealed: Boolean = true, defaultCompleted: Boolean = false): TimerState {
        if (obj == null) {
            val fallbackState = when {
                defaultCompleted -> "completed"
                defaultRevealed -> "paused"
                else -> "ready"
            }
            return TimerState(state = fallbackState)
        }
        return TimerState(
            state = obj.optCleanString("state", "ready"),
            elapsedSec = obj.optDouble("elapsed_sec", 0.0).let { if (it.isNaN()) 0.0 else it },
            liveElapsedSec = obj.optDouble("live_elapsed_sec", obj.optDouble("elapsed_sec", 0.0)).let { if (it.isNaN()) 0.0 else it },
            startedAt = obj.optCleanString("started_at").takeIf { it.isNotBlank() },
            targetFastSec = obj.optInt("target_fast_sec", 45),
            targetNormalSec = obj.optInt("target_normal_sec", 120)
        )
    }

    fun parseCoach(obj: JSONObject?): CoachInsight? {
        if (obj == null) return null
        val reason = parseLocalizedMessage(obj.optJSONObject("reason"))
        if (reason.ar.isBlank() && reason.fr.isBlank()) return null
        return CoachInsight(
            mode = obj.optCleanString("mode", "balanced"),
            reason = reason
        )
    }

    fun parseFluency(obj: JSONObject?): FluencyRating? {
        if (obj == null) return null
        val code = obj.optCleanString("code")
        val label = parseLocalizedMessage(obj.optJSONObject("label"))
        if (code.isBlank() && label.ar.isBlank() && label.fr.isBlank()) return null
        return FluencyRating(code = code.ifBlank { "good" }, label = label)
    }

    fun parseStats(json: JSONObject, existingFallback: StudentStats? = null): StudentStats {
        val totalSolved = json.optInt("total_solved", existingFallback?.totalSolved ?: 0)
        val currentStreak = json.optInt("current_streak", existingFallback?.currentStreak ?: 0)
        val bestStreak = json.optInt("best_streak", maxOf(currentStreak, existingFallback?.bestStreak ?: 0))

        val masteryList = mutableListOf<TechniqueMastery>()
        var sumAttempts = 0
        val masteryArr = json.optJSONArray("mastery_by_technique")
        if (masteryArr != null) {
            for (i in 0 until masteryArr.length()) {
                val item = masteryArr.optJSONObject(i) ?: continue
                val tech = item.optCleanString("technique", "adaptive")
                val att = item.optInt("attempts", 0)
                val cor = item.optInt("correct", 0)
                sumAttempts += att
                val rawM = item.optDouble("mastery", if (att == 0) 0.28 else cor.toDouble() / att.toDouble())
                val effM = item.optDouble("effective_mastery", rawM)
                val chapter = item.optCleanString("chapter").ifBlank { LimitsCasEngine.chapterOfTechnique(tech) }
                val unlockHint = if (!item.isNull("unlock_hint")) parseLocalizedMessage(item.optJSONObject("unlock_hint")) else null
                masteryList.add(
                    TechniqueMastery(
                        technique = tech,
                        mastery = if (rawM.isNaN()) 0.28 else rawM,
                        correct = cor,
                        attempts = att,
                        chapter = chapter,
                        effectiveMastery = if (effM.isNaN()) rawM else effM,
                        avgTimeSec = item.optNullableDouble("avg_time_sec"),
                        isUnlocked = item.optBoolean("is_unlocked", true),
                        stage = item.optInt("stage", 1),
                        unlockHint = unlockHint
                    )
                )
            }
        } else if (existingFallback != null) {
            masteryList.addAll(existingFallback.masteryByTechnique)
        }

        val totalAttempts = json.optInt(
            "total_attempted",
            json.optInt("total_attempts", sumAttempts)
        )
        val accuracy = RemoteSyncProtocol.extractAccuracyPercent(json, totalSolved, totalAttempts)

        val historyList = mutableListOf<HistoryEntry>()
        val histArr = json.optJSONArray("recent_history")
        if (histArr != null) {
            for (i in 0 until histArr.length()) {
                val h = histArr.optJSONObject(i) ?: continue
                val seed = h.optCleanString("seed").trim()
                if (seed.isEmpty()) continue
                val isCorrect: Boolean? = if (h.isNull("is_correct")) null else h.optBoolean("is_correct", false)
                val tech = h.optCleanString("technique")
                val chap = h.optCleanString("chapter")
                val diff = h.optInt("difficulty", 0)
                val revealed = if (h.has("is_revealed")) h.optBoolean("is_revealed", true) else true
                historyList.add(
                    HistoryEntry(
                        seed = seed,
                        isCorrect = isCorrect,
                        fullLimitLatex = h.optCleanString("full_limit_latex"),
                        chapter = chap,
                        techniqueOverride = tech,
                        difficultyOverride = diff,
                        isRevealed = revealed,
                        elapsedSec = h.optDouble("elapsed_sec", 0.0).let { if (it.isNaN()) 0.0 else it },
                        fluencyCode = h.optCleanString("fluency_code").takeIf { it.isNotBlank() },
                        solutionViewed = h.optBoolean("solution_viewed", false)
                    )
                )
            }
        } else if (existingFallback != null) {
            historyList.addAll(existingFallback.recentHistory)
        }

        val solvedByChapter = mutableMapOf<String, Int>()
        val sbcObj = json.optJSONObject("solved_by_chapter")
        if (sbcObj != null) {
            sbcObj.keys().forEach { k ->
                solvedByChapter[k] = sbcObj.optInt(k, 0)
            }
        }

        return StudentStats(
            username = json.optCleanString("username", existingFallback?.username ?: "").trim(),
            displayName = json.optCleanString("display_name", existingFallback?.displayName ?: "").trim(),
            totalSolved = totalSolved,
            accuracy = accuracy,
            currentStreak = currentStreak,
            masteryByTechnique = masteryList,
            recentHistory = historyList,
            bestStreak = bestStreak,
            totalAttempted = totalAttempts,
            avgSolveTimeSec = json.optNullableDouble("avg_solve_time_sec"),
            overallMastery = json.optDouble("overall_mastery", 0.28).let { if (it.isNaN()) 0.28 else it },
            dueReviewsCount = json.optInt("due_reviews_count", 0),
            activeLock = json.optBoolean("active_lock", false),
            activeSeed = json.optCleanString("active_seed").takeIf { it.isNotBlank() },
            solvedByChapter = solvedByChapter
        )
    }

    fun parseExercise(json: JSONObject): ExerciseData {
        val tech = json.optCleanString("technique", "adaptive")
        val isRevealed = if (json.has("is_revealed")) json.optBoolean("is_revealed", true) else true
        val isCompleted = json.optBoolean("is_completed", false)
        val promptObj = if (json.isNull("prompt")) null else json.optJSONObject("prompt")
        val coachObj = if (json.isNull("coach")) null else json.optJSONObject("coach")
        val timerObj = if (json.isNull("timer")) null else json.optJSONObject("timer")

        return ExerciseData(
            seed = json.optCleanString("seed"),
            technique = tech,
            difficulty = json.optInt("difficulty", 1),
            fullLimitLatex = json.optCleanString("full_limit_latex"),
            totalHints = json.optInt("total_hints", 0),
            chapter = json.optCleanString("chapter").ifBlank { LimitsCasEngine.chapterOfTechnique(tech) },
            answerKind = json.optCleanString("answer_kind", "constant").ifBlank { "constant" },
            prompt = promptObj?.let { parseLocalizedMessage(it) },
            isRevealed = isRevealed,
            isCompleted = isCompleted,
            verifiedByCas = json.optBoolean("verified_by_cas", true),
            timer = parseTimer(timerObj, defaultRevealed = isRevealed, defaultCompleted = isCompleted),
            coach = parseCoach(coachObj)
        )
    }

    fun parsePause(json: JSONObject): PauseResult {
        val isRevealed = json.optBoolean("is_revealed", true)
        val isCompleted = json.optBoolean("is_completed", false)
        return PauseResult(
            seed = json.optCleanString("seed"),
            timer = parseTimer(json.optJSONObject("timer"), defaultRevealed = isRevealed, defaultCompleted = isCompleted),
            isRevealed = isRevealed,
            isCompleted = isCompleted
        )
    }

    fun parseGrade(json: JSONObject?): GradeFeedback? {
        if (json == null) return null
        return GradeFeedback(
            isCorrect = json.optBoolean("is_correct", false),
            message = parseLocalizedMessage(json.optJSONObject("message")),
            validSyntax = json.optBoolean("valid_syntax", true),
            studentLatex = json.optCleanString("student_latex"),
            expectedLatex = json.optCleanString("expected_latex")
        )
    }

    fun parseSolution(json: JSONObject?): SolutionData? {
        if (json == null) return null
        val stepsArr = json.optJSONArray("steps")
        val steps = mutableListOf<SolutionStepData>()
        if (stepsArr != null) {
            for (i in 0 until stepsArr.length()) {
                val st = stepsArr.optJSONObject(i) ?: continue
                val latexRaw = st.optCleanString("latex").takeIf { it.isNotBlank() }
                steps.add(
                    SolutionStepData(
                        title = parseLocalizedMessage(st.optJSONObject("title")),
                        explanation = parseLocalizedMessage(st.optJSONObject("explanation")),
                        latex = latexRaw
                    )
                )
            }
        }
        return SolutionData(
            answerLatex = json.optCleanString("answer_latex"),
            steps = steps
        )
    }

    fun parseSolutionResult(json: JSONObject, existingFallback: StudentStats?): SolutionResult? {
        val sol = parseSolution(json) ?: return null
        val fluency = if (json.isNull("fluency")) null else parseFluency(json.optJSONObject("fluency"))
        val timer = if (json.isNull("timer")) null else parseTimer(json.optJSONObject("timer"), defaultRevealed = true, defaultCompleted = true)
        val statsObj = if (json.isNull("stats")) null else json.optJSONObject("stats")
        val stats = statsObj?.let { parseStats(it, existingFallback) }
        return SolutionResult(
            solution = sol,
            fluency = fluency,
            timer = timer,
            isCompleted = json.optBoolean("is_completed", true),
            stats = stats,
            fullLimitLatex = json.optCleanString("full_limit_latex").takeIf { it.isNotBlank() }
        )
    }

    fun parseResume(json: JSONObject): ResumeState {
        val hasEx = json.optBoolean("has_exercise", false)
        val exObj = if (hasEx) json.optJSONObject("exercise") else null
        val hintsArr = json.optJSONArray("hints")
        val hints = mutableListOf<LocalizedMessage>()
        if (hintsArr != null) {
            for (i in 0 until hintsArr.length()) {
                val h = hintsArr.optJSONObject(i)
                if (h != null) hints.add(parseLocalizedMessage(h))
            }
        }
        val fluency = if (json.isNull("fluency")) null else parseFluency(json.optJSONObject("fluency"))
        return ResumeState(
            hasExercise = hasEx && exObj != null,
            exercise = exObj?.let { parseExercise(it) },
            hints = hints,
            lastGrade = if (json.isNull("last_grade")) null else parseGrade(json.optJSONObject("last_grade")),
            solution = if (json.isNull("solution")) null else parseSolution(json.optJSONObject("solution")),
            studentAnswer = json.optCleanString("student_answer"),
            fluency = fluency
        )
    }

    fun parseSubmit(json: JSONObject, existingFallback: StudentStats?): SubmitResult {
        val grade = parseGrade(json.optJSONObject("grade"))
            ?: GradeFeedback(false, LocalizedMessage("", ""))
        val statsObj = if (json.isNull("stats")) null else json.optJSONObject("stats")
        val stats = statsObj?.let { parseStats(it, existingFallback) }
        val sol = if (json.isNull("solution")) null else parseSolution(json.optJSONObject("solution"))
        val fluency = if (json.isNull("fluency")) null else parseFluency(json.optJSONObject("fluency"))
        val timer = if (json.isNull("timer")) null else parseTimer(json.optJSONObject("timer"), defaultRevealed = true, defaultCompleted = grade.isCorrect)
        val isCompleted = json.optBoolean("is_completed", grade.isCorrect)
        return SubmitResult(
            grade = grade,
            stats = stats,
            solution = sol,
            fluency = fluency,
            timer = timer,
            isCompleted = isCompleted
        )
    }
}
