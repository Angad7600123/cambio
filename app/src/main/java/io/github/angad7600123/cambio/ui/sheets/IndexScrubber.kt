package io.github.angad7600123.cambio.ui.sheets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme
import io.github.angad7600123.cambio.ui.theme.rememberIndexBubbleColor
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The A–Z scrubber down the edge of the currency list.
 *
 * Modelled on One UI's contacts index rather than a column of letters: a slim
 * rounded track with one dot per section, a thumb that follows the scroll position,
 * and — while your finger is down — a drop showing the letter under it. With
 * 160-plus currencies a stack of 26 tappable letters is both cramped and imprecise;
 * a scrubber gives a continuous, readable target.
 *
 * The drop is a teardrop pulled out of the rail, not a circle floating beside it.
 * Its tip stays pinned to the track while its body grows from nothing on touch and
 * is drawn back in on release, so it reads as liquid coming off the rail and falling
 * back into it. A detached circle has no such relationship to the thing it belongs
 * to, which is what made the earlier version feel bolted on.
 *
 * Rail, dots, thumb and drop are all one canvas. The drop is several times wider
 * than the 17dp track, and as a composable it had to fight its parent's constraints
 * to avoid being squashed into a pill; as a path it simply extends left from the tip.
 *
 * @param letters the section initials, in order.
 * @param currentLetter the letter the list is scrolled to, for the resting thumb.
 * @param onSeek invoked with the letter under the finger, continuously while
 *   dragging, so the list follows rather than jumping only on release.
 */
@Composable
internal fun IndexScrubber(
    letters: List<Char>,
    currentLetter: Char?,
    onSeek: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    val colors = CambioTheme.colors
    val dropColor = rememberIndexBubbleColor()
    val measurer = rememberTextMeasurer()

    var touchedLetter by remember { mutableStateOf<Char?>(null) }

    // One animatable drives the drop's radius, its tail and its opacity together.
    // Sharing a single value is what keeps the collapse reading as one gesture
    // rather than as three things finishing at slightly different moments.
    val emergence = remember { Animatable(0f) }

    LaunchedEffect(touchedLetter != null) {
        val forming = touchedLetter != null
        emergence.animateTo(
            targetValue = if (forming) 1f else 0f,
            animationSpec = spring(
                // A little overshoot as it forms, none as it retracts: a drop swells
                // past its size when it appears and is simply drawn back in.
                dampingRatio = if (forming) Spring.DampingRatioMediumBouncy else 1f,
                // The retraction is deliberately the slower of the two. At the same
                // stiffness as the growth it crossed most of its travel inside a
                // single frame, so the drop appeared to vanish rather than to fall
                // back into the rail — the part of the motion worth seeing.
                stiffness = if (forming) Spring.StiffnessMediumLow else Spring.StiffnessLow,
            ),
        )
    }

    // The letter under the finger while touching, the list's own position otherwise.
    val shown = touchedLetter ?: currentLetter
    val activeIndex = letters.indexOf(shown).takeIf { it >= 0 } ?: 0

    val description = stringResource(R.string.cd_alphabet_index)
    val letterStyle = CambioTextStyles.Display.copy(
        fontSize = DROP_TEXT_SIZE,
        fontWeight = FontWeight.Normal,
        // The drop takes the system accent, whose tone Cambio does not control, so
        // the letter is chosen against the drop rather than from the palette.
        color = if (dropColor.luminance() < MID_LUMINANCE) Color.White else Color.Black,
    )

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(TRACK_WIDTH)
            .semantics { contentDescription = description }
            .pointerInput(letters) {
                awaitEachGesture {
                    // A single down-move-up loop rather than separate tap and drag
                    // detectors: One UI's index answers the instant you touch it, and
                    // a drag detector says nothing until the touch passes slop.
                    val down = awaitFirstDown(requireUnconsumed = false)

                    fun seekTo(y: Float) {
                        val letter = letterAt(y, size.height, letters, THUMB_DIAMETER.toPx())
                        if (letter != touchedLetter) {
                            touchedLetter = letter
                            onSeek(letter)
                        }
                    }

                    seekTo(down.position.y)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        seekTo(pointer.position.y)
                        pointer.consume()
                    }

                    touchedLetter = null
                }
            },
    ) {
        val thumbRadius = THUMB_DIAMETER.toPx() / 2
        val usable = (size.height - thumbRadius * 2).coerceAtLeast(1f)
        val divisor = (letters.size - 1).coerceAtLeast(1)
        val activeY = thumbRadius + usable * activeIndex / divisor

        drawRail(letters.size, activeIndex, thumbRadius, usable, colors.operatorKey, colors.textSecondary)
        drawCircle(
            color = colors.onKey.copy(alpha = THUMB_ALPHA),
            radius = thumbRadius,
            center = Offset(size.width / 2, activeY),
        )

        if (emergence.value > 0f && shown != null) {
            drawDrop(
                letter = shown,
                tipY = activeY,
                progress = emergence.value,
                color = dropColor,
                measurer = measurer,
                style = letterStyle,
            )
        }
    }
}

/** Draws the rounded rail and its section dots, skipping the one under the thumb. */
private fun DrawScope.drawRail(
    letterCount: Int,
    activeIndex: Int,
    thumbRadius: Float,
    usable: Float,
    trackColor: Color,
    dotColor: Color,
) {
    drawRoundRect(
        color = trackColor,
        topLeft = Offset.Zero,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(size.width / 2),
    )

    val divisor = (letterCount - 1).coerceAtLeast(1)
    repeat(letterCount) { index ->
        if (index == activeIndex) return@repeat
        val y = thumbRadius + usable * index / divisor
        drawCircle(
            color = dotColor,
            radius = DOT_RADIUS.toPx(),
            center = Offset(size.width / 2, y),
        )
    }
}

/**
 * Draws the drop hanging off the rail at [tipY], grown to [progress].
 *
 * The shape is the two tangent lines from a fixed tip to a growing circle, closed by
 * the major arc between their contact points. Because the tip never moves while the
 * circle does, the tail lengthens and narrows as the drop forms and is swallowed
 * back into the rail as it collapses: the geometry performs the animation, so there
 * is nothing to keep in step by hand.
 */
private fun DrawScope.drawDrop(
    letter: Char,
    tipY: Float,
    progress: Float,
    color: Color,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val radius = DROP_RADIUS.toPx() * progress
    if (radius <= 1f) return

    // The tip sits on the rail's own edge, so drop and track always touch.
    val tipX = 0f
    val centreX = tipX - (radius + TAIL_LENGTH.toPx() * progress)
    val distance = tipX - centreX
    val bounds = Rect(centreX - radius, tipY - radius, centreX + radius, tipY + radius)

    val path = Path()
    if (distance > radius) {
        // Half-angle at the circle's centre between the line to the tip — which runs
        // along +x — and each of the two tangents.
        val alpha = acos(radius / distance)

        path.moveTo(centreX + radius * cos(-alpha), tipY + radius * sin(-alpha))
        path.lineTo(tipX, tipY)
        path.lineTo(centreX + radius * cos(alpha), tipY + radius * sin(alpha))
        path.arcTo(
            rect = bounds,
            startAngleDegrees = Math.toDegrees(alpha.toDouble()).toFloat(),
            // The long way round, back to where the first tangent met the circle.
            sweepAngleDegrees = FULL_TURN_DEGREES - Math.toDegrees(2.0 * alpha).toFloat(),
            forceMoveTo = false,
        )
        path.close()
    } else {
        // Still swallowed by the rail: a forming drop is simply a circle.
        path.addOval(bounds)
    }

    drawPath(path = path, color = color)

    // Squared so the letter trails the shape's own growth and never appears at full
    // strength on a drop that is still a sliver.
    val laid = measurer.measure(letter.toString(), style)
    drawText(
        textLayoutResult = laid,
        topLeft = Offset(centreX - laid.size.width / 2f, tipY - laid.size.height / 2f),
        alpha = progress * progress,
    )
}

/** Maps a vertical touch to a letter, clamped to the ends of the track. */
internal fun letterAt(y: Float, height: Int, letters: List<Char>, thumbDiameterPx: Float): Char {
    if (height <= 0) return letters.first()
    val inset = thumbDiameterPx / 2
    val usable = (height - inset * 2).coerceAtLeast(1f)
    val fraction = ((y - inset) / usable).coerceIn(0f, 1f)
    return letters[(fraction * (letters.size - 1)).roundToInt()]
}

private val TRACK_WIDTH = 17.dp
private val THUMB_DIAMETER = 20.dp
private val DOT_RADIUS = 1.5.dp
private val DROP_RADIUS = 32.dp
private val TAIL_LENGTH = 14.dp
private val DROP_TEXT_SIZE = 30.sp
private const val THUMB_ALPHA = 0.72f
private const val FULL_TURN_DEGREES = 360f
private const val MID_LUMINANCE = 0.5f
