package io.github.angad7600123.cambio.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.calculator.CalcResult
import io.github.angad7600123.cambio.calculator.CalculatorEngine
import io.github.angad7600123.cambio.calculator.OperatorType
import io.github.angad7600123.cambio.currency.ConversionEngine
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

            val value = if (hasError) null else previewValue(expression)
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

            val resultText = when {
                hasError -> context.getString(R.string.calc_error_short)
                value != null -> numberFormatter.format(value)
                else -> "0"
            }

            GlanceTheme {
                WidgetBody(
                    expressionText = expressionFormatter.formatSecondary(expression, resultText),
                    resultText = resultText,
                    convertedText = converted,
                    fromCode = settings.fromCurrency,
                    toCode = settings.toCurrency,
                    isError = hasError,
                    palette = palette,
                )
            }
        }
    }

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
    expressionText: String,
    resultText: String,
    convertedText: String?,
    fromCode: String,
    toCode: String,
    isError: Boolean,
    palette: WidgetPalette,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(12.dp),
    ) {
        WidgetDisplay(
            expressionText = expressionText,
            resultText = resultText,
            convertedText = convertedText,
            fromCode = fromCode,
            toCode = toCode,
            isError = isError,
            palette = palette,
        )
        // The keypad takes all the space the display does not, so the widget never
        // leaves a dead band at the bottom.
        WidgetKeypad(palette, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun WidgetDisplay(
    expressionText: String,
    resultText: String,
    convertedText: String?,
    fromCode: String,
    toCode: String,
    isError: Boolean,
    palette: WidgetPalette,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = expressionText.ifEmpty { " " },
            maxLines = 1,
            style = TextStyle(
                color = palette.textSecondary,
                fontSize = 13.sp(),
                textAlign = TextAlign.End,
            ),
        )
        Text(
            text = resultText,
            maxLines = 1,
            style = TextStyle(
                color = if (isError) palette.destructive else palette.textPrimary,
                fontSize = 30.sp(),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            ),
        )
        Text(
            text = convertedText?.let { "$it $toCode" } ?: "$fromCode → $toCode",
            maxLines = 1,
            style = TextStyle(
                color = palette.accentText,
                fontSize = 15.sp(),
                textAlign = TextAlign.End,
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
private fun WidgetKeypad(palette: WidgetPalette, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        WIDGET_KEY_ROWS.forEach { row ->
            WidgetRow(modifier = GlanceModifier.defaultWeight()) {
                row.forEach { spec ->
                    WidgetKey(
                        label = spec.label,
                        style = spec.style,
                        action = spec.action,
                        palette = palette,
                        modifier = GlanceModifier.defaultWeight(),
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
        WidgetKeySpec("÷", WidgetKeyStyle.Standard, WidgetAction.DIVIDE),
    ),
    listOf(
        WidgetKeySpec("7", WidgetKeyStyle.Standard, WidgetAction.DIGIT_7),
        WidgetKeySpec("8", WidgetKeyStyle.Standard, WidgetAction.DIGIT_8),
        WidgetKeySpec("9", WidgetKeyStyle.Standard, WidgetAction.DIGIT_9),
        WidgetKeySpec("×", WidgetKeyStyle.Standard, WidgetAction.MULTIPLY),
    ),
    listOf(
        WidgetKeySpec("4", WidgetKeyStyle.Standard, WidgetAction.DIGIT_4),
        WidgetKeySpec("5", WidgetKeyStyle.Standard, WidgetAction.DIGIT_5),
        WidgetKeySpec("6", WidgetKeyStyle.Standard, WidgetAction.DIGIT_6),
        WidgetKeySpec("−", WidgetKeyStyle.Standard, WidgetAction.SUBTRACT),
    ),
    listOf(
        WidgetKeySpec("1", WidgetKeyStyle.Standard, WidgetAction.DIGIT_1),
        WidgetKeySpec("2", WidgetKeyStyle.Standard, WidgetAction.DIGIT_2),
        WidgetKeySpec("3", WidgetKeyStyle.Standard, WidgetAction.DIGIT_3),
        WidgetKeySpec("+", WidgetKeyStyle.Standard, WidgetAction.ADD),
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private enum class WidgetKeyStyle(val backgroundRes: Int) {
    Standard(R.drawable.widget_key),
    Destructive(R.drawable.widget_key),
    Accent(R.drawable.widget_key_accent),
    ;

    fun textColor(palette: WidgetPalette): ColorProvider = when (this) {
        Standard -> palette.textPrimary
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
/** Fixed key diameter; keeps keys circular at every widget size. */
private val KEY_DIAMETER = 46.dp

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
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .size(KEY_DIAMETER)
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
                    fontSize = 17.sp(),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/** Glance text sizes are expressed in sp via the Compose unit type. */
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)
