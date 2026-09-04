package io.github.angad7600123.cambio.format

import io.github.angad7600123.cambio.currency.CurrencyCatalog
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Formatting tests.
 *
 * A fixed locale is used throughout so expectations are stable regardless of the
 * machine running the suite; a separate test covers locale-dependent behaviour
 * explicitly.
 */
class NumberFormattingTest {

    private val formatter = NumberDisplayFormatter(Locale.US)
    private val catalog = CurrencyCatalog(Locale.US)

    // region Display formatting

    @Test
    fun `groups thousands`() = assertEquals("1,234,567", formatter.format(BigDecimal("1234567")))

    @Test
    fun `keeps decimals without padding them`() = assertEquals("2.5", formatter.format(BigDecimal("2.5")))

    @Test
    fun `formats zero plainly`() = assertEquals("0", formatter.format(BigDecimal.ZERO))

    @Test
    fun `formats negative numbers`() = assertEquals("-1,234.5", formatter.format(BigDecimal("-1234.5")))

    @Test
    fun `switches to scientific notation for very large numbers`() {
        val formatted = formatter.format(BigDecimal("1E+20"))
        assertTrue(formatted.contains("E"), "Expected scientific notation, got $formatted")
    }

    @Test
    fun `switches to scientific notation for very small numbers`() {
        val formatted = formatter.format(BigDecimal("0.0000000001"))
        assertTrue(formatted.contains("E"), "Expected scientific notation, got $formatted")
    }

    @Test
    fun `stays plain just below the scientific threshold`() {
        val formatted = formatter.format(BigDecimal("1000000000000000"))
        assertTrue(!formatted.contains("E"), "Should not be scientific, got $formatted")
    }

    // endregion

    // region Money formatting

    @Test
    fun `uses two decimals for a two-minor-unit currency`() =
        assertEquals("1,234.50", formatter.formatMoney(BigDecimal("1234.5"), catalog.infoFor("USD")))

    @Test
    fun `uses no decimals for yen`() =
        assertEquals("1,235", formatter.formatMoney(BigDecimal("1234.5"), catalog.infoFor("JPY")))

    @Test
    fun `uses three decimals for the Kuwaiti dinar`() =
        assertEquals("1.235", formatter.formatMoney(BigDecimal("1.2345"), catalog.infoFor("KWD")))

    @Test
    fun `escalates precision so a tiny non-zero amount never displays as zero`() {
        // 0.00004 USD would round to "0.00", which reads as nothing at all.
        val formatted = formatter.formatMoney(BigDecimal("0.00004"), catalog.infoFor("USD"))
        assertTrue(
            formatted.trimStart('0', '.', ',').any { it != '0' },
            "Tiny amount collapsed to zero: $formatted",
        )
    }

    @Test
    fun `genuine zero still displays with the standard minor units`() =
        assertEquals("0.00", formatter.formatMoney(BigDecimal.ZERO, catalog.infoFor("USD")))

    @Test
    fun `rounds money half up`() =
        assertEquals("2.35", formatter.formatMoney(BigDecimal("2.345"), catalog.infoFor("USD")))

    // endregion

    // region Rate formatting

    @Test
    fun `shows more decimals for rates below one`() {
        val formatted = formatter.formatRate(BigDecimal("0.860629"))
        assertEquals("0.860629", formatted)
    }

    @Test
    fun `shows four decimals for mid-range rates`() =
        assertEquals("94.5405", formatter.formatRate(BigDecimal("94.540498")))

    @Test
    fun `shows two decimals for very large rates`() =
        assertEquals("1,300,000.12", formatter.formatRate(BigDecimal("1300000.123456")))

    // endregion

    @Test
    fun `formatting follows the locale`() {
        val german = NumberDisplayFormatter(Locale.GERMANY)
        assertEquals("1.234,5", german.format(BigDecimal("1234.5")))
        assertEquals(',', german.decimalSeparator)
    }
}

/** Tests for rendering a canonical expression on the display. */
class ExpressionFormatterTest {

    private val formatter = ExpressionFormatter(NumberDisplayFormatter(Locale.US), Locale.US)

    @Test
    fun `multiplication uses the multiplication sign`() =
        assertTrue(formatter.format("2*3").contains(ExpressionFormatter.MULTIPLY_GLYPH))

    @Test
    fun `division uses the division sign`() =
        assertTrue(formatter.format("6/2").contains(ExpressionFormatter.DIVIDE_GLYPH))

    @Test
    fun `subtraction uses the minus sign`() =
        assertTrue(formatter.format("6-2").contains(ExpressionFormatter.MINUS_GLYPH))

    @Test
    fun `groups digits inside an expression`() = assertTrue(formatter.format("1234567+1").startsWith("1,234,567"))

    @Test
    fun `binary operators are spaced for readability`() = assertEquals("1,260 + 16%", formatter.format("1260+16%"))

    @Test
    fun `a unary minus stays tight against its number`() = assertEquals(
        "8 ${ExpressionFormatter.MULTIPLY_GLYPH} ${ExpressionFormatter.MINUS_GLYPH}2",
        formatter.format("8*-2"),
    )

    @Test
    fun `a leading minus is not spaced`() = assertEquals("${ExpressionFormatter.MINUS_GLYPH}5", formatter.format("-5"))

    @Test
    fun `preserves a decimal point being typed`() = assertEquals("12.", formatter.format("12."))

    @Test
    fun `preserves trailing decimal digits`() = assertEquals("12.30", formatter.format("12.30"))

    @Test
    fun `formats an empty expression as empty`() = assertEquals("", formatter.format(""))

    @Test
    fun `keeps parentheses and percent intact`() {
        val formatted = formatter.format("(2+3)%")
        assertTrue(formatted.contains("(") && formatted.contains(")") && formatted.contains("%"))
    }

    @Test
    fun `the secondary line is blank when it would repeat the result`() =
        assertEquals("", formatter.formatSecondary("750", "750"))

    @Test
    fun `the secondary line shows once there is an operation`() =
        assertEquals("750 + 15%", formatter.formatSecondary("750+15%", "862.5"))

    @Test
    fun `the secondary line accounts for grouping when comparing`() =
        assertEquals("", formatter.formatSecondary("1234567", "1,234,567"))

    @Test
    fun `uses the locale decimal separator`() {
        val german = ExpressionFormatter(NumberDisplayFormatter(Locale.GERMANY), Locale.GERMANY)
        assertEquals("12,5", german.format("12.5"))
    }
}
