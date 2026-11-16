package com.ayoub.baclimits.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

data class LocalizedText(val ar: String, val fr: String)

data class SolutionStep(
    val title: LocalizedText,
    val explanation: LocalizedText,
    val latex: String?
)

data class GeneratedExercise(
    val seed: String,
    val technique: String,
    val difficulty: Int,
    val fullLimitLatex: String,
    val hints: List<LocalizedText>,
    val answerLatex: String,
    val expectedAnswer: ExpectedAnswer,
    val steps: List<SolutionStep>
)

private class SplitMix64(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound.toLong()).toInt()
    }

    fun range(min: Int, max: Int): Int = min + nextInt(max - min + 1)

    fun pickNonZero(min: Int, max: Int): Int {
        var v = range(min, max)
        while (v == 0) v = range(min, max)
        return v
    }

    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]
}

private data class Rational(val num: Long, val den: Long) {
    val reduced: Rational by lazy {
        if (den == 0L) throw ArithmeticException("Zero denominator")
        if (num == 0L) Rational(0L, 1L)
        else {
            val g = gcd(abs(num), abs(den))
            val sign = if (den < 0) -1L else 1L
            Rational(sign * (num / g), sign * (den / g))
        }
    }

    operator fun plus(other: Rational): Rational =
        Rational(num * other.den + other.num * den, den * other.den).reduced

    operator fun minus(other: Rational): Rational =
        Rational(num * other.den - other.num * den, den * other.den).reduced

    operator fun times(other: Rational): Rational =
        Rational(num * other.num, den * other.den).reduced

    operator fun div(other: Rational): Rational =
        Rational(num * other.den, den * other.num).reduced

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    fun toLatex(): String {
        val r = reduced
        return when {
            r.num == 0L -> "0"
            r.den == 1L -> "${r.num}"
            r.num < 0 -> "-\\frac{${-r.num}}{${r.den}}"
            else -> "\\frac{${r.num}}{${r.den}}"
        }
    }

    companion object {
        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
        fun of(n: Int, d: Int = 1): Rational = Rational(n.toLong(), d.toLong()).reduced
        fun of(n: Long, d: Long = 1L): Rational = Rational(n, d).reduced
    }
}

object LimitsCasEngine {

    val CORE_TECHNIQUES = listOf(
        "poly_factor",
        "sqrt_conjugate",
        "nth_root",
        "add_sub_zero",
        "trig",
        "hybrid"
    )

    private val TECH_TO_CODE = mapOf(
        "poly_factor" to "POLY",
        "sqrt_conjugate" to "SQRT",
        "nth_root" to "NROT",
        "add_sub_zero" to "ADD0",
        "trig" to "TRIG",
        "hybrid" to "HYBR"
    )

    private val CODE_TO_TECH = TECH_TO_CODE.entries.associate { (k, v) -> v to k }

    private val SEED_REGEX = Regex("^2BAC-([A-Z0-9]{4})-L([123])-([0-9A-F]{8})$")

    fun isValidSeed(seedRaw: String): Boolean {
        val clean = seedRaw.trim().uppercase()
        val m = SEED_REGEX.matchEntire(clean) ?: return false
        return CODE_TO_TECH.containsKey(m.groupValues[1])
    }

    fun parseSeed(seedRaw: String): Triple<String, Int, Long> {
        val clean = seedRaw.trim().uppercase()
        val m = SEED_REGEX.matchEntire(clean)
            ?: throw IllegalArgumentException("Invalid seed format (expected 2BAC-TECH-L1-XXXXXXXX)")
        val tech = CODE_TO_TECH[m.groupValues[1]]
            ?: throw IllegalArgumentException("Unknown technique code: ${m.groupValues[1]}")
        val diff = m.groupValues[2].toInt()
        val hexVal = m.groupValues[3].toLong(16)
        return Triple(tech, diff, hexVal)
    }

    fun generate(
        requestedTechnique: String,
        requestedDifficulty: Int,
        explicitSeed: String? = null,
        weakestTechnique: String? = null,
        suggestedDifficulty: Int = 2
    ): GeneratedExercise {
        val (tech, diff, seedVal) = if (!explicitSeed.isNullOrBlank()) {
            parseSeed(explicitSeed)
        } else {
            val chosenTech = when {
                requestedTechnique in CORE_TECHNIQUES -> requestedTechnique
                weakestTechnique in CORE_TECHNIQUES -> weakestTechnique!!
                else -> CORE_TECHNIQUES[Random.nextInt(CORE_TECHNIQUES.size)]
            }
            val chosenDiff = if (requestedDifficulty in 1..3) {
                requestedDifficulty
            } else {
                suggestedDifficulty.coerceIn(1, 3)
            }
            val randHex = Random.nextLong() and 0xFFFFFFFFL
            Triple(chosenTech, chosenDiff, randHex)
        }

        val code = TECH_TO_CODE.getValue(tech)
        val formattedSeed = "2BAC-$code-L$diff-${seedVal.toString(16).uppercase().padStart(8, '0')}"
        val mixSeed = (code.hashCode().toLong() shl 32) xor (diff.toLong() shl 48) xor seedVal
        val rng = SplitMix64(mixSeed)

        return when (tech) {
            "poly_factor" -> genPolyFactor(formattedSeed, diff, rng)
            "sqrt_conjugate" -> genSqrtConjugate(formattedSeed, diff, rng)
            "nth_root" -> genNthRoot(formattedSeed, diff, rng)
            "add_sub_zero" -> genAddSubZero(formattedSeed, diff, rng)
            "trig" -> genTrig(formattedSeed, diff, rng)
            "hybrid" -> genHybrid(formattedSeed, diff, rng)
            else -> genPolyFactor(formattedSeed, diff, rng)
        }
    }

    // ---------- LaTeX Formatting Helpers ----------

    private fun fmtPoly(vararg coeffsDesc: Int, varName: String = "x"): String {
        val deg = coeffsDesc.size - 1
        val sb = StringBuilder()
        for ((idx, c) in coeffsDesc.withIndex()) {
            if (c == 0) continue
            val p = deg - idx
            val absC = abs(c)
            if (sb.isEmpty()) {
                if (c < 0) sb.append("-")
            } else {
                sb.append(if (c < 0) " - " else " + ")
            }
            when (p) {
                0 -> sb.append(absC)
                1 -> {
                    if (absC != 1) sb.append(absC)
                    sb.append(varName)
                }
                else -> {
                    if (absC != 1) sb.append(absC)
                    sb.append(varName).append("^").append(p)
                }
            }
        }
        return if (sb.isEmpty()) "0" else sb.toString()
    }

    private fun fmtLinear(m: Int, p: Int, varName: String = "x"): String = fmtPoly(m, p, varName = varName)

    private fun fmtFactor(a: Int, varName: String = "x"): String {
        return when {
            a == 0 -> varName
            a > 0 -> "($varName - $a)"
            else -> "($varName + ${-a})"
        }
    }

    private fun fmtScaledFactor(k: Int, a: Int): String {
        return when (k) {
            1 -> fmtPoly(1, -a)
            -1 -> fmtPoly(-1, a)
            else -> "$k${fmtFactor(a)}"
        }
    }

    private fun fmtSignedTerm(coeff: Int, expr: String, first: Boolean = false): String {
        if (coeff == 0) return ""
        val sign = if (first) {
            if (coeff < 0) "-" else ""
        } else {
            if (coeff < 0) " - " else " + "
        }
        val mag = if (abs(coeff) == 1) "" else "${abs(coeff)}"
        return "$sign$mag$expr"
    }

    private fun limPoint(target: String, exprLatex: String): String =
        "\\lim_{x \\to $target} $exprLatex"

    // ---------- 1. POLY FACTOR ----------

    private fun genPolyFactor(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        return when (diff) {
            1 -> if (rng.nextInt(2) == 0) {
                val a = rng.range(-5, 5)
                val p = rng.pick(listOf(1, 2, 3, -1, -2))
                var b = rng.range(-7, 7)
                while (p * a + b == 0) b = rng.range(-7, 7)
                val k = rng.pick(listOf(1, 2, 3, -1, -2))

                val c2 = p
                val c1 = b - p * a
                val c0 = -a * b
                val num = fmtPoly(c2, c1, c0)
                val den = fmtPoly(k, -k * a)
                val ans = Rational.of(p * a + b, k)
                val expr = "\\frac{$num}{$den}"
                val linFactor = fmtPoly(p, b)

                GeneratedExercise(
                    seed = seed,
                    technique = "poly_factor",
                    difficulty = 1,
                    fullLimitLatex = limPoint("$a", expr),
                    hints = listOf(
                        LocalizedText(
                            "التعويض المباشر بـ \$x = $a\$ يعطي شكلاً غير محدد من نوع \$\\frac{0}{0}\$.",
                            "La substitution directe en \$x = $a\$ donne une forme indéterminée \$\\frac{0}{0}\$."
                        ),
                        LocalizedText(
                            "بما أن \$$a\$ جذر للبسط والمقام، قم بتعميل البسط بالحد \$${fmtFactor(a)}\$.",
                            "Comme \$$a\$ est racine du numérateur et du dénominateur, factorise par \$${fmtFactor(a)}\$."
                        ),
                        LocalizedText(
                            "اختزل بـ \$${fmtFactor(a)}\$ ثم احسب النهاية عندما يؤول \$x\$ إلى \$$a\$.",
                            "Simplifie par \$${fmtFactor(a)}\$ puis calcule la limite en \$x = $a\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تحديد الشكل غير المحدد", "Forme indéterminée"),
                            LocalizedText(
                                "عند تعويض \$x = $a\$ نجد \$\\frac{0}{0}\$ (شكل غير محدد).",
                                "En remplaçant \$x = $a\$, on obtient la forme indéterminée \$\\frac{0}{0}\$."
                            ),
                            null
                        ),
                        SolutionStep(
                            LocalizedText("التعميل والاختزال", "Factorisation et simplification"),
                            LocalizedText(
                                "نعمّل البسط والمقام بالحد \$${fmtFactor(a)}\$ ثم نختزل:",
                                "On factorise numérateur et dénominateur par \$${fmtFactor(a)}\$ puis on simplifie :"
                            ),
                            "\\frac{$num}{$den} = \\frac{${fmtFactor(a)}($linFactor)}{${fmtScaledFactor(k, a)}} = \\frac{$linFactor}{$k}"
                        ),
                        SolutionStep(
                            LocalizedText("حساب النهاية", "Calcul final"),
                            LocalizedText("نعوض \$x = $a\$ في التعبير المبسط:", "On substitue \$x = $a\$ :"),
                            "\\lim_{x \\to $a} \\frac{$linFactor}{$k} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val infStr = if (rng.nextInt(2) == 0) "+\\infty" else "-\\infty"
                val a2 = rng.pickNonZero(-6, 6)
                val a1 = rng.range(-7, 7)
                val a0 = rng.range(-8, 8)
                val b2 = rng.pickNonZero(-5, 5)
                val b1 = rng.range(-7, 7)
                val b0 = rng.range(-8, 8)
                val num = fmtPoly(a2, a1, a0)
                val den = fmtPoly(b2, b1, b0)
                val ans = Rational.of(a2, b2)

                GeneratedExercise(
                    seed = seed,
                    technique = "poly_factor",
                    difficulty = 1,
                    fullLimitLatex = limPoint(infStr, "\\frac{$num}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "نهاية دالة جذرية (خارج حدوديتين) بجوار \$$infStr\$ تعطي شكلاً غير محدد \$\\frac{\\infty}{\\infty}\$.",
                            "En \$$infStr\$, on obtient une forme indéterminée \$\\frac{\\infty}{\\infty}\$."
                        ),
                        LocalizedText(
                            "خذ الحد الأكبر درجة في البسط والحد الأكبر درجة في المقام.",
                            "Conserve uniquement les monômes de plus haut degré du numérateur et du dénominateur."
                        ),
                        LocalizedText(
                            "اختزل بـ \$x^2\$ لتحصل على النسبة بين المعاملين الرئيسيين.",
                            "Simplifie par \$x^2\$ pour obtenir le quotient des coefficients dominants."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("قاعدة الحدودية عند اللانهاية", "Monômes de plus haut degré"),
                            LocalizedText(
                                "عند \$$infStr\$ نأخذ خارج الحدين الأعلى درجة:",
                                "À l'infini, la limite d'une fraction rationnelle est celle du quotient des termes de plus haut degré :"
                            ),
                            "\\lim_{x \\to $infStr} \\frac{$num}{$den} = \\lim_{x \\to $infStr} \\frac{${fmtPoly(a2, 0, 0)}}{${fmtPoly(b2, 0, 0)}} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            2 -> if (rng.nextInt(2) == 0) {
                val a = rng.pickNonZero(-3, 3)
                val p = rng.pick(listOf(1, 2, -1))
                val q = rng.range(-5, 5)
                var r = rng.range(-6, 6)
                while (p * a * a + q * a + r == 0) r = rng.range(-6, 6)
                val u = rng.pick(listOf(1, 2, -1))
                var v = rng.range(-5, 5)
                while (u * a + v == 0) v = rng.range(-5, 5)

                val n3 = p
                val n2 = q - p * a
                val n1 = r - q * a
                val n0 = -r * a
                val d2 = u
                val d1 = v - u * a
                val d0 = -v * a
                val num = fmtPoly(n3, n2, n1, n0)
                val den = fmtPoly(d2, d1, d0)
                val qPoly = fmtPoly(p, q, r)
                val dPoly = fmtPoly(u, v)
                val ans = Rational.of(p * a * a + q * a + r, u * a + v)

                GeneratedExercise(
                    seed = seed,
                    technique = "poly_factor",
                    difficulty = 2,
                    fullLimitLatex = limPoint("$a", "\\frac{$num}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "التعويض بـ \$x = $a\$ يعطي \$\\frac{0}{0}\$ لأن \$$a\$ جذر مشترك للحدوديتين.",
                            "En \$x = $a\$, on obtient \$\\frac{0}{0}\$ car \$$a\$ annule les deux polynômes."
                        ),
                        LocalizedText(
                            "استعمل القسمة الإقليدية أو جدول هورنر (Horner) لتعميل البسط من الدرجة 3 بـ \$${fmtFactor(a)}\$.",
                            "Utilise la division euclidienne ou le schéma de Horner pour factoriser le degré 3 par \$${fmtFactor(a)}\$."
                        ),
                        LocalizedText(
                            "بعد الاختزال تصبح الدالة \$\\frac{$qPoly}{$dPoly}\$؛ عوّض بـ \$x = $a\$.",
                            "Après simplification par \$${fmtFactor(a)}\$, évalue \$\\frac{$qPoly}{$dPoly}\$ en \$x = $a\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تعميل البسط والمقام (القسمة الإقليدية)", "Factorisation par division euclidienne"),
                            LocalizedText(
                                "بما أن \$x = $a\$ جذر، نقسم البسط والمقام على \$${fmtFactor(a)}\$:",
                                "En divisant numérateur et dénominateur par \$${fmtFactor(a)}\$ :"
                            ),
                            "\\frac{$num}{$den} = \\frac{${fmtFactor(a)}($qPoly)}{${fmtFactor(a)}($dPoly)} = \\frac{$qPoly}{$dPoly}"
                        ),
                        SolutionStep(
                            LocalizedText("التعويض النهائي", "Calcul de la limite"),
                            LocalizedText("نعوض \$x = $a\$:", "En substituant \$x = $a\$ :"),
                            "\\lim_{x \\to $a} \\frac{$qPoly}{$dPoly} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val a = rng.pickNonZero(-4, 4)
                val u = rng.pick(listOf(1, 2, 3))
                var v = rng.range(-5, 5)
                while (u * a + v == 0) v = rng.range(-5, 5)
                val num = fmtPoly(1, 0, 0, -a * a * a)
                val den = fmtPoly(u, v - u * a, -v * a)
                val quad = fmtPoly(1, a, a * a)
                val lin = fmtPoly(u, v)
                val ans = Rational.of(3 * a * a, u * a + v)

                GeneratedExercise(
                    seed = seed,
                    technique = "poly_factor",
                    difficulty = 2,
                    fullLimitLatex = limPoint("$a", "\\frac{$num}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "استحضر المتطابقة الهامة \$x^3 - a^3 = (x-a)(x^2 + ax + a^2)\$.",
                            "Rappelle-toi l'identité remarquable \$x^3 - a^3 = (x-a)(x^2 + ax + a^2)\$."
                        ),
                        LocalizedText(
                            "عمّل المقام أيضاً بـ \$${fmtFactor(a)}\$.",
                            "Factorise également le dénominateur par \$${fmtFactor(a)}\$."
                        ),
                        LocalizedText(
                            "اختزل بـ \$${fmtFactor(a)}\$ ثم عوّض \$x = $a\$.",
                            "Simplifie par \$${fmtFactor(a)}\$ puis substitue \$x = $a\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تعميل الفرق بين مكعبين", "Identité remarquable cubique"),
                            LocalizedText(
                                "نستعمل \$x^3 - ($a)^3 = ${fmtFactor(a)}($quad)\$:",
                                "On applique la factorisation de \$x^3 - a^3\$ au numérateur et du trinôme au dénominateur :"
                            ),
                            "\\frac{$num}{$den} = \\frac{${fmtFactor(a)}($quad)}{${fmtFactor(a)}($lin)} = \\frac{$quad}{$lin}"
                        ),
                        SolutionStep(
                            LocalizedText("النتيجة", "Résultat"),
                            LocalizedText("بالتعويض بـ \$x = $a\$:", "Pour \$x = $a\$ :"),
                            "\\lim_{x \\to $a} \\frac{$quad}{$lin} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            else -> if (rng.nextInt(2) == 0) {
                val a = rng.pickNonZero(-3, 3)
                val p = rng.pick(listOf(1, 2, -1, -2))
                var q = rng.range(-5, 5)
                while (p * a + q == 0) q = rng.range(-5, 5)
                val u = rng.pick(listOf(1, 2, -1, -2))
                var v = rng.range(-5, 5)
                while (u * a + v == 0) v = rng.range(-5, 5)

                // (x-a)^2 (px+q) = p x^3 + (q-2ap)x^2 + (p a^2 - 2aq)x + q a^2
                val n3 = p
                val n2 = q - 2 * a * p
                val n1 = p * a * a - 2 * a * q
                val n0 = q * a * a
                val d3 = u
                val d2 = v - 2 * a * u
                val d1 = u * a * a - 2 * a * v
                val d0 = v * a * a
                val num = fmtPoly(n3, n2, n1, n0)
                val den = fmtPoly(d3, d2, d1, d0)
                val ans = Rational.of(p * a + q, u * a + v)

                GeneratedExercise(
                    seed = seed,
                    technique = "poly_factor",
                    difficulty = 3,
                    fullLimitLatex = limPoint("$a", "\\frac{$num}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "تعميل واحد قد لا يكفي! جرب التحقق مما إذا كان \$$a\$ جذراً مضعفاً (للمشتقة أيضاً).",
                            "Une simple factorisation peut laisser \$\\frac{0}{0}\$ ! Vérifie si \$$a\$ est racine double."
                        ),
                        LocalizedText(
                            "البسط والمقام يقبلان القسمة على \$${fmtFactor(a)}^2\$.",
                            "Le numérateur et le dénominateur sont tous deux divisibles par \$${fmtFactor(a)}^2\$."
                        ),
                        LocalizedText(
                            "بعد القسمة على \$${fmtFactor(a)}^2\$ يبقى كسر خطي بسيط.",
                            "Après division par \$${fmtFactor(a)}^2\$, il reste un quotient de deux polynômes de degré 1."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("كشف الجذر المزدوج", "Racine double"),
                            LocalizedText(
                                "عند القسمة الأولى على \$${fmtFactor(a)}\$ يبقى شكل غير محدد \$\\frac{0}{0}\$؛ إذن نعمل بـ \$${fmtFactor(a)}^2\$:",
                                "Après une première factorisation, \$x = $a\$ annule encore les quotients ; c'est une racine double :"
                            ),
                            "\\frac{$num}{$den} = \\frac{${fmtFactor(a)}^2(${fmtPoly(p, q)})}{${fmtFactor(a)}^2(${fmtPoly(u, v)})} = \\frac{${fmtPoly(p, q)}}{${fmtPoly(u, v)}}"
                        ),
                        SolutionStep(
                            LocalizedText("النهاية", "Limite finale"),
                            LocalizedText("بالتعويض بـ \$x = $a\$:", "En \$x = $a\$ :"),
                            "\\lim_{x \\to $a} \\frac{${fmtPoly(p, q)}}{${fmtPoly(u, v)}} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val negInf = rng.nextInt(2) == 0
                val infStr = if (negInf) "-\\infty" else "+\\infty"
                val a3 = rng.pickNonZero(-5, 5)
                val a2 = rng.range(-6, 6)
                val a0 = rng.range(-7, 7)
                val b2 = rng.pickNonZero(-4, 4)
                val b1 = rng.range(-6, 6)
                val b0 = rng.range(-7, 7)
                val num = fmtPoly(a3, a2, 0, a0)
                val den = fmtPoly(b2, b1, b0)

                val ratioSign = if ((a3 > 0) == (b2 > 0)) 1 else -1
                val xSign = if (negInf) -1 else 1
                val isPos = (ratioSign * xSign) > 0
                val ansLatex = if (isPos) "+\\infty" else "-\\infty"
                val expected = if (isPos) ExpectedAnswer.PosInf else ExpectedAnswer.NegInf

                GeneratedExercise(
                    seed = seed,
                    technique = "poly_factor",
                    difficulty = 3,
                    fullLimitLatex = limPoint(infStr, "\\frac{$num}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "درجة البسط (3) أكبر من درجة المقام (2)، لذا النهاية لا نهائية.",
                            "Le degré du numérateur (3) dépasse celui du dénominateur (2) : la limite est infinie."
                        ),
                        LocalizedText(
                            "انتبه لإشارة المعاملين الرئيسيين وكذلك لإشارة \$x\$ عند \$$infStr\$!",
                            "Attention au signe du quotient des termes dominants ET au signe de \$x\$ en \$$infStr\$ !"
                        ),
                        LocalizedText(
                            "تُختزل النسبة إلى \$${Rational.of(a3, b2).toLatex()}x\$.",
                            "Le terme équivalent se réduit à \$${Rational.of(a3, b2).toLatex()}x\$."
                        )
                    ),
                    answerLatex = ansLatex,
                    expectedAnswer = expected,
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("اختزال الحدين الأكبر درجة", "Termes de plus haut degré"),
                            LocalizedText(
                                "بجوار \$$infStr\$ نحتفظ بالحد الأعلى درجة في البسط والمقام:",
                                "Au voisinage de \$$infStr\$, on garde les monômes dominants :"
                            ),
                            "\\lim_{x \\to $infStr} \\frac{$num}{$den} = \\lim_{x \\to $infStr} \\frac{${fmtPoly(a3, 0, 0, 0)}}{${fmtPoly(b2, 0, 0)}} = \\lim_{x \\to $infStr} ${Rational.of(a3, b2).toLatex()}x"
                        ),
                        SolutionStep(
                            LocalizedText("تحديد الإشارة النهائية", "Règle des signes"),
                            LocalizedText(
                                "حاصل ضرب إشارة \$${Rational.of(a3, b2).toLatex()}\$ في إشارة \$$infStr\$ يعطي:",
                                "Par produit des signes entre \$${Rational.of(a3, b2).toLatex()}\$ et \$$infStr\$ :"
                            ),
                            "\\lim_{x \\to $infStr} ${Rational.of(a3, b2).toLatex()}x = $ansLatex"
                        )
                    )
                )
            }
        }
    }

    // ---------- 2. SQRT CONJUGATE ----------

    private fun genSqrtConjugate(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        return when (diff) {
            1 -> if (rng.nextInt(2) == 0) {
                val c = rng.range(1, 5)
                val a = rng.range(-4, 4)
                val m = rng.pick(listOf(1, 2, 3, 4, 5, -1, -2, -3))
                val p = c * c - m * a
                val k = rng.pick(listOf(1, 2, 3, -1, -2))
                val rad = fmtLinear(m, p)
                val den = fmtPoly(k, -k * a)
                val ans = Rational.of(m, 2 * c * k)
                val conj = "\\sqrt{$rad} + $c"

                GeneratedExercise(
                    seed = seed,
                    technique = "sqrt_conjugate",
                    difficulty = 1,
                    fullLimitLatex = limPoint("$a", "\\frac{\\sqrt{$rad} - $c}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "التعويض بـ \$x = $a\$ يعطي \$\\frac{\\sqrt{${c * c}} - $c}{0} = \\frac{0}{0}\$.",
                            "En \$x = $a\$, on a \$\\frac{\\sqrt{${c * c}} - $c}{0} = \\frac{0}{0}\$."
                        ),
                        LocalizedText(
                            "اضرب البسط والمقام في مرافق البسط: \$$conj\$.",
                            "Multiplie le numérateur et le dénominateur par le conjugué : \$$conj\$."
                        ),
                        LocalizedText(
                            "استعمل \$(A-B)(A+B) = A^2 - B^2\$ ليظهر العامل \$${fmtFactor(a)}\$ في البسط.",
                            "Utilise \$(A-B)(A+B) = A^2 - B^2\$ pour faire apparaître \$${fmtFactor(a)}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("الضرب في المرافق", "Multiplication par le conjugué"),
                            LocalizedText(
                                "نضرب بسطاً ومقاماً في \$$conj\$:",
                                "On multiplie haut et bas par \$$conj\$ :"
                            ),
                            "\\frac{(\\sqrt{$rad} - $c)(\\sqrt{$rad} + $c)}{($den)(\\sqrt{$rad} + $c)} = \\frac{${fmtPoly(m, p - c * c)}}{${fmtScaledFactor(k, a)}(\\sqrt{$rad} + $c)}"
                        ),
                        SolutionStep(
                            LocalizedText("الاختزال وحساب النهاية", "Simplification et résultat"),
                            LocalizedText(
                                "نلاحظ أن \$${fmtPoly(m, p - c * c)} = ${fmtScaledFactor(m, a)}\$، فنختزل بـ \$${fmtFactor(a)}\$ ونعوض \$x = $a\$:",
                                "On simplifie par \$${fmtFactor(a)}\$ puis on substitue \$x = $a\$ :"
                            ),
                            "\\lim_{x \\to $a} \\frac{$m}{$k(\\sqrt{$rad} + $c)} = \\frac{$m}{${2 * c * k}} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val c = rng.range(1, 5)
                val a = rng.range(-4, 4)
                val m = rng.pick(listOf(1, 2, 3, 4, -1, -2, -3))
                val p = c * c - m * a
                val k = rng.pick(listOf(1, 2, 3, -1))
                val rad = fmtLinear(m, p)
                val num = fmtPoly(k, -k * a)
                val ans = Rational.of(-2 * c * k, m)

                GeneratedExercise(
                    seed = seed,
                    technique = "sqrt_conjugate",
                    difficulty = 1,
                    fullLimitLatex = limPoint("$a", "\\frac{$num}{$c - \\sqrt{$rad}}"),
                    hints = listOf(
                        LocalizedText(
                            "المقام يحتوي على جذر مربع وينعدم عند \$$a\$؛ المرافق هنا للمقام.",
                            "Le dénominateur contient la racine et s'annule en \$$a\$ : le conjugué s'applique au dénominateur."
                        ),
                        LocalizedText(
                            "انتبه لإشارة الطرح: \$(c - \\sqrt{u})(c + \\sqrt{u}) = c^2 - u = -m(x - a)\$.",
                            "Attention au signe : \$(c - \\sqrt{u})(c + \\sqrt{u}) = c^2 - u = -m(x - a)\$."
                        ),
                        LocalizedText(
                            "اختزل بـ \$${fmtFactor(a)}\$ ثم عوّض \$x = $a\$.",
                            "Simplifie par \$${fmtFactor(a)}\$ puis remplace \$x = $a\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("ضرب البسط والمقام في مرافق المقام", "Conjugué du dénominateur"),
                            LocalizedText(
                                "نضرب في \$($c + \\sqrt{$rad})\$:",
                                "On multiplie par \$($c + \\sqrt{$rad})\$ :"
                            ),
                            "\\frac{($num)($c + \\sqrt{$rad})}{${c * c} - ($rad)} = \\frac{${fmtScaledFactor(k, a)}($c + \\sqrt{$rad})}{${fmtScaledFactor(-m, a)}}"
                        ),
                        SolutionStep(
                            LocalizedText("الاختزال وحساب النهاية", "Simplification"),
                            LocalizedText("نختزل بـ \$${fmtFactor(a)}\$ ونعوض:", "On simplifie par \$${fmtFactor(a)}\$ :"),
                            "\\lim_{x \\to $a} \\frac{$k($c + \\sqrt{$rad})}{${-m}} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            2 -> if (rng.nextInt(2) == 0) {
                val c = rng.range(1, 4)
                val a = rng.range(-3, 3)
                val m = rng.pick(listOf(1, 2, 3, 4, -1, -2))
                var n = rng.pick(listOf(1, 2, 3, 4, -1, -2, -3))
                while (n == m) n = rng.pick(listOf(1, 2, 3, 4, -1, -2, -3))
                val k = rng.pick(listOf(1, 2, 3))
                val p = c * c - m * a
                val q = c * c - n * a
                val r1 = fmtLinear(m, p)
                val r2 = fmtLinear(n, q)
                val den = fmtPoly(k, -k * a)
                val ans = Rational.of(m - n, 2 * c * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "sqrt_conjugate",
                    difficulty = 2,
                    fullLimitLatex = limPoint("$a", "\\frac{\\sqrt{$r1} - \\sqrt{$r2}}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "البسط عبارة عن فرق جذرين مربعين يساوي كلاهما \$$c\$ عند \$x = $a\$.",
                            "Le numérateur est une différence de deux racines égales à \$$c\$ en \$x = $a\$."
                        ),
                        LocalizedText(
                            "المرافق هو \$\\sqrt{$r1} + \\sqrt{$r2}\$.",
                            "Multiplie par le conjugué \$\\sqrt{$r1} + \\sqrt{$r2}\$."
                        ),
                        LocalizedText(
                            "في البسط ستحصل على \$($r1) - ($r2) = ${fmtScaledFactor(m - n, a)}\$.",
                            "Le numérateur devient \$($r1) - ($r2) = ${fmtScaledFactor(m - n, a)}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("المرافق المزدوج", "Conjugué de la différence"),
                            LocalizedText(
                                "نضرب في \$\\sqrt{$r1} + \\sqrt{$r2}\$:",
                                "On multiplie haut et bas par \$\\sqrt{$r1} + \\sqrt{$r2}\$ :"
                            ),
                            "\\frac{($r1) - ($r2)}{($den)(\\sqrt{$r1} + \\sqrt{$r2})} = \\frac{${fmtScaledFactor(m - n, a)}}{${fmtScaledFactor(k, a)}(\\sqrt{$r1} + \\sqrt{$r2})}"
                        ),
                        SolutionStep(
                            LocalizedText("الاختزال وحساب النهاية", "Résultat final"),
                            LocalizedText("نختزل بـ \$${fmtFactor(a)}\$ ونعوض \$x = $a\$:", "Après simplification :"),
                            "\\lim_{x \\to $a} \\frac{${m - n}}{$k(\\sqrt{$r1} + \\sqrt{$r2})} = \\frac{${m - n}}{${2 * c * k}} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                // Classic 2BAC -infinity sign trap: sqrt(r^2 x^2 + b x + c) + r x
                val r = rng.range(1, 4)
                val b = rng.pickNonZero(-9, 9)
                val c = rng.range(-7, 7)
                val inner = fmtPoly(r * r, b, c)
                val linTerm = fmtSignedTerm(r, "x")
                val ans = Rational.of(-b, 2 * r)

                GeneratedExercise(
                    seed = seed,
                    technique = "sqrt_conjugate",
                    difficulty = 2,
                    fullLimitLatex = limPoint("-\\infty", "\\left(\\sqrt{$inner}$linTerm\\right)"),
                    hints = listOf(
                        LocalizedText(
                            "فخ الباك الكلاسيكي! عند \$-\\infty\$ لدينا \$\\sqrt{$inner} \\to +\\infty\$ و \$${r}x \\to -\\infty\$: شكل غير محدد \$\\infty - \\infty\$.",
                            "Piège classique du Bac ! En \$-\\infty\$, on obtient la forme indéterminée \$\\infty - \\infty\$."
                        ),
                        LocalizedText(
                            "بما أن معامل \$x^2\$ هو \$${r * r} = $r^2\$ ومعامل \$x\$ هو \$+$r\$، فالتعميل بـ \$|x| = -x\$ يعطي \$-rx + rx = 0\$: إذن يجب استعمال المرافق!",
                            "Comme \$\\sqrt{${r * r}x^2} = ${r}|x| = -${r}x\$ compense \$+${r}x\$, il faut multiplier par le conjugué !"
                        ),
                        LocalizedText(
                            "تذكر في المقام عند التعميل بـ \$x^2\$ أن \$\\sqrt{x^2} = |x| = -x\$ لأن \$x < 0\$!",
                            "Attention dans le dénominateur : pour \$x < 0\$, \$\\sqrt{x^2} = |x| = -x\$ !"
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("فحص التعميل ثم اللجوء للمرافق", "Multiplication par le conjugué"),
                            LocalizedText(
                                "عند \$-\\infty\$ يختفي الحد الأعلى درجة عند التعميل المباشر لأن \$\\sqrt{${r * r}x^2} + ${r}x = ${r}|x| + ${r}x = -${r}x + ${r}x = 0\$، لذا نضرب في المرافق:",
                                "La factorisation directe laisse \$0 \\cdot \\infty\$ car \$|x| = -x\$ en \$-\\infty\$. On utilise donc le conjugué :"
                            ),
                            "\\frac{($inner) - ${r * r}x^2}{\\sqrt{$inner} - ${if (r == 1) "" else "$r"}x} = \\frac{${fmtLinear(b, c)}}{\\sqrt{$inner} - ${if (r == 1) "" else "$r"}x}"
                        ),
                        SolutionStep(
                            LocalizedText("التعميل بـ |x| = -x عند -∞", "Factorisation par |x| = -x"),
                            LocalizedText(
                                "لأن \$x < 0\$ فإن \$\\sqrt{x^2} = -x\$، فنعمّل بـ \$x\$ في البسط والمقام:",
                                "Puisque \$x \\to -\\infty\$ (\$x < 0\$), \$\\sqrt{x^2} = |x| = -x\$ :"
                            ),
                            "\\frac{x\\left($b + \\frac{$c}{x}\\right)}{-x\\sqrt{${r * r} + \\frac{$b}{x} + \\frac{$c}{x^2}} - ${if (r == 1) "" else "$r"}x} = \\frac{$b + \\frac{$c}{x}}{-\\sqrt{${r * r} + \\frac{$b}{x} + \\frac{$c}{x^2}} - $r}"
                        ),
                        SolutionStep(
                            LocalizedText("النهاية", "Résultat final"),
                            LocalizedText("عندما يؤول \$x\$ إلى \$-\\infty\$ تؤول الكسور إلى \$0\$:", "En \$-\\infty\$, les termes en \$\\frac{1}{x}\$ tendent vers \$0\$ :"),
                            "\\frac{$b}{-$r - $r} = \\frac{$b}{${-2 * r}} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            else -> if (rng.nextInt(2) == 0) {
                val c = rng.range(1, 4)
                val d = rng.range(1, 4)
                val a = rng.range(-3, 3)
                val m = rng.pick(listOf(1, 2, 3, 4, -1, -2))
                val n = rng.pick(listOf(1, 2, 3, 4, -1, -2))
                val p = c * c - m * a
                val q = d * d - n * a
                val r1 = fmtLinear(m, p)
                val r2 = fmtLinear(n, q)
                val ans = Rational.of(m * d, n * c)

                GeneratedExercise(
                    seed = seed,
                    technique = "sqrt_conjugate",
                    difficulty = 3,
                    fullLimitLatex = limPoint("$a", "\\frac{\\sqrt{$r1} - $c}{\\sqrt{$r2} - $d}"),
                    hints = listOf(
                        LocalizedText(
                            "كل من البسط والمقام يحتوي على جذر مربع: نحتاج للضرب في مرافق البسط ومرافق المقام معاً!",
                            "Numérateur et dénominateur ont une racine carrée : il faut appliquer deux conjugués simultanément !"
                        ),
                        LocalizedText(
                            "اضرب في \$\\frac{(\\sqrt{$r1} + $c)(\\sqrt{$r2} + $d)}{(\\sqrt{$r1} + $c)(\\sqrt{$r2} + $d)}\$.",
                            "Multiplie par \$(\\sqrt{$r1} + $c)(\\sqrt{$r2} + $d)\$ en haut et en bas."
                        ),
                        LocalizedText(
                            "يختزل العامل \$${fmtFactor(a)}\$ ويتبقى \$\\frac{m(\\sqrt{$r2} + $d)}{n(\\sqrt{$r1} + $c)}\$.",
                            "Simplifie par \$${fmtFactor(a)}\$ pour obtenir \$\\frac{m(\\sqrt{$r2} + $d)}{n(\\sqrt{$r1} + $c)}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("المرافق في البسط والمقام", "Double conjugué"),
                            LocalizedText(
                                "نضرب بسطاً ومقاماً في مرافق كل طرف:",
                                "On multiplie par les deux expressions conjuguées :"
                            ),
                            "\\frac{($r1 - ${c * c})(\\sqrt{$r2} + $d)}{($r2 - ${d * d})(\\sqrt{$r1} + $c)} = \\frac{${fmtScaledFactor(m, a)}(\\sqrt{$r2} + $d)}{${fmtScaledFactor(n, a)}(\\sqrt{$r1} + $c)}"
                        ),
                        SolutionStep(
                            LocalizedText("الاختزال وحساب النهاية", "Calcul final"),
                            LocalizedText("بعد الاختزال بـ \$${fmtFactor(a)}\$ والتعويض بـ \$x = $a\$:", "Après simplification et substitution :"),
                            "\\frac{$m \\cdot ${2 * d}}{$n \\cdot ${2 * c}} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                // -infinity without conjugate trap! d != r
                val r = rng.range(1, 4)
                var d = rng.range(-5, 5)
                while (d == r) d = rng.range(-5, 5)
                val b = rng.range(-6, 6)
                val c = rng.range(-6, 6)
                val p = rng.pickNonZero(-4, 4)
                val q = rng.range(-6, 6)

                val inner = fmtPoly(r * r, b, c)
                val linExtra = fmtSignedTerm(d, "x")
                val den = fmtLinear(p, q)
                val ans = Rational.of(d - r, p)

                GeneratedExercise(
                    seed = seed,
                    technique = "sqrt_conjugate",
                    difficulty = 3,
                    fullLimitLatex = limPoint("-\\infty", "\\frac{\\sqrt{$inner}$linExtra}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "هل تحتاج للمرافق هنا؟ تحقق من تعميل \$|x| = -x\$ أولاً!",
                            "A-t-on vraiment besoin du conjugué ici ? Teste d'abord la factorisation par \$|x| = -x\$ !"
                        ),
                        LocalizedText(
                            "عند \$-\\infty\$ لدينا \$\\sqrt{${r * r}x^2} = ${r}|x| = -${r}x\$، ومع \$${linExtra}\$ المعامل في \$x\$ غير منعدم!",
                            "En \$-\\infty\$, \$\\sqrt{${r * r}x^2} = -${r}x\$, qui ne s'annule pas avec \$${linExtra}\$ !"
                        ),
                        LocalizedText(
                            "اختزل بـ \$x\$ واحذر من إشارة السالب عند إخراج \$x^2\$ من الجذر!",
                            "Factorise par \$x\$ en faisant attention au signe moins issu de \$\\sqrt{x^2} = -x\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("التعميل بـ x واستخدام |x| = -x", "Factorisation directe sans conjugué"),
                            LocalizedText(
                                "بما أن \$x < 0\$ فإن \$\\sqrt{x^2} = |x| = -x\$، وبما أن \$$d \\neq $r\$ فلا حاجة للمرافق:",
                                "Comme \$x < 0\$, \$\\sqrt{x^2} = -x\$. Et puisque \$$d \\neq $r\$, pas besoin de conjugué :"
                            ),
                            "\\frac{-x\\sqrt{${r * r} + \\frac{$b}{x} + \\frac{$c}{x^2}}$linExtra}{x\\left($p + \\frac{$q}{x}\\right)} = \\frac{-\\sqrt{${r * r} + \\frac{$b}{x} + \\frac{$c}{x^2}} + ($d)}{$p + \\frac{$q}{x}}"
                        ),
                        SolutionStep(
                            LocalizedText("النهاية", "Résultat"),
                            LocalizedText("نأخذ النهاية عند \$-\\infty\$:", "Par passage à la limite en \$-\\infty\$ :"),
                            "\\frac{-$r + ($d)}{$p} = ${ans.toLatex()}"
                        )
                    )
                )
            }
        }
    }

    // ---------- 3. NTH ROOT ----------

    private fun genNthRoot(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        return when (diff) {
            1 -> {
                val c = rng.range(1, 4)
                val a = rng.range(-4, 4)
                val m = rng.pick(listOf(1, 2, 3, 4, 5, -1, -2, -3))
                val k = rng.pick(listOf(1, 2, -1))
                val p = c * c * c - m * a
                val rad = fmtLinear(m, p)
                val den = fmtPoly(k, -k * a)
                val ans = Rational.of(m, 3 * c * c * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "nth_root",
                    difficulty = 1,
                    fullLimitLatex = limPoint("$a", "\\frac{\\sqrt[3]{$rad} - $c}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "التعويض بـ \$x = $a\$ يعطي \$\\frac{\\sqrt[3]{${c * c * c}} - $c}{0} = \\frac{0}{0}\$.",
                            "En \$x = $a\$, on a \$\\frac{\\sqrt[3]{${c * c * c}} - $c}{0} = \\frac{0}{0}\$."
                        ),
                        LocalizedText(
                            "استعمل المتطابقة \$A - B = \\frac{A^3 - B^3}{A^2 + AB + B^2}\$ مع \$A = \\sqrt[3]{$rad}\$ و \$B = $c\$.",
                            "Utilise \$A - B = \\frac{A^3 - B^3}{A^2 + AB + B^2}\$ avec \$A = \\sqrt[3]{$rad}\$ et \$B = $c\$."
                        ),
                        LocalizedText(
                            "عند \$x = $a\$ كل حد من الحدود الثلاثة في المقام \$A^2 + AB + B^2\$ يساوي \$${c * c}\$.",
                            "En \$x = $a\$, chacun des trois termes \$A^2, AB, B^2\$ vaut \$${c * c}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("المرافق التكعيبي", "Expression conjuguée cubique"),
                            LocalizedText(
                                "نستعمل \$A - B = \\frac{A^3 - B^3}{A^2 + AB + B^2}\$:",
                                "On utilise l'identité \$A^3 - B^3 = (A - B)(A^2 + AB + B^2)\$ :"
                            ),
                            "\\frac{($rad) - ${c * c * c}}{($den)\\left((\\sqrt[3]{$rad})^2 + $c\\sqrt[3]{$rad} + ${c * c}\\right)} = \\frac{${fmtScaledFactor(m, a)}}{${fmtScaledFactor(k, a)}\\left((\\sqrt[3]{$rad})^2 + $c\\sqrt[3]{$rad} + ${c * c}\\right)}"
                        ),
                        SolutionStep(
                            LocalizedText("الاختزال والنتيجة", "Calcul final"),
                            LocalizedText("نختزل بـ \$${fmtFactor(a)}\$ ثم نعوض \$x = $a\$:", "On simplifie par \$${fmtFactor(a)}\$ puis on substitue \$x = $a\$ :"),
                            "\\frac{$m}{$k(${c * c} + ${c * c} + ${c * c})} = \\frac{$m}{${3 * c * c * k}} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            2 -> if (rng.nextInt(2) == 0) {
                val c = rng.range(1, 3)
                val a = rng.range(-3, 3)
                val m = rng.pick(listOf(1, 2, 3, 4, -1, -2))
                val k = rng.pick(listOf(1, 2))
                val c4 = c * c * c * c
                val p = c4 - m * a
                val rad = fmtLinear(m, p)
                val den = fmtPoly(k, -k * a)
                val ans = Rational.of(m, 4 * c * c * c * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "nth_root",
                    difficulty = 2,
                    fullLimitLatex = limPoint("$a", "\\frac{\\sqrt[4]{$rad} - $c}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "هنا جذر من الرتبة 4؛ يمكنك استعمال \$A^4 - B^4\$ أو وضع تغيير المتغير \$t = \\sqrt[4]{$rad}\$.",
                            "Racine d'ordre 4 : utilise \$A^4 - B^4\$ ou pose le changement de variable \$t = \\sqrt[4]{$rad}\$."
                        ),
                        LocalizedText(
                            "تذكر أن \$t^4 - $c^4 = (t - $c)(t^3 + ${c}t^2 + ${c * c}t + ${c * c * c})\$.",
                            "Rappel : \$t^4 - $c^4 = (t - $c)(t^3 + ${c}t^2 + ${c * c}t + ${c * c * c})\$."
                        ),
                        LocalizedText(
                            "المجموع في المقام يعطي \$4 \\cdot $c^3 = ${4 * c * c * c}\$.",
                            "En \$t = $c\$, la somme des 4 termes vaut \$4 \\cdot $c^3 = ${4 * c * c * c}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تغيير المتغير أو متطابقة الرتبة 4", "Changement de variable t = ⁴√(...)"),
                            LocalizedText(
                                "نضع \$t = \\sqrt[4]{$rad}\$ فتؤول \$t \\to $c\$ ويكون \$t^4 = $rad\$، أي \$x - $a = \\frac{t^4 - $c4}{$m}\$:",
                                "En posant \$t = \\sqrt[4]{$rad} \\to $c\$, on a \$x - $a = \\frac{t^4 - $c4}{$m}\$ :"
                            ),
                            "\\lim_{t \\to $c} \\frac{t - $c}{\\frac{$k}{$m}(t^4 - $c4)} = \\lim_{t \\to $c} \\frac{$m}{$k(t^3 + ${c}t^2 + ${c * c}t + ${c * c * c})}"
                        ),
                        SolutionStep(
                            LocalizedText("النتيجة النهائية", "Résultat"),
                            LocalizedText("نعوض \$t = $c\$:", "En substituant \$t = $c\$ :"),
                            "\\frac{$m}{$k \\cdot ${4 * c * c * c}} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                // Change of variable t = x^(1/6)
                val t0 = rng.pick(listOf(1, 2))
                val aVal = if (t0 == 1) 1 else 64
                val p = rng.pick(listOf(1, 2, 3))
                val k = rng.pick(listOf(1, 2, 3, -1, -2))
                val t0Sq = t0 * t0
                val t0Cu = t0 * t0 * t0
                val numLatex = if (p == 1) "\\sqrt[3]{x} - $t0Sq" else "$p(\\sqrt[3]{x} - $t0Sq)"
                val denLatex = if (k == 1) "\\sqrt{x} - $t0Cu" else "$k(\\sqrt{x} - $t0Cu)"
                val ans = Rational.of(2 * p, 3 * k * t0)

                GeneratedExercise(
                    seed = seed,
                    technique = "nth_root",
                    difficulty = 2,
                    fullLimitLatex = limPoint("$aVal", "\\frac{$numLatex}{$denLatex}"),
                    hints = listOf(
                        LocalizedText(
                            "المضاعف المشترك الأصغر لرتبتي الجذرين (2 و 3) هو 6!",
                            "Le PPCM des ordres 2 et 3 est 6 !"
                        ),
                        LocalizedText(
                            "ضع تغيير المتغير \$t = \\sqrt[6]{x}\$؛ عندها \$\\sqrt[3]{x} = t^2\$ و \$\\sqrt{x} = t^3\$ وتؤول \$t \\to $t0\$.",
                            "Pose \$t = \\sqrt[6]{x}\$ : alors \$\\sqrt[3]{x} = t^2\$, \$\\sqrt{x} = t^3\$ et \$t \\to $t0\$."
                        ),
                        LocalizedText(
                            "تتحول النهاية إلى كسر حدودي في \$t\$ يُختزل بـ \$(t - $t0)\$.",
                            "La limite devient une fraction rationnelle en \$t\$ qu'on simplifie par \$(t - $t0)\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تغيير المتغير t = ⁶√x", "Changement de variable t = x^(1/6)"),
                            LocalizedText(
                                "نضع \$t = \\sqrt[6]{x}\$ بحيث \$t \\to $t0\$، فيكون \$\\sqrt[3]{x} = t^2\$ و \$\\sqrt{x} = t^3\$:",
                                "On pose \$t = \\sqrt[6]{x} \\to $t0\$, donc \$\\sqrt[3]{x} = t^2\$ et \$\\sqrt{x} = t^3\$ :"
                            ),
                            "\\lim_{t \\to $t0} \\frac{$p(t^2 - $t0Sq)}{$k(t^3 - $t0Cu)} = \\lim_{t \\to $t0} \\frac{$p(t - $t0)(t + $t0)}{$k(t - $t0)(t^2 + ${if (t0 == 1) "" else "$t0"}t + $t0Sq)}"
                        ),
                        SolutionStep(
                            LocalizedText("الاختزال والنتيجة", "Simplification et résultat"),
                            LocalizedText("نختزل بـ \$(t - $t0)\$ ونعوض \$t = $t0\$:", "Après simplification par \$(t - $t0)\$ :"),
                            "\\frac{$p(${2 * t0})}{$k(${3 * t0Sq})} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            else -> if (rng.nextInt(2) == 0) {
                val r = rng.range(1, 3)
                val b = rng.pickNonZero(-9, 9)
                val c = rng.range(-8, 8)
                val inner = fmtPoly(r * r * r, b, 0, c)
                val ans = Rational.of(b, 3 * r * r)

                GeneratedExercise(
                    seed = seed,
                    technique = "nth_root",
                    difficulty = 3,
                    fullLimitLatex = limPoint("+\\infty", "\\left(\\sqrt[3]{$inner} - ${if (r == 1) "" else "$r"}x\\right)"),
                    hints = listOf(
                        LocalizedText(
                            "عند \$+\\infty\$ نجد شكلاً غير محدد \$+\\infty - \\infty\$. وبما أن \$\\sqrt[3]{${r * r * r}x^3} = ${r}x\$، التعميل بـ \$x\$ يترك \$0 \\cdot \\infty\$!",
                            "En \$+\\infty\$, forme indéterminée \$\\infty - \\infty\$. La factorisation directe donne \$0 \\cdot \\infty\$."
                        ),
                        LocalizedText(
                            "استعمل المرافق التكعيبي \$A - B = \\frac{A^3 - B^3}{A^2 + AB + B^2}\$ مع \$B = ${r}x\$.",
                            "Utilise le conjugué cubique \$A - B = \\frac{A^3 - B^3}{A^2 + AB + B^2}\$ avec \$B = ${r}x\$."
                        ),
                        LocalizedText(
                            "اقسم البسط والمقام على \$x^2\$: الحد المهيمن في المقام هو \$3 \\cdot $r^2 x^2\$.",
                            "Divise haut et bas par \$x^2\$ : le terme dominant du dénominateur est \$3 \\cdot $r^2 x^2\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تطبيق المرافق التكعيبي", "Conjugué cubique"),
                            LocalizedText(
                                "نستعمل \$A - B = \\frac{A^3 - B^3}{A^2 + AB + B^2}\$ لإزالة الجذر التكعيبي:",
                                "On applique \$A - B = \\frac{A^3 - B^3}{A^2 + AB + B^2}\$ :"
                            ),
                            "\\frac{($inner) - ${r * r * r}x^3}{(\\sqrt[3]{$inner})^2 + ${if (r == 1) "" else "$r"}x\\sqrt[3]{$inner} + ${r * r}x^2} = \\frac{${fmtPoly(b, 0, c)}}{(\\sqrt[3]{$inner})^2 + ${if (r == 1) "" else "$r"}x\\sqrt[3]{$inner} + ${r * r}x^2}"
                        ),
                        SolutionStep(
                            LocalizedText("التعميل بـ x² وحساب النهاية", "Factorisation par x²"),
                            LocalizedText(
                                "نعامل البسط والمقام بـ \$x^2\$ وبأخذ النهاية عند \$+\\infty\$:",
                                "On factorise numérateur et dénominateur par \$x^2\$ puis on passe à la limite :"
                            ),
                            "\\frac{$b + \\frac{$c}{x^2}}{\\left(\\sqrt[3]{${r * r * r} + \\frac{$b}{x} + \\frac{$c}{x^3}}\\right)^2 + $r\\sqrt[3]{${r * r * r} + \\frac{$b}{x} + \\frac{$c}{x^3}} + ${r * r}} \\to \\frac{$b}{${3 * r * r}} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val c = rng.range(1, 3)
                val d = rng.range(1, 2)
                val a = rng.range(-3, 3)
                val m = rng.pick(listOf(1, 2, 3, 4, -1, -2))
                val n = rng.pick(listOf(1, 2, 3, -1, -2))
                val p = c * c * c - m * a
                val q = d * d * d * d - n * a
                val r1 = fmtLinear(m, p)
                val r2 = fmtLinear(n, q)
                val ans = Rational.of(4 * m * d * d * d, 3 * n * c * c)

                GeneratedExercise(
                    seed = seed,
                    technique = "nth_root",
                    difficulty = 3,
                    fullLimitLatex = limPoint("$a", "\\frac{\\sqrt[3]{$r1} - $c}{\\sqrt[4]{$r2} - $d}"),
                    hints = listOf(
                        LocalizedText(
                            "اقسم كلاً من البسط والمقام على \$${fmtFactor(a)}\$!",
                            "Astuce : divise numérateur et dénominateur par \$${fmtFactor(a)}\$ !"
                        ),
                        LocalizedText(
                            "احسب نهاية البسط على \$${fmtFactor(a)}\$ بالمرافق التكعيبي، ونهاية المقام على \$${fmtFactor(a)}\$ بمرافق الرتبة 4.",
                            "Calcule séparément la limite de \$\\frac{\\sqrt[3]{$r1}-$c}{${fmtFactor(a)}}\$ et de \$\\frac{\\sqrt[4]{$r2}-$d}{${fmtFactor(a)}}\$."
                        ),
                        LocalizedText(
                            "الناتج هو خارج النهايتين: \$\\frac{$m}{3\\cdot $c^2} \\div \\frac{$n}{4\\cdot $d^3}\$.",
                            "Le résultat est le quotient \$\\frac{$m}{3\\cdot $c^2} \\div \\frac{$n}{4\\cdot $d^3}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("القسمة على (x - a)", "Division par (x - a)"),
                            LocalizedText(
                                "نكتب الكسر على شكل خارج معدلَي التغير عند \$$a\$:",
                                "On sépare le numérateur et le dénominateur en les divisant par \$${fmtFactor(a)}\$ :"
                            ),
                            "\\frac{\\frac{\\sqrt[3]{$r1} - $c}{${fmtFactor(a)}}}{\\frac{\\sqrt[4]{$r2} - $d}{${fmtFactor(a)}}}"
                        ),
                        SolutionStep(
                            LocalizedText("حساب كل نهاية على حدة", "Limites partielles et quotient"),
                            LocalizedText(
                                "بالمرافق التكعيبي في الأعلى ومرافق الرتبة 4 في الأسفل نحصل على:",
                                "Par conjugué d'ordre 3 et d'ordre 4 :"
                            ),
                            "\\frac{${Rational.of(m, 3 * c * c).toLatex()}}{${Rational.of(n, 4 * d * d * d).toLatex()}} = ${ans.toLatex()}"
                        )
                    )
                )
            }
        }
    }

    // ---------- 4. ADD / SUB ZERO ----------

    private fun genAddSubZero(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        return when (diff) {
            1 -> {
                val c = rng.range(1, 3)
                val a = rng.range(-3, 3)
                val m = rng.range(1, 5)
                val n = rng.range(1, 5)
                val k = rng.pick(listOf(1, 2, 3))
                val isMinus = rng.nextInt(2) == 0
                val p = c * c - m * a
                val q = c * c * c - n * a
                val r1 = fmtLinear(m, p)
                val r2 = fmtLinear(n, q)
                val den = fmtPoly(k, -k * a)

                val ans = if (isMinus) {
                    Rational.of(3 * c * m - 2 * n, 6 * c * c * k)
                } else {
                    Rational.of(3 * c * m + 2 * n, 6 * c * c * k)
                }

                val exprLatex = if (isMinus) {
                    "\\frac{\\sqrt{$r1} - \\sqrt[3]{$r2}}{$den}"
                } else {
                    "\\frac{\\sqrt{$r1} + \\sqrt[3]{$r2} - ${2 * c}}{$den}"
                }

                val splitLatex = if (isMinus) {
                    "\\frac{(\\sqrt{$r1} - $c) - (\\sqrt[3]{$r2} - $c)}{$den} = \\frac{\\sqrt{$r1} - $c}{$den} - \\frac{\\sqrt[3]{$r2} - $c}{$den}"
                } else {
                    "\\frac{(\\sqrt{$r1} - $c) + (\\sqrt[3]{$r2} - $c)}{$den} = \\frac{\\sqrt{$r1} - $c}{$den} + \\frac{\\sqrt[3]{$r2} - $c}{$den}"
                }

                val term1 = Rational.of(m, 2 * c * k)
                val term2 = Rational.of(n, 3 * c * c * k)
                val signOp = if (isMinus) "-" else "+"

                GeneratedExercise(
                    seed = seed,
                    technique = "add_sub_zero",
                    difficulty = 1,
                    fullLimitLatex = limPoint("$a", exprLatex),
                    hints = listOf(
                        LocalizedText(
                            "رتبتا الجذرين مختلفتان (2 و 3)، لذا لا يمكن الضرب في مرافق واحد مباشرة.",
                            "Les radicaux sont d'ordres différents (2 et 3) : un conjugué unique ne suffit pas."
                        ),
                        LocalizedText(
                            "عند \$x = $a\$ يأخذ كل جذر القيمة \$$c\$. استعمل تقنية إضافة وطرح \$$c\$.",
                            "En \$x = $a\$, chaque radical vaut \$$c\$. Ajoute et retranche \$$c\$ au numérateur."
                        ),
                        LocalizedText(
                            "افصل الكسر إلى نهايتين: إحداهما بالمرافق التربيعي والأخرى بالمرافق التكعيبي.",
                            "Sépare la fraction en deux limites : une avec conjugué carré, l'autre avec conjugué cubique."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("إضافة وطرح الصفر (±$c)", "Ajout et retranchement de $c"),
                            LocalizedText(
                                "بما أن \$\\sqrt{${c * c}} = $c\$ و \$\\sqrt[3]{${c * c * c}} = $c\$ نكتب:",
                                "Comme chaque terme tend vers \$$c\$ en \$x = $a\$, on fait apparaître \$(- $c + $c)\$ :"
                            ),
                            splitLatex
                        ),
                        SolutionStep(
                            LocalizedText("حساب كل نهاية على حدة", "Calcul séparé"),
                            LocalizedText(
                                "نضرب الكسر الأول في المرافق التربيعي والثاني في المرافق التكعيبي:",
                                "On applique le conjugué carré au premier terme et le conjugué cubique au second :"
                            ),
                            "${term1.toLatex()} $signOp ${term2.toLatex()} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            2 -> if (rng.nextInt(2) == 0) {
                val c = rng.range(1, 3)
                val d = rng.range(1, 3)
                val a = rng.range(-3, 3)
                val u = rng.range(1, 3)
                val v = rng.pickNonZero(-3, 3)
                val m = rng.range(1, 4)
                val n = rng.range(1, 4)
                val k = rng.pick(listOf(1, 2))
                val p = c * c - m * a
                val q = d * d * d - n * a
                val bigK = u * c + v * d

                val r1 = fmtLinear(m, p)
                val r2 = fmtLinear(n, q)
                val den = fmtPoly(k, -k * a)
                val termU = if (u == 1) "\\sqrt{$r1}" else "$u\\sqrt{$r1}"
                val termV = fmtSignedTerm(v, "\\sqrt[3]{$r2}")
                val constPart = if (bigK == 0) "" else if (bigK > 0) " - $bigK" else " + ${-bigK}"
                val ans = Rational.of(3 * u * m * d * d + 2 * v * n * c, 6 * c * d * d * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "add_sub_zero",
                    difficulty = 2,
                    fullLimitLatex = limPoint("$a", "\\frac{$termU$termV$constPart}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "عوّض \$x = $a\$ في كل جذر على حدة: الجذر التربيعي يعطي \$$c\$ والتكعيبي يعطي \$$d\$.",
                            "En \$x = $a\$, la racine carrée vaut \$$c\$ et la racine cubique vaut \$$d\$."
                        ),
                        LocalizedText(
                            "فكّك الثابت \$${-bigK}\$ إلى \$(-${u * c}) + (${-v * d})\$ لموازنة كل جذر على حدة!",
                            "Décompose la constante \$${-bigK}\$ en \$(-${u * c}) + (${-v * d})\$ pour équilibrer chaque terme !"
                        ),
                        LocalizedText(
                            "اعزل \$u(\\sqrt{$r1} - $c)\$ و \$v(\\sqrt[3]{$r2} - $d)\$ ثم طبّق المرافق المناسب لكل منهما.",
                            "Sépare en \$u(\\sqrt{$r1} - $c)\$ et \$v(\\sqrt[3]{$r2} - $d)\$ puis utilise les conjugués."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تفكيك الثابت (إضافة وطرح)", "Décomposition de la constante"),
                            LocalizedText(
                                "نلاحظ أن \$$u($c) + ($v)($d) = $bigK\$، لذا نفصل البسط إلى جزأين:",
                                "On remarque que \$$u\\cdot $c + ($v)\\cdot $d = $bigK\$, d'où :"
                            ),
                            "\\frac{$u(\\sqrt{$r1} - $c) + ($v)(\\sqrt[3]{$r2} - $d)}{$den}"
                        ),
                        SolutionStep(
                            LocalizedText("حساب النهايتين والجمع", "Somme des deux limites"),
                            LocalizedText("باستعمال المرافق التربيعي ثم التكعيبي:", "Par conjugué d'ordre 2 et d'ordre 3 :"),
                            "${Rational.of(u * m, 2 * c * k).toLatex()} + \\left(${Rational.of(v * n, 3 * d * d * k).toLatex()}\\right) = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                // Product (u x + v)*sqrt(m x + p) - (u a + v)c
                val c = rng.range(1, 3)
                val a = rng.range(-3, 3)
                val u = rng.pick(listOf(1, 2, -1))
                val v = rng.range(-4, 4)
                val m = rng.range(1, 4)
                val k = rng.pick(listOf(1, 2))
                val p = c * c - m * a
                val uaPlusV = u * a + v
                val bigK = uaPlusV * c

                val lin = fmtLinear(u, v)
                val rad = fmtLinear(m, p)
                val den = fmtPoly(k, -k * a)
                val constTail = if (bigK == 0) "" else if (bigK > 0) " - $bigK" else " + ${-bigK}"
                val ans = Rational.of(2 * u * c * c + m * uaPlusV, 2 * c * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "add_sub_zero",
                    difficulty = 2,
                    fullLimitLatex = limPoint("$a", "\\frac{($lin)\\sqrt{$rad}$constTail}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "لدينا جداء دالتين في البسط؛ أضف واطرح \$($uaPlusV)\\sqrt{$rad}\$ (أو \$$c($lin)\$)!",
                            "Le numérateur contient un produit ! Ajoute et retranche \$($uaPlusV)\\sqrt{$rad}\$ (ou \$$c($lin)\$)."
                        ),
                        LocalizedText(
                            "الطرف الأول يختزل فيه \$${fmtFactor(a)}\$ مباشرة، والطرف الثاني يُحل بالمرافق التربيعي.",
                            "Le premier terme se factorise directement par \$${fmtFactor(a)}\$, le second utilise le conjugué."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("إضافة وطرح الحد الوسيط", "Ajout et retranchement"),
                            LocalizedText(
                                "نضيف ونطرح \$($uaPlusV)\\sqrt{$rad}\$ في البسط:",
                                "On ajoute et on retranche \$($uaPlusV)\\sqrt{$rad}\$ :"
                            ),
                            "\\frac{(($lin) - ($uaPlusV))\\sqrt{$rad} + ($uaPlusV)(\\sqrt{$rad} - $c)}{$den} = \\frac{${fmtScaledFactor(u, a)}\\sqrt{$rad}}{$den} + \\frac{($uaPlusV)(\\sqrt{$rad} - $c)}{$den}"
                        ),
                        SolutionStep(
                            LocalizedText("الاختزال وحساب النهاية", "Calcul final"),
                            LocalizedText("نختزل بـ \$${fmtFactor(a)}\$ ونستعمل المرافق في الكسر الثاني:", "On simplifie par \$${fmtFactor(a)}\$ :"),
                            "${Rational.of(u * c, k).toLatex()} + \\left(${Rational.of(uaPlusV * m, 2 * c * k).toLatex()}\\right) = ${ans.toLatex()}"
                        )
                    )
                )
            }

            else -> if (rng.nextInt(2) == 0) {
                // sqrt(m x + p) * cbrt(n x + q) - c * d
                val c = rng.range(1, 3)
                val d = rng.range(1, 3)
                val a = rng.range(-2, 2)
                val m = rng.range(1, 4)
                val n = rng.range(1, 4)
                val k = rng.pick(listOf(1, 2))
                val p = c * c - m * a
                val q = d * d * d - n * a
                val r1 = fmtLinear(m, p)
                val r2 = fmtLinear(n, q)
                val den = fmtPoly(k, -k * a)
                val ans = Rational.of(3 * m * d * d * d + 2 * n * c * c, 6 * c * d * d * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "add_sub_zero",
                    difficulty = 3,
                    fullLimitLatex = limPoint("$a", "\\frac{\\sqrt{$r1}\\sqrt[3]{$r2} - ${c * d}}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "في البسط جداء جذرين مختلفين؛ عند \$x = $a\$ الأول يساوي \$$c\$ والثاني \$$d\$.",
                            "Produit de deux radicaux différents : en \$x = $a\$, ils valent \$$c\$ et \$$d\$."
                        ),
                        LocalizedText(
                            "أضف واطرح الحد الوسيط \$${d}\\sqrt{$r1}\$ لتفكيك الجداء!",
                            "Ajoute et retranche \$${d}\\sqrt{$r1}\$ au numérateur pour séparer les deux radicaux !"
                        ),
                        LocalizedText(
                            "يتحول البسط إلى \$$d(\\sqrt{$r1} - $c) + \\sqrt{$r1}(\\sqrt[3]{$r2} - $d)\$.",
                            "Le numérateur s'écrit \$$d(\\sqrt{$r1} - $c) + \\sqrt{$r1}(\\sqrt[3]{$r2} - $d)\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("إضافة وطرح ${d}√(...)", "Ajout et retranchement du terme mixte"),
                            LocalizedText(
                                "نضيف ونطرح \$${d}\\sqrt{$r1}\$ لنعزل كل جذر:",
                                "On ajoute et on retranche \$${d}\\sqrt{$r1}\$ :"
                            ),
                            "\\frac{${if (d == 1) "" else "$d"}(\\sqrt{$r1} - $c)}{$den} + \\sqrt{$r1} \\cdot \\frac{\\sqrt[3]{$r2} - $d}{$den}"
                        ),
                        SolutionStep(
                            LocalizedText("تطبيق المرافقين والجمع", "Limites et somme"),
                            LocalizedText("نطبق المرافق التربيعي للأول والتكعيبي للثاني:", "On obtient :"),
                            "${Rational.of(d * m, 2 * c * k).toLatex()} + ${Rational.of(c * n, 3 * d * d * k).toLatex()} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                // (sqrt(1 + m x^2) * cos(b x) - 1) / (k x^2)
                val m = rng.pickNonZero(-4, 6)
                val b = rng.range(1, 4)
                val k = rng.pick(listOf(1, 2, 3))
                val radInner = fmtPoly(m, 0, 1)
                val den = if (k == 1) "x^2" else "${k}x^2"
                val ans = Rational.of(m - b * b, 2 * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "add_sub_zero",
                    difficulty = 3,
                    fullLimitLatex = limPoint("0", "\\frac{\\sqrt{$radInner}\\cos(${if (b == 1) "" else "$b"}x) - 1}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "امزج تقنية إضافة وطرح الصفر مع المثلثات! أضف واطرح \$\\cos(${if (b == 1) "" else "$b"}x)\$ في البسط.",
                            "Ajoute et retranche \$\\cos(${if (b == 1) "" else "$b"}x)\$ au numérateur !"
                        ),
                        LocalizedText(
                            "تتفرع النهاية إلى: \$\\cos(${if (b == 1) "" else "$b"}x)\\frac{\\sqrt{$radInner} - 1}{$den} - \\frac{1 - \\cos(${if (b == 1) "" else "$b"}x)}{$den}\$.",
                            "La fraction se sépare en deux limites classiques : un conjugué et \$\\frac{1 - \\cos(bx)}{x^2}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("إضافة وطرح cos(${if (b == 1) "" else "$b"}x)", "Ajout et retranchement de cos"),
                            LocalizedText("نفصل الجذر عن دالة التجيب:", "On sépare le radical et le cosinus :"),
                            "\\cos(${if (b == 1) "" else "$b"}x) \\cdot \\frac{\\sqrt{$radInner} - 1}{$den} - \\frac{1 - \\cos(${if (b == 1) "" else "$b"}x)}{$den}"
                        ),
                        SolutionStep(
                            LocalizedText("حساب النهايتين", "Calcul final"),
                            LocalizedText(
                                "لدينا \$\\frac{1-\\cos(bx)}{x^2} \\to \\frac{b^2}{2}\$ وبالمرافق \$\\frac{\\sqrt{1+mx^2}-1}{x^2} \\to \\frac{m}{2}\$:",
                                "On utilise les limites usuelles :"
                            ),
                            "1 \\cdot ${Rational.of(m, 2 * k).toLatex()} - ${Rational.of(b * b, 2 * k).toLatex()} = ${ans.toLatex()}"
                        )
                    )
                )
            }
        }
    }

    // ---------- 5. TRIG ----------

    private fun genTrig(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        return when (diff) {
            1 -> if (rng.nextInt(2) == 0) {
                val a = rng.range(1, 6)
                val b = rng.range(1, 6)
                val p = rng.pick(listOf(1, 2, 3, -1, -2))
                val q = rng.pick(listOf(1, 2, 3, -1, -2))
                val k = rng.pick(listOf(1, 2, 3, -1))
                val term1 = fmtSignedTerm(p, "\\sin(${if (a == 1) "" else "$a"}x)", first = true)
                val term2 = fmtSignedTerm(q, "\\tan(${if (b == 1) "" else "$b"}x)")
                val den = fmtPoly(k, 0)
                val ans = Rational.of(p * a + q * b, k)

                GeneratedExercise(
                    seed = seed,
                    technique = "trig",
                    difficulty = 1,
                    fullLimitLatex = limPoint("0", "\\frac{$term1$term2}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "استعمل النهايتين الاعتياديتين بجوار 0: \$\\lim_{u \\to 0} \\frac{\\sin u}{u} = 1\$ و \$\\lim_{u \\to 0} \\frac{\\tan u}{u} = 1\$.",
                            "Utilise les limites usuelles en 0 : \$\\frac{\\sin u}{u} \\to 1\$ et \$\\frac{\\tan u}{u} \\to 1\$."
                        ),
                        LocalizedText(
                            "اقسم كل حد على \$x\$ ورتّب المعاملات: \$\\frac{\\sin(${a}x)}{x} = $a \\cdot \\frac{\\sin(${a}x)}{${a}x}\$.",
                            "Sépare la fraction et ajuste chaque argument : \$\\frac{\\sin(${a}x)}{x} = $a \\cdot \\frac{\\sin(${a}x)}{${a}x}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("الربط بالنهايات الاعتيادية", "Mise en forme"),
                            LocalizedText("نفصل الكسر ونضبط الزوايا:", "On sépare en deux fractions usuelles :"),
                            "\\frac{1}{$k}\\left( $p\\cdot $a \\frac{\\sin(${if (a == 1) "" else "$a"}x)}{${if (a == 1) "" else "$a"}x} + ($q)\\cdot $b \\frac{\\tan(${if (b == 1) "" else "$b"}x)}{${if (b == 1) "" else "$b"}x} \\right)"
                        ),
                        SolutionStep(
                            LocalizedText("النتيجة", "Résultat"),
                            LocalizedText("بما أن كل نسبة تؤول إلى 1:", "Comme chaque quotient tend vers 1 :"),
                            "\\frac{${p * a} + (${q * b})}{$k} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val a = rng.range(1, 6)
                val k = rng.pick(listOf(1, 2, 3, -1, -2))
                val den = fmtPoly(k, 0, 0)
                val ans = Rational.of(a * a, 2 * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "trig",
                    difficulty = 1,
                    fullLimitLatex = limPoint("0", "\\frac{1 - \\cos(${if (a == 1) "" else "$a"}x)}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "استعمل النهاية الاعتيادية: \$\\lim_{u \\to 0} \\frac{1 - \\cos u}{u^2} = \\frac{1}{2}\$.",
                            "Rappel : \$\\lim_{u \\to 0} \\frac{1 - \\cos u}{u^2} = \\frac{1}{2}\$."
                        ),
                        LocalizedText(
                            "اضرب واقسم في \$${a * a}\$ لتحويل \$x^2\$ إلى \$(${a}x)^2\$.",
                            "Fais apparaître \$(${a}x)^2 = ${a * a}x^2\$ au dénominateur."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تعديل المقام ليطابق الزاوية", "Changement d'échelle"),
                            LocalizedText("نضبط المربع ليطابق \$${a}x\$:", "On ajuste l'argument carré :"),
                            "\\frac{${a * a}}{$k} \\cdot \\frac{1 - \\cos(${if (a == 1) "" else "$a"}x)}{(${if (a == 1) "" else "$a"}x)^2} \\to \\frac{${a * a}}{$k} \\cdot \\frac{1}{2} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            2 -> if (rng.nextInt(2) == 0) {
                val a = rng.range(1, 6)
                var b = rng.range(1, 6)
                while (b == a) b = rng.range(1, 6)
                val k = rng.pick(listOf(1, 2, 3))
                val den = fmtPoly(k, 0, 0)
                val ans = Rational.of(b * b - a * a, 2 * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "trig",
                    difficulty = 2,
                    fullLimitLatex = limPoint("0", "\\frac{\\cos(${if (a == 1) "" else "$a"}x) - \\cos(${if (b == 1) "" else "$b"}x)}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "أضف واطرح 1 في البسط: \$\\cos(ax) - \\cos(bx) = (1 - \\cos(bx)) - (1 - \\cos(ax))\$.",
                            "Ajoute et retranche 1 : \$\\cos(ax) - \\cos(bx) = (1 - \\cos(bx)) - (1 - \\cos(ax))\$."
                        ),
                        LocalizedText(
                            "طبق النهاية الاعتيادية \$\\frac{1 - \\cos(ux)}{x^2} \\to \\frac{u^2}{2}\$ على كل طرف.",
                            "Applique \$\\frac{1 - \\cos(ux)}{x^2} \\to \\frac{u^2}{2}\$ à chaque terme."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("إضافة وطرح 1", "Séparation via ±1"),
                            LocalizedText("نكتب الفرق باستعمال \$(1 - \\cos)\$:", "On réécrit le numérateur :"),
                            "\\frac{(1 - \\cos(${if (b == 1) "" else "$b"}x)) - (1 - \\cos(${if (a == 1) "" else "$a"}x))}{$den}"
                        ),
                        SolutionStep(
                            LocalizedText("النهاية النهائية", "Calcul final"),
                            LocalizedText("نطبق القاعدة عند 0:", "En utilisant la limite usuelle en 0 :"),
                            "${Rational.of(b * b, 2 * k).toLatex()} - ${Rational.of(a * a, 2 * k).toLatex()} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val n = rng.range(1, 5)
                val k = rng.pick(listOf(1, 2, 3, -1))
                val sign = if (n % 2 == 0) 1 else -1
                val ans = Rational.of(sign * n, k)
                val denLatex = when (k) {
                    1 -> "x - \\pi"
                    -1 -> "\\pi - x"
                    else -> "$k(x - \\pi)"
                }

                GeneratedExercise(
                    seed = seed,
                    technique = "trig",
                    difficulty = 2,
                    fullLimitLatex = limPoint("\\pi", "\\frac{\\sin(${if (n == 1) "" else "$n"}x)}{$denLatex}"),
                    hints = listOf(
                        LocalizedText(
                            "النهاية بجوار \$\\pi\$ وليست بجوار 0! قم بتغيير المتغير \$t = x - \\pi\$.",
                            "La limite est en \$\\pi\$ et non en 0 ! Pose \$t = x - \\pi\$."
                        ),
                        LocalizedText(
                            "تذكر أن \$\\sin(n\\pi + nt) = (-1)^n \\sin(nt)\$.",
                            "Rappelle-toi que \$\\sin(n\\pi + nt) = (-1)^n \\sin(nt)\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تغيير المتغير t = x - π", "Changement de variable t = x - π"),
                            LocalizedText("نضع \$t = x - \\pi\$ فتؤول \$t \\to 0\$ و \$x = \\pi + t\$:", "On pose \$t = x - \\pi \\to 0\$ :"),
                            "\\sin(${n}(\\pi + t)) = \\sin(${n}\\pi + ${n}t) = ${if (sign < 0) "-" else ""}\\sin(${if (n == 1) "" else "$n"}t)"
                        ),
                        SolutionStep(
                            LocalizedText("النهاية عند t → 0", "Limite finale"),
                            LocalizedText("نعوض في الكسر:", "On conclut :"),
                            "\\lim_{t \\to 0} \\frac{${if (sign < 0) "-" else ""}\\sin(${if (n == 1) "" else "$n"}t)}{${if (k == 1) "t" else "${k}t"}} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            else -> if (rng.nextInt(2) == 0) {
                val a = rng.range(1, 4)
                val k = rng.pick(listOf(1, 2, 3, -1))
                val aStr = if (a == 1) "" else "$a"
                val den = if (k == 1) "x^3" else "${k}x^3"
                val ans = Rational.of(a * a * a, 2 * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "trig",
                    difficulty = 3,
                    fullLimitLatex = limPoint("0", "\\frac{\\tan(${aStr}x) - \\sin(${aStr}x)}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "المقام من الدرجة 3 (\$x^3\$)! تحويل \$\\tan u = \\frac{\\sin u}{\\cos u}\$ وتعميل \$\\sin u\$ هو المفتاح.",
                            "Le dénominateur est de degré 3 (\$x^3\$) ! Écris \$\\tan u = \\frac{\\sin u}{\\cos u}\$ et factorise par \$\\sin u\$."
                        ),
                        LocalizedText(
                            "ستحصل على \$\\sin(${aStr}x)\\cdot \\frac{1 - \\cos(${aStr}x)}{\\cos(${aStr}x)}\$.",
                            "On obtient \$\\sin(${aStr}x) \\cdot \\frac{1 - \\cos(${aStr}x)}{\\cos(${aStr}x)}\$."
                        ),
                        LocalizedText(
                            "وزّع \$x^3 = x \\cdot x^2\$ بين \$\\sin(${aStr}x)\$ و \$1 - \\cos(${aStr}x)\$.",
                            "Répartis \$x^3 = x \\cdot x^2\$ sous \$\\sin\$ et sous \$(1 - \\cos)\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("التعميل بـ sin(${aStr}x)", "Factorisation trigonométrique"),
                            LocalizedText("نعوّض \$\\tan = \\frac{\\sin}{\\cos}\$ ونعمّل:", "On factorise par \$\\sin(${aStr}x)\$ :"),
                            "\\tan(${aStr}x) - \\sin(${aStr}x) = \\sin(${aStr}x)\\left(\\frac{1}{\\cos(${aStr}x)} - 1\\right) = \\frac{\\sin(${aStr}x)(1 - \\cos(${aStr}x))}{\\cos(${aStr}x)}"
                        ),
                        SolutionStep(
                            LocalizedText("توزيع قوى x وحساب النهاية", "Calcul final"),
                            LocalizedText("نقسم على \$$den\$ ونرتب الحدود:", "En répartissant \$x^3 = x \\cdot x^2\$ :"),
                            "\\frac{1}{$k\\cos(${aStr}x)} \\cdot \\frac{\\sin(${aStr}x)}{x} \\cdot \\frac{1 - \\cos(${aStr}x)}{x^2} \\to \\frac{1}{$k \\cdot 1} \\cdot $a \\cdot \\frac{${a * a}}{2} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                // Exact radical trig value at pi/4 or pi/3
                if (rng.nextInt(2) == 0) {
                    val k = rng.pick(listOf(1, 2, 3, -1))
                    val denLatex = when (k) {
                        1 -> "x - \\frac{\\pi}{4}"
                        -1 -> "\\frac{\\pi}{4} - x"
                        else -> "$k\\left(x - \\frac{\\pi}{4}\\right)"
                    }
                    val ansLatex = when (k) {
                        1 -> "\\sqrt{2}"
                        -1 -> "-\\sqrt{2}"
                        else -> "\\frac{\\sqrt{2}}{$k}"
                    }
                    val valDouble = kotlin.math.sqrt(2.0) / k

                    GeneratedExercise(
                        seed = seed,
                        technique = "trig",
                        difficulty = 3,
                        fullLimitLatex = limPoint("\\frac{\\pi}{4}", "\\frac{\\sin x - \\cos x}{$denLatex}"),
                        hints = listOf(
                            LocalizedText(
                                "عند \$x = \\frac{\\pi}{4}\$ لدينا \$\\sin\\frac{\\pi}{4} = \\cos\\frac{\\pi}{4} = \\frac{\\sqrt{2}}{2}\$.",
                                "En \$\\frac{\\pi}{4}\$, \$\\sin x = \\cos x\$ : forme \$\\frac{0}{0}\$."
                            ),
                            LocalizedText(
                                "استعمل الصيغة \$\\sin x - \\cos x = \\sqrt{2}\\sin\\left(x - \\frac{\\pi}{4}\\right)\$ أو ضع \$t = x - \\frac{\\pi}{4}\$.",
                                "Utilise \$\\sin x - \\cos x = \\sqrt{2}\\sin\\left(x - \\frac{\\pi}{4}\\right)\$ ou pose \$t = x - \\frac{\\pi}{4}\$."
                            )
                        ),
                        answerLatex = ansLatex,
                        expectedAnswer = ExpectedAnswer.Finite(valDouble),
                        steps = listOf(
                            SolutionStep(
                                LocalizedText("تحويل المجموع المثلثي", "Transformation trigonométrique"),
                                LocalizedText(
                                    "باستعمال صيغة الطرح \$\\sin(a-b)\$ نجد:",
                                    "On utilise la formule d'addition :"
                                ),
                                "\\sin x - \\cos x = \\sqrt{2}\\left(\\frac{\\sqrt{2}}{2}\\sin x - \\frac{\\sqrt{2}}{2}\\cos x\\right) = \\sqrt{2}\\sin\\left(x - \\frac{\\pi}{4}\\right)"
                            ),
                            SolutionStep(
                                LocalizedText("تغيير المتغير t = x - π/4", "Limite finale"),
                                LocalizedText("نضع \$t = x - \\frac{\\pi}{4} \\to 0\$:", "En posant \$t = x - \\frac{\\pi}{4} \\to 0\$ :"),
                                "\\lim_{t \\to 0} \\frac{\\sqrt{2}\\sin t}{${if (k == 1) "t" else "${k}t"}} = $ansLatex"
                            )
                        )
                    )
                } else {
                    val k = rng.pick(listOf(1, 2, 3, -1))
                    val denLatex = when (k) {
                        1 -> "x - \\frac{\\pi}{3}"
                        -1 -> "\\frac{\\pi}{3} - x"
                        else -> "$k\\left(x - \\frac{\\pi}{3}\\right)"
                    }
                    val negK = -k
                    val ansLatex = when (negK) {
                        1 -> "\\sqrt{3}"
                        -1 -> "-\\sqrt{3}"
                        else -> if (negK < 0) "-\\frac{\\sqrt{3}}{${-negK}}" else "\\frac{\\sqrt{3}}{$negK}"
                    }
                    val valDouble = -kotlin.math.sqrt(3.0) / k

                    GeneratedExercise(
                        seed = seed,
                        technique = "trig",
                        difficulty = 3,
                        fullLimitLatex = limPoint("\\frac{\\pi}{3}", "\\frac{2\\cos x - 1}{$denLatex}"),
                        hints = listOf(
                            LocalizedText(
                                "عند \$x = \\frac{\\pi}{3}\$ نعلم أن \$\\cos\\frac{\\pi}{3} = \\frac{1}{2}\$.",
                                "Comme \$\\cos\\frac{\\pi}{3} = \\frac{1}{2}\$, c'est une forme \$\\frac{0}{0}\$."
                            ),
                            LocalizedText(
                                "ضع \$t = x - \\frac{\\pi}{3}\$ ثم انشر \$\\cos\\left(t + \\frac{\\pi}{3}\\right) = \\frac{1}{2}\\cos t - \\frac{\\sqrt{3}}{2}\\sin t\$.",
                                "Pose \$t = x - \\frac{\\pi}{3}\$ et développe \$\\cos\\left(t + \\frac{\\pi}{3}\\right)\$."
                            )
                        ),
                        answerLatex = ansLatex,
                        expectedAnswer = ExpectedAnswer.Finite(valDouble),
                        steps = listOf(
                            SolutionStep(
                                LocalizedText("تغيير المتغير ونشر cos(t + π/3)", "Changement de variable t = x - π/3"),
                                LocalizedText("نضع \$x = \\frac{\\pi}{3} + t\$ مع \$t \\to 0\$:", "Pour \$t = x - \\frac{\\pi}{3} \\to 0\$ :"),
                                "2\\cos\\left(\\frac{\\pi}{3} + t\\right) - 1 = 2\\left(\\frac{1}{2}\\cos t - \\frac{\\sqrt{3}}{2}\\sin t\\right) - 1 = (\\cos t - 1) - \\sqrt{3}\\sin t"
                            ),
                            SolutionStep(
                                LocalizedText("القسمة على t وحساب النهاية", "Calcul final"),
                                LocalizedText("نعلم أن \$\\frac{\\cos t - 1}{t} \\to 0\$ و \$\\frac{\\sin t}{t} \\to 1\$:", "Comme \$\\frac{\\cos t - 1}{t} \\to 0\$ et \$\\frac{\\sin t}{t} \\to 1\$ :"),
                                "\\lim_{t \\to 0} \\frac{1}{$k}\\left(\\frac{\\cos t - 1}{t} - \\sqrt{3}\\frac{\\sin t}{t}\\right) = $ansLatex"
                            )
                        )
                    )
                }
            }
        }
    }

    // ---------- 6. HYBRID ----------

    private fun genHybrid(seed: String, diff: Int, rng: SplitMix64): GeneratedExercise {
        return when (diff) {
            1 -> if (rng.nextInt(2) == 0) {
                val a = rng.pickNonZero(-3, 3)
                val c = rng.range(1, 4)
                val m = rng.pick(listOf(1, 2, 3, 4, -1, -2))
                val p = c * c - m * a
                val rad = fmtLinear(m, p)
                val den = fmtPoly(1, 0, -a * a)
                val ans = Rational.of(m, 4 * a * c)

                GeneratedExercise(
                    seed = seed,
                    technique = "hybrid",
                    difficulty = 1,
                    fullLimitLatex = limPoint("$a", "\\frac{\\sqrt{$rad} - $c}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "تحتاج إلى تقنيتين: المرافق التربيعي في البسط وتعميل المتطابقة \$x^2 - a^2\$ في المقام.",
                            "Combine deux techniques : conjugué au numérateur et identité \$x^2 - a^2\$ au dénominateur."
                        ),
                        LocalizedText(
                            "يختزل العامل \$${fmtFactor(a)}\$ وتبقى \$$c \\cdot 2\$ مع \$(${2 * a})\$ في المقام.",
                            "Simplifie par \$${fmtFactor(a)}\$ et remplace \$x = $a\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("المرافق + تعميل المقام", "Conjugué + factorisation"),
                            LocalizedText("نضرب في المرافق ونفكك \$x^2 - ${a * a}\$:", "On combine les deux :"),
                            "\\frac{${fmtScaledFactor(m, a)}}{${fmtFactor(a)}${fmtFactor(-a)}(\\sqrt{$rad} + $c)} = \\frac{$m}{${fmtFactor(-a)}(\\sqrt{$rad} + $c)}"
                        ),
                        SolutionStep(
                            LocalizedText("النتيجة", "Résultat"),
                            LocalizedText("نعوض \$x = $a\$:", "En \$x = $a\$ :"),
                            "\\frac{$m}{${2 * a} \\cdot ${2 * c}} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val a = rng.range(1, 5)
                val b = rng.pickNonZero(-3, 4)
                val c = rng.range(1, 4)
                val rad = fmtLinear(b, c * c)
                val ans = Rational.of(a, c)

                GeneratedExercise(
                    seed = seed,
                    technique = "hybrid",
                    difficulty = 1,
                    fullLimitLatex = limPoint("0", "\\frac{\\sin(${if (a == 1) "" else "$a"}x)}{x\\sqrt{$rad}}"),
                    hints = listOf(
                        LocalizedText(
                            "افصل النسبة المثلثية \$\\frac{\\sin(${if (a == 1) "" else "$a"}x)}{x}\$ عن \$\\frac{1}{\\sqrt{$rad}}\$.",
                            "Sépare le facteur trigonométrique \$\\frac{\\sin(${if (a == 1) "" else "$a"}x)}{x}\$ de \$\\frac{1}{\\sqrt{$rad}}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تفكيك الجداء", "Séparation"),
                            LocalizedText("نفصل الكسر عند \$x \\to 0\$:", "On sépare les facteurs :"),
                            "\\frac{\\sin(${if (a == 1) "" else "$a"}x)}{x} \\cdot \\frac{1}{\\sqrt{$rad}} \\to $a \\cdot \\frac{1}{$c} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            2 -> if (rng.nextInt(2) == 0) {
                val a = rng.range(1, 5)
                val b = rng.pickNonZero(-4, 4)
                val c = rng.range(1, 4)
                val rad = fmtLinear(b, c * c)
                val ans = Rational.of(a * a * c, b)

                GeneratedExercise(
                    seed = seed,
                    technique = "hybrid",
                    difficulty = 2,
                    fullLimitLatex = limPoint("0", "\\frac{1 - \\cos(${if (a == 1) "" else "$a"}x)}{x(\\sqrt{$rad} - $c)}"),
                    hints = listOf(
                        LocalizedText(
                            "تخلص من الجذر في المقام بالضرب في مرافقه \$(\\sqrt{$rad} + $c)\$.",
                            "Supprime la racine au dénominateur en multipliant par \$(\\sqrt{$rad} + $c)\$."
                        ),
                        LocalizedText(
                            "يصبح المقام \$${b}x^2\$، مما يفتح المجال لاستعمال النهاية \$\\frac{1 - \\cos(${if (a == 1) "" else "$a"}x)}{x^2} \\to \\frac{${a * a}}{2}\$!",
                            "Le dénominateur devient \$${b}x^2\$, ce qui fait apparaître \$\\frac{1 - \\cos(${if (a == 1) "" else "$a"}x)}{x^2}\$ !"
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("مرافق المقام", "Conjugué du dénominateur"),
                            LocalizedText("نضرب في \$\\sqrt{$rad} + $c\$:", "On multiplie haut et bas par \$\\sqrt{$rad} + $c\$ :"),
                            "\\frac{(1 - \\cos(${if (a == 1) "" else "$a"}x))(\\sqrt{$rad} + $c)}{x(${b}x)} = \\frac{1 - \\cos(${if (a == 1) "" else "$a"}x)}{x^2} \\cdot \\frac{\\sqrt{$rad} + $c}{$b}"
                        ),
                        SolutionStep(
                            LocalizedText("النهاية النهائية", "Calcul final"),
                            LocalizedText("نحسب جداء النهايتين:", "Produit des limites :"),
                            "${Rational.of(a * a, 2).toLatex()} \\cdot ${Rational.of(2 * c, b).toLatex()} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val m = rng.pickNonZero(-4, 5)
                val b = rng.range(1, 4)
                val k = rng.pick(listOf(1, 2, 3))
                val rad = fmtPoly(m, 0, 1)
                val den = if (k == 1) "x^2" else "${k}x^2"
                val ans = Rational.of(m + b * b, 2 * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "hybrid",
                    difficulty = 2,
                    fullLimitLatex = limPoint("0", "\\frac{\\sqrt{$rad} - \\cos(${if (b == 1) "" else "$b"}x)}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "اجمع بين إضافة وطرح 1 والمرافق والمثلثات!",
                            "Utilise l'ajout/retranchement de 1 avec conjugué et limite trigonométrique !"
                        ),
                        LocalizedText(
                            "افصل الكسر إلى \$\\frac{\\sqrt{$rad} - 1}{$den} + \\frac{1 - \\cos(${if (b == 1) "" else "$b"}x)}{$den}\$.",
                            "Sépare en \$\\frac{\\sqrt{$rad} - 1}{$den} + \\frac{1 - \\cos(${if (b == 1) "" else "$b"}x)}{$den}\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("إضافة وطرح 1", "Ajout et retranchement de 1"),
                            LocalizedText("نفصل الجذر عن التجيب بإضافة وطرح 1:", "On sépare via ±1 :"),
                            "\\frac{\\sqrt{$rad} - 1}{$den} + \\frac{1 - \\cos(${if (b == 1) "" else "$b"}x)}{$den}"
                        ),
                        SolutionStep(
                            LocalizedText("النتيجة", "Calcul final"),
                            LocalizedText("نحسب كل طرف:", "Somme des deux limites :"),
                            "${Rational.of(m, 2 * k).toLatex()} + ${Rational.of(b * b, 2 * k).toLatex()} = ${ans.toLatex()}"
                        )
                    )
                )
            }

            else -> if (rng.nextInt(2) == 0) {
                val a = rng.pickNonZero(-5, 6)
                val b = rng.range(1, 4)
                val k = rng.pick(listOf(1, 2, 3))
                val rad = fmtPoly(a, 0, 1)
                val den = if (k == 1) "x^2" else "${k}x^2"
                val ans = Rational.of(2 * a + 3 * b * b, 6 * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "hybrid",
                    difficulty = 3,
                    fullLimitLatex = limPoint("0", "\\frac{\\sqrt[3]{$rad} - \\cos(${if (b == 1) "" else "$b"}x)}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "أضف واطرح 1 لفصل الجذر التكعيبي عن \$\\cos(${if (b == 1) "" else "$b"}x)\$!",
                            "Ajoute et retranche 1 pour isoler la racine cubique et le cosinus !"
                        ),
                        LocalizedText(
                            "استعمل المرافق التكعيبي في الكسر الأول والنهاية المثلثية \$\\frac{1-\\cos(bx)}{x^2}\$ في الثاني.",
                            "Applique le conjugué cubique au 1er terme et la limite trigonométrique au 2nd."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("تفكيك بإضافة وطرح 1", "Séparation par ±1"),
                            LocalizedText("نفكك البسط إلى نهايتين مألوفتين:", "On décompose le numérateur :"),
                            "\\frac{\\sqrt[3]{$rad} - 1}{$den} + \\frac{1 - \\cos(${if (b == 1) "" else "$b"}x)}{$den}"
                        ),
                        SolutionStep(
                            LocalizedText("المرافق التكعيبي + المثلثات", "Calcul des deux termes"),
                            LocalizedText("الحد الأول يؤول إلى \$\\frac{$a}{${3 * k}}\$ والثاني إلى \$\\frac{${b * b}}{${2 * k}}\$:", "D'où :"),
                            "${Rational.of(a, 3 * k).toLatex()} + ${Rational.of(b * b, 2 * k).toLatex()} = ${ans.toLatex()}"
                        )
                    )
                )
            } else {
                val a = rng.pickNonZero(-4, 5)
                var b = rng.pickNonZero(-4, 5)
                while (b == a) b = rng.pickNonZero(-4, 5)
                val k = rng.pick(listOf(1, 2, 3))
                val aStr = if (a == 1) "" else if (a == -1) "-" else "$a"
                val bStr = if (b == 1) "" else if (b == -1) "-" else "$b"
                val r1 = "1 + \\tan(${aStr}x)"
                val r2 = "1 + \\sin(${bStr}x)"
                val den = fmtPoly(k, 0)
                val ans = Rational.of(a - b, 2 * k)

                GeneratedExercise(
                    seed = seed,
                    technique = "hybrid",
                    difficulty = 3,
                    fullLimitLatex = limPoint("0", "\\frac{\\sqrt{$r1} - \\sqrt{$r2}}{$den}"),
                    hints = listOf(
                        LocalizedText(
                            "اضرب في مرافق فرق الجذرين المربعين أولاً!",
                            "Commence par multiplier par le conjugué de la différence des deux racines !"
                        ),
                        LocalizedText(
                            "بعد المرافق سيبقى في البسط \$\\tan(${aStr}x) - \\sin(${bStr}x)\$، ثم اقسم على \$x\$!",
                            "Après conjugué, il reste \$\\tan(${aStr}x) - \\sin(${bStr}x)\$ sur \$$den\$."
                        )
                    ),
                    answerLatex = ans.toLatex(),
                    expectedAnswer = ExpectedAnswer.Finite(ans.toDouble()),
                    steps = listOf(
                        SolutionStep(
                            LocalizedText("الضرب في المرافق", "Conjugué"),
                            LocalizedText("نضرب بسطاً ومقاماً في \$\\sqrt{$r1} + \\sqrt{$r2}\$:", "On applique le conjugué :"),
                            "\\frac{\\tan(${aStr}x) - \\sin(${bStr}x)}{$den(\\sqrt{$r1} + \\sqrt{$r2})}"
                        ),
                        SolutionStep(
                            LocalizedText("النهايات المثلثية وحساب الناتج", "Limites trigonométriques"),
                            LocalizedText("نفصل \$\\frac{\\tan(${aStr}x)}{x} \\to $a\$ و \$\\frac{\\sin(${bStr}x)}{x} \\to $b\$:", "On passe à la limite en 0 :"),
                            "\\frac{($a) - ($b)}{$k(1 + 1)} = ${ans.toLatex()}"
                        )
                    )
                )
            }
        }
    }
}
