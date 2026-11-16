package com.ayoub.baclimits.data

import com.ayoub.baclimits.math.ExpectedAnswer
import com.ayoub.baclimits.math.GeneratedExercise
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.math.MathExprEvaluator

data class EvaluationResult(
    val isCorrect: Boolean,
    val messageAr: String,
    val messageFr: String,
    val validSyntax: Boolean = true
)

interface OfflineLimitsEngine {
    val edition: String           // "lite" or "full"
    val engineName: String        // "Kotlin CAS Engine (Lite)" or "Python 3.11 + SymPy (Full)"
    val isPythonPowered: Boolean

    fun generate(
        requestedTechnique: String,
        requestedDifficulty: Int,
        explicitSeed: String?,
        weakestTechnique: String? = null,
        suggestedDifficulty: Int = 1,
        masteryJson: String? = null
    ): GeneratedExercise

    fun isValidSeed(seed: String): Boolean

    fun evaluateAnswer(
        expectedAnswer: ExpectedAnswer,
        studentAnswer: String,
        expectedLatex: String? = null,
        expectedSympyStr: String? = null
    ): EvaluationResult
}

object LiteKotlinLimitsEngine : OfflineLimitsEngine {
    override val edition: String = "lite"
    override val engineName: String = "Kotlin CAS Engine (Lite)"
    override val isPythonPowered: Boolean = false

    override fun generate(
        requestedTechnique: String,
        requestedDifficulty: Int,
        explicitSeed: String?,
        weakestTechnique: String?,
        suggestedDifficulty: Int,
        masteryJson: String?
    ): GeneratedExercise {
        return LimitsCasEngine.generate(
            requestedTechnique = requestedTechnique,
            requestedDifficulty = requestedDifficulty,
            explicitSeed = explicitSeed,
            weakestTechnique = weakestTechnique ?: "adaptive",
            suggestedDifficulty = suggestedDifficulty
        )
    }

    override fun isValidSeed(seed: String): Boolean = LimitsCasEngine.isValidSeed(seed)

    override fun evaluateAnswer(
        expectedAnswer: ExpectedAnswer,
        studentAnswer: String,
        expectedLatex: String?,
        expectedSympyStr: String?
    ): EvaluationResult {
        val eval = MathExprEvaluator.evaluate(studentAnswer)
        val isCorrect = MathExprEvaluator.isEquivalent(expectedAnswer, studentAnswer)

        val msgAr: String
        val msgFr: String
        when {
            isCorrect -> {
                msgAr = "أحسنت! إجابة صحيحة ومتطابقة رمزياً."
                msgFr = "Bravo ! Réponse exacte et symboliquement équivalente."
            }
            eval is com.ayoub.baclimits.math.EvalResult.Invalid -> {
                msgAr = "تعذر فهم الصيغة الرياضية (${eval.reason}). جرب مثالاً مثل: 1/2 أو sqrt(2)/2 أو +inf"
                msgFr = "Expression invalide (${eval.reason}). Ex : 1/2, sqrt(2)/2 ou +inf"
            }
            else -> {
                msgAr = "إجابة غير صحيحة. استعمل زر التلميح أو راجع الإشارة والمعاملات ثم أعد المحاولة!"
                msgFr = "Réponse incorrecte. Utilise un indice ou vérifie le signe et les coefficients !"
            }
        }

        return EvaluationResult(
            isCorrect = isCorrect,
            messageAr = msgAr,
            messageFr = msgFr,
            validSyntax = eval !is com.ayoub.baclimits.math.EvalResult.Invalid
        )
    }
}

object OfflineLimitsEngineHolder {
    @Volatile
    private var engine: OfflineLimitsEngine = LiteKotlinLimitsEngine

    fun set(customEngine: OfflineLimitsEngine) {
        engine = customEngine
    }

    fun get(): OfflineLimitsEngine = engine
}
