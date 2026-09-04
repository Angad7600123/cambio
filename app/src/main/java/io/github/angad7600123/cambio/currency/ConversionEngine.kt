package io.github.angad7600123.cambio.currency

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Pure cross-rate arithmetic.
 *
 * The app fetches a single rate table quoted against one base currency (USD), then
 * derives every other pair from it:
 *
 * ```
 * rate(FROM -> TO) = rates[TO] / rates[FROM]
 * ```
 *
 * That means one network request serves all 166 x 166 pairs, and switching
 * currencies never needs the network. The maths is deterministic and has no
 * Android or I/O dependency, so it is directly unit-testable.
 */
object ConversionEngine {
    /** 34 significant digits, matching the calculator engine. */
    val MATH_CONTEXT: MathContext = MathContext(34, RoundingMode.HALF_UP)

    /**
     * Returns the exchange rate from [from] to [to], or `null` when either currency
     * is missing from [rates] or the base rate is zero (which would be a corrupt
     * table rather than a real quote).
     */
    fun rate(from: String, to: String, rates: Map<String, BigDecimal>): BigDecimal? {
        if (from == to) return BigDecimal.ONE

        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null
        if (fromRate.signum() == 0) return null

        return toRate.divide(fromRate, MATH_CONTEXT)
    }

    /**
     * Converts [amount] from one currency to another, or returns `null` when the
     * pair cannot be resolved from [rates].
     */
    fun convert(amount: BigDecimal, from: String, to: String, rates: Map<String, BigDecimal>): BigDecimal? {
        val rate = rate(from, to, rates) ?: return null
        return amount.multiply(rate, MATH_CONTEXT)
    }
}
