package io.github.angad7600123.cambio.data

import java.math.BigDecimal

/**
 * One complete exchange-rate table, as fetched from the provider.
 *
 * Rates are quoted against [base]. Every other pair is derived from this single
 * table by [io.github.angad7600123.cambio.currency.ConversionEngine], so the app
 * needs exactly one network request regardless of which currencies are selected.
 *
 * @property fetchedAtEpochSeconds when the provider last recalculated these rates
 *   (their `time_last_update_unix`), not when we downloaded them.
 * @property nextUpdateEpochSeconds when the provider says the next recalculation is
 *   due (their `time_next_update_unix`). This is the authoritative cache expiry —
 *   the app does not invent a TTL of its own.
 */
data class RateSnapshot(
    val base: String,
    val rates: Map<String, BigDecimal>,
    val fetchedAtEpochSeconds: Long,
    val nextUpdateEpochSeconds: Long,
) {
    /** The currency codes this snapshot can convert between. */
    val currencyCodes: Set<String> get() = rates.keys

    /** True once the provider's own stated update time has passed. */
    fun isStaleAt(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= nextUpdateEpochSeconds
}

/** Why a rate refresh failed. Each case maps to a distinct message in the UI. */
sealed interface RatesError {
    /** No connectivity, DNS failure, timeout — anything below HTTP. */
    data object Network : RatesError

    /** The server answered, but not with success. */
    data class Http(val code: Int) : RatesError

    /** The response was not valid JSON, or was missing required fields. */
    data object Malformed : RatesError

    /** The provider explicitly reported an error in its response body. */
    data class Provider(val detail: String) : RatesError
}

/** The outcome of a fetch. Modelled explicitly so failures cannot be ignored. */
sealed interface FetchOutcome {
    data class Success(val snapshot: RateSnapshot) : FetchOutcome

    data class Failure(val error: RatesError) : FetchOutcome
}
