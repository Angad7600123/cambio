package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * The number display: the expression being typed, the running result, and the
 * converted amount.
 *
 * Content is right-aligned and bottom-anchored, the way a physical calculator
 * reads. Long values scroll horizontally rather than wrapping or truncating, and
 * the result's font size steps down as the number grows so it stays on one line
 * without ever becoming unreadably small.
 *
 * The result carries a polite live region so TalkBack announces new values as they
 * are calculated, instead of leaving a blind user to hunt for the change.
 */
@Composable
fun CalculatorDisplay(expression: String, result: String, errorMessage: String?, modifier: Modifier = Modifier) {
    val colors = CambioTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // The expression line. Kept present (as empty space) rather than removed, so
        // the result does not jump vertically as typing begins.
        Text(
            text = expression,
            style = CambioTextStyles.Expression,
            color = colors.textSecondary,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), reverseScrolling = true),
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = CambioTextStyles.Converted,
                color = colors.destructive,
                maxLines = 2,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            )
        } else {
            AutoSizingResult(result = result)
        }
    }
}

/**
 * Renders the result, stepping the font size down for longer numbers.
 *
 * A fixed size would either clip a 15-digit result or waste the display on a
 * short one. Stepping keeps the number on a single line at the largest size that
 * fits, and the change is animated so it does not snap between keystrokes.
 */
@Composable
private fun AutoSizingResult(result: String) {
    val colors = CambioTheme.colors

    val targetSize = remember(result.length) { fontSizeFor(result.length) }
    val animatedSize by animateFloatAsState(
        targetValue = targetSize.value,
        animationSpec = tween(durationMillis = RESIZE_MILLIS),
        label = "resultFontSize",
    )

    Text(
        text = result,
        style = CambioTextStyles.Display.copy(fontSize = animatedSize.sp),
        color = colors.textPrimary,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The converted amount, shown beneath the result.
 *
 * Fades in and out rather than appearing abruptly, because it depends on the
 * network and would otherwise pop in at an arbitrary moment.
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
                color = colors.accent,
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
    length <= 7 -> 72.sp
    length <= 9 -> 62.sp
    length <= 12 -> 52.sp
    length <= 16 -> 42.sp
    else -> 34.sp
}

private const val RESIZE_MILLIS = 140
private const val FADE_MILLIS = 180
