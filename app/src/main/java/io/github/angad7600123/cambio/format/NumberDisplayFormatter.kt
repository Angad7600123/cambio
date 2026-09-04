package io.github.angad7600123.cambio.format

import io.github.angad7600123.cambio.currency.CurrencyInfo
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats numbers for display.
 *
 * Everything here follows the device locale, so grouping and decimal separators
 * match what the user sees everywhere else on their phone (`1,234.5` in en-US,
 * `1.234,5` in de-DE, `1 234,5` in fr-FR).
 */
class NumberDisplayFormatter(private val locale: Locale = Locale.getDefault()) {
    private val symbols = DecimalFormatSymbols.getInstance(locale)

    /** The decimal separator for this locale, shown on the `.` key. */
    val decimalSeparator: Char get() = symbols.decimalSeparator

    val groupingSeparator: Char get() = symbols.groupingSeparator

    /**
     * Formats a calculator result: grouped, up to [MAX_FRACTION_DIGITS] decimals,
     * switching to scientific notation for magnitudes that would otherwise run off
     * the display.
     */
    fun format(value: BigDecimal): String {
        val magnitude = value.abs()

        val useScientific = magnitude >= SCIENTIFIC_UPPER_BOUND ||
            (magnitude.signum() != 0 && magnitude < SCIENTIFIC_LOWER_BOUND)

        return if (useScientific) {
            DecimalFormat(SCIENTIFIC_PATTERN, symbols).format(value)
        } else {
            DecimalFormat(PLAIN_PATTERN, symbols)
                .apply {
                    maximumFractionDigits = MAX_FRACTION_DIGITS
                    isGroupingUsed = true
                    roundingMode = RoundingMode.HALF_UP
                }
                .format(value)
        }
    }

    /**
     * Formats a monetary amount using the currency's real minor-unit count: two
     * decimals for USD, none for JPY, three for KWD.
     *
     * When a non-zero amount would round away to nothing at that precision (a tiny
     * fraction of a high-value currency), precision is escalated so the user sees a
     * real number instead of a misleading `0.00`.
     */
    fun formatMoney(value: BigDecimal, currency: CurrencyInfo): String {
        val digits = fractionDigitsFor(value, currency.minorUnits)

        return DecimalFormat(PLAIN_PATTERN, symbols)
            .apply {
                minimumFractionDigits = digits
                maximumFractionDigits = digits
                isGroupingUsed = true
                roundingMode = RoundingMode.HALF_UP
            }
            .format(value)
    }

    /**
     * Formats an exchange rate. Rates span a huge range (one USD is ~0.86 EUR but
     * ~1,300,000 IRR), so this uses significant digits rather than a fixed scale.
     */
    fun formatRate(value: BigDecimal): String {
        val magnitude = value.abs()
        val digits = when {
            magnitude.signum() == 0 -> 2
            magnitude >= BigDecimal("1000") -> 2
            magnitude >= BigDecimal.ONE -> 4
            else -> 6
        }

        return DecimalFormat(PLAIN_PATTERN, symbols)
            .apply {
                maximumFractionDigits = digits
                isGroupingUsed = true
                roundingMode = RoundingMode.HALF_UP
            }
            .format(value)
    }

    /**
     * Chooses how many decimals to show so a non-zero amount never renders as zero,
     * capped so the result stays readable.
     */
    private fun fractionDigitsFor(value: BigDecimal, minorUnits: Int): Int {
        if (value.signum() == 0) return minorUnits
        if (value.abs().setScale(minorUnits, RoundingMode.HALF_UP).signum() != 0) return minorUnits

        var digits = minorUnits
        while (
            digits < MAX_ESCALATED_DIGITS &&
            value.abs().setScale(digits, RoundingMode.HALF_UP).signum() == 0
        ) {
            digits++
        }
        // One extra digit past the first significant one reads better than a bare "0.0001".
        return (digits + 1).coerceAtMost(MAX_ESCALATED_DIGITS)
    }

    private companion object {
        const val MAX_FRACTION_DIGITS = 10
        const val MAX_ESCALATED_DIGITS = 8
        const val PLAIN_PATTERN = "#,##0.###"
        const val SCIENTIFIC_PATTERN = "0.######E0"

        val SCIENTIFIC_UPPER_BOUND: BigDecimal = BigDecimal("1E+16")
        val SCIENTIFIC_LOWER_BOUND: BigDecimal = BigDecimal("1E-6")
    }
}
