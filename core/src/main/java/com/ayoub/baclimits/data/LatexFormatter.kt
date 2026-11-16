package com.ayoub.baclimits.data

object LatexFormatter {

    /**
     * Converts student ASCII input (e.g. `1/2`, `sqrt(2)/2`, `root(8, 3)`, `+inf`)
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
        s = s.replace(Regex("qdrt\\(([^)]+)\\)", RegexOption.IGNORE_CASE)) { m ->
            "\\sqrt[4]{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("cbrt\\(([^)]+)\\)", RegexOption.IGNORE_CASE)) { m ->
            "\\sqrt[3]{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("root\\(([^,]+),([^)]+)\\)", RegexOption.IGNORE_CASE)) { m ->
            "\\sqrt[${m.groupValues[2]}]{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("√\\(([^)]+)\\)")) { m ->
            "\\sqrt{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("√([0-9]+(?:\\.[0-9]+)?|pi)\\b", RegexOption.IGNORE_CASE)) { m ->
            "\\sqrt{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("sqrt\\(([^)]+)\\)", RegexOption.IGNORE_CASE)) { m ->
            "\\sqrt{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("\\*\\*([A-Za-z0-9]+)")) { m ->
            "^{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("\\^\\(([^)]+)\\)")) { m ->
            "^{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("\\^([A-Za-z0-9]+)")) { m ->
            "^{${m.groupValues[1]}}"
        }
        s = s.replace(Regex("\\\\?\\bpi\\b|π", RegexOption.IGNORE_CASE)) { "\\pi " }
        s = s.replace(Regex("\\b(sin|cos|tan)\\(", RegexOption.IGNORE_CASE)) { m ->
            "\\${m.groupValues[1].lowercase()}("
        }
        if (s.contains("/") && !s.contains("\\frac")) {
            val idx = findTopLevelSlash(s)
            if (idx > 0 && idx < s.length - 1) {
                val left = s.substring(0, idx)
                val right = s.substring(idx + 1)
                if (!right.contains("/")) {
                    s = "\\frac{$left}{$right}"
                }
            }
        }
        s = s.replace("*", "\\cdot ")
        return s.trim()
    }

    private fun findTopLevelSlash(s: String): Int {
        var depth = 0
        for (i in s.indices) {
            when (s[i]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> if (depth > 0) depth--
                '/' -> if (depth == 0) return i
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
        s = s.replace("\\cdot", "·").replace("\\times", "×")
        s = s.replace("\\to", "→").replace("\\rightarrow", "→")
        s = s.replace("\\infty", "∞")
        s = s.replace("\\pi", "π")
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
                    var j = i + 5
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
                    var j = skipSpaces(input, i + 5)
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
                    var j = i + 1
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
                    var j = i + 1
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
