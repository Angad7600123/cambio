package io.github.angad7600123.cambio.calculator

import java.math.BigDecimal

/**
 * Turns the raw expression string into a flat list of [Token]s.
 *
 * The lexer is intentionally strict: anything it cannot classify makes the whole
 * expression malformed rather than being silently skipped, so a typo can never
 * be quietly reinterpreted as a different calculation.
 *
 * The expression string uses a canonical internal alphabet (`+ - * / % ( ) .` and
 * digits). Localised glyphs such as `×`, `÷`, `−` and locale-specific decimal
 * separators are normalised before they ever reach this class.
 */
internal object Lexer {
    fun tokenize(expression: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var index = 0

        while (index < expression.length) {
            val char = expression[index]

            when {
                char.isWhitespace() -> index++

                char.isDigit() || char == DECIMAL_POINT -> {
                    val end = consumeNumber(expression, index) ?: return null
                    val literal = expression.substring(index, end)
                    val value = literal.toBigDecimalOrNull() ?: return null
                    tokens += Token.Number(value)
                    index = end
                }

                char == '(' -> {
                    tokens += Token.LeftParen
                    index++
                }

                char == ')' -> {
                    tokens += Token.RightParen
                    index++
                }

                char == '%' -> {
                    tokens += Token.Percent
                    index++
                }

                else -> {
                    val operator = OperatorType.fromSymbol(char) ?: return null
                    // A '-' is unary when nothing that can end an operand precedes it,
                    // which is what makes "-5", "(-5)" and "8 * -2" all valid.
                    if (operator == OperatorType.SUBTRACT && isUnaryPosition(tokens)) {
                        tokens += Token.UnaryOperator(UnaryOperatorType.NEGATE)
                    } else {
                        tokens += Token.Operator(operator)
                    }
                    index++
                }
            }
        }

        return tokens
    }

    /**
     * Returns the index just past the number starting at [start], or `null` if the
     * literal contains more than one decimal point.
     */
    private fun consumeNumber(expression: String, start: Int): Int? {
        var index = start
        var seenDecimalPoint = false

        while (index < expression.length) {
            val char = expression[index]
            when {
                char.isDigit() -> index++
                char == DECIMAL_POINT && !seenDecimalPoint -> {
                    seenDecimalPoint = true
                    index++
                }
                char == DECIMAL_POINT -> return null
                else -> break
            }
        }

        // A lone "." is not a number.
        if (index == start + 1 && expression[start] == DECIMAL_POINT) return null
        return index
    }

    /**
     * A minus sign is unary at the very start of an expression, or when it follows
     * another operator or an opening parenthesis.
     */
    private fun isUnaryPosition(tokens: List<Token>): Boolean = when (tokens.lastOrNull()) {
        null, is Token.Operator, is Token.UnaryOperator, Token.LeftParen -> true
        else -> false
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }

    const val DECIMAL_POINT: Char = '.'
}
