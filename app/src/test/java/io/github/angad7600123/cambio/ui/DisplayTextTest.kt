package io.github.angad7600123.cambio.ui

import androidx.compose.ui.graphics.Color
import io.github.angad7600123.cambio.ui.components.buildDisplayText
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for how the display line is styled.
 *
 * Operators are tinted so a long expression stays scannable, and a caret is
 * appended while editing — both behaviours copied from One UI. These are pure
 * string/annotation transforms, so they are checked here rather than on a device.
 */
class DisplayTextTest {

    private val accent = Color(0xFF5DD4B9)
    private val caret = Color(0xFF5DD4B9)

    private fun styled(text: String, showCaret: Boolean = false) = buildDisplayText(
        text = text,
        operatorColor = accent,
        caretColor = caret,
        showCaret = showCaret,
    )

    @Test
    fun `plain digits carry no colour spans`() {
        val result = styled("1,250")
        assertEquals("1,250", result.text)
        assertTrue(result.spanStyles.isEmpty(), "Digits should not be tinted")
    }

    @Test
    fun `operators are tinted`() {
        val result = styled("12 − 10")
        assertEquals("12 − 10", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(accent, result.spanStyles.single().item.color)
    }

    @Test
    fun `every binary operator is tinted`() {
        listOf("1 + 2", "1 − 2", "1 × 2", "1 ÷ 2").forEach { expression ->
            assertEquals(1, styled(expression).spanStyles.size, "No tint in \"$expression\"")
        }
    }

    @Test
    fun `parentheses are tinted too`() {
        // Three tinted glyphs: the opening bracket, the plus, and the closing bracket.
        val tinted = styled("(1 + 2)").spanStyles.count { it.item.color == accent }
        assertEquals(3, tinted)
    }

    @Test
    fun `digits and percent stay untinted`() {
        // Percent belongs to the number it follows, so it is not an operator here.
        val result = styled("50%")
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `the caret is appended while editing`() {
        val result = styled("12", showCaret = true)
        assertEquals("12|", result.text)
    }

    @Test
    fun `no caret once a result is shown`() {
        val result = styled("12", showCaret = false)
        assertEquals("12", result.text)
    }

    @Test
    fun `the caret follows the whole expression, operators included`() {
        val result = styled("12 +", showCaret = true)
        assertTrue(result.text.endsWith("|"))
        assertTrue(result.text.startsWith("12 +"))
    }

    @Test
    fun `an empty expression still gets its caret`() = assertEquals("|", styled("", showCaret = true).text)
}
