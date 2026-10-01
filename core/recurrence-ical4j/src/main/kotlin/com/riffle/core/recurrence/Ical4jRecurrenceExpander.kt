package com.riffle.core.recurrence

import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceExpander
import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceFailure
import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceRequest
import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceResult
import com.riffle.core.domain.launcher.workspace.sources.ics.assemble
import com.riffle.core.domain.launcher.workspace.sources.ics.localSearchRange
import net.fortuna.ical4j.model.Recur
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * [RecurrenceExpander] backed by ical4j's `Recur` rule engine, and nothing else of ical4j: no calendar
 * parser, no time-zone registry, no `VTIMEZONE`. Rules are generated on wall-clock `LocalDateTime`s and the
 * shared domain code (`assemble`) does `EXDATE`/`RDATE`/override handling and zone resolution with
 * `java.time`, so the library is confined to "which wall-clock starts does this RRULE produce".
 *
 * Fails closed: an unparsable or refused rule is a [RecurrenceResult.Failed] with a code, never an
 * exception and never a message (the rule text is user content).
 */
class Ical4jRecurrenceExpander : RecurrenceExpander {
    override fun expand(request: RecurrenceRequest): RecurrenceResult {
        val rule = request.rrule?.trim().orEmpty()
        if (rule.isEmpty()) return request.assemble(emptyList())
        return when (val generated = generate(rule, request)) {
            is Generated.Starts -> finish(request, generated)
            is Generated.Refused -> RecurrenceResult.Failed(generated.reason)
        }
    }

    /** A rule that filled the library limit cannot be proven complete, so it is reported as truncated. */
    private fun finish(
        request: RecurrenceRequest,
        generated: Generated.Starts,
    ): RecurrenceResult {
        val assembled = request.assemble(generated.starts)
        return if (generated.limitReached) assembled.copy(truncated = true) else assembled
    }

    private fun generate(
        rule: String,
        request: RecurrenceRequest,
    ): Generated =
        when (val normalised = normalise(rule, request.zone)) {
            is Normalised.Refused -> Generated.Refused(normalised.reason)
            is Normalised.Rule -> runRule(normalised.text, request)
        }

    @Suppress("TooGenericExceptionCaught")
    private fun runRule(
        text: String,
        request: RecurrenceRequest,
    ): Generated =
        try {
            val range = request.localSearchRange()
            val recur = Recur<LocalDateTime>(text)
            // The library limit counts dates inside the (padded) search range. Exdates and overrides can
            // still remove some and the pad adds some, so leave room for both.
            val limit = request.maxInstances + request.exDates.size + request.overrides.size + EDGE_ALLOWANCE
            val dates = recur.getDates(request.dtStart, range.start, range.endInclusive, limit)
            Generated.Starts(dates, limitReached = dates.size >= limit)
        } catch (
            // ical4j reports malformed rules with several unchecked types (IllegalArgumentException,
            // DateTimeException, NumberFormatException, ClassCastException on a mismatched UNTIL) and none
            // of them may escape: the expander's contract is "fail closed with a code".
            @Suppress("SwallowedException") e: RuntimeException,
        ) {
            Generated.Refused(RecurrenceFailure.INVALID_RULE)
        }

    private sealed interface Generated {
        data class Starts(val starts: List<LocalDateTime>, val limitReached: Boolean) : Generated

        data class Refused(val reason: RecurrenceFailure) : Generated
    }

    private sealed interface Normalised {
        data class Rule(val text: String) : Normalised

        data class Refused(val reason: RecurrenceFailure) : Normalised
    }

    private fun normalise(
        rule: String,
        zone: ZoneId,
    ): Normalised {
        val parts = rule.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val keys = parts.map { it.substringBefore('=').uppercase() }
        val refusal =
            when {
                rule.length > MAX_RULE_LENGTH || parts.any { '=' !in it } || keys.size != keys.toSet().size ->
                    RecurrenceFailure.INVALID_RULE
                else -> refusalFor(parts)
            }
        if (refusal != null) return Normalised.Refused(refusal)
        val rewritten = parts.mapNotNull { it.rewriteUntil(zone) }
        return if (rewritten.size != parts.size) {
            Normalised.Refused(RecurrenceFailure.INVALID_RULE)
        } else {
            Normalised.Rule(rewritten.joinToString(";"))
        }
    }

    private fun refusalFor(parts: List<String>): RecurrenceFailure? {
        val values = parts.associate { it.substringBefore('=').uppercase() to it.substringAfter('=').uppercase() }
        val frequency = values["FREQ"]
        return when {
            frequency == null || frequency !in KNOWN_FREQUENCIES -> RecurrenceFailure.INVALID_RULE
            frequency in SUB_DAILY -> RecurrenceFailure.UNSUPPORTED_RULE
            values["RSCALE"].let { it != null && it != "GREGORIAN" } -> RecurrenceFailure.UNSUPPORTED_RULE
            else -> null
        }
    }

    /**
     * The generator works on floating wall-clock time, so `UNTIL` is restated that way: a UTC `UNTIL` is
     * converted to [zone], and a date-only `UNTIL` is inclusive of its whole day.
     */
    private fun String.rewriteUntil(zone: ZoneId): String? =
        if (startsWith("UNTIL=", ignoreCase = true)) {
            parseUntil(substringAfter('=').trim().uppercase(), zone)?.let { "UNTIL=${LOCAL_FORMAT.format(it)}" }
        } else {
            this
        }

    private fun parseUntil(
        raw: String,
        zone: ZoneId,
    ): LocalDateTime? =
        try {
            when {
                raw.endsWith("Z") -> LocalDateTime.ofInstant(Instant.from(UTC_FORMAT.parse(raw)), zone)
                'T' in raw -> LocalDateTime.parse(raw, LOCAL_FORMAT)
                else -> LocalDate.parse(raw, DATE_FORMAT).atTime(LocalTime.MAX.withNano(0))
            }
        } catch (_: DateTimeException) {
            null
        }

    private companion object {
        const val MAX_RULE_LENGTH = 1024

        /** Room for generated starts that fall in the two pad days around the window and are filtered out. */
        const val EDGE_ALLOWANCE = 100
        val KNOWN_FREQUENCIES = setOf("SECONDLY", "MINUTELY", "HOURLY", "DAILY", "WEEKLY", "MONTHLY", "YEARLY")
        val SUB_DAILY = setOf("SECONDLY", "MINUTELY", "HOURLY")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        val UTC_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}
