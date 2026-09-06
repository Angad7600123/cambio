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
 * between the digits you actually meant, and it is also what lets the entry animation
 * find the glyph the caret is on.
 */
internal class ExpressionTransformation(
    private val operatorColor: Color,
    private val groupingSeparator: Char,
    private val decimalSeparator: Char,
    private val entry: EntryAnimation = EntryAnimation.None,
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
                    // No padding around operators. The reference sets them tight —
                    // `2x2x2`, not `2 x 2 x 2` — and the spaces were not merely a
                    // stylistic difference: each one was two characters' worth of
                    // width appearing in a single frame, while the glyph between them
                    // grew in over 180ms. The line lurched sideways every time an
                    // operator was pressed, because part of its width was animated
                    // and part of it simply appeared. The tint already separates the
                    // operators from the digits without help from whitespace.
                    originalToTransformed[index] = builder.length
                    val start = builder.length
                    builder.append(glyphFor(char))
                    if (char in ACCENTED) {
                        spans += AnnotatedString.Range(
                            SpanStyle(color = operatorColor),
                            start,
                            builder.length,
                        )
                    }
                    index++
                }
            }
        }
        originalToTransformed[raw.length] = builder.length

        // One UI grows each new character into place. The character is addressed by
        // its index in the raw expression, not by being last: typing into the middle
        // of a figure must animate the digit under the caret, leaving everything
        // after it perfectly still.
        //
        // Every original character occupies exactly one output character — separators
        // and spacing are inserted *before* the position that gets recorded — so the
        // glyph to style is the single character at its mapped offset.
        if (entry.index in raw.indices && entry.scale < 1f) {
            val at = originalToTransformed[entry.index]
            if (at < builder.length) {
                spans += AnnotatedString.Range(
                    SpanStyle(
                        fontSize = TextUnit(entry.scale, TextUnitType.Em),
                        // Scaling the font alone drops the glyph onto the baseline and
                        // reads as a subscript, so the baseline is lifted in step to
                        // keep it growing about its own middle.
                        baselineShift = BaselineShift((1f - entry.scale) * BASELINE_LIFT),
                    ),
                    at,
                    at + 1,
                )
            }
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

        /**
         * How far a shrunken glyph is lifted off the baseline as it grows.
         *
         * Not all the way to centred. Scaling alone leaves the glyph sitting on the
         * baseline, which reads as a subscript; lifting it fully to the line's middle
         * reads as a superscript. Measured against the reference, One UI's growing
         * glyph sits about three fifths of the way from the baseline to the centre,
         * and this is that fraction of the full centring lift.
         */
        private const val BASELINE_LIFT = 0.22f

        /** Glyphs drawn in the accent tone once transformed. */
        private val ACCENTED = setOf('*', '/', '-', '+', '(', ')')

        /** Builds a transformation using the device's own separators. */
        fun forLocale(
            operatorColor: Color,
            formatter: NumberDisplayFormatter,
            entry: EntryAnimation = EntryAnimation.None,
        ) = ExpressionTransformation(
            operatorColor = operatorColor,
            groupingSeparator = formatter.groupingSeparator,
            decimalSeparator = formatter.decimalSeparator,
            entry = entry,
        )
    }
}
