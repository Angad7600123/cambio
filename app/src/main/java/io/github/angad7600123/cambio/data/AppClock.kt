package io.github.angad7600123.cambio.data

/**
 * The current time, behind an interface.
 *
 * Cache expiry depends on "now", so injecting the clock is what lets the tests
 * assert staleness deterministically instead of sleeping or guessing.
 */
fun interface AppClock {
    fun nowEpochSeconds(): Long

    companion object {
        val System = AppClock { java.lang.System.currentTimeMillis() / 1_000L }
    }
}
