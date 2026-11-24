package com.ayoub.baclimits.math

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

sealed class ExpectedAnswer {
    object PosInf : ExpectedAnswer()
    object NegInf : ExpectedAnswer()
    data class Finite(val value: Double) : ExpectedAnswer()
}

sealed class EvalResult {
    object PosInf : EvalResult()
    object NegInf : EvalResult()
    data class Finite(val value: Double) : EvalResult()
    data class Invalid(val reason: String) : EvalResult()
}

object MathExprEvaluator {

    private val EXPR_N_PROBES = doubleArrayOf(1.0, 2.0, 3.0, 5.0, 8.0, 13.0)

    fun isEquivalent(expected: ExpectedAnswer, rawInput: String): Boolean {
        return when (val parsed = evaluate(rawInput)) {
            is EvalResult.PosInf -> expected is ExpectedAnswer.PosInf
            is EvalResult.NegInf -> expected is ExpectedAnswer.NegInf
            is EvalResult.Finite -> {
                if (expected !is ExpectedAnswer.Finite) return false
                val tol = 1e-6 * maxOf(1.0, abs(expected.value))
                abs(parsed.value - expected.value) <= tol
            }
            is EvalResult.Invalid -> false
        }
    }

    fun isEquivalentExprN(expectedExpr: String, rawInput: String): Boolean {
        if (expectedExpr.isBlank() || rawInput.isBlank()) return false
        for (k in EXPR_N_PROBES) {
            val expRes = evaluate(expectedExpr, nValue = k)
            val stuRes = evaluate(rawInput, nValue = k)
            if (expRes !is EvalResult.Finite || stuRes !is EvalResult.Finite) {
                return false
            }
            val tol = 1e-6 * maxOf(1.0, abs(expRes.value))
            if (abs(stuRes.value - expRes.value) > tol) {
                return false
            }
        }
        return true
    }

    fun validateSyntax(rawInput: String, answerKind: String = "constant"): EvalResult {
        return if (answerKind == "expression_n") {
            evaluate(rawInput, nValue = 2.0)
        } else {
            evaluate(rawInput, nValue = null)
        }
    }

    fun evaluate(rawInput: String, nValue: Double? = null): EvalResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return EvalResult.Invalid("Empty input")

        if (nValue == null) {
            val normalizedInf = trimmed
                .lowercase()
                .replace("\\s+".toRegex(), "")
                .replace("−", "-")
                .replace("–", "-")

            when (normalizedInf) {
                "+inf", "inf", "+oo", "oo", "+infinity", "infinity", "+∞", "∞", "+\\infty", "\\infty" ->
                    return EvalResult.PosInf
                "-inf", "-oo", "-infinity", "-∞", "-\\infty" ->
                    return EvalResult.NegInf
            }
        }

        return try {
            val cleaned = preprocess(trimmed)
            val tokens = tokenize(cleaned)
            if (tokens.isEmpty()) return EvalResult.Invalid("Empty expression")
            val parser = Parser(tokens, nValue)
            val value = parser.parseExpression()
            if (parser.hasMore()) {
                return EvalResult.Invalid("Unexpected trailing token")
            }
            if (value.isNaN() || value.isInfinite()) {
                return EvalResult.Invalid("Undefined numerical result")
            }
            EvalResult.Finite(value)
        } catch (e: Exception) {
            EvalResult.Invalid(e.message ?: "Syntax error")
        }
    }

    private fun extractBracedGroup(s: String, start: Int): Pair<String, Int> {
        var depth = 0
        var i = start
        while (i < s.length) {
            if (s[i] == '{') depth++
            else if (s[i] == '}') {
                depth--
                if (depth == 0) {
                    return s.substring(start + 1, i) to (i + 1)
                }
            }
            i++
        }
        throw IllegalArgumentException("Unmatched '{'")
    }

    private fun preprocess(input: String): String {
        var s = input.trim()
            .replace("−", "-")
            .replace("–", "-")
            .replace("×", "*")
            .replace("·", "*")
            .replace("÷", "/")
            .replace("\\left", "")
            .replace("\\right", "")
            .replace("\\cdot", "*")
            .replace("\\times", "*")
            .replace("\\div", "/")
            .replace("**", "^")
            .replace("π", "pi")
            .replace("\\pi", "pi")
            .replace("⁴√", "qdrt")
            .replace("∛", "cbrt")

        s = Regex("\\\\(sin|cos|tan|ln|log|exp)\\b").replace(s) { m ->
            if (m.groupValues[1] == "log") "ln" else m.groupValues[1]
        }

        // Convert \frac{a}{b} iteratively (supports nested braces)
        while (s.contains("\\frac")) {
            val idx = s.indexOf("\\frac")
            var pos = idx + 5
            while (pos < s.length && s[pos].isWhitespace()) pos++
            if (pos >= s.length || s[pos] != '{') throw IllegalArgumentException("Malformed \\frac")
            val (num, afterNum) = extractBracedGroup(s, pos)
            pos = afterNum
            while (pos < s.length && s[pos].isWhitespace()) pos++
            if (pos >= s.length || s[pos] != '{') throw IllegalArgumentException("Malformed \\frac denominator")
            val (den, afterDen) = extractBracedGroup(s, pos)
            s = s.substring(0, idx) + "(($num)/($den))" + s.substring(afterDen)
        }

        // Convert \sqrt[n]{a} and \sqrt{a} iteratively
        while (s.contains("\\sqrt")) {
            val idx = s.indexOf("\\sqrt")
            var pos = idx + 5
            while (pos < s.length && s[pos].isWhitespace()) pos++
            if (pos < s.length && s[pos] == '[') {
                val endBracket = s.indexOf(']', pos)
                if (endBracket == -1) throw IllegalArgumentException("Malformed \\sqrt[n]")
                val degStr = s.substring(pos + 1, endBracket)
                pos = endBracket + 1
                while (pos < s.length && s[pos].isWhitespace()) pos++
                if (pos >= s.length || s[pos] != '{') throw IllegalArgumentException("Malformed \\sqrt[n]{...}")
                val (inner, afterInner) = extractBracedGroup(s, pos)
                s = s.substring(0, idx) + "root(($inner), $degStr)" + s.substring(afterInner)
            } else if (pos < s.length && s[pos] == '{') {
                val (inner, afterInner) = extractBracedGroup(s, pos)
                s = s.substring(0, idx) + "sqrt($inner)" + s.substring(afterInner)
            } else {
                throw IllegalArgumentException("Malformed \\sqrt")
            }
        }

        // Convert ^{...} exponents
        while (s.contains("^{")) {
            val idx = s.indexOf("^{")
            val (inner, afterInner) = extractBracedGroup(s, idx + 1)
            s = s.substring(0, idx) + "^($inner)" + s.substring(afterInner)
        }

        // Support √2 -> sqrt(2) and √(x) -> sqrt(x)
        s = Regex("√\\s*\\(").replace(s, "sqrt(")
        s = Regex("√\\s*([0-9]+(?:\\.[0-9]+)?|pi|e|n)\\b").replace(s) { m ->
            "sqrt(${m.groupValues[1]})"
        }
        s = s.replace("√", "sqrt")

        // Support cbrt2 / qdrt2 without parens
        s = Regex("(cbrt|qdrt)\\s*([0-9]+(?:\\.[0-9]+)?)").replace(s) { m ->
            "${m.groupValues[1]}(${m.groupValues[2]})"
        }

        // Convert French decimal comma (e.g. 0,5) outside root(...) arg separator
        if (!s.contains("root", ignoreCase = true)) {
            s = Regex("(\\d),(\\d)").replace(s, "$1.$2")
        }

        return s.lowercase()
    }

    private sealed class Token {
        data class Num(val v: Double) : Token()
        data class Ident(val name: String) : Token()
        data class Op(val ch: Char) : Token()
        object LParen : Token()
        object RParen : Token()
        object Comma : Token()
    }

    private fun tokenize(s: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    var seenDot = (c == '.')
                    i++
                    while (i < s.length) {
                        val ch = s[i]
                        if (ch.isDigit()) {
                            i++
                        } else if (ch == '.' && !seenDot) {
                            seenDot = true
                            i++
                        } else {
                            break
                        }
                    }
                    val numStr = s.substring(start, i)
                    val v = numStr.toDoubleOrNull()
                        ?: throw IllegalArgumentException("Invalid number: $numStr")
                    out.add(Token.Num(v))
                }
                c.isLetter() -> {
                    val start = i
                    i++
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) {
                        i++
                    }
                    out.add(Token.Ident(s.substring(start, i)))
                }
                c == '(' -> {
                    out.add(Token.LParen)
                    i++
                }
                c == ')' -> {
                    out.add(Token.RParen)
                    i++
                }
                c == ',' -> {
                    out.add(Token.Comma)
                    i++
                }
                c in charArrayOf('+', '-', '*', '/', '^') -> {
                    out.add(Token.Op(c))
                    i++
                }
                else -> throw IllegalArgumentException("Unexpected character: $c")
            }
        }
        return out
    }

    private class Parser(
        private val tokens: List<Token>,
        private val nValue: Double?
    ) {
        private var pos = 0

        fun hasMore(): Boolean = pos < tokens.size

        private fun peek(): Token? = if (pos < tokens.size) tokens[pos] else null

        private fun consume(): Token = tokens[pos++]

        fun parseExpression(): Double {
            var left = parseTerm()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.ch == '+' || t.ch == '-')) {
                    consume()
                    val right = parseTerm()
                    left = if (t.ch == '+') left + right else left - right
                } else {
                    break
                }
            }
            return left
        }

        private fun canStartImplicitMul(t: Token?): Boolean {
            return t is Token.Num || t is Token.Ident || t is Token.LParen
        }

        private fun parseTerm(): Double {
            var left = parsePower()
            while (true) {
                val t = peek()
                when {
                    t is Token.Op && (t.ch == '*' || t.ch == '/') -> {
                        consume()
                        val right = parsePower()
                        if (t.ch == '*') {
                            left *= right
                        } else {
                            if (abs(right) < 1e-15) throw ArithmeticException("Division by zero")
                            left /= right
                        }
                    }
                    canStartImplicitMul(t) -> {
                        val right = parsePower()
                        left *= right
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            val t = peek()
            if (t is Token.Op && t.ch == '^') {
                consume()
                val exponent = parsePower()
                return powSafe(base, exponent)
            }
            return base
        }

        private fun parseUnary(): Double {
            val t = peek()
            if (t is Token.Op && (t.ch == '+' || t.ch == '-')) {
                consume()
                val v = parseUnary()
                return if (t.ch == '-') -v else v
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            val t = consume()
            return when (t) {
                is Token.Num -> t.v
                is Token.LParen -> {
                    val v = parseExpression()
                    val close = peek()
                    if (close !is Token.RParen) throw IllegalArgumentException("Missing ')'")
                    consume()
                    v
                }
                is Token.Ident -> {
                    if (peek() is Token.LParen) {
                        consume() // '('
                        val args = mutableListOf<Double>()
                        if (peek() !is Token.RParen) {
                            args.add(parseExpression())
                            while (peek() is Token.Comma) {
                                consume()
                                args.add(parseExpression())
                            }
                        }
                        if (peek() !is Token.RParen) throw IllegalArgumentException("Missing ')' in ${t.name}")
                        consume()
                        evalFunc(t.name, args)
                    } else {
                        when (t.name) {
                            "pi" -> PI
                            "e" -> E
                            "n" -> nValue ?: throw IllegalArgumentException("Variable n is not allowed here")
                            else -> throw IllegalArgumentException("Unknown identifier: ${t.name}")
                        }
                    }
                }
                else -> throw IllegalArgumentException("Unexpected token")
            }
        }

        private fun evalFunc(name: String, args: List<Double>): Double {
            return when (name) {
                "sqrt" -> {
                    require(args.size == 1)
                    val x = args[0]
                    if (x < -1e-12) throw ArithmeticException("Negative sqrt")
                    sqrt(maxOf(0.0, x))
                }
                "cbrt" -> {
                    require(args.size == 1)
                    Math.cbrt(args[0])
                }
                "qdrt" -> {
                    require(args.size == 1)
                    val x = args[0]
                    if (x < -1e-12) throw ArithmeticException("Negative 4th root")
                    maxOf(0.0, x).pow(0.25)
                }
                "root" -> {
                    require(args.size == 2)
                    val x = args[0]
                    val n = args[1]
                    val nRound = n.roundToLong()
                    if (n <= 0.0 || abs(n - nRound) > 1e-9) {
                        throw IllegalArgumentException("Root degree must be positive integer")
                    }
                    if (x < 0.0) {
                        if (nRound % 2L == 1L) {
                            -(-x).pow(1.0 / nRound)
                        } else {
                            throw ArithmeticException("Even root of negative number")
                        }
                    } else {
                        x.pow(1.0 / nRound)
                    }
                }
                "sin" -> {
                    require(args.size == 1)
                    sin(args[0])
                }
                "cos" -> {
                    require(args.size == 1)
                    cos(args[0])
                }
                "tan" -> {
                    require(args.size == 1)
                    val c = cos(args[0])
                    if (abs(c) < 1e-12) throw ArithmeticException("Undefined tan")
                    tan(args[0])
                }
                "abs" -> {
                    require(args.size == 1)
                    abs(args[0])
                }
                "ln", "log" -> {
                    require(args.size == 1)
                    if (args[0] <= 0.0) throw ArithmeticException("Non-positive ln")
                    ln(args[0])
                }
                "exp" -> {
                    require(args.size == 1)
                    exp(args[0])
                }
                else -> throw IllegalArgumentException("Unknown function: $name")
            }
        }

        private fun powSafe(base: Double, expVal: Double): Double {
            if (base < 0.0) {
                val rounded = expVal.roundToLong()
                if (abs(expVal - rounded) < 1e-9) {
                    val p = (-base).pow(rounded.toDouble())
                    return if (rounded % 2L == 0L) p else -p
                }
                throw ArithmeticException("Negative base with non-integer exponent")
            }
            return base.pow(expVal)
        }
    }
}
