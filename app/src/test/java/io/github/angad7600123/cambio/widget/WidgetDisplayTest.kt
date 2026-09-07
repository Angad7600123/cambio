package io.github.angad7600123.cambio.widget

import io.github.angad7600123.cambio.format.ExpressionFormatter
import io.github.angad7600123.cambio.format.NumberDisplayFormatter
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for what the widget's display shows and how big it draws it.
 *
 * Both of these were reported as faults against a recording: the widget answered
 * expressions without ever showing them, and long figures arrived truncated to
 * `117,339....`. The first is a formatting rule, the second an arithmetic one, and
 * neither needs a device to pin down.
 */
class WidgetDisplayTest {

    private val formatter = ExpressionFormatter(NumberDisplayFormatter(Locale.US), Locale.US)

    @Test
    fun `the compact form keeps operators tight against their operands`() {
        // The widget has half a phone display to work in, and every spaced operator
        // costs it two characters' worth of width.
        assertEquals("1,250+15", formatter.formatCompact("1250+15"))
        assertEquals("1,234×2", formatter.formatCompact("1234*2"))
        assertEquals("5÷0", formatter.formatCompact("5/0"))
    }

    @Test
    fun `the spaced form is unchanged, because history still reads better with it`() {
        assertEquals("1,250 + 15", formatter.format("1250+15"))
    }

    @Test
    fun `a unary minus stays tight in both forms`() {
        // Tight against its number, and drawn as a typographic minus rather than a
        // hyphen — the same glyph the binary operator gets.
        assertEquals("8×−2", formatter.formatCompact("8*-2"))
        assertEquals("8 × −2", formatter.format("8*-2"))
    }

    @Test
    fun `an expression survives the compact form intact`() {
        // Nothing may be dropped: what is shown is what will be evaluated.
        val compact = formatter.formatCompact("(2+3)*4")
        assertEquals("(2+3)×4", compact)
    }

    @Test
    fun `separators are measured narrower than the digits around them`() {
        // Counting characters and multiplying by one average width over-measures
        // money, and an over-measured figure is drawn smaller than it needed to be.
        val grouped = widthEm("1,651,065.83")
        val digitsOnly = widthEm("165106583")

        assertTrue(
            grouped < 12 * 0.58f,
            "twelve characters, but only nine of them digit-width: was $grouped",
        )
        assertTrue(grouped > digitsOnly, "the separators still take some width")
    }

    @Test
    fun `a longer reading never measures narrower than a shorter one`() {
        // The size the widget picks falls as this rises, so it must never go
        // backwards. Only *never narrower*, though: `945.40` and `12,345` are five
        // digits and a separator either way, and they do measure the same.
        val readings = listOf("0", "50", "945.40", "12,345", "117,339.45", "1,651,065.83")
        readings.zipWithNext { shorter, longer ->
            assertTrue(
                widthEm(shorter) <= widthEm(longer),
                "$shorter should not measure wider than $longer",
            )
        }
        assertTrue(widthEm("0") < widthEm("1,651,065.83"))
    }

    @Test
    fun `the minus sign is not counted as a full digit`() {
        // `-1,407.08` is nine characters but nowhere near nine digits wide, and
        // treating it as such shrank every negative reading a step too far.
        assertTrue(widthEm("-1,407.08") < widthEm("11,407.08"))
    }
}
