package io.github.angad7600123.cambio.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Talks to the exchange-rate provider. This is the only class in the app that
 * knows the provider exists.
 *
 * Everything above it works with [RateSnapshot] and [RatesError], so swapping
 * providers means replacing this one file. The base URL is injectable, which is
 * how the tests point it at a local [okhttp3.mockwebserver.MockWebServer] instead
 * of the internet — no test ever touches the real API.
 *
 * ## Provider
 * `open.er-api.com` is ExchangeRate-API's open endpoint. It needs no API key and
 * no signup, and returns ~166 currencies refreshed once every 24 hours. Their
 * terms require visible attribution, which the app renders in its About sheet.
 */
class RatesRemoteDataSource(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val json: Json = defaultJson(),
) {
    /**
     * Fetches the rate table quoted against [base].
     *
     * Never throws: every failure is returned as [FetchOutcome.Failure] so callers
     * are forced to deal with it.
     */
    suspend fun fetchRates(base: String): FetchOutcome {
        val url = baseUrl.newBuilder()
            .addPathSegment(base)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (_: IOException) {
            return FetchOutcome.Failure(RatesError.Network)
        }

        return response.use { parse(it) }
    }

    private fun parse(response: Response): FetchOutcome {
        if (!response.isSuccessful) {
            return FetchOutcome.Failure(RatesError.Http(response.code))
        }

        val body = response.body?.string()
            ?: return FetchOutcome.Failure(RatesError.Malformed)

        val parsed = try {
            json.decodeFromString(ExchangeRatesResponse.serializer(), body)
        } catch (_: Exception) {
            // kotlinx.serialization raises several unrelated exception types for bad
            // input; none of them should ever reach the UI as a crash.
            return FetchOutcome.Failure(RatesError.Malformed)
        }

        if (!parsed.isSuccess) {
            return FetchOutcome.Failure(RatesError.Provider(parsed.errorType ?: parsed.result))
        }

        val rates = parsed.rates?.takeIf { it.isNotEmpty() }
            ?: return FetchOutcome.Failure(RatesError.Malformed)
        val baseCode = parsed.baseCode?.takeIf { it.isNotBlank() }
            ?: return FetchOutcome.Failure(RatesError.Malformed)
        val lastUpdate = parsed.timeLastUpdateUnix
            ?: return FetchOutcome.Failure(RatesError.Malformed)

        return FetchOutcome.Success(
            RateSnapshot(
                base = baseCode,
                rates = rates,
                fetchedAtEpochSeconds = lastUpdate,
                // If the provider omits its next-update time, fall back to a day so the
                // app still refreshes eventually rather than caching forever.
                nextUpdateEpochSeconds = parsed.timeNextUpdateUnix
                    ?: (lastUpdate + FALLBACK_TTL_SECONDS),
            ),
        )
    }

    companion object {
        /** ExchangeRate-API's keyless open endpoint. */
        const val DEFAULT_BASE_URL = "https://open.er-api.com/v6/latest/"

        /** Used only when the provider does not state its own next-update time. */
        const val FALLBACK_TTL_SECONDS = 24L * 60 * 60

        private const val TIMEOUT_SECONDS = 15L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        fun defaultJson(): Json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Bridges OkHttp's callback API to coroutines, so a slow network suspends rather
 * than blocking a thread, and cancelling the caller cancels the HTTP call.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWith(Result.failure(e))
            }
        },
    )
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
}
