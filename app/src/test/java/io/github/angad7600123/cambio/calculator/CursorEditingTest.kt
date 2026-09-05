package io.github.angad7600123.cambio.calculator

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for editing at the caret rather than only at the end.
 *
 * Fixing a wrong digit in the middle of a long figure without retyping the tail is
 * the behaviour these lock down; before the caret existed, the only recourse was to
 * backspace all the way back to the mistake.
 */
class CursorEditingTest {

    private fun at(expression: String, cursor: Int) = InputState(expression, cursor)

    private fun press(state: InputState, key: CalculatorKey) = CalculatorInput.press(state, key)

    @Test
    fun `a digit is inserted at the caret, not appended`() {
        val result = press(at("1234", 2), CalculatorKey.Digit(9))
        assertEquals("12934", result.expression)
    }

    @Test
    fun `the caret advances past what was inserted`() {
        val result = press(at("1234", 2), CalculatorKey.Digit(9))
        assertEquals(3, result.cursor)
    }

    @Test
    fun `inserting at the start works`() {
        val result = press(at("250", 0), CalculatorKey.Digit(1))
        assertEquals("1250", result.expression)
        assertEquals(1, result.cursor)
    }

    @Test
    fun `inserting at the end behaves like appending`() {
        val result = press(at("250", 3), CalculatorKey.Digit(7))
        assertEquals("2507", result.expression)
        assertEquals(4, result.cursor)
    }

    @Test
    fun `backspace deletes the character before the caret`() {
        val result = press(at("1234", 2), CalculatorKey.Backspace)
        assertEquals("134", result.expression)
        assertEquals(1, result.cursor)
    }

    @Test
    fun `backspace at the start does nothing`() {
        val result = press(at("1234", 0), CalculatorKey.Backspace)
        assertEquals("1234", result.expression)
        assertEquals(0, result.cursor)
    }

    @Test
    fun `an operator inserted mid-expression splits it`() {
        val result = press(at("1234", 2), CalculatorKey.Operator(OperatorType.ADD))
        assertEquals("12+34", result.expression)
    }

    @Test
    fun `the digit cap counts the whole number around the caret`() {
        val fifteen = "9".repeat(CalculatorInput.MAX_DIGITS_PER_NUMBER)
        // Caret in the middle of an already-full number: nothing more may go in.
        val result = press(at(fifteen, 7), CalculatorKey.Digit(1))
        assertEquals(fifteen, result.expression)
    }

    @Test
    fun `a decimal point is refused when the number around the caret already has one`() {
        val result = press(at("12.34", 2), CalculatorKey.Decimal)
        assertEquals("12.34", result.expression)
    }

    @Test
    fun `a decimal point is allowed in a different number`() {
        // Caret sits in the second operand, which has no point of its own yet.
        val result = press(at("1.5+27", 6), CalculatorKey.Decimal)
        assertEquals("1.5+27.", result.expression)
    }

    @Test
    fun `moving the caret clamps to the expression`() {
        assertEquals(4, CalculatorInput.moveCursor(at("1234", 0), 99).cursor)
        assertEquals(0, CalculatorInput.moveCursor(at("1234", 4), -5).cursor)
    }

    @Test
    fun `clearing resets the caret`() {
        val result = press(at("1234", 2), CalculatorKey.Clear)
        assertEquals("", result.expression)
        assertEquals(0, result.cursor)
    }

    @Test
    fun `equals moves the caret to the end of the result`() {
        val result = press(at("2+3", 1), CalculatorKey.Equals)
        assertEquals("5", result.expression)
        assertEquals(1, result.cursor)
    }

    @Test
    fun `correcting a digit mid-number keeps the rest intact`() {
        // "12345678901234" with a wrong digit at index 4: delete it and type the
        // right one, without touching the nine digits that follow.
        var state = at("12345678901234", 5)
        state = press(state, CalculatorKey.Backspace)
        state = press(state, CalculatorKey.Digit(9))
        assertEquals("12349678901234", state.expression)
    }
}
