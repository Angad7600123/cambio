package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
 * One UI eases each new character in: it lands slightly under size and settles to
 * full over about a fifth of a second. Returns 1 when nothing is animating.
 *
 * The gentleness is the point. An earlier version started at a third of full size
 * over three frames, which read as a snap rather than as motion — the glyph was
 * simply in two places on consecutive frames. Starting close to full size and
 * taking longer over it is what makes the movement legible.
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
                animationSpec = tween(durationMillis = ENTRY_MILLIS, easing = OneUiStandard),
            )
        } else {
            entry.snapTo(1f)
        }
        previousLength = text.length
    }

    return entry.value
}

/** How small a freshly typed character starts before settling to full size. */
private const val ENTRY_START_SCALE = 0.7f

/** Long enough to read as movement rather than as a jump between two frames. */
private const val ENTRY_MILLIS = 180

/**
 * One UI's standard easing.
 *
 * A very late, very long deceleration: the glyph covers most of its growth
 * immediately and then eases into place, which is what gives One UI its
 * characteristic softness compared with Material's fast-out-slow-in.
 */
private val OneUiStandard = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)
