package io.github.angad7600123.cambio.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.angad7600123.cambio.MainActivity
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.calculator.CalcResult
import io.github.angad7600123.cambio.calculator.CalculatorEngine
import io.github.angad7600123.cambio.calculator.OperatorType
import io.github.angad7600123.cambio.currency.ConversionEngine
import io.github.angad7600123.cambio.currency.ConversionSide
import io.github.angad7600123.cambio.currency.CurrencyCatalog
import io.github.angad7600123.cambio.data.DataStoreRatesCache
import io.github.angad7600123.cambio.data.SettingsRepository
import io.github.angad7600123.cambio.data.cambioDataStore
import io.github.angad7600123.cambio.format.ExpressionFormatter
import io.github.angad7600123.cambio.format.NumberDisplayFormatter
import io.github.angad7600123.cambio.ui.theme.DarkAccentText
import io.github.angad7600123.cambio.ui.theme.DarkDestructive
import io.github.angad7600123.cambio.ui.theme.DarkOnAccent
import io.github.angad7600123.cambio.ui.theme.DarkTextPrimary
import io.github.angad7600123.cambio.ui.theme.DarkTextSecondary
import io.github.angad7600123.cambio.ui.theme.LightAccent
import io.github.angad7600123.cambio.ui.theme.LightDestructive
import io.github.angad7600123.cambio.ui.theme.LightOnAccent
import io.github.angad7600123.cambio.ui.theme.LightTextPrimary
import io.github.angad7600123.cambio.ui.theme.LightTextSecondary
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

/**
 * The home-screen widget.
 *
 * It is the same calculator, not a cut-down remote control: the identical
 * [CalculatorInput] state machine and [CalculatorEngine] drive it, and it reads the
 * same cached rates and currency selection as the app. The visual language matches
 * too — circular keys, coral for the destructive pair, one filled jade equals.
 *
 * Glance renders through RemoteViews, so the styling is expressed with drawable
 * resources rather than Compose modifiers. Those drawables have `-night` variants,
 * which is what makes the widget follow the system light/dark theme automatically.
 */
class CambioWidget : GlanceAppWidget() {
    // Responsive rather than a single fixed layout, so a resized widget re-lays out
    // instead of clipping its keypad.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read everything the widget needs once, before composing. Glance composition
        // must not perform I/O.
        val settings = SettingsRepository(context.cambioDataStore).settings.first()
        val snapshot = DataStoreRatesCache(context.cambioDataStore).snapshot.first()

        val palette = paletteFor(context)
        val catalog = CurrencyCatalog()
        val numberFormatter = NumberDisplayFormatter()
        val expressionFormatter = ExpressionFormatter(numberFormatter)

        provideContent {
            val prefs = currentState<Preferences>()
            val expression = prefs[WidgetStateKeys.EXPRESSION].orEmpty()
            val hasError = !prefs[WidgetStateKeys.ERROR].isNullOrEmpty()
            val side = readSide(prefs[WidgetStateKeys.ACTIVE_SIDE])

            val typed = if (hasError) null else previewValue(expression)

            // Whichever side is active holds what was typed; the other is converted
            // from it, so the widget reads in both directions.
            val typedCode =
                if (side == ConversionSide.SOURCE) settings.fromCurrency else settings.toCurrency
            val otherCode =
                if (side == ConversionSide.SOURCE) settings.toCurrency else settings.fromCurrency
            val otherValue = if (typed != null && snapshot != null) {
                ConversionEngine.convert(typed, typedCode, otherCode, snapshot.rates)
            } else {
                null
            }

            val typedText = when {
                hasError -> context.getString(R.string.calc_error_short)
                typed != null -> numberFormatter.format(typed)
                else -> "0"
            }
            val otherText = otherValue
                ?.let { numberFormatter.formatMoney(it, catalog.infoFor(otherCode)) }
                ?: PLACEHOLDER

            GlanceTheme {
                WidgetBody(
                    leftValue = if (side == ConversionSide.SOURCE) typedText else otherText,
                    rightValue = if (side == ConversionSide.TARGET) typedText else otherText,
                    leftCode = settings.fromCurrency,
                    rightCode = settings.toCurrency,
                    activeSide = side,
                    isError = hasError,
                    palette = palette,
                )
            }
        }
    }

    /** Reads the stored side, defaulting to the right-hand figure. */
    private fun readSide(stored: String?): ConversionSide = runCatching { ConversionSide.valueOf(stored.orEmpty()) }
        .getOrDefault(ConversionSide.TARGET)

    /**
     * Evaluates what is typed so far, dropping a trailing operator so the preview
     * stays stable mid-expression — the same rule the app's display uses.
     */
    private fun previewValue(expression: String): BigDecimal? {
        if (expression.isEmpty()) return BigDecimal.ZERO
        (CalculatorEngine.evaluate(expression) as? CalcResult.Success)?.let { return it.value }
        val trimmed = expression.trimEnd { OperatorType.fromSymbol(it) != null }
        if (trimmed.isEmpty() || trimmed == expression) return null
        return (CalculatorEngine.evaluate(trimmed) as? CalcResult.Success)?.value
    }
}

@Composable
private fun WidgetBody(
    leftValue: String,
    rightValue: String,
    leftCode: String,
    rightCode: String,
    activeSide: ConversionSide,
    isError: Boolean,
    palette: WidgetPalette,
) {
    val metrics = WidgetMetrics.forSize(LocalSize.current)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(metrics.padding),
    ) {
        WidgetDisplay(
            leftValue = leftValue,
            rightValue = rightValue,
            leftCode = leftCode,
            rightCode = rightCode,
            activeSide = activeSide,
            isError = isError,
            palette = palette,
            metrics = metrics,
        )
        // The keypad absorbs whatever the display does not use, and its rows share
        // that space evenly. Giving the display the weight instead left a dead band
        // of empty pixels above the figures.
        WidgetKeypad(palette, metrics, GlanceModifier.defaultWeight())
    }
}

/**
 * The two figures, side by side.
 *
 * Each column is one currency: its code above, its figure below. The active side
 * is drawn in the foreground colour with its code tinted and the idle one recedes
 * to grey, which is the same cue the app uses, so there is never any doubt about
 * where the next digit lands.
 *
 * Tapping a figure moves the caret to that currency; tapping a code opens the app,
 * where the full searchable picker lives.
 */
@Composable
private fun WidgetDisplay(
    leftValue: String,
    rightValue: String,
    leftCode: String,
    rightCode: String,
    activeSide: ConversionSide,
    isError: Boolean,
    palette: WidgetPalette,
    metrics: WidgetMetrics,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetSide(
            value = leftValue,
            code = leftCode,
            side = ConversionSide.SOURCE,
            isActive = activeSide == ConversionSide.SOURCE,
            alignEnd = false,
            isError = isError,
            palette = palette,
            metrics = metrics,
            modifier = GlanceModifier.defaultWeight(),
        )
        WidgetSide(
            value = rightValue,
            code = rightCode,
            side = ConversionSide.TARGET,
            isActive = activeSide == ConversionSide.TARGET,
            alignEnd = true,
            isError = isError,
            palette = palette,
            metrics = metrics,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun WidgetSide(
    value: String,
    code: String,
    side: ConversionSide,
    isActive: Boolean,
    alignEnd: Boolean,
    isError: Boolean,
    palette: WidgetPalette,
    metrics: WidgetMetrics,
    modifier: GlanceModifier = GlanceModifier,
) {
    val valueColor = when {
        isError && isActive -> palette.destructive
        isActive -> palette.textPrimary
        else -> palette.textSecondary
    }
    val alignment = if (alignEnd) TextAlign.End else TextAlign.Start

    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = code,
            maxLines = 1,
            modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            style = TextStyle(
                color = if (isActive) palette.accentText else palette.textSecondary,
                fontSize = metrics.codeSp.sp(),
                fontWeight = FontWeight.Medium,
                textAlign = alignment,
            ),
        )
        Text(
            text = value,
            maxLines = 1,
            modifier = GlanceModifier.clickable(
                actionRunCallback<WidgetSideActionCallback>(
                    WidgetSideActionCallback.parametersOf(side),
                ),
            ),
            style = TextStyle(
                color = valueColor,
                fontSize = metrics.resultSp.sp(),
                fontWeight = FontWeight.Medium,
                textAlign = alignment,
            ),
        )
    }
}

/**
 * The same 5x4 grid as the app.
 *
 * `defaultWeight()` is scoped to Row and Column in Glance, so each weight is
 * created at its call site and handed down as a modifier rather than being applied
 * inside the child composable.
 */
@Composable
private fun WidgetKeypad(palette: WidgetPalette, metrics: WidgetMetrics, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        WIDGET_KEY_ROWS.forEach { row ->
            WidgetRow(modifier = GlanceModifier.defaultWeight()) {
                row.forEach { spec ->
                    WidgetKey(
                        label = spec.label,
                        style = spec.style,
                        action = spec.action,
                        palette = palette,
                        metrics = metrics,
                        modifier = GlanceModifier.width(metrics.columnWidth),
                    )
                }
            }
        }
    }
}

/** One key's presentation, so the grid can be declared as data. */
private data class WidgetKeySpec(val label: String, val style: WidgetKeyStyle, val action: WidgetAction)

private val WIDGET_KEY_ROWS: List<List<WidgetKeySpec>> = listOf(
    listOf(
        WidgetKeySpec("C", WidgetKeyStyle.Destructive, WidgetAction.CLEAR),
        WidgetKeySpec("⌫", WidgetKeyStyle.Destructive, WidgetAction.BACKSPACE),
        WidgetKeySpec("%", WidgetKeyStyle.Standard, WidgetAction.PERCENT),
        WidgetKeySpec("÷", WidgetKeyStyle.Operator, WidgetAction.DIVIDE),
    ),
    listOf(
        WidgetKeySpec("7", WidgetKeyStyle.Standard, WidgetAction.DIGIT_7),
        WidgetKeySpec("8", WidgetKeyStyle.Standard, WidgetAction.DIGIT_8),
        WidgetKeySpec("9", WidgetKeyStyle.Standard, WidgetAction.DIGIT_9),
        WidgetKeySpec("×", WidgetKeyStyle.Operator, WidgetAction.MULTIPLY),
    ),
    listOf(
        WidgetKeySpec("4", WidgetKeyStyle.Standard, WidgetAction.DIGIT_4),
        WidgetKeySpec("5", WidgetKeyStyle.Standard, WidgetAction.DIGIT_5),
        WidgetKeySpec("6", WidgetKeyStyle.Standard, WidgetAction.DIGIT_6),
        WidgetKeySpec("−", WidgetKeyStyle.Operator, WidgetAction.SUBTRACT),
    ),
    listOf(
        WidgetKeySpec("1", WidgetKeyStyle.Standard, WidgetAction.DIGIT_1),
        WidgetKeySpec("2", WidgetKeyStyle.Standard, WidgetAction.DIGIT_2),
        WidgetKeySpec("3", WidgetKeyStyle.Standard, WidgetAction.DIGIT_3),
        WidgetKeySpec("+", WidgetKeyStyle.Operator, WidgetAction.ADD),
    ),
    listOf(
        WidgetKeySpec("( )", WidgetKeyStyle.Standard, WidgetAction.PAREN),
        WidgetKeySpec("0", WidgetKeyStyle.Standard, WidgetAction.DIGIT_0),
        WidgetKeySpec(".", WidgetKeyStyle.Standard, WidgetAction.DECIMAL),
        WidgetKeySpec("=", WidgetKeyStyle.Accent, WidgetAction.EQUALS),
    ),
)

@Composable
private fun WidgetRow(modifier: GlanceModifier = GlanceModifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private enum class WidgetKeyStyle(val backgroundRes: Int) {
    Standard(R.drawable.widget_key),

    /** The operator column, a shade lighter, exactly as in the app. */
    Operator(R.drawable.widget_key_operator),
    Destructive(R.drawable.widget_key),
    Accent(R.drawable.widget_key_accent),
    ;

    fun textColor(palette: WidgetPalette): ColorProvider = when (this) {
        Standard, Operator -> palette.textPrimary
        Destructive -> palette.destructive
        Accent -> palette.onAccent
    }
}

/**
 * Widget colours as day/night pairs.
 *
 * Glance resolves these per the system theme at render time, which is how the
 * widget follows light and dark without a second palette. They reference the same
 * values as the app's Compose theme, so the two surfaces cannot drift apart.
 *
 * Key and canvas *backgrounds* still come from drawable resources (with `-night`
 * variants), because a RemoteViews background must be a drawable.
 */
/**
 * Sizes derived from the widget's actual dimensions.
 *
 * A fixed key size left wide empty gutters on a large widget and a cramped
 * display, so everything is proportional instead: the keys grow to fill the width
 * the way the iOS calculator's do, and the type scales with them.
 *
 * The width budget is `4d + 3g + 2p`, with the gap and padding expressed as
 * fractions of the diameter, which reduces to `d = width / 4.76`. Height is
 * checked too, so a short-but-wide widget shrinks its keys rather than clipping a
 * row.
 */
/** Shown in place of a figure until rates are available. */
private const val PLACEHOLDER = "—"

private data class WidgetMetrics(
    val keyDiameter: Dp,
    val keyGap: Dp,
    val padding: Dp,
    /** Exact height of the five rows, so the keypad is sized rather than stretched. */
    val keypadHeight: Dp,
    val rowHeight: Dp,
    /** Width of one grid cell: the key plus its gutter. */
    val columnWidth: Dp,
    val resultSp: Float,
    val codeSp: Float,
    val keyGlyphSp: Float,
) {
    companion object {
        private const val GAP_RATIO = 0.16f
        private const val PADDING_RATIO = 0.14f

        /** Widest a cell may get relative to its key, before it looks disconnected. */
        private const val MAX_CELL_RATIO = 1.42f
        private const val COLUMNS = 4
        private const val ROWS = 5

        /**
         * Share of the widget height the display needs.
         *
         * Only two lines now that the figures sit side by side rather than stacked,
         * which is what freed the space the keypad uses.
         */
        private const val DISPLAY_SHARE = 0.20f

        /** Fraction of its row a key fills, leaving a little breathing room. */
        private const val KEY_IN_ROW = 0.88f

        private val MIN_DIAMETER = 40.dp
        private val MAX_DIAMETER = 88.dp

        fun forSize(size: DpSize): WidgetMetrics {
            val widthBudget = COLUMNS + (COLUMNS - 1) * GAP_RATIO + 2 * PADDING_RATIO
            val byWidth = size.width.value / widthBudget

            // Rows share the keypad's height evenly, so a key must fit inside one
            // row with a margin; without that the circle is clipped into an octagon.
            val byHeight = (size.height.value * (1f - DISPLAY_SHARE)) / ROWS * KEY_IN_ROW

            val diameter = minOf(byWidth, byHeight)
                .coerceIn(MIN_DIAMETER.value, MAX_DIAMETER.value)
            val rowHeight = diameter * (1f + GAP_RATIO)

            // When height limits the key size there is width to spare. Let the
            // cells absorb some of it rather than leaving one wide margin, but cap
            // how far apart they drift so the grid still reads as a keypad.
            val padding = diameter * PADDING_RATIO
            val availableCell = (size.width.value - 2 * padding) / COLUMNS
            val columnWidth = minOf(availableCell, diameter * MAX_CELL_RATIO)

            return WidgetMetrics(
                keyDiameter = diameter.dp,
                keyGap = (diameter * GAP_RATIO).dp,
                padding = padding.dp,
                keypadHeight = (rowHeight * ROWS).dp,
                rowHeight = rowHeight.dp,
                columnWidth = columnWidth.dp,
                // Tied to the key size so the display is never lost above a large
                // keypad, nor overpowering on a small one.
                resultSp = diameter * 0.52f,
                codeSp = diameter * 0.26f,
                keyGlyphSp = diameter * 0.42f,
            )
        }
    }
}

/**
 * The widget's resolved colours for the current light/dark configuration.
 *
 * Glance's own day/night provider resolves against a context that does not always
 * agree with the launcher's configuration, which showed up as light glyphs on the
 * dark background. Reading `uiMode` directly means text is resolved by exactly the
 * same signal the `-night` drawable qualifiers use, so the two can never disagree.
 */
private data class WidgetPalette(
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val destructive: ColorProvider,
    val onAccent: ColorProvider,
    val accentText: ColorProvider,
)

private fun paletteFor(context: Context): WidgetPalette {
    val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    return if (isNight) {
        WidgetPalette(
            textPrimary = ColorProvider(DarkTextPrimary),
            textSecondary = ColorProvider(DarkTextSecondary),
            destructive = ColorProvider(DarkDestructive),
            onAccent = ColorProvider(DarkOnAccent),
            accentText = ColorProvider(DarkAccentText),
        )
    } else {
        WidgetPalette(
            textPrimary = ColorProvider(LightTextPrimary),
            textSecondary = ColorProvider(LightTextSecondary),
            destructive = ColorProvider(LightDestructive),
            onAccent = ColorProvider(LightOnAccent),
            accentText = ColorProvider(LightAccent),
        )
    }
}

/**
 * A single widget key.
 *
 * The outer box is weighted so keys share the row evenly; the inner box is a fixed
 * *square*, which is what makes the oval drawable render as a real circle. Glance
 * has no aspect-ratio modifier, so without the fixed size a key stretches into an
 * ellipse as the widget is resized.
 */
@Composable
private fun WidgetKey(
    label: String,
    style: WidgetKeyStyle,
    action: WidgetAction,
    palette: WidgetPalette,
    metrics: WidgetMetrics,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .size(metrics.keyDiameter)
                .background(ImageProvider(style.backgroundRes))
                .clickable(
                    actionRunCallback<WidgetKeyActionCallback>(
                        WidgetKeyActionCallback.parametersOf(action),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = style.textColor(palette),
                    fontSize = metrics.keyGlyphSp.sp(),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/** Glance text sizes are expressed in sp via the Compose unit type. */
private fun Float.sp() = androidx.compose.ui.unit.TextUnit(
    this,
    androidx.compose.ui.unit.TextUnitType.Sp,
)
