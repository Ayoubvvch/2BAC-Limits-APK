package com.ayoub.baclimits.math

import kotlin.math.abs
import kotlin.math.ln

/**
 * Pure-Kotlin procedural CAS generators for the expanded 2BAC PC curriculum:
 * - Natural Logarithm limits (`ln_limits`)
 * - Exponential limits (`exp_limits`)
 * - Chapter 2: Numerical Sequences (`seq_limits`, `seq_arith_geo`, `seq_fixed_point`, `seq_homographic`)
 *
 * Used by Lite Edition for 100% offline-first capability matching the server & Full Edition.
 */
internal object NewChaptersCasGenerators {

    private fun gcd(a: Long, b: Long): Long {
        var x = abs(a)
        var y = abs(b)
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0L) 1L else x
    }

    private fun fmtRatLatex(num: Long, den: Long): String {
        if (num == 0L) return "0"
        val g = gcd(num, den)
        val s = if ((num < 0) xor (den < 0)) -1 else 1
        val n = abs(num) / g
        val d = abs(den) / g
        val core = if (d == 1L) "$n" else "\\frac{$n}{$d}"
        return if (s < 0) "-$core" else core
    }

    private fun fmtRatSympy(num: Long, den: Long): String {
        if (num == 0L) return "0"
        val g = gcd(num, den)
        val s = if ((num < 0) xor (den < 0)) -1 else 1
        val n = abs(num) / g
        val d = abs(den) / g
        val signedN = if (s < 0) -n else n
        return if (d == 1L) "$signedN" else "$signedN/$d"
    }

    private fun fmtSignedConst(c: Int): String = when {
        c > 0 -> " + $c"
        c < 0 -> " - ${-c}"
        else -> ""
    }

    private fun fmtLinearExpr(a: Int, b: Int, v: String = "x"): String {
        val head = when (a) {
            0 -> ""
            1 -> v
            -1 -> "-$v"
            else -> "$a$v"
        }
        return when {
            head.isEmpty() -> "$b"
            b > 0 -> "$head + $b"
            b < 0 -> "$head - ${-b}"
            else -> head
        }
    }

    private fun fmtFactor(x0: Int, v: String = "x"): String = when {
        x0 == 0 -> v
        x0 > 0 -> "$v - $x0"
        else -> "$v + ${-x0}"
    }

    private fun fmtPowBaseN(p: Long, q: Long, shift: Int = 0): String {
        val g = gcd(p, q)
        val pn = p / g
        val qd = q / g
        val expStr = when {
            shift == 0 -> "n"
            shift > 0 -> "n+$shift"
            else -> "n-${-shift}"
        }
        return if (qd == 1L && pn > 0L) {
            "$pn^{$expStr}"
        } else {
            "\\left(${fmtRatLatex(pn, qd)}\\right)^{$expStr}"
        }
    }

    private fun fmtPowBaseNSympy(p: Long, q: Long, shift: Int = 0): String {
        val g = gcd(p, q)
        val pn = p / g
        val qd = q / g
        val expStr = when {
            shift == 0 -> "n"
            shift > 0 -> "(n+$shift)"
            else -> "(n-${-shift})"
        }
        val base = if (qd == 1L) "$pn" else "(${fmtRatSympy(pn, qd)})"
        return "$base^$expStr"
    }

    // -----------------------------------------------------------------------
    // 1. LN LIMITS (ln_limits)
    // -----------------------------------------------------------------------

    fun genLnLimits(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        val variant = when (diff) {
            1 -> rng.nextInt(3) // 0: ln/x^p, 1: x^p*ln, 2: taux accroissement
            2 -> rng.nextInt(3) // 0: ratio (d*x^p + a*ln)/(e*x^p + f), 1: ax^p - b*ln, 2: ln(P)/ln(Q)
            else -> rng.nextInt(3) // 0: x*(ln(kx+a)-ln(kx+b)), 1: x*(ln x)^2 + d, 2: ln ratio
        }

        if (diff == 1) {
            return when (variant) {
                0 -> {
                    val a = rng.pickNonZero(-5, 5)
                    val b = rng.pick(listOf(1, 2, 3))
                    val c = rng.range(0, 5)
                    val d = rng.range(-4, 4)
                    val p = rng.pick(listOf(1, 2))
                    val argTex = fmtLinearExpr(b, c)
                    val denTex = if (p == 1) "x" else "x^{$p}"
                    val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                    val numTex = "$aPre\\ln($argTex)${fmtSignedConst(d)}"
                    val fullTex = "\\lim_{x \\to +\\infty} \\frac{$numTex}{$denTex}"
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "ln_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                ar = "استعمل النهاية الأساسية \$\\lim_{x \\to +\\infty} \\frac{\\ln x}{x^$p} = 0\$.",
                                fr = "Utilisez la croissance comparée \$\\lim_{x \\to +\\infty} \\frac{\\ln x}{x^$p} = 0\$."
                            )
                        ),
                        answerLatex = "0",
                        expectedAnswer = ExpectedAnswer.Finite(0.0),
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("تطبيق المقارنة اللوغاريتمية", "Croissance comparée logarithmique"),
                                explanation = LocalizedText(
                                    "بوضع \$t = $argTex \\to +\\infty\$ نستعمل \$\\lim_{t \\to +\\infty} \\frac{\\ln t}{t^$p} = 0\$:",
                                    "En posant \$t = $argTex \\to +\\infty\$, on utilise \$\\lim_{t \\to +\\infty} \\frac{\\ln t}{t^$p} = 0\$ :"
                                ),
                                latex = "$fullTex = 0"
                            )
                        ),
                        answerSympyStr = "0"
                    )
                }
                1 -> {
                    val a = rng.pickNonZero(-5, 5)
                    val b = rng.pick(listOf(1, 2, 3))
                    val d = rng.range(-5, 5)
                    val p = 1
                    val bx = if (b == 1) "x" else "${b}x"
                    val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                    val core = "${aPre}x\\ln($bx)"
                    val expr = "$core${fmtSignedConst(d)}"
                    val fullTex = "\\lim_{x \\to 0^+} \\left($expr\\right)"
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "ln_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "استعمل النهاية الاعتيادية \$\\lim_{x \\to 0^+} x\\ln x = 0\$.",
                                "Utilisez la limite usuelle \$\\lim_{x \\to 0^+} x\\ln x = 0\$."
                            )
                        ),
                        answerLatex = "$d",
                        expectedAnswer = ExpectedAnswer.Finite(d.toDouble()),
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("تطبيق النهاية عند \$0^+\$", "Limite usuelle en \$0^+\$"),
                                explanation = LocalizedText(
                                    "بما أن \$\\lim_{x \\to 0^+} x\\ln($bx) = 0\$ فإن:",
                                    "Comme \$\\lim_{x \\to 0^+} x\\ln($bx) = 0\$, on obtient :"
                                ),
                                latex = "$fullTex = $d"
                            )
                        ),
                        answerSympyStr = "$d"
                    )
                }
                else -> {
                    val x0 = rng.pick(listOf(0, 1))
                    val r = rng.pickNonZero(-4, 4)
                    val m = rng.pickNonZero(-3, 3)
                    // ln(1 + r*(x - x0)) = ln(r*x + (1 - r*x0))
                    val argTex = fmtLinearExpr(r, 1 - r * x0)
                    val denTex = fmtLinearExpr(m, -m * x0)
                    val fullTex = "\\lim_{x \\to $x0} \\frac{\\ln($argTex)}{$denTex}"
                    val ansTex = fmtRatLatex(r.toLong(), m.toLong())
                    val ansSym = fmtRatSympy(r.toLong(), m.toLong())
                    val lin = fmtFactor(x0)
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "ln_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "اكتب البسط على شكل \$\\ln(1 + u(x))\$ حيث \$u($x0) = 0\$ ثم استعمل \$\\lim_{u \\to 0} \\frac{\\ln(1+u)}{u} = 1\$.",
                                "Écrivez le numérateur sous la forme \$\\ln(1 + u(x))\$ avec \$u($x0) = 0\$, puis utilisez \$\\lim_{u \\to 0} \\frac{\\ln(1+u)}{u} = 1\$."
                            )
                        ),
                        answerLatex = ansTex,
                        expectedAnswer = ExpectedAnswer.Finite(r.toDouble() / m.toDouble()),
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("معدل التغيّر عند \$x_0 = $x0\$", "Taux d'accroissement en \$x_0 = $x0\$"),
                                explanation = LocalizedText(
                                    "نكتب \$$argTex = 1 + $r($lin)\$ ونضع \$u = $r($lin) \\to 0\$:",
                                    "On écrit \$$argTex = 1 + $r($lin)\$ et on pose \$u = $r($lin) \\to 0\$ :"
                                ),
                                latex = "$fullTex = \\frac{$r}{$m} = $ansTex"
                            )
                        ),
                        answerSympyStr = ansSym
                    )
                }
            }
        }

        if (diff == 2) {
            return when (variant) {
                0 -> {
                    val d = rng.pickNonZero(-4, 4)
                    val e = rng.pickNonZero(-4, 4)
                    val a = rng.pickNonZero(-4, 4)
                    val b = rng.pick(listOf(1, 2, 3))
                    val c = rng.range(1, 5)
                    val f = rng.range(-5, 5)
                    val p = rng.pick(listOf(1, 2))
                    val xp = if (p == 1) "x" else "x^{$p}"
                    val aAbs = if (abs(a) == 1) "" else "${abs(a)}"
                    val op = if (a > 0) " + " else " - "
                    val dPre = when (d) { 1 -> ""; -1 -> "-"; else -> "$d" }
                    val ePre = when (e) { 1 -> ""; -1 -> "-"; else -> "$e" }
                    val numTex = "$dPre$xp$op$aAbs\\ln(${fmtLinearExpr(b, c)})"
                    val denTex = "$ePre$xp${fmtSignedConst(f)}"
                    val fullTex = "\\lim_{x \\to +\\infty} \\frac{$numTex}{$denTex}"
                    val ansTex = fmtRatLatex(d.toLong(), e.toLong())
                    val ansSym = fmtRatSympy(d.toLong(), e.toLong())
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "ln_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "عمّل البسط والمقام بـ \$$xp\$ ثم استعمل \$\\frac{\\ln x}{$xp} \\to 0\$.",
                                "Factorisez numérateur et dénominateur par \$$xp\$ puis utilisez \$\\frac{\\ln x}{$xp} \\to 0\$."
                            )
                        ),
                        answerLatex = ansTex,
                        expectedAnswer = ExpectedAnswer.Finite(d.toDouble() / e.toDouble()),
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("التعميل بالحد الأكبر درجة", "Factorisation par le terme dominant"),
                                explanation = LocalizedText(
                                    "نُعمّل بـ \$$xp\$؛ وبما أن \$\\lim_{x \\to +\\infty} \\frac{\\ln(${fmtLinearExpr(b, c)})}{$xp} = 0\$:",
                                    "On factorise par \$$xp\$ ; comme \$\\lim_{x \\to +\\infty} \\frac{\\ln(${fmtLinearExpr(b, c)})}{$xp} = 0\$ :"
                                ),
                                latex = "$fullTex = $ansTex"
                            )
                        ),
                        answerSympyStr = ansSym
                    )
                }
                1 -> {
                    val a = rng.pickNonZero(-4, 4)
                    val b = rng.pick(listOf(1, 2, 3, 4)) * (if (a > 0) 1 else -1)
                    val c = rng.pick(listOf(1, 2, 3))
                    val d = rng.range(1, 5)
                    val p = rng.pick(listOf(1, 2))
                    val xp = if (p == 1) "x" else "x^{$p}"
                    val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                    val bAbs = if (abs(b) == 1) "" else "${abs(b)}"
                    val op = if (b > 0) " - " else " + "
                    val exprTex = "$aPre$xp$op$bAbs\\ln(${fmtLinearExpr(c, d)})"
                    val fullTex = "\\lim_{x \\to +\\infty} \\left($exprTex\\right)"
                    val isPos = a > 0
                    val ansTex = if (isPos) "+\\infty" else "-\\infty"
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "ln_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "عمّل بـ \$$xp\$ لتحويل \$\\infty - \\infty\$ إلى جداء.",
                                "Factorisez par \$$xp\$ pour lever l'indétermination \$\\infty - \\infty\$."
                            )
                        ),
                        answerLatex = ansTex,
                        expectedAnswer = if (isPos) ExpectedAnswer.PosInf else ExpectedAnswer.NegInf,
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("التعميل بـ \$$xp\$", "Factorisation par \$$xp\$"),
                                explanation = LocalizedText(
                                    "بالتعميل بـ \$$xp\$ تؤول النسبة \$\\frac{\\ln(${fmtLinearExpr(c, d)})}{$xp}\$ إلى \$0\$:",
                                    "En factorisant par \$$xp\$, le quotient \$\\frac{\\ln(${fmtLinearExpr(c, d)})}{$xp}\$ tend vers \$0\$ :"
                                ),
                                latex = "$fullTex = $ansTex"
                            )
                        ),
                        answerSympyStr = if (isPos) "oo" else "-oo"
                    )
                }
                else -> {
                    val p = rng.pick(listOf(2, 3, 4))
                    var q = rng.pick(listOf(1, 2, 3))
                    while (q == p) q = rng.pick(listOf(1, 2, 3))
                    val a = rng.pick(listOf(1, 2, 3))
                    val c = rng.range(1, 5)
                    val d = rng.pick(listOf(1, 2, 3))
                    val e = rng.range(1, 6)
                    val aPre = if (a == 1) "" else "$a"
                    val dPre = if (d == 1) "" else "$d"
                    val qTex = if (q == 1) "x" else "x^{$q}"
                    val numTex = "\\ln(${aPre}x^{$p} + $c)"
                    val denTex = "\\ln(${dPre}$qTex + $e)"
                    val fullTex = "\\lim_{x \\to +\\infty} \\frac{$numTex}{$denTex}"
                    val ansTex = fmtRatLatex(p.toLong(), q.toLong())
                    val ansSym = fmtRatSympy(p.toLong(), q.toLong())
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "ln_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "عمّل داخل كل لوغاريتم بأكبر قوة لـ \$x\$ ثم اختزل بـ \$\\ln x\$.",
                                "Factorisez par la plus grande puissance de \$x\$ dans chaque \$\\ln\$ puis simplifiez par \$\\ln x\$."
                            )
                        ),
                        answerLatex = ansTex,
                        expectedAnswer = ExpectedAnswer.Finite(p.toDouble() / q.toDouble()),
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("التعميل داخل اللوغاريتم", "Factorisation dans chaque logarithme"),
                                explanation = LocalizedText(
                                    "نكتب البسط \$${p}\\ln x + \\dots\$ والمقام \$${q}\\ln x + \\dots\$ ثم نختزل بـ \$\\ln x\$:",
                                    "On obtient \$${p}\\ln x + \\dots\$ au numérateur et \$${q}\\ln x + \\dots\$ au dénominateur, puis on simplifie par \$\\ln x\$ :"
                                ),
                                latex = "$fullTex = $ansTex"
                            )
                        ),
                        answerSympyStr = ansSym
                    )
                }
            }
        }

        // Diff 3
        return when (variant) {
            0 -> {
                val k = rng.pick(listOf(1, 2, 3))
                val a = rng.range(-5, 6)
                var b = rng.range(-5, 6)
                while (b == a) b = rng.range(-5, 6)
                val u1 = fmtLinearExpr(k, a)
                val u2 = fmtLinearExpr(k, b)
                val fullTex = "\\lim_{x \\to +\\infty} x\\left(\\ln($u1) - \\ln($u2)\\right)"
                val diffAb = (a - b).toLong()
                val ansTex = fmtRatLatex(diffAb, k.toLong())
                val ansSym = fmtRatSympy(diffAb, k.toLong())
                GeneratedExercise(
                    seed = seed,
                    chapter = "limits",
                    technique = "ln_limits",
                    difficulty = diff,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "استعمل \$\\ln A - \\ln B = \\ln(A/B)\$ وأظهر الشكل \$\\ln(1 + u)\$.",
                            "Utilisez \$\\ln A - \\ln B = \\ln(A/B)\$ puis faites apparaître \$\\ln(1 + u)\$."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = ExpectedAnswer.Finite(diffAb.toDouble() / k.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("دمج اللوغاريتمين", "Regroupement des logarithmes"),
                            explanation = LocalizedText(
                                "نكتب \$x\\ln\\left(1 + \\frac{$diffAb}{$u2}\\right)\$ ونستعمل \$\\lim_{u \\to 0} \\frac{\\ln(1+u)}{u} = 1\$:",
                                "On écrit \$x\\ln\\left(1 + \\frac{$diffAb}{$u2}\\right)\$ et on utilise \$\\lim_{u \\to 0} \\frac{\\ln(1+u)}{u} = 1\$ :"
                            ),
                            latex = "$fullTex = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            }
            1 -> {
                val a = rng.pickNonZero(-4, 4)
                val d = rng.range(-5, 5)
                val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                val exprTex = "${aPre}x(\\ln x)^2${fmtSignedConst(d)}"
                val fullTex = "\\lim_{x \\to 0^+} \\left($exprTex\\right)"
                GeneratedExercise(
                    seed = seed,
                    chapter = "limits",
                    technique = "ln_limits",
                    difficulty = diff,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "اكتب \$x(\\ln x)^2 = 4(\\sqrt{x}\\ln\\sqrt{x})^2\$ واستعمل \$\\lim_{t \\to 0^+} t\\ln t = 0\$.",
                            "Écrivez \$x(\\ln x)^2 = 4(\\sqrt{x}\\ln\\sqrt{x})^2\$ et utilisez \$\\lim_{t \\to 0^+} t\\ln t = 0\$."
                        )
                    ),
                    answerLatex = "$d",
                    expectedAnswer = ExpectedAnswer.Finite(d.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("وضع \$t = \\sqrt{x} \\to 0^+\$", "Changement \$t = \\sqrt{x} \\to 0^+\$"),
                            explanation = LocalizedText(
                                "لدينا \$\\lim_{x \\to 0^+} x(\\ln x)^2 = 0\$، ومنه:",
                                "On a \$\\lim_{x \\to 0^+} x(\\ln x)^2 = 0\$, d'où :"
                            ),
                            latex = "$fullTex = $d"
                        )
                    ),
                    answerSympyStr = "$d"
                )
            }
            else -> {
                val x0 = rng.pick(listOf(-1, 1, 2))
                val r = rng.pickNonZero(-4, 4)
                val L = rng.pick(listOf(1, 2, 3))
                val k = rng.pickNonZero(-3, 3)
                // sqrt(k*(x - x0) + L^2) - L has slope k/(2L)
                val pTex = fmtLinearExpr(k, L * L - k * x0)
                val lnTex = "\\ln(${fmtLinearExpr(r, 1 - r * x0)})"
                val fullTex = "\\lim_{x \\to $x0} \\frac{$lnTex}{\\sqrt{$pTex} - $L}"
                val ansNum = (2 * L * r).toLong()
                val ansDen = k.toLong()
                val ansTex = fmtRatLatex(ansNum, ansDen)
                val ansSym = fmtRatSympy(ansNum, ansDen)
                GeneratedExercise(
                    seed = seed,
                    chapter = "limits",
                    technique = "ln_limits",
                    difficulty = diff,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "اضرب في المرافق \$\\sqrt{$pTex} + $L\$ واستعمل \$\\frac{\\ln(1+u)}{u} \\to 1\$.",
                            "Multipliez par le conjugué \$\\sqrt{$pTex} + $L\$ puis utilisez \$\\frac{\\ln(1+u)}{u} \\to 1\$."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = ExpectedAnswer.Finite(ansNum.toDouble() / ansDen.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("الجمع بين المرافق واللوغاريتم", "Conjugué et limite logarithmique"),
                            explanation = LocalizedText(
                                "بعد الضرب في المرافق والاختزال بـ \$(${fmtFactor(x0)})\$ نحصل على:",
                                "Après multiplication par le conjugué et simplification par \$(${fmtFactor(x0)})\$ :"
                            ),
                            latex = "$fullTex = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 2. EXP LIMITS (exp_limits)
    // -----------------------------------------------------------------------

    fun genExpLimits(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        val variant = rng.nextInt(3)

        if (diff == 1) {
            return when (variant) {
                0 -> {
                    val a = rng.pickNonZero(-4, 4)
                    val k = rng.pick(listOf(1, 2))
                    val c = rng.range(-4, 4)
                    val p = rng.pick(listOf(1, 2))
                    val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                    val ek = if (k == 1) "e^{x}" else "e^{${k}x}"
                    val xp = if (p == 1) "x" else "x^{$p}"
                    val fullTex = "\\lim_{x \\to +\\infty} \\frac{$aPre$ek${fmtSignedConst(c)}}{$xp}"
                    val isPos = a > 0
                    val ansTex = if (isPos) "+\\infty" else "-\\infty"
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "exp_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "استعمل \$\\lim_{x \\to +\\infty} \\frac{e^x}{x^$p} = +\\infty\$ وانتبه لإشارة المعامل \$a\$.",
                                "Utilisez \$\\lim_{x \\to +\\infty} \\frac{e^x}{x^$p} = +\\infty\$ en tenant compte du signe."
                            )
                        ),
                        answerLatex = ansTex,
                        expectedAnswer = if (isPos) ExpectedAnswer.PosInf else ExpectedAnswer.NegInf,
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("المقارنة الأسية عند \$+\\infty\$", "Croissance comparée en \$+\\infty\$"),
                                explanation = LocalizedText(
                                    "بما أن \$\\lim_{x \\to +\\infty} \\frac{$ek}{$xp} = +\\infty\$ ومع الإشارة، نحصل على:",
                                    "Comme \$\\lim_{x \\to +\\infty} \\frac{$ek}{$xp} = +\\infty\$, on obtient :"
                                ),
                                latex = "$fullTex = $ansTex"
                            )
                        ),
                        answerSympyStr = if (isPos) "oo" else "-oo"
                    )
                }
                1 -> {
                    val a = rng.pickNonZero(-4, 4)
                    val k = rng.pick(listOf(1, 2))
                    val p = rng.pick(listOf(1, 2))
                    val c = rng.range(-5, 5)
                    val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                    val ek = if (k == 1) "e^{x}" else "e^{${k}x}"
                    val xp = if (p == 1) "x" else "x^{$p}"
                    val fullTex = "\\lim_{x \\to -\\infty} \\left($aPre$xp $ek${fmtSignedConst(c)}\\right)"
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "exp_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "استعمل النهاية الأساسية \$\\lim_{x \\to -\\infty} x^n e^x = 0\$.",
                                "Utilisez la limite usuelle \$\\lim_{x \\to -\\infty} x^n e^x = 0\$."
                            )
                        ),
                        answerLatex = "$c",
                        expectedAnswer = ExpectedAnswer.Finite(c.toDouble()),
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("النهاية عند \$-\\infty\$", "Limite usuelle en \$-\\infty\$"),
                                explanation = LocalizedText(
                                    "بما أن \$\\lim_{x \\to -\\infty} $xp $ek = 0\$:",
                                    "Comme \$\\lim_{x \\to -\\infty} $xp $ek = 0\$ :"
                                ),
                                latex = "$fullTex = $c"
                            )
                        ),
                        answerSympyStr = "$c"
                    )
                }
                else -> {
                    val a = rng.pickNonZero(-4, 4)
                    val m = rng.pickNonZero(-4, 4)
                    val eArg = when (a) { 1 -> "x"; -1 -> "-x"; else -> "${a}x" }
                    val mTex = when (m) { 1 -> "x"; -1 -> "-x"; else -> "${m}x" }
                    val fullTex = "\\lim_{x \\to 0} \\frac{e^{$eArg} - 1}{$mTex}"
                    val ansTex = fmtRatLatex(a.toLong(), m.toLong())
                    val ansSym = fmtRatSympy(a.toLong(), m.toLong())
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "exp_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "استعمل النهاية الاعتيادية \$\\lim_{u \\to 0} \\frac{e^u - 1}{u} = 1\$.",
                                "Utilisez \$\\lim_{u \\to 0} \\frac{e^u - 1}{u} = 1\$."
                            )
                        ),
                        answerLatex = ansTex,
                        expectedAnswer = ExpectedAnswer.Finite(a.toDouble() / m.toDouble()),
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("معدل التغيّر الأسي", "Taux d'accroissement exponentiel"),
                                explanation = LocalizedText(
                                    "بوضع \$u = $eArg \\to 0\$ نكتب \$\\frac{e^{$eArg}-1}{$mTex} = \\frac{$a}{$m} \\cdot \\frac{e^u - 1}{u}\$:",
                                    "En posant \$u = $eArg \\to 0\$, on a \$\\frac{e^{$eArg}-1}{$mTex} = \\frac{$a}{$m} \\cdot \\frac{e^u - 1}{u}\$ :"
                                ),
                                latex = "$fullTex = $ansTex"
                            )
                        ),
                        answerSympyStr = ansSym
                    )
                }
            }
        }

        if (diff == 2) {
            return when (variant) {
                0, 1 -> {
                    val atPosInf = variant == 0
                    val a = rng.pickNonZero(-5, 5)
                    val b = rng.range(-4, 4)
                    val c = rng.pickNonZero(-5, 5)
                    val d = rng.pickNonZero(-5, 5)
                    val f = rng.range(-4, 4)
                    val g = rng.pickNonZero(-5, 5)
                    fun fmtE(c2: Int, c1: Int, c0: Int): String {
                        val t2 = when (c2) { 1 -> "e^{2x}"; -1 -> "-e^{2x}"; else -> "${c2}e^{2x}" }
                        val t1 = when {
                            c1 == 0 -> ""
                            c1 == 1 -> " + e^{x}"
                            c1 == -1 -> " - e^{x}"
                            c1 > 0 -> " + ${c1}e^{x}"
                            else -> " - ${-c1}e^{x}"
                        }
                        return "\$t2\$t1${fmtSignedConst(c0)}"
                    }
                    val numTex = fmtE(a, b, c)
                    val denTex = fmtE(d, f, g)
                    val pt = if (atPosInf) "+\\infty" else "-\\infty"
                    val fullTex = "\\lim_{x \\to $pt} \\frac{$numTex}{$denTex}"
                    val ansNum = if (atPosInf) a.toLong() else c.toLong()
                    val ansDen = if (atPosInf) d.toLong() else g.toLong()
                    val ansTex = fmtRatLatex(ansNum, ansDen)
                    val ansSym = fmtRatSympy(ansNum, ansDen)
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "exp_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            if (atPosInf) LocalizedText(
                                "عند \$+\\infty\$ عمّل البسط والمقام بالحد المهيمن \$e^{2x}\$.",
                                "En \$+\\infty\$, factorisez par le terme dominant \$e^{2x}\$."
                            ) else LocalizedText(
                                "انتبه! ما هي نهاية \$e^x\$ عندما يؤول \$x\$ إلى \$-\\infty\$؟",
                                "Attention à la borne \$-\\infty\$ ! Quelle est la limite de \$e^x\$ en \$-\\infty\$ ?"
                            )
                        ),
                        answerLatex = ansTex,
                        expectedAnswer = ExpectedAnswer.Finite(ansNum.toDouble() / ansDen.toDouble()),
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText(
                                    if (atPosInf) "التعميل بـ \$e^{2x}\$ عند \$+\\infty\$" else "فخّ \$-\\infty\$: تعويض مباشر",
                                    if (atPosInf) "Factorisation par \$e^{2x}\$ en \$+\\infty\$" else "Piège en \$-\\infty\$ : calcul direct"
                                ),
                                explanation = LocalizedText(
                                    if (atPosInf) "نُعمّل بـ \$e^{2x}\$ لأن \$e^{-x} \\to 0\$:" else "عند \$-\\infty\$ لدينا \$e^x \\to 0\$ و \$e^{2x} \\to 0\$:",
                                    if (atPosInf) "On factorise par \$e^{2x}\$ car \$e^{-x} \\to 0\$ :" else "En \$-\\infty\$, \$e^x \\to 0\$ et \$e^{2x} \\to 0\$ :"
                                ),
                                latex = "$fullTex = $ansTex"
                            )
                        ),
                        answerSympyStr = ansSym
                    )
                }
                else -> {
                    val a = rng.pickNonZero(-4, 4)
                    val b = rng.pick(listOf(1, 2, 3, 4)) * (if (a > 0) 1 else -1)
                    val k = rng.pick(listOf(1, 2))
                    val p = rng.pick(listOf(1, 2, 3))
                    val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                    val ek = if (k == 1) "e^{x}" else "e^{${k}x}"
                    val xp = if (p == 1) "x" else "x^{$p}"
                    val bAbs = if (abs(b) == 1) "" else "${abs(b)}"
                    val op = if (b > 0) " - " else " + "
                    val fullTex = "\\lim_{x \\to +\\infty} \\left($aPre$ek$op$bAbs$xp\\right)"
                    val isPos = a > 0
                    val ansTex = if (isPos) "+\\infty" else "-\\infty"
                    GeneratedExercise(
                        seed = seed,
                        chapter = "limits",
                        technique = "exp_limits",
                        difficulty = diff,
                        fullLimitLatex = fullTex,
                        hints = listOf(
                            LocalizedText(
                                "عمّل بـ \$$ek\$ واستعمل \$\\frac{$xp}{$ek} \\to 0\$.",
                                "Factorisez par \$$ek\$ puis utilisez \$\\frac{$xp}{$ek} \\to 0\$."
                            )
                        ),
                        answerLatex = ansTex,
                        expectedAnswer = if (isPos) ExpectedAnswer.PosInf else ExpectedAnswer.NegInf,
                        steps = listOf(
                            SolutionStep(
                                title = LocalizedText("التعميل بالحد الأسي", "Factorisation par \$$ek\$"),
                                explanation = LocalizedText(
                                    "نُعمّل بـ \$$ek\$ ونستعمل المقارنة الأسية:",
                                    "On factorise par \$$ek\$ et on applique la croissance comparée :"
                                ),
                                latex = "$fullTex = $ansTex"
                            )
                        ),
                        answerSympyStr = if (isPos) "oo" else "-oo"
                    )
                }
            }
        }

        // Diff 3
        return when (variant) {
            0, 1 -> {
                val a = rng.pick(listOf(2, 3, 4, 5, 7))
                val b = rng.range(1, 7)
                val k = rng.pick(listOf(1, 2, 3))
                val kx = if (k == 1) "x" else "${k}x"
                val ek = if (k == 1) "e^{x}" else "e^{${k}x}"
                val fullTex = "\\lim_{x \\to +\\infty} \\left(\\ln($a$ek + $b) - $kx\\right)"
                GeneratedExercise(
                    seed = seed,
                    chapter = "limits",
                    technique = "exp_limits",
                    difficulty = diff,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "عمّل بـ \$$ek\$ داخل اللوغاريتم ثم اختزل \$$kx\$ مع \$- $kx\$.",
                            "Factorisez par \$$ek\$ dans le \$\\ln\$ pour simplifier \$$kx\$."
                        )
                    ),
                    answerLatex = "\\ln($a)",
                    expectedAnswer = ExpectedAnswer.Finite(ln(a.toDouble())),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("التعميل بـ \$$ek\$ داخل اللوغاريتم", "Factorisation dans le logarithme"),
                            explanation = LocalizedText(
                                "نكتب \$\\ln($ek($a + ${b}e^{-$kx})) - $kx = \\ln($a + ${b}e^{-$kx})\$؛ وعند \$+\\infty\$:",
                                "On écrit \$\\ln($ek($a + ${b}e^{-$kx})) - $kx = \\ln($a + ${b}e^{-$kx})\$ ; en \$+\\infty\$ :"
                            ),
                            latex = "$fullTex = \\ln($a)"
                        )
                    ),
                    answerSympyStr = "log($a)"
                )
            }
            else -> {
                val a = rng.pickNonZero(-6, 6)
                val b = rng.range(-4, 4)
                val fracTex = if (a > 0) "\\frac{$a}{x}" else "-\\frac{${-a}}{x}"
                val exprTex = "x\\left(e^{$fracTex} - 1\\right)${fmtSignedConst(b)}"
                val fullTex = "\\lim_{x \\to +\\infty} \\left($exprTex\\right)"
                val ans = a + b
                GeneratedExercise(
                    seed = seed,
                    chapter = "limits",
                    technique = "exp_limits",
                    difficulty = diff,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "ضع تغيير المتغير \$t = \\frac{1}{x} \\to 0^+\$ لاستعمال \$\\lim_{t \\to 0} \\frac{e^{at}-1}{t} = a\$.",
                            "Posez \$t = \\frac{1}{x} \\to 0^+\$ pour utiliser \$\\lim_{t \\to 0} \\frac{e^{at}-1}{t} = a\$."
                        )
                    ),
                    answerLatex = "$ans",
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("تغيير المتغير \$t = 1/x\$", "Changement de variable \$t = 1/x\$"),
                            explanation = LocalizedText(
                                "نحصل على \$\\lim_{t \\to 0^+} \\frac{e^{${a}t}-1}{t} = $a\$، وبإضافة الثابت:",
                                "On obtient \$\\lim_{t \\to 0^+} \\frac{e^{${a}t}-1}{t} = $a\$, et en ajoutant la constante :"
                            ),
                            latex = "$fullTex = $ans"
                        )
                    ),
                    answerSympyStr = "$ans"
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 3. SEQ_LIMITS (seq_limits)
    // -----------------------------------------------------------------------

    fun genSeqLimits(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        val prompt = LocalizedText("احسب نهاية المتتالية التالية:", "Calculer la limite de la suite suivante :")
        if (diff == 1) {
            return if (rng.nextInt(2) == 0) {
                val p = rng.pick(listOf(1, 2, 3))
                val q = if (rng.nextInt(10) < 6) p else rng.pick(listOf(1, 2, 3))
                val a = rng.pickNonZero(-6, 6)
                val b = rng.range(-4, 4)
                val d = rng.pickNonZero(1, 6)
                val e = rng.range(1, 6)
                val np = if (p == 1) "n" else "n^{$p}"
                val nq = if (q == 1) "n" else "n^{$q}"
                val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                val dPre = if (d == 1) "" else "$d"
                val numTex = "$aPre$np${fmtSignedConst(b)}"
                val denTex = "$dPre$nq + $e"
                val fullTex = "\\lim_{n \\to +\\infty} \\frac{$numTex}{$denTex}"
                val (ansTex, expAns, ansSym) = when {
                    p == q -> Triple(fmtRatLatex(a.toLong(), d.toLong()), ExpectedAnswer.Finite(a.toDouble() / d.toDouble()), fmtRatSympy(a.toLong(), d.toLong()))
                    p > q -> if (a > 0) Triple("+\\infty", ExpectedAnswer.PosInf, "oo") else Triple("-\\infty", ExpectedAnswer.NegInf, "-oo")
                    else -> Triple("0", ExpectedAnswer.Finite(0.0), "0")
                }
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_limits",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = prompt,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "عمّل بأكبر قوة لـ \$n\$ في البسط والمقام.",
                            "Factorisez par la plus grande puissance de \$n\$ au numérateur et au dénominateur."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = expAns,
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("النسبة بين الحدين الأكبر درجة", "Quotient des termes dominants"),
                            explanation = LocalizedText(
                                "عند \$n \\to +\\infty\$ نختزل بأكبر قوة لـ \$n\$:",
                                "En \$n \\to +\\infty\$, on simplifie par la plus grande puissance de \$n\$ :"
                            ),
                            latex = "$fullTex = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            } else {
                val pool = listOf(1L to 2L, 2L to 3L, 3L to 4L, -1L to 2L, 3L to 2L, 5L to 4L, 2L to 1L)
                val (qp, qq) = rng.pick(pool)
                val a = rng.pickNonZero(-5, 5)
                val c = rng.range(-6, 6)
                val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a\\cdot " }
                val exprTex = "$aPre${fmtPowBaseN(qp, qq)}${fmtSignedConst(c)}"
                val fullTex = if (c != 0) "\\lim_{n \\to +\\infty} \\left($exprTex\\right)" else "\\lim_{n \\to +\\infty} $exprTex"
                val conv = abs(qp) < abs(qq)
                val (ansTex, expAns, ansSym) = if (conv) {
                    Triple("$c", ExpectedAnswer.Finite(c.toDouble()), "$c")
                } else {
                    if (a > 0) Triple("+\\infty", ExpectedAnswer.PosInf, "oo") else Triple("-\\infty", ExpectedAnswer.NegInf, "-oo")
                }
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_limits",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = prompt,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "قارن الأساس \$q\$ بـ \$1\$: إذا كان \$|q| < 1\$ فإن \$q^n \\to 0\$، وإذا كان \$q > 1\$ فإن \$q^n \\to +\\infty\$.",
                            "Comparez la raison \$q\$ à \$1\$ : si \$|q| < 1\$ alors \$q^n \\to 0\$."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = expAns,
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("نهاية \$q^n\$", "Limite de la suite géométrique \$q^n\$"),
                            explanation = LocalizedText(
                                if (conv) "بما أن \$|q| < 1\$ فإن \$q^n \\to 0\$:" else "بما أن \$q > 1\$ فإن \$q^n \\to +\\infty\$:",
                                if (conv) "Comme \$|q| < 1\$, \$q^n \\to 0\$ :" else "Comme \$q > 1\$, \$q^n \\to +\\infty\$ :"
                            ),
                            latex = "$fullTex = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            }
        }

        if (diff == 2) {
            return if (rng.nextInt(2) == 0) {
                val (p, q) = rng.pick(listOf(3 to 2, 5 to 3, 4 to 3, 5 to 2))
                val s1 = rng.pick(listOf(0, 1))
                val a = rng.pickNonZero(-4, 4)
                val b = rng.pickNonZero(-5, 5)
                val c = rng.pickNonZero(1, 5)
                val d = rng.pickNonZero(-5, 5)
                val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a\\cdot " }
                val bStr = (if (b > 0) " + " else " - ") + (if (abs(b) == 1) "" else "${abs(b)}\\cdot ")
                val cPre = if (c == 1) "" else "$c\\cdot "
                val dStr = (if (d > 0) " + " else " - ") + (if (abs(d) == 1) "" else "${abs(d)}\\cdot ")
                val numTex = "$aPre${fmtPowBaseN(p.toLong(), 1, s1)}$bStr${q}^n"
                val denTex = "$cPre${p}^n$dStr${q}^n"
                val fullTex = "\\lim_{n \\to +\\infty} \\frac{$numTex}{$denTex}"
                val numAns = a.toLong() * (if (s1 == 1) p.toLong() else 1L)
                val denAns = c.toLong()
                val ansTex = fmtRatLatex(numAns, denAns)
                val ansSym = fmtRatSympy(numAns, denAns)
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_limits",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = prompt,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "عمّل البسط والمقام بأكبر أساس (\$$p^n\$) ثم استعمل \$(\\frac{$q}{$p})^n \\to 0\$.",
                            "Factorisez par la plus grande base (\$$p^n\$) puis utilisez \$(\\frac{$q}{$p})^n \\to 0\$."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = ExpectedAnswer.Finite(numAns.toDouble() / denAns.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("التعميل بالأساس الأكبر \$$p^n\$", "Factorisation par la base dominante \$$p^n\$"),
                            explanation = LocalizedText(
                                "بما أن \$0 < \\frac{$q}{$p} < 1\$ فإن \$\\left(\\frac{$q}{$p}\\right)^n \\to 0\$:",
                                "Comme \$0 < \\frac{$q}{$p} < 1\$, \$\\left(\\frac{$q}{$p}\\right)^n \\to 0\$ :"
                            ),
                            latex = "$fullTex = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            } else {
                val oscKind = rng.pick(listOf("(-1)^n", "\\cos(n)", "\\sin(n)"))
                val a = rng.pickNonZero(-5, 5)
                val b = rng.pickNonZero(-6, 6)
                val d = rng.pickNonZero(1, 5)
                val e = rng.range(1, 6)
                val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a" }
                val bStr = (if (b > 0) " + " else " - ") + (if (abs(b) == 1) "" else "${abs(b)}")
                val dPre = if (d == 1) "" else "$d"
                val fullTex = "\\lim_{n \\to +\\infty} \\frac{${aPre}n$bStr$oscKind}{${dPre}n + $e}"
                val ansTex = fmtRatLatex(a.toLong(), d.toLong())
                val ansSym = fmtRatSympy(a.toLong(), d.toLong())
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_limits",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = prompt,
                    fullLimitLatex = fullTex,
                    hints = listOf(
                        LocalizedText(
                            "استعمل التأطير \$-1 \\le $oscKind \\le 1\$ بعد القسمة على \$n\$ (مبرهنة الحصر).",
                            "Utilisez \$-1 \\le $oscKind \\le 1\$ après division par \$n\$ (gendarmes)."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = ExpectedAnswer.Finite(a.toDouble() / d.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("التأطير ومصاديق التقارب", "Théorème des gendarmes"),
                            explanation = LocalizedText(
                                "بما أن \$\\left|\\frac{$oscKind}{n}\\right| \\le \\frac{1}{n} \\to 0\$ نحصل على:",
                                "Comme \$\\left|\\frac{$oscKind}{n}\\right| \\le \\frac{1}{n} \\to 0\$, il vient :"
                            ),
                            latex = "$fullTex = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            }
        }

        // Diff 3: Hidden base trap 2^(2n) = 4^n or composition f(u_n)
        return if (rng.nextInt(2) == 0) {
            val baseRoot = rng.pick(listOf(2, 3))
            val p = baseRoot * baseRoot
            val q = if (p == 4) rng.pick(listOf(2, 3)) else rng.pick(listOf(5, 7))
            val a = rng.pickNonZero(-4, 4)
            val b = rng.pickNonZero(-5, 5)
            val c = rng.pickNonZero(1, 5)
            val d = rng.pickNonZero(-5, 5)
            val aPre = when (a) { 1 -> ""; -1 -> "-"; else -> "$a\\cdot " }
            val bStr = (if (b > 0) " + " else " - ") + (if (abs(b) == 1) "" else "${abs(b)}\\cdot ")
            val cPre = if (c == 1) "" else "$c\\cdot "
            val dStr = (if (d > 0) " + " else " - ") + (if (abs(d) == 1) "" else "${abs(d)}\\cdot ")
            val fullTex = "\\lim_{n \\to +\\infty} \\frac{$aPre$p^n$bStr$q^n}{$cPre$baseRoot^{2n}$dStr$q^n}"
            val ansTex = fmtRatLatex(a.toLong(), c.toLong())
            val ansSym = fmtRatSympy(a.toLong(), c.toLong())
            GeneratedExercise(
                seed = seed,
                chapter = "sequences",
                technique = "seq_limits",
                difficulty = diff,
                answerKind = "constant",
                prompt = prompt,
                fullLimitLatex = fullTex,
                hints = listOf(
                    LocalizedText(
                        "انتبه! اكتب \$$baseRoot^{2n} = $p^n\$ ثم عمّل بـ \$$p^n\$.",
                        "Attention ! Écrivez \$$baseRoot^{2n} = $p^n\$ puis factorisez par \$$p^n\$."
                    )
                ),
                answerLatex = ansTex,
                expectedAnswer = ExpectedAnswer.Finite(a.toDouble() / c.toDouble()),
                steps = listOf(
                    SolutionStep(
                        title = LocalizedText("كشف فخ الأساس المخفي", "Déjouer le piège \$$baseRoot^{2n} = $p^n\$"),
                        explanation = LocalizedText(
                            "بعد تعويض \$$baseRoot^{2n} = $p^n\$ والتعميل بـ \$$p^n\$ نحصل على:",
                            "En écrivant \$$baseRoot^{2n} = $p^n\$ et en factorisant par \$$p^n\$, on obtient :"
                        ),
                        latex = "$fullTex = $ansTex"
                    )
                ),
                answerSympyStr = ansSym
            )
        } else {
            val fnKind = rng.pick(listOf("ln", "exp", "sqrt"))
            val p = rng.pick(listOf(2, 3, 5))
            val a = rng.pick(listOf(2, 3, 4, 6))
            var c = rng.pick(listOf(1, 2, 3))
            while (a == c) c = rng.pick(listOf(1, 2, 3))
            val b = rng.range(1, 7)
            val d = rng.range(1, 7)
            val cPre = if (c == 1) "" else "$c\\cdot "
            val innerTex = "\\frac{$a\\cdot $p^n + $b}{$cPre$p^n + $d}"
            val ratioTex = fmtRatLatex(a.toLong(), c.toLong())
            val ratioSym = fmtRatSympy(a.toLong(), c.toLong())
            val ratioVal = a.toDouble() / c.toDouble()
            val (exprTex, ansTex, ansVal, ansSym) = when (fnKind) {
                "ln" -> Quad(
                    "\\ln\\left($innerTex\\right)",
                    "\\ln\\left($ratioTex\\right)",
                    ln(ratioVal),
                    "log($ratioSym)"
                )
                "exp" -> Quad(
                    "\\exp\\left($innerTex\\right)",
                    "e^{$ratioTex}",
                    kotlin.math.exp(ratioVal),
                    "exp($ratioSym)"
                )
                else -> Quad(
                    "\\sqrt{$innerTex}",
                    "\\sqrt{$ratioTex}",
                    kotlin.math.sqrt(ratioVal),
                    "sqrt($ratioSym)"
                )
            }
            val fullTex = "\\lim_{n \\to +\\infty} $exprTex"
            GeneratedExercise(
                seed = seed,
                chapter = "sequences",
                technique = "seq_limits",
                difficulty = diff,
                answerKind = "constant",
                prompt = prompt,
                fullLimitLatex = fullTex,
                hints = listOf(
                    LocalizedText(
                        "احسب أولاً نهاية الكسر الداخلي بالتعميل بـ \$$p^n\$ ثم استعمل اتصال الدالة.",
                        "Calculez d'abord la limite du quotient intérieur puis utilisez la continuité."
                    )
                ),
                answerLatex = ansTex,
                expectedAnswer = ExpectedAnswer.Finite(ansVal),
                steps = listOf(
                    SolutionStep(
                        title = LocalizedText("اتصال \$v_n = f(u_n)\$", "Continuité \$v_n = f(u_n)\$"),
                        explanation = LocalizedText(
                            "نهاية الكسر الداخلي هي \$$ratioTex\$، وباتصال الدالة نحصل على:",
                            "Le quotient intérieur tend vers \$$ratioTex\$ ; par continuité :"
                        ),
                        latex = "$fullTex = $ansTex"
                    )
                ),
                answerSympyStr = ansSym
            )
        }
    }

    // -----------------------------------------------------------------------
    // 4. SEQ_ARITH_GEO (seq_arith_geo)
    // -----------------------------------------------------------------------

    fun genSeqArithGeo(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        val convPool = listOf(1L to 2L, 1L to 3L, 2L to 3L, 3L to 4L, 1L to 4L)
        val divPool = listOf(2L to 1L, 3L to 1L, 3L to 2L)

        val ask = when (diff) {
            1 -> if (rng.nextInt(2) == 0) "ratio" else "v0"
            2 -> if (rng.nextInt(10) < 6) "general_un" else "limit_un"
            else -> if (rng.nextInt(10) < 6) "sum_vn" else "general_un"
        }

        val (qp, qq) = if (ask == "limit_un") rng.pick(convPool + divPool) else rng.pick(convPool)
        val alpha = rng.pickNonZero(-4, 5)
        val bNum = alpha.toLong() * (qq - qp)
        val v0 = rng.pickNonZero(-4, 4)
        val u0 = alpha + v0

        val qTex = fmtRatLatex(qp, qq)
        val qSym = fmtRatSympy(qp, qq)
        val bTex = fmtRatLatex(bNum, qq)
        val recTex = when {
            bNum == 0L -> "$qTex u_n"
            bNum > 0L -> "$qTex u_n + ${fmtRatLatex(bNum, qq)}"
            else -> "$qTex u_n - ${fmtRatLatex(-bNum, qq)}"
        }
        val vnDef = when {
            alpha == 0 -> "u_n"
            alpha > 0 -> "u_n - $alpha"
            else -> "u_n + ${-alpha}"
        }
        val sysTex = "\\begin{cases} u_0 = $u0 \\\\ u_{n+1} = $recTex \\end{cases}, \\qquad v_n = $vnDef"

        return when (ask) {
            "ratio" -> GeneratedExercise(
                seed = seed,
                chapter = "sequences",
                technique = "seq_arith_geo",
                difficulty = diff,
                answerKind = "constant",
                prompt = LocalizedText(
                    "المتتالية \$(v_n)\$ هندسية. احسب أساسها \$q\$:",
                    "La suite \$(v_n)\$ est géométrique. Calculer sa raison \$q\$ :"
                ),
                fullLimitLatex = sysTex,
                hints = listOf(
                    LocalizedText(
                        "احسب \$v_{n+1} = u_{n+1} - ($alpha)\$ وعمّل لتظهر \$v_n\$.",
                        "Calculez \$v_{n+1} = u_{n+1} - ($alpha)\$ puis factorisez par \$v_n\$."
                    )
                ),
                answerLatex = qTex,
                expectedAnswer = ExpectedAnswer.Finite(qp.toDouble() / qq.toDouble()),
                steps = listOf(
                    SolutionStep(
                        title = LocalizedText("تعبير \$v_{n+1}\$ بدلالة \$v_n\$", "Expression de \$v_{n+1}\$"),
                        explanation = LocalizedText(
                            "نعوّض \$u_{n+1}\$ ونُعمّل بمعامل \$u_n\$:",
                            "On remplace \$u_{n+1}\$ et on factorise :"
                        ),
                        latex = "v_{n+1} = $qTex($vnDef) = $qTex v_n \\implies q = $qTex"
                    )
                ),
                answerSympyStr = qSym
            )
            "v0" -> GeneratedExercise(
                seed = seed,
                chapter = "sequences",
                technique = "seq_arith_geo",
                difficulty = diff,
                answerKind = "constant",
                prompt = LocalizedText(
                    "احسب الحد الأول \$v_0\$ للمتتالية المساعدة \$(v_n)\$:",
                    "Calculer le premier terme \$v_0\$ de la suite auxiliaire \$(v_n)\$ :"
                ),
                fullLimitLatex = sysTex,
                hints = listOf(
                    LocalizedText(
                        "عوّض \$n = 0\$ في العلاقة \$v_n = $vnDef\$.",
                        "Remplacez \$n = 0\$ dans \$v_n = $vnDef\$."
                    )
                ),
                answerLatex = "$v0",
                expectedAnswer = ExpectedAnswer.Finite(v0.toDouble()),
                steps = listOf(
                    SolutionStep(
                        title = LocalizedText("التعويض المباشر", "Calcul direct pour \$n = 0\$"),
                        explanation = LocalizedText("بما أن \$u_0 = $u0\$:", "Comme \$u_0 = $u0\$ :"),
                        latex = "v_0 = $u0 - ($alpha) = $v0"
                    )
                ),
                answerSympyStr = "$v0"
            )
            "general_un" -> {
                val powTex = fmtPowBaseN(qp, qq)
                val powSym = fmtPowBaseNSympy(qp, qq)
                val v0Str = if (v0 > 0) " + $v0\\cdot $powTex" else " - ${-v0}\\cdot $powTex"
                val ansTex = "$alpha$v0Str"
                val ansSym = "$alpha + ($v0)*($powSym)"
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_arith_geo",
                    difficulty = diff,
                    answerKind = "expression_n",
                    prompt = LocalizedText(
                        "علماً أن \$(v_n)\$ هندسية، عبّر عن \$u_n\$ بدلالة \$n\$:",
                        "Sachant que \$(v_n)\$ est géométrique, exprimer \$u_n\$ en fonction de \$n\$ :"
                    ),
                    fullLimitLatex = sysTex,
                    hints = listOf(
                        LocalizedText(
                            "أوجد الأساس \$q = $qTex\$ و \$v_0 = $v0\$ ثم اكتب \$u_n = $alpha + v_0 q^n\$.",
                            "Trouvez \$q = $qTex\$ et \$v_0 = $v0\$, puis écrivez \$u_n = $alpha + v_0 q^n\$."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = ExpectedAnswer.Finite(0.0),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("تعبير \$v_n\$ ثم \$u_n\$", "Expression de \$v_n\$ puis de \$u_n\$"),
                            explanation = LocalizedText(
                                "لدينا \$v_n = $v0 \\cdot $powTex\$ ومنه \$u_n = $alpha + v_n\$:",
                                "On a \$v_n = $v0 \\cdot $powTex\$ d'où \$u_n = $alpha + v_n\$ :"
                            ),
                            latex = "u_n = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            }
            "limit_un" -> {
                val conv = abs(qp) < abs(qq)
                val (ansTex, expAns, ansSym) = if (conv) {
                    Triple("$alpha", ExpectedAnswer.Finite(alpha.toDouble()), "$alpha")
                } else {
                    if (v0 > 0) Triple("+\\infty", ExpectedAnswer.PosInf, "oo")
                    else Triple("-\\infty", ExpectedAnswer.NegInf, "-oo")
                }
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_arith_geo",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = LocalizedText(
                        "استنتج نهاية المتتالية \$(u_n)\$ عندما يؤول \$n\$ إلى \$+\\infty\$:",
                        "En déduire \$\\lim_{n \\to +\\infty} u_n\$ :"
                    ),
                    fullLimitLatex = sysTex,
                    hints = listOf(
                        LocalizedText(
                            "عبّر عن \$u_n = $alpha + v_0 q^n\$ وقارن \$q\$ بـ \$1\$ (انتبه لإشارة \$v_0\$).",
                            "Exprimez \$u_n = $alpha + v_0 q^n\$ et comparez \$q\$ à \$1\$."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = expAns,
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("حساب النهاية", "Calcul de la limite"),
                            explanation = LocalizedText(
                                "لدينا \$u_n = $alpha + $v0 \\cdot ${fmtPowBaseN(qp, qq)}\$ ومنه:",
                                "On a \$u_n = $alpha + $v0 \\cdot ${fmtPowBaseN(qp, qq)}\$, d'où :"
                            ),
                            latex = "\\lim_{n \\to +\\infty} u_n = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            }
            else -> { // sum_vn
                val coeffNum = v0.toLong() * qq
                val coeffDen = qq - qp
                val cTex = fmtRatLatex(coeffNum, coeffDen)
                val cSym = fmtRatSympy(coeffNum, coeffDen)
                val pow1Tex = fmtPowBaseN(qp, qq, 1)
                val pow1Sym = fmtPowBaseNSympy(qp, qq, 1)
                val ansTex = "$cTex\\left(1 - $pow1Tex\\right)"
                val ansSym = "($cSym)*(1 - ($pow1Sym))"
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_arith_geo",
                    difficulty = diff,
                    answerKind = "expression_n",
                    prompt = LocalizedText(
                        "احسب المجموع \$S_n = v_0 + v_1 + \\dots + v_n\$ بدلالة \$n\$:",
                        "Calculer la somme \$S_n = v_0 + v_1 + \\dots + v_n\$ en fonction de \$n\$ :"
                    ),
                    fullLimitLatex = sysTex,
                    hints = listOf(
                        LocalizedText(
                            "استعمل \$S_n = v_0 \\cdot \\frac{1 - q^{n+1}}{1 - q}\$ (انتبه: عدد الحدود هو \$n+1\$).",
                            "Utilisez \$S_n = v_0 \\cdot \\frac{1 - q^{n+1}}{1 - q}\$ (attention aux \$n+1\$ termes)."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = ExpectedAnswer.Finite(0.0),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("مجموع حدود متتالية هندسية", "Somme géométrique"),
                            explanation = LocalizedText(
                                "نعوّض \$v_0 = $v0\$ و \$q = $qTex\$ في صيغة المجموع:",
                                "On remplace \$v_0 = $v0\$ et \$q = $qTex\$ :"
                            ),
                            latex = "S_n = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 5. SEQ_FIXED_POINT (seq_fixed_point)
    // -----------------------------------------------------------------------

    fun genSeqFixedPoint(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        return when (diff) {
            1 -> {
                val lRoot = rng.pick(listOf(2, 3, 4, 5, 6))
                val negMag = rng.range(1, lRoot - 1)
                val a = lRoot - negMag
                val b = lRoot * negMag
                val inner = if (a == 1) "u_n + $b" else "${a}u_n + $b"
                val sysTex = "u_{n+1} = \\sqrt{$inner}, \\quad \\forall n \\in \\mathbb{N}:\\; u_n \\in [0, +\\infty["
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_fixed_point",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = LocalizedText(
                        "نفترض أن المتتالية \$(u_n)\$ متقاربة. حدّد نهايتها \$l\$:",
                        "On admet que \$(u_n)\$ est convergente. Déterminer sa limite \$l\$ :"
                    ),
                    fullLimitLatex = sysTex,
                    hints = listOf(
                        LocalizedText(
                            "حلّ المعادلة \$f(l) = l\$ ثم احتفظ بالحل المنتمي إلى \$[0, +\\infty[\$.",
                            "Résolvez \$f(l) = l\$ puis gardez la solution dans \$[0, +\\infty[\$."
                        )
                    ),
                    answerLatex = "$lRoot",
                    expectedAnswer = ExpectedAnswer.Finite(lRoot.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("حل المعادلة \$f(l) = l\$", "Résolution de \$f(l) = l\$"),
                            explanation = LocalizedText(
                                "نحل \$l^2 - ${a}l - $b = 0 \\iff (l - $lRoot)(l + $negMag) = 0\$ مع \$l \\ge 0\$:",
                                "On résout \$l^2 - ${a}l - $b = 0 \\iff (l - $lRoot)(l + $negMag) = 0\$ avec \$l \\ge 0\$ :"
                            ),
                            latex = "\\lim_{n \\to +\\infty} u_n = $lRoot"
                        )
                    ),
                    answerSympyStr = "$lRoot"
                )
            }
            2 -> {
                val r1 = rng.pick(listOf(-3, -2, -1, 0, 1))
                val r2 = r1 + rng.pick(listOf(2, 3, 4))
                val keepUpper = rng.nextInt(2) == 0
                val expected = if (keepUpper) r2 else r1
                val d = rng.pick(listOf(2, 3, 4))
                val a = r1 + r2 + d
                val b = -r1 * r2
                val numTex = fmtLinearExpr(a, b, "u_n")
                val denTex = "u_n + $d"
                val intervalTex = if (keepUpper) "[${r1 + 1}, +\\infty[" else "[${r1 - 2}, ${r2 - 1}]"
                val sysTex = "u_{n+1} = \\frac{$numTex}{$denTex}, \\quad \\forall n \\in \\mathbb{N}:\\; u_n \\in $intervalTex"
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_fixed_point",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = LocalizedText(
                        "نفترض أن المتتالية \$(u_n)\$ متقاربة. حدّد نهايتها \$l\$ بالانتباه للمجال:",
                        "On admet que \$(u_n)\$ est convergente. Déterminer sa limite \$l\$ en tenant compte de l'intervalle :"
                    ),
                    fullLimitLatex = sysTex,
                    hints = listOf(
                        LocalizedText(
                            "حلّ \$f(l) = l\$ واختر الجذر الوحيد المنتمي إلى \$$intervalTex\$.",
                            "Résolvez \$f(l) = l\$ et retenez la racine appartenant à \$$intervalTex\$."
                        )
                    ),
                    answerLatex = "$expected",
                    expectedAnswer = ExpectedAnswer.Finite(expected.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("اختيار الحل وفق المجال", "Sélection grâce à l'intervalle"),
                            explanation = LocalizedText(
                                "الجذور هي \$\\{$r1, $r2\\}\$، والحل الوحيد داخل \$$intervalTex\$ هو:",
                                "Les racines sont \$\\{$r1, $r2\\}\$ ; la seule dans \$$intervalTex\$ est :"
                            ),
                            latex = "\\lim_{n \\to +\\infty} u_n = $expected"
                        )
                    ),
                    answerSympyStr = "$expected"
                )
            }
            else -> {
                val alpha = rng.range(-3, 5)
                val b = rng.pick(listOf(2, 3))
                val targetN0 = if (b == 2) rng.range(3, 6) else rng.range(2, 4)
                var powPrev = 1
                repeat(targetN0 - 1) { powPrev *= b }
                val powCurr = powPrev * b
                val cm = rng.range(powPrev, powCurr - 1)
                val c = 1
                val M = cm
                val unTex = if (alpha == 0) "\\left(\\frac{1}{$b}\\right)^n" else "$alpha + \\left(\\frac{1}{$b}\\right)^n"
                val absPart = if (alpha < 0) "\\left|u_n - ($alpha)\\right|" else "|u_n - $alpha|"
                val epsTex = if (M > 1) "\\frac{1}{$M}" else "1"
                val sysTex = "u_n = $unTex, \\qquad $absPart < $epsTex"
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_fixed_point",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = LocalizedText(
                        "حدّد أصغر عدد صحيح طبيعي \$n_0\$ بحيث تتحقق المتراجحة لكل \$n \\ge n_0\$:",
                        "Déterminer le plus petit entier naturel \$n_0\$ tel que l'inégalité soit vérifiée pour tout \$n \\ge n_0\$ :"
                    ),
                    fullLimitLatex = sysTex,
                    hints = listOf(
                        LocalizedText(
                            "حوّل المتراجحة إلى الشكل \$$b^n > $M\$ ثم أوجد أصغر قوة لـ \$$b\$ أكبر قطعا من \$$M\$.",
                            "Transformez l'inéquation en \$$b^n > $M\$ puis trouvez le plus petit \$n_0\$."
                        )
                    ),
                    answerLatex = "$targetN0",
                    expectedAnswer = ExpectedAnswer.Finite(targetN0.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("تحديد الرتبة \$n_0\$", "Détermination du rang seuil \$n_0\$"),
                            explanation = LocalizedText(
                                "بما أن \$$b^{${targetN0 - 1}} = $powPrev \\le $M < $powCurr = $b^{$targetN0}\$:",
                                "Comme \$$b^{${targetN0 - 1}} = $powPrev \\le $M < $powCurr = $b^{$targetN0}\$ :"
                            ),
                            latex = "n \\ge $targetN0 \\implies n_0 = $targetN0"
                        )
                    ),
                    answerSympyStr = "$targetN0"
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 6. SEQ_HOMOGRAPHIC (seq_homographic)
    // -----------------------------------------------------------------------

    fun genSeqHomographic(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        val useGeo = rng.nextInt(10) < 6
        if (!useGeo) {
            val alpha = rng.pick(listOf(-2, -1, 1, 2, 3))
            val (pr, qr) = rng.pick(listOf(1L to 1L, 1L to 2L, 1L to 3L, 2L to 1L, 2L to 3L, 3L to 2L))
            val aRaw = qr + pr * alpha
            val bRaw = -pr * alpha * alpha
            val cRaw = pr
            val dRaw = qr - pr * alpha
            val g = gcd(gcd(aRaw, bRaw), gcd(cRaw, dRaw))
            var a = (aRaw / g).toInt()
            var b = (bRaw / g).toInt()
            var c = (cRaw / g).toInt()
            var d = (dRaw / g).toInt()
            if (c < 0) { a = -a; b = -b; c = -c; d = -d }
            val u0 = alpha + 1
            val vnDen = when {
                alpha == 0 -> "u_n"
                alpha > 0 -> "u_n - $alpha"
                else -> "u_n + ${-alpha}"
            }
            val sysTex = "\\begin{cases} u_0 = $u0 \\\\ u_{n+1} = \\frac{${fmtLinearExpr(a, b, "u_n")}}{${fmtLinearExpr(c, d, "u_n")}} \\end{cases}, \\qquad v_n = \\frac{1}{$vnDen}"
            val rTex = fmtRatLatex(pr, qr)
            val rSym = fmtRatSympy(pr, qr)
            if (diff == 1) {
                return GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_homographic",
                    difficulty = diff,
                    answerKind = "constant",
                    prompt = LocalizedText(
                        "المتتالية \$(v_n)\$ حسابية. احسب أساسها \$r\$:",
                        "La suite \$(v_n)\$ est arithmétique. Calculer sa raison \$r\$ :"
                    ),
                    fullLimitLatex = sysTex,
                    hints = listOf(
                        LocalizedText(
                            "احسب \$v_{n+1} - v_n\$ بتعويض \$u_{n+1}\$ وتبسيط المقلوب.",
                            "Calculez \$v_{n+1} - v_n\$ en substituant \$u_{n+1}\$."
                        )
                    ),
                    answerLatex = rTex,
                    expectedAnswer = ExpectedAnswer.Finite(pr.toDouble() / qr.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("حساب الأساس \$r\$", "Calcul de la raison \$r\$"),
                            explanation = LocalizedText("نجد بعد التبسيط:", "Après simplification :"),
                            latex = "v_{n+1} = v_n + $rTex \\implies r = $rTex"
                        )
                    ),
                    answerSympyStr = rSym
                )
            }
            val vnTex = if (pr == qr) "1 + n" else "1 + $rTex n"
            val ansTex = "$alpha + \\frac{1}{$vnTex}"
            val ansSym = "$alpha + 1/(1 + ($rSym)*n)"
            return GeneratedExercise(
                seed = seed,
                chapter = "sequences",
                technique = "seq_homographic",
                difficulty = diff,
                answerKind = "expression_n",
                prompt = LocalizedText(
                    "علماً أن \$(v_n)\$ حسابية، عبّر عن \$u_n\$ بدلالة \$n\$:",
                    "Sachant que \$(v_n)\$ est arithmétique, exprimer \$u_n\$ en fonction de \$n\$ :"
                ),
                fullLimitLatex = sysTex,
                hints = listOf(
                    LocalizedText(
                        "أوجد \$v_n = 1 + nr\$ ثم استنتج \$u_n = $alpha + \\frac{1}{v_n}\$.",
                        "Écrivez \$v_n = 1 + nr\$ puis déduisez \$u_n = $alpha + \\frac{1}{v_n}\$."
                    )
                ),
                answerLatex = ansTex,
                expectedAnswer = ExpectedAnswer.Finite(0.0),
                steps = listOf(
                    SolutionStep(
                        title = LocalizedText("تعبير \$u_n\$ بدلالة \$n\$", "Expression de \$u_n\$"),
                        explanation = LocalizedText(
                            "لدينا \$v_n = $vnTex\$ ومنه \$u_n = $alpha + \\frac{1}{v_n}\$:",
                            "On a \$v_n = $vnTex\$ d'où :"
                        ),
                        latex = "u_n = $ansTex"
                    )
                ),
                answerSympyStr = ansSym
            )
        }

        // Geometric auxiliary homographic
        val alpha = rng.pick(listOf(-2, -1, 1, 2, 3))
        val betaCandidates = listOf(-3, -2, -1, 0, 1, 2, 3, 4).filter { it != alpha }
        val beta = rng.pick(betaCandidates)
        val (pq, qq) = rng.pick(listOf(1L to 2L, 1L to 3L, 2L to 3L, 2L to 5L, 3L to 4L, 1L to 4L))
        val aRaw = qq * alpha - pq * beta
        val bRaw = alpha.toLong() * beta.toLong() * (pq - qq)
        val cRaw = qq - pq
        val dRaw = pq * alpha - qq * beta
        val g = gcd(gcd(aRaw, bRaw), gcd(cRaw, dRaw))
        var a = (aRaw / g).toInt()
        var b = (bRaw / g).toInt()
        var c = (cRaw / g).toInt()
        var d = (dRaw / g).toInt()
        if (c < 0) { a = -a; b = -b; c = -c; d = -d }

        val lo = minOf(alpha, beta)
        val hi = maxOf(alpha, beta)
        val interior = (lo + 1 until hi).toList()
        val (u0Num, u0Den) = if (interior.isNotEmpty()) rng.pick(interior).toLong() to 1L else (alpha + beta).toLong() to 2L
        // v0 = (u0 - alpha) / (u0 - beta)
        val v0Num = u0Num - alpha * u0Den
        val v0Den = u0Num - beta * u0Den
        val v0G = gcd(v0Num, v0Den)
        val v0Sign = if ((v0Num < 0) xor (v0Den < 0)) -1L else 1L
        val v0n = v0Sign * (abs(v0Num) / v0G)
        val v0d = abs(v0Den) / v0G

        val vnNum = when { alpha == 0 -> "u_n"; alpha > 0 -> "u_n - $alpha"; else -> "u_n + ${-alpha}" }
        val vnDen = when { beta == 0 -> "u_n"; beta > 0 -> "u_n - $beta"; else -> "u_n + ${-beta}" }
        val sysTex = "\\begin{cases} u_0 = ${fmtRatLatex(u0Num, u0Den)} \\\\ u_{n+1} = \\frac{${fmtLinearExpr(a, b, "u_n")}}{${fmtLinearExpr(c, d, "u_n")}} \\end{cases}, \\qquad v_n = \\frac{$vnNum}{$vnDen}"
        val qTex = fmtRatLatex(pq, qq)
        val qSym = fmtRatSympy(pq, qq)

        return when (diff) {
            1 -> GeneratedExercise(
                seed = seed,
                chapter = "sequences",
                technique = "seq_homographic",
                difficulty = diff,
                answerKind = "constant",
                prompt = LocalizedText(
                    "المتتالية \$(v_n)\$ هندسية. احسب أساسها \$q\$:",
                    "La suite \$(v_n)\$ est géométrique. Déterminer sa raison \$q\$ :"
                ),
                fullLimitLatex = sysTex,
                hints = listOf(
                    LocalizedText(
                        "احسب \$v_{n+1} = \\frac{u_{n+1} - ($alpha)}{u_{n+1} - ($beta)}\$ بتوحيد المقامين والاختزال.",
                        "Calculez \$v_{n+1} = \\frac{u_{n+1} - ($alpha)}{u_{n+1} - ($beta)}\$ en simplifiant."
                    )
                ),
                answerLatex = qTex,
                expectedAnswer = ExpectedAnswer.Finite(pq.toDouble() / qq.toDouble()),
                steps = listOf(
                    SolutionStep(
                        title = LocalizedText("تعبير \$v_{n+1}\$ بدلالة \$v_n\$", "Calcul de \$v_{n+1}\$"),
                        explanation = LocalizedText("بعد تعويض \$u_{n+1}\$ نجد:", "En substituant \$u_{n+1}\$, on obtient :"),
                        latex = "v_{n+1} = $qTex v_n \\implies q = $qTex"
                    )
                ),
                answerSympyStr = qSym
            )
            2 -> {
                val v0Tex = fmtRatLatex(v0n, v0d)
                val v0Sym = fmtRatSympy(v0n, v0d)
                val powTex = fmtPowBaseN(pq, qq)
                val powSym = fmtPowBaseNSympy(pq, qq)
                val vnExprTex = "$v0Tex \\cdot $powTex"
                val ansTex = "\\frac{$alpha - ($beta)\\left($vnExprTex\\right)}{1 - \\left($vnExprTex\\right)}"
                val ansSym = "(($alpha) - ($beta)*(($v0Sym)*($powSym)))/(1 - ($v0Sym)*($powSym))"
                GeneratedExercise(
                    seed = seed,
                    chapter = "sequences",
                    technique = "seq_homographic",
                    difficulty = diff,
                    answerKind = "expression_n",
                    prompt = LocalizedText(
                        "علماً أن \$(v_n)\$ هندسية، عبّر عن \$u_n\$ بدلالة \$n\$:",
                        "Sachant que \$(v_n)\$ est géométrique, exprimer \$u_n\$ en fonction de \$n\$ :"
                    ),
                    fullLimitLatex = sysTex,
                    hints = listOf(
                        LocalizedText(
                            "أوجد \$v_n = v_0 q^n\$ ثم اعزل \$u_n = \\frac{\\alpha - \\beta v_n}{1 - v_n}\$.",
                            "Exprimez \$v_n = v_0 q^n\$ puis isolez \$u_n = \\frac{\\alpha - \\beta v_n}{1 - v_n}\$."
                        )
                    ),
                    answerLatex = ansTex,
                    expectedAnswer = ExpectedAnswer.Finite(0.0),
                    steps = listOf(
                        SolutionStep(
                            title = LocalizedText("عزل \$u_n\$ بدلالة \$v_n\$ ثم \$n\$", "Isolement de \$u_n\$"),
                            explanation = LocalizedText(
                                "لدينا \$v_n = $vnExprTex\$ ومنه \$u_n = \\frac{$alpha - ($beta)v_n}{1 - v_n}\$:",
                                "On a \$v_n = $vnExprTex\$ et \$u_n = \\frac{$alpha - ($beta)v_n}{1 - v_n}\$ :"
                            ),
                            latex = "u_n = $ansTex"
                        )
                    ),
                    answerSympyStr = ansSym
                )
            }
            else -> GeneratedExercise(
                seed = seed,
                chapter = "sequences",
                technique = "seq_homographic",
                difficulty = diff,
                answerKind = "constant",
                prompt = LocalizedText(
                    "باستعمال \$(v_n)\$، احسب النهاية \$\\lim_{n \\to +\\infty} u_n\$:",
                    "À l'aide de \$(v_n)\$, calculer \$\\lim_{n \\to +\\infty} u_n\$ :"
                ),
                fullLimitLatex = sysTex,
                hints = listOf(
                    LocalizedText(
                        "أوجد الأساس \$q\$؛ بما أن \$|q| < 1\$ فإن \$v_n \\to 0\$ ثم استنتج نهاية \$u_n\$.",
                        "Comme \$|q| < 1\$, on a \$v_n \\to 0\$ puis déduisez la limite de \$u_n\$."
                    )
                ),
                answerLatex = "$alpha",
                expectedAnswer = ExpectedAnswer.Finite(alpha.toDouble()),
                steps = listOf(
                    SolutionStep(
                        title = LocalizedText("استنتاج نهاية \$(u_n)\$", "Limite de \$(u_n)\$"),
                        explanation = LocalizedText(
                            "بما أن \$q = $qTex \\in ]0, 1[\$ فإن \$v_n \\to 0\$، ومنه:",
                            "Comme \$q = $qTex \\in ]0, 1[\$, \$v_n \\to 0\$, d'où :"
                        ),
                        latex = "u_n = \\frac{$alpha - ($beta)v_n}{1 - v_n} \\xrightarrow[n \\to +\\infty]{} $alpha"
                    )
                ),
                answerSympyStr = "$alpha"
            )
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
