package io.github.angad7600123.cambio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.math.BigDecimal

/**
 * Storage for the most recent rate table.
 *
 * An interface rather than a concrete class so the repository's caching policy can
 * be tested against an in-memory fake, with no DataStore and no filesystem.
 */
interface RatesCache {
    /** Emits the cached snapshot, or `null` when nothing valid is stored. */
    val snapshot: Flow<RateSnapshot?>

    suspend fun save(snapshot: RateSnapshot)

    suspend fun clear()
}

/**
 * The real, on-disk implementation.
 *
 * Rates are persisted as decimal *strings* rather than doubles so a value survives
 * the round-trip exactly as the provider sent it.
 *
 * A corrupt or unreadable cache is treated as "no cache" rather than an error: the
 * app falls back to fetching, and a bad write can never brick the app.
 */
class DataStoreRatesCache(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RatesCache {
    override val snapshot: Flow<RateSnapshot?> = dataStore.data
        .catch { cause ->
            // A read failure means we have no usable cache, not that the app is broken.
            if (cause is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw cause
        }
        .map { preferences -> preferences[KEY_SNAPSHOT]?.let(::decode) }

    override suspend fun save(snapshot: RateSnapshot) {
        val encoded = json.encodeToString(CachedSnapshot.serializer(), snapshot.toCached())
        dataStore.edit { preferences -> preferences[KEY_SNAPSHOT] = encoded }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(KEY_SNAPSHOT) }
    }

    private fun decode(raw: String): RateSnapshot? = try {
        json.decodeFromString(CachedSnapshot.serializer(), raw).toDomain()
    } catch (_: Exception) {
        // Corrupt cache: behave exactly as if nothing had been stored.
        null
    }

    @Serializable
    private data class CachedSnapshot(
        @SerialName("base") val base: String,
        @SerialName("rates") val rates: Map<String, String>,
        @SerialName("fetched_at") val fetchedAt: Long,
        @SerialName("next_update") val nextUpdate: Long,
    ) {
        fun toDomain(): RateSnapshot? {
            if (base.isBlank() || rates.isEmpty()) return null
            val parsed = rates.mapNotNull { (code, value) ->
                val decimal = try {
                    BigDecimal(value)
                } catch (_: NumberFormatException) {
                    null
                }
                decimal?.let { code to it }
            }.toMap()

            if (parsed.isEmpty()) return null

            return RateSnapshot(
                base = base,
                rates = parsed,
                fetchedAtEpochSeconds = fetchedAt,
                nextUpdateEpochSeconds = nextUpdate,
            )
        }
    }

    private fun RateSnapshot.toCached(): CachedSnapshot = CachedSnapshot(
        base = base,
        rates = rates.mapValues { (_, value) -> value.toPlainString() },
        fetchedAt = fetchedAtEpochSeconds,
        nextUpdate = nextUpdateEpochSeconds,
    )

    private companion object {
        val KEY_SNAPSHOT = stringPreferencesKey("rates_snapshot")
    }
}
