package io.github.angad7600123.cambio.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.currency.ConversionEngine
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.format.NumberDisplayFormatter
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale

/**
 * The currency picker.
 *
 * Modelled on the iOS unit picker rather than One UI, which has no equivalent: the
 * full name leads with the code beneath it, the current choice is tinted and
 * ticked, and an A–Z rail down the right edge jumps the list — which matters with
 * 160-plus entries.
 *
 * Search matches code *or* name, so "rupee", "INR" and "india" all find the same
 * row, and recently used currencies stay pinned at the top.
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // What one unit of the source currency is worth in each row's currency.
    // Computed once per sheet rather than on every recomposition.
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

    // Sorted by name, because that is what the A-Z rail indexes.
    val sorted = remember(currencies) { currencies.sortedBy { it.displayName.uppercase(Locale.ROOT) } }
    val filtered = remember(sorted, query) { sorted.filterByQuery(query) }
    val visibleRecents = remember(recents, query, currencies) {
        if (query.isBlank()) {
            recents.filter { recent -> currencies.any { it.code == recent.code } }
        } else {
            emptyList()
        }
    }

    // Index of the first row for each initial, so the rail can scroll to it.
    val letterOffsets = remember(filtered, visibleRecents) {
        val header = if (visibleRecents.isEmpty()) 0 else visibleRecents.size + 1
        buildMap {
            filtered.forEachIndexed { index, currency ->
                val letter = currency.displayName.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
                putIfAbsent(letter, header + index)
            }
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
            leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
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

        Row(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
            ) {
                if (visibleRecents.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.currency_section_recent)) }
                    items(visibleRecents, key = { "recent-" + it.code }) { currency ->
                        CurrencyRow(
                            currency = currency,
                            isSelected = currency.code == selectedCode,
                            ratePreview = ratePreviews[currency.code],
                            onClick = { onSelect(currency.code) },
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item { EmptyState(message = stringResource(R.string.currency_no_results, query)) }
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

            if (query.isBlank() && letterOffsets.isNotEmpty()) {
                AlphabetRail(
                    letters = letterOffsets.keys.sorted(),
                    onLetter = { letter ->
                        letterOffsets[letter]?.let { index ->
                            scope.launch { listState.scrollToItem(index) }
                        }
                    },
                )
            }
        }
    }
}

/**
 * The A–Z rail down the right edge.
 *
 * With 160-plus currencies, scrolling to "Swiss Franc" by flinging is tedious; the
 * rail turns it into one tap.
 */
@Composable
private fun AlphabetRail(letters: List<Char>, onLetter: (Char) -> Unit) {
    val colors = CambioTheme.colors
    val description = stringResource(R.string.cd_alphabet_index)

    Column(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .padding(end = 6.dp)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                style = CambioTextStyles.Meta.copy(fontSize = RAIL_TEXT_SIZE, fontWeight = FontWeight.SemiBold),
                color = colors.accentText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLetter(letter) }
                    .padding(vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = CambioTextStyles.Meta.copy(fontWeight = FontWeight.SemiBold),
        color = CambioTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

/**
 * One currency: the name leads, the code sits under it, the rate sits on the right.
 *
 * The selected row is tinted and ticked rather than highlighted with a background,
 * which keeps a long list calm.
 */
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
            .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = currency.flag.ifEmpty { PLACEHOLDER_FLAG }, style = CambioTextStyles.CurrencyCode)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currency.displayName,
                style = CambioTextStyles.CurrencyCode.copy(fontWeight = FontWeight.Normal),
                color = if (isSelected) colors.accentText else colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = currency.code,
                style = CambioTextStyles.Meta,
                color = if (isSelected) colors.accentText else colors.textSecondary,
            )
        }

        if (ratePreview != null) {
            Text(text = ratePreview, style = CambioTextStyles.Meta, color = colors.textSecondary, maxLines = 1)
        }

        Box(modifier = Modifier.width(TICK_COLUMN)) {
            if (isSelected) {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = colors.accentText)
            }
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
private val RAIL_WIDTH = 22.dp
private val TICK_COLUMN = 28.dp
private val RAIL_TEXT_SIZE = 10.sp
