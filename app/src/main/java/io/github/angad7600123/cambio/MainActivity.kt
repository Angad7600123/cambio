package io.github.angad7600123.cambio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.angad7600123.cambio.ui.CalculatorScreen
import io.github.angad7600123.cambio.ui.CalculatorViewModel
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * The single activity hosting the calculator.
 *
 * Edge-to-edge is enabled so the true-black canvas runs behind the system bars;
 * the content itself is inset with `safeDrawingPadding` inside the screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as CambioApplication).container

        setContent {
            val viewModel: CalculatorViewModel = viewModel(factory = container.viewModelFactory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Rates can expire while the app sits in the background, so check on every
            // return to the foreground. The call is a no-op when they are still fresh.
            LifecycleResumeEffect(viewModel) {
                viewModel.refreshRatesIfNeeded()
                onPauseOrDispose { }
            }

            CambioTheme(
                themeMode = state.themeMode,
                useSystemColors = state.useSystemColors,
            ) {
                CalculatorScreen(
                    state = state,
                    onKeyPress = viewModel::onKeyPress,
                    onSwapCurrencies = viewModel::onSwapCurrencies,
                    onSelectFromCurrency = viewModel::onSelectFromCurrency,
                    onSelectToCurrency = viewModel::onSelectToCurrency,
                    onRefreshRates = viewModel::onRefreshRates,
                    onRestoreHistory = viewModel::onRestoreHistory,
                    onClearHistory = viewModel::onClearHistory,
                    onThemeModeChange = viewModel::onSetThemeMode,
                    onUseSystemColorsChange = viewModel::onSetUseSystemColors,
                )
            }
        }
    }
}
