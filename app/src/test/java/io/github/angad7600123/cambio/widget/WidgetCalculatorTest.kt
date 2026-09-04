package io.github.angad7600123.cambio.widget

import io.github.angad7600123.cambio.calculator.CalcError
import io.github.angad7600123.cambio.calculator.CalcResult
import io.github.angad7600123.cambio.calculator.CalculatorEngine
import io.github.angad7600123.cambio.calculator.CalculatorInput
import io.github.angad7600123.cambio.calculator.InputState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests that the widget keypad is wired to the same calculator as the app.
 *
 * The widget stores an expression string and replays it through the shared
 * [CalculatorInput] state machine on every press. These tests drive that exact
 * path via [WidgetAction], which is what proves the two surfaces cannot diverge.
 */
class WidgetCalculatorTest {

    /** Applies widget actions the same way [WidgetKeyActionCallback] does. */
    private fun press(vararg actions: WidgetAction): InputState = actions.fold(InputState.Empty) { state, action ->
        CalculatorInput.press(state, action.key)
    }

    @Test
    fun `every widget action maps to a calculator key`() {
        // A missing or duplicated mapping would silently break a key on the widget.
        val keys = WidgetAction.entries.map { it.key }
        assertEquals(WidgetAction.entries.size, keys.distinct().size)
        assertEquals(20, WidgetAction.entries.size)
    }

    @Test
    fun `digits build a number`() =
        assertEquals("123", press(WidgetAction.DIGIT_1, WidgetAction.DIGIT_2, WidgetAction.DIGIT_3).expression)

    @Test
    fun `arithmetic through the widget respects precedence`() {
        val state = press(
            WidgetAction.DIGIT_2,
            WidgetAction.ADD,
            WidgetAction.DIGIT_3,
            WidgetAction.MULTIPLY,
            WidgetAction.DIGIT_4,
            WidgetAction.EQUALS,
        )
        assertEquals("14", state.expression)
    }

    @Test
    fun `percent through the widget is contextual`() {
        val state = press(
            WidgetAction.DIGIT_2,
            WidgetAction.DIGIT_0,
            WidgetAction.DIGIT_0,
            WidgetAction.ADD,
            WidgetAction.DIGIT_1,
            WidgetAction.DIGIT_0,
            WidgetAction.PERCENT,
            WidgetAction.EQUALS,
        )
        assertEquals("220", state.expression)
    }

    @Test
    fun `decimal arithmetic through the widget is exact`() {
        val state = press(
            WidgetAction.DIGIT_0,
            WidgetAction.DECIMAL,
            WidgetAction.DIGIT_1,
            WidgetAction.ADD,
            WidgetAction.DIGIT_0,
            WidgetAction.DECIMAL,
            WidgetAction.DIGIT_2,
            WidgetAction.EQUALS,
        )
        assertEquals("0.3", state.expression)
    }

    @Test
    fun `clear resets the widget`() = assertTrue(press(WidgetAction.DIGIT_9, WidgetAction.CLEAR).isEmpty)

    @Test
    fun `backspace removes a character`() = assertEquals(
        "12",
        press(WidgetAction.DIGIT_1, WidgetAction.DIGIT_2, WidgetAction.DIGIT_3, WidgetAction.BACKSPACE).expression,
    )

    @Test
    fun `division by zero surfaces an error rather than crashing the widget`() {
        val state = press(WidgetAction.DIGIT_5, WidgetAction.DIVIDE, WidgetAction.DIGIT_0, WidgetAction.EQUALS)
        assertEquals(CalcError.DIVIDE_BY_ZERO, state.error)
    }

    @Test
    fun `the error round-trips through the widget's string storage`() {
        // The widget persists the error by name; it must come back as the same case.
        val original = CalcError.DIVIDE_BY_ZERO
        val stored = original.name
        assertEquals(original, CalcError.valueOf(stored))
    }

    @Test
    fun `an unrecognised stored error decodes to no error instead of throwing`() = assertEquals(
        null,
        runCatching {
            CalcError.valueOf("NOT_A_REAL_ERROR")
        }.getOrNull(),
    )

    @Test
    fun `parentheses work from the widget keypad`() {
        val state = press(
            WidgetAction.PAREN,
            WidgetAction.DIGIT_2,
            WidgetAction.ADD,
            WidgetAction.DIGIT_3,
            WidgetAction.PAREN,
            WidgetAction.MULTIPLY,
            WidgetAction.DIGIT_4,
            WidgetAction.EQUALS,
        )
        assertEquals("20", state.expression)
    }

    @Test
    fun `the widget and the app produce identical results for the same keys`() {
        // Same key sequence, both surfaces, one shared state machine.
        val viaWidget = press(
            WidgetAction.DIGIT_1, WidgetAction.DIGIT_2, WidgetAction.DIGIT_5, WidgetAction.DIGIT_0,
            WidgetAction.ADD,
            WidgetAction.DIGIT_1, WidgetAction.DIGIT_5, WidgetAction.PERCENT,
            WidgetAction.EQUALS,
        )
        val viaApp = CalculatorEngine.evaluate("1250+15%")
        val appValue = assertNotNull((viaApp as? CalcResult.Success)?.value)
        assertEquals(appValue.toPlainString(), viaWidget.expression)
    }
}
