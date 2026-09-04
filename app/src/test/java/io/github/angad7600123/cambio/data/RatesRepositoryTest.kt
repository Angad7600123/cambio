package io.github.angad7600123.cambio.data

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the caching and refresh policy — in particular the stale-while-error
 * rule, which is the behaviour that keeps the app useful when the network drops.
 *
 * The clock is injected, so cache expiry is asserted exactly rather than by
 * waiting.
 */
class RatesRepositoryTest {

    private lateinit var server: MockWebServer

    /** An in-memory stand-in for the DataStore-backed cache. */
    private class FakeCache : RatesCache {
        private val state = MutableStateFlow<RateSnapshot?>(null)
        override val snapshot: Flow<RateSnapshot?> = state
        var saveCount = 0
            private set

        override suspend fun save(snapshot: RateSnapshot) {
            saveCount++
            state.value = snapshot
        }

        override suspend fun clear() {
            state.value = null
        }

        fun seed(snapshot: RateSnapshot) {
            state.value = snapshot
        }

        fun current(): RateSnapshot? = state.value
    }

    private lateinit var cache: FakeCache

    private fun clockAt(seconds: Long) = AppClock { seconds }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cache = FakeCache()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun body(lastUpdate: Long, nextUpdate: Long) = """
        {
          "result": "success",
          "time_last_update_unix": $lastUpdate,
          "time_next_update_unix": $nextUpdate,
          "base_code": "USD",
          "rates": { "USD": 1, "EUR": 0.86, "INR": 94.54 }
        }
    """.trimIndent()

    private fun repository(now: Long): RatesRepository = RatesRepository(
        remote = RatesRemoteDataSource(baseUrl = server.url("/v6/latest/")),
        cache = cache,
        clock = clockAt(now),
    )

    private fun snapshot(lastUpdate: Long, nextUpdate: Long) = RateSnapshot(
        base = "USD",
        rates = mapOf("USD" to BigDecimal.ONE, "EUR" to BigDecimal("0.86")),
        fetchedAtEpochSeconds = lastUpdate,
        nextUpdateEpochSeconds = nextUpdate,
    )

    @Test
    fun `a successful refresh caches the snapshot`() = runTest {
        server.enqueue(MockResponse().setBody(body(1000, 2000)))

        repository(now = 500).refresh()

        val cached = assertNotNull(cache.current())
        assertEquals(1000L, cached.fetchedAtEpochSeconds)
        assertEquals(2000L, cached.nextUpdateEpochSeconds)
    }

    @Test
    fun `refreshIfStale fetches when nothing is cached`() = runTest {
        server.enqueue(MockResponse().setBody(body(1000, 2000)))

        repository(now = 500).refreshIfStale()

        assertEquals(1, cache.saveCount)
    }

    @Test
    fun `refreshIfStale skips the network while the cache is fresh`() = runTest {
        cache.seed(snapshot(lastUpdate = 1000, nextUpdate = 2000))

        // Now is before the provider's stated next update, so no request should go out.
        repository(now = 1500).refreshIfStale()

        assertEquals(0, server.requestCount)
        assertEquals(0, cache.saveCount)
    }

    @Test
    fun `refreshIfStale fetches once the provider's update time has passed`() = runTest {
        cache.seed(snapshot(lastUpdate = 1000, nextUpdate = 2000))
        server.enqueue(MockResponse().setBody(body(2000, 3000)))

        repository(now = 2001).refreshIfStale()

        assertEquals(1, server.requestCount)
        assertEquals(2000L, assertNotNull(cache.current()).fetchedAtEpochSeconds)
    }

    @Test
    fun `expiry uses the provider's time, not a locally invented TTL`() {
        val snap = snapshot(lastUpdate = 1000, nextUpdate = 5000)
        assertTrue(!snap.isStaleAt(4999))
        assertTrue(snap.isStaleAt(5000))
        assertTrue(snap.isStaleAt(5001))
    }

    @Test
    fun `a failed refresh keeps the existing cache intact`() = runTest {
        val existing = snapshot(lastUpdate = 1000, nextUpdate = 2000)
        cache.seed(existing)
        server.enqueue(MockResponse().setResponseCode(500))

        repository(now = 3000).refresh()

        // Stale-while-error: the good data must survive a failed update.
        assertEquals(existing, cache.current())
        assertEquals(0, cache.saveCount)
    }

    @Test
    fun `a failed refresh reports the error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val repo = repository(now = 100)

        repo.refresh()

        val failed = assertIs<RefreshState.Failed>(repo.refreshState.value)
        assertEquals(RatesError.Http(503), failed.error)
    }

    @Test
    fun `a successful refresh clears a previous failure`() = runTest {
        val repo = repository(now = 100)
        server.enqueue(MockResponse().setResponseCode(500))
        repo.refresh()
        assertIs<RefreshState.Failed>(repo.refreshState.value)

        server.enqueue(MockResponse().setBody(body(1000, 2000)))
        repo.refresh()

        assertEquals(RefreshState.Idle, repo.refreshState.value)
    }

    @Test
    fun `the snapshot flow emits the cached value`() = runTest {
        val repo = repository(now = 100)

        repo.snapshot.test {
            assertEquals(null, awaitItem())
            cache.seed(snapshot(lastUpdate = 1000, nextUpdate = 2000))
            assertEquals(1000L, assertNotNull(awaitItem()).fetchedAtEpochSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isStale reflects the injected clock`() {
        val snap = snapshot(lastUpdate = 1000, nextUpdate = 2000)
        assertTrue(!repository(now = 1999).isStale(snap))
        assertTrue(repository(now = 2000).isStale(snap))
    }

    @Test
    fun `a corrupt response leaves the cache untouched`() = runTest {
        val existing = snapshot(lastUpdate = 1000, nextUpdate = 2000)
        cache.seed(existing)
        server.enqueue(MockResponse().setBody("not json at all"))

        repository(now = 5000).refresh()

        assertEquals(existing, cache.current())
    }
}
