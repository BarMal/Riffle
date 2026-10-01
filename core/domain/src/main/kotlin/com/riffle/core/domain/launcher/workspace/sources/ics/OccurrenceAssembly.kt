package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Resolves a wall-clock time to an instant with RFC 5545 section 3.3.5 semantics: a time skipped by a DST
 * gap is read with the offset before the gap (so 02:30 on spring-forward day is 03:30 local), and an
 * ambiguous time in a fall-back overlap is the first occurrence.
 */
fun LocalDateTime.toInstantIn(zone: ZoneId): Instant = ZonedDateTime.ofLocal(this, zone, null).toInstant()

/** The local-time range a rule generator must cover so every instant of [window] in [zone] is reachable. */
fun RecurrenceRequest.localSearchRange(): ClosedRange<LocalDateTime> {
    // Offsets never exceed a day, so a two-day pad on each side covers any gap/overlap shift.
    val from = LocalDateTime.ofInstant(window.from, zone).minus(SEARCH_PAD)
    val to = LocalDateTime.ofInstant(window.to, zone).plus(SEARCH_PAD)
    return from..to
}

private val SEARCH_PAD: Duration = Duration.ofDays(2)

/**
 * The library-independent half of expansion. [ruleStarts] are the wall-clock starts the `RRULE` generated
 * (including [RecurrenceRequest.dtStart] itself, which RFC 5545 always includes), covering at least
 * [localSearchRange]. This applies, in order: `RDATE` union, `EXDATE` removal, overrides (an overridden
 * original is removed, a non-cancelled replacement is added), conversion to instants, window filtering,
 * ordering, de-duplication and the instance cap.
 */
fun RecurrenceRequest.assemble(ruleStarts: Collection<LocalDateTime>): RecurrenceResult.Ok {
    val overridden = overrides.associateBy { it.recurrenceId }
    val originals = LinkedHashSet<LocalDateTime>(ruleStarts)
    originals += rDates
    originals += dtStart
    originals -= exDates

    val candidates = ArrayList<Occurrence>(originals.size + overrides.size)
    for (original in originals) {
        val override = overridden[original]
        if (override == null) {
            candidates += Occurrence(original.toInstantIn(zone), original)
        }
    }
    for (override in overrides) {
        val replacement = override.replacementStart
        if (replacement != null && override.recurrenceId !in exDates) {
            candidates += Occurrence(replacement.toInstantIn(zone), override.recurrenceId)
        }
    }

    val inWindow =
        candidates
            .filter { it.start in window }
            .distinctBy { it.start to it.originalStart }
            .sortedWith(compareBy<Occurrence> { it.start }.thenBy { it.originalStart })
    return RecurrenceResult.Ok(
        occurrences = inWindow.take(maxInstances),
        truncated = inWindow.size > maxInstances,
    )
}
