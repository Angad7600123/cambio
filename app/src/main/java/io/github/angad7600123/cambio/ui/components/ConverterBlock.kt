package io.github.angad7600123.cambio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.currency.ConversionSide
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * The currency conversion block.
 *
 * Two stacked rows separated by a rule, each pairing a figure with its currency
 * code, and a swap control sitting on the rule at the left — the layout iOS uses
 * for unit conversion. The code *is* the picker: tapping it opens the list, which
 * is why there is no separate row of dropdowns above the keypad.
 *
 * This is the one part of the interface that does not follow One UI, because One
 * UI's calculator has nothing equivalent to copy.
 *
 * @param amount the value in the source currency, already formatted.
 * @param converted the value in the target currency, or null while rates load.
 */
@Composable
fun ConverterBlock(
    sourceValue: String,
    targetValue: String?,
    from: CurrencyInfo,
    to: CurrencyInfo,
    activeSide: ConversionSide,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onSelectSide: (ConversionSide) -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CambioTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwapButton(onClick = onSwapClick)

        Column(modifier = Modifier.weight(1f)) {
            ConverterRow(
                value = sourceValue,
                currency = from,
                isActive = activeSide == ConversionSide.SOURCE,
                onActivate = { onSelectSide(ConversionSide.SOURCE) },
                onPickCurrency = onFromClick,
            )

            HorizontalDivider(
                color = colors.outline,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            ConverterRow(
                value = targetValue ?: PLACEHOLDER,
                currency = to,
                isActive = activeSide == ConversionSide.TARGET,
                onActivate = { onSelectSide(ConversionSide.TARGET) },
                onPickCurrency = onToClick,
            )
        }
    }
}

/**
 * One side of the conversion: the figure, then its currency code as a control.
 *
 * Two separate targets share the row. Tapping the figure moves the caret to this
 * currency; tapping the code opens the picker. The active figure is drawn in the
 * foreground colour and the idle one recedes to grey — the only cue needed to show
 * where the next digit will land, with no border or fill to clutter the display.
 */
@Composable
private fun ConverterRow(
    value: String,
    currency: CurrencyInfo,
    isActive: Boolean,
    onActivate: () -> Unit,
    onPickCurrency: () -> Unit,
) {
    val colors = CambioTheme.colors

    val valueColor by animateColorAsState(
        targetValue = if (isActive) colors.textPrimary else colors.textSecondary,
        animationSpec = tween(durationMillis = ACTIVATE_MILLIS),
        label = "converterValueColor",
    )

    // The active figure is where digits land when no operator has been typed, so it
    // carries the same pop-in the expression line uses. The idle side changes only
    // because the other side was edited, so it should not pop.
    val entryScale = rememberEntryScale(value)
    val styled = buildDisplayText(
        text = value,
        operatorColor = colors.accentText,
        caretColor = Color.Transparent,
        showCaret = false,
        lastCharScale = if (isActive) entryScale else 1f,
        baseFontSize = CambioTextStyles.Converted.fontSize,
    )

    val activateDescription = stringResource(
        if (isActive) R.string.cd_side_active else R.string.cd_side_inactive,
        currency.displayName,
    )
    val pickDescription = stringResource(
        R.string.cd_pick_currency,
        currency.displayName,
        currency.code,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = styled,
            style = CambioTextStyles.Converted,
            color = valueColor,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(ROW_RADIUS))
                .clickable(onClick = onActivate)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                .semantics { contentDescription = activateDescription },
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(ROW_RADIUS))
                .clickable(onClick = onPickCurrency)
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics { contentDescription = pickDescription },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = currency.code,
                style = CambioTextStyles.CurrencyCode,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Icon(
                imageVector = Icons.Rounded.UnfoldMore,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier
                    .padding(start = 2.dp, bottom = 2.dp)
                    .size(CHEVRON_SIZE),
            )
        }
    }
}

/**
 * The swap control, sitting on the rule between the two rows.
 *
 * Rotates a half-turn on each press so the reversal is felt, not merely inferred
 * from the two figures trading places.
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

    Box(
        modifier = Modifier
            // A full 48dp target even though the glyph is small.
            .size(TOUCH_TARGET)
            .clip(CircleShape)
            .clickable {
                turns++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.SwapVert,
            contentDescription = stringResource(R.string.cd_swap_currencies),
            tint = colors.accentText,
            modifier = Modifier
                .size(SWAP_ICON_SIZE)
                .rotate(rotation),
        )
    }
}

/** Shown in place of the converted figure until rates are available. */
private const val PLACEHOLDER = "—"

private val ROW_RADIUS = 10.dp
private val CHEVRON_SIZE = 18.dp
private val SWAP_ICON_SIZE = 24.dp
private val TOUCH_TARGET = 48.dp
private const val HALF_TURN_DEGREES = 180f
private const val SWAP_MILLIS = 320
private const val ACTIVATE_MILLIS = 180
