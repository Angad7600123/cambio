package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The press indication used by the calculator keys.
 *
 * One UI does not clip its key ripple to the button. It blooms a soft halo
 * *outside* the circle, tinted with that key's own glyph colour, so pressing
 * backspace throws a coral glow and pressing equals a jade one. Material's stock
 * ripple is clipped to the shape and all but disappears on a true-black canvas,
 * which is why this replaces it rather than tweaking it.
 *
 * The halo is a radial gradient that is transparent at the centre, peaks just
 * outside the key's edge, and fades to nothing — so it reads as a rim of light
 * around the key rather than a wash across its face.
 *
 * @param color the halo tint, normally the key's content colour.
 */
class KeyGlow(private val color: Color) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode = KeyGlowNode(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is KeyGlow && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

/** Where the glow is heading, and how long it may take to get there. */
internal data class GlowStep(val target: Float, val durationMillis: Int)

/** The glow a single interaction calls for, or null for one that does not affect it. */
internal fun glowStepFor(interaction: Interaction): GlowStep? = when (interaction) {
    is PressInteraction.Press -> GlowStep(target = 1f, durationMillis = GLOW_FADE_IN_MILLIS)
    is PressInteraction.Release, is PressInteraction.Cancel -> GlowStep(
        target = 0f,
        durationMillis = GLOW_FADE_OUT_MILLIS,
    )
    else -> null
}

/**
 * Drives the glow from a stream of press interactions, one at a time, newest wins.
 *
 * **Each animation runs in its own child job, and starting one cancels the last.**
 * That is the whole point of this function, and the previous version got it wrong in
 * a way worth recording: it awaited `animateTo` inside `collect`. Because `collect`
 * runs its body sequentially and `animateTo` suspends for the animation's full
 * duration, the collector was blocked for 220ms after every press — so the *next*
 * press could not even be read from the flow until the last one had finished
 * animating. Tapping faster than that built a backlog, and the glow ran further and
 * further behind the finger, still lighting keys after the user had stopped.
 *
 * The buffer made it worse. An interaction source is a shared flow with a bounded
 * buffer that drops on overflow, so a fast enough burst could lose a *Release*
 * outright and strand a key at full glow with nothing left to turn it off.
 *
 * Cancelling rather than awaiting also gives the behaviour you want on its own
 * terms: press a key twice quickly and the second press takes the halo over
 * immediately, from wherever the first had got to, instead of queueing behind it.
 */
internal fun CoroutineScope.launchGlow(interactions: Flow<Interaction>, animate: suspend (GlowStep) -> Unit): Job =
    launch {
        var running: Job? = null

        interactions.collect { interaction ->
            val step = glowStepFor(interaction) ?: return@collect
            // Cancelled, not joined: waiting for the old animation to unwind would put
            // the delay back, in smaller form.
            running?.cancel()
            running = launch { animate(step) }
        }
    }

private class KeyGlowNode(private val interactionSource: InteractionSource, private val color: Color) :
    Modifier.Node(),
    DrawModifierNode {

    /** 0 when idle, 1 at full bloom. Animated so presses fade rather than snap. */
    private val glow = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launchGlow(interactionSource.interactions) { step ->
            glow.animateTo(step.target, tween(durationMillis = step.durationMillis))
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        val progress = glow.value
        if (progress <= 0f) return

        val keyRadius = size.minDimension / 2f
        val haloRadius = keyRadius * HALO_SCALE
        // Where the key's own edge falls within the halo; the peak sits just past
        // it so the light appears to escape from behind the key.
        val edge = keyRadius / haloRadius

        drawCircle(
            brush = Brush.radialGradient(
                // Five stops, not three. The decay past the peak is what makes this
                // read as light rather than as a drawn ring, and it needs enough
                // stops to curve: measured off the reference, the brightness falls to
                // roughly a half, then a quarter, then a tenth of its peak on the way
                // out. Three stops gave a straight ramp to zero over a sixth of the
                // key's radius, which is a hard edge — visibly a circle outline.
                colorStops = arrayOf(
                    0f to color.copy(alpha = CENTRE_ALPHA * progress),
                    edge * INNER_STOP to color.copy(alpha = INNER_ALPHA * progress),
                    edge to color.copy(alpha = PEAK_ALPHA * progress),
                    edge + (1f - edge) * 0.33f to color.copy(alpha = PEAK_ALPHA * 0.53f * progress),
                    edge + (1f - edge) * 0.63f to color.copy(alpha = PEAK_ALPHA * 0.24f * progress),
                    1f to color.copy(alpha = 0f),
                ),
                center = center,
                radius = haloRadius,
            ),
            radius = haloRadius,
            center = center,
        )
    }
}

/**
 * Quick enough that any real tap reaches full bloom.
 *
 * A finger is down for 50ms at the very least, so the halo is at or near full by the
 * time the release arrives and the fade begins from there.
 */
internal const val GLOW_FADE_IN_MILLIS = 40

/**
 * The whole life of the glow after you lift off, and now genuinely the whole of it:
 * with nothing queued behind it, the last release fades out and the key is dark
 * exactly this long afterwards.
 */
internal const val GLOW_FADE_OUT_MILLIS = 110

/**
 * How far past the key's radius the halo reaches.
 *
 * Measured against the reference, whose light carries about half a key-radius beyond
 * the edge before it dies. At the previous 1.16 the whole outward fade had to happen
 * within a sixth of a radius — about 16px on a real key — which is too short a
 * distance to read as a fade at all.
 */
private const val HALO_SCALE = 1.5f

/** Where the gradient starts brightening on its way out to the key's edge. */
private const val INNER_STOP = 0.72f

/**
 * The key's own face is not quite dark under a press.
 *
 * The reference shows the face lifting slightly along with the rim; leaving it at
 * zero made the halo look detached from the key it belongs to.
 */
private const val CENTRE_ALPHA = 0.05f
private const val INNER_ALPHA = 0.11f
private const val PEAK_ALPHA = 0.34f
