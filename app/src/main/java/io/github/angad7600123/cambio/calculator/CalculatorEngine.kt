package io.github.angad7600123.cambio.calculator

/**
 * The public entry point to the calculator: a string in, a [CalcResult] out.
 *
 * This is pure Kotlin with no Android dependencies, so it is fully unit-testable
 * in isolation from the UI. The three stages — lex, parse, evaluate — are separate
 * objects so each can be tested and reasoned about on its own.
 */
object CalculatorEngine {
    /**
     * Evaluates a canonical expression string.
     *
     * The input is expected to use the internal alphabet (`+ - * / % ( ) .`).
     * Unbalanced trailing parentheses are closed automatically, which mirrors what
     * a physical calculator does when you press `=` with a group still open.
     */
    fun evaluate(expression: String): CalcResult {
        val normalized = expression.trim()
        if (normalized.isEmpty()) return CalcResult.failure(CalcError.EMPTY)

        val balanced = autoCloseParentheses(normalized)
        val tokens = Lexer.tokenize(balanced) ?: return CalcResult.failure(CalcError.MALFORMED)
        if (tokens.isEmpty()) return CalcResult.failure(CalcError.EMPTY)

        val rpn = ShuntingYard.toRpn(tokens) ?: return CalcResult.failure(CalcError.MALFORMED)

        // Something was typed, but it reduced to nothing to evaluate — an empty group
        // such as "()". That is a syntax error, not an absent expression.
        if (rpn.isEmpty()) return CalcResult.failure(CalcError.MALFORMED)

        return RpnEvaluator.evaluate(rpn)
    }

    /** Appends any `)` needed to balance the expression. */
    internal fun autoCloseParentheses(expression: String): String {
        val open = expression.count { it == '(' }
        val close = expression.count { it == ')' }
        val missing = open - close
        return if (missing > 0) expression + ")".repeat(missing) else expression
    }
}
