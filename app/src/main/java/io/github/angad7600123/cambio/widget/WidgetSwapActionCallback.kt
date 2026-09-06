package io.github.angad7600123.cambio.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import io.github.angad7600123.cambio.data.SettingsRepository
import io.github.angad7600123.cambio.data.cambioDataStore

/**
 * Swaps the two currencies from the widget.
 *
 * Only the pair is exchanged; what has been typed stays where it is and simply
 * means the other currency now, which is the app's behaviour and the reason the
 * control is worth having on a home screen at all — one tap turns "what is this in
 * euros" into "what is this in dollars".
 *
 * The write goes to the shared settings, so the app sees it too, and every placed
 * widget is repainted rather than only the one that was tapped.
 *
 * The repaint is explicit here and not left to [WidgetSync]. A callback can cold
 * start the process, in which case the sync begins collecting at the same moment
 * this writes, and whether it sees the write as its first emission — which it
 * ignores — or its second is a race. Asking directly costs one redundant redraw in
 * the common case and removes the race in the uncommon one.
 */
class WidgetSwapActionCallback : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SettingsRepository(context.cambioDataStore).swapCurrencies()
        CambioWidget().updateAll(context)
    }
}
