package io.github.angad7600123.cambio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The sizes a figure is allowed to take, largest first.
 *
 * Three rungs, not a smooth ramp: One UI drops the figure a step at a time, and a
 * continuous scale makes every keypress nudge the type by a pixel or two, which
 * reads as jitter. Two shrinks cover everything up to the fifteen-digit cap.
 */
internal val FigureSizes = listOf(52.sp, 38.sp, 26.sp)

/** The same ladder for the inactive figure, which sits a step down throughout. */
internal val IdleFigureSizes = FigureSizes.map { it * IDLE_RATIO }

/**
 * Picks the largest size from [sizes] at which [text] fits inside [maxWidthPx].
 *
 * Sizing by character count — the previous approach — cannot work: it has to assume
 * a glyph width, an available width and a screen size, and it was wrong about all
 * three. Ten characters fit at 46sp on one phone and clip on another, and a string
 * of `1`s is far narrower than a string of `8`s at the same count. Measuring the
 * actual laid-out text against the actual box removes every one of those guesses,
 * so the figure fits whatever the screen.
 *
 * Returns the smallest rung when even that overflows; the caller lets the field
 * scroll from there, which is what One UI does with an expression too long to shrink
 * into view.
 *
 * [text] is the plain display string, deliberately without the styling spans the
 * field itself carries. The only span that affects width is the newest glyph's entry
 * scale, which shrinks it — so measuring unstyled is both a safe upper bound and,
 * more importantly, stable: keying the measurement on a string that changes every
 * animation frame would re-measure the figure sixty times a second.
 */
internal fun fitFontSize(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    sizes: List<TextUnit> = FigureSizes,
): TextUnit {
    if (text.isEmpty() || maxWidthPx <= 0) return sizes.first()

    val annotated = AnnotatedString(text)
    return sizes.firstOrNull { size ->
        !measurer.measure(
            text = annotated,
            style = style.copy(fontSize = size),
            maxLines = 1,
            softWrap = false,
            constraints = Constraints(maxWidth = maxWidthPx),
        ).didOverflowWidth
    } ?: sizes.last()
}

/**
 * Remembers the fitted size for a figure, re-measuring only when the outcome could
 * actually change.
 *
 * Measurement is not free and this runs on every keystroke, so it is keyed on the
 * text, the box and the style rather than recomputed each recomposition.
 */
@Composable
internal fun rememberFittedSize(
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    sizes: List<TextUnit> = FigureSizes,
): TextUnit {
    val measurer = rememberTextMeasurer()
    return remember(text, style, maxWidthPx, sizes) {
        fitFontSize(measurer, text, style, maxWidthPx, sizes)
    }
}

/** How much smaller the inactive figure runs than the active one. */
private const val IDLE_RATIO = 0.74f
