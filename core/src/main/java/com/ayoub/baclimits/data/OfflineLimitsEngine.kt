package com.ayoub.baclimits.data

import com.ayoub.baclimits.math.EvalResult
import com.ayoub.baclimits.math.ExpectedAnswer
import com.ayoub.baclimits.math.GeneratedExercise
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.math.MathExprEvaluator

data class EvaluationResult(
    val isCorrect: Boolean,
    val messageAr: String,
    val messageFr: String,
    val validSyntax: Boolean = true,
    val studentLatex: String = "",
    val expectedLatex: String = ""
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
        masteryJson: String? = null,
        chapter: String = "limits"
    ): GeneratedExercise

    fun isValidSeed(seed: String): Boolean

    fun evaluateAnswer(
        expectedAnswer: ExpectedAnswer,
        studentAnswer: String,
        expectedLatex: String? = null,
        expectedSympyStr: String? = null,
        answerKind: String = "constant"
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
        masteryJson: String?,
        chapter: String
    ): GeneratedExercise {
        return LimitsCasEngine.generate(
            requestedTechnique = requestedTechnique,
            requestedDifficulty = requestedDifficulty,
            explicitSeed = explicitSeed,
            weakestTechnique = weakestTechnique ?: "adaptive",
            suggestedDifficulty = suggestedDifficulty,
            chapter = chapter
        )
    }

    override fun isValidSeed(seed: String): Boolean = LimitsCasEngine.isValidSeed(seed)

    override fun evaluateAnswer(
        expectedAnswer: ExpectedAnswer,
        studentAnswer: String,
        expectedLatex: String?,
        expectedSympyStr: String?,
        answerKind: String
    ): EvaluationResult {
        val syntaxEval = MathExprEvaluator.validateSyntax(studentAnswer, answerKind = answerKind)
        val isCorrect = if (syntaxEval is EvalResult.Invalid) {
            false
        } else if (answerKind == "expression_n") {
            MathExprEvaluator.isEquivalentExprN(expectedSympyStr ?: expectedLatex.orEmpty(), studentAnswer)
        } else {
            MathExprEvaluator.isEquivalent(expectedAnswer, studentAnswer)
        }

        val studentLatex = if (syntaxEval is EvalResult.Invalid) "" else LatexFormatter.asciiToPreviewLatex(studentAnswer)
        val msgAr: String
        val msgFr: String
        when {
            isCorrect -> {
                msgAr = "أحسنت! إجابة صحيحة تماماً."
                msgFr = "Bravo ! Votre réponse est exacte."
            }
            syntaxEval is EvalResult.Invalid -> {
                if (answerKind == "expression_n") {
                    msgAr = "صيغة التعبير غير واضحة (${syntaxEval.reason}). أمثلة: `2 + 3*(1/2)^n` أو `(2^n - 1)/(2^n + 1)`."
                    msgFr = "Format d'expression invalide (${syntaxEval.reason}). Ex : `2 + 3*(1/2)^n`."
                } else {
                    msgAr = "صيغة الإجابة غير واضحة (${syntaxEval.reason}). أمثلة: `1/2` أو `sqrt(3)/2` أو `ln(2)` أو `e^2` أو `+inf`."
                    msgFr = "Format de réponse invalide (${syntaxEval.reason}). Ex : `1/2`, `ln(2)`, `e^2` ou `+inf`."
                }
            }
            else -> {
                msgAr = "إجابة غير صحيحة. راجع التلميحات أو جرّب التحقق من الإشارات والمعاملات."
                msgFr = "Réponse incorrecte. Consultez les indices ou vérifiez les signes et coefficients."
            }
        }

        return EvaluationResult(
            isCorrect = isCorrect,
            messageAr = msgAr,
            messageFr = msgFr,
            validSyntax = syntaxEval !is EvalResult.Invalid,
            studentLatex = studentLatex,
            expectedLatex = expectedLatex.orEmpty()
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
