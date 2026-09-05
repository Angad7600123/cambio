package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme
import kotlinx.coroutines.delay

/**
 * A transient message pill.
 *
 * One UI does not put calculator errors in the display — pressing equals on an
 * incomplete expression leaves what you typed alone and floats a brief
 * "Invalid format used." over the keypad. That is a better model than blanking the
 * result: the user keeps their work and simply learns the expression is not finished
 * yet, so this reproduces it.
 *
 * The pill is translucent and non-interactive; it never blocks a keypress.
 *
 * @param message the text to show, or null for nothing.
 * @param onDismiss invoked once the display duration elapses.
 */
@Composable
fun CalcToast(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Restart the timer whenever a new message arrives, so repeated errors keep
    // the pill visible rather than letting an earlier timer cut one short.
    LaunchedEffect(message) {
        if (message != null) {
            delay(VISIBLE_MILLIS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(FADE_IN_MILLIS)),
        exit = fadeOut(tween(FADE_OUT_MILLIS)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(CambioTheme.colors.surface.copy(alpha = SCRIM_ALPHA))
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = CambioTextStyles.Secondary,
                color = CambioTheme.colors.textPrimary.copy(alpha = TEXT_ALPHA),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val VISIBLE_MILLIS = 2_000L
private const val FADE_IN_MILLIS = 120
private const val FADE_OUT_MILLIS = 260
private const val SCRIM_ALPHA = 0.92f
private const val TEXT_ALPHA = 0.85f
