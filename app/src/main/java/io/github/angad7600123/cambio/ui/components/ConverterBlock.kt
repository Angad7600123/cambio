package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.currency.ConversionSide
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.format.NumberDisplayFormatter
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * The conversion block, and the app's primary display.
 *
 * The active currency's figure *is* the big line — it is not a separate small row
 * beneath an empty display. That single decision is what keeps the caret attached
 * to the number you are typing, lets the type shrink to fit a long figure, and
 * removes the empty band that otherwise sits above everything.
 *
 * Structurally it follows the iOS converter: two figures, each paired with its
 * currency code, a rule between them and the swap control on the rule. The active
 * figure is an editable field, so the caret can be placed anywhere in it and digits
 * inserted mid-number; the idle figure is plain text.
 *
 * @param activeText the raw expression being typed, in the active currency.
 * @param activePreview the running total, shown small only when an operation is in
 *   progress and it would not merely repeat [activeText].
 * @param evaluatedExpression the expression that produced the current result, shown
 *   above after equals.
 */
@Composable
fun ConverterBlock(
    activeText: String,
    activeCursor: Int,
    activePreview: String,
    evaluatedExpression: String,
    otherValue: String,
    from: CurrencyInfo,
    to: CurrencyInfo,
    activeSide: ConversionSide,
    onCursorChange: (Int) -> Unit,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onSelectSide: (ConversionSide) -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CambioTheme.colors
    val activeCurrency = if (activeSide == ConversionSide.SOURCE) from else to
    val idleCurrency = if (activeSide == ConversionSide.SOURCE) to else from

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        // Only present after equals, where One UI puts the expression that produced
        // the result above it.
        if (evaluatedExpression.isNotEmpty()) {
            Text(
                text = evaluatedExpression,
                style = CambioTextStyles.Secondary,
                color = colors.textSecondary,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SwapButton(onClick = onSwapClick)

            Column(modifier = Modifier.weight(1f)) {
                ActiveRow(
                    text = activeText,
                    cursor = activeCursor,
                    currency = activeCurrency,
                    onCursorChange = onCursorChange,
                    onPickCurrency = if (activeSide == ConversionSide.SOURCE) onFromClick else onToClick,
                )

                if (activePreview.isNotEmpty()) {
                    Text(
                        text = activePreview,
                        style = CambioTextStyles.Secondary,
                        color = colors.textSecondary,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = CODE_COLUMN),
                    )
                }

                HorizontalDivider(
                    color = colors.outline,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                IdleRow(
                    value = otherValue,
                    currency = idleCurrency,
                    onActivate = { onSelectSide(activeSide.opposite()) },
                    onPickCurrency = if (activeSide == ConversionSide.SOURCE) onToClick else onFromClick,
                )
            }
        }
    }
}

/**
 * The figure being typed into, and the app's largest element.
 *
 * A [BasicTextField] rather than a Text, so the caret can be placed anywhere in the
 * number and digits inserted mid-string — the thing a plain read-only display
 * cannot do.
 *
 * The field is `readOnly`, which keeps the system keyboard away and blocks IME
 * edits while still letting a tap position the caret. Compose does not paint a
 * caret for a read-only field, so one is drawn here from the text layout: that is
 * the only way to have both a visible caret and no keyboard.
 */
@Composable
private fun ActiveRow(
    text: String,
    cursor: Int,
    currency: CurrencyInfo,
    onCursorChange: (Int) -> Unit,
    onPickCurrency: () -> Unit,
) {
    val colors = CambioTheme.colors
    val formatter = remember { NumberDisplayFormatter() }

    // Each new character lands small and grows, as One UI's does. Scaling is applied
    // inside the transformation so only the newest glyph moves.
    val entryScale = rememberEntryScale(text)
    val transformation = remember(colors.accentText, entryScale) {
        ExpressionTransformation.forLocale(colors.accentText, formatter, entryScale)
    }
    val transformed = remember(text, transformation) { transformation.filter(AnnotatedString(text)) }

    val target = remember(transformed.text.length) { heroSizeFor(transformed.text.length) }
    val animatedSize by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = RESIZE_MILLIS),
        label = "heroFontSize",
    )

    // A square-wave blink, matching a text caret rather than a smooth pulse.
    val blinkTransition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CARET_PERIOD_MILLIS
                1f at 0
                1f at CARET_PERIOD_MILLIS / 2
                0f at CARET_PERIOD_MILLIS / 2 + 1
                0f at CARET_PERIOD_MILLIS
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "caretAlpha",
    )

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val caretColor = colors.accentText
    val selectionColors = TextSelectionColors(
        handleColor = caretColor,
        backgroundColor = caretColor.copy(alpha = SELECTION_ALPHA),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End,
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            BasicTextField(
                value = TextFieldValue(
                    text = text,
                    selection = TextRange(cursor.coerceIn(0, text.length)),
                ),
                // Only the caret can move from here; the text itself is owned by the
                // calculator state and changed exclusively by the keypad.
                onValueChange = { onCursorChange(it.selection.start) },
                readOnly = true,
                singleLine = true,
                visualTransformation = transformation,
                onTextLayout = { layout = it },
                textStyle = CambioTextStyles.Display.copy(
                    fontSize = animatedSize.sp,
                    color = colors.textPrimary,
                    textAlign = TextAlign.End,
                ),
                modifier = Modifier
                    .weight(1f)
                    .drawWithContent {
                        drawContent()
                        val result = layout ?: return@drawWithContent
                        if (caretAlpha <= 0f) return@drawWithContent

                        // The layout describes the *transformed* string, so the caret
                        // has to be mapped out of the raw expression first.
                        val offset = transformed.offsetMapping
                            .originalToTransformed(cursor.coerceIn(0, text.length))
                            .coerceIn(0, result.layoutInput.text.length)
                        val rect = result.getCursorRect(offset)

                        drawLine(
                            color = caretColor.copy(alpha = caretAlpha),
                            start = Offset(rect.left, rect.top),
                            end = Offset(rect.left, rect.bottom),
                            strokeWidth = CARET_WIDTH.toPx(),
                            cap = StrokeCap.Round,
                        )
                    },
            )
        }

        CurrencyControl(currency = currency, onClick = onPickCurrency, isActive = true)
    }
}

/** The other currency: plain text, tappable to move the caret to it. */
@Composable
private fun IdleRow(value: String, currency: CurrencyInfo, onActivate: () -> Unit, onPickCurrency: () -> Unit) {
    val colors = CambioTheme.colors
    val target = remember(value.length) { idleSizeFor(value.length) }
    val animatedSize by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = RESIZE_MILLIS),
        label = "idleFontSize",
    )
    val description = stringResource(R.string.cd_side_inactive, currency.displayName)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = value,
            style = CambioTextStyles.Display.copy(fontSize = animatedSize.sp),
            color = colors.textSecondary,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(ROW_RADIUS))
                .clickable(onClick = onActivate)
                .padding(vertical = 2.dp)
                .semantics { contentDescription = description },
        )

        CurrencyControl(currency = currency, onClick = onPickCurrency, isActive = false)
    }
}

/** The currency code, which doubles as the picker control. */
@Composable
private fun CurrencyControl(currency: CurrencyInfo, onClick: () -> Unit, isActive: Boolean) {
    val colors = CambioTheme.colors
    val codeColor by animateColorAsState(
        targetValue = if (isActive) colors.accentText else colors.textSecondary,
        animationSpec = tween(durationMillis = ACTIVATE_MILLIS),
        label = "codeColor",
    )
    val description = stringResource(R.string.cd_pick_currency, currency.displayName, currency.code)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ROW_RADIUS))
            .clickable(onClick = onClick)
            .padding(start = CODE_GAP, end = 2.dp, top = 4.dp, bottom = 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = currency.code, style = CambioTextStyles.CurrencyCode, color = codeColor)
        Icon(
            imageVector = Icons.Rounded.UnfoldMore,
            contentDescription = null,
            tint = codeColor,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(CHEVRON_SIZE),
        )
    }
}

/**
 * The swap control, sitting on the rule between the two figures.
 *
 * Rotates a half-turn on each press so the reversal is felt, not merely inferred
 * from the two figures trading places.
 */
@Composable
private fun SwapButton(onClick: () -> Unit) {
    val colors = CambioTheme.colors
    var turns by remember { mutableIntStateOf(0) }

    val rotation by animateFloatAsState(
        targetValue = turns * HALF_TURN_DEGREES,
        animationSpec = tween(durationMillis = SWAP_MILLIS),
        label = "swapRotation",
    )

    Box(
        modifier = Modifier
            .size(TOUCH_TARGET)
            .clip(CircleShape)
            .clickable {
                turns++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.SwapVert,
            contentDescription = stringResource(R.string.cd_swap_currencies),
            tint = colors.accentText,
            modifier = Modifier
                .size(SWAP_ICON_SIZE)
                .rotate(rotation),
        )
    }
}

/**
 * Largest size that keeps the active figure on one line.
 *
 * Shrinking rather than clipping is the point: a fourteen-digit figure has to stay
 * readable, because a calculator that hides the front of your number is useless.
 */
private fun heroSizeFor(length: Int): Float = when {
    length <= 8 -> 52f
    length <= 10 -> 46f
    length <= 12 -> 40f
    length <= 15 -> 34f
    length <= 18 -> 29f
    length <= 22 -> 25f
    else -> 21f
}

/** The idle figure runs a step behind the active one. */
private fun idleSizeFor(length: Int): Float = (heroSizeFor(length) * IDLE_RATIO).coerceAtLeast(16f)

private const val IDLE_RATIO = 0.74f
private const val SELECTION_ALPHA = 0.3f
private const val CARET_PERIOD_MILLIS = 1000
private const val HALF_TURN_DEGREES = 180f
private const val SWAP_MILLIS = 320
private const val ACTIVATE_MILLIS = 180
private const val RESIZE_MILLIS = 140

private val ROW_RADIUS = 10.dp
private val CHEVRON_SIZE = 18.dp
private val SWAP_ICON_SIZE = 24.dp
private val TOUCH_TARGET = 48.dp
private val CARET_WIDTH = 2.5.dp
private val CODE_GAP = 10.dp
private val CODE_COLUMN = 56.dp
