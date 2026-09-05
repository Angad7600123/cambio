package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
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

private class KeyGlowNode(private val interactionSource: InteractionSource, private val color: Color) :
    Modifier.Node(),
    DrawModifierNode {

    /** 0 when idle, 1 at full bloom. Animated so presses fade rather than snap. */
    private val glow = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press ->
                        glow.animateTo(1f, tween(durationMillis = FADE_IN_MILLIS))

                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        glow.animateTo(0f, tween(durationMillis = FADE_OUT_MILLIS))
                }
            }
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
                colorStops = arrayOf(
                    0f to color.copy(alpha = CENTRE_ALPHA * progress),
                    edge * INNER_STOP to color.copy(alpha = CENTRE_ALPHA * progress),
                    edge to color.copy(alpha = PEAK_ALPHA * progress),
                    1f to color.copy(alpha = 0f),
                ),
                center = center,
                radius = haloRadius,
            ),
            radius = haloRadius,
            center = center,
        )
    }

    private companion object {
        const val FADE_IN_MILLIS = 90
        const val FADE_OUT_MILLIS = 340

        /** How far past the key's radius the halo reaches. */
        const val HALO_SCALE = 1.32f

        /** Where the gradient starts brightening, as a fraction of the key edge. */
        const val INNER_STOP = 0.55f

        const val CENTRE_ALPHA = 0.05f
        const val PEAK_ALPHA = 0.34f
    }
}
