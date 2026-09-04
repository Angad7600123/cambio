package io.github.angad7600123.cambio.calculator

/**
 * Every key on the keypad, as a closed set.
 *
 * The layout mirrors the physical grid:
 * ```
 * C   <x  %   /
 * 7   8   9   *
 * 4   5   6   -
 * 1   2   3   +
 * ()  0   .   =
 * ```
 */
sealed interface CalculatorKey {
    data class Digit(val value: Int) : CalculatorKey {
        init {
            require(value in 0..9) { "Digit must be 0-9, was $value" }
        }
    }

    data class Operator(val type: OperatorType) : CalculatorKey

    data object Decimal : CalculatorKey

    data object Percent : CalculatorKey

    /** A single context-aware key that inserts whichever parenthesis fits. */
    data object Parenthesis : CalculatorKey

    data object Clear : CalculatorKey

    data object Backspace : CalculatorKey

    data object Equals : CalculatorKey
}
