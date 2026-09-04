package io.github.angad7600123.cambio.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.angad7600123.cambio.calculator.CalcError
import io.github.angad7600123.cambio.calculator.CalculatorInput
import io.github.angad7600123.cambio.calculator.CalculatorKey
import io.github.angad7600123.cambio.calculator.InputState
import io.github.angad7600123.cambio.calculator.OperatorType

/** Every key the widget can send, as a stable string safe to put in an intent. */
enum class WidgetAction(val key: CalculatorKey) {
    DIGIT_0(CalculatorKey.Digit(0)),
    DIGIT_1(CalculatorKey.Digit(1)),
    DIGIT_2(CalculatorKey.Digit(2)),
    DIGIT_3(CalculatorKey.Digit(3)),
    DIGIT_4(CalculatorKey.Digit(4)),
    DIGIT_5(CalculatorKey.Digit(5)),
    DIGIT_6(CalculatorKey.Digit(6)),
    DIGIT_7(CalculatorKey.Digit(7)),
    DIGIT_8(CalculatorKey.Digit(8)),
    DIGIT_9(CalculatorKey.Digit(9)),
    ADD(CalculatorKey.Operator(OperatorType.ADD)),
    SUBTRACT(CalculatorKey.Operator(OperatorType.SUBTRACT)),
    MULTIPLY(CalculatorKey.Operator(OperatorType.MULTIPLY)),
    DIVIDE(CalculatorKey.Operator(OperatorType.DIVIDE)),
    DECIMAL(CalculatorKey.Decimal),
    PERCENT(CalculatorKey.Percent),
    PAREN(CalculatorKey.Parenthesis),
    CLEAR(CalculatorKey.Clear),
    BACKSPACE(CalculatorKey.Backspace),
    EQUALS(CalculatorKey.Equals),
}

/**
 * Applies a widget keypress.
 *
 * The widget stores only its expression string, so each press rehydrates an
 * [InputState], runs it through the very same [CalculatorInput] state machine the
 * app uses, and writes the result back. There is no second calculator
 * implementation to drift out of sync.
 */
class WidgetKeyActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val actionName = parameters[ACTION_KEY] ?: return
        val action = runCatching { WidgetAction.valueOf(actionName) }.getOrNull() ?: return

        updateAppWidgetState(context, glanceId) { preferences ->
            val next = CalculatorInput.press(preferences.toInputState(), action.key)
            preferences.write(next)
        }

        CambioWidget().update(context, glanceId)
    }

    private fun MutablePreferences.toInputState(): InputState = InputState(
        expression = this[WidgetStateKeys.EXPRESSION].orEmpty(),
        justEvaluated = this[WidgetStateKeys.JUST_EVALUATED] == TRUE,
        // The error is stored by name so the specific cause survives the
        // round-trip, rather than collapsing to a generic failure.
        error = this[WidgetStateKeys.ERROR]
            ?.takeIf { it.isNotEmpty() }
            ?.let { name -> runCatching { CalcError.valueOf(name) }.getOrNull() },
    )

    private fun MutablePreferences.write(state: InputState) {
        this[WidgetStateKeys.EXPRESSION] = state.expression
        this[WidgetStateKeys.JUST_EVALUATED] = state.justEvaluated.toString()
        this[WidgetStateKeys.ERROR] = state.error?.name.orEmpty()
    }

    companion object {
        private val ACTION_KEY = ActionParameters.Key<String>("cambio_widget_action")
        private const val TRUE = "true"

        fun parametersOf(action: WidgetAction): ActionParameters = actionParametersOf(ACTION_KEY to action.name)
    }
}
