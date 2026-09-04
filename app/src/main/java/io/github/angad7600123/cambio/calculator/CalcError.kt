package io.github.angad7600123.cambio.calculator

/**
 * Every way an expression can fail to produce a number.
 *
 * The evaluator never throws; it returns one of these inside [CalcResult] so the
 * UI can map each case to a specific, translatable message.
 */
enum class CalcError {
    /** The expression is empty or contains only whitespace. */
    EMPTY,

    /** Structurally invalid, e.g. `5 +` or `()` or mismatched parentheses. */
    MALFORMED,

    /** A division whose divisor evaluates to exactly zero. */
    DIVIDE_BY_ZERO,

    /** The result is finite but too large to represent or display meaningfully. */
    OVERFLOW,
}

/**
 * The outcome of evaluating an expression.
 *
 * Modelled as a sealed type rather than a nullable number so that callers are
 * forced to handle failure, and so the specific failure survives to the UI.
 */
sealed interface CalcResult {
    @JvmInline
    value class Success(val value: java.math.BigDecimal) : CalcResult

    @JvmInline
    value class Failure(val error: CalcError) : CalcResult

    companion object {
        fun success(value: java.math.BigDecimal): CalcResult = Success(value)

        fun failure(error: CalcError): CalcResult = Failure(error)
    }
}

/** Returns the value on success, or `null` if evaluation failed. */
fun CalcResult.valueOrNull(): java.math.BigDecimal? = (this as? CalcResult.Success)?.value

/** Returns the error on failure, or `null` if evaluation succeeded. */
fun CalcResult.errorOrNull(): CalcError? = (this as? CalcResult.Failure)?.error
