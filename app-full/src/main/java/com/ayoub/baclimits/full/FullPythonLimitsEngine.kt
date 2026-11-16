package com.ayoub.baclimits.full

import android.content.Context
import android.util.Log
import com.ayoub.baclimits.data.EvaluationResult
import com.ayoub.baclimits.data.OfflineLimitsEngine
import com.ayoub.baclimits.math.ExpectedAnswer
import com.ayoub.baclimits.math.GeneratedExercise
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.math.LocalizedText
import com.ayoub.baclimits.math.MathExprEvaluator
import com.ayoub.baclimits.math.SolutionStep
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class FullPythonLimitsEngine(private val context: Context) : OfflineLimitsEngine {

    companion object {
        private const val TAG = "FullPythonLimitsEngine"
    }

    override val edition: String = "full"
    override val engineName: String = "Python 3.11 + SymPy CAS (Full)"
    override val isPythonPowered: Boolean = true

    private val isInitialized = AtomicBoolean(false)
    private var initError: String? = null

    // Cache to hold answer_sympy_str by seed for exact symbolic grading
    private val sympyStrCache = mutableMapOf<String, String>()

    init {
        Thread {
            ensurePythonInitialized()
        }.start()
    }

    private fun ensurePythonInitialized(): Boolean {
        if (isInitialized.get()) return true
        synchronized(this) {
            if (isInitialized.get()) return true
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
                isInitialized.set(true)
                Log.i(TAG, "Chaquopy Python 3.11 engine initialized successfully.")
                return true
            } catch (e: Throwable) {
                initError = e.message
                Log.e(TAG, "Failed to start Chaquopy Python engine: ${e.message}", e)
                return false
            }
        }
    }

    override fun generate(
        requestedTechnique: String,
        requestedDifficulty: Int,
        explicitSeed: String?,
        weakestTechnique: String?,
        suggestedDifficulty: Int,
        masteryJson: String?
    ): GeneratedExercise {
        if (ensurePythonInitialized()) {
            try {
                val py = Python.getInstance()
                val bridge = py.getModule("engine_bridge")
                val resStr = bridge.callAttr(
                    "generate_exercise_json",
                    requestedTechnique,
                    requestedDifficulty,
                    explicitSeed,
                    masteryJson
                ).toString()

                val resJson = JSONObject(resStr)
                if (resJson.optString("status") == "ok") {
                    val exJson = resJson.getJSONObject("exercise")
                    val seed = exJson.getString("seed")
                    val sympyStr = exJson.optString("answer_sympy_str", "")
                    if (sympyStr.isNotBlank()) {
                        synchronized(sympyStrCache) {
                            sympyStrCache[seed] = sympyStr
                        }
                    }

                    return parseExerciseFromJson(exJson)
                } else {
                    Log.w(TAG, "Python engine error: ${resJson.optString("error")}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Python exercise generation: ${e.message}", e)
            }
        }

        // Safe fallback to local Kotlin CAS engine if Python encounters an edge case
        return LimitsCasEngine.generate(
            requestedTechnique = requestedTechnique,
            requestedDifficulty = requestedDifficulty,
            explicitSeed = explicitSeed,
            weakestTechnique = weakestTechnique ?: "adaptive",
            suggestedDifficulty = suggestedDifficulty
        )
    }

    override fun isValidSeed(seed: String): Boolean {
        if (ensurePythonInitialized()) {
            try {
                val py = Python.getInstance()
                val bridge = py.getModule("engine_bridge")
                return bridge.callAttr("is_valid_seed", seed).toBoolean()
            } catch (_: Exception) {}
        }
        return LimitsCasEngine.isValidSeed(seed)
    }

    override fun evaluateAnswer(
        expectedAnswer: ExpectedAnswer,
        studentAnswer: String,
        expectedLatex: String?,
        expectedSympyStr: String?
    ): EvaluationResult {
        val targetSympy = expectedSympyStr ?: run {
            when (expectedAnswer) {
                is ExpectedAnswer.PosInf -> "oo"
                is ExpectedAnswer.NegInf -> "-oo"
                is ExpectedAnswer.Finite -> expectedLatex ?: expectedAnswer.value.toString()
            }
        }

        if (ensurePythonInitialized() && targetSympy.isNotBlank()) {
            try {
                val py = Python.getInstance()
                val bridge = py.getModule("engine_bridge")
                val resStr = bridge.callAttr("grade_answer_json", studentAnswer, targetSympy).toString()
                val resJson = JSONObject(resStr)
                if (resJson.optString("status") == "ok") {
                    val grade = resJson.getJSONObject("grade")
                    val isCorrect = grade.optBoolean("is_correct", false)
                    val msgObj = grade.optJSONObject("message")
                    val msgAr = msgObj?.optString("ar") ?: if (isCorrect) "أحسنت! إجابة صحيحة تماماً." else "إجابة غير صحيحة."
                    val msgFr = msgObj?.optString("fr") ?: if (isCorrect) "Bravo ! Votre réponse est exacte." else "Réponse incorrecte."
                    val validSyntax = grade.optBoolean("valid_syntax", true)

                    return EvaluationResult(
                        isCorrect = isCorrect,
                        messageAr = msgAr,
                        messageFr = msgFr,
                        validSyntax = validSyntax
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Python grading: ${e.message}", e)
            }
        }

        // Fallback to Kotlin evaluator
        val eval = MathExprEvaluator.evaluate(studentAnswer)
        val isCorrect = MathExprEvaluator.isEquivalent(expectedAnswer, studentAnswer)
        val (msgAr, msgFr) = when {
            isCorrect -> "أحسنت! إجابة صحيحة ومتطابقة رمزياً." to "Bravo ! Réponse exacte et symboliquement équivalente."
            eval is com.ayoub.baclimits.math.EvalResult.Invalid ->
                "تعذر فهم الصيغة الرياضية (${eval.reason}). جرب: 1/2 أو sqrt(2)/2 أو +inf" to "Expression invalide (${eval.reason})."
            else -> "إجابة غير صحيحة. راجع الإشارة والمعاملات ثم أعد المحاولة!" to "Réponse incorrecte."
        }
        return EvaluationResult(
            isCorrect = isCorrect,
            messageAr = msgAr,
            messageFr = msgFr,
            validSyntax = eval !is com.ayoub.baclimits.math.EvalResult.Invalid
        )
    }

    private fun parseExerciseFromJson(json: JSONObject): GeneratedExercise {
        val seed = json.getString("seed")
        val technique = json.optString("technique", "adaptive")
        val difficulty = json.optInt("difficulty", 1)
        val fullLimitLatex = json.optString("full_limit_latex", "")
        val answerLatex = json.optString("answer_latex", "")
        val answerSympyStr = json.optString("answer_sympy_str", "")

        val hints = mutableListOf<LocalizedText>()
        val hintsArr = json.optJSONArray("hints")
        if (hintsArr != null) {
            for (i in 0 until hintsArr.length()) {
                val h = hintsArr.optJSONObject(i) ?: continue
                hints.add(LocalizedText(ar = h.optString("ar", ""), fr = h.optString("fr", "")))
            }
        }

        val steps = mutableListOf<SolutionStep>()
        val stepsArr = json.optJSONArray("steps")
        if (stepsArr != null) {
            for (i in 0 until stepsArr.length()) {
                val s = stepsArr.optJSONObject(i) ?: continue
                val titleObj = s.optJSONObject("title")
                val expObj = s.optJSONObject("explanation")
                val latex = if (s.isNull("latex")) null else s.optString("latex", "").takeIf { it.isNotBlank() }
                steps.add(
                    SolutionStep(
                        title = LocalizedText(titleObj?.optString("ar", "").orEmpty(), titleObj?.optString("fr", "").orEmpty()),
                        explanation = LocalizedText(expObj?.optString("ar", "").orEmpty(), expObj?.optString("fr", "").orEmpty()),
                        latex = latex
                    )
                )
            }
        }

        val expectedAnswer: ExpectedAnswer = when (answerSympyStr.trim()) {
            "oo", "+oo" -> ExpectedAnswer.PosInf
            "-oo" -> ExpectedAnswer.NegInf
            else -> {
                val doubleVal = answerSympyStr.toDoubleOrNull()
                if (doubleVal != null) {
                    ExpectedAnswer.Finite(doubleVal)
                } else {
                    // Try parsing simple fractions like "3/2"
                    val parts = answerSympyStr.split("/")
                    if (parts.size == 2) {
                        val num = parts[0].trim().toDoubleOrNull()
                        val den = parts[1].trim().toDoubleOrNull()
                        if (num != null && den != null && den != 0.0) {
                            ExpectedAnswer.Finite(num / den)
                        } else {
                            ExpectedAnswer.Finite(0.0)
                        }
                    } else {
                        ExpectedAnswer.Finite(0.0)
                    }
                }
            }
        }

        return GeneratedExercise(
            seed = seed,
            technique = technique,
            difficulty = difficulty,
            fullLimitLatex = fullLimitLatex,
            hints = hints,
            answerLatex = answerLatex,
            expectedAnswer = expectedAnswer,
            steps = steps
        )
    }
}
