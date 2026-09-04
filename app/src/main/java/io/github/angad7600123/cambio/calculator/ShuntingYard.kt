package io.github.angad7600123.cambio.calculator

import java.util.ArrayDeque

/**
 * Converts infix tokens to Reverse Polish Notation using Dijkstra's shunting-yard
 * algorithm.
 *
 * This is what gives the calculator real operator precedence — `2 + 3 × 4` becomes
 * `2 3 4 × +` and evaluates to 14, not 20 — without any string manipulation.
 *
 * Returns `null` when the token stream cannot form a valid expression, which
 * covers mismatched parentheses in either direction.
 */
internal object ShuntingYard {
    fun toRpn(tokens: List<Token>): List<Token>? {
        val output = mutableListOf<Token>()
        val operators = ArrayDeque<Token>()

        for (token in tokens) {
            when (token) {
                is Token.Number -> output += token

                // Percent is postfix and binds tighter than any binary operator, so it
                // goes straight to the output alongside the operand it modifies.
                Token.Percent -> output += token

                is Token.UnaryOperator -> operators.push(token)

                is Token.Operator -> {
                    while (true) {
                        val top = operators.peek() ?: break
                        if (!shouldPopBefore(token.type, top)) break
                        output += operators.pop()
                    }
                    operators.push(token)
                }

                Token.LeftParen -> operators.push(token)

                Token.RightParen -> {
                    var foundLeftParen = false
                    while (operators.isNotEmpty()) {
                        val top = operators.pop()
                        if (top == Token.LeftParen) {
                            foundLeftParen = true
                            break
                        }
                        output += top
                    }
                    if (!foundLeftParen) return null // Unmatched ')'
                    // A unary minus directly wrapping the group, as in "-(2+3)".
                    if (operators.peek() is Token.UnaryOperator) output += operators.pop()
                }
            }
        }

        while (operators.isNotEmpty()) {
            val top = operators.pop()
            if (top == Token.LeftParen) return null // Unmatched '('
            output += top
        }

        return output
    }

    /**
     * Standard precedence rule: pop while the stack top is an operator that must be
     * applied first. Unary negation binds tighter than every binary operator.
     */
    private fun shouldPopBefore(incoming: OperatorType, stackTop: Token): Boolean = when (stackTop) {
        is Token.UnaryOperator -> true
        is Token.Operator ->
            stackTop.type.precedence > incoming.precedence ||
                (stackTop.type.precedence == incoming.precedence && incoming.isLeftAssociative)
        else -> false
    }
}
