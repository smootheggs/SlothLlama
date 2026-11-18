package app.localmind.assistant.domain.tools

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Exact arithmetic for the assistant, so sums are computed rather than guessed: + − × ÷ ^,
 * brackets, percentages, n!, square/cube roots, logs and trigonometry (in degrees), π and e.
 * [find] picks the calculation out of a message ("what's 18% of 2,450?", "12 times 7.5").
 */
object Calculator {

    class Result(val expression: String, val value: Double) {
        val formatted: String get() = format(value)
    }

    /** The calculation in [message], or null if there's none worth doing. */
    fun find(message: String): Result? {
        val text = normalize(message)
        percentOf(text)?.let { return it }
        val cue = MATH_CUE.containsMatchIn(message)
        return CANDIDATE.findAll(text)
            .map { it.value.trim().trimEnd('=', '?', ' ') }
            .filter { looksLikeMath(it, cue) }
            .sortedByDescending { it.length }
            .firstNotNullOfOrNull { expr -> runCatching { Result(pretty(expr), evaluate(expr)) }.getOrNull()?.takeIf { it.value.isFinite() } }
    }

    /** Evaluates [expression]; throws on anything it can't read. */
    fun evaluate(expression: String): Double = Parser(expression.replace(" ", "")).parseAll()

    fun format(value: Double): String {
        if (value == floor(value) && abs(value) < 1e15) return "%,d".format(Locale.US, value.toLong())
        val abs = abs(value)
        if (abs >= 1e15 || abs < 1e-6) return "%.6g".format(Locale.US, value)
        // Up to 10 significant digits, without trailing zeros.
        val decimals = (9 - floor(log10(abs)).toInt()).coerceIn(0, 12)
        val s = "%,.${decimals}f".format(Locale.US, value)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }

    // --- reading the message ---

    private val MATH_CUE = Regex(
        "(?i)\\b(calculate|calc|compute|what(?:'s| is)|how much is|solve|equals?|plus|minus|times|divided|multiplied|squared|cubed|root|percent)\\b|="
    )

    /** Runs of calculator characters (digits, operators, brackets, function names). */
    private val CANDIDATE = Regex(
        "(?:(?:\\d+(?:\\.\\d+)?|(?<![a-z])(?:sqrt|cbrt|abs|ln|log|sin|cos|tan|round|pi)(?![a-z])|π|(?<![a-z])e(?![a-z])|[-+*/^%!()])\\s*)+"
    )

    private val PERCENT_OF = Regex("(\\d+(?:\\.\\d+)?)\\s*%\\s*of\\s*(\\d+(?:\\.\\d+)?)")

    private fun percentOf(text: String): Result? {
        val m = PERCENT_OF.find(text) ?: return null
        val p = m.groupValues[1].toDouble()
        val of = m.groupValues[2].toDouble()
        return Result("${format(p)}% of ${format(of)}", p / 100 * of)
    }

    private fun looksLikeMath(expr: String, cue: Boolean): Boolean {
        val numbers = Regex("\\d+(?:\\.\\d+)?").findAll(expr).count()
        val hasFunction = Regex("sqrt|cbrt|abs|ln|log|sin|cos|tan").containsMatchIn(expr)
        val strongOp = Regex("[+*^%!]").containsMatchIn(expr)
        val weakOp = Regex("\\d\\s*[-/]\\s*[\\d(]").containsMatchIn(expr)
        // Letters left over mean words, not a formula ("e" and "pi" are fine).
        if (Regex("[a-z]").containsMatchIn(expr.replace(Regex("sqrt|cbrt|abs|ln|log|sin|cos|tan|round|pi|\\be\\b"), ""))) return false
        if (DATE_LIKE.matches(expr.trim())) return false
        return when {
            hasFunction && numbers >= 1 -> true
            numbers < 2 -> expr.contains('!') && numbers == 1 && cue
            strongOp -> true
            // "5-10" or "24/7" alone are ranges and times; with spaces or a cue word they're sums.
            weakOp -> cue || Regex("\\d\\s+[-/]\\s+[\\d(]").containsMatchIn(expr)
            else -> false
        }
    }

    private val DATE_LIKE = Regex("\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}-\\d{1,2}-\\d{2,4}")

    /** Lower-case, words to symbols, "1,250.5" → "1250.5", × ÷ − → * / -. */
    private fun normalize(message: String): String {
        var t = message.lowercase()
            .replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-').replace('·', '*')
            .replace(Regex("(?<=\\d),(?=\\d{3}\\b)"), "")
        WORDS.forEach { (pattern, symbol) -> t = t.replace(pattern, symbol) }
        // "3 x 4" (a letter x between numbers) is multiplication.
        t = t.replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*")
        return t
    }

    private val WORDS = listOf(
        Regex("\\bsquare root of\\s+") to "sqrt ",
        Regex("\\bcube root of\\s+") to "cbrt ",
        Regex("\\s+to the power of\\s+") to "^",
        Regex("\\s+multiplied by\\s+") to "*",
        Regex("\\s+divided by\\s+") to "/",
        Regex("\\s+over\\s+(?=\\d)") to "/",
        Regex("\\s+times\\s+") to "*",
        Regex("\\s+plus\\s+") to "+",
        Regex("\\s+minus\\s+") to "-",
        Regex("\\s+squared\\b") to "^2",
        Regex("\\s+cubed\\b") to "^3",
        Regex("(?<=\\d)\\s*percent\\b") to "%",
        Regex("\\bsqrt\\s+(\\d+(?:\\.\\d+)?)") to "sqrt($1)",
        Regex("\\bcbrt\\s+(\\d+(?:\\.\\d+)?)") to "cbrt($1)"
    )

    private fun pretty(expr: String): String =
        expr.replace("*", " × ").replace("/", " ÷ ").replace("+", " + ")
            .replace(Regex("(?<=[\\d)])-"), " − ")
            .replace(Regex("\\s+"), " ").trim()

    // --- evaluating ---

    private class Parser(val s: String) {
        var pos = 0

        fun parseAll(): Double {
            val v = sum()
            require(pos == s.length) { "Unexpected '${s.getOrNull(pos)}'" }
            return v
        }

        fun sum(): Double {
            var v = product()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val r = product()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun product(): Double {
            var v = power()
            while (pos < s.length) {
                val c = s[pos]
                v = when {
                    c == '*' -> { pos++; v * power() }
                    c == '/' -> { pos++; v / power() }
                    // Implicit multiplication: 2(3+4), 2pi, (1+2)(3+4).
                    c == '(' || c.isLetter() || c == 'π' -> v * power()
                    else -> return v
                }
            }
            return v
        }

        fun power(): Double {
            val base = unary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                // Right-associative: 2^3^2 = 2^9.
                return base.pow(power())
            }
            return base
        }

        fun unary(): Double {
            if (pos < s.length && s[pos] == '-') { pos++; return -unary() }
            if (pos < s.length && s[pos] == '+') { pos++; return unary() }
            return postfix(atom())
        }

        fun postfix(value: Double): Double {
            var v = value
            while (pos < s.length) {
                when (s[pos]) {
                    '%' -> { pos++; v /= 100 }
                    '!' -> {
                        pos++
                        require(v >= 0 && v == floor(v) && v <= 170) { "Factorial needs a whole number up to 170" }
                        var f = 1.0
                        for (i in 2..v.toInt()) f *= i
                        v = f
                    }
                    else -> return v
                }
            }
            return v
        }

        fun atom(): Double {
            require(pos < s.length) { "Unexpected end" }
            val c = s[pos]
            if (c == '(') {
                pos++
                val v = sum()
                require(pos < s.length && s[pos] == ')') { "Missing )" }
                pos++
                return v
            }
            if (c.isDigit() || c == '.') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                return s.substring(start, pos).toDouble()
            }
            if (c == 'π') { pos++; return Math.PI }
            val start = pos
            while (pos < s.length && s[pos].isLetter()) pos++
            val name = s.substring(start, pos)
            if (name == "pi") return Math.PI
            if (name == "e") return Math.E
            val arg = if (pos < s.length && s[pos] == '(') atom() else unary()
            return when (name) {
                "sqrt" -> sqrt(arg)
                "cbrt" -> cbrt(arg)
                "abs" -> abs(arg)
                "ln" -> ln(arg)
                "log" -> log10(arg)
                "sin" -> clean(kotlin.math.sin(Math.toRadians(arg)))
                "cos" -> clean(kotlin.math.cos(Math.toRadians(arg)))
                "tan" -> clean(kotlin.math.tan(Math.toRadians(arg)))
                "round" -> round(arg)
                else -> throw IllegalArgumentException("Unknown function $name")
            }
        }

        /** sin(180) is 1.2e-16 in floating point; call it 0. */
        private fun clean(v: Double) = if (abs(v) < 1e-12) 0.0 else v
    }
}
