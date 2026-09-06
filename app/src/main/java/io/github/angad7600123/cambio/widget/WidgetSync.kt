package io.github.angad7600123.cambio.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.github.angad7600123.cambio.data.RateSnapshot
import io.github.angad7600123.cambio.data.RatesRepository
import io.github.angad7600123.cambio.data.SettingsRepository
import io.github.angad7600123.cambio.data.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Keeps the home-screen widget in step with the app.
 *
 * A Glance widget is pull-based: it reads its data once, in `provideGlance`, and
 * that only runs again when something asks the widget to update. Nothing did. The
 * app would write a new currency to DataStore and the widget would carry on showing
 * the old one — until you pressed a key on it, because a keypress writes widget
 * state, which forces the reload as a side effect. Changing the currency in the app
 * and seeing nothing happen until you typed was that gap, exactly.
 *
 * So the app tells it. Both the currency pair and the rate table are watched,
 * because the widget shows a converted figure and a stale rate is as wrong as a
 * stale currency.
 *
 * This runs for the life of the process, which is the right scope: only the app can
 * change a setting, and it has to be running to do so. The widget's own controls
 * already refresh it directly from their action callbacks.
 */
class WidgetSync(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settingsRepository.settings, ratesRepository.snapshot, ::WidgetInputs)
                .distinctUntilChanged()
                // The first emission is the state the widget already drew itself
                // from; updating for it would repaint every widget on every launch.
                .drop(1)
                .collect { CambioWidget().updateAll(context) }
        }
    }
}

/**
 * What the widget shows, reduced to the parts worth redrawing for.
 *
 * Deliberately not the whole of [UserSettings]: theme choices do not reach the
 * widget, and including them would repaint it whenever the user changed one.
 */
private data class WidgetInputs(val from: String, val to: String, val rates: Map<String, String>) {
    constructor(settings: UserSettings, snapshot: RateSnapshot?) : this(
        from = settings.fromCurrency,
        to = settings.toCurrency,
        rates = snapshot?.rates?.mapValues { (_, rate) -> rate.toPlainString() }.orEmpty(),
    )
}
