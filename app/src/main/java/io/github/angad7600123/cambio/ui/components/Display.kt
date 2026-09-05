package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.format.ExpressionFormatter
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * The number display.
 *
 * The hierarchy follows One UI rather than a conventional calculator: **while you
 * are typing, the expression is the large line** and the running result sits small
 * and grey beneath it. Pressing equals swaps them — the result becomes the hero and
 * the expression it came from shrinks to the line above.
 *
 * Content is right-aligned. Long values scroll horizontally rather than wrapping,
 * and the primary line steps down in size as it grows so it stays on one line.
 *
 * The result carries a polite live region so TalkBack announces new values instead
 * of leaving a blind user to hunt for the change.
 *
 * @param primary the large line.
 * @param secondary the small line beneath it; blank hides it.
 * @param isEditing true while typing, which colours the operators and shows the
 *   caret. False once equals has been pressed.
 */
@Composable
fun CalculatorDisplay(primary: String, secondary: String, isEditing: Boolean, modifier: Modifier = Modifier) {
    val colors = CambioTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom,
    ) {
        PrimaryLine(text = primary, isEditing = isEditing)

        // Kept in the tree even when blank so the primary line does not shift
        // vertically the moment a preview appears.
        Text(
            text = secondary,
            style = CambioTextStyles.Secondary,
            color = colors.textSecondary,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * The large line, with operators tinted and a blinking caret while editing.
 *
 * Font size steps down for longer content: a fixed size would either clip a
 * 15-digit number or waste the display on a short one. The change is animated so
 * it does not snap between keystrokes.
 */
@Composable
private fun PrimaryLine(text: String, isEditing: Boolean) {
    val colors = CambioTheme.colors

    val targetSize = remember(text.length) { fontSizeFor(text.length) }
    val animatedSize by animateFloatAsState(
        targetValue = targetSize.value,
        animationSpec = tween(durationMillis = RESIZE_MILLIS),
        label = "primaryFontSize",
    )

    // A square-wave blink, matching a text caret rather than a smooth pulse.
    val transition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by transition.animateFloat(
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

    val styled = remember(text, isEditing, caretAlpha > 0f, colors.accentText, colors.textPrimary) {
        buildDisplayText(
            text = text,
            operatorColor = colors.accentText,
            caretColor = if (isEditing) colors.accentText.copy(alpha = caretAlpha) else Color.Transparent,
            showCaret = isEditing,
        )
    }

    Text(
        text = styled,
        style = CambioTextStyles.Display.copy(fontSize = animatedSize.sp),
        color = colors.textPrimary,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Tints operator glyphs and appends the caret.
 *
 * Operators are rendered in the accent tone so a long expression stays readable at
 * a glance — the eye can find the operations without parsing every digit.
 */
internal fun buildDisplayText(
    text: String,
    operatorColor: Color,
    caretColor: Color,
    showCaret: Boolean,
): AnnotatedString = buildAnnotatedString {
    text.forEach { char ->
        if (char in ACCENTED_GLYPHS) {
            withStyle(SpanStyle(color = operatorColor)) { append(char) }
        } else {
            append(char)
        }
    }
    if (showCaret) {
        withStyle(SpanStyle(color = caretColor)) { append(CARET) }
    }
}

/**
 * The converted amount, shown beneath the currency selectors.
 *
 * Fades rather than popping in, because it depends on the network and would
 * otherwise appear at an arbitrary moment.
 */
@Composable
fun ConvertedAmount(amount: String?, currencyCode: String, modifier: Modifier = Modifier) {
    val colors = CambioTheme.colors

    AnimatedVisibility(
        visible = amount != null,
        enter = fadeIn(tween(FADE_MILLIS)),
        exit = fadeOut(tween(FADE_MILLIS)),
        modifier = modifier,
    ) {
        val shown = amount.orEmpty()
        val description = stringResource(R.string.cd_converted_amount, shown, currencyCode)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = shown,
                style = CambioTextStyles.Converted,
                color = colors.accentText,
                maxLines = 1,
            )
            Text(
                text = " $currencyCode",
                style = CambioTextStyles.CurrencyCode,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/** Picks the largest display size that keeps [length] characters on one line. */
private fun fontSizeFor(length: Int): TextUnit = when {
    length <= 11 -> 45.sp
    length <= 14 -> 40.sp
    length <= 18 -> 34.sp
    length <= 24 -> 28.sp
    else -> 24.sp
}

/** Glyphs rendered in the accent tone inside the expression. */
private val ACCENTED_GLYPHS = setOf(
    ExpressionFormatter.MULTIPLY_GLYPH.first(),
    ExpressionFormatter.DIVIDE_GLYPH.first(),
    ExpressionFormatter.MINUS_GLYPH.first(),
    ExpressionFormatter.PLUS_GLYPH.first(),
    '(',
    ')',
)

private const val CARET = "|"
private const val CARET_PERIOD_MILLIS = 1000
private const val RESIZE_MILLIS = 140
private const val FADE_MILLIS = 180
