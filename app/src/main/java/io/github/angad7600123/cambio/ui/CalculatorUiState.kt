package io.github.angad7600123.cambio.ui

import androidx.compose.runtime.Immutable
import io.github.angad7600123.cambio.calculator.CalcError
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.data.HistoryEntry
import io.github.angad7600123.cambio.data.RatesError
import io.github.angad7600123.cambio.data.ThemeMode
import java.math.BigDecimal

/** What the app knows about the rate table right now. */
@Immutable
sealed interface RatesStatus {
    /** First load, with nothing cached yet to show. */
    data object Loading : RatesStatus

    /**
     * Rates are available.
     *
     * @property isStale true when the provider's stated update time has passed and
     *   a refresh has not yet succeeded. The figures are still shown — they are the
     *   best available — but the UI marks them.
     * @property isRefreshing true while a background refresh is in flight.
     */
    data class Ready(val lastUpdatedEpochSeconds: Long, val isStale: Boolean, val isRefreshing: Boolean) : RatesStatus

    /** No rates at all: the first fetch failed and there is no cache to fall back on. */
    data class Unavailable(val error: RatesError) : RatesStatus
}

/**
 * Everything the calculator screen renders, as one immutable snapshot.
 *
 * All values are pre-formatted strings. Formatting decisions (locale separators,
 * minor units, scientific notation) belong to the presentation layer and are
 * resolved before this state reaches a composable, which keeps the UI code free of
 * numeric logic and makes the state trivially assertable in tests.
 */
@Immutable
data class CalculatorUiState(
    /** The large line: the expression while typing, the result after equals. */
    val primaryDisplay: String = "0",
    /** The small line beneath it: the running preview, or the evaluated expression. */
    val secondaryDisplay: String = "",
    /**
     * True while an expression is being typed, so the primary line shows the caret
     * and tints its operators. False once equals has produced a result.
     */
    val isEditing: Boolean = true,
    /**
     * A transient failure to surface as a toast. One UI does not blank the display
     * on a bad expression, it floats a brief message and leaves the input intact.
     */
    val transientError: CalcError? = null,
    val fromCurrency: CurrencyInfo,
    val toCurrency: CurrencyInfo,
    val convertedDisplay: String? = null,
    val rateDisplay: String? = null,
    val ratesStatus: RatesStatus = RatesStatus.Loading,
    val availableCurrencies: List<CurrencyInfo> = emptyList(),
    /** Raw rates against the fetch base, so the picker can preview each row. */
    val rates: Map<String, BigDecimal> = emptyMap(),
    val recentCurrencies: List<CurrencyInfo> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useSystemColors: Boolean = false,
) {
    /** True when there is a value worth copying or converting. */
    val hasValue: Boolean get() = primaryDisplay.isNotEmpty()
}
