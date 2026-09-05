package io.github.angad7600123.cambio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import io.github.angad7600123.cambio.ui.components.ExpressionTransformation
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the display transformation and, most importantly, its caret mapping.
 *
 * Grouping separators and operator spacing change the string's length, so without a
 * correct mapping a tap lands the caret several characters away from where it was
 * aimed — and every subsequent digit goes to the wrong place.
 */
class ExpressionTransformationTest {

    private val transformation = ExpressionTransformation(
        operatorColor = Color(0xFF5DD4B9),
        groupingSeparator = ',',
        decimalSeparator = '.',
    )

    private fun transform(raw: String) = transformation.filter(AnnotatedString(raw))

    @Test
    fun `thousands are grouped`() = assertEquals("1,234,567", transform("1234567").text.text)

    @Test
    fun `short numbers are untouched`() = assertEquals("123", transform("123").text.text)

    @Test
    fun `only the integer part is grouped`() = assertEquals("1,234.5678", transform("1234.5678").text.text)

    @Test
    fun `operators get typographic glyphs and spacing`() = assertEquals("1,250 + 15", transform("1250+15").text.text)

    @Test
    fun `multiply and divide use their real glyphs`() {
        assertTrue(transform("6*2").text.text.contains('×'))
        assertTrue(transform("6/2").text.text.contains('÷'))
    }

    @Test
    fun `a unary minus is not spaced`() = assertEquals("8 × −2", transform("8*-2").text.text)

    @Test
    fun `operators are tinted`() {
        val spans = transform("1+2").text.spanStyles
        assertEquals(1, spans.size)
    }

    // region Caret mapping

    @Test
    fun `the caret maps past grouping separators`() {
        // "1234567" -> "1,234,567". Raw index 1 (after the leading 1) must land
        // after the comma, at visual index 2.
        val mapping = transform("1234567").offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(1))
        assertEquals(3, mapping.originalToTransformed(2))
        assertEquals(9, mapping.originalToTransformed(7))
    }

    @Test
    fun `the caret maps back from a visual position`() {
        val mapping = transform("1234567").offsetMapping
        // Visual index 2 is the '2' in "1,234,567", which is raw index 1.
        assertEquals(1, mapping.transformedToOriginal(2))
        assertEquals(7, mapping.transformedToOriginal(9))
    }

    @Test
    fun `the caret maps across operator spacing`() {
        // "1250+15" -> "1,250 + 15". The '+' is raw index 4, visual index 6.
        val mapping = transform("1250+15").offsetMapping
        assertEquals(6, mapping.originalToTransformed(4))
        assertEquals(4, mapping.transformedToOriginal(6))
    }

    @Test
    fun `round-tripping every position is stable`() {
        val raw = "12345.67+890"
        val mapping = transform(raw).offsetMapping
        for (i in 0..raw.length) {
            val there = mapping.originalToTransformed(i)
            assertEquals(i, mapping.transformedToOriginal(there), "position $i")
        }
    }

    @Test
    fun `mapping an empty expression is safe`() {
        val mapping = transform("").offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(0, mapping.transformedToOriginal(0))
    }

    @Test
    fun `out-of-range offsets are clamped rather than throwing`() {
        val mapping = transform("123").offsetMapping
        assertEquals(3, mapping.originalToTransformed(99))
        assertEquals(0, mapping.originalToTransformed(-4))
        assertEquals(3, mapping.transformedToOriginal(99))
    }

    // endregion

    @Test
    fun `a fourteen digit figure stays whole`() {
        // The clipping bug: nothing may be dropped, however long the figure.
        val text = transform("12345678901234").text.text
        assertEquals("12,345,678,901,234", text)
    }

    @Test
    fun `a different locale's separators are honoured`() {
        val german = ExpressionTransformation(
            operatorColor = Color.White,
            groupingSeparator = '.',
            decimalSeparator = ',',
        )
        assertEquals("1.234,5", german.filter(AnnotatedString("1234.5")).text.text)
    }
}
