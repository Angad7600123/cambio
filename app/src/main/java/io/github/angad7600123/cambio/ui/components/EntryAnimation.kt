package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The scale of the most recently typed character.
 *
 * One UI pops each new character in: it lands at roughly a third of its size and
 * grows to full over about three frames. Returns 1 when nothing is animating.
 *
 * Only *growth* triggers it. Deleting, clearing and evaluating all leave the text
 * still, because animating those would read as a glitch rather than as feedback.
 *
 * Shared between the expression line and the converter's active figure, since
 * which of the two receives a digit depends on whether an operator has been typed.
 */
@Composable
internal fun rememberEntryScale(text: String): Float {
    val entry = remember { Animatable(1f) }
    var previousLength by remember { mutableIntStateOf(text.length) }

    LaunchedEffect(text) {
        if (text.length > previousLength && text.isNotEmpty()) {
            entry.snapTo(ENTRY_START_SCALE)
            entry.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = ENTRY_MILLIS, easing = FastOutSlowInEasing),
            )
        } else {
            entry.snapTo(1f)
        }
        previousLength = text.length
    }

    return entry.value
}

/** How small a freshly typed character starts before scaling up. */
private const val ENTRY_START_SCALE = 0.35f

/** Measured from the reference recording: roughly three frames at 30fps. */
private const val ENTRY_MILLIS = 90
