package io.github.angad7600123.cambio.calculator

import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for expression evaluation: precedence, parentheses, percent semantics,
 * exact decimal arithmetic, and every documented failure mode.
 */
class CalculatorEngineTest {

    private fun evaluate(expression: String): BigDecimal {
        val result = CalculatorEngine.evaluate(expression)
        assertTrue(result is CalcResult.Success, "Expected success for \"$expression\", got $result")
        return result.value
    }

    private fun errorOf(expression: String): CalcError {
        val result = CalculatorEngine.evaluate(expression)
        assertTrue(result is CalcResult.Failure, "Expected failure for \"$expression\", got $result")
        return result.error
    }

    private fun assertValue(expected: String, expression: String) {
        assertEquals(
            0,
            BigDecimal(expected).compareTo(evaluate(expression)),
            "\"$expression\" should equal $expected but was ${evaluate(expression)}",
        )
    }

    // region Basic arithmetic

    @Test
    fun `adds two numbers`() = assertValue("5", "2+3")

    @Test
    fun `subtracts two numbers`() = assertValue("4", "9-5")

    @Test
    fun `multiplies two numbers`() = assertValue("42", "6*7")

    @Test
    fun `divides two numbers`() = assertValue("4", "12/3")

    @Test
    fun `evaluates a single number`() = assertValue("7", "7")

    // endregion

    // region Operator precedence

    @Test
    fun `multiplication takes precedence over addition`() = assertValue("14", "2+3*4")

    @Test
    fun `division takes precedence over subtraction`() = assertValue("8", "10-4/2")

    @Test
    fun `same precedence evaluates left to right`() {
        assertValue("3", "8-3-2")
        assertValue("2", "16/4/2")
    }

    @Test
    fun `mixed precedence across a long chain`() = assertValue("17", "2+3*4+3")

    // endregion

    // region Parentheses

    @Test
    fun `parentheses override precedence`() = assertValue("20", "(2+3)*4")

    @Test
    fun `nested parentheses evaluate innermost first`() = assertValue("18", "((1+2)*3)*2")

    @Test
    fun `unclosed parentheses are closed automatically at evaluation`() = assertValue("20", "(2+3)*4")

    @Test
    fun `engine auto-closes a trailing open group`() = assertValue("9", "3*(1+2")

    @Test
    fun `unmatched closing parenthesis is malformed`() = assertEquals(CalcError.MALFORMED, errorOf("2+3)"))

    // endregion

    // region Negative numbers

    @Test
    fun `leading minus negates`() = assertValue("-5", "-5")

    @Test
    fun `unary minus after an operator`() = assertValue("6", "8*-2+22")

    @Test
    fun `unary minus inside parentheses`() = assertValue("-1", "(-5)+4")

    @Test
    fun `negating a parenthesised group`() = assertValue("-5", "-(2+3)")

    @Test
    fun `subtracting a negative number adds`() = assertValue("12", "10--2")

    // endregion

    // region Decimals and precision

    @Test
    fun `decimal addition is exact rather than binary floating point`() {
        // The headline case: a Double would give 0.30000000000000004 here.
        assertValue("0.3", "0.1+0.2")
    }

    @Test
    fun `repeated decimal addition stays exact`() = assertValue("1", "0.1+0.1+0.1+0.1+0.1+0.1+0.1+0.1+0.1+0.1")

    @Test
    fun `decimal multiplication is exact`() = assertValue("0.06", "0.2*0.3")

    @Test
    fun `non-terminating division is rounded, not thrown`() {
        val result = evaluate("1/3")
        assertTrue(result.toPlainString().startsWith("0.3333333333"))
    }

    @Test
    fun `trailing zeros are stripped from the result`() = assertEquals("2.5", evaluate("5/2").toPlainString())

    @Test
    fun `whole number results do not gain a decimal part`() = assertEquals("4", evaluate("2+2").toPlainString())

    // endregion

    // region Percent

    @Test
    fun `percent added is a percentage of the left operand`() = assertValue("220", "200+10%")

    @Test
    fun `percent subtracted is a percentage of the left operand`() = assertValue("180", "200-10%")

    @Test
    fun `percent multiplied is a plain fraction`() = assertValue("20", "200*10%")

    @Test
    fun `percent divided is a plain fraction`() = assertValue("2000", "200/10%")

    @Test
    fun `standalone percent is a fraction of one`() = assertValue("0.5", "50%")

    @Test
    fun `percent chains use the running total`() {
        // 200 + 10% = 220, then 220 + 5% = 231.
        assertValue("231", "200+10%+5%")
    }

    @Test
    fun `percent applies to a parenthesised group`() = assertValue("110", "100+(5+5)%")

    // endregion

    // region Errors

    @Test
    fun `division by zero is reported, not thrown`() = assertEquals(CalcError.DIVIDE_BY_ZERO, errorOf("5/0"))

    @Test
    fun `division by a zero expression is reported`() = assertEquals(CalcError.DIVIDE_BY_ZERO, errorOf("5/(3-3)"))

    @Test
    fun `division by zero percent is reported`() = assertEquals(CalcError.DIVIDE_BY_ZERO, errorOf("5/0%"))

    @Test
    fun `empty expression is reported as empty`() = assertEquals(CalcError.EMPTY, errorOf(""))

    @Test
    fun `whitespace-only expression is reported as empty`() = assertEquals(CalcError.EMPTY, errorOf("   "))

    @Test
    fun `trailing operator is malformed`() = assertEquals(CalcError.MALFORMED, errorOf("5+"))

    @Test
    fun `two operators in a row are malformed`() = assertEquals(CalcError.MALFORMED, errorOf("5+*3"))

    @Test
    fun `unknown character is malformed`() = assertEquals(CalcError.MALFORMED, errorOf("5&3"))

    @Test
    fun `double decimal point is malformed`() = assertEquals(CalcError.MALFORMED, errorOf("1.2.3"))

    @Test
    fun `empty parentheses are malformed`() = assertEquals(CalcError.MALFORMED, errorOf("()"))

    @Test
    fun `a lone decimal point is malformed`() = assertEquals(CalcError.MALFORMED, errorOf("."))

    @Test
    fun `evaluation never throws for arbitrary junk`() {
        // Whatever the input, the engine must return a value or an error.
        listOf("+++", ")(", "%%%", "..", "-", "*/", "1++2", "((((", "))))").forEach { input ->
            val result = CalculatorEngine.evaluate(input)
            assertTrue(
                result is CalcResult.Success || result is CalcResult.Failure,
                "No outcome for \"$input\"",
            )
        }
    }

    // endregion

    // region Large and small numbers

    @Test
    fun `handles numbers far beyond Long range`() = assertValue("999999999999999999999999", "999999999999999999999999")

    @Test
    fun `large multiplication stays exact`() = assertValue("1000000000000000000000000", "1000000000000*1000000000000")

    @Test
    fun `runaway magnitude is reported as overflow rather than hanging`() {
        val huge = "9".repeat(300)
        assertEquals(CalcError.OVERFLOW, errorOf("$huge*$huge*$huge*$huge"))
    }

    @Test
    fun `very small results retain precision`() {
        val result = evaluate("1/1000000")
        assertEquals(0, BigDecimal("0.000001").compareTo(result))
    }

    // endregion

    @Test
    fun `whitespace between tokens is ignored`() = assertValue("14", " 2 + 3 * 4 ")
}
