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
import androidx.compose.material3.ripple
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
 * Hierarchy is carried by glyph colour and a single filled key, exactly as in the
 * reference design — not by colouring whole rows, which would flatten the meaning
 * of the accent.
 */
enum class KeyStyle {
    /** Digits, operators, decimal point, parentheses: white glyph on a graphite key. */
    Standard,

    /** Clear and backspace: coral glyph, marking the two destructive actions. */
    Destructive,

    /** Equals: the one filled key on the pad. */
    Accent,
}

/**
 * A single circular calculator key.
 *
 * Press feedback is deliberate: a subtle scale-down plus a background lift, both
 * spring-driven so rapid presses interrupt and re-target smoothly rather than
 * queueing up. A physical keyboard-tap haptic fires on press, respecting the
 * user's system haptics setting.
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

    val targetBackground = when (style) {
        KeyStyle.Accent -> if (isPressed) colors.accentPressed else colors.accent
        else -> if (isPressed) colors.keyPressed else colors.key
    }

    val contentColor = when (style) {
        KeyStyle.Standard -> colors.onKey
        KeyStyle.Destructive -> colors.destructive
        KeyStyle.Accent -> colors.onAccent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
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
            .clip(CircleShape)
            .background(background)
            .semantics {
                this.contentDescription = contentDescription
                onClick(label = contentDescription, action = null)
            }
            .keyClickable(
                interactionSource = interactionSource,
                contentColor = contentColor,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
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

/** Clickable with a ripple tinted to the key's own content colour. */
@Composable
private fun Modifier.keyClickable(
    interactionSource: MutableInteractionSource,
    contentColor: Color,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = ripple(bounded = true, color = contentColor),
    role = Role.Button,
    onClick = onClick,
)

private const val PRESSED_SCALE = 0.93f
private const val PRESS_COLOR_MILLIS = 90
private val MIN_TOUCH_TARGET = 48.dp
