package io.github.angad7600123.cambio.calculator

/**
 * The immutable state of what the user has typed.
 *
 * @property expression the canonical expression string (internal alphabet).
 * @property cursor the caret position, as an index into [expression]. Everything is
 *   inserted and deleted here rather than at the end, which is what lets a digit be
 *   fixed in the middle of a long number instead of retyping the tail.
 * @property justEvaluated true immediately after `=`, which changes what the next
 *   keypress means: a digit starts a fresh calculation, an operator continues from
 *   the result.
 * @property error set when the last evaluation failed.
 */
data class InputState(
    val expression: String = "",
    val cursor: Int = expression.length,
    val justEvaluated: Boolean = false,
    val error: CalcError? = null,
) {
    val isEmpty: Boolean get() = expression.isEmpty()

    /** The text before the caret; every input rule is decided from its tail. */
    internal val before: String get() = expression.take(cursor.coerceIn(0, expression.length))

    /** The text after the caret, carried along untouched by an insert. */
    internal val after: String get() = expression.drop(cursor.coerceIn(0, expression.length))

    companion object {
        val Empty = InputState()

        /** Builds a state with the caret at the end, the common case. */
        fun atEnd(expression: String, justEvaluated: Boolean = false, error: CalcError? = null) =
            InputState(expression, expression.length, justEvaluated, error)
    }
}

/**
 * A pure state machine mapping a [CalculatorKey] press onto a new [InputState].
 *
 * All the "feels like a real calculator" rules live here — replacing a trailing
 * operator instead of stacking two, refusing a second decimal point in the same
 * number, capping digit entry, and choosing the right parenthesis.
 *
 * Every rule is evaluated against the text *immediately before the caret* rather
 * than the end of the string, so they behave identically whether you are typing at
 * the end or correcting a digit in the middle.
 */
object CalculatorInput {
    /** Digits allowed in a single number literal, matching typical pocket calculators. */
    const val MAX_DIGITS_PER_NUMBER = 15

    /**
     * Digits already in the number the caret sits in, counted exactly as a digit
     * press would count them.
     *
     * Exposed so the UI can say *why* a keypress did nothing. Inferring it from an
     * unchanged expression does not work: pressing `0` on a lone `0` is also a
     * no-op, and reporting a digit limit there would be a lie.
     */
    fun digitsAtCaret(state: InputState): Int {
        val base = if (state.justEvaluated || state.error != null) InputState.Empty else state
        return numberDigitsAround(base)
    }

    fun press(state: InputState, key: CalculatorKey): InputState = when (key) {
        is CalculatorKey.Digit -> insertDigit(state, key.value)
        is CalculatorKey.Operator -> insertOperator(state, key.type)
        CalculatorKey.Decimal -> insertDecimal(state)
        CalculatorKey.Percent -> insertPercent(state)
        CalculatorKey.Parenthesis -> insertParenthesis(state)
        CalculatorKey.Clear -> InputState.Empty
        CalculatorKey.Backspace -> backspace(state)
        CalculatorKey.Equals -> equals(state)
    }

    /** Moves the caret, clamped to the expression. */
    fun moveCursor(state: InputState, position: Int): InputState =
        state.copy(cursor = position.coerceIn(0, state.expression.length))

    /** Inserts [text] at the caret and leaves the caret after it. */
    private fun insert(state: InputState, text: String): InputState = InputState(
        expression = state.before + text + state.after,
        cursor = state.cursor + text.length,
        justEvaluated = false,
        error = null,
    )

    /** Replaces the character before the caret with [text]. */
    private fun replaceBefore(state: InputState, text: String): InputState = InputState(
        expression = state.before.dropLast(1) + text + state.after,
        cursor = state.cursor - 1 + text.length,
        justEvaluated = false,
        error = null,
    )

    private fun insertDigit(state: InputState, digit: Int): InputState {
        // After `=` or an error, a digit begins a brand-new calculation.
        val base = if (state.justEvaluated || state.error != null) InputState.Empty else state
        if (numberDigitsAround(base) >= MAX_DIGITS_PER_NUMBER) return state

        // Avoid a leading zero run: "0" then "5" should read "5", not "05".
        val token = numberTokenBefore(base)
        if (token == "0") return replaceBefore(base, digit.toString())

        return insert(base, digit.toString())
    }

    private fun insertOperator(state: InputState, type: OperatorType): InputState {
        if (state.error != null) return state
        val previous = state.before.lastOrNull()

        if (previous == null) {
            // Only a leading minus makes sense with nothing before the caret.
            return if (type == OperatorType.SUBTRACT) insert(state, type.symbol.toString()) else state
        }

        // Replace a trailing operator rather than stacking two, except when the new
        // operator is a minus that can legitimately act as a sign, as in "5*-".
        if (isOperatorChar(previous)) {
            val isSignPosition = type == OperatorType.SUBTRACT &&
                (previous == OperatorType.MULTIPLY.symbol || previous == OperatorType.DIVIDE.symbol)
            return if (isSignPosition) {
                insert(state, type.symbol.toString())
            } else {
                replaceBefore(state, type.symbol.toString())
            }
        }

        // An operator cannot directly follow an opening parenthesis, apart from a
        // unary minus.
        if (previous == '(') {
            return if (type == OperatorType.SUBTRACT) insert(state, type.symbol.toString()) else state
        }

        return insert(state, type.symbol.toString())
    }

    private fun insertDecimal(state: InputState): InputState {
        val base = if (state.justEvaluated || state.error != null) InputState.Empty else state
        val previous = base.before.lastOrNull()

        // Starting a number with a decimal point should produce "0.".
        if (previous == null || isOperatorChar(previous) || previous == '(') {
            return insert(base, "0" + Lexer.DECIMAL_POINT)
        }
        // Only one decimal point per number.
        if (numberTokenAround(base).contains(Lexer.DECIMAL_POINT)) return state
        // A decimal point after a percent or a closing bracket is not meaningful.
        if (previous == '%' || previous == ')') return state

        return insert(base, Lexer.DECIMAL_POINT.toString())
    }

    private fun insertPercent(state: InputState): InputState {
        if (state.error != null) return state
        // Percent only makes sense straight after a complete operand.
        val previous = state.before.lastOrNull() ?: return state
        if (!previous.isDigit() && previous != ')') return state
        return insert(state, "%")
    }

    private fun insertParenthesis(state: InputState): InputState {
        val base = if (state.error != null) InputState.Empty else state
        val open = base.expression.count { it == '(' }
        val close = base.expression.count { it == ')' }

        val previous = base.before.lastOrNull()
        val endsOperand = previous != null && (previous.isDigit() || previous == ')' || previous == '%')

        return when {
            open > close && endsOperand -> insert(base, ")")
            // "5(" implies multiplication, which is what a calculator does here.
            endsOperand -> insert(base, OperatorType.MULTIPLY.symbol + "(")
            else -> insert(base, "(")
        }
    }

    private fun backspace(state: InputState): InputState {
        // Backspacing out of an error or a result clears, rather than editing digits
        // of a value the user never typed.
        if (state.error != null || state.justEvaluated) return InputState.Empty
        if (state.cursor == 0) return state

        return InputState(
            expression = state.before.dropLast(1) + state.after,
            cursor = state.cursor - 1,
        )
    }

    private fun equals(state: InputState): InputState {
        if (state.expression.isEmpty()) return state
        // A result is already final: equals has nothing left to do with it. Without
        // this, pressing equals on `5` re-evaluates `5` and calls the answer a fresh
        // calculation, which put duplicate entries in the history.
        if (state.justEvaluated) return state

        return when (val result = CalculatorEngine.evaluate(state.expression)) {
            is CalcResult.Success ->
                InputState.atEnd(result.value.toPlainString(), justEvaluated = true)

            is CalcResult.Failure ->
                state.copy(justEvaluated = false, error = result.error)
        }
    }

    private fun isOperatorChar(char: Char): Boolean = OperatorType.fromSymbol(char) != null

    /** The number literal the caret sits inside or beside. */
    private fun numberTokenAround(state: InputState): String =
        numberTokenBefore(state) + state.after.takeWhile { it.isDigit() || it == Lexer.DECIMAL_POINT }

    private fun numberTokenBefore(state: InputState): String =
        state.before.takeLastWhile { it.isDigit() || it == Lexer.DECIMAL_POINT }

    private fun numberDigitsAround(state: InputState): Int = numberTokenAround(state).count { it.isDigit() }
}
