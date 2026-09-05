package io.github.angad7600123.cambio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.calculator.CalcError
import io.github.angad7600123.cambio.calculator.CalculatorKey
import io.github.angad7600123.cambio.currency.ConversionSide
import io.github.angad7600123.cambio.data.HistoryEntry
import io.github.angad7600123.cambio.data.ThemeMode
import io.github.angad7600123.cambio.ui.components.CalcToast
import io.github.angad7600123.cambio.ui.components.ConverterBlock
import io.github.angad7600123.cambio.ui.components.Keypad
import io.github.angad7600123.cambio.ui.components.RateLine
import io.github.angad7600123.cambio.ui.sheets.CurrencyPickerContent
import io.github.angad7600123.cambio.ui.sheets.HistoryContent
import io.github.angad7600123.cambio.ui.sheets.SettingsContent
import io.github.angad7600123.cambio.ui.theme.CambioTheme
import java.text.DateFormat
import java.util.Date

/** Which modal sheet, if any, is open. */
private sealed interface ActiveSheet {
    data object None : ActiveSheet

    data object FromCurrency : ActiveSheet

    data object ToCurrency : ActiveSheet

    data object History : ActiveSheet

    data object Settings : ActiveSheet
}

/**
 * The calculator screen.
 *
 * Layout adapts to the available space rather than locking orientation: in
 * portrait the display sits above the keypad; in landscape and on wide screens
 * they sit side by side, so the keypad never squashes into an unusable strip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    onKeyPress: (CalculatorKey) -> Unit,
    onSwapCurrencies: () -> Unit,
    onSelectSide: (ConversionSide) -> Unit,
    onCursorChange: (Int) -> Unit,
    onSelectFromCurrency: (String) -> Unit,
    onSelectToCurrency: (String) -> Unit,
    onRefreshRates: () -> Unit,
    onRestoreHistory: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUseSystemColorsChange: (Boolean) -> Unit,
    onTransientErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CambioTheme.colors
    var activeSheet by remember { mutableStateOf<ActiveSheet>(ActiveSheet.None) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth > maxHeight && maxWidth >= WIDE_LAYOUT_MIN_WIDTH

            if (isWide) {
                WideLayout(
                    state = state,
                    onKeyPress = onKeyPress,
                    onSwapCurrencies = onSwapCurrencies,
                    onSelectSide = onSelectSide,
                    onCursorChange = onCursorChange,
                    onRefreshRates = onRefreshRates,
                    onOpenSheet = { activeSheet = it },
                )
            } else {
                TallLayout(
                    state = state,
                    onKeyPress = onKeyPress,
                    onSwapCurrencies = onSwapCurrencies,
                    onSelectSide = onSelectSide,
                    onCursorChange = onCursorChange,
                    onRefreshRates = onRefreshRates,
                    onOpenSheet = { activeSheet = it },
                )
            }
        }

        CalcToast(
            message = state.transientError?.let { stringResource(it.messageRes()) },
            onDismiss = onTransientErrorShown,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = TOAST_BOTTOM_INSET),
        )
    }

    if (activeSheet != ActiveSheet.None) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.None },
            sheetState = sheetState,
            containerColor = colors.surface,
        ) {
            when (activeSheet) {
                ActiveSheet.FromCurrency -> CurrencyPickerContent(
                    currencies = state.availableCurrencies,
                    recents = state.recentCurrencies,
                    selectedCode = state.fromCurrency.code,
                    rates = state.rates,
                    referenceCode = state.fromCurrency.code,
                    onSelect = {
                        onSelectFromCurrency(it)
                        activeSheet = ActiveSheet.None
                    },
                )

                ActiveSheet.ToCurrency -> CurrencyPickerContent(
                    currencies = state.availableCurrencies,
                    recents = state.recentCurrencies,
                    selectedCode = state.toCurrency.code,
                    rates = state.rates,
                    // Rows preview what one unit of the source currency buys.
                    referenceCode = state.fromCurrency.code,
                    onSelect = {
                        onSelectToCurrency(it)
                        activeSheet = ActiveSheet.None
                    },
                )

                ActiveSheet.History -> HistoryContent(
                    history = state.history,
                    onRestore = {
                        onRestoreHistory(it)
                        activeSheet = ActiveSheet.None
                    },
                    onClear = onClearHistory,
                )

                ActiveSheet.Settings -> SettingsContent(
                    themeMode = state.themeMode,
                    useSystemColors = state.useSystemColors,
                    onThemeModeChange = onThemeModeChange,
                    onUseSystemColorsChange = onUseSystemColorsChange,
                )

                ActiveSheet.None -> Unit
            }
        }
    }
}

/** Portrait: top bar, display, then keypad anchored to the bottom. */
@Composable
private fun TallLayout(
    state: CalculatorUiState,
    onKeyPress: (CalculatorKey) -> Unit,
    onSwapCurrencies: () -> Unit,
    onSelectSide: (ConversionSide) -> Unit,
    onCursorChange: (Int) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenSheet: (ActiveSheet) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SCREEN_PADDING),
    ) {
        TopBar(
            onHistoryClick = { onOpenSheet(ActiveSheet.History) },
            onSettingsClick = { onOpenSheet(ActiveSheet.Settings) },
        )

        DisplaySection(
            state = state,
            onSwapCurrencies = onSwapCurrencies,
            onSelectSide = onSelectSide,
            onCursorChange = onCursorChange,
            onRefreshRates = onRefreshRates,
            onOpenSheet = onOpenSheet,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(KEYPAD_TOP_GAP))

        Keypad(
            onKeyPress = onKeyPress,
            modifier = Modifier.padding(bottom = KEYPAD_BOTTOM_MARGIN),
        )
    }
}

/** Landscape and tablets: display and keypad side by side. */
@Composable
private fun WideLayout(
    state: CalculatorUiState,
    onKeyPress: (CalculatorKey) -> Unit,
    onSwapCurrencies: () -> Unit,
    onSelectSide: (ConversionSide) -> Unit,
    onCursorChange: (Int) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenSheet: (ActiveSheet) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            onHistoryClick = { onOpenSheet(ActiveSheet.History) },
            onSettingsClick = { onOpenSheet(ActiveSheet.Settings) },
            modifier = Modifier.padding(horizontal = SCREEN_PADDING),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SCREEN_PADDING, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            DisplaySection(
                state = state,
                onSwapCurrencies = onSwapCurrencies,
                onSelectSide = onSelectSide,
                onCursorChange = onCursorChange,
                onRefreshRates = onRefreshRates,
                onOpenSheet = onOpenSheet,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )

            Keypad(
                onKeyPress = onKeyPress,
                modifier = Modifier
                    .weight(1f)
                    // Keeps keys a sensible size on very wide screens instead of
                    // stretching them into huge circles.
                    .widthIn(max = KEYPAD_MAX_WIDTH)
                    .align(Alignment.CenterVertically)
                    .padding(bottom = SCREEN_PADDING),
            )
        }
    }
}

/** History on the left, settings on the right, as small unobtrusive glyphs. */
@Composable
private fun TopBar(onHistoryClick: () -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CambioTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onHistoryClick) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.cd_open_history),
                tint = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = stringResource(R.string.cd_open_settings),
                tint = colors.textSecondary,
            )
        }
    }
}

/**
 * The display stack: the converter block, which is also the primary display, and
 * the rate line beneath it.
 */
@Composable
private fun DisplaySection(
    state: CalculatorUiState,
    onSwapCurrencies: () -> Unit,
    onSelectSide: (ConversionSide) -> Unit,
    onCursorChange: (Int) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenSheet: (ActiveSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        ConverterBlock(
            activeText = state.activeText,
            activeCursor = state.activeCursor,
            activePreview = state.activePreview,
            evaluatedExpression = state.evaluatedExpression,
            otherValue = state.otherValue,
            from = state.fromCurrency,
            to = state.toCurrency,
            activeSide = state.activeSide,
            onCursorChange = onCursorChange,
            onFromClick = { onOpenSheet(ActiveSheet.FromCurrency) },
            onToClick = { onOpenSheet(ActiveSheet.ToCurrency) },
            onSelectSide = onSelectSide,
            onSwapClick = onSwapCurrencies,
        )

        Spacer(modifier = Modifier.height(8.dp))

        RateLine(
            status = state.ratesStatus,
            rateText = state.rateDisplay,
            lastUpdatedText = (state.ratesStatus as? RatesStatus.Ready)
                ?.lastUpdatedEpochSeconds
                ?.let(::formatUpdatedAt),
            onRefresh = onRefreshRates,
        )
    }
}

/** Formats the provider's last-update time using the device's own date format. */
private fun formatUpdatedAt(epochSeconds: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(
    Date(
        epochSeconds * 1_000L,
    ),
)

/** Each calculator failure gets its own message rather than a generic "Error". */
private fun CalcError.messageRes(): Int = when (this) {
    CalcError.EMPTY -> R.string.calc_error_empty
    CalcError.MALFORMED -> R.string.calc_error_malformed
    CalcError.DIVIDE_BY_ZERO -> R.string.calc_error_divide_by_zero
    CalcError.OVERFLOW -> R.string.calc_error_overflow
}

private val SCREEN_PADDING = 24.dp
private val TOP_BAR_HEIGHT = 56.dp
private val KEYPAD_TOP_GAP = 20.dp
private val KEYPAD_BOTTOM_MARGIN = 13.dp
private val TOAST_BOTTOM_INSET = 140.dp
private val KEYPAD_MAX_WIDTH = 420.dp
private val WIDE_LAYOUT_MIN_WIDTH = 600.dp
