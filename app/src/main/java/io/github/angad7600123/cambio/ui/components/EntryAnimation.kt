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
                animationSpec = tween(durationMillis = ENTRY_MILLIS, easing = SmoothFlow),
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
 * The curve the reference actually moves on.
 *
 * Fitted, not chosen: the display's horizontal travel was measured frame by frame
 * across a keystroke and the cumulative curve came out very nearly straight, with
 * soft ends — at the halfway point it has covered 53% of the distance. This ease
 * tracks that to within 0.05, where the curve used before was out by 0.28.
 *
 * That earlier curve was the whole problem. `CubicBezier(0.22, 0.25, 0, 1)` is
 * ferociously front-loaded: 62% of the travel in the first fifth of the duration.
 * The line lurched and then crept, and since the glyph's growth is what widens the
 * text, the lurch dragged the entire figure with it. Measured on device, single
 * keystrokes were completing 83% and sometimes 100% of their movement inside one
 * frame — a teleport, not an animation. Spreading the same distance evenly over the
 * same time is the difference between the two.
 */
internal val SmoothFlow = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
