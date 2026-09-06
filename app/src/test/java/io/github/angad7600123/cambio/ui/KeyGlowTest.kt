package io.github.angad7600123.cambio.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import io.github.angad7600123.cambio.ui.components.GLOW_FADE_IN_MILLIS
import io.github.angad7600123.cambio.ui.components.GLOW_FADE_OUT_MILLIS
import io.github.angad7600123.cambio.ui.components.GlowStep
import io.github.angad7600123.cambio.ui.components.launchGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that the key glow never queues.
 *
 * The bug these lock down was visible on a device: hold down the keypad and the
 * halo fell further and further behind the finger, still lighting keys after the
 * last press. The cause was awaiting the animation inside `collect`, which blocked
 * the collector for the animation's whole duration, so each press could not even be
 * *read* until the one before it had finished animating.
 *
 * Virtual time makes that exact: every assertion below is about *when* an animation
 * starts, which is the whole of the defect. The collector runs on `backgroundScope`
 * because it watches a flow that never completes.
 */
class KeyGlowTest {

    private fun press() = PressInteraction.Press(Offset.Zero)

    @Test
    fun `a release takes over the glow immediately rather than waiting for the press`() = runTest {
        val source = MutableInteractionSource()
        val started = mutableListOf<Pair<Float, Long>>()

        backgroundScope.launchGlow(source.interactions) { step ->
            started += step.target to testScheduler.currentTime
            delay(step.durationMillis.toLong())
        }
        runCurrent()

        val down = press()
        source.emit(down)
        runCurrent()
        source.emit(PressInteraction.Release(down))
        runCurrent()

        // Both begin at once. The old code started the fade-out at 40ms, because the
        // fade-in had to run to completion before the release was even collected.
        assertEquals(listOf(1f to 0L, 0f to 0L), started)
    }

    @Test
    fun `a burst of presses never falls behind`() = runTest {
        val source = MutableInteractionSource()
        val started = mutableListOf<Pair<Float, Long>>()

        backgroundScope.launchGlow(source.interactions) { step ->
            started += step.target to testScheduler.currentTime
            delay(step.durationMillis.toLong())
        }
        runCurrent()

        repeat(10) {
            val down = press()
            source.emit(down)
            runCurrent()
            source.emit(PressInteraction.Release(down))
            runCurrent()
        }

        assertEquals(20, started.size, "every interaction should have been acted on")
        assertTrue(started.all { it.second == 0L }, "nothing may be deferred: $started")
    }

    @Test
    fun `the glow ends exactly one fade-out after the last release`() = runTest {
        val source = MutableInteractionSource()
        val finished = mutableListOf<Pair<Float, Long>>()

        backgroundScope.launchGlow(source.interactions) { step ->
            delay(step.durationMillis.toLong())
            finished += step.target to testScheduler.currentTime
        }
        runCurrent()

        repeat(6) {
            val down = press()
            source.emit(down)
            runCurrent()
            source.emit(PressInteraction.Release(down))
            runCurrent()
        }
        // One fade-out's worth of time, and not a millisecond more.
        testScheduler.advanceTimeBy(GLOW_FADE_OUT_MILLIS + 1L)

        // Exactly one animation survives to completion — the last fade-out — and it
        // lands at its own duration, not at the sum of everything queued behind it.
        assertEquals(listOf(0f to GLOW_FADE_OUT_MILLIS.toLong()), finished)
    }

    @Test
    fun `pressing the same key again restarts the glow instead of stacking`() = runTest {
        val source = MutableInteractionSource()
        val started = mutableListOf<Pair<Float, Long>>()
        val finished = mutableListOf<Float>()

        backgroundScope.launchGlow(source.interactions) { step ->
            started += step.target to testScheduler.currentTime
            delay(step.durationMillis.toLong())
            finished += step.target
        }
        runCurrent()

        val first = press()
        source.emit(first)
        runCurrent()
        source.emit(PressInteraction.Release(first))
        runCurrent()

        // Press again while that fade-out is still running.
        testScheduler.advanceTimeBy(GLOW_FADE_OUT_MILLIS / 2L)
        val second = press()
        source.emit(second)
        runCurrent()
        testScheduler.advanceTimeBy(GLOW_FADE_IN_MILLIS + 1L)

        assertEquals(1f to GLOW_FADE_OUT_MILLIS / 2L, started.last())
        // The interrupted fade-out never completed, so no stale glow is left behind.
        assertEquals(listOf(1f), finished)
    }

    @Test
    fun `a cancelled press fades the glow out like a release`() = runTest {
        val source = MutableInteractionSource()
        val started = mutableListOf<GlowStep>()

        backgroundScope.launchGlow(source.interactions) { step ->
            started += step
            delay(step.durationMillis.toLong())
        }
        runCurrent()

        val down = press()
        source.emit(down)
        runCurrent()
        source.emit(PressInteraction.Cancel(down))
        runCurrent()

        assertEquals(
            listOf(GlowStep(1f, GLOW_FADE_IN_MILLIS), GlowStep(0f, GLOW_FADE_OUT_MILLIS)),
            started,
        )
    }
}
