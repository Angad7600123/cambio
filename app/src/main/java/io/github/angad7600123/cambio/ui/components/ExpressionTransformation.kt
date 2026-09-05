package io.github.angad7600123.cambio.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import io.github.angad7600123.cambio.calculator.OperatorType
import io.github.angad7600123.cambio.format.NumberDisplayFormatter

/**
 * Renders the raw expression for display while keeping the caret honest.
 *
 * The field's *value* is the canonical expression (`1250+15`); what is drawn is the
 * presentation form (`1,250 + 15`) with grouping separators, typographic operator
 * glyphs and the operators tinted. Because those transformations change the string
 * length, a plain formatter would leave the caret pointing at the wrong character —
 * so this builds an explicit index map alongside the text and hands it back as an
 * [OffsetMapping].
 *
 * That mapping is what makes tapping into the middle of `12,345,678` land the caret
 * between the digits you actually meant.
 */
class ExpressionTransformation(
    private val operatorColor: Color,
    private val groupingSeparator: Char,
    private val decimalSeparator: Char,
    private val lastCharScale: Float = 1f,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = StringBuilder(raw.length + raw.length / 2)

        // originalToTransformed[i] is where original index i starts in the output.
        val originalToTransformed = IntArray(raw.length + 1)
        val spans = mutableListOf<AnnotatedString.Range<SpanStyle>>()

        var index = 0
        while (index < raw.length) {
            val char = raw[index]

            when {
                char.isDigit() -> {
                    // Consume the whole literal so grouping can be applied to its
                    // integer part, recording a position for every digit consumed.
                    val end = endOfNumber(raw, index)
                    appendNumber(raw, index, end, builder, originalToTransformed)
                    index = end
                }

                char == '.' -> {
                    originalToTransformed[index] = builder.length
                    builder.append(decimalSeparator)
                    index++
                }

                else -> {
                    val glyph = glyphFor(char)
                    val spaced = OperatorType.fromSymbol(char) != null && !isUnaryAt(raw, index)
                    if (spaced) builder.append(' ')
                    // Recorded after the leading space so the caret hugs the operator
                    // rather than floating in the gutter before it.
                    originalToTransformed[index] = builder.length
                    val start = builder.length
                    builder.append(glyph)
                    if (char in ACCENTED) {
                        spans += AnnotatedString.Range(
                            SpanStyle(color = operatorColor),
                            start,
                            builder.length,
                        )
                    }
                    if (spaced) builder.append(' ')
                    index++
                }
            }
        }
        originalToTransformed[raw.length] = builder.length

        // One UI pops each new character in. Scaling the font alone would drop the
        // glyph onto the baseline and read as a subscript, so the baseline is lifted
        // by the same proportion to keep it centred as it grows.
        if (lastCharScale < 1f && builder.isNotEmpty()) {
            spans += AnnotatedString.Range(
                SpanStyle(
                    fontSize = TextUnit(lastCharScale, TextUnitType.Em),
                    baselineShift = BaselineShift((1f - lastCharScale) * BASELINE_LIFT),
                ),
                builder.length - 1,
                builder.length,
            )
        }

        val transformed = builder.toString()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int {
                val target = offset.coerceIn(0, transformed.length)
                // The nearest original index whose start is at or before the target.
                var best = 0
                for (i in 0..raw.length) {
                    if (originalToTransformed[i] <= target) best = i else break
                }
                return best
            }
        }

        return TransformedText(AnnotatedString(transformed, spans), mapping)
    }

    /**
     * Appends one number literal with grouping, mapping every original digit.
     *
     * Separators are inserted *before* the digit they precede, so the digit's
     * recorded position still points at the digit itself.
     */
    private fun appendNumber(raw: String, start: Int, end: Int, builder: StringBuilder, map: IntArray) {
        val literal = raw.substring(start, end)
        val pointAt = literal.indexOf('.')
        val integerLength = if (pointAt < 0) literal.length else pointAt

        literal.forEachIndexed { offset, char ->
            val original = start + offset
            if (char == '.') {
                map[original] = builder.length
                builder.append(decimalSeparator)
                return@forEachIndexed
            }
            // A separator goes in front of every third digit from the right, but
            // never in front of the first.
            val fromRight = integerLength - offset
            if (offset < integerLength && offset > 0 && fromRight % GROUP_SIZE == 0) {
                builder.append(groupingSeparator)
            }
            map[original] = builder.length
            builder.append(char)
        }
    }

    private fun endOfNumber(text: String, start: Int): Int {
        var index = start
        while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
        return index
    }

    /** A minus is unary at the start, or after another operator or an open bracket. */
    private fun isUnaryAt(text: String, index: Int): Boolean {
        if (text[index] != OperatorType.SUBTRACT.symbol) return false
        val previous = text.getOrNull(index - 1) ?: return true
        return previous == '(' || OperatorType.fromSymbol(previous) != null
    }

    private fun glyphFor(char: Char): Char = when (char) {
        OperatorType.MULTIPLY.symbol -> MULTIPLY
        OperatorType.DIVIDE.symbol -> DIVIDE
        OperatorType.SUBTRACT.symbol -> MINUS
        else -> char
    }

    companion object {
        const val MULTIPLY = '×'
        const val DIVIDE = '÷'
        const val MINUS = '−'
        private const val GROUP_SIZE = 3

        /** How far to lift a shrunken glyph so it grows from its middle. */
        private const val BASELINE_LIFT = 0.36f

        /** Glyphs drawn in the accent tone once transformed. */
        private val ACCENTED = setOf('*', '/', '-', '+', '(', ')')

        /** Builds a transformation using the device's own separators. */
        fun forLocale(operatorColor: Color, formatter: NumberDisplayFormatter, lastCharScale: Float = 1f) =
            ExpressionTransformation(
                operatorColor = operatorColor,
                groupingSeparator = formatter.groupingSeparator,
                decimalSeparator = formatter.decimalSeparator,
                lastCharScale = lastCharScale,
            )
    }
}
