package io.github.angad7600123.cambio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.angad7600123.cambio.calculator.CalcError
import io.github.angad7600123.cambio.calculator.CalcResult
import io.github.angad7600123.cambio.calculator.CalculatorEngine
import io.github.angad7600123.cambio.calculator.CalculatorInput
import io.github.angad7600123.cambio.calculator.CalculatorKey
import io.github.angad7600123.cambio.calculator.InputState
import io.github.angad7600123.cambio.calculator.OperatorType
import io.github.angad7600123.cambio.currency.ConversionEngine
import io.github.angad7600123.cambio.currency.ConversionSide
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
import java.math.RoundingMode

/** A four-way tuple, since the standard library stops at [Triple]. */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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

    /**
     * A failure to surface as a toast.
     *
     * One UI leaves a bad expression on screen and floats a brief message rather
     * than blanking the display, so this is deliberately *not* part of the input
     * state — the typed expression survives untouched.
     */
    private val transientError = MutableStateFlow<CalcError?>(null)

    /**
     * Which currency the keypad is typing into.
     *
     * Conversion runs in whichever direction the active side implies, so the same
     * keypad serves both "what is 50 dollars in euros" and the reverse.
     */
    private val activeSide = MutableStateFlow(ConversionSide.SOURCE)

    val uiState: StateFlow<CalculatorUiState> = combine(
        combine(inputState, evaluatedExpression, transientError, activeSide, ::Quad),
        settingsRepository.settings,
        ratesRepository.snapshot,
        ratesRepository.refreshState,
        historyRepository.history,
    ) { (input, evaluated, _, side), settings, snapshot, refreshState, history ->
        buildUiState(input, evaluated, side, settings, snapshot, refreshState, history)
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

        transientError.value = null

        // Any other key leaves the "showing a finished result" mode.
        if (before.justEvaluated || before.error != null) {
            evaluatedExpression.value = null
        }
        inputState.value = CalculatorInput.press(before, key)
    }

    private fun onEquals(before: InputState) {
        val after = CalculatorInput.press(before, CalculatorKey.Equals)

        if (after.error != null) {
            // Keep exactly what the user typed; only float a message about it.
            transientError.value = after.error
            return
        }

        inputState.value = after
        if (after.justEvaluated) {
            evaluatedExpression.value = before.expression
            recordHistory(before.expression, after.expression)
        } else {
            evaluatedExpression.value = null
        }
    }

    /**
     * Moves the caret to the other currency.
     *
     * The figure carries across rather than resetting: whatever the other side was
     * showing becomes the new input, so switching sides never loses the amount and
     * the two readings stay equivalent.
     */
    fun onSelectSide(side: ConversionSide) {
        if (activeSide.value == side) return

        viewModelScope.launch {
            val snapshot = ratesRepository.snapshot.first()
            val settings = settingsRepository.settings.first()
            val current = currentValue(inputState.value)

            val carried = if (snapshot != null && current != null) {
                val (fromCode, toCode) = if (side == ConversionSide.TARGET) {
                    settings.fromCurrency to settings.toCurrency
                } else {
                    settings.toCurrency to settings.fromCurrency
                }
                ConversionEngine.convert(current, fromCode, toCode, snapshot.rates)
            } else {
                null
            }

            activeSide.value = side
            if (carried != null) {
                // Round to the currency the figure is moving into. Carrying the raw
                // quotient would drop six decimal places into the input field.
                val minorUnits = catalog.infoFor(
                    if (side == ConversionSide.SOURCE) settings.fromCurrency else settings.toCurrency,
                ).minorUnits
                inputState.value = InputState.atEnd(carried.asInput(minorUnits))
                evaluatedExpression.value = null
            }
        }
    }

    /** Moves the caret, so a digit can be corrected mid-number. */
    fun onCursorChange(position: Int) {
        inputState.value = CalculatorInput.moveCursor(inputState.value, position)
    }

    /** Called once the toast has been shown for its duration. */
    fun onTransientErrorShown() {
        transientError.value = null
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
        inputState.value = InputState.atEnd(entry.expression)
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
                val side = activeSide.value
                val fromCode = if (side == ConversionSide.SOURCE) settings.fromCurrency else settings.toCurrency
                val toCode = if (side == ConversionSide.SOURCE) settings.toCurrency else settings.fromCurrency
                ConversionEngine.convert(value, fromCode, toCode, snapshot.rates)
                    ?.let { numberFormatter.formatMoney(it, catalog.infoFor(toCode)) }
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
        side: ConversionSide,
        settings: UserSettings,
        snapshot: RateSnapshot?,
        refreshState: RefreshState,
        history: List<HistoryEntry>,
    ): CalculatorUiState {
        val from = catalog.infoFor(settings.fromCurrency)
        val to = catalog.infoFor(settings.toCurrency)
        val value = currentValue(input)

        // What was typed belongs to the active side; the other side is converted
        // from it, which is what makes the block work in both directions.
        val activeCurrency = if (side == ConversionSide.SOURCE) from else to
        val otherCurrency = if (side == ConversionSide.SOURCE) to else from
        val otherAmount = value?.let {
            convertBetween(it, activeCurrency.code, otherCurrency.code, snapshot)
        }

        val previewText = value?.let(numberFormatter::format) ?: ZERO_DISPLAY
        val isEditing = evaluated == null

        return CalculatorUiState(
            // The raw expression drives the field; the visual transformation adds
            // grouping and operator glyphs while keeping the caret aligned.
            activeText = input.expression,
            activeCursor = input.cursor,
            // Suppress the running total when it would only repeat the figure above.
            activePreview = if (isEditing && previewText != groupingOf(input.expression)) {
                previewText
            } else {
                ""
            },
            evaluatedExpression = evaluated
                ?.let { expressionFormatter.format(it) + EQUALS_SUFFIX }
                .orEmpty(),
            otherValue = otherAmount
                ?.let { numberFormatter.formatMoney(it, otherCurrency) }
                ?: PLACEHOLDER,
            activeSide = side,
            transientError = transientError.value,
            fromCurrency = from,
            toCurrency = to,
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
     * The expression as it will appear once grouped, used only to decide whether the
     * running total would be a duplicate of it.
     */
    private fun groupingOf(expression: String): String = expressionFormatter.format(expression)

    /** Converts between two currencies, or null when rates are not yet available. */
    private fun convertBetween(
        amount: BigDecimal,
        fromCode: String,
        toCode: String,
        snapshot: RateSnapshot?,
    ): BigDecimal? {
        if (snapshot == null) return null
        return ConversionEngine.convert(amount, fromCode, toCode, snapshot.rates)
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

    /** Rounds a converted figure to something sane to keep typing into. */
    private fun BigDecimal.asInput(minorUnits: Int): String =
        setScale(minorUnits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun String.toBigDecimalOrNull(): BigDecimal? = try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val ZERO_DISPLAY = "0"
        const val PLACEHOLDER = "\u2014"
        const val EQUALS_SUFFIX = " ="
    }
}
