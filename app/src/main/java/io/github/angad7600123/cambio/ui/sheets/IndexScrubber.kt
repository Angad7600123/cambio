package io.github.angad7600123.cambio.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme
import kotlin.math.roundToInt

/**
 * The A–Z scrubber down the edge of the currency list.
 *
 * Modelled on One UI's contacts index rather than a column of letters: a slim
 * rounded track with one dot per section, a thumb that tracks the scroll position,
 * and — only while dragging — a large filled bubble showing the letter under your
 * finger. With 160-plus currencies a stack of 26 tappable letters is both cramped
 * and imprecise; a scrubber gives a continuous, readable target.
 *
 * Geometry and tones were measured from the reference recording: a 17dp track, a
 * 20dp thumb, and a 76dp bubble sitting 22dp clear of the track.
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
    val density = LocalDensity.current
    var draggedLetter by remember { mutableStateOf<Char?>(null) }
    var heightPx by remember { mutableStateOf(0) }

    val shown = draggedLetter ?: currentLetter
    val activeIndex = letters.indexOf(shown).takeIf { it >= 0 } ?: 0

    /** Maps a vertical touch to a letter, clamped to the ends of the track. */
    fun letterAt(y: Float, height: Int): Char {
        if (height <= 0) return letters.first()
        val inset = with(density) { (THUMB_DIAMETER / 2).toPx() }
        val usable = (height - inset * 2).coerceAtLeast(1f)
        val fraction = ((y - inset) / usable).coerceIn(0f, 1f)
        return letters[(fraction * (letters.size - 1)).roundToInt()]
    }

    val description = stringResource(R.string.cd_alphabet_index)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(TRACK_WIDTH)
            .semantics { contentDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height }
                .pointerInput(letters) {
                    detectTapGestures { offset -> onSeek(letterAt(offset.y, size.height)) }
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val letter = letterAt(offset.y, size.height)
                            draggedLetter = letter
                            onSeek(letter)
                        },
                        onDragEnd = { draggedLetter = null },
                        onDragCancel = { draggedLetter = null },
                    ) { change, _ ->
                        val letter = letterAt(change.position.y, size.height)
                        if (letter != draggedLetter) {
                            draggedLetter = letter
                            onSeek(letter)
                        }
                    }
                },
        ) {
            drawTrack(
                letterCount = letters.size,
                activeIndex = activeIndex,
                trackColor = colors.operatorKey,
                dotColor = colors.textSecondary,
                thumbColor = colors.onKey.copy(alpha = THUMB_ALPHA),
            )
        }

        // The bubble sits outside the track's own width. Compose does not clip to
        // bounds, so a negative offset lets it hang over the list as One UI's does.
        if (draggedLetter != null && heightPx > 0) {
            val inset = with(density) { (THUMB_DIAMETER / 2).toPx() }
            val usable = (heightPx - inset * 2).coerceAtLeast(1f)
            val centreY = inset + usable * activeIndex / (letters.size - 1).coerceAtLeast(1)
            val bubblePx = with(density) { BUBBLE_SIZE.toPx() }
            val gapPx = with(density) { (BUBBLE_SIZE + BUBBLE_GAP).toPx() }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = -gapPx.roundToInt(),
                            y = (centreY - bubblePx / 2).roundToInt(),
                        )
                    }
                    // requiredSize, not size: the parent is only as wide as the
                    // track, and a plain size() would be clamped to it and render
                    // the bubble as a narrow pill.
                    .requiredSize(BUBBLE_SIZE)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = draggedLetter.toString(),
                    style = CambioTextStyles.Display.copy(
                        fontSize = BUBBLE_TEXT_SIZE,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = colors.onAccent,
                )
            }
        }
    }
}

/** Draws the rail, its section dots, and the thumb. */
private fun DrawScope.drawTrack(
    letterCount: Int,
    activeIndex: Int,
    trackColor: androidx.compose.ui.graphics.Color,
    dotColor: androidx.compose.ui.graphics.Color,
    thumbColor: androidx.compose.ui.graphics.Color,
) {
    val centreX = size.width / 2
    val thumbRadius = THUMB_DIAMETER.toPx() / 2
    val usable = (size.height - thumbRadius * 2).coerceAtLeast(1f)

    drawRoundRect(
        color = trackColor,
        topLeft = Offset.Zero,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(size.width / 2),
    )

    val divisor = (letterCount - 1).coerceAtLeast(1)
    repeat(letterCount) { index ->
        val y = thumbRadius + usable * index / divisor
        if (index != activeIndex) {
            drawCircle(color = dotColor, radius = DOT_RADIUS.toPx(), center = Offset(centreX, y))
        }
    }

    val thumbY = thumbRadius + usable * activeIndex / divisor
    drawCircle(color = thumbColor, radius = thumbRadius, center = Offset(centreX, thumbY))
}

private val TRACK_WIDTH = 17.dp
private val THUMB_DIAMETER = 20.dp
private val DOT_RADIUS = 1.5.dp
private val BUBBLE_SIZE = 76.dp
private val BUBBLE_GAP = 22.dp
private val BUBBLE_TEXT_SIZE = 34.sp
private const val THUMB_ALPHA = 0.72f
