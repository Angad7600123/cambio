package io.github.angad7600123.cambio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.angad7600123.cambio.calculator.CalcResult
import io.github.angad7600123.cambio.calculator.CalculatorEngine
import io.github.angad7600123.cambio.calculator.CalculatorInput
import io.github.angad7600123.cambio.calculator.CalculatorKey
import io.github.angad7600123.cambio.calculator.InputState
import io.github.angad7600123.cambio.calculator.OperatorType
import io.github.angad7600123.cambio.currency.ConversionEngine
import io.github.angad7600123.cambio.currency.CurrencyCatalog
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.data.AppClock
import io.github.angad7600123.cambio.data.HistoryEntry
import io.github.angad7600123.cambio.data.HistoryRepository
import io.github.angad7600123.cambio.data.RateSnapshot
import io.github.angad7600123.cambio.data.RatesRepository
import io.github.angad7600123.cambio.data.RefreshState
import io.github.angad7600123.cambio.data.SettingsRepository
import io.github.angad7600123.cambio.data.ThemeMode
import io.github.angad7600123.cambio.data.UserSettings
import io.github.angad7600123.cambio.format.ExpressionFormatter
import io.github.angad7600123.cambio.format.NumberDisplayFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Drives the calculator screen.
 *
 * The ViewModel owns only *input* state. Everything else — rates, settings,
 * history — is observed from repositories and combined into a single
 * [CalculatorUiState]. Nothing here performs I/O directly or blocks: network work
 * happens in the repository on a coroutine, so the keypad stays responsive even
 * while rates are loading.
 */
class CalculatorViewModel(
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val catalog: CurrencyCatalog,
    private val numberFormatter: NumberDisplayFormatter,
    private val expressionFormatter: ExpressionFormatter,
    private val clock: AppClock = AppClock.System,
) : ViewModel() {
    private val inputState = MutableStateFlow(InputState.Empty)

    /** The expression that produced the currently displayed result, shown after `=`. */
    private val evaluatedExpression = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CalculatorUiState> = combine(
        inputState,
        evaluatedExpression,
        settingsRepository.settings,
        ratesRepository.snapshot,
        combine(ratesRepository.refreshState, historyRepository.history, ::Pair),
    ) { input, evaluated, settings, snapshot, (refreshState, history) ->
        buildUiState(input, evaluated, settings, snapshot, refreshState, history)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = initialUiState(),
    )

    init {
        refreshRatesIfNeeded()
    }

    // region Intents

    fun onKeyPress(key: CalculatorKey) {
        val before = inputState.value

        if (key == CalculatorKey.Equals) {
            onEquals(before)
            return
        }

        // Any other key leaves the "showing a finished result" mode.
        if (before.justEvaluated || before.error != null) {
            evaluatedExpression.value = null
        }
        inputState.value = CalculatorInput.press(before, key)
    }

    private fun onEquals(before: InputState) {
        val after = CalculatorInput.press(before, CalculatorKey.Equals)
        inputState.value = after

        if (after.justEvaluated && after.error == null) {
            evaluatedExpression.value = before.expression
            recordHistory(before.expression, after.expression)
        } else {
            evaluatedExpression.value = null
        }
    }

    fun onSwapCurrencies() {
        viewModelScope.launch { settingsRepository.swapCurrencies() }
    }

    fun onSelectFromCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setFromCurrency(code) }
    }

    fun onSelectToCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setToCurrency(code) }
    }

    fun onSetThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onSetUseSystemColors(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseSystemColors(enabled) }
    }

    fun onClearHistory() {
        viewModelScope.launch { historyRepository.clear() }
    }

    /** Restores a past calculation into the display. */
    fun onRestoreHistory(entry: HistoryEntry) {
        inputState.value = InputState(expression = entry.expression)
        evaluatedExpression.value = null
    }

    /** Explicit user-initiated refresh, used by the rate line and the error retry. */
    fun onRefreshRates() {
        viewModelScope.launch { ratesRepository.refresh() }
    }

    /** Refreshes when stale; safe to call on every resume. */
    fun refreshRatesIfNeeded() {
        viewModelScope.launch { ratesRepository.refreshIfStale() }
    }

    // endregion

    private fun recordHistory(expression: String, result: String) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val snapshot = ratesRepository.snapshot.first()
            val value = result.toBigDecimalOrNull()

            val converted = if (value != null && snapshot != null) {
                ConversionEngine.convert(
                    amount = value,
                    from = settings.fromCurrency,
                    to = settings.toCurrency,
                    rates = snapshot.rates,
                )?.let { numberFormatter.formatMoney(it, catalog.infoFor(settings.toCurrency)) }
            } else {
                null
            }

            historyRepository.add(
                HistoryEntry(
                    expression = expressionFormatter.format(expression),
                    result = value?.let(numberFormatter::format) ?: result,
                    fromCurrency = settings.fromCurrency,
                    toCurrency = settings.toCurrency,
                    convertedResult = converted,
                    timestampEpochSeconds = clock.nowEpochSeconds(),
                ),
            )
        }
    }

    private fun buildUiState(
        input: InputState,
        evaluated: String?,
        settings: UserSettings,
        snapshot: RateSnapshot?,
        refreshState: RefreshState,
        history: List<HistoryEntry>,
    ): CalculatorUiState {
        val from = catalog.infoFor(settings.fromCurrency)
        val to = catalog.infoFor(settings.toCurrency)
        val value = currentValue(input)

        val resultDisplay = when {
            input.error != null -> ""
            value != null -> numberFormatter.format(value)
            else -> ZERO_DISPLAY
        }

        // After `=` the top line shows the expression that produced the result;
        // while typing it shows what is being typed.
        val expressionDisplay = when {
            evaluated != null -> expressionFormatter.format(evaluated) + EQUALS_SUFFIX
            else -> expressionFormatter.formatSecondary(input.expression, resultDisplay)
        }

        return CalculatorUiState(
            expressionDisplay = expressionDisplay,
            resultDisplay = resultDisplay,
            calcError = input.error,
            fromCurrency = from,
            toCurrency = to,
            convertedDisplay = convertedDisplay(value, settings, snapshot, to),
            rateDisplay = rateDisplay(settings, snapshot, from, to),
            ratesStatus = ratesStatus(snapshot, refreshState),
            availableCurrencies = availableCurrencies(snapshot),
            rates = snapshot?.rates.orEmpty(),
            recentCurrencies = settings.recentCurrencies.map(catalog::infoFor),
            history = history,
            themeMode = settings.themeMode,
            useSystemColors = settings.useSystemColors,
        )
    }

    /**
     * The value to convert.
     *
     * While an expression is mid-typing it is often not yet valid ("12 +"), so the
     * trailing operator is dropped for the purposes of the live preview. That keeps
     * the converted amount stable and updating as the user types, rather than
     * flickering to nothing between keystrokes.
     */
    private fun currentValue(input: InputState): BigDecimal? {
        if (input.error != null) return null
        if (input.expression.isEmpty()) return BigDecimal.ZERO

        (CalculatorEngine.evaluate(input.expression) as? CalcResult.Success)?.let { return it.value }

        val trimmed = input.expression.trimEnd { OperatorType.fromSymbol(it) != null }
        if (trimmed.isEmpty() || trimmed == input.expression) return null

        return (CalculatorEngine.evaluate(trimmed) as? CalcResult.Success)?.value
    }

    private fun convertedDisplay(
        value: BigDecimal?,
        settings: UserSettings,
        snapshot: RateSnapshot?,
        to: CurrencyInfo,
    ): String? {
        if (value == null || snapshot == null) return null
        val converted = ConversionEngine.convert(
            amount = value,
            from = settings.fromCurrency,
            to = settings.toCurrency,
            rates = snapshot.rates,
        ) ?: return null
        return numberFormatter.formatMoney(converted, to)
    }

    private fun rateDisplay(
        settings: UserSettings,
        snapshot: RateSnapshot?,
        from: CurrencyInfo,
        to: CurrencyInfo,
    ): String? {
        if (snapshot == null) return null
        val rate = ConversionEngine.rate(
            from = settings.fromCurrency,
            to = settings.toCurrency,
            rates = snapshot.rates,
        ) ?: return null
        return "1 ${from.code} = ${numberFormatter.formatRate(rate)} ${to.code}"
    }

    private fun ratesStatus(snapshot: RateSnapshot?, refreshState: RefreshState): RatesStatus = when {
        snapshot != null -> RatesStatus.Ready(
            lastUpdatedEpochSeconds = snapshot.fetchedAtEpochSeconds,
            isStale = ratesRepository.isStale(snapshot),
            isRefreshing = refreshState is RefreshState.Refreshing,
        )

        refreshState is RefreshState.Failed -> RatesStatus.Unavailable(refreshState.error)

        else -> RatesStatus.Loading
    }

    private fun availableCurrencies(snapshot: RateSnapshot?): List<CurrencyInfo> =
        snapshot?.let { catalog.infoForAll(it.currencyCodes) } ?: emptyList()

    private fun initialUiState(): CalculatorUiState {
        val defaults = UserSettings.defaults()
        return CalculatorUiState(
            fromCurrency = catalog.infoFor(defaults.fromCurrency),
            toCurrency = catalog.infoFor(defaults.toCurrency),
        )
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val ZERO_DISPLAY = "0"
        const val EQUALS_SUFFIX = " ="
    }
}
