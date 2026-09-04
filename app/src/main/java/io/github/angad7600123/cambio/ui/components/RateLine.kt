package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.data.RatesError
import io.github.angad7600123.cambio.ui.RatesStatus
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * The line beneath the converted amount showing the live rate and its age.
 *
 * This is where every rate-related state is surfaced, deliberately in one small,
 * non-blocking strip: loading shimmer on first launch, the rate itself once known,
 * a stale marker when the figures have aged past the provider's update time, and a
 * tappable retry when there is nothing to show at all. The app never puts a dialog
 * or a full-screen spinner in front of the keypad — the calculator stays usable
 * regardless of the network.
 */
@Composable
fun RateLine(
    status: RatesStatus,
    rateText: String?,
    lastUpdatedText: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (status) {
        RatesStatus.Loading -> LoadingRateLine(modifier)

        is RatesStatus.Ready -> ReadyRateLine(
            status = status,
            rateText = rateText,
            lastUpdatedText = lastUpdatedText,
            onRefresh = onRefresh,
            modifier = modifier,
        )

        is RatesStatus.Unavailable -> UnavailableRateLine(
            error = status.error,
            onRetry = onRefresh,
            modifier = modifier,
        )
    }
}

/** A pulsing placeholder bar, used only before any rates have ever been loaded. */
@Composable
private fun LoadingRateLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rateShimmer")
    val alpha by transition.animateFloat(
        initialValue = SHIMMER_MIN_ALPHA,
        targetValue = SHIMMER_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_MILLIS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rateShimmerAlpha",
    )

    val description = stringResource(R.string.rates_loading)

    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .height(PLACEHOLDER_HEIGHT)
            .width(PLACEHOLDER_WIDTH)
            .alpha(alpha)
            .clip(RoundedCornerShape(PLACEHOLDER_RADIUS))
            .background(CambioTheme.colors.key),
    )
}

@Composable
private fun ReadyRateLine(
    status: RatesStatus.Ready,
    rateText: String?,
    lastUpdatedText: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CambioTheme.colors

    val suffix = when {
        status.isRefreshing -> stringResource(R.string.rates_updating)
        status.isStale -> stringResource(R.string.rates_stale)
        lastUpdatedText != null -> lastUpdatedText
        else -> null
    }

    val text = listOfNotNull(rateText, suffix).joinToString(SEPARATOR)
    val description = stringResource(R.string.cd_refresh_rates, text)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PILL_RADIUS))
            .clickable(onClick = onRefresh)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            style = CambioTextStyles.Meta,
            color = if (status.isStale) colors.destructive else colors.textSecondary,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

@Composable
private fun UnavailableRateLine(error: RatesError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CambioTheme.colors
    val message = error.toMessage()
    val description = stringResource(R.string.cd_retry_rates, message)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PILL_RADIUS))
            .background(colors.key)
            .clickable(onClick = onRetry)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = colors.destructive,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = message,
            style = CambioTextStyles.Meta,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.action_retry),
            style = CambioTextStyles.Meta,
            color = colors.accent,
        )
    }
}

/** Maps each failure cause to its own message, so "offline" never reads as "server error". */
@Composable
private fun RatesError.toMessage(): String = when (this) {
    RatesError.Network -> stringResource(R.string.error_network)
    is RatesError.Http -> stringResource(R.string.error_http, code)
    RatesError.Malformed -> stringResource(R.string.error_malformed)
    is RatesError.Provider -> stringResource(R.string.error_provider)
}

private const val SEPARATOR = "  ·  "
private const val SHIMMER_MILLIS = 900
private const val SHIMMER_MIN_ALPHA = 0.35f
private const val SHIMMER_MAX_ALPHA = 0.75f
private val PLACEHOLDER_HEIGHT = 14.dp
private val PLACEHOLDER_WIDTH = 180.dp
private val PLACEHOLDER_RADIUS = 7.dp
private val PILL_RADIUS = 10.dp
private val ICON_SIZE = 14.dp
