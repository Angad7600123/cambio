package io.github.angad7600123.cambio.calculator

import java.math.BigDecimal

/**
 * A single lexical unit of a calculator expression.
 *
 * The token model is deliberately closed (a sealed hierarchy) so the parser and
 * evaluator can exhaustively handle every case without a fallback branch.
 */
sealed interface Token {
    /** A literal number, already parsed into an exact [BigDecimal]. */
    data class Number(val value: BigDecimal) : Token

    /** A binary operator such as `+` or `×`. */
    data class Operator(val type: OperatorType) : Token

    /** A unary prefix operator; currently only negation, e.g. the `-` in `-5`. */
    data class UnaryOperator(val type: UnaryOperatorType) : Token

    /**
     * A postfix percent sign. Its meaning depends on the operator it follows,
     * which is resolved during evaluation rather than lexing.
     */
    data object Percent : Token

    data object LeftParen : Token

    data object RightParen : Token
}

enum class OperatorType(val symbol: Char, val precedence: Int) {
    ADD('+', precedence = 1),
    SUBTRACT('-', precedence = 1),
    MULTIPLY('*', precedence = 2),
    DIVIDE('/', precedence = 2),
    ;

    /**
     * All four operators are left-associative: `8 - 3 - 2` is `(8 - 3) - 2`.
     * Exposed explicitly so the shunting-yard step reads declaratively.
     */
    val isLeftAssociative: Boolean get() = true

    companion object {
        fun fromSymbol(symbol: Char): OperatorType? = entries.firstOrNull { it.symbol == symbol }
    }
}

enum class UnaryOperatorType {
    NEGATE,
}
