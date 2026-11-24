package com.ayoub.baclimits

import com.ayoub.baclimits.math.EvalResult
import com.ayoub.baclimits.math.ExpectedAnswer
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.math.MathExprEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sqrt

class LimitsCasEngineTest {

    @Test
    fun `expression evaluator handles fractions decimals radicals ln exp sequences and infinity`() {
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
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(ln(2.0)), "ln(2)"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(ln(2.0)), "\\ln(2)"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(E * E), "e^2"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(E * E), "exp(2)"))

        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.PosInf, "+inf"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.PosInf, "+oo"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.PosInf, "∞"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.NegInf, "-inf"))
        assertTrue(MathExprEvaluator.isEquivalent(ExpectedAnswer.NegInf, "-oo"))
        assertFalse(MathExprEvaluator.isEquivalent(ExpectedAnswer.NegInf, "+inf"))
        assertFalse(MathExprEvaluator.isEquivalent(ExpectedAnswer.Finite(1.0), "1/0"))
        assertTrue(MathExprEvaluator.evaluate("1/0") is EvalResult.Invalid)

        // Sequences expression in variable n
        assertTrue(MathExprEvaluator.isEquivalentExprN("2*(1/3)^n + 1", "1 + 2/3^n"))
        assertFalse(MathExprEvaluator.isEquivalentExprN("2*(1/3)^n + 1", "2*(1/2)^n + 1"))
        assertTrue(MathExprEvaluator.validateSyntax("2*(1/3)^n + 1", "expression_n"))
        assertFalse(MathExprEvaluator.validateSyntax("2*(((", "expression_n"))
    }

    @Test
    fun `all 12 techniques across 2 chapters and 3 difficulties generate deterministically`() {
        assertEquals(8, LimitsCasEngine.LIMITS_TECHNIQUES.size)
        assertEquals(4, LimitsCasEngine.SEQUENCES_TECHNIQUES.size)
        assertEquals(12, LimitsCasEngine.CORE_TECHNIQUES.size)

        val seenSeeds = mutableSetOf<String>()
        for (tech in LimitsCasEngine.CORE_TECHNIQUES) {
            val expectedChapter = LimitsCasEngine.chapterOfTechnique(tech)
            for (diff in 1..3) {
                for (i in 0 until 15) {
                    val ex = LimitsCasEngine.generate(tech, diff, chapter = expectedChapter)
                    assertEquals(tech, ex.technique)
                    assertEquals(expectedChapter, ex.chapter)
                    assertEquals(diff, ex.difficulty)
                    assertTrue(ex.fullLimitLatex.isNotBlank())
                    if (expectedChapter == "limits") {
                        assertTrue("Expected \\lim_ for $tech: ${ex.fullLimitLatex}", ex.fullLimitLatex.contains("\\lim_"))
                    }
                    assertTrue(ex.hints.isNotEmpty())
                    assertTrue(ex.steps.isNotEmpty())
                    assertEquals("Odd \$ count in prompt AR: ${ex.prompt.ar}", 0, ex.prompt.ar.count { it == '$' } % 2)
                    assertEquals("Odd \$ count in prompt FR: ${ex.prompt.fr}", 0, ex.prompt.fr.count { it == '$' } % 2)
                    ex.hints.forEach { h ->
                        assertEquals("Odd \$ count in hint AR: ${h.ar}", 0, h.ar.count { it == '$' } % 2)
                        assertEquals("Odd \$ count in hint FR: ${h.fr}", 0, h.fr.count { it == '$' } % 2)
                    }
                    ex.steps.forEach { st ->
                        assertEquals("Odd \$ count in step title AR: ${st.title.ar}", 0, st.title.ar.count { it == '$' } % 2)
                        assertEquals("Odd \$ count in step expl AR: ${st.explanation.ar}", 0, st.explanation.ar.count { it == '$' } % 2)
                    }
                    seenSeeds.add(ex.seed)

                    // Deterministic replay from seed
                    val replayed = LimitsCasEngine.generate("adaptive", 0, explicitSeed = ex.seed)
                    assertEquals(ex.seed, replayed.seed)
                    assertEquals(ex.chapter, replayed.chapter)
                    assertEquals(ex.answerKind, replayed.answerKind)
                    assertEquals(ex.fullLimitLatex, replayed.fullLimitLatex)
                    assertEquals(ex.answerLatex, replayed.answerLatex)
                    assertEquals(ex.expectedAnswer, replayed.expectedAnswer)

                    if (ex.answerKind == "expression_n") {
                        val exprStr = ex.answerSympyStr.orEmpty()
                        assertTrue("Missing answerSympyStr for $tech", exprStr.isNotBlank())
                        assertTrue(MathExprEvaluator.isEquivalentExprN(exprStr, exprStr))
                    } else {
                        when (val exp = ex.expectedAnswer) {
                            is ExpectedAnswer.PosInf -> assertTrue(ex.answerLatex.contains("+\\infty"))
                            is ExpectedAnswer.NegInf -> assertTrue(ex.answerLatex.contains("-\\infty"))
                            is ExpectedAnswer.Finite -> assertFalse(exp.value.isNaN() || exp.value.isInfinite())
                        }
                    }
                }
            }
        }
        assertTrue("Expected high variety of seeds", seenSeeds.size > 400)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid seed throws clear error`() {
        LimitsCasEngine.parseSeed("INVALID-SEED-123")
    }
}
