package com.riffle.core.domain.launcher.stream

/**
 * How long ago something was, as the Stream says it ("5 min. ago", "3 h. ago", "2 d. ago"): minutes below an hour, hours
 * below a day, days beyond. The one rule every relative time in the Stream follows (a source card's "Last played", a
 * notification's subtitle, an app's "Last used"): 180 minutes is three hours, never "180 min.". The platform's
 * relative-time formatter follows the same bounds; this is what a fixed clock (a test, a screenshot) uses.
 */
object RelativeAge {
    enum class Unit { MINUTES, HOURS, DAYS }

    data class Age(
        val unit: Unit,
        val count: Long,
    )

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    /** The age of something [elapsedMillis] old (never negative: a time in the future reads as zero minutes). */
    fun of(elapsedMillis: Long): Age {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        return when {
            elapsed < HOUR_MILLIS -> Age(Unit.MINUTES, elapsed / MINUTE_MILLIS)
            elapsed < DAY_MILLIS -> Age(Unit.HOURS, elapsed / HOUR_MILLIS)
            else -> Age(Unit.DAYS, elapsed / DAY_MILLIS)
        }
    }
}
