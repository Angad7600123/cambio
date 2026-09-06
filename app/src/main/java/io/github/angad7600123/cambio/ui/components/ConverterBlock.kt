package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
import kotlinx.coroutines.awaitCancellation

/**
 * The conversion block, and the app's primary display.
 *
 * Two figures with a rule between them, following the iOS converter: the source
 * currency on top, the target beneath, each paired with its code. The figure you are
 * typing into *is* the big line — it is not a separate small row under an empty
 * display — which is what keeps the caret attached to the number being edited.
 *
 * **The rows never move.** Whichever side is active, the source stays above the rule
 * and the target below it; activating the other side changes only which figure is
 * lit and where the caret sits. An earlier version promoted the active side to the
 * top, so tapping a figure made both it and its currency appear to jump across the
 * screen — the numbers looked like they had swapped when nothing had.
 *
 * The swap control lives *on* the rule rather than in a column beside the figures.
 * As a sibling it stole its own width from both rows for its whole height, and long
 * figures ran underneath it and were clipped. On the rule it costs the figures
 * nothing, so they run the full width of the screen.
 *
 * @param activeText the raw expression being typed, in the active currency.
 * @param activePreview the running total, shown small beneath the active figure only
 *   when an operation is in progress.
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
    val sourceActive = activeSide == ConversionSide.SOURCE

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

        FigureRow(
            text = if (sourceActive) activeText else otherValue,
            cursor = activeCursor,
            currency = from,
            isActive = sourceActive,
            onCursorChange = onCursorChange,
            onActivate = { onSelectSide(ConversionSide.SOURCE) },
            onPickCurrency = onFromClick,
        )

        if (sourceActive) {
            PreviewLine(activePreview)
        }

        SwapRule(onSwapClick = onSwapClick)

        FigureRow(
            text = if (sourceActive) otherValue else activeText,
            cursor = activeCursor,
            currency = to,
            isActive = !sourceActive,
            onCursorChange = onCursorChange,
            onActivate = { onSelectSide(ConversionSide.TARGET) },
            onPickCurrency = onToClick,
        )

        if (!sourceActive) {
            PreviewLine(activePreview)
        }
    }
}

/**
 * One of the two figures.
 *
 * Active and inactive are the same row rather than two composables, so the two can
 * never drift apart in size, alignment or spacing. Which one is live is carried
 * entirely by colour — the primary tone against the secondary — as One UI does it;
 * an earlier attempt outlined the active figure, which fought the flat display.
 *
 * The active figure is a text field so the caret can be placed inside it. The
 * inactive one is plain text, tappable to become the active one.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FigureRow(
    text: String,
    cursor: Int,
    currency: CurrencyInfo,
    isActive: Boolean,
    onCursorChange: (Int) -> Unit,
    onActivate: () -> Unit,
    onPickCurrency: () -> Unit,
) {
    val colors = CambioTheme.colors
    val formatter = remember { NumberDisplayFormatter() }

    // Each new character eases in, as One UI's does. The scale is applied inside the
    // transformation so only the newest glyph moves, not the whole figure.
    val typedScale = rememberEntryScale(text)
    val entryScale = if (isActive) typedScale else 1f
    val transformation = remember(colors.accentText, entryScale) {
        ExpressionTransformation.forLocale(colors.accentText, formatter, entryScale)
    }

    // The active figure holds a raw expression that the transformation formats for
    // display; the inactive one arrives already formatted from the view model, so it
    // is measured and drawn exactly as given.
    val displayed = remember(text, transformation, isActive) {
        if (isActive) transformation.filter(AnnotatedString(text)).text.text else text
    }

    // The figure's own box, measured rather than assumed — this is what the type
    // size is fitted against, so the same code lands correctly on any screen width.
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val baseStyle = CambioTextStyles.Display
    val fitted = rememberFittedSize(
        text = displayed,
        style = baseStyle,
        maxWidthPx = fieldWidthPx,
        sizes = if (isActive) FigureSizes else IdleFigureSizes,
    )
    val animatedSize by animateFloatAsState(
        targetValue = fitted.value,
        animationSpec = tween(durationMillis = RESIZE_MILLIS),
        label = "figureFontSize",
    )

    val textColor by animateColorAsState(
        targetValue = if (isActive) colors.textPrimary else colors.textSecondary,
        animationSpec = tween(durationMillis = ACTIVATE_MILLIS),
        label = "figureColor",
    )

    val caretColor = colors.accentText
    val selectionColors = TextSelectionColors(
        handleColor = caretColor,
        backgroundColor = caretColor.copy(alpha = SELECTION_ALPHA),
    )
    val style = baseStyle.copy(
        fontSize = animatedSize.sp,
        color = textColor,
        textAlign = TextAlign.End,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { fieldWidthPx = it.width },
            contentAlignment = Alignment.BottomEnd,
        ) {
            if (isActive) {
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    // Never let a text input session start. This is what keeps the
                    // soft keyboard away while the field stays fully editable, and
                    // an editable field is the only kind the platform gives a caret
                    // and a drag handle to. `showKeyboardOnFocus` alone is not
                    // enough: it covers focus, but tapping an already-focused field
                    // asks for the keyboard again, and it duly appeared.
                    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                        BasicTextField(
                            value = TextFieldValue(
                                text = text,
                                selection = TextRange(cursor.coerceIn(0, text.length)),
                            ),
                            // The field is editable so the platform draws its caret and
                            // the drag handle beneath it; every actual edit is rejected
                            // below, leaving the keypad as the only way to change the
                            // text. Making it read-only instead is what suppressed the
                            // handle, since a read-only field has nothing to insert at.
                            onValueChange = { new ->
                                if (new.text == text) onCursorChange(new.selection.start)
                            },
                            // Belt and braces with the interceptor above: this
                            // stops the request being made at all on focus, rather
                            // than making it and refusing to serve it.
                            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                            singleLine = true,
                            visualTransformation = transformation,
                            cursorBrush = SolidColor(caretColor),
                            textStyle = style,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                val description = stringResource(R.string.cd_side_inactive, currency.displayName)
                Text(
                    text = text,
                    style = style,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ROW_RADIUS))
                        .clickable(onClick = onActivate)
                        .semantics { contentDescription = description },
                )
            }
        }

        CurrencyControl(currency = currency, onClick = onPickCurrency, isActive = isActive)
    }
}

/** The running total under the active figure, when it says more than the figure does. */
@Composable
private fun PreviewLine(preview: String) {
    if (preview.isEmpty()) return

    Text(
        text = preview,
        style = CambioTextStyles.Secondary,
        color = CambioTheme.colors.textSecondary,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = CODE_COLUMN),
    )
}

/**
 * The rule between the two figures, with the swap control at its left end.
 *
 * The arrows are a bare glyph on a transparent touch target: a filled circle here
 * would read as a third element competing with the two figures, when all this does
 * is reverse them. The rule starts where the glyph ends so the two read as one
 * piece of furniture rather than as a line with something parked on it.
 */
@Composable
private fun SwapRule(onSwapClick: () -> Unit) {
    val colors = CambioTheme.colors
    var turns by remember { mutableIntStateOf(0) }

    val rotation by animateFloatAsState(
        targetValue = turns * HALF_TURN_DEGREES,
        animationSpec = tween(durationMillis = SWAP_MILLIS),
        label = "swapRotation",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RULE_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                // Sized to the glyph, not to a 48dp target. A padded target held the
                // arrows a finger's width in from the margin, which is the gap that
                // made the control look unaligned with everything below it.
                .size(SWAP_ICON_SIZE)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    turns++
                    onSwapClick()
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

        HorizontalDivider(
            color = colors.outline,
            modifier = Modifier
                .weight(1f)
                .padding(start = RULE_GAP),
        )
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

private const val SELECTION_ALPHA = 0.3f
private const val HALF_TURN_DEGREES = 180f
private const val SWAP_MILLIS = 320
private const val ACTIVATE_MILLIS = 180
private const val RESIZE_MILLIS = 140

private val ROW_RADIUS = 10.dp
private val CHEVRON_SIZE = 18.dp
private val SWAP_ICON_SIZE = 24.dp
private val RULE_GAP = 8.dp
private val CODE_GAP = 10.dp
private val CODE_COLUMN = 56.dp
