package io.github.angad7600123.cambio.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * The visual role a key plays.
 *
 * Three tiers, matching One UI: digits and utilities sit on the darkest surface,
 * the operator column one step lighter so it reads as its own group, and equals
 * as the single filled key.
 */
enum class KeyStyle {
    /** Digits, decimal point and parentheses: white glyph on the darkest key. */
    Standard,

    /** The operator column. Same glyph colour, one step lighter background. */
    Operator,

    /** Clear and backspace: coral glyph, marking the two destructive actions. */
    Destructive,

    /** Equals: the one filled key on the pad. */
    Accent,
}

/**
 * A single circular calculator key.
 *
 * Press feedback deliberately mirrors One UI rather than stock Material: a soft
 * glow blooms *outside* the circle in the key's own content colour, together with
 * a slight scale-down. Material's default ripple is clipped to the shape and is
 * far too subdued to read on a true-black canvas.
 *
 * Both animations are spring/tween driven and interruptible, so rapid presses
 * re-target smoothly instead of queueing.
 *
 * @param label the glyph to render.
 * @param contentDescription spoken label for TalkBack — "divide" rather than the
 *   raw glyph, which a screen reader would otherwise read unhelpfully.
 */
@Composable
fun CalcButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyStyle = KeyStyle.Standard,
) {
    val colors = CambioTheme.colors
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val restingBackground = when (style) {
        KeyStyle.Accent -> colors.accent
        KeyStyle.Operator -> colors.operatorKey
        else -> colors.key
    }
    val pressedBackground = when (style) {
        KeyStyle.Accent -> colors.accentPressed
        KeyStyle.Operator -> colors.operatorKeyPressed
        else -> colors.keyPressed
    }

    val contentColor = when (style) {
        KeyStyle.Standard, KeyStyle.Operator -> colors.onKey
        KeyStyle.Destructive -> colors.destructive
        KeyStyle.Accent -> colors.onAccent
    }

    /** The halo takes the glyph's colour, so the destructive keys bloom coral. */
    val glowColor = when (style) {
        KeyStyle.Accent -> colors.accent
        KeyStyle.Destructive -> colors.destructive
        else -> Color.White
    }

    val background by animateColorAsState(
        targetValue = if (isPressed) pressedBackground else restingBackground,
        animationSpec = tween(durationMillis = PRESS_COLOR_MILLIS),
        label = "keyBackground",
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "keyScale",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            // Guarantees a comfortable touch target even on very small screens,
            // where the grid would otherwise shrink the key below 48dp.
            .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
            .scale(scale)
            .semantics {
                this.contentDescription = contentDescription
                onClick(label = contentDescription, action = null)
            }
            .clickable(
                interactionSource = interactionSource,
                // The glow is drawn by the indication itself, unclipped, so it can
                // spill past the circle the way One UI's does.
                indication = KeyGlow(glowColor),
                role = Role.Button,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Text(
                text = label,
                style = CambioTextStyles.Key,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val PRESSED_SCALE = 0.93f
private const val PRESS_COLOR_MILLIS = 90
private val MIN_TOUCH_TARGET = 48.dp
