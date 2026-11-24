package com.ayoub.baclimits.data

object LatexFormatter {

    /**
     * Converts student ASCII input (e.g. `1/2`, `sqrt(2)/2`, `ln(2)`, `e^2`, `2 + 3*(1/2)^n`, `+inf`)
     * into KaTeX LaTeX for live answer preview.
     */
    fun asciiToPreviewLatex(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        s = s.replace(Regex("\\s+"), "")
            .replace("−", "-")
            .replace("–", "-")
            .replace("×", "*")
            .replace("·", "*")
            .replace("÷", "/")
        s = s.replace(Regex("-(?:infinity|inf|oo|∞)", RegexOption.IGNORE_CASE)) { "-\\infty" }
        s = s.replace(Regex("\\+(?:infinity|inf|oo|∞)", RegexOption.IGNORE_CASE)) { "+\\infty" }
        s = s.replace(Regex("(?:∞|\\binfinity\\b|\\binf\\b|\\boo\\b)", RegexOption.IGNORE_CASE)) { "\\infty" }
        s = s.replace("⁴√", "qdrt").replace("∛", "cbrt")
        s = replaceBalancedFn(s, "qdrt") { inside -> "\\sqrt[4]{$inside}" }
        s = replaceBalancedFn(s, "cbrt") { inside -> "\\sqrt[3]{$inside}" }
        s = replaceBalancedFn(s, "root") { inside ->
            var pDepth = 0
            var commaIdx = -1
            for (i in inside.indices) {
                when (inside[i]) {
                    '(' -> pDepth++
                    ')' -> if (pDepth > 0) pDepth--
                    ',' -> if (pDepth == 0) commaIdx = i
                }
            }
            if (commaIdx == -1) {
                "\\sqrt{${asciiToPreviewLatex(inside)}}"
            } else {
                val base = asciiToPreviewLatex(inside.substring(0, commaIdx))
                val deg = inside.substring(commaIdx + 1)
                "\\sqrt[$deg]{$base}"
            }
        }
        s = s.replace(Regex("√\\(([^)]+)\\)")) { m -> "\\sqrt{${m.groupValues[1]}}" }
        s = s.replace(Regex("√([0-9]+(?:\\.[0-9]+)?|pi|e|n)\\b", RegexOption.IGNORE_CASE)) { m ->
            "\\sqrt{${m.groupValues[1]}}"
        }
        s = replaceBalancedFn(s, "sqrt") { inside -> "\\sqrt{${asciiToPreviewLatex(inside)}}" }
        s = replaceBalancedFn(s, "abs") { inside -> "\\left|${asciiToPreviewLatex(inside)}\\right|" }
        s = replaceBalancedFn(s, "exp") { inside -> "e^{${asciiToPreviewLatex(inside)}}" }
        s = s.replace(Regex("\\*\\*(\\([^)]+\\)|[A-Za-z0-9]+)")) { m ->
            "^{${m.groupValues[1].removeSurrounding("(", ")")}}"
        }
        s = s.replace(Regex("\\^\\(([^)]+)\\)")) { m ->
            "^{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("\\^([A-Za-z0-9]+)")) { m ->
            "^{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("\\\\?\\bpi\\b|π", RegexOption.IGNORE_CASE)) { "\\pi " }
        s = s.replace(Regex("\\b(sin|cos|tan|ln|log)\\(", RegexOption.IGNORE_CASE)) { m ->
            val fn = m.groupValues[1].lowercase()
            val mapped = if (fn == "log") "ln" else fn
            "\\$mapped("
        }
        if (s.contains("/") && !s.contains("\\frac")) {
            val idx = findTopLevelSlash(s)
            if (idx > 0 && idx < s.length - 1) {
                val left = s.substring(0, idx)
                val right = s.substring(idx + 1)
                if (!right.contains("/")) {
                    s = "\\frac{${left.removeSurrounding("(", ")")}}{${right.removeSurrounding("(", ")")}}"
                }
            }
        }
        s = s.replace("*", "\\cdot ")
        return s.trim()
    }

    private fun replaceBalancedFn(str: String, fnName: String, formatter: (String) -> String): String {
        var out = str
        val needle = "$fnName("
        var guard = 0
        while (guard++ < 30) {
            val idx = out.indexOf(needle, ignoreCase = true)
            if (idx == -1) break
            if (idx > 0 && (out[idx - 1].isLetter() || out[idx - 1] == '\\')) {
                break
            }
            var depth = 0
            var end = -1
            for (i in (idx + fnName.length) until out.length) {
                if (out[i] == '(') depth++
                else if (out[i] == ')') {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
            if (end == -1) break
            val inside = out.substring(idx + needle.length, end)
            val replaced = formatter(inside)
            out = out.substring(0, idx) + replaced + out.substring(end + 1)
        }
        return out
    }

    private fun findTopLevelSlash(s: String): Int {
        var depth = 0
        var bDepth = 0
        for (i in s.indices) {
            when (s[i]) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                '{' -> bDepth++
                '}' -> if (bDepth > 0) bDepth--
                '/' -> if (depth == 0 && bDepth == 0) return i
            }
        }
        return -1
    }

    /**
     * Converts KaTeX LaTeX formulas into readable mathematical Unicode for list cards and accessibility.
     */
    fun toReadableUnicode(latexRaw: String): String {
        if (latexRaw.isBlank()) return ""
        var s = latexRaw.trim()
        s = s.replace("\\left", "").replace("\\right", "")
        s = s.replace("\\displaystyle", "")
        s = s.replace("\\begin{cases}", "").replace("\\end{cases}", "")
        s = s.replace("\\\\", " ; ")
        s = s.replace("\\qquad", " ").replace("\\quad", " ")
        s = s.replace("\\mathbb{N}", "ℕ")
        s = s.replace("\\cdot", "·").replace("\\times", "×")
        s = s.replace("\\to", "→").replace("\\rightarrow", "→")
        s = s.replace("\\xrightarrow", "→")
        s = s.replace("\\infty", "∞")
        s = s.replace("\\pi", "π")
        s = s.replace("\\ln", "ln").replace("\\exp", "exp")
        s = s.replace("\\sin", "sin").replace("\\cos", "cos").replace("\\tan", "tan")
        s = s.replace("\\pm", "±")
        s = s.replace("\\,", " ").replace("\\;", " ").replace("\\:", " ")

        s = parseExpression(s)
        s = s.replace("-", "−").replace(Regex("\\s+"), " ").trim()
        return s
    }

    private fun parseExpression(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            when {
                input.startsWith("\\lim_", i) -> {
                    val j = i + 5
                    if (j < input.length && input[j] == '{') {
                        val (inner, nextIdx) = extractBalanced(input, j, '{', '}')
                        sb.append("lim(").append(parseExpression(inner).trim()).append(") ")
                        i = nextIdx
                    } else {
                        sb.append("lim ")
                        i += 4
                    }
                }
                input.startsWith("\\lim", i) -> {
                    sb.append("lim ")
                    i += 4
                }
                input.startsWith("\\frac", i) -> {
                    val j = skipSpaces(input, i + 5)
                    if (j < input.length && input[j] == '{') {
                        val (numRaw, afterNum) = extractBalanced(input, j, '{', '}')
                        val k = skipSpaces(input, afterNum)
                        if (k < input.length && input[k] == '{') {
                            val (denRaw, afterDen) = extractBalanced(input, k, '{', '}')
                            val num = parseExpression(numRaw).trim()
                            val den = parseExpression(denRaw).trim()
                            val numFmt = if (needsParens(num)) "($num)" else num
                            val denFmt = if (needsParens(den)) "($den)" else den
                            sb.append(numFmt).append(" / ").append(denFmt)
                            i = afterDen
                            continue
                        }
                    }
                    sb.append("/")
                    i += 5
                }
                input.startsWith("\\sqrt", i) -> {
                    var j = skipSpaces(input, i + 5)
                    var rootIndex: String? = null
                    if (j < input.length && input[j] == '[') {
                        val (idxRaw, afterIdx) = extractBalanced(input, j, '[', ']')
                        rootIndex = parseExpression(idxRaw).trim()
                        j = skipSpaces(input, afterIdx)
                    }
                    if (j < input.length && input[j] == '{') {
                        val (radRaw, afterRad) = extractBalanced(input, j, '{', '}')
                        val rad = parseExpression(radRaw).trim()
                        val prefix = when (rootIndex) {
                            null, "2" -> "√"
                            "3" -> "∛"
                            "4" -> "∜"
                            else -> "${toSuperscript(rootIndex)}√"
                        }
                        sb.append(prefix).append("(").append(rad).append(")")
                        i = afterRad
                    } else {
                        sb.append("√")
                        i += 5
                    }
                }
                input[i] == '^' -> {
                    val j = i + 1
                    if (j < input.length && input[j] == '{') {
                        val (expRaw, afterExp) = extractBalanced(input, j, '{', '}')
                        val exp = parseExpression(expRaw).trim()
                        sb.append(toSuperscript(exp))
                        i = afterExp
                    } else if (j < input.length) {
                        sb.append(toSuperscript(input[j].toString()))
                        i = j + 1
                    } else {
                        i++
                    }
                }
                input[i] == '_' -> {
                    val j = i + 1
                    if (j < input.length && input[j] == '{') {
                        val (subRaw, afterSub) = extractBalanced(input, j, '{', '}')
                        val sub = parseExpression(subRaw).trim()
                        sb.append(sub)
                        i = afterSub
                    } else {
                        i++
                    }
                }
                input[i] == '{' || input[i] == '}' -> {
                    i++
                }
                else -> {
                    sb.append(input[i])
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun extractBalanced(s: String, startIdx: Int, open: Char, close: Char): Pair<String, Int> {
        if (startIdx >= s.length || s[startIdx] != open) return "" to startIdx
        var depth = 0
        var i = startIdx
        while (i < s.length) {
            if (s[i] == open) depth++
            else if (s[i] == close) {
                depth--
                if (depth == 0) {
                    return s.substring(startIdx + 1, i) to (i + 1)
                }
            }
            i++
        }
        return s.substring(startIdx + 1) to s.length
    }

    private fun skipSpaces(s: String, idx: Int): Int {
        var i = idx
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun needsParens(expr: String): Boolean {
        return expr.contains("+") || expr.contains("-") || expr.contains("−") || expr.contains(" ")
    }

    private fun toSuperscript(s: String): String {
        val map = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '+' to '⁺', '-' to '⁻', 'n' to 'ⁿ', 'i' to 'ⁱ'
        )
        return if (s.all { it in map }) {
            s.map { map[it] ?: it }.joinToString("")
        } else {
            "^($s)"
        }
    }
}
