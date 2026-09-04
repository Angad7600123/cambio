package io.github.angad7600123.cambio.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.data.HistoryEntry
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * Past calculations, newest first.
 *
 * Each row shows what was calculated, the result, and what that result was worth
 * in the target currency *at the time* — recomputing it at today's rate would
 * quietly rewrite history, so the stored figure is displayed as-is.
 *
 * Tapping a row restores the expression to the display.
 */
@Composable
fun HistoryContent(
    history: List<HistoryEntry>,
    onRestore: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CambioTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = CambioTextStyles.Converted.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(
                        text = stringResource(R.string.action_clear_history),
                        color = colors.destructive,
                    )
                }
            }
        }

        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = CambioTextStyles.Meta,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 40.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
            items(history, key = { it.timestampEpochSeconds.toString() + it.expression }) { entry ->
                HistoryRow(entry = entry, onClick = { onRestore(entry) })
                HorizontalDivider(
                    color = colors.outline,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val colors = CambioTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = entry.expression,
            style = CambioTextStyles.Meta,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Text(
            text = entry.result,
            style = CambioTextStyles.CurrencyCode,
            color = colors.textPrimary,
            maxLines = 1,
        )
        if (entry.convertedResult != null) {
            Text(
                text = "${entry.convertedResult} ${entry.toCurrency}",
                style = CambioTextStyles.Meta,
                color = colors.accent,
                maxLines = 1,
            )
        }
    }
}

private val LIST_MAX_HEIGHT = 460.dp
