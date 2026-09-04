package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * The source and destination currency selectors with a swap control between them.
 *
 * This is what keeps the app a calculator rather than a conversion form: two
 * compact chips sitting inside the display area, not a pair of dropdowns above a
 * Convert button.
 */
@Composable
fun CurrencyBar(
    from: CurrencyInfo,
    to: CurrencyInfo,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CurrencyChip(
            currency = from,
            onClick = onFromClick,
            descriptionRes = R.string.cd_select_from_currency,
        )
        SwapButton(onClick = onSwapClick)
        CurrencyChip(
            currency = to,
            onClick = onToClick,
            descriptionRes = R.string.cd_select_to_currency,
        )
    }
}

@Composable
private fun CurrencyChip(currency: CurrencyInfo, onClick: () -> Unit, descriptionRes: Int) {
    val colors = CambioTheme.colors
    val description = stringResource(descriptionRes, currency.displayName, currency.code)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CHIP_RADIUS))
            .background(colors.key)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (currency.flag.isNotEmpty()) {
            Text(text = currency.flag, style = CambioTextStyles.CurrencyCode)
        }
        Text(
            text = currency.code,
            style = CambioTextStyles.CurrencyCode,
            color = colors.textPrimary,
        )
    }
}

/**
 * The swap control.
 *
 * Rotates a half-turn on each press, so the direction change is felt rather than
 * merely inferred from the chips exchanging places.
 */
@Composable
private fun SwapButton(onClick: () -> Unit) {
    val colors = CambioTheme.colors
    var turns by remember { mutableIntStateOf(0) }

    val rotation by animateFloatAsState(
        targetValue = turns * HALF_TURN_DEGREES,
        animationSpec = tween(durationMillis = SWAP_MILLIS),
        label = "swapRotation",
    )

    val description = stringResource(R.string.cd_swap_currencies)

    Box(
        modifier = Modifier
            // A full 48dp target, even though the glyph itself is small.
            .size(SWAP_TOUCH_TARGET)
            .clip(CircleShape)
            .clickable {
                turns++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.SwapHoriz,
            contentDescription = description,
            tint = colors.textSecondary,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation),
        )
    }
}

private val CHIP_RADIUS = 14.dp
private const val HALF_TURN_DEGREES = 180f
private const val SWAP_MILLIS = 320
private val SWAP_TOUCH_TARGET = 48.dp
