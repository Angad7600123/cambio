package io.github.angad7600123.cambio

import android.app.Application
import io.github.angad7600123.cambio.di.AppContainer

/**
 * Owns the process-wide [AppContainer], so the activity and the home-screen widget
 * share one set of repositories and therefore one consistent view of the cached
 * rates and the selected currency pair.
 */
class CambioApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
