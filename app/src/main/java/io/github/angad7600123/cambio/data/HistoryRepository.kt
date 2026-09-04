package io.github.angad7600123.cambio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * One completed calculation, kept so the user can look back at it or reuse it.
 *
 * The converted amount is stored as text exactly as it was displayed, because the
 * rate that produced it was correct at [timestampEpochSeconds] and would be wrong
 * to recompute later at today's rate.
 */
@Serializable
data class HistoryEntry(
    @SerialName("expression") val expression: String,
    @SerialName("result") val result: String,
    @SerialName("from") val fromCurrency: String,
    @SerialName("to") val toCurrency: String,
    @SerialName("converted") val convertedResult: String?,
    @SerialName("timestamp") val timestampEpochSeconds: Long,
)

/**
 * Persists the calculation history, newest first and capped at [MAX_ENTRIES].
 *
 * As with the rate cache, unreadable stored data degrades to an empty history
 * rather than an error.
 */
class HistoryRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val history: Flow<List<HistoryEntry>> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { preferences -> preferences[KEY_HISTORY]?.let(::decode) ?: emptyList() }

    suspend fun add(entry: HistoryEntry) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_HISTORY]?.let(::decode) ?: emptyList()
            val updated = (listOf(entry) + current).take(MAX_ENTRIES)
            preferences[KEY_HISTORY] = json.encodeToString(ListSerializer, updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(KEY_HISTORY) }
    }

    private fun decode(raw: String): List<HistoryEntry>? = try {
        json.decodeFromString(ListSerializer, raw)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val MAX_ENTRIES = 100

        private val ListSerializer =
            kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer())
        private val KEY_HISTORY = stringPreferencesKey("history")
    }
}
