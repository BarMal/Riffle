package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A parsed `DATE` or `DATE-TIME` value. [utc] is a trailing `Z`; [dateOnly] a `DATE` (read as start of day). */
internal data class IcsTime(
    val local: LocalDateTime,
    val utc: Boolean,
    val dateOnly: Boolean,
)

/** Value parsers for the few iCalendar value types the source reads. All return null instead of throwing. */
internal object IcsValues {
    private const val DATE_LENGTH = 8
    private const val DATE_TIME_LENGTH = 15
    private const val MIN_YEAR = 1970
    private const val MAX_YEAR = 2200
    private const val MAX_ZONE_NAME = 64
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
    private const val DAYS_PER_WEEK = 7L
    private const val MAX_DURATION_PART = 1_000_000L

    fun time(raw: String): IcsTime? {
        val value = raw.trim()
        val utc = value.endsWith("Z") || value.endsWith("z")
        val body = if (utc) value.dropLast(1) else value
        val dateOnly = body.length == DATE_LENGTH
        val valid =
            (dateOnly && !utc && body.all(Char::isAsciiDigit)) ||
                (body.length == DATE_TIME_LENGTH && body[DATE_LENGTH].uppercaseChar() == 'T' && digitsAround(body))
        return if (valid) build(body, utc, dateOnly) else null
    }

    private fun digitsAround(body: String): Boolean =
        body.withIndex().all { (index, char) -> index == DATE_LENGTH || char.isAsciiDigit() }

    private fun build(
        body: String,
        utc: Boolean,
        dateOnly: Boolean,
    ): IcsTime? {
        val year = body.substring(0, 4).toInt()
        if (year !in MIN_YEAR..MAX_YEAR) return null
        return try {
            val date = LocalDate.of(year, body.substring(4, 6).toInt(), body.substring(6, 8).toInt())
            val local =
                if (dateOnly) {
                    date.atStartOfDay()
                } else {
                    // Second 60 (a leap second) is read as 59.
                    date.atTime(
                        body.substring(9, 11).toInt(),
                        body.substring(11, 13).toInt(),
                        body.substring(13, 15).toInt().coerceAtMost(59),
                    )
                }
            IcsTime(local, utc, dateOnly)
        } catch (_: DateTimeException) {
            null
        }
    }

    /** `[+-]P[nW][nD][T[nH][nM][nS]]`, as an absolute length; null when malformed. */
    @Suppress("ReturnCount")
    fun duration(raw: String): Duration? {
        val match = DURATION.matchEntire(raw.trim()) ?: return null
        val groups = match.groupValues
        val parts = (2..6).map { groups[it].ifEmpty { "0" }.toLongOrNull() ?: Long.MAX_VALUE }
        // Absurd components (digit strings far beyond any real event) are malformed, which also rules out overflow.
        if (parts.any { it > MAX_DURATION_PART }) return null
        val total =
            parts[0] * DAYS_PER_WEEK * SECONDS_PER_DAY + parts[1] * SECONDS_PER_DAY +
                parts[2] * SECONDS_PER_HOUR + parts[3] * SECONDS_PER_MINUTE + parts[4]
        return Duration.ofSeconds(if (groups[1] == "-") -total else total)
    }

    /** An IANA zone from a `TZID`, tolerating prefixed names and common Windows names; null when unknown. */
    @Suppress("ReturnCount")
    fun zone(tzid: String?): ZoneId? {
        val name = tzid?.trim()?.trim('"').orEmpty()
        if (name.isEmpty() || name.length > MAX_ZONE_NAME) return null
        if (name.equals("UTC", ignoreCase = true) || name.equals("Z", ignoreCase = true)) return ZoneOffset.UTC
        return candidates(name).firstNotNullOfOrNull { candidate -> runCatching { ZoneId.of(candidate) }.getOrNull() }
    }

    private fun candidates(name: String): Sequence<String> =
        sequence {
            yield(name)
            WINDOWS_ZONES[name.lowercase()]?.let { yield(it) }
            // "/mozilla.org/20070129_1/Europe/Berlin": try every suffix after a slash.
            var index = name.indexOf('/')
            while (index >= 0) {
                yield(name.substring(index + 1))
                index = name.indexOf('/', index + 1)
            }
        }

    private val DURATION = Regex("^([+-]?)P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

    private val WINDOWS_ZONES =
        mapOf(
            "eastern standard time" to "America/New_York",
            "central standard time" to "America/Chicago",
            "mountain standard time" to "America/Denver",
            "pacific standard time" to "America/Los_Angeles",
            "us mountain standard time" to "America/Phoenix",
            "alaskan standard time" to "America/Anchorage",
            "hawaiian standard time" to "Pacific/Honolulu",
            "atlantic standard time" to "America/Halifax",
            "newfoundland standard time" to "America/St_Johns",
            "greenwich standard time" to "Atlantic/Reykjavik",
            "gmt standard time" to "Europe/London",
            "w. europe standard time" to "Europe/Berlin",
            "central europe standard time" to "Europe/Budapest",
            "central european standard time" to "Europe/Warsaw",
            "romance standard time" to "Europe/Paris",
            "e. europe standard time" to "Europe/Chisinau",
            "gtb standard time" to "Europe/Bucharest",
            "fle standard time" to "Europe/Kiev",
            "russian standard time" to "Europe/Moscow",
            "turkey standard time" to "Europe/Istanbul",
            "israel standard time" to "Asia/Jerusalem",
            "south africa standard time" to "Africa/Johannesburg",
            "arab standard time" to "Asia/Riyadh",
            "india standard time" to "Asia/Kolkata",
            "china standard time" to "Asia/Shanghai",
            "singapore standard time" to "Asia/Singapore",
            "tokyo standard time" to "Asia/Tokyo",
            "korea standard time" to "Asia/Seoul",
            "aus eastern standard time" to "Australia/Sydney",
            "new zealand standard time" to "Pacific/Auckland",
            "e. south america standard time" to "America/Sao_Paulo",
        )
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
