package com.riffle.core.domain.launcher.search.wider

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** The decimal and grouping characters of the user's locale, so "3,5" reads as 3.5 where the comma is the point. */
data class NumberSymbols(
    val decimal: Char = '.',
    val grouping: Char = ',',
) {
    companion object {
        val DOT_DECIMAL = NumberSymbols('.', ',')
        val COMMA_DECIMAL = NumberSymbols(',', '.')
    }
}

/** An arithmetic answer: the [value] and the [text] the search row shows and copies (in the locale's separators). */
data class CalculatorOutcome(
    val value: BigDecimal,
    val text: String,
)

/**
 * The search's calculator: `+ - × * / ÷ ^ %`, parentheses, decimals in the locale's separator and implicit
 * multiplication by parentheses ("2(3+4)"). Pure and decimal: 0.1 + 0.2 is exactly 0.3. A query that is only a number,
 * looks like a phone number or a date, has a letter in it, divides by zero or overflows gives null (nothing shown).
 *
 * Percent: a postfix `%` divides by 100; after `+` or `-` it is that share of the left side ("200 + 10%" is 220).
 */
object Calculator {
    private const val MAX_LENGTH = 80
    private const val MAX_DEPTH = 24
    private const val MAX_POWER = 1000
    private const val PHONE_DIGITS = 7
    private val limit = BigDecimal.TEN.pow(MAX_DIGITS)
    private val mathContext = MathContext(INTERNAL_PRECISION, RoundingMode.HALF_EVEN)
    private val dateShape = Regex("""^\d{1,4}[./-]\d{1,2}[./-](\d{2}|\d{4})$""")
    private val phoneChars = Regex("""^[0-9+\-() .]+$""")

    fun evaluate(
        input: String,
        symbols: NumberSymbols = NumberSymbols.DOT_DECIMAL,
    ): CalculatorOutcome? {
        val text = normalise(input)
        val plausible =
            text.length in 3..MAX_LENGTH &&
                text.any { char -> char in "+-*/^%" || char == '(' } &&
                text.any(Char::isDigit) &&
                !dateShape.matches(text.replace(" ", "")) &&
                !looksLikePhoneNumber(text)
        val value = if (plausible) runCatching { Parser(text, symbols).parse() }.getOrNull() else null
        return value?.let { CalculatorOutcome(it, format(it, symbols)) }
    }

    private fun normalise(input: String): String =
        input.trim().removePrefix("=").trim()
            .replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-')

    private fun looksLikePhoneNumber(text: String): Boolean =
        phoneChars.matches(text) && text.count(Char::isDigit) >= PHONE_DIGITS && text.none { it == '*' || it == '/' }

    /** The digits a person wants to read: whole numbers exact, others to ten places, never scientific. */
    internal fun format(
        value: BigDecimal,
        symbols: NumberSymbols,
    ): String {
        val stripped = value.stripTrailingZeros()
        val shown =
            if (stripped.scale() <= 0) {
                stripped
            } else {
                val rounded = value.setScale(DISPLAY_PLACES, RoundingMode.HALF_UP).stripTrailingZeros()
                if (rounded.signum() == 0) value.round(MathContext(DISPLAY_PLACES, RoundingMode.HALF_UP)) else rounded
            }
        val plain = (if (shown.signum() == 0) BigDecimal.ZERO else shown).toPlainString()
        return plain.replace('.', symbols.decimal)
    }

    private class Failure : RuntimeException(null, null, false, false)

    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token

        data class Op(val char: Char) : Token
    }

    @Suppress("TooManyFunctions")
    private class Parser(text: String, private val symbols: NumberSymbols) {
        private val tokens = tokenise(text)
        private var position = 0
        private var depth = 0
        private var operations = 0

        fun parse(): BigDecimal {
            val result = expression().value
            if (position != tokens.size || operations == 0) throw Failure()
            return check(result)
        }

        private class Term(val value: BigDecimal, val bareShare: Boolean = false)

        private fun peekOp(): Char? = (tokens.getOrNull(position) as? Token.Op)?.char

        private fun expression(): Term {
            var left = term()
            while (peekOp() == '+' || peekOp() == '-') {
                val op = (tokens[position++] as Token.Op).char
                val right = term()
                operations++
                val amount = if (right.bareShare) left.value.multiply(right.value, mathContext) else right.value
                val sum =
                    if (op == '+') {
                        left.value.add(
                            amount,
                            mathContext,
                        )
                    } else {
                        left.value.subtract(amount, mathContext)
                    }
                left = Term(check(sum))
            }
            return left
        }

        private fun term(): Term {
            var left = signed()
            while (true) {
                val next = tokens.getOrNull(position)
                val explicit = (next as? Token.Op)?.char?.takeIf { it == '*' || it == '/' }
                val implicit = explicit == null && (next as? Token.Op)?.char == '('
                val afterParen = implicit || (next is Token.Num && tokens.getOrNull(position - 1) == Token.Op(')'))
                if (explicit == null && !afterParen) return left
                if (explicit != null) position++
                val right = signed()
                operations++
                left =
                    Term(
                        check(
                            when (explicit) {
                                '/' -> {
                                    if (right.value.signum() == 0) throw Failure()
                                    left.value.divide(right.value, mathContext)
                                }
                                else -> left.value.multiply(right.value, mathContext)
                            },
                        ),
                    )
            }
        }

        private fun signed(): Term {
            val op = peekOp()
            return if (op == '-' || op == '+') {
                position++
                val inner = signed()
                if (op == '-') Term(inner.value.negate()) else inner
            } else {
                power()
            }
        }

        private fun power(): Term {
            val base = postfix()
            if (peekOp() != '^') return base
            position++
            operations++
            val exponent = signed()
            return Term(check(raise(base.value, exponent.value)))
        }

        private fun postfix(): Term {
            var term = primary()
            var share = false
            while (peekOp() == '%') {
                position++
                operations++
                term = Term(term.value.divide(HUNDRED, mathContext))
                share = true
            }
            return if (share) Term(term.value, bareShare = true) else term
        }

        @Suppress("ThrowsCount")
        private fun primary(): Term {
            val token = tokens.getOrNull(position++) ?: throw Failure()
            return when {
                token is Token.Num -> Term(token.value)
                token == Token.Op('(') -> {
                    if (++depth > MAX_DEPTH) throw Failure()
                    val inner = expression()
                    if (tokens.getOrNull(position++) != Token.Op(')')) throw Failure()
                    depth--
                    Term(inner.value)
                }
                else -> throw Failure()
            }
        }

        private fun raise(
            base: BigDecimal,
            exponent: BigDecimal,
        ): BigDecimal {
            val whole = exponent.stripTrailingZeros().scale() <= 0
            val power = if (whole && exponent.abs() <= BigDecimal(MAX_POWER)) exponent.toInt() else null
            return when {
                power != null -> integerPower(base, power)
                else -> {
                    val result = Math.pow(base.toDouble(), exponent.toDouble())
                    if (result.isNaN() || result.isInfinite()) throw Failure()
                    BigDecimal(result, mathContext)
                }
            }
        }

        private fun integerPower(
            base: BigDecimal,
            power: Int,
        ): BigDecimal {
            if (base.signum() == 0) {
                if (power < 0) throw Failure()
                return if (power == 0) BigDecimal.ONE else BigDecimal.ZERO
            }
            if (Math.log10(base.abs().toDouble()) * Math.abs(power) > MAX_DIGITS) throw Failure()
            val positive = base.pow(Math.abs(power), mathContext)
            return if (power < 0) BigDecimal.ONE.divide(positive, mathContext) else positive
        }

        private fun check(value: BigDecimal): BigDecimal = if (value.abs() >= limit) throw Failure() else value

        private fun tokenise(text: String): List<Token> {
            val out = mutableListOf<Token>()
            var index = 0
            while (index < text.length) {
                val char = text[index]
                when {
                    char.isWhitespace() -> index++
                    char.isDigit() || char == symbols.decimal -> {
                        var end = index
                        while (end < text.length && isNumberChar(text[end])) end++
                        out += Token.Num(number(text.substring(index, end)))
                        index = end
                    }
                    char in "+-*/^%()" -> {
                        out += Token.Op(char)
                        index++
                    }
                    else -> throw Failure()
                }
            }
            return out
        }

        private fun isNumberChar(char: Char): Boolean =
            char.isDigit() || char == symbols.decimal || char == symbols.grouping

        private fun number(run: String): BigDecimal {
            val parts = run.split(symbols.decimal)
            if (parts.size > 2) throw Failure()
            val whole = parts[0]
            val groups = whole.split(symbols.grouping)
            val grouped = groups.size > 1
            val valid =
                groups.all { group -> group.all(Char::isDigit) } &&
                    (!grouped || (groups[0].length in 1..3 && groups.drop(1).all { it.length == 3 })) &&
                    (parts.size == 1 || parts[1].all(Char::isDigit)) &&
                    (parts.size == 1 || !parts[1].contains(symbols.grouping))
            if (!valid || (whole.isEmpty() && parts.getOrNull(1).isNullOrEmpty())) throw Failure()
            val digits = groups.joinToString("") + (if (parts.size == 2) "." + parts[1] else "")
            return BigDecimal(if (digits.startsWith(".")) "0$digits" else digits)
        }
    }

    private const val MAX_DIGITS = 30
    private const val INTERNAL_PRECISION = 34
    private const val DISPLAY_PLACES = 10
    private val HUNDRED = BigDecimal(100)
}
