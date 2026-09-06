package io.github.angad7600123.cambio

import android.app.Application
import io.github.angad7600123.cambio.di.AppContainer
import io.github.angad7600123.cambio.widget.WidgetSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Owns the process-wide [AppContainer], so the activity and the home-screen widget
 * share one set of repositories and therefore one consistent view of the cached
 * rates and the selected currency pair.
 *
 * Sharing the repositories is not by itself enough to keep the widget current — it
 * reads them only when something asks it to redraw — so [WidgetSync] watches them
 * here and does the asking.
 */
class CambioApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Lives as long as the process; nothing here should outlive it. */
    private val scope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        WidgetSync(
            context = this,
            settingsRepository = container.settingsRepository,
            ratesRepository = container.ratesRepository,
        ).start(scope)
    }

    override fun onTerminate() {
        // Only ever called on emulators, but leaving a scope running is untidy.
        scope.cancel()
        super.onTerminate()
    }
}
