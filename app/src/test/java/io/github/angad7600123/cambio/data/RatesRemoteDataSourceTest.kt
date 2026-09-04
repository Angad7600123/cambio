package io.github.angad7600123.cambio.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the network layer against a local [MockWebServer].
 *
 * No test here reaches the real provider: every response is scripted, including
 * the malformed and failing ones, so the suite is fast, offline and deterministic.
 */
class RatesRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: RatesRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dataSource = RatesRemoteDataSource(baseUrl = server.url("/v6/latest/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private val validBody = """
        {
          "result": "success",
          "provider": "https://www.exchangerate-api.com",
          "time_last_update_unix": 1788480151,
          "time_next_update_unix": 1788568301,
          "base_code": "USD",
          "rates": { "USD": 1, "EUR": 0.860629, "INR": 94.540498, "JPY": 156.019704 }
        }
    """.trimIndent()

    @Test
    fun `parses a successful response`() = runTest {
        enqueueJson(validBody)

        val outcome = dataSource.fetchRates("USD")

        val success = assertIs<FetchOutcome.Success>(outcome)
        assertEquals("USD", success.snapshot.base)
        assertEquals(1788480151L, success.snapshot.fetchedAtEpochSeconds)
        assertEquals(1788568301L, success.snapshot.nextUpdateEpochSeconds)
        assertEquals(4, success.snapshot.rates.size)
    }

    @Test
    fun `preserves rate precision exactly`() = runTest {
        enqueueJson(validBody)

        val success = assertIs<FetchOutcome.Success>(dataSource.fetchRates("USD"))

        // Going via Double would perturb these digits; BigDecimal must not.
        assertEquals(0, BigDecimal("0.860629").compareTo(success.snapshot.rates.getValue("EUR")))
        assertEquals(0, BigDecimal("94.540498").compareTo(success.snapshot.rates.getValue("INR")))
    }

    @Test
    fun `requests the base currency in the path`() = runTest {
        enqueueJson(validBody)

        dataSource.fetchRates("USD")

        assertTrue(server.takeRequest().path!!.endsWith("/v6/latest/USD"))
    }

    @Test
    fun `ignores unknown fields so the provider can add them safely`() = runTest {
        enqueueJson(
            """
            {
              "result": "success",
              "brand_new_field": { "nested": true },
              "time_last_update_unix": 100,
              "time_next_update_unix": 200,
              "base_code": "USD",
              "rates": { "EUR": 0.9 }
            }
            """.trimIndent(),
        )

        assertIs<FetchOutcome.Success>(dataSource.fetchRates("USD"))
    }

    @Test
    fun `falls back to a day when the provider omits its next update time`() = runTest {
        enqueueJson(
            """
            {
              "result": "success",
              "time_last_update_unix": 1000,
              "base_code": "USD",
              "rates": { "EUR": 0.9 }
            }
            """.trimIndent(),
        )

        val success = assertIs<FetchOutcome.Success>(dataSource.fetchRates("USD"))
        assertEquals(
            1000L + RatesRemoteDataSource.FALLBACK_TTL_SECONDS,
            success.snapshot.nextUpdateEpochSeconds,
        )
    }

    @Test
    fun `reports an HTTP error with its status code`() = runTest {
        enqueueJson("{}", code = 500)

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("USD"))
        assertEquals(RatesError.Http(500), failure.error)
    }

    @Test
    fun `reports a 404 rather than treating it as empty data`() = runTest {
        enqueueJson("{}", code = 404)

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("USD"))
        assertEquals(RatesError.Http(404), failure.error)
    }

    @Test
    fun `reports malformed JSON`() = runTest {
        enqueueJson("{ this is not json")

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("USD"))
        assertEquals(RatesError.Malformed, failure.error)
    }

    @Test
    fun `reports an empty body as malformed`() = runTest {
        enqueueJson("")

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("USD"))
        assertEquals(RatesError.Malformed, failure.error)
    }

    @Test
    fun `reports a response with no rates as malformed`() = runTest {
        enqueueJson(
            """{ "result": "success", "time_last_update_unix": 1, "base_code": "USD" }""",
        )

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("USD"))
        assertEquals(RatesError.Malformed, failure.error)
    }

    @Test
    fun `reports an empty rates object as malformed`() = runTest {
        enqueueJson(
            """{ "result": "success", "time_last_update_unix": 1, "base_code": "USD", "rates": {} }""",
        )

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("USD"))
        assertEquals(RatesError.Malformed, failure.error)
    }

    @Test
    fun `reports a missing timestamp as malformed`() = runTest {
        enqueueJson("""{ "result": "success", "base_code": "USD", "rates": { "EUR": 0.9 } }""")

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("USD"))
        assertEquals(RatesError.Malformed, failure.error)
    }

    @Test
    fun `surfaces a provider-reported error`() = runTest {
        enqueueJson("""{ "result": "error", "error-type": "unsupported-code" }""")

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("XXX"))
        assertEquals(RatesError.Provider("unsupported-code"), failure.error)
    }

    @Test
    fun `reports a network failure when the server is unreachable`() = runTest {
        server.shutdown()

        val failure = assertIs<FetchOutcome.Failure>(dataSource.fetchRates("USD"))
        assertEquals(RatesError.Network, failure.error)
    }
}
