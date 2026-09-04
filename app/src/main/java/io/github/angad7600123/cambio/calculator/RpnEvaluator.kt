package io.github.angad7600123.cambio.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.ArrayDeque

/**
 * Evaluates a Reverse Polish Notation token stream to an exact [BigDecimal].
 *
 * Arithmetic uses [BigDecimal] throughout, so `0.1 + 0.2` is exactly `0.3` rather
 * than the `0.30000000000000004` a binary floating-point calculator would show.
 *
 * This evaluator never throws. Every failure path returns a [CalcResult.Failure].
 */
internal object RpnEvaluator {
    /** 34 significant digits — IEEE 754 decimal128, ample for a pocket calculator. */
    val MATH_CONTEXT: MathContext = MathContext(34, RoundingMode.HALF_UP)

    /** Results beyond this magnitude are reported as overflow rather than rendered. */
    private val OVERFLOW_LIMIT: BigDecimal = BigDecimal("1E+1000")

    /** Guards against unbounded scale growth from long chains of operations. */
    private const val MAX_PRECISION_BEFORE_ROUNDING = 60

    private val HUNDRED = BigDecimal("100")

    /**
     * A value on the evaluation stack, plus whether it was written with a trailing
     * `%`.
     *
     * Percent is resolved lazily: `10%` only means "10% *of the left operand*" once
     * we know which operator consumes it. That is what makes `200 + 10%` equal 220
     * while `200 × 10%` equals 20.
     */
    private data class Operand(val value: BigDecimal, val isPercent: Boolean = false)

    fun evaluate(rpn: List<Token>): CalcResult {
        if (rpn.isEmpty()) return CalcResult.failure(CalcError.EMPTY)

        val stack = ArrayDeque<Operand>()

        for (token in rpn) {
            when (token) {
                is Token.Number -> stack.push(Operand(token.value))

                Token.Percent -> {
                    val operand = stack.poll() ?: return CalcResult.failure(CalcError.MALFORMED)
                    stack.push(operand.copy(isPercent = true))
                }

                is Token.UnaryOperator -> {
                    val operand = stack.poll() ?: return CalcResult.failure(CalcError.MALFORMED)
                    stack.push(operand.copy(value = operand.value.negate()))
                }

                is Token.Operator -> {
                    val right = stack.poll() ?: return CalcResult.failure(CalcError.MALFORMED)
                    val left = stack.poll() ?: return CalcResult.failure(CalcError.MALFORMED)
                    when (val applied = apply(token.type, left, right)) {
                        is CalcResult.Failure -> return applied
                        is CalcResult.Success -> stack.push(Operand(applied.value))
                    }
                }

                Token.LeftParen, Token.RightParen ->
                    return CalcResult.failure(CalcError.MALFORMED)
            }
        }

        if (stack.size != 1) return CalcResult.failure(CalcError.MALFORMED)

        val final = stack.pop()
        // A bare "50%" with no operator is simply 0.5.
        val result = if (final.isPercent) final.value.divide(HUNDRED, MATH_CONTEXT) else final.value

        return if (result.abs() >= OVERFLOW_LIMIT) {
            CalcResult.failure(CalcError.OVERFLOW)
        } else {
            CalcResult.success(result.normalize())
        }
    }

    private fun apply(operator: OperatorType, left: Operand, right: Operand): CalcResult {
        // Resolve a percent right-hand operand against the left operand, using the
        // contextual meaning people expect from a physical calculator.
        val rightValue = if (right.isPercent) {
            when (operator) {
                OperatorType.ADD, OperatorType.SUBTRACT ->
                    left.value.multiply(right.value).divide(HUNDRED, MATH_CONTEXT)
                OperatorType.MULTIPLY, OperatorType.DIVIDE ->
                    right.value.divide(HUNDRED, MATH_CONTEXT)
            }
        } else {
            right.value
        }

        // A percent on the left with no context of its own is just a plain fraction.
        val leftValue =
            if (left.isPercent) left.value.divide(HUNDRED, MATH_CONTEXT) else left.value

        val result = when (operator) {
            OperatorType.ADD -> leftValue.add(rightValue)
            OperatorType.SUBTRACT -> leftValue.subtract(rightValue)
            OperatorType.MULTIPLY -> leftValue.multiply(rightValue)
            OperatorType.DIVIDE -> {
                if (rightValue.signum() == 0) {
                    return CalcResult.failure(CalcError.DIVIDE_BY_ZERO)
                }
                leftValue.divide(rightValue, MATH_CONTEXT)
            }
        }

        val bounded = if (result.precision() > MAX_PRECISION_BEFORE_ROUNDING) {
            result.round(MATH_CONTEXT)
        } else {
            result
        }

        return if (bounded.abs() >= OVERFLOW_LIMIT) {
            CalcResult.failure(CalcError.OVERFLOW)
        } else {
            CalcResult.success(bounded)
        }
    }

    /**
     * Drops meaningless trailing zeros while keeping the value an integer-scaled
     * decimal, so `2.50` prints as `2.5` and `100` never becomes `1E+2`.
     */
    private fun BigDecimal.normalize(): BigDecimal {
        val stripped = stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }
}
