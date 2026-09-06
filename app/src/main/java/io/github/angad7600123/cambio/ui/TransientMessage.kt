package io.github.angad7600123.cambio.ui

import io.github.angad7600123.cambio.calculator.CalcError

/**
 * Something to float briefly over the keypad.
 *
 * Distinct from [CalcError] because not everything worth saying is an evaluation
 * failure: refusing a sixteenth digit is a rule of the input, not a fault in the
 * expression, and the evaluator can never produce it. Keeping it out of [CalcError]
 * leaves that enum an honest description of what the engine can return.
 */
enum class TransientMessage {
    EMPTY,
    MALFORMED,
    DIVIDE_BY_ZERO,
    OVERFLOW,

    /** A digit was refused because the number already holds the maximum. */
    DIGIT_LIMIT,
    ;

    companion object {
        fun of(error: CalcError): TransientMessage = when (error) {
            CalcError.EMPTY -> EMPTY
            CalcError.MALFORMED -> MALFORMED
            CalcError.DIVIDE_BY_ZERO -> DIVIDE_BY_ZERO
            CalcError.OVERFLOW -> OVERFLOW
        }
    }
}
