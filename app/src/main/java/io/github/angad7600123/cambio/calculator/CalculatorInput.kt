package io.github.angad7600123.cambio.calculator

/**
 * The immutable state of what the user has typed.
 *
 * @property expression the canonical expression string (internal alphabet).
 * @property justEvaluated true immediately after `=`, which changes what the next
 *   keypress means: a digit starts a fresh calculation, an operator continues from
 *   the result.
 * @property error set when the last evaluation failed, so the display can show a
 *   message and the next keypress can clear it.
 */
data class InputState(val expression: String = "", val justEvaluated: Boolean = false, val error: CalcError? = null) {
    val isEmpty: Boolean get() = expression.isEmpty()

    companion object {
        val Empty = InputState()
    }
}

/**
 * A pure state machine mapping a [CalculatorKey] press onto a new [InputState].
 *
 * All the "feels like a real calculator" rules live here: replacing a trailing
 * operator instead of stacking two, refusing a second decimal point in the same
 * number, capping digit entry, and choosing the right parenthesis. Keeping them in
 * one pure function makes every rule directly testable without touching the UI.
 */
object CalculatorInput {
    /** Digits allowed in a single number literal, matching typical pocket calculators. */
    const val MAX_DIGITS_PER_NUMBER = 15

    fun press(state: InputState, key: CalculatorKey): InputState = when (key) {
        is CalculatorKey.Digit -> appendDigit(state, key.value)
        is CalculatorKey.Operator -> appendOperator(state, key.type)
        CalculatorKey.Decimal -> appendDecimal(state)
        CalculatorKey.Percent -> appendPercent(state)
        CalculatorKey.Parenthesis -> appendParenthesis(state)
        CalculatorKey.Clear -> InputState.Empty
        CalculatorKey.Backspace -> backspace(state)
        CalculatorKey.Equals -> equals(state)
    }

    private fun appendDigit(state: InputState, digit: Int): InputState {
        // After `=` or an error, a digit begins a brand-new calculation.
        val base = if (state.justEvaluated || state.error != null) "" else state.expression

        if (currentNumberDigitCount(base) >= MAX_DIGITS_PER_NUMBER) return state

        // Avoid a leading zero run: "0" then "5" should read "5", not "05".
        val next = if (base == "0") digit.toString() else base + digit
        return InputState(expression = next, justEvaluated = false, error = null)
    }

    private fun appendOperator(state: InputState, type: OperatorType): InputState {
        if (state.error != null) return state
        val base = state.expression

        if (base.isEmpty()) {
            // Only a leading minus makes sense on an empty expression.
            return if (type == OperatorType.SUBTRACT) {
                InputState(expression = type.symbol.toString())
            } else {
                state
            }
        }

        val last = base.last()

        // Replace a trailing operator rather than stacking two, except when the new
        // operator is a minus that can legitimately act as a sign, as in "5*-".
        if (isOperatorChar(last)) {
            val isSignPosition = type == OperatorType.SUBTRACT &&
                (last == OperatorType.MULTIPLY.symbol || last == OperatorType.DIVIDE.symbol)
            return if (isSignPosition) {
                InputState(expression = base + type.symbol)
            } else {
                InputState(expression = base.dropLast(1) + type.symbol)
            }
        }

        // An operator cannot directly follow an opening parenthesis, apart from a
        // unary minus.
        if (last == '(') {
            return if (type == OperatorType.SUBTRACT) {
                InputState(expression = base + type.symbol)
            } else {
                state
            }
        }

        return InputState(expression = base + type.symbol, justEvaluated = false)
    }

    private fun appendDecimal(state: InputState): InputState {
        val base = if (state.justEvaluated || state.error != null) "" else state.expression

        // Starting a number with a decimal point should produce "0.".
        if (base.isEmpty() || isOperatorChar(base.last()) || base.last() == '(') {
            return InputState(expression = base + "0" + Lexer.DECIMAL_POINT)
        }
        // Only one decimal point per number.
        if (currentNumberHasDecimalPoint(base)) return state
        // A decimal point after a percent or a closing bracket is not meaningful.
        if (base.last() == '%' || base.last() == ')') return state

        return InputState(expression = base + Lexer.DECIMAL_POINT, justEvaluated = false)
    }

    private fun appendPercent(state: InputState): InputState {
        if (state.error != null) return state
        val base = state.expression
        if (base.isEmpty()) return state

        // Percent only makes sense straight after a complete operand.
        val last = base.last()
        if (!last.isDigit() && last != ')') return state

        return InputState(expression = base + "%", justEvaluated = false)
    }

    private fun appendParenthesis(state: InputState): InputState {
        val base = if (state.error != null) "" else state.expression
        val open = base.count { it == '(' }
        val close = base.count { it == ')' }

        val lastChar = base.lastOrNull()
        val lastEndsOperand = lastChar != null &&
            (lastChar.isDigit() || lastChar == ')' || lastChar == '%')

        return if (open > close && lastEndsOperand) {
            // Close the group that is currently open.
            InputState(expression = base + ")", justEvaluated = false)
        } else if (lastEndsOperand) {
            // "5(" implies multiplication, which is what a calculator does here.
            InputState(
                expression = base + OperatorType.MULTIPLY.symbol + "(",
                justEvaluated = false,
            )
        } else {
            InputState(expression = base + "(", justEvaluated = false)
        }
    }

    private fun backspace(state: InputState): InputState {
        // Backspacing out of an error or a result clears, rather than editing digits
        // of a value the user never typed.
        if (state.error != null || state.justEvaluated) return InputState.Empty
        if (state.expression.isEmpty()) return state
        return InputState(expression = state.expression.dropLast(1))
    }

    private fun equals(state: InputState): InputState {
        if (state.expression.isEmpty()) return state

        return when (val result = CalculatorEngine.evaluate(state.expression)) {
            is CalcResult.Success ->
                InputState(
                    expression = result.value.toPlainString(),
                    justEvaluated = true,
                    error = null,
                )

            is CalcResult.Failure ->
                state.copy(justEvaluated = false, error = result.error)
        }
    }

    private fun isOperatorChar(char: Char): Boolean = OperatorType.fromSymbol(char) != null

    /** Digits typed so far in the number currently being entered. */
    private fun currentNumberDigitCount(expression: String): Int = expression.takeLastWhile {
        it.isDigit() ||
            it == Lexer.DECIMAL_POINT
    }
        .count { it.isDigit() }

    private fun currentNumberHasDecimalPoint(expression: String): Boolean = expression.takeLastWhile {
        it.isDigit() ||
            it == Lexer.DECIMAL_POINT
    }
        .contains(Lexer.DECIMAL_POINT)
}
