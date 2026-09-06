package io.github.angad7600123.cambio.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.angad7600123.cambio.ui.components.FigureSizes
import io.github.angad7600123.cambio.ui.components.fitFontSize
import io.github.angad7600123.cambio.ui.theme.CambioTextStyles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that a figure is sized to fit the box it is actually given.
 *
 * On device rather than as a unit test because the whole point is real measurement:
 * the previous implementation guessed a size from the character count and clipped
 * `7,566.6400` — ten characters, which its table said would fit at 46sp. Only a real
 * font, at a real density, against a real box, can show whether that is true.
 */
@RunWith(AndroidJUnit4::class)
class FigureAutoSizeTest {

    @get:Rule
    val rule = createComposeRule()

    private val style = CambioTextStyles.Display

    /** Runs [block] with a live measurer from a composition. */
    private fun withMeasurer(block: (TextMeasurer) -> Unit) {
        lateinit var measurer: TextMeasurer
        rule.setContent { measurer = rememberTextMeasurer() }
        rule.runOnIdle { block(measurer) }
    }

    private fun TextMeasurer.overflows(text: String, size: TextUnit, boxPx: Int): Boolean = measure(
        text = AnnotatedString(text),
        style = style.copy(fontSize = size),
        maxLines = 1,
        softWrap = false,
        constraints = Constraints(maxWidth = boxPx),
    ).didOverflowWidth

    @Test
    fun aShortFigureKeepsTheLargestSize() = withMeasurer { measurer ->
        assertEquals(FigureSizes.first(), fitFontSize(measurer, "250", style, BOX_PX))
    }

    @Test
    fun anEmptyFigureKeepsTheLargestSize() = withMeasurer { measurer ->
        assertEquals(FigureSizes.first(), fitFontSize(measurer, "", style, BOX_PX))
    }

    @Test
    fun theChosenSizeActuallyFits() = withMeasurer { measurer ->
        // The whole contract, stated directly: whatever comes back must not overflow
        // unless the ladder has run out.
        listOf("250", "7,566.6400", "12,345,678.90", "123,456,789,012,345").forEach { figure ->
            val chosen = fitFontSize(measurer, figure, style, BOX_PX)
            if (chosen != FigureSizes.last()) {
                assertFalse(measurer.overflows(figure, chosen, BOX_PX), "$figure overflowed at $chosen")
            }
        }
    }

    @Test
    fun theRegressionCaseIsNotClipped() = withMeasurer { measurer ->
        // The exact figure from the bug report, in the box a phone actually gives it.
        val chosen = fitFontSize(measurer, "7,566.6400", style, BOX_PX)
        assertFalse(measurer.overflows("7,566.6400", chosen, BOX_PX))
    }

    @Test
    fun aLongerFigureNeverGetsALargerSize() = withMeasurer { measurer ->
        val short = fitFontSize(measurer, "250", style, BOX_PX)
        val medium = fitFontSize(measurer, "1,234,567.89", style, BOX_PX)
        val long = fitFontSize(measurer, "123,456,789,012,345", style, BOX_PX)

        assertTrue(short.value >= medium.value, "$short then $medium")
        assertTrue(medium.value >= long.value, "$medium then $long")
    }

    @Test
    fun aNarrowBoxFallsBackToTheSmallestSize() = withMeasurer { measurer ->
        // Below the floor the field scrolls instead; the size must not keep dropping.
        assertEquals(FigureSizes.last(), fitFontSize(measurer, "123,456,789,012,345", style, NARROW_PX))
    }

    @Test
    fun anUnmeasuredBoxDoesNotShrinkTheFigure() = withMeasurer { measurer ->
        // The first frame, before layout has reported a width: guessing small there
        // would make every figure visibly jump up a size once measurement arrives.
        assertEquals(FigureSizes.first(), fitFontSize(measurer, "1,234,567", style, 0))
    }
}

/** Roughly the figure's box on a 411dp phone once margins and the code are taken. */
private const val BOX_PX = 750

/** Deliberately too small for anything, to reach the bottom of the ladder. */
private const val NARROW_PX = 90
