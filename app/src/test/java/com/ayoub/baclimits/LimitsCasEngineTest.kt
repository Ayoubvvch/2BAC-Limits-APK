package com.ayoub.baclimits

import com.ayoub.baclimits.math.EvalResult
import com.ayoub.baclimits.math.ExpectedAnswer
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.math.MathExprEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class LimitsCasEngineTest {

    @Test
    fun `expression evaluator handles fractions decimals radicals and infinity equivalently`() {
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(0.5), "0.5"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(0.5), "0,5"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(0.5), "1/2"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(0.5), "2/4"))

        val invSqrt2 = 1.0 / sqrt(2.0)
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(invSqrt2), "sqrt(2)/2"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(invSqrt2), "1/sqrt(2)"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(invSqrt2), "√2/2"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(invSqrt2), "1/√2"))

        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(2.0), "root(8, 3)"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(2.0), "∛8"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(3.0), "root(81, 4)"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(PI / 2.0), "pi/2"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(-sqrt(3.0)), "-√3"))

        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.PosInf, "+inf"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.PosInf, "+oo"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.PosInf, "∞"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.NegInf, "-inf"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.NegInf, "-oo"))
        assertFalse(MathExprEvaluator.isEquivalent(ExpectedAnswer.NegInf, "+inf"))
        assertFalse(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(1.0), "1/0"))
        assertTrue(MathExprEvaluator.evaluate("1/0") is EvalResult.Invalid)
    }

    @Test
    fun `all 6 techniques and 3 difficulties generate deterministically with valid solutions`() {
        val seenSeeds = mutableSetOf<String>()
        for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
            for (diff in 1..3) {
                for (i in 0 until 20) {
                    val ex = LimitsCasEngine.generate(tech, diff)
                    assertEquals(tech, ex.technique)
                    assertEquals(diff, ex.difficulty)
                    assertTrue(ex.fullLimitLatex.contains("\\lim_"))
                    assertTrue(ex.hints.isNotEmpty())
                    assertTrue(ex.steps.isNotEmpty())
                    seenSeeds.add(ex.seed)

                    // Deterministic replay from seed
                    val replayed = LimitsCasEngine.generate("adaptive", 0, explicitSeed = ex.seed)
                    assertEquals(ex.seed, replayed.seed)
                    assertEquals(ex.fullLimitLatex, replayed.fullLimitLatex)
                    assertEquals(ex.answerLatex, replayed.answerLatex)
                    assertEquals(ex.expectedAnswer, replayed.expectedAnswer)

                    // Answer latex or expected value consistency check
                    when (val exp = ex.expectedAnswer) {
                        is ExpectedAnswer.PosInf -> assertTrue(ex.answerLatex.contains("+\\infty"))
                        is ExpectedAnswer.NegInf -> assertTrue(ex.answerLatex.contains("-\\infty"))
                        is ExpectedAnswer.Finite -> assertFalse(exp.value.isNaN() || exp.value.isInfinite())
                    }
                }
            }
        }
        assertTrue("Expected high variety of seeds", seenSeeds.size > 300)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid seed throws clear error`() {
        LimitsCasEngine.parseSeed("INVALID-SEED-123")
    }
}
