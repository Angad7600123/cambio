package io.github.angad7600123.cambio.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.angad7600123.cambio.calculator.CalcResult
import io.github.angad7600123.cambio.calculator.CalculatorEngine
import io.github.angad7600123.cambio.calculator.OperatorType
import io.github.angad7600123.cambio.currency.ConversionEngine
import io.github.angad7600123.cambio.currency.ConversionSide
import io.github.angad7600123.cambio.currency.CurrencyCatalog
import io.github.angad7600123.cambio.data.DataStoreRatesCache
import io.github.angad7600123.cambio.data.SettingsRepository
import io.github.angad7600123.cambio.data.cambioDataStore
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Moves the widget's caret to the other currency.
 *
 * The figure carries across rather than resetting: whatever the other column was
 * showing becomes the new input, so tapping between the two never loses the amount
 * and both readings stay equivalent. Without that, switching sides would silently
 * reinterpret the typed number as a different currency.
 */
class WidgetSideActionCallback : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val requested = parameters[SIDE_KEY]
            ?.let { name -> runCatching { ConversionSide.valueOf(name) }.getOrNull() }
            ?: return

        val settings = SettingsRepository(context.cambioDataStore).settings.first()
        val snapshot = DataStoreRatesCache(context.cambioDataStore).snapshot.first()

        updateAppWidgetState(context, glanceId) { preferences ->
            val current = runCatching {
                ConversionSide.valueOf(preferences[WidgetStateKeys.ACTIVE_SIDE].orEmpty())
            }.getOrDefault(ConversionSide.TARGET)

            if (current == requested) return@updateAppWidgetState

            val typed = evaluate(preferences[WidgetStateKeys.EXPRESSION].orEmpty())
            val carried = if (typed != null && snapshot != null) {
                // Convert out of the side being left and into the one being entered.
                val fromCode = if (requested == ConversionSide.TARGET) {
                    settings.fromCurrency
                } else {
                    settings.toCurrency
                }
                val toCode = if (requested == ConversionSide.TARGET) {
                    settings.toCurrency
                } else {
                    settings.fromCurrency
                }
                ConversionEngine.convert(typed, fromCode, toCode, snapshot.rates)
            } else {
                null
            }

            preferences[WidgetStateKeys.ACTIVE_SIDE] = requested.name
            preferences[WidgetStateKeys.ERROR] = ""
            preferences[WidgetStateKeys.JUST_EVALUATED] = false.toString()
            if (carried != null) {
                // Round to the currency being entered; the raw quotient would drop
                // six decimal places into the input.
                val minorUnits = CurrencyCatalog().infoFor(
                    if (requested == ConversionSide.SOURCE) settings.fromCurrency else settings.toCurrency,
                ).minorUnits
                preferences[WidgetStateKeys.EXPRESSION] = carried
                    .setScale(minorUnits, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString()
            }
        }

        CambioWidget().update(context, glanceId)
    }

    /** The value of what is typed, ignoring a trailing operator. */
    private fun evaluate(expression: String): BigDecimal? {
        if (expression.isEmpty()) return BigDecimal.ZERO
        (CalculatorEngine.evaluate(expression) as? CalcResult.Success)?.let { return it.value }
        val trimmed = expression.trimEnd { OperatorType.fromSymbol(it) != null }
        if (trimmed.isEmpty() || trimmed == expression) return null
        return (CalculatorEngine.evaluate(trimmed) as? CalcResult.Success)?.value
    }

    companion object {
        private val SIDE_KEY = ActionParameters.Key<String>("cambio_widget_side")

        fun parametersOf(side: ConversionSide): ActionParameters = actionParametersOf(SIDE_KEY to side.name)
    }
}
