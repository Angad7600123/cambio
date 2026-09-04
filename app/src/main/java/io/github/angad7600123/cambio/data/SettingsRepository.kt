package io.github.angad7600123.cambio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Currency
import java.util.Locale

/** How the app picks between light and dark. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Everything the user can configure, as one immutable snapshot. */
data class UserSettings(
    val fromCurrency: String,
    val toCurrency: String,
    val recentCurrencies: List<String>,
    val themeMode: ThemeMode,
    val useSystemColors: Boolean,
) {
    companion object {
        /** Applied on first launch, before the user has chosen anything. */
        fun defaults(locale: Locale = Locale.getDefault()): UserSettings {
            val local = localCurrencyCode(locale)
            return UserSettings(
                fromCurrency = local,
                // Pairing a currency with itself would be useless, so fall back to EUR
                // for users whose local currency is already the default counterpart.
                toCurrency = if (local == FALLBACK_COUNTERPART) SECOND_COUNTERPART else FALLBACK_COUNTERPART,
                recentCurrencies = emptyList(),
                themeMode = ThemeMode.SYSTEM,
                useSystemColors = false,
            )
        }

        private const val FALLBACK_COUNTERPART = "USD"
        private const val SECOND_COUNTERPART = "EUR"

        /** The device locale's currency, falling back to USD if it has none. */
        private fun localCurrencyCode(locale: Locale): String =
            runCatching { Currency.getInstance(locale).currencyCode }
                .getOrNull()
                ?: FALLBACK_COUNTERPART
    }
}

/**
 * Persists user preferences.
 *
 * Reads are resilient: an I/O failure surfaces as defaults rather than an
 * exception, so a damaged preferences file degrades to a first-run experience
 * instead of a crash loop.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val defaults: UserSettings = UserSettings.defaults(),
) {
    val settings: Flow<UserSettings> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { preferences ->
            UserSettings(
                fromCurrency = preferences[KEY_FROM] ?: defaults.fromCurrency,
                toCurrency = preferences[KEY_TO] ?: defaults.toCurrency,
                recentCurrencies = preferences[KEY_RECENTS]
                    ?.split(SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    ?: defaults.recentCurrencies,
                themeMode = ThemeMode.fromName(preferences[KEY_THEME]),
                useSystemColors = preferences[KEY_SYSTEM_COLORS] ?: defaults.useSystemColors,
            )
        }

    suspend fun setFromCurrency(code: String) {
        dataStore.edit { preferences ->
            preferences[KEY_FROM] = code
            preferences[KEY_RECENTS] = pushRecent(preferences[KEY_RECENTS], code)
        }
    }

    suspend fun setToCurrency(code: String) {
        dataStore.edit { preferences ->
            preferences[KEY_TO] = code
            preferences[KEY_RECENTS] = pushRecent(preferences[KEY_RECENTS], code)
        }
    }

    suspend fun swapCurrencies() {
        dataStore.edit { preferences ->
            val from = preferences[KEY_FROM] ?: defaults.fromCurrency
            val to = preferences[KEY_TO] ?: defaults.toCurrency
            preferences[KEY_FROM] = to
            preferences[KEY_TO] = from
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME] = mode.name }
    }

    suspend fun setUseSystemColors(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_SYSTEM_COLORS] = enabled }
    }

    /** Moves [code] to the front of the recents list, keeping it capped and unique. */
    private fun pushRecent(current: String?, code: String): String {
        val existing = current?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
        return (listOf(code) + existing.filterNot { it == code })
            .take(MAX_RECENTS)
            .joinToString(SEPARATOR)
    }

    private companion object {
        const val SEPARATOR = ","
        const val MAX_RECENTS = 8

        val KEY_FROM = stringPreferencesKey("from_currency")
        val KEY_TO = stringPreferencesKey("to_currency")
        val KEY_RECENTS = stringPreferencesKey("recent_currencies")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SYSTEM_COLORS = booleanPreferencesKey("use_system_colors")
    }
}
