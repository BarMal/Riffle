package com.riffle.core.domain.launcher.search.wider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnitConverterTest {
    private fun convert(
        text: String,
        symbols: NumberSymbols = NumberSymbols.DOT_DECIMAL,
    ): String? = UnitConverter.convert(text, symbols)?.let { "${it.input} = ${it.answer}" }

    @Test
    fun convertsLength() {
        assertEquals("12 km = 7.456454 mi", convert("12 km in miles"))
        assertEquals("5 ft = 152.4 cm", convert("5ft to cm"))
        assertEquals("1 mi = 1.609344 km", convert("1 mile to km"))
        assertEquals("12 in = 30.48 cm", convert("12 in in cm"))
    }

    @Test
    fun convertsMass() {
        assertEquals("3.5 lb = 1.587573 kg", convert("3.5 lb in kg"))
        assertEquals("1 kg = 2.204623 lb", convert("1 kg to lbs"))
    }

    @Test
    fun convertsTemperatureThroughKelvin() {
        assertEquals("70 °F = 21.11111 °C", convert("70 f to c"))
        assertEquals("100 °C = 212 °F", convert("100 c in f"))
        assertEquals("32 °F = 0 °C", convert("32 f to c"))
        assertEquals("0 °C = 273.15 K", convert("0 celsius to kelvin"))
        assertEquals("-40 °C = -40 °F", convert("-40 c to f"))
    }

    @Test
    fun convertsVolumeSpeedDataAndTime() {
        assertEquals("1 gal = 3.785412 l", convert("1 gallon to liters"))
        assertEquals("100 km/h = 62.13712 mph", convert("100 kph to mph"))
        assertEquals("1 GB = 1000 MB", convert("1 gb in mb"))
        assertEquals("1 GiB = 1024 MiB", convert("1 gib to mib"))
        assertEquals("2 h = 120 min", convert("2 hours in minutes"))
        assertEquals("1 d = 24 h", convert("1 day to hours"))
    }

    @Test
    fun readsTheLocalesDecimalSeparator() {
        assertEquals("3,5 lb = 1,587573 kg", convert("3,5 lb in kg", NumberSymbols.COMMA_DECIMAL))
        assertNull(convert("3,5 lb in kg", NumberSymbols.DOT_DECIMAL))
    }

    @Test
    fun differentKindsOfUnitDoNotConvert() {
        assertNull(convert("5 km to kg"))
        assertNull(convert("5 c to mi"))
    }

    @Test
    fun thingsThatAreNotConversionsShowNothing() {
        listOf("12 km", "km in miles", "12", "12 widgets in kg", "7-Zip to c", "Wi-Fi", "2048 in", "to c").forEach {
            assertNull(convert(it), it)
        }
    }

    @Test
    fun noCurrencies() {
        assertNull(convert("5 usd to eur"))
        assertNull(convert("5 dollars in euro"))
    }
}
