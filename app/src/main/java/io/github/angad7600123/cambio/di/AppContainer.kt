package io.github.angad7600123.cambio.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.angad7600123.cambio.currency.CurrencyCatalog
import io.github.angad7600123.cambio.data.AppClock
import io.github.angad7600123.cambio.data.DataStoreRatesCache
import io.github.angad7600123.cambio.data.HistoryRepository
import io.github.angad7600123.cambio.data.RatesRemoteDataSource
import io.github.angad7600123.cambio.data.RatesRepository
import io.github.angad7600123.cambio.data.SettingsRepository
import io.github.angad7600123.cambio.data.cambioDataStore
import io.github.angad7600123.cambio.format.ExpressionFormatter
import io.github.angad7600123.cambio.format.NumberDisplayFormatter
import io.github.angad7600123.cambio.ui.CalculatorViewModel

/**
 * Manual dependency injection.
 *
 * A project this size does not need an annotation processor and the build time
 * that comes with it. One container, constructed once per process and holding
 * lazily-created singletons, gives the same testability — every consumer takes its
 * dependencies through its constructor — with far less machinery for a reader to
 * unpick.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: AppClock = AppClock.System

    val catalog: CurrencyCatalog by lazy { CurrencyCatalog() }

    val numberFormatter: NumberDisplayFormatter by lazy { NumberDisplayFormatter() }

    val expressionFormatter: ExpressionFormatter by lazy {
        ExpressionFormatter(numberFormatter)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext.cambioDataStore)
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(appContext.cambioDataStore)
    }

    val ratesRepository: RatesRepository by lazy {
        RatesRepository(
            remote = RatesRemoteDataSource(),
            cache = DataStoreRatesCache(appContext.cambioDataStore),
            clock = clock,
        )
    }

    /** Builds the calculator ViewModel with everything it needs. */
    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CalculatorViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return CalculatorViewModel(
                ratesRepository = ratesRepository,
                settingsRepository = settingsRepository,
                historyRepository = historyRepository,
                catalog = catalog,
                numberFormatter = numberFormatter,
                expressionFormatter = expressionFormatter,
                clock = clock,
            ) as T
        }
    }
}
