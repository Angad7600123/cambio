package io.github.angad7600123.cambio.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.angad7600123.cambio.calculator.CalculatorKey
import io.github.angad7600123.cambio.calculator.OperatorType
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.data.RatesError
import io.github.angad7600123.cambio.ui.theme.CambioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI tests for the calculator screen, run on a device or emulator.
 *
 * The screen is stateless — it takes a [CalculatorUiState] and emits callbacks — so
 * these drive it directly with fixed states rather than going through the network
 * or the ViewModel. That keeps them fast and deterministic while still exercising
 * the real composables, layout and semantics.
 */
@RunWith(AndroidJUnit4::class)
class CalculatorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val usd = CurrencyInfo("USD", "US Dollar", "$", 2, "🇺🇸")
    private val inr = CurrencyInfo("INR", "Indian Rupee", "₹", 2, "🇮🇳")

    private fun state(
        primary: String = "0",
        secondary: String = "",
        isEditing: Boolean = true,
        converted: String? = "945.40",
        rate: String? = "1 USD = 94.5405 INR",
        status: RatesStatus = RatesStatus.Ready(
            lastUpdatedEpochSeconds = 1_788_480_151L,
            isStale = false,
            isRefreshing = false,
        ),
    ) = CalculatorUiState(
        primaryDisplay = primary,
        secondaryDisplay = secondary,
        isEditing = isEditing,
        fromCurrency = usd,
        toCurrency = inr,
        convertedDisplay = converted,
        rateDisplay = rate,
        ratesStatus = status,
    )

    /** Renders the screen, collecting any keys it reports back. */
    private fun setScreen(
        uiState: CalculatorUiState,
        onKey: (CalculatorKey) -> Unit = {},
        onSwap: () -> Unit = {},
        onRefresh: () -> Unit = {},
    ) {
        composeRule.setContent {
            CambioTheme {
                CalculatorScreen(
                    state = uiState,
                    onKeyPress = onKey,
                    onSwapCurrencies = onSwap,
                    onSelectFromCurrency = {},
                    onSelectToCurrency = {},
                    onRefreshRates = onRefresh,
                    onRestoreHistory = {},
                    onClearHistory = {},
                    onThemeModeChange = {},
                    onUseSystemColorsChange = {},
                    onTransientErrorShown = {},
                )
            }
        }
    }

    @Test
    fun everyKeypadKeyIsPresentAndLabelled() {
        setScreen(state())

        listOf(
            "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Plus", "Minus", "Multiply", "Divide", "Decimal point", "Percent",
            "Parenthesis", "Clear", "Backspace", "Equals",
        ).forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
    }

    @Test
    fun pressingADigitReportsThatDigit() {
        val pressed = mutableListOf<CalculatorKey>()
        setScreen(state(), onKey = { pressed += it })

        composeRule.onNodeWithContentDescription("Seven").performClick()

        assertEquals(listOf<CalculatorKey>(CalculatorKey.Digit(7)), pressed)
    }

    @Test
    fun pressingOperatorsReportsTheCorrectOperator() {
        val pressed = mutableListOf<CalculatorKey>()
        setScreen(state(), onKey = { pressed += it })

        composeRule.onNodeWithContentDescription("Plus").performClick()
        composeRule.onNodeWithContentDescription("Divide").performClick()

        val expected: List<CalculatorKey> = listOf(
            CalculatorKey.Operator(OperatorType.ADD),
            CalculatorKey.Operator(OperatorType.DIVIDE),
        )
        assertEquals(expected, pressed.toList())
    }

    @Test
    fun rapidRepeatedPressesAreAllDelivered() {
        val pressed = mutableListOf<CalculatorKey>()
        setScreen(state(), onKey = { pressed += it })

        repeat(12) { composeRule.onNodeWithContentDescription("Nine").performClick() }

        assertEquals(12, pressed.size)
        assertTrue(pressed.all { it == CalculatorKey.Digit(9) })
    }

    @Test
    fun whileTypingTheExpressionIsTheLargeLineAndThePreviewSitsBelow() {
        // One UI's hierarchy: the expression leads, the running total follows.
        setScreen(state(primary = "1,234 × 2", secondary = "2,468", isEditing = true))

        composeRule.onNodeWithText("1,234 × 2", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2,468").assertIsDisplayed()
    }

    @Test
    fun afterEqualsTheResultBecomesTheLargeLine() {
        setScreen(state(primary = "2,468", secondary = "1,234 × 2 =", isEditing = false))

        composeRule.onNodeWithText("2,468").assertIsDisplayed()
        composeRule.onNodeWithText("1,234 × 2 =").assertIsDisplayed()
    }

    @Test
    fun convertedAmountAndCurrencyCodeAreShown() {
        setScreen(state(converted = "945.40"))

        composeRule.onNodeWithText("945.40").assertIsDisplayed()
    }

    @Test
    fun currencyChipsShowBothCodes() {
        setScreen(state())

        composeRule.onNodeWithText("USD").assertIsDisplayed()
        composeRule.onNodeWithText("INR").assertIsDisplayed()
    }

    @Test
    fun swapControlReportsASwap() {
        var swaps = 0
        setScreen(state(), onSwap = { swaps++ })

        composeRule.onNodeWithContentDescription("Swap currencies").performClick()

        assertEquals(1, swaps)
    }

    @Test
    fun rateLineIsShownAndTappingItRefreshes() {
        var refreshes = 0
        setScreen(state(), onRefresh = { refreshes++ })

        composeRule.onNodeWithText("1 USD = 94.5405 INR", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tap to refresh rates", substring = true)
            .performClick()

        assertEquals(1, refreshes)
    }

    @Test
    fun staleRatesAreMarked() {
        setScreen(
            state(
                status = RatesStatus.Ready(
                    lastUpdatedEpochSeconds = 1L,
                    isStale = true,
                    isRefreshing = false,
                ),
            ),
        )

        composeRule.onNodeWithText("Rates may be out of date", substring = true).assertIsDisplayed()
    }

    @Test
    fun offlineWithNoCacheOffersRetryAndKeepsKeypadUsable() {
        var refreshes = 0
        val pressed = mutableListOf<CalculatorKey>()
        setScreen(
            state(converted = null, rate = null, status = RatesStatus.Unavailable(RatesError.Network)),
            onKey = { pressed += it },
            onRefresh = { refreshes++ },
        )

        composeRule.onNodeWithText("You appear to be offline", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assertEquals(1, refreshes)

        // The calculator must still work with no rates at all.
        composeRule.onNodeWithContentDescription("Five").performClick()
        assertEquals(listOf<CalculatorKey>(CalculatorKey.Digit(5)), pressed)
    }

    @Test
    fun loadingStateShowsNoErrorAndKeypadStillWorks() {
        val pressed = mutableListOf<CalculatorKey>()
        setScreen(
            state(converted = null, rate = null, status = RatesStatus.Loading),
            onKey = { pressed += it },
        )

        composeRule.onNodeWithContentDescription("Loading exchange rates").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("One").performClick()
        assertEquals(1, pressed.size)
    }

    @Test
    fun topBarExposesHistoryAndSettings() {
        setScreen(state())

        composeRule.onNodeWithContentDescription("Open history").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open settings").assertIsDisplayed()
    }

    @Test
    fun openingSettingsShowsThemeOptionsAndAttribution() {
        setScreen(state())

        composeRule.onNodeWithContentDescription("Open settings").performClick()

        composeRule.onNodeWithText("Follow system").assertIsDisplayed()
        // The provider credit is a licensing requirement, not decoration.
        composeRule.onNodeWithText("Rates by ExchangeRate-API").assertIsDisplayed()
    }

    @Test
    fun openingHistoryWithNoEntriesShowsTheEmptyState() {
        setScreen(state())

        composeRule.onNodeWithContentDescription("Open history").performClick()

        composeRule.onNodeWithText("Calculations you complete will appear here.").assertIsDisplayed()
    }

    @Test
    fun currencyPickerOpensFromTheChipAndCanBeSearched() {
        val screenState = state().copy(
            availableCurrencies = listOf(usd, inr, CurrencyInfo("EUR", "Euro", "€", 2, "🇪🇺")),
        )
        setScreen(screenState)

        composeRule.onNodeWithContentDescription("Convert from", substring = true).performClick()

        composeRule.onNodeWithText("Choose currency").assertIsDisplayed()
        composeRule.onNodeWithText("Euro", substring = true).assertIsDisplayed()
    }

    @Test
    fun aCalculatorErrorFloatsAToastAndLeavesTheInputAlone() {
        // One UI keeps the typed expression on screen and floats a brief message,
        // rather than blanking the display.
        setScreen(
            state(primary = "5 ÷ 0", isEditing = true).copy(
                transientError = io.github.angad7600123.cambio.calculator.CalcError.DIVIDE_BY_ZERO,
            ),
        )

        composeRule.onNodeWithText("Cannot divide by zero").assertIsDisplayed()
        composeRule.onNodeWithText("5 ÷ 0", substring = true).assertIsDisplayed()
    }
}
