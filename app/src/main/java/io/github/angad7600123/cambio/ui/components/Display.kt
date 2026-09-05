package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
fun CalculatorDisplay(expression: String, isEditing: Boolean, modifier: Modifier = Modifier) {
    PrimaryLine(text = expression, isEditing = isEditing, modifier = modifier)
}

/**
 * The large line, with operators tinted and a blinking caret while editing.
 *
 * Font size steps down for longer content: a fixed size would either clip a
 * 15-digit number or waste the display on a short one. The change is animated so
 * it does not snap between keystrokes.
 */
@Composable
private fun PrimaryLine(text: String, isEditing: Boolean, modifier: Modifier = Modifier) {
    val colors = CambioTheme.colors

    val targetSize = remember(text.length, isEditing) {
        if (isEditing) fontSizeFor(text.length) else EVALUATED_SIZE
    }
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

    val entryScale = rememberEntryScale(text)

    val styled = buildDisplayText(
        text = text,
        operatorColor = colors.accentText,
        caretColor = if (isEditing) colors.accentText.copy(alpha = caretAlpha) else Color.Transparent,
        showCaret = isEditing,
        lastCharScale = entryScale,
        baseFontSize = animatedSize.sp,
    )

    Text(
        text = styled,
        style = CambioTextStyles.Display.copy(fontSize = animatedSize.sp),
        color = if (isEditing) colors.textPrimary else colors.textSecondary,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = modifier
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
    lastCharScale: Float = 1f,
    baseFontSize: TextUnit = TextUnit.Unspecified,
): AnnotatedString = buildAnnotatedString {
    val lastIndex = text.lastIndex
    // The freshly typed character is scaled through a font-size span. Sizing the
    // span rather than the whole line is what lets the earlier digits stay put
    // while only the new one grows.
    val animateLast = lastCharScale < 1f && baseFontSize != TextUnit.Unspecified

    text.forEachIndexed { index, char ->
        val color = if (char in ACCENTED_GLYPHS) operatorColor else Color.Unspecified
        val size = if (animateLast && index == lastIndex) {
            baseFontSize * lastCharScale
        } else {
            TextUnit.Unspecified
        }

        if (color == Color.Unspecified && size == TextUnit.Unspecified) {
            append(char)
        } else {
            withStyle(SpanStyle(color = color, fontSize = size)) { append(char) }
        }
    }

    if (showCaret) {
        withStyle(SpanStyle(color = caretColor)) { append(CARET) }
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

/** Once evaluated the expression is no longer the hero, so it shrinks. */
private val EVALUATED_SIZE = 26.sp

private const val CARET = "|"

private const val CARET_PERIOD_MILLIS = 1000
private const val RESIZE_MILLIS = 140
private const val FADE_MILLIS = 180
