package com.ayoub.baclimits

import com.ayoub.baclimits.data.LatexFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexFormatterTest {

    @Test
    fun `asciiToPreviewLatex formats fractions roots and infinities cleanly`() {
        assertEquals("", LatexFormatter.asciiToPreviewLatex("   "))
        assertEquals("\\frac{1}{2}", LatexFormatter.asciiToPreviewLatex("1/2"))
        assertEquals("\\frac{\\sqrt{2}}{2}", LatexFormatter.asciiToPreviewLatex("sqrt(2)/2"))
        assertEquals("\\frac{\\sqrt{2}}{2}", LatexFormatter.asciiToPreviewLatex("√(2)/2"))
        assertEquals("\\sqrt[3]{8}", LatexFormatter.asciiToPreviewLatex("∛(8)"))
        assertEquals("\\sqrt[4]{16}", LatexFormatter.asciiToPreviewLatex("⁴√(16)"))
        assertEquals("\\sqrt[3]{8}", LatexFormatter.asciiToPreviewLatex("root(8, 3)"))
        assertEquals("+\\infty", LatexFormatter.asciiToPreviewLatex("+inf"))
        assertEquals("-\\infty", LatexFormatter.asciiToPreviewLatex("-inf"))
        assertEquals("-\\infty", LatexFormatter.asciiToPreviewLatex("-∞"))
        assertEquals("\\infty", LatexFormatter.asciiToPreviewLatex("∞"))
        assertEquals("\\frac{\\pi }{2}", LatexFormatter.asciiToPreviewLatex("pi/2"))
        assertEquals("\\frac{\\sin(x)}{x}", LatexFormatter.asciiToPreviewLatex("sin(x)/x"))
    }

    @Test
    fun `asciiToPreviewLatex converts keypad minus multiplication logs exp abs and sequence powers`() {
        assertEquals("\\frac{-3}{4}", LatexFormatter.asciiToPreviewLatex("−3/4"))
        assertEquals("2\\cdot \\sqrt{5}", LatexFormatter.asciiToPreviewLatex("2*√(5)"))
        assertEquals("x^{12}", LatexFormatter.asciiToPreviewLatex("x^12"))
        assertEquals("e^{2}", LatexFormatter.asciiToPreviewLatex("e^2"))
        assertEquals("e^{3}", LatexFormatter.asciiToPreviewLatex("exp(3)"))
        assertEquals("\\frac{\\ln(2)}{2}", LatexFormatter.asciiToPreviewLatex("ln(2)/2"))
        assertEquals("\\left|x-1\\right|", LatexFormatter.asciiToPreviewLatex("abs(x-1)"))
        assertEquals("2\\cdot (1/3)^{n}", LatexFormatter.asciiToPreviewLatex("2*(1/3)^n"))
        assertEquals("a^{n}", LatexFormatter.asciiToPreviewLatex("a^n"))
    }

    @Test
    fun `toReadableUnicode produces readable compact limit text for history cards`() {
        val raw = "\\lim_{x \\to +\\infty} \\frac{\\sqrt{x^2 + 1} - x}{2}"
        val pretty = LatexFormatter.toReadableUnicode(raw)
        assertTrue(pretty.contains("lim("))
        assertTrue(pretty.contains("→ +∞"))
        assertTrue(pretty.contains("√(x² + 1)"))
        assertTrue(pretty.contains("/ 2"))
    }
}
