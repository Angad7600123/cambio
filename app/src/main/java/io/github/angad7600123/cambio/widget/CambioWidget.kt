package io.github.angad7600123.cambio.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import androidx.glance.layout.Spacer
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
import io.github.angad7600123.cambio.ui.isBareNumber
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
 * `CalculatorInput` state machine and [CalculatorEngine] drive it, and it reads the
 * same cached rates and currency selection as the app. The visual language matches
 * too — circular keys, coral for the destructive pair, one filled jade equals, and
 * the expression on the display rather than only its answer.
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
        val settingsRepository = SettingsRepository(context.cambioDataStore)
        val ratesCache = DataStoreRatesCache(context.cambioDataStore)

        // Seed values, so the first frame draws with real data rather than blanks.
        // Glance composition must not perform I/O, so the initial read happens here.
        val initialSettings = settingsRepository.settings.first()
        val initialSnapshot = ratesCache.snapshot.first()

        val palette = paletteFor(context)
        val catalog = CurrencyCatalog()
        val numberFormatter = NumberDisplayFormatter()
        val expressionFormatter = ExpressionFormatter(numberFormatter)
        val errorText = context.getString(R.string.calc_error_short)

        provideContent {
            // Collected for the life of the composition rather than read once.
            //
            // `provideGlance` runs when a *session* starts; every update after that
            // recomposes the content that session is already holding. Anything read
            // above and captured is therefore frozen at whatever it was when the
            // widget was first drawn — which is precisely why the swap control did
            // nothing on a widget that had just been touched. The write landed, the
            // widget redrew, and it redrew the codes it had captured. Leave it alone
            // long enough for the session to end and it appeared to work again,
            // which is what made it look intermittent rather than broken.
            val settings by settingsRepository.settings.collectAsState(initialSettings)
            val snapshot by ratesCache.snapshot.collectAsState(initialSnapshot)

            val prefs = currentState<Preferences>()
            val expression = prefs[WidgetStateKeys.EXPRESSION].orEmpty()
            val hasError = !prefs[WidgetStateKeys.ERROR].isNullOrEmpty()
            val side = readSide(prefs[WidgetStateKeys.ACTIVE_SIDE])

            val rates = snapshot?.rates
            val total = if (hasError) null else runningTotal(expression)

            // Whichever side is active holds what was typed; the other is converted
            // from it, so the widget reads in both directions.
            val typedCode =
                if (side == ConversionSide.SOURCE) settings.fromCurrency else settings.toCurrency
            val otherCode =
                if (side == ConversionSide.SOURCE) settings.toCurrency else settings.fromCurrency
            val otherValue = if (total != null && rates != null) {
                ConversionEngine.convert(total, typedCode, otherCode, rates)
            } else {
                null
            }

            // The expression as typed, not its answer. Showing only the answer meant
            // the widget silently evaluated as you went: press 2, +, 2 and it read 4
            // before equals was ever touched, with no sign of what had been entered.
            val activeText = when {
                hasError -> errorText
                expression.isEmpty() -> ZERO
                else -> expressionFormatter.formatCompact(expression)
            }
            val otherText = otherValue
                ?.let { numberFormatter.formatMoney(it, catalog.infoFor(otherCode)) }
                ?: PLACEHOLDER

            // The running total, on the same terms the app uses it: shown only when
            // it says something the line above does not. While `250` is being typed
            // it would print the same number twice.
            val totalText = if (total != null && expression.isNotEmpty() && !isBareNumber(expression, total)) {
                numberFormatter.format(total)
            } else {
                ""
            }

            GlanceTheme {
                WidgetBody(
                    leftValue = if (side == ConversionSide.SOURCE) activeText else otherText,
                    rightValue = if (side == ConversionSide.TARGET) activeText else otherText,
                    leftTotal = if (side == ConversionSide.SOURCE) totalText else "",
                    rightTotal = if (side == ConversionSide.TARGET) totalText else "",
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
     * Evaluates what is typed so far, dropping a trailing operator so the total
     * stays stable mid-expression — the same rule the app's display uses.
     */
    private fun runningTotal(expression: String): BigDecimal? {
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
    leftTotal: String,
    rightTotal: String,
    leftCode: String,
    rightCode: String,
    activeSide: ConversionSide,
    isError: Boolean,
    palette: WidgetPalette,
) {
    val metrics = WidgetMetrics.forSize(LocalSize.current)
    // One size for both figures, taken from the longer of the two. Sizing them
    // independently would put a huge `5` beside a shrunken `117,339.45`.
    val figureSp = metrics.figureSpFor(
        left = leftValue,
        right = rightValue,
        fontScale = LocalContext.current.resources.configuration.fontScale,
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(metrics.padding),
    ) {
        WidgetDisplay(
            leftValue = leftValue,
            rightValue = rightValue,
            leftTotal = leftTotal,
            rightTotal = rightTotal,
            leftCode = leftCode,
            rightCode = rightCode,
            activeSide = activeSide,
            isError = isError,
            figureSp = figureSp,
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
 * The display: three rows, rather than two columns.
 *
 * Laying it out by row is what keeps it aligned. The two sides carry different
 * numbers of lines — only the side being typed into has a running total — so as
 * columns they would each centre their own content and the two currency codes
 * would end up at different heights. By row, the codes share a row and the figures
 * share a row, and the total appears in its own strip beneath.
 *
 * That strip is reserved whether or not anything is in it. Letting it collapse
 * would shunt the figures up and down as operators were pressed.
 *
 * Tapping a figure moves the caret to that currency; tapping a code opens the app,
 * where the full searchable picker lives.
 */
@Composable
private fun WidgetDisplay(
    leftValue: String,
    rightValue: String,
    leftTotal: String,
    rightTotal: String,
    leftCode: String,
    rightCode: String,
    activeSide: ConversionSide,
    isError: Boolean,
    figureSp: Float,
    palette: WidgetPalette,
    metrics: WidgetMetrics,
) {
    Column(modifier = GlanceModifier.fillMaxWidth().height(metrics.displayHeight)) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetCode(
                code = leftCode,
                isActive = activeSide == ConversionSide.SOURCE,
                alignEnd = false,
                palette = palette,
                metrics = metrics,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(metrics.swapWidth))
            WidgetCode(
                code = rightCode,
                isActive = activeSide == ConversionSide.TARGET,
                alignEnd = true,
                palette = palette,
                metrics = metrics,
                modifier = GlanceModifier.defaultWeight(),
            )
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetFigure(
                value = leftValue,
                side = ConversionSide.SOURCE,
                isActive = activeSide == ConversionSide.SOURCE,
                alignEnd = false,
                isError = isError,
                figureSp = figureSp,
                palette = palette,
                modifier = GlanceModifier.defaultWeight(),
            )
            WidgetSwap(palette = palette, metrics = metrics)
            WidgetFigure(
                value = rightValue,
                side = ConversionSide.TARGET,
                isActive = activeSide == ConversionSide.TARGET,
                alignEnd = true,
                isError = isError,
                figureSp = figureSp,
                palette = palette,
                modifier = GlanceModifier.defaultWeight(),
            )
        }

        Row(modifier = GlanceModifier.fillMaxWidth().height(metrics.totalHeight)) {
            WidgetTotal(leftTotal, alignEnd = false, palette, metrics, GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(metrics.swapWidth))
            WidgetTotal(rightTotal, alignEnd = true, palette, metrics, GlanceModifier.defaultWeight())
        }
    }
}

/** A currency code. Its own tap target, so the press feedback hugs the word. */
@Composable
private fun WidgetCode(
    code: String,
    isActive: Boolean,
    alignEnd: Boolean,
    palette: WidgetPalette,
    metrics: WidgetMetrics,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = code,
            maxLines = 1,
            modifier = GlanceModifier.clickable(
                onClick = actionStartActivity<MainActivity>(),
                rippleOverride = R.drawable.widget_control_ripple,
            ),
            style = TextStyle(
                color = if (isActive) palette.accentText else palette.textSecondary,
                fontSize = metrics.codeSp.sp(),
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** One side's figure: the expression while it is being typed, or a conversion. */
@Composable
private fun WidgetFigure(
    value: String,
    side: ConversionSide,
    isActive: Boolean,
    alignEnd: Boolean,
    isError: Boolean,
    figureSp: Float,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    val color = when {
        isError && isActive -> palette.destructive
        isActive -> palette.textPrimary
        else -> palette.textSecondary
    }

    Text(
        text = value,
        maxLines = 1,
        modifier = modifier.clickable(
            onClick = actionRunCallback<WidgetSideActionCallback>(
                WidgetSideActionCallback.parametersOf(side),
            ),
            rippleOverride = R.drawable.widget_control_ripple,
        ),
        style = TextStyle(
            color = color,
            fontSize = figureSp.sp(),
            fontWeight = FontWeight.Medium,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        ),
    )
}

/** The running total under the expression, or nothing at all. */
@Composable
private fun WidgetTotal(
    text: String,
    alignEnd: Boolean,
    palette: WidgetPalette,
    metrics: WidgetMetrics,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text = text,
        maxLines = 1,
        modifier = modifier,
        style = TextStyle(
            color = palette.textSecondary,
            fontSize = metrics.totalSp.sp(),
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        ),
    )
}

/**
 * The swap control, between the two figures.
 *
 * The app puts it on the rule dividing its stacked figures; side by side, the
 * equivalent place is the gap between them — which was empty, and part of why the
 * numbers looked stranded at the edges of a wide widget.
 *
 * A plain glyph, no background: it is a third element in a row that belongs to the
 * two figures, and giving it a filled shape would let it compete with them.
 */
@Composable
private fun WidgetSwap(palette: WidgetPalette, metrics: WidgetMetrics) {
    Box(
        modifier = GlanceModifier
            .width(metrics.swapWidth)
            .clickable(
                onClick = actionRunCallback<WidgetSwapActionCallback>(),
                rippleOverride = R.drawable.widget_control_ripple,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = SWAP_GLYPH,
            maxLines = 1,
            style = TextStyle(
                color = palette.accentText,
                fontSize = metrics.swapSp.sp(),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
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

/**
 * A key's face, and the press feedback that goes with it.
 *
 * The ripple has to be named explicitly because Glance's default is
 * `?android:attr/selectableItemBackground` — an unbounded, rectangular ripple. On a
 * circular key that drew a grey *square* behind the glyph and let the bloom spill
 * out across its neighbours, which is nothing like the app. Each of these masks the
 * ripple to the key and tints it with that key's own glyph colour, the way the
 * app's halo takes its colour from the glyph it surrounds.
 */
private enum class WidgetKeyStyle(val backgroundRes: Int, val rippleRes: Int) {
    Standard(R.drawable.widget_key, R.drawable.widget_key_ripple),

    /** The operator column, a shade lighter, exactly as in the app. */
    Operator(R.drawable.widget_key_operator, R.drawable.widget_key_ripple),
    Destructive(R.drawable.widget_key, R.drawable.widget_key_ripple_destructive),
    Accent(R.drawable.widget_key_accent, R.drawable.widget_key_ripple_accent),
    ;

    fun textColor(palette: WidgetPalette): ColorProvider = when (this) {
        Standard, Operator -> palette.textPrimary
        Destructive -> palette.destructive
        Accent -> palette.onAccent
    }
}

/** Two arrows, the same mark the app uses for the swap. */
private const val SWAP_GLYPH = "⇄"

/** Shown in place of a figure until rates are available. */
private const val PLACEHOLDER = "—"

/** An untouched display. */
private const val ZERO = "0"

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
 *
 * Everything here is a function of the widget's size alone, deliberately: the
 * display band's height decides how much is left for the keys, so it must not
 * depend on what is being displayed. Only the figure size does, and it can only
 * ever shrink from [maxFigureSp] — so the band always holds whatever it is given.
 */
private data class WidgetMetrics(
    val keyDiameter: Dp,
    val padding: Dp,
    /** Width of one grid cell: the key plus its gutter. */
    val columnWidth: Dp,
    /** The whole display band: codes, figures and the running total. */
    val displayHeight: Dp,
    /** The strip the running total occupies, reserved whether used or not. */
    val totalHeight: Dp,
    /** The largest a figure may be; longer ones are fitted below this. */
    val maxFigureSp: Float,
    val codeSp: Float,
    val totalSp: Float,
    val keyGlyphSp: Float,
    /** The swap control's column, between the two figures. */
    val swapWidth: Dp,
    val swapSp: Float,
    /** How much width one figure has to itself, in dp. */
    private val figureWidth: Float,
) {
    /**
     * The size that fits the longer of the two figures into the width it has.
     *
     * Fitting the *content* rather than a guess at it is the whole point. The
     * previous version sized for "about eight characters" and clamped, so anything
     * longer was ellipsised — `117,339.45` arrived as `117,339....`, which on a
     * calculator is worse than useless.
     *
     * [fontScale] divides out because a widget's text is measured in sp and its
     * layout in dp: at a system font scale of 1.3 every glyph is a third wider than
     * its point size suggests, and a fit computed without it would overflow on
     * exactly the devices whose owners can least afford to lose digits.
     */
    fun figureSpFor(left: String, right: String, fontScale: Float): Float {
        val widest = maxOf(widthEm(left), widthEm(right)).coerceAtLeast(MIN_EM)
        val byWidth = figureWidth / widest / fontScale.coerceAtLeast(1f)
        return minOf(maxFigureSp, byWidth).coerceAtLeast(MIN_FIGURE_SP)
    }

    companion object {
        private const val GAP_RATIO = 0.16f
        private const val PADDING_RATIO = 0.14f

        /** Widest a cell may get relative to its key, before it looks disconnected. */
        private const val MAX_CELL_RATIO = 1.42f
        private const val COLUMNS = 4
        private const val ROWS = 5

        /**
         * Share of the widget height the display may take.
         *
         * Three lines now — code, figure, running total — where there were two,
         * which is what the extra share over the old 0.20 pays for.
         */
        private const val DISPLAY_SHARE = 0.27f

        /** Fraction of its row a key fills, leaving a little breathing room. */
        private const val KEY_IN_ROW = 0.88f

        private val MIN_DIAMETER = 40.dp
        private val MAX_DIAMETER = 88.dp

        /** The swap control's share of the width, and the bounds it may not leave. */
        private const val SWAP_SHARE = 0.10f
        private val MIN_SWAP = 20.dp
        private val MAX_SWAP = 40.dp

        /** A line of text occupies rather more height than its point size. */
        private const val LINE_HEIGHT = 1.28f

        private const val CODE_RATIO = 0.42f
        private const val TOTAL_RATIO = 0.50f

        /** The three lines stacked, as a multiple of the figure's own size. */
        private const val LINE_STACK = LINE_HEIGHT * (1f + CODE_RATIO + TOTAL_RATIO)

        private const val MIN_FIGURE_SP = 11f
        private const val MAX_FIGURE_SP = 32f

        /** Never size for less text than this, or a lone `0` would be enormous. */
        private const val MIN_EM = 3.6f

        fun forSize(size: DpSize): WidgetMetrics {
            val widthBudget = COLUMNS + (COLUMNS - 1) * GAP_RATIO + 2 * PADDING_RATIO
            val byWidth = size.width.value / widthBudget

            // Rows share the keypad's height evenly, so a key must fit inside one
            // row with a margin; without that the circle is clipped into an octagon.
            val byHeight = (size.height.value * (1f - DISPLAY_SHARE)) / ROWS * KEY_IN_ROW

            val diameter = minOf(byWidth, byHeight)
                .coerceIn(MIN_DIAMETER.value, MAX_DIAMETER.value)

            // When height limits the key size there is width to spare. Let the
            // cells absorb some of it rather than leaving one wide margin, but cap
            // how far apart they drift so the grid still reads as a keypad.
            val padding = diameter * PADDING_RATIO
            val availableCell = (size.width.value - 2 * padding) / COLUMNS
            val columnWidth = minOf(availableCell, diameter * MAX_CELL_RATIO)

            // The band never takes more height than its three lines need, so a tall
            // widget gives the surplus to the keypad rather than to empty air.
            val maxFigureSp = minOf(size.height.value * DISPLAY_SHARE / LINE_STACK, MAX_FIGURE_SP)
            val displayHeight = maxFigureSp * LINE_STACK

            val swapWidth = (size.width.value * SWAP_SHARE).coerceIn(MIN_SWAP.value, MAX_SWAP.value)

            return WidgetMetrics(
                keyDiameter = diameter.dp,
                padding = padding.dp,
                columnWidth = columnWidth.dp,
                displayHeight = displayHeight.dp,
                totalHeight = (maxFigureSp * TOTAL_RATIO * LINE_HEIGHT).dp,
                maxFigureSp = maxFigureSp,
                codeSp = maxFigureSp * CODE_RATIO,
                totalSp = maxFigureSp * TOTAL_RATIO,
                keyGlyphSp = diameter * 0.42f,
                swapWidth = swapWidth.dp,
                swapSp = maxFigureSp * 0.62f,
                figureWidth = (size.width.value - 2 * padding - swapWidth) / 2,
            )
        }
    }
}

/**
 * How wide a string is, in multiples of its own point size.
 *
 * Counting characters and multiplying by one average width over-measures anything
 * with separators in it — `1,651,065.83` is twelve characters but only nine of them
 * are digit-width — and an over-measured string is drawn smaller than it needed to
 * be. Money is mostly digits and separators, so the difference is worth having.
 */
internal fun widthEm(text: String): Float {
    var total = 0f
    text.forEach { char -> total += emWidth(char) }
    return total
}

private fun emWidth(char: Char): Float = when (char) {
    ',', '.', '\'', ' ' -> 0.30f
    '-', '−' -> 0.36f
    '(', ')' -> 0.34f
    '+', '×', '÷', '=', '⇄' -> 0.60f
    else -> 0.58f
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
                    onClick = actionRunCallback<WidgetKeyActionCallback>(
                        WidgetKeyActionCallback.parametersOf(action),
                    ),
                    rippleOverride = style.rippleRes,
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
