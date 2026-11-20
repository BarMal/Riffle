package com.riffle.core.domain.launcher.search.wider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculatorTest {
    private fun calc(
        text: String,
        symbols: NumberSymbols = NumberSymbols.DOT_DECIMAL,
    ): String? = Calculator.evaluate(text, symbols)?.text

    @Test
    fun evaluatesTheFourOperationsWithPrecedence() {
        assertEquals("7", calc("1+2*3"))
        assertEquals("9", calc("(1+2)*3"))
        assertEquals("2.5", calc("10 / 4"))
        assertEquals("12", calc("3 × 4"))
        assertEquals("5", calc("20 ÷ 4"))
        assertEquals("-1", calc("2-3"))
    }

    @Test
    fun decimalArithmeticIsExact() {
        assertEquals("0.3", calc("0.1+0.2"))
        assertEquals("0.333333333", calc("1/3")!!.take(11))
        assertEquals("0.6666666667", calc("2/3"))
    }

    @Test
    fun powerIsRightAssociativeAndBindsTighterThanUnaryMinus() {
        assertEquals("512", calc("2^3^2"))
        assertEquals("-4", calc("-2^2"))
        assertEquals("0.25", calc("2^-2"))
        assertEquals("1024", calc("2^10"))
        assertEquals("18446744073709551616", calc("2^64"))
    }

    @Test
    fun percentIsAShareAfterPlusAndMinus() {
        assertEquals("220", calc("200+10%"))
        assertEquals("180", calc("200-10%"))
        assertEquals("0.5", calc("50%"))
        assertEquals("20", calc("200*10%"))
    }

    @Test
    fun parenthesesMultiplyImplicitly() {
        assertEquals("14", calc("2(3+4)"))
        assertEquals("21", calc("(1+2)(3+4)"))
        assertEquals("9", calc("(1+2)3"))
    }

    @Test
    fun readsTheLocalesDecimalSeparator() {
        assertEquals("7", calc("3.5*2"))
        assertEquals("7", calc("3,5*2", NumberSymbols.COMMA_DECIMAL))
        assertEquals("3,5", calc("1,75*2", NumberSymbols.COMMA_DECIMAL))
        assertEquals("1000", calc("1.000*1", NumberSymbols.COMMA_DECIMAL))
        assertEquals("1000", calc("1,000*1", NumberSymbols.DOT_DECIMAL))
        // The other character is not a decimal point.
        assertNull(calc("3,5*2", NumberSymbols.DOT_DECIMAL))
        assertNull(calc("3.5*2", NumberSymbols.COMMA_DECIMAL))
    }

    @Test
    fun divisionByZeroAndOverflowShowNothing() {
        assertNull(calc("1/0"))
        assertNull(calc("5/(2-2)"))
        assertNull(calc("0^-1"))
        assertNull(calc("10^40"))
        assertNull(calc("9^9^9"))
        assertNull(calc("99999999999*99999999999*99999999999"))
    }

    @Test
    fun incompleteOrMalformedExpressionsShowNothing() {
        assertNull(calc("2+"))
        assertNull(calc("(2+3"))
        assertNull(calc("2+3)"))
        assertNull(calc("2++"))
        assertNull(calc("*3"))
        assertNull(calc("2 3"))
        assertNull(calc("1..2+3"))
        assertNull(calc("2+abc"))
    }

    @Test
    fun aPlainNumberDoesNotTrigger() {
        assertNull(calc("42"))
        assertNull(calc("3.14"))
        assertNull(calc("-5"))
        assertNull(calc("(42)"))
        assertNull(calc("2048"))
        assertNull(calc("1,000"))
    }

    @Test
    fun aPhoneNumberDoesNotTrigger() {
        assertNull(calc("555-1234"))
        assertNull(calc("+1 555 123 4567"))
        assertNull(calc("(555) 123-4567"))
        assertNull(calc("555 1234"))
    }

    @Test
    fun aDateDoesNotTrigger() {
        assertNull(calc("12/05/2024"))
        assertNull(calc("2024-05-12"))
        assertNull(calc("12.05.2024"))
        assertNull(calc("12.05.2024", NumberSymbols.COMMA_DECIMAL))
        assertNull(calc("5-12-24"))
        assertNull(calc("12:30"))
    }

    @Test
    fun anAppNameDoesNotTrigger() {
        listOf("7-Zip", "Wi-Fi", "K-9 Mail", "2048", "1Password", "Calc 2", "G-Cal", "Mail-2").forEach { name ->
            assertNull(calc(name), name)
        }
    }

    @Test
    fun aVersionOrAddressDoesNotTrigger() {
        assertNull(calc("1.2.3"))
        assertNull(calc("192.168.1.1"))
    }

    @Test
    fun aLongQueryIsIgnored() {
        assertNull(calc("1+".repeat(60) + "1"))
    }
}
