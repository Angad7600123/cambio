package io.github.angad7600123.cambio.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the app is currently doing about rates, for the benefit of the UI. */
sealed interface RefreshState {
    data object Idle : RefreshState

    data object Refreshing : RefreshState

    /**
     * The last refresh failed. If a cached snapshot exists it is still shown, with
     * a stale marker; this only reports why it could not be updated.
     */
    data class Failed(val error: RatesError) : RefreshState
}

/**
 * The app's single source of truth for exchange rates.
 *
 * Policy, in one place:
 * - Rates are cached on disk and served immediately on launch, so the app is
 *   useful before the network answers.
 * - Expiry is whatever the provider says it is ([RateSnapshot.nextUpdateEpochSeconds]),
 *   not a TTL invented here.
 * - **Stale-while-error**: a failed refresh never clears good cached data. The user
 *   keeps seeing the last known rates, marked as stale, instead of an empty screen.
 * - Concurrent refreshes collapse into one in-flight request.
 */
class RatesRepository(
    private val remote: RatesRemoteDataSource,
    private val cache: RatesCache,
    private val clock: AppClock = AppClock.System,
) {
    /** The cached rate table, or `null` before the first successful fetch. */
    val snapshot: Flow<RateSnapshot?> = cache.snapshot

    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

    private val refreshMutex = Mutex()

    /**
     * Refreshes only when there is no cached table or the provider's stated update
     * time has passed. Safe to call on every launch and every resume.
     */
    suspend fun refreshIfStale() {
        val cached = snapshot.first()
        if (cached != null && !cached.isStaleAt(clock.nowEpochSeconds())) return
        refresh()
    }

    /**
     * Fetches unconditionally, for an explicit user-initiated retry.
     *
     * On failure the existing cache is deliberately left untouched.
     */
    suspend fun refresh() {
        // If a refresh is already running, wait for it rather than starting a second.
        if (refreshMutex.isLocked) {
            refreshMutex.withLock { }
            return
        }

        refreshMutex.withLock {
            _refreshState.value = RefreshState.Refreshing

            when (val outcome = remote.fetchRates(BASE_CURRENCY)) {
                is FetchOutcome.Success -> {
                    cache.save(outcome.snapshot)
                    _refreshState.value = RefreshState.Idle
                }

                is FetchOutcome.Failure -> {
                    _refreshState.value = RefreshState.Failed(outcome.error)
                }
            }
        }
    }

    /** True when the given snapshot has passed the provider's stated update time. */
    fun isStale(snapshot: RateSnapshot): Boolean = snapshot.isStaleAt(clock.nowEpochSeconds())

    companion object {
        /**
         * All rates are fetched against USD and cross-converted locally, so a single
         * request covers every currency pair the app offers.
         */
        const val BASE_CURRENCY = "USD"
    }
}
