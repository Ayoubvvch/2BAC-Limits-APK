package com.ayoub.baclimits.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class LocalizedMessage(val ar: String, val fr: String) {
    fun get(lang: String): String = if (lang == "fr") fr.ifBlank { ar } else ar.ifBlank { fr }
}

data class TechniqueMastery(
    val technique: String,
    val mastery: Double,
    val correct: Int,
    val attempts: Int
) {
    val percent: Int get() = (mastery * 100.0).roundToInt().coerceIn(0, 100)
}

data class HistoryEntry(
    val seed: String,
    val isCorrect: Boolean?,
    val fullLimitLatex: String
) {
    val technique: String
        get() {
            val code = seed.uppercase().split("-").getOrNull(1).orEmpty()
            return when (code) {
                "POLY" -> "poly_factor"
                "SQRT" -> "sqrt_conjugate"
                "NROT" -> "nth_root"
                "ADD0" -> "add_sub_zero"
                "TRIG" -> "trig"
                "HYBR" -> "hybrid"
                else -> "adaptive"
            }
        }

    val difficulty: Int
        get() {
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
    val recentHistory: List<HistoryEntry>
)

data class ExerciseData(
    val seed: String,
    val technique: String,
    val difficulty: Int,
    val fullLimitLatex: String,
    val totalHints: Int
)

data class GradeFeedback(
    val isCorrect: Boolean,
    val message: LocalizedMessage
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

data class ResumeState(
    val hasExercise: Boolean,
    val exercise: ExerciseData?,
    val hints: List<LocalizedMessage>,
    val lastGrade: GradeFeedback?,
    val solution: SolutionData?,
    val studentAnswer: String
)

data class SubmitResult(
    val grade: GradeFeedback,
    val stats: StudentStats?,
    val solution: SolutionData?
)

object LimitsJsonParser {

    fun parseApiError(bodyJson: String, status: Int): String {
        return try {
            val obj = JSONObject(bodyJson)
            val detail = obj.opt("detail")
            when (detail) {
                is JSONArray -> {
                    val msgs = mutableListOf<String>()
                    for (i in 0 until detail.length()) {
                        val item = detail.optJSONObject(i)
                        val msg = item?.optString("msg", "").orEmpty().ifBlank { detail.optString(i) }
                        if (msg.isNotBlank()) msgs.add(msg)
                    }
                    msgs.joinToString(", ").ifBlank { "HTTP $status" }
                }
                is String -> detail.ifBlank { "HTTP $status" }
                else -> obj.optString("detail", "HTTP $status").ifBlank { "HTTP $status" }
            }
        } catch (_: Exception) {
            "HTTP $status"
        }
    }

    fun parseLocalizedMessage(obj: JSONObject?): LocalizedMessage {
        if (obj == null) return LocalizedMessage("", "")
        return LocalizedMessage(
            ar = obj.optString("ar", ""),
            fr = obj.optString("fr", "")
        )
    }

    fun parseStats(json: JSONObject, existingFallback: StudentStats? = null): StudentStats {
        val totalSolved = json.optInt("total_solved", existingFallback?.totalSolved ?: 0)
        val currentStreak = json.optInt("current_streak", existingFallback?.currentStreak ?: 0)

        val masteryList = mutableListOf<TechniqueMastery>()
        var sumAttempts = 0
        val masteryArr = json.optJSONArray("mastery_by_technique")
        if (masteryArr != null) {
            for (i in 0 until masteryArr.length()) {
                val item = masteryArr.optJSONObject(i) ?: continue
                val att = item.optInt("attempts", 0)
                val cor = item.optInt("correct", 0)
                sumAttempts += att
                masteryList.add(
                    TechniqueMastery(
                        technique = item.optString("technique", "adaptive"),
                        mastery = item.optDouble("mastery", if (att == 0) 0.0 else cor.toDouble() / att.toDouble()),
                        correct = cor,
                        attempts = att
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
                val seed = h.optString("seed", "").trim()
                if (seed.isEmpty()) continue
                val isCorrect: Boolean? = if (h.isNull("is_correct")) null else h.optBoolean("is_correct", false)
                historyList.add(
                    HistoryEntry(
                        seed = seed,
                        isCorrect = isCorrect,
                        fullLimitLatex = h.optString("full_limit_latex", "")
                    )
                )
            }
        } else if (existingFallback != null) {
            historyList.addAll(existingFallback.recentHistory)
        }

        return StudentStats(
            username = json.optString("username", existingFallback?.username ?: "").trim(),
            displayName = json.optString("display_name", existingFallback?.displayName ?: "").trim(),
            totalSolved = totalSolved,
            accuracy = accuracy,
            currentStreak = currentStreak,
            masteryByTechnique = masteryList,
            recentHistory = historyList
        )
    }

    fun parseExercise(json: JSONObject): ExerciseData {
        return ExerciseData(
            seed = json.optString("seed", ""),
            technique = json.optString("technique", "adaptive"),
            difficulty = json.optInt("difficulty", 1),
            fullLimitLatex = json.optString("full_limit_latex", ""),
            totalHints = json.optInt("total_hints", 0)
        )
    }

    fun parseGrade(json: JSONObject?): GradeFeedback? {
        if (json == null) return null
        return GradeFeedback(
            isCorrect = json.optBoolean("is_correct", false),
            message = parseLocalizedMessage(json.optJSONObject("message"))
        )
    }

    fun parseSolution(json: JSONObject?): SolutionData? {
        if (json == null) return null
        val stepsArr = json.optJSONArray("steps")
        val steps = mutableListOf<SolutionStepData>()
        if (stepsArr != null) {
            for (i in 0 until stepsArr.length()) {
                val st = stepsArr.optJSONObject(i) ?: continue
                val latexRaw = if (st.isNull("latex")) null else st.optString("latex", "").takeIf { it.isNotBlank() }
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
            answerLatex = json.optString("answer_latex", ""),
            steps = steps
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
        return ResumeState(
            hasExercise = hasEx && exObj != null,
            exercise = exObj?.let { parseExercise(it) },
            hints = hints,
            lastGrade = if (json.isNull("last_grade")) null else parseGrade(json.optJSONObject("last_grade")),
            solution = if (json.isNull("solution")) null else parseSolution(json.optJSONObject("solution")),
            studentAnswer = json.optString("student_answer", "")
        )
    }

    fun parseSubmit(json: JSONObject, existingFallback: StudentStats?): SubmitResult {
        val grade = parseGrade(json.optJSONObject("grade"))
            ?: GradeFeedback(false, LocalizedMessage("", ""))
        val statsObj = json.optJSONObject("stats")
        val stats = statsObj?.let { parseStats(it, existingFallback) }
        val sol = if (json.isNull("solution")) null else parseSolution(json.optJSONObject("solution"))
        return SubmitResult(grade = grade, stats = stats, solution = sol)
    }
}
