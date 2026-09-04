package io.github.angad7600123.cambio.currency

import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for cross-rate arithmetic.
 *
 * Rates come from a fixed table so every expectation is exact and reproducible;
 * no test touches the network.
 */
class ConversionEngineTest {

    private val rates = mapOf(
        "USD" to BigDecimal("1"),
        "EUR" to BigDecimal("0.860629"),
        "INR" to BigDecimal("94.540498"),
        "JPY" to BigDecimal("156.019704"),
        "KWD" to BigDecimal("0.308482"),
        "ZERO" to BigDecimal("0"),
    )

    @Test
    fun `rate from the base currency is the quoted rate`() =
        assertEquals(0, BigDecimal("94.540498").compareTo(ConversionEngine.rate("USD", "INR", rates)!!))

    @Test
    fun `rate to the base currency is the reciprocal`() {
        val rate = ConversionEngine.rate("INR", "USD", rates)!!
        assertEquals(0, BigDecimal.ONE.divide(BigDecimal("94.540498"), ConversionEngine.MATH_CONTEXT).compareTo(rate))
    }

    @Test
    fun `cross rate between two non-base currencies`() {
        // EUR -> INR must equal INR/USD divided by EUR/USD.
        val expected = BigDecimal("94.540498")
            .divide(BigDecimal("0.860629"), ConversionEngine.MATH_CONTEXT)
        assertEquals(0, expected.compareTo(ConversionEngine.rate("EUR", "INR", rates)!!))
    }

    @Test
    fun `converting a currency to itself is identity`() {
        assertEquals(0, BigDecimal.ONE.compareTo(ConversionEngine.rate("EUR", "EUR", rates)!!))
        val amount = BigDecimal("123.45")
        assertEquals(0, amount.compareTo(ConversionEngine.convert(amount, "EUR", "EUR", rates)!!))
    }

    @Test
    fun `converting a currency to itself works even when absent from the table`() {
        // Guards the identity shortcut: it must not require a lookup.
        assertEquals(0, BigDecimal.ONE.compareTo(ConversionEngine.rate("XYZ", "XYZ", rates)!!))
    }

    @Test
    fun `round trip returns approximately the original amount`() {
        val original = BigDecimal("100")
        val toInr = ConversionEngine.convert(original, "USD", "INR", rates)!!
        val back = ConversionEngine.convert(toInr, "INR", "USD", rates)!!
        // 34 significant digits each way; any drift is far below a cent.
        assertTrue(back.subtract(original).abs() < BigDecimal("0.0000000001"))
    }

    @Test
    fun `converts a whole amount`() {
        val result = ConversionEngine.convert(BigDecimal("10"), "USD", "INR", rates)!!
        assertEquals(0, BigDecimal("945.40498").compareTo(result))
    }

    @Test
    fun `converts zero to zero`() {
        val result = ConversionEngine.convert(BigDecimal.ZERO, "USD", "INR", rates)!!
        assertEquals(0, BigDecimal.ZERO.compareTo(result))
    }

    @Test
    fun `converts a negative amount`() {
        val result = ConversionEngine.convert(BigDecimal("-10"), "USD", "INR", rates)!!
        assertEquals(0, BigDecimal("-945.40498").compareTo(result))
    }

    @Test
    fun `unknown source currency yields null rather than throwing`() {
        assertNull(ConversionEngine.rate("NOPE", "USD", rates))
        assertNull(ConversionEngine.convert(BigDecimal.ONE, "NOPE", "USD", rates))
    }

    @Test
    fun `unknown target currency yields null`() {
        assertNull(ConversionEngine.rate("USD", "NOPE", rates))
        assertNull(ConversionEngine.convert(BigDecimal.ONE, "USD", "NOPE", rates))
    }

    @Test
    fun `an empty rate table yields null`() = assertNull(ConversionEngine.rate("USD", "EUR", emptyMap()))

    @Test
    fun `a zero base rate yields null instead of dividing by zero`() {
        // A corrupt table must not crash the app.
        assertNull(ConversionEngine.rate("ZERO", "USD", rates))
        assertNull(ConversionEngine.convert(BigDecimal.ONE, "ZERO", "USD", rates))
    }

    @Test
    fun `a zero target rate converts to zero`() {
        val result = ConversionEngine.convert(BigDecimal("10"), "USD", "ZERO", rates)!!
        assertEquals(0, BigDecimal.ZERO.compareTo(result))
    }

    @Test
    fun `handles very large amounts without losing precision`() {
        val large = BigDecimal("1000000000000")
        val result = ConversionEngine.convert(large, "USD", "INR", rates)!!
        assertEquals(0, BigDecimal("94540498000000").compareTo(result))
    }

    @Test
    fun `handles very small amounts`() {
        val tiny = BigDecimal("0.000001")
        val result = ConversionEngine.convert(tiny, "USD", "INR", rates)!!
        assertEquals(0, BigDecimal("0.000094540498").compareTo(result))
    }
}
