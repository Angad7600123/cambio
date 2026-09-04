package io.github.angad7600123.cambio.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.angad7600123.cambio.data.DataStoreRatesCache
import io.github.angad7600123.cambio.data.RatesRemoteDataSource
import io.github.angad7600123.cambio.data.RatesRepository
import io.github.angad7600123.cambio.data.cambioDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hosts [CambioWidget] on the home screen.
 *
 * When the system asks the widget to update, this also refreshes the rate table if
 * the provider's stated update time has passed. The refresh is fire-and-forget on
 * a background scope: the widget renders immediately from cache and simply
 * re-renders if newer rates arrive, so a slow network never delays the UI.
 */
class CambioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CambioWidget()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshRatesIfStale(context)
    }

    private fun refreshRatesIfStale(context: Context) {
        val appContext = context.applicationContext
        val repository = RatesRepository(
            remote = RatesRemoteDataSource(),
            cache = DataStoreRatesCache(appContext.cambioDataStore),
        )
        scope.launch {
            repository.refreshIfStale()
        }
    }
}
