package io.github.angad7600123.cambio.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.angad7600123.cambio.R
import io.github.angad7600123.cambio.calculator.CalculatorKey
import io.github.angad7600123.cambio.calculator.OperatorType
import io.github.angad7600123.cambio.format.ExpressionFormatter
import io.github.angad7600123.cambio.format.NumberDisplayFormatter

/**
 * One key's presentation: what it shows, what it announces, and how it is styled.
 *
 * Declaring the pad as data rather than 20 hand-written composables keeps the grid
 * honest — every key is spaced and sized by the same rules — and makes the layout
 * readable at a glance.
 */
private data class KeySpec(
    val label: String,
    @StringRes val descriptionRes: Int,
    val style: KeyStyle,
    val key: CalculatorKey,
)

/**
 * The calculator keypad.
 *
 * ```
 * C   <x  %   ÷
 * 7   8   9   ×
 * 4   5   6   −
 * 1   2   3   +
 * ()  0   .   =
 * ```
 *
 * The decimal key shows the *locale's* separator, so it reads `,` on a German
 * device — matching what the display renders.
 */
@Composable
fun Keypad(
    onKeyPress: (CalculatorKey) -> Unit,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = KEY_GAP_HORIZONTAL,
    verticalSpacing: Dp = KEY_GAP_VERTICAL,
    numberFormatter: NumberDisplayFormatter = remember { NumberDisplayFormatter() },
) {
    val decimalSeparator = remember(numberFormatter) {
        numberFormatter.decimalSeparator.toString()
    }

    val rows = remember(decimalSeparator) { keypadRows(decimalSeparator) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            ) {
                row.forEach { spec ->
                    CalcButton(
                        label = spec.label,
                        contentDescription = stringResource(spec.descriptionRes),
                        onClick = { onKeyPress(spec.key) },
                        style = spec.style,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun keypadRows(decimalSeparator: String): List<List<KeySpec>> = listOf(
    listOf(
        KeySpec("C", R.string.key_clear, KeyStyle.Destructive, CalculatorKey.Clear),
        KeySpec("⌫", R.string.key_backspace, KeyStyle.Destructive, CalculatorKey.Backspace),
        KeySpec("%", R.string.key_percent, KeyStyle.Standard, CalculatorKey.Percent),
        KeySpec(
            ExpressionFormatter.DIVIDE_GLYPH,
            R.string.key_divide,
            KeyStyle.Operator,
            CalculatorKey.Operator(OperatorType.DIVIDE),
        ),
    ),
    listOf(
        digit(7),
        digit(8),
        digit(9),
        KeySpec(
            ExpressionFormatter.MULTIPLY_GLYPH,
            R.string.key_multiply,
            KeyStyle.Operator,
            CalculatorKey.Operator(OperatorType.MULTIPLY),
        ),
    ),
    listOf(
        digit(4),
        digit(5),
        digit(6),
        KeySpec(
            ExpressionFormatter.MINUS_GLYPH,
            R.string.key_subtract,
            KeyStyle.Operator,
            CalculatorKey.Operator(OperatorType.SUBTRACT),
        ),
    ),
    listOf(
        digit(1),
        digit(2),
        digit(3),
        KeySpec(
            ExpressionFormatter.PLUS_GLYPH,
            R.string.key_add,
            KeyStyle.Operator,
            CalculatorKey.Operator(OperatorType.ADD),
        ),
    ),
    listOf(
        KeySpec("( )", R.string.key_parenthesis, KeyStyle.Standard, CalculatorKey.Parenthesis),
        digit(0),
        KeySpec(decimalSeparator, R.string.key_decimal, KeyStyle.Standard, CalculatorKey.Decimal),
        KeySpec("=", R.string.key_equals, KeyStyle.Accent, CalculatorKey.Equals),
    ),
)

private fun digit(value: Int): KeySpec = KeySpec(
    label = value.toString(),
    descriptionRes = DIGIT_DESCRIPTIONS[value],
    style = KeyStyle.Standard,
    key = CalculatorKey.Digit(value),
)

private val DIGIT_DESCRIPTIONS = intArrayOf(
    R.string.key_0,
    R.string.key_1,
    R.string.key_2,
    R.string.key_3,
    R.string.key_4,
    R.string.key_5,
    R.string.key_6,
    R.string.key_7,
    R.string.key_8,
    R.string.key_9,
)

/**
 * Gutters measured from One UI: 17.5dp horizontally, 15dp vertically. The wider
 * horizontal gutter is what stops the four columns reading as a solid slab.
 */
private val KEY_GAP_HORIZONTAL = 17.5.dp
private val KEY_GAP_VERTICAL = 15.dp
