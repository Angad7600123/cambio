package io.github.angad7600123.cambio.calculator

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the keypress state machine — the rules that make typing feel like a
 * real calculator rather than a text field.
 */
class CalculatorInputTest {

    /** Types a sequence of keys from empty and returns the resulting state. */
    private fun type(vararg keys: CalculatorKey): InputState =
        keys.fold(InputState.Empty) { state, key -> CalculatorInput.press(state, key) }

    private fun digits(text: String): Array<CalculatorKey> = text.map { char ->
        when (char) {
            '+' -> CalculatorKey.Operator(OperatorType.ADD)
            '-' -> CalculatorKey.Operator(OperatorType.SUBTRACT)
            '*' -> CalculatorKey.Operator(OperatorType.MULTIPLY)
            '/' -> CalculatorKey.Operator(OperatorType.DIVIDE)
            '.' -> CalculatorKey.Decimal
            '%' -> CalculatorKey.Percent
            '(' -> CalculatorKey.Parenthesis
            ')' -> CalculatorKey.Parenthesis
            else -> CalculatorKey.Digit(char.digitToInt())
        }
    }.toTypedArray()

    // region Digit entry

    @Test
    fun `typing digits builds a number`() = assertEquals("123", type(*digits("123")).expression)

    @Test
    fun `leading zero is replaced by the next digit`() =
        assertEquals("5", type(CalculatorKey.Digit(0), CalculatorKey.Digit(5)).expression)

    @Test
    fun `zero alone is preserved`() = assertEquals("0", type(CalculatorKey.Digit(0)).expression)

    @Test
    fun `digit entry is capped per number`() {
        val many = (1..25).map { CalculatorKey.Digit(9) }.toTypedArray()
        val state = type(*many)
        assertEquals(CalculatorInput.MAX_DIGITS_PER_NUMBER, state.expression.length)
    }

    @Test
    fun `the digit cap applies per number, not per expression`() {
        val fifteen = "9".repeat(CalculatorInput.MAX_DIGITS_PER_NUMBER)
        val state = type(*digits(fifteen), CalculatorKey.Operator(OperatorType.ADD), *digits(fifteen))
        assertEquals("$fifteen+$fifteen", state.expression)
    }

    // endregion

    // region Operators

    @Test
    fun `a trailing operator is replaced rather than stacked`() {
        val state = type(
            CalculatorKey.Digit(5),
            CalculatorKey.Operator(OperatorType.ADD),
            CalculatorKey.Operator(OperatorType.MULTIPLY),
        )
        assertEquals("5*", state.expression)
    }

    @Test
    fun `minus after times is kept as a sign`() {
        val state = type(
            CalculatorKey.Digit(5),
            CalculatorKey.Operator(OperatorType.MULTIPLY),
            CalculatorKey.Operator(OperatorType.SUBTRACT),
        )
        assertEquals("5*-", state.expression)
    }

    @Test
    fun `a leading minus is allowed`() =
        assertEquals("-", type(CalculatorKey.Operator(OperatorType.SUBTRACT)).expression)

    @Test
    fun `a leading plus is ignored`() = assertEquals("", type(CalculatorKey.Operator(OperatorType.ADD)).expression)

    @Test
    fun `an operator directly after an open bracket is ignored except minus`() {
        assertEquals("(", type(CalculatorKey.Parenthesis, CalculatorKey.Operator(OperatorType.MULTIPLY)).expression)
        assertEquals("(-", type(CalculatorKey.Parenthesis, CalculatorKey.Operator(OperatorType.SUBTRACT)).expression)
    }

    // endregion

    // region Decimal point

    @Test
    fun `a decimal point on an empty display yields zero point`() =
        assertEquals("0.", type(CalculatorKey.Decimal).expression)

    @Test
    fun `a decimal point after an operator yields zero point`() = assertEquals(
        "5+0.",
        type(CalculatorKey.Digit(5), CalculatorKey.Operator(OperatorType.ADD), CalculatorKey.Decimal).expression,
    )

    @Test
    fun `only one decimal point per number is accepted`() =
        assertEquals("1.5", type(*digits("1.5"), CalculatorKey.Decimal).expression)

    @Test
    fun `a second number may have its own decimal point`() =
        assertEquals("1.5+2.5", type(*digits("1.5+2.5")).expression)

    @Test
    fun `a decimal point after a percent is ignored`() =
        assertEquals("5%", type(CalculatorKey.Digit(5), CalculatorKey.Percent, CalculatorKey.Decimal).expression)

    // endregion

    // region Percent

    @Test
    fun `percent is accepted after a digit`() =
        assertEquals("50%", type(*digits("50"), CalculatorKey.Percent).expression)

    @Test
    fun `percent on an empty display is ignored`() = assertEquals("", type(CalculatorKey.Percent).expression)

    @Test
    fun `percent directly after an operator is ignored`() = assertEquals(
        "5+",
        type(CalculatorKey.Digit(5), CalculatorKey.Operator(OperatorType.ADD), CalculatorKey.Percent).expression,
    )

    // endregion

    // region Parentheses

    @Test
    fun `the bracket key opens a group on an empty display`() =
        assertEquals("(", type(CalculatorKey.Parenthesis).expression)

    @Test
    fun `the bracket key closes an open group after an operand`() = assertEquals(
        "(5)",
        type(CalculatorKey.Parenthesis, CalculatorKey.Digit(5), CalculatorKey.Parenthesis).expression,
    )

    @Test
    fun `a bracket after a number implies multiplication`() =
        assertEquals("5*(", type(CalculatorKey.Digit(5), CalculatorKey.Parenthesis).expression)

    @Test
    fun `the bracket key opens again after a closed group`() {
        val state = type(
            CalculatorKey.Parenthesis,
            CalculatorKey.Digit(5),
            CalculatorKey.Parenthesis,
            CalculatorKey.Operator(OperatorType.ADD),
            CalculatorKey.Parenthesis,
        )
        assertEquals("(5)+(", state.expression)
    }

    // endregion

    // region Clear and backspace

    @Test
    fun `clear empties the expression`() = assertTrue(type(*digits("123+456"), CalculatorKey.Clear).isEmpty)

    @Test
    fun `backspace removes one character`() =
        assertEquals("12", type(*digits("123"), CalculatorKey.Backspace).expression)

    @Test
    fun `backspace on an empty expression is harmless`() = assertTrue(type(CalculatorKey.Backspace).isEmpty)

    @Test
    fun `backspace after equals clears rather than editing the result`() {
        val state = type(*digits("2+3"), CalculatorKey.Equals, CalculatorKey.Backspace)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `backspace clears an error state`() {
        val state = type(*digits("5/0"), CalculatorKey.Equals, CalculatorKey.Backspace)
        assertTrue(state.isEmpty)
        assertNull(state.error)
    }

    // endregion

    // region Equals and chaining

    @Test
    fun `equals replaces the expression with the result`() {
        val state = type(*digits("2+3"), CalculatorKey.Equals)
        assertEquals("5", state.expression)
        assertTrue(state.justEvaluated)
    }

    @Test
    fun `a digit after equals starts a new calculation`() {
        val state = type(*digits("2+3"), CalculatorKey.Equals, CalculatorKey.Digit(7))
        assertEquals("7", state.expression)
    }

    @Test
    fun `an operator after equals continues from the result`() {
        val state = type(
            *digits("2+3"),
            CalculatorKey.Equals,
            CalculatorKey.Operator(OperatorType.MULTIPLY),
            CalculatorKey.Digit(2),
        )
        assertEquals("5*2", state.expression)
    }

    @Test
    fun `chained equals presses recompute from the shown result`() {
        val state = type(*digits("2+3"), CalculatorKey.Equals, CalculatorKey.Equals)
        assertEquals("5", state.expression)
    }

    @Test
    fun `equals on an invalid expression records the error`() {
        val state = type(*digits("5/0"), CalculatorKey.Equals)
        assertEquals(CalcError.DIVIDE_BY_ZERO, state.error)
    }

    @Test
    fun `a digit after an error starts fresh`() {
        val state = type(*digits("5/0"), CalculatorKey.Equals, CalculatorKey.Digit(9))
        assertEquals("9", state.expression)
        assertNull(state.error)
    }

    @Test
    fun `equals on an empty display does nothing`() = assertTrue(type(CalculatorKey.Equals).isEmpty)

    // endregion

    @Test
    fun `rapid mixed keypresses never produce an unparseable state`() {
        // Simulates someone mashing the keypad: whatever comes out must still be
        // something the engine can evaluate or reject cleanly, never a crash.
        val keys = listOf(
            CalculatorKey.Digit(1), CalculatorKey.Operator(OperatorType.ADD),
            CalculatorKey.Operator(OperatorType.MULTIPLY), CalculatorKey.Decimal,
            CalculatorKey.Percent, CalculatorKey.Parenthesis, CalculatorKey.Digit(0),
            CalculatorKey.Backspace, CalculatorKey.Equals, CalculatorKey.Digit(9),
            CalculatorKey.Parenthesis, CalculatorKey.Operator(OperatorType.DIVIDE),
            CalculatorKey.Digit(0), CalculatorKey.Equals, CalculatorKey.Clear,
        )

        var state = InputState.Empty
        repeat(20) {
            keys.forEach { key ->
                state = CalculatorInput.press(state, key)
                // The engine must always reach a verdict on whatever has been typed.
                CalculatorEngine.evaluate(state.expression)
            }
        }
    }

    // region Repeated equals

    @Test
    fun `equals on a finished result changes nothing`() {
        // The crash this guards: each extra press used to re-evaluate the answer and
        // file it as a new calculation, so three quick taps left three identical
        // history entries stamped with the same second.
        val evaluated = CalculatorInput.press(InputState.atEnd("2+3"), CalculatorKey.Equals)
        assertEquals("5", evaluated.expression)

        val again = CalculatorInput.press(evaluated, CalculatorKey.Equals)
        assertEquals(evaluated, again)
    }

    @Test
    fun `equals stays a no-op however many times it is pressed`() {
        var state = CalculatorInput.press(InputState.atEnd("12+8"), CalculatorKey.Equals)
        repeat(5) { state = CalculatorInput.press(state, CalculatorKey.Equals) }
        assertEquals("20", state.expression)
        assertTrue(state.justEvaluated)
    }

    @Test
    fun `typing after equals makes equals work again`() {
        // The guard keys on justEvaluated, which any other keypress clears, so a new
        // expression built on the result still evaluates.
        val evaluated = CalculatorInput.press(InputState.atEnd("2+3"), CalculatorKey.Equals)
        var state = CalculatorInput.press(evaluated, CalculatorKey.Operator(OperatorType.MULTIPLY))
        state = CalculatorInput.press(state, CalculatorKey.Digit(4))
        state = CalculatorInput.press(state, CalculatorKey.Equals)
        assertEquals("20", state.expression)
    }

    // endregion
}
