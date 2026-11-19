package com.riffle.core.domain.launcher.search.wider

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** A converted quantity: what was asked ("12 km") and the answer ("7.456454 mi"), in the locale's separators. */
data class UnitConversionOutcome(
    val input: String,
    val answer: String,
)

/**
 * Simple conversions with fixed factors: length, mass, temperature, volume, speed, data sizes and time. No currencies
 * and no network. "12 km in miles", "70 f to c", "3.5 lb in kg". Anything that is not a number, a unit, the word
 * "in", "to" or "as" and a unit of the same kind gives null.
 */
object UnitConverter {
    private const val SHOWN_DIGITS = 7
    private const val MAX_LENGTH = 60
    private val separators = setOf("in", "to", "as", "into", "=", "->", "→")
    private val numberThenUnit = Regex("""^(-?[0-9][0-9.,]*|-?[.,][0-9]+)\s*(.+)$""")

    fun convert(
        input: String,
        symbols: NumberSymbols = NumberSymbols.DOT_DECIMAL,
    ): UnitConversionOutcome? {
        val text = input.trim().lowercase().replace('−', '-')
        val match = if (text.length <= MAX_LENGTH) numberThenUnit.matchEntire(text) else null
        val amount = match?.let { parseAmount(it.groupValues[1], symbols) }
        val words = match?.groupValues?.get(2)?.split(Regex("\\s+"))?.filter(String::isNotEmpty).orEmpty()
        return if (amount == null) null else resolve(amount, match.groupValues[1], words, symbols)
    }

    private fun resolve(
        amount: BigDecimal,
        amountText: String,
        words: List<String>,
        symbols: NumberSymbols,
    ): UnitConversionOutcome? {
        for (split in words.indices.reversed()) {
            if (split == 0 || split == words.lastIndex || words[split] !in separators) continue
            val from = unitOf(words.subList(0, split).joinToString(" "))
            val to = unitOf(words.subList(split + 1, words.size).joinToString(" "))
            if (from != null && to != null && from.dimension == to.dimension) {
                val value = to.fromBase(from.toBase(amount))
                val answer = Calculator.format(shown(value), symbols)
                return UnitConversionOutcome(
                    input = "${amountText.trim().replace('.', symbols.decimal)} ${from.symbol}",
                    answer = "$answer ${to.symbol}",
                ).takeIf { value.abs() < BigDecimal.TEN.pow(MAX_DIGITS) }
            }
        }
        return null
    }

    private fun shown(value: BigDecimal): BigDecimal =
        if (value.abs() < NEGLIGIBLE) BigDecimal.ZERO else value.round(MathContext(SHOWN_DIGITS, RoundingMode.HALF_UP))

    private fun parseAmount(
        text: String,
        symbols: NumberSymbols,
    ): BigDecimal? {
        val cleaned = text.replace(symbols.grouping.toString(), "")
        val plain = cleaned.replace(symbols.decimal, '.')
        val shape = plain.count { it == '.' } <= 1 && plain.none { it == ',' } && plain.any(Char::isDigit)
        // "1,5" where the comma is only a grouping mark is not a number.
        val groupingOk =
            !text.contains(symbols.grouping) ||
                Regex("""^-?\d{1,3}(\Q${symbols.grouping}\E\d{3})+""").containsMatchIn(text)
        return if (shape && groupingOk) runCatching { BigDecimal(plain.removePrefix("+")) }.getOrNull() else null
    }

    private const val MAX_DIGITS = 30
    private val NEGLIGIBLE = BigDecimal("0.000000001")

    private enum class Dimension { LENGTH, MASS, TEMPERATURE, VOLUME, SPEED, DATA, TIME }

    private class UnitDef(
        val dimension: Dimension,
        val symbol: String,
        private val factor: BigDecimal,
        private val offset: BigDecimal = BigDecimal.ZERO,
    ) {
        fun toBase(value: BigDecimal): BigDecimal = value.multiply(factor, MathContext(34)).add(offset)

        fun fromBase(value: BigDecimal): BigDecimal = value.subtract(offset).divide(factor, MathContext(34))
    }

    private fun def(
        dimension: Dimension,
        symbol: String,
        factor: String,
        vararg names: String,
    ): Map<String, UnitDef> {
        val unit = UnitDef(dimension, symbol, BigDecimal(factor))
        return (names.toList() + symbol.lowercase()).associateWith { unit }
    }

    private val FIVE_NINTHS = BigDecimal("0.5555555555555555555555555555555556")
    private val FAHRENHEIT_OFFSET = BigDecimal("255.3722222222222222222222222222222")

    private val units: Map<String, UnitDef> =
        buildMap {
            // Length (metre).
            putAll(def(Dimension.LENGTH, "mm", "0.001", "millimeter", "millimeters", "millimetre", "millimetres"))
            putAll(def(Dimension.LENGTH, "cm", "0.01", "centimeter", "centimeters", "centimetre", "centimetres"))
            putAll(def(Dimension.LENGTH, "m", "1", "meter", "meters", "metre", "metres"))
            putAll(def(Dimension.LENGTH, "km", "1000", "kilometer", "kilometers", "kilometre", "kilometres"))
            putAll(def(Dimension.LENGTH, "in", "0.0254", "inch", "inches", "\""))
            putAll(def(Dimension.LENGTH, "ft", "0.3048", "foot", "feet", "'"))
            putAll(def(Dimension.LENGTH, "yd", "0.9144", "yard", "yards"))
            putAll(def(Dimension.LENGTH, "mi", "1609.344", "mile", "miles"))
            putAll(def(Dimension.LENGTH, "nmi", "1852", "nautical mile", "nautical miles"))
            // Mass (kilogram).
            putAll(def(Dimension.MASS, "mg", "0.000001", "milligram", "milligrams"))
            putAll(def(Dimension.MASS, "g", "0.001", "gram", "grams"))
            putAll(def(Dimension.MASS, "kg", "1", "kilogram", "kilograms", "kilo", "kilos"))
            putAll(def(Dimension.MASS, "oz", "0.028349523125", "ounce", "ounces"))
            putAll(def(Dimension.MASS, "lb", "0.45359237", "lbs", "pound", "pounds"))
            putAll(def(Dimension.MASS, "st", "6.35029318", "stone", "stones"))
            putAll(def(Dimension.MASS, "tonne", "1000", "tonnes", "metric ton", "metric tons"))
            // Volume (litre; US customary).
            putAll(def(Dimension.VOLUME, "ml", "0.001", "milliliter", "milliliters", "millilitre", "millilitres"))
            putAll(def(Dimension.VOLUME, "cl", "0.01", "centiliter", "centiliters", "centilitre", "centilitres"))
            putAll(def(Dimension.VOLUME, "l", "1", "liter", "liters", "litre", "litres"))
            putAll(def(Dimension.VOLUME, "tsp", "0.00492892159375", "teaspoon", "teaspoons"))
            putAll(def(Dimension.VOLUME, "tbsp", "0.01478676478125", "tablespoon", "tablespoons"))
            putAll(def(Dimension.VOLUME, "fl oz", "0.0295735295625", "floz", "fluid ounce", "fluid ounces"))
            putAll(def(Dimension.VOLUME, "cup", "0.2365882365", "cups"))
            putAll(def(Dimension.VOLUME, "pt", "0.473176473", "pint", "pints"))
            putAll(def(Dimension.VOLUME, "qt", "0.946352946", "quart", "quarts"))
            putAll(def(Dimension.VOLUME, "gal", "3.785411784", "gallon", "gallons"))
            // Speed (metre per second).
            putAll(def(Dimension.SPEED, "m/s", "1", "mps", "meters per second", "metres per second"))
            putAll(def(Dimension.SPEED, "km/h", "0.2777777777777777777777777777777778", "kph", "kmh", "kmph"))
            putAll(def(Dimension.SPEED, "mph", "0.44704", "mi/h", "miles per hour"))
            putAll(def(Dimension.SPEED, "kn", "0.5144444444444444444444444444444444", "knot", "knots", "kt"))
            putAll(def(Dimension.SPEED, "ft/s", "0.3048", "fps"))
            // Data (byte; kilo = 1000, kibi = 1024).
            putAll(def(Dimension.DATA, "bit", "0.125", "bits"))
            putAll(def(Dimension.DATA, "B", "1", "byte", "bytes"))
            putAll(def(Dimension.DATA, "kB", "1000", "kilobyte", "kilobytes"))
            putAll(def(Dimension.DATA, "MB", "1000000", "megabyte", "megabytes"))
            putAll(def(Dimension.DATA, "GB", "1000000000", "gigabyte", "gigabytes"))
            putAll(def(Dimension.DATA, "TB", "1000000000000", "terabyte", "terabytes"))
            putAll(def(Dimension.DATA, "KiB", "1024", "kibibyte", "kibibytes"))
            putAll(def(Dimension.DATA, "MiB", "1048576", "mebibyte", "mebibytes"))
            putAll(def(Dimension.DATA, "GiB", "1073741824", "gibibyte", "gibibytes"))
            putAll(def(Dimension.DATA, "TiB", "1099511627776", "tebibyte", "tebibytes"))
            // Time (second).
            putAll(def(Dimension.TIME, "ms", "0.001", "millisecond", "milliseconds"))
            putAll(def(Dimension.TIME, "s", "1", "sec", "secs", "second", "seconds"))
            putAll(def(Dimension.TIME, "min", "60", "mins", "minute", "minutes"))
            putAll(def(Dimension.TIME, "h", "3600", "hr", "hrs", "hour", "hours"))
            putAll(def(Dimension.TIME, "d", "86400", "day", "days"))
            putAll(def(Dimension.TIME, "wk", "604800", "week", "weeks"))
            // Temperature (kelvin): the offset is applied before the factor is divided out.
            put("c", UnitDef(Dimension.TEMPERATURE, "°C", BigDecimal.ONE, BigDecimal("273.15")))
            put("f", UnitDef(Dimension.TEMPERATURE, "°F", FIVE_NINTHS, FAHRENHEIT_OFFSET))
            put("k", UnitDef(Dimension.TEMPERATURE, "K", BigDecimal.ONE))
            listOf("°c", "celsius", "centigrade", "degc", "deg c").forEach { put(it, getValue("c")) }
            listOf("°f", "fahrenheit", "degf", "deg f").forEach { put(it, getValue("f")) }
            listOf("kelvin", "kelvins").forEach { put(it, getValue("k")) }
        }

    private fun unitOf(name: String): UnitDef? = units[name.trim().trimEnd('.')]
}
