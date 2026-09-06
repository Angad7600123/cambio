package io.github.angad7600123.cambio.ui

import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for when the grey running total is worth showing.
 *
 * The bug these lock down was visible in the app: `7566.6400` in the big line with
 * `7,566.64` printed grey directly beneath it — the same number twice, because the
 * two *spellings* differed. Every case here is about value, not text.
 */
class PreviewRuleTest {

    private fun bare(expression: String, value: String) = isBareNumber(expression, BigDecimal(value))

    @Test
    fun `a plain number is bare`() = assertTrue(bare("250", "250"))

    @Test
    fun `trailing zeros do not make a number an expression`() {
        // The regression: same value, different spelling.
        assertTrue(bare("7566.6400", "7566.64"))
    }

    @Test
    fun `a trailing decimal point is still bare`() = assertTrue(bare("5.", "5"))

    @Test
    fun `a negative literal is bare`() = assertTrue(bare("-42", "-42"))

    @Test
    fun `an addition is not bare`() = assertFalse(bare("1250+15", "1265"))

    @Test
    fun `a percentage is not bare`() = assertFalse(bare("50%", "0.5"))

    @Test
    fun `a bracketed expression is not bare`() = assertFalse(bare("(2+3)", "5"))

    @Test
    fun `an unevaluated expression is not bare`() = assertFalse(isBareNumber("250", null))

    @Test
    fun `an empty expression is not bare`() = assertFalse(bare("", "0"))
}
