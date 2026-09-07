package io.github.angad7600123.cambio.format

import io.github.angad7600123.cambio.calculator.OperatorType
import java.util.Locale

/**
 * Renders a canonical expression string for the display.
 *
 * The engine works in a plain ASCII alphabet (`* / - .`) while the display shows
 * proper typographic glyphs and locale-aware numbers. Keeping the two apart means
 * the parser never has to know about locales, and the display never constrains the
 * parser.
 *
 * `1234.5*-2` in en-US becomes `1,234.5 x -2` with real multiplication and minus
 * signs; in de-DE the same expression becomes `1.234,5 x -2`.
 */
class ExpressionFormatter(
    private val numbers: NumberDisplayFormatter = NumberDisplayFormatter(),
    private val locale: Locale = Locale.getDefault(),
) {
    /** Converts a canonical expression into its on-screen representation. */
    fun format(expression: String): String = render(expression, spaceOperators = true)

    /**
     * The same expression with its operators tight against their operands.
     *
     * This is the form the calculator's own display uses — `1,250+15`, not
     * `1,250 + 15` — and the widget wants it for the same reason the app does,
     * with one of its own on top: a widget figure has half the width of a phone
     * display to work in, and two spaces per operator is width it cannot spare.
     * The operator glyphs are distinct enough to separate the operands unaided.
     */
    fun formatCompact(expression: String): String = render(expression, spaceOperators = false)

    private fun render(expression: String, spaceOperators: Boolean): String {
        if (expression.isEmpty()) return ""

        val builder = StringBuilder(expression.length + expression.length / 2)
        var index = 0

        while (index < expression.length) {
            val char = expression[index]

            when {
                char.isDigit() -> {
                    val end = endOfNumber(expression, index)
                    builder.append(formatNumberLiteral(expression.substring(index, end)))
                    index = end
                }

                char == '.' -> {
                    // A bare leading '.' cannot occur, but be defensive rather than
                    // dropping characters silently.
                    builder.append(numbers.decimalSeparator)
                    index++
                }

                else -> {
                    // Binary operators get breathing room; a unary minus stays tight
                    // against its number, so "8 × −2" reads correctly.
                    val glyph = glyphFor(char)
                    if (spaceOperators &&
                        OperatorType.fromSymbol(char) != null &&
                        !isUnaryAt(expression, index)
                    ) {
                        builder.append(' ').append(glyph).append(' ')
                    } else {
                        builder.append(glyph)
                    }
                    index++
                }
            }
        }

        return builder.toString()
    }

    /**
     * The expression line to show above the result.
     *
     * While a bare number is being typed, the expression and the result are the
     * same text, and showing both just prints it twice. In that case the line is
     * blank, so the secondary line only appears once there is an actual operation
     * to show — which is what both the app and the widget want.
     */
    fun formatSecondary(expression: String, resultDisplay: String): String {
        val formatted = format(expression)
        return if (formatted == resultDisplay) "" else formatted
    }

    /**
     * Groups the integer part of a literal and localises its decimal separator,
     * while preserving a trailing separator the user is mid-way through typing
     * (`12.` must not collapse back to `12`).
     */
    private fun formatNumberLiteral(literal: String): String {
        val pointIndex = literal.indexOf('.')
        if (pointIndex < 0) return groupInteger(literal)

        val integerPart = literal.take(pointIndex)
        val fractionPart = literal.substring(pointIndex + 1)
        return buildString {
            append(groupInteger(integerPart))
            append(numbers.decimalSeparator)
            append(fractionPart)
        }
    }

    private fun groupInteger(digits: String): String {
        if (digits.isEmpty()) return digits
        // Long literals are capped by the input state machine, so a plain Long parse
        // is safe here; fall back to the raw digits if that ever stops holding.
        val value = digits.toLongOrNull() ?: return digits
        return String.format(locale, "%,d", value)
    }

    private fun endOfNumber(expression: String, start: Int): Int {
        var index = start
        while (index < expression.length &&
            (expression[index].isDigit() || expression[index] == '.')
        ) {
            index++
        }
        return index
    }

    /**
     * A minus is unary when it opens the expression or follows another operator or
     * an opening bracket — the same rule the lexer applies.
     */
    private fun isUnaryAt(expression: String, index: Int): Boolean {
        if (expression[index] != OperatorType.SUBTRACT.symbol) return false
        val previous = expression.getOrNull(index - 1) ?: return true
        return previous == '(' || OperatorType.fromSymbol(previous) != null
    }

    private fun glyphFor(char: Char): String = when (char) {
        OperatorType.MULTIPLY.symbol -> MULTIPLY_GLYPH
        OperatorType.DIVIDE.symbol -> DIVIDE_GLYPH
        OperatorType.SUBTRACT.symbol -> MINUS_GLYPH
        OperatorType.ADD.symbol -> PLUS_GLYPH
        else -> char.toString()
    }

    companion object {
        const val MULTIPLY_GLYPH = "×" // x
        const val DIVIDE_GLYPH = "÷" // /
        const val MINUS_GLYPH = "−" // minus sign
        const val PLUS_GLYPH = "+"
    }
}
