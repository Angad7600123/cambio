package io.github.angad7600123.cambio.ui.sheets

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.data.ThemeMode
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import io.github.angad7600123.cambio.ui.theme.CambioTheme

/**
 * Settings, plus the provider attribution.
 *
 * The attribution link is not decorative: ExchangeRate-API's free open endpoint
 * requires visible credit, so this row is part of complying with their terms.
 */
@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    useSystemColors: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUseSystemColorsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CambioTheme.colors
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = CambioTextStyles.Converted.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        SectionLabel(stringResource(R.string.settings_appearance))

        Column(modifier = Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                ThemeRow(
                    mode = mode,
                    isSelected = mode == themeMode,
                    onSelect = { onThemeModeChange(mode) },
                )
            }
        }

        // Material You only exists on Android 12+, so hiding the toggle below that
        // is more honest than showing a control that would do nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                title = stringResource(R.string.settings_system_colors),
                subtitle = stringResource(R.string.settings_system_colors_subtitle),
                checked = useSystemColors,
                onCheckedChange = onUseSystemColorsChange,
            )
        }

        HorizontalDivider(
            color = colors.outline,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        SectionLabel(stringResource(R.string.settings_about))

        LinkRow(
            title = stringResource(R.string.attribution_title),
            subtitle = stringResource(R.string.attribution_subtitle),
            onClick = { context.openUrl(RATES_PROVIDER_URL) },
        )

        Text(
            text = stringResource(R.string.settings_privacy_note),
            style = CambioTextStyles.Meta,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = CambioTextStyles.Meta.copy(fontWeight = FontWeight.SemiBold),
        color = CambioTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun ThemeRow(mode: ThemeMode, isSelected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Text(
            text = stringResource(mode.labelRes()),
            style = CambioTextStyles.CurrencyCode.copy(fontWeight = FontWeight.Normal),
            color = CambioTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = CambioTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CambioTextStyles.CurrencyCode.copy(fontWeight = FontWeight.Normal),
                color = colors.textPrimary,
            )
            Text(text = subtitle, style = CambioTextStyles.Meta, color = colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = CambioTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = CambioTextStyles.CurrencyCode.copy(fontWeight = FontWeight.Normal),
            color = colors.textPrimary,
        )
        Text(text = subtitle, style = CambioTextStyles.Meta, color = colors.accent)
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** Opens a URL, ignoring the case where the device has no browser at all. */
private fun android.content.Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser installed; failing silently is better than crashing.
    }
}

/** Attribution target required by the rate provider's terms of use. */
const val RATES_PROVIDER_URL = "https://www.exchangerate-api.com"
