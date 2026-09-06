package io.github.angad7600123.cambio.ui

import io.github.angad7600123.cambio.calculator.Lexer
import java.math.BigDecimal

/**
 * Whether the running total would merely repeat the figure above it.
 *
 * The grey preview line earns its place only when it says something the big line
 * does not — while `250×4` is being typed, not while `250` is. Showing it always
 * printed the same number twice, one above the other, which read as a rendering
 * fault and cost the display a line it did not need.
 *
 * The comparison has to be numeric. An earlier version compared the two *formatted*
 * strings, so `7566.6400` was judged different from a preview of `7,566.64` and both
 * were drawn: same value, different spelling.
 *
 * A trailing decimal point counts as bare — `5.` is still five being typed.
 */
internal fun isBareNumber(expression: String, value: BigDecimal?): Boolean {
    if (value == null) return false
    val literal = expression.removeSuffix(Lexer.DECIMAL_POINT.toString()).toBigDecimalOrNull() ?: return false
    return literal.compareTo(value) == 0
}
