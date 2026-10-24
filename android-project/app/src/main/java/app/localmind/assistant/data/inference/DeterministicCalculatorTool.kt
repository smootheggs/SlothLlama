package app.localmind.assistant.data.inference

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

data class CalculationResult(
    val expression: String,
    val numericResult: Double,
    val formattedResult: String
)

/**
 * Deterministic calculator tool for the Math Tutor and General Assistant so arithmetic is never
 * guessed or hallucinated. Separate from the future SymbolicMathEngine interface.
 */
@Singleton
class DeterministicCalculatorTool @Inject constructor() {

    fun tryEvaluateArithmetic(prompt: String): CalculationResult? {
        val mathRegex = Regex("([0-9]+(?:\\.[0-9]+)?\\s*[+\\-*/^]\\s*[0-9]+(?:\\.[0-9]+)?(?:\\s*[+\\-*/^]\\s*[0-9]+(?:\\.[0-9]+)?)*)")
        val match = mathRegex.find(prompt) ?: return null
        val expr = match.value.trim()
        return runCatching {
            val value = evaluateSimpleExpression(expr)
            val formatted = if (value % 1.0 == 0.0) value.toLong().toString() else "%.6f".format(value).trimEnd('0').trimEnd('.')
            CalculationResult(expr, value, formatted)
        }.getOrNull()
    }

    fun evaluateSimpleExpression(expr: String): Double {
        val tokens = expr.replace(" ", "")
        // Recursive descent parser for +, -, *, /, ^
        var pos = 0
        fun parseNumber(): Double {
            val start = pos
            while (pos < tokens.length && (tokens[pos].isDigit() || tokens[pos] == '.')) pos++
            return tokens.substring(start, pos).toDouble()
        }
        fun parseFactor(): Double {
            var base = parseNumber()
            while (pos < tokens.length && tokens[pos] == '^') {
                pos++
                val exp = parseNumber()
                base = base.pow(exp)
            }
            return base
        }
        fun parseTerm(): Double {
            var value = parseFactor()
            while (pos < tokens.length && (tokens[pos] == '*' || tokens[pos] == '/')) {
                val op = tokens[pos++]
                val right = parseFactor()
                value = if (op == '*') value * right else value / right
            }
            return value
        }
        var result = parseTerm()
        while (pos < tokens.length && (tokens[pos] == '+' || tokens[pos] == '-')) {
            val op = tokens[pos++]
            val right = parseTerm()
            result = if (op == '+') result + right else result - right
        }
        return result
    }
}

/**
 * Separate symbolic-math integration interface reserved for future on-device CAS implementation.
 */
interface SymbolicMathEngine {
    suspend fun simplifyExpression(latexOrAscii: String): Result<String>
    suspend fun verifyAlgebraicStep(previousStep: String, candidateStep: String): Result<Boolean>
}
