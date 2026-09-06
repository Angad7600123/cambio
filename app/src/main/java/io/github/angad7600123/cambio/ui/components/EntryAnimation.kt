package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * A character growing into place, and where in the expression it sits.
 *
 * @param index the index *in the raw expression* of the character being animated,
 *   or -1 when nothing is animating.
 * @param scale how far through the growth it is.
 */
@Immutable
internal data class EntryAnimation(val index: Int, val scale: Float) {
    companion object {
        val None = EntryAnimation(index = -1, scale = 1f)
    }
}

/**
 * Tracks the character most recently typed, so it can be grown into place.
 *
 * **The index matters as much as the scale.** An earlier version animated whichever
 * character happened to be last in the string. Type into the middle of a figure and
 * that is the wrong one entirely: the final digit swelled and shrank, and because the
 * figure is right-aligned, its changing width dragged the whole right-hand end of the
 * number about on every keypress. Frame-stepping One UI shows the opposite — the
 * digits after the caret are frozen and only the inserted one moves — which is what
 * animating at the caret gives for free.
 *
 * Only a single-character insertion triggers it. A jump of more than one character is
 * not typing: it is a value carried in when the active side changes, or a result
 * replacing an expression, and popping a glyph there would be noise.
 *
 * Deleting, clearing and evaluating leave the text still, for the same reason.
 */
@Composable
internal fun rememberEntryAnimation(text: String, cursor: Int): EntryAnimation {
    // Held outside `remember(text)` so the previous length survives the keyed block
    // being recomputed, which is the whole point of the comparison below.
    val lengths = remember { intArrayOf(-1) }

    // Resolved during composition rather than in an effect. An effect runs *after*
    // the frame is laid out, so the new character was drawn once at full size before
    // the animation could shrink it — a single-frame pop, plainly visible when
    // stepping through a recording.
    val index = remember(text) {
        val previous = lengths[0]
        lengths[0] = text.length
        if (previous >= 0 && text.length == previous + 1 && text.isNotEmpty()) {
            // The caret sits just past what was typed, so the new character is behind it.
            (cursor - 1).coerceIn(0, text.length - 1)
        } else {
            -1
        }
    }

    // A fresh animatable per keystroke, born already small, so the very first frame
    // showing the new character shows it mid-growth.
    val scale = remember(text) { Animatable(if (index >= 0) ENTRY_START_SCALE else 1f) }

    LaunchedEffect(text) {
        if (index >= 0) {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = ENTRY_MILLIS, easing = OneUiStandard),
            )
        }
    }

    return if (index >= 0) EntryAnimation(index, scale.value) else EntryAnimation.None
}

/**
 * How small a freshly typed character starts.
 *
 * Measured off the reference recording: on the first frame after a keypress the new
 * glyph is roughly a quarter to a third the height of its neighbours.
 */
private const val ENTRY_START_SCALE = 0.3f

/** Also measured: the growth settles in a little under a fifth of a second. */
internal const val ENTRY_MILLIS = 180

/**
 * One UI's standard easing.
 *
 * A very late, very long deceleration: the glyph covers most of its growth
 * immediately and then eases into place. The measured frames bear it out — the
 * figure's width jumps in the first two frames after a keypress and then creeps for
 * another ten.
 */
private val OneUiStandard = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)
