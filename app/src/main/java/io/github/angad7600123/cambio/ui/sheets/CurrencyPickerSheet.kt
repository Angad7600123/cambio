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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.currency.ConversionEngine
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.format.NumberDisplayFormatter
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme
import java.math.BigDecimal
import java.util.Locale

/**
 * The currency picker.
 *
 * Searchable by both code and name, so "rupee", "INR" and "india" all find the
 * same row. Recently used currencies float to the top, because in practice people
 * cycle between a small handful.
 */
@Composable
fun CurrencyPickerContent(
    currencies: List<CurrencyInfo>,
    recents: List<CurrencyInfo>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    rates: Map<String, BigDecimal> = emptyMap(),
    referenceCode: String = selectedCode,
) {
    val colors = CambioTheme.colors
    var query by remember { mutableStateOf("") }
    val numberFormatter = remember { NumberDisplayFormatter() }

    // What one unit of the currently selected source currency is worth in each row's
    // currency. Computed once per sheet rather than on every recomposition.
    val ratePreviews = remember(rates, referenceCode, currencies) {
        if (rates.isEmpty()) {
            emptyMap()
        } else {
            currencies.associate { currency ->
                currency.code to ConversionEngine.rate(referenceCode, currency.code, rates)
                    ?.let(numberFormatter::formatRate)
            }
        }
    }

    val filtered = remember(currencies, query) { currencies.filterByQuery(query) }
    val visibleRecents = remember(recents, query, currencies) {
        if (query.isBlank()) {
            // Only offer recents the current rate table can actually convert.
            recents.filter { recent -> currencies.any { it.code == recent.code } }
        } else {
            emptyList()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.currency_picker_title),
            style = CambioTextStyles.Converted.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
            },
            placeholder = { Text(stringResource(R.string.currency_search_hint)) },
            shape = RoundedCornerShape(SEARCH_RADIUS),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        if (currencies.isEmpty()) {
            // Reachable when the first fetch has not succeeded and there is no cache.
            EmptyState(message = stringResource(R.string.currency_picker_unavailable))
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LIST_MAX_HEIGHT)
                .padding(top = 8.dp),
        ) {
            if (visibleRecents.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.currency_section_recent))
                }
                items(visibleRecents, key = { "recent-" + it.code }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        isSelected = currency.code == selectedCode,
                        ratePreview = ratePreviews[currency.code],
                        onClick = { onSelect(currency.code) },
                    )
                }
                item {
                    HorizontalDivider(
                        color = colors.outline,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                item {
                    SectionHeader(stringResource(R.string.currency_section_all))
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(message = stringResource(R.string.currency_no_results, query))
                }
            } else {
                items(filtered, key = { it.code }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        isSelected = currency.code == selectedCode,
                        ratePreview = ratePreviews[currency.code],
                        onClick = { onSelect(currency.code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = CambioTextStyles.Meta.copy(fontWeight = FontWeight.SemiBold),
        color = CambioTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun CurrencyRow(currency: CurrencyInfo, isSelected: Boolean, ratePreview: String?, onClick: () -> Unit) {
    val colors = CambioTheme.colors
    val description = stringResource(
        if (isSelected) R.string.cd_currency_row_selected else R.string.cd_currency_row,
        currency.displayName,
        currency.code,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = currency.flag.ifEmpty { PLACEHOLDER_FLAG },
            style = CambioTextStyles.Converted,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currency.code,
                style = CambioTextStyles.CurrencyCode,
                color = colors.textPrimary,
            )
            Text(
                text = currency.displayName,
                style = CambioTextStyles.Meta,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        if (ratePreview != null) {
            Text(
                text = ratePreview,
                style = CambioTextStyles.Meta,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(
        text = message,
        style = CambioTextStyles.Meta,
        color = CambioTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    )
}

/** Matches a query against both the code and the localised name. */
private fun List<CurrencyInfo>.filterByQuery(query: String): List<CurrencyInfo> {
    if (query.isBlank()) return this
    val needle = query.trim().lowercase(Locale.getDefault())
    return filter { currency ->
        currency.code.lowercase(Locale.getDefault()).contains(needle) ||
            currency.displayName.lowercase(Locale.getDefault()).contains(needle)
    }
}

private const val PLACEHOLDER_FLAG = "🏳"
private val SEARCH_RADIUS = 14.dp
private val LIST_MAX_HEIGHT = 460.dp
