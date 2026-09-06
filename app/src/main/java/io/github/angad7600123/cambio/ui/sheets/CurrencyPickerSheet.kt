package io.github.angad7600123.cambio.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.currency.CurrencyInfo
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme
import kotlinx.coroutines.launch
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
) {
    val colors = CambioTheme.colors
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }

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

        // The scrubber overlays the list rather than sitting beside it, so its
        // bubble can hang over the rows the way One UI's does.
        Box(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = SCRUBBER_INSET),
            ) {
                if (visibleRecents.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.currency_section_recent)) }
                    itemsIndexed(visibleRecents, key = { _, it -> "recent-" + it.code }) { index, currency ->
                        CurrencyRow(
                            currency = currency,
                            isSelected = currency.code == selectedCode,
                            // No rule under the last row of a section: the section
                            // heading below is separation enough, and a trailing rule
                            // reads as the start of something that never comes.
                            showDivider = index < visibleRecents.lastIndex,
                            onClick = { onSelect(currency.code) },
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item { EmptyState(message = stringResource(R.string.currency_no_results, query)) }
                } else {
                    itemsIndexed(filtered, key = { _, it -> it.code }) { index, currency ->
                        CurrencyRow(
                            currency = currency,
                            isSelected = currency.code == selectedCode,
                            showDivider = index < filtered.lastIndex,
                            onClick = { onSelect(currency.code) },
                        )
                    }
                }
            }

            if (query.isBlank() && letterOffsets.isNotEmpty()) {
                val letters = remember(letterOffsets) { letterOffsets.keys.sorted() }
                // Which section the list is resting on, so the thumb reflects the
                // scroll rather than only the last letter that was dragged to.
                val currentLetter = remember(firstVisible, letters, letterOffsets) {
                    letters.lastOrNull { letterOffsets.getValue(it) <= firstVisible }
                }

                IndexScrubber(
                    letters = letters,
                    currentLetter = currentLetter,
                    onSeek = { letter ->
                        letterOffsets[letter]?.let { index ->
                            scope.launch { listState.scrollToItem(index) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp, top = 8.dp, bottom = 8.dp),
                )
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
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

/**
 * One currency: the name leads, the code sits under it, a tick marks the choice.
 *
 * The selected row is tinted and ticked rather than given a filled background, which
 * keeps a long list calm.
 *
 * The name is allowed two lines. At 16sp the column holds about 29 characters, which
 * covers all but two of the 159 names the platform supplies — but those two used to
 * be cut off mid-word with no ellipsis, so they simply read as a shorter currency
 * that does not exist. Wrapping costs a little height on two rows out of 159 and
 * removes the possibility entirely.
 *
 * The flag has a fixed column rather than its intrinsic width, so every name starts
 * at the same x whatever the emoji measures, and the rule below can be inset to meet
 * them.
 */
@Composable
private fun CurrencyRow(currency: CurrencyInfo, isSelected: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    val colors = CambioTheme.colors
    val description = stringResource(
        if (isSelected) R.string.cd_currency_row_selected else R.string.cd_currency_row,
        currency.displayName,
        currency.code,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(start = 24.dp, end = 8.dp, top = ROW_PADDING, bottom = ROW_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = currency.flag.ifEmpty { PLACEHOLDER_FLAG },
                style = CambioTextStyles.CurrencyCode,
                modifier = Modifier.width(FLAG_COLUMN),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currency.displayName,
                    style = CambioTextStyles.CurrencyCode.copy(fontWeight = FontWeight.Normal),
                    color = if (isSelected) colors.accentText else colors.textPrimary,
                    maxLines = 2,
                    // Belt and braces behind the two lines: a name too long even for
                    // those must say so rather than stopping mid-word.
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currency.code,
                    style = CambioTextStyles.Meta,
                    color = if (isSelected) colors.accentText else colors.textSecondary,
                )
            }

            Box(modifier = Modifier.width(TICK_COLUMN), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(TICK_SIZE),
                    )
                }
            }
        }

        if (showDivider) {
            // Inset to meet the name rather than run the full width, so the flags
            // read as a column of their own — the convention iOS uses for a list
            // whose rows carry a leading element.
            HorizontalDivider(
                color = colors.outline,
                thickness = Dp.Hairline,
                modifier = Modifier.padding(start = DIVIDER_INSET),
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

/** Keeps row content clear of the scrubber track. */
private val SCRUBBER_INSET = 30.dp

/** Fixed so every name starts at the same x, whatever the flag emoji measures. */
private val FLAG_COLUMN = 28.dp
private val TICK_COLUMN = 24.dp
private val TICK_SIZE = 18.dp
private val ROW_PADDING = 14.dp

/** One line of name plus the code, so ordinary rows are all exactly this tall. */
private val ROW_MIN_HEIGHT = 68.dp

/** Where the name begins: the row's own inset, plus the flag column and its gap. */
private val DIVIDER_INSET = 64.dp
