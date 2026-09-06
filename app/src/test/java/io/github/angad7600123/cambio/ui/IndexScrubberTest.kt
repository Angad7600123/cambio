package io.github.angad7600123.cambio.ui

import io.github.angad7600123.cambio.ui.sheets.letterAt
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for mapping a touch on the scrubber to a letter.
 *
 * The ends matter most: the track is inset by half a thumb at top and bottom so the
 * thumb never hangs off it, and forgetting that inset means the first and last
 * sections become unreachable — you can drag to B and to Y but never to A or Z.
 */
class IndexScrubberTest {

    private val letters = ('A'..'Z').toList()
    private val thumb = 40f
    private val height = 1000

    private fun at(y: Float) = letterAt(y, height, letters, thumb)

    @Test
    fun `the top of the track is the first letter`() = assertEquals('A', at(20f))

    @Test
    fun `the bottom of the track is the last letter`() = assertEquals('Z', at(980f))

    @Test
    fun `touching above the track clamps to the first letter`() = assertEquals('A', at(-50f))

    @Test
    fun `touching below the track clamps to the last letter`() = assertEquals('Z', at(4000f))

    @Test
    fun `the middle of the track is the middle letter`() {
        // 26 letters: the midpoint falls between M and N, and rounding takes N.
        assertEquals('N', at(500f))
    }

    @Test
    fun `a short list still spans the whole track`() {
        val three = listOf('A', 'M', 'Z')
        assertEquals('A', letterAt(20f, height, three, thumb))
        assertEquals('M', letterAt(500f, height, three, thumb))
        assertEquals('Z', letterAt(980f, height, three, thumb))
    }

    @Test
    fun `a single letter needs no division`() {
        assertEquals('A', letterAt(500f, height, listOf('A'), thumb))
    }

    @Test
    fun `an unmeasured track does not divide by zero`() {
        assertEquals('A', letterAt(0f, 0, letters, thumb))
    }
}
