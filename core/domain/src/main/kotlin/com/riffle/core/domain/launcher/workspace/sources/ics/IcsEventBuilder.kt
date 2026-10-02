package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** One `VEVENT` read on its own: a master, or (with [recurrenceId]) an override or standalone instance. */
internal class IcsVevent(
    val event: IcsEvent,
    val recurrenceId: LocalDateTime?,
    val cancelled: Boolean,
    val unknownZone: Boolean,
)

/** Turns the top-level properties of one `VEVENT` into an [IcsVevent]; null when it is unusable. */
internal class IcsEventBuilder(
    private val deviceZone: ZoneId,
    private val calendarZone: ZoneId?,
) {
    @Suppress("ReturnCount")
    fun build(props: List<IcsProperty>): IcsVevent? {
        val startProp = props.firstOrNull { it.name == "DTSTART" } ?: return null
        val startTime = IcsValues.time(startProp.value) ?: return null
        if (props.any { it.name == "RECURRENCE-ID" && it.params["RANGE"].equals("THISANDFUTURE", true) }) return null
        val allDay = startTime.dateOnly || startProp.params["VALUE"].equals("DATE", true)
        val tzid = startProp.params["TZID"]
        val explicit = if (startTime.utc) ZoneOffset.UTC else IcsValues.zone(tzid)
        val unknownZone = !allDay && !startTime.utc && tzid != null && explicit == null
        val zone = if (allDay) null else explicit ?: calendarZone.takeUnless { unknownZone }
        val start =
            if (allDay) startTime.local.toLocalDate().atStartOfDay() else toWall(startTime, explicit, zone, null)
        val title = props.text("SUMMARY").orEmpty()
        val event =
            IcsEvent(
                uid = uidOf(props, title, start),
                title = title,
                location = props.text("LOCATION")?.takeIf(String::isNotEmpty),
                start = start,
                zone = zone,
                allDay = allDay,
                duration = durationOf(props, start, zone, allDay),
                rrule = props.firstOrNull { it.name == "RRULE" }?.value?.trim()?.takeIf(::validRule),
                exDates = dates(props, "EXDATE", zone, start, allDay, IcsLimits.MAX_EX_DATES),
                rDates = dates(props, "RDATE", zone, start, allDay, IcsLimits.MAX_R_DATES),
                isPrivate = props.text("CLASS")?.uppercase() in PRIVATE_CLASSES,
            )
        val recurrenceId = props.firstOrNull { it.name == "RECURRENCE-ID" }?.let { wall(it, zone, start, allDay) }
        val cancelled = props.text("STATUS").equals("CANCELLED", ignoreCase = true)
        return IcsVevent(event, recurrenceId, cancelled, unknownZone)
    }

    private fun validRule(rule: String): Boolean = rule.isNotEmpty() && rule.length <= IcsLimits.MAX_RRULE_CHARS

    private fun List<IcsProperty>.text(name: String): String? =
        firstOrNull { it.name == name }?.let { IcsContentLines.text(it.value) }

    private fun uidOf(
        props: List<IcsProperty>,
        title: String,
        start: LocalDateTime,
    ): String {
        val raw = props.text("UID")?.take(MAX_UID_CHARS)?.takeIf(String::isNotEmpty)
        return raw ?: "no-uid-" + Integer.toHexString((title + start).hashCode())
    }

    /** The wall-clock reading of [time] in the event's [zone] (the device zone when the event is floating). */
    private fun toWall(
        time: IcsTime,
        own: ZoneId?,
        zone: ZoneId?,
        startOfEvent: LocalDateTime?,
    ): LocalDateTime {
        val target = zone ?: deviceZone
        val source = if (time.utc) ZoneOffset.UTC else own
        val local =
            if (time.dateOnly && startOfEvent != null) {
                time.local.toLocalDate().atTime(startOfEvent.toLocalTime())
            } else {
                time.local
            }
        return if (source == null || source == target) {
            local
        } else {
            local.atZone(source).withZoneSameInstant(target).toLocalDateTime()
        }
    }

    private fun wall(
        prop: IcsProperty,
        zone: ZoneId?,
        start: LocalDateTime,
        allDay: Boolean,
    ): LocalDateTime? {
        val time = IcsValues.time(prop.value.substringBefore('/')) ?: return null
        val own = if (time.utc) ZoneOffset.UTC else IcsValues.zone(prop.params["TZID"])
        return if (allDay) time.local.toLocalDate().atStartOfDay() else toWall(time, own, zone, start)
    }

    private fun dates(
        props: List<IcsProperty>,
        name: String,
        zone: ZoneId?,
        start: LocalDateTime,
        allDay: Boolean,
        max: Int,
    ): Set<LocalDateTime> {
        val out = LinkedHashSet<LocalDateTime>()
        for (prop in props) {
            if (prop.name != name) continue
            for (part in prop.value.split(',')) {
                if (out.size >= max) return out
                wall(IcsProperty(prop.name, prop.params, part.trim()), zone, start, allDay)?.let(out::add)
            }
        }
        return out
    }

    private fun durationOf(
        props: List<IcsProperty>,
        start: LocalDateTime,
        zone: ZoneId?,
        allDay: Boolean,
    ): Duration {
        val endProp = props.firstOrNull { it.name == "DTEND" }
        val explicit = props.firstOrNull { it.name == "DURATION" }?.let { IcsValues.duration(it.value) }
        val raw = endProp?.let { span(it, start, zone, allDay) } ?: explicit
        val limit = Duration.ofDays(IcsLimits.MAX_DURATION_DAYS)
        return if (allDay) {
            Duration.ofDays((raw?.toDays() ?: 1L).coerceIn(1L, IcsLimits.MAX_DURATION_DAYS))
        } else {
            (raw ?: Duration.ZERO).coerceIn(Duration.ZERO, limit)
        }
    }

    private fun span(
        endProp: IcsProperty,
        start: LocalDateTime,
        zone: ZoneId?,
        allDay: Boolean,
    ): Duration? {
        val end = IcsValues.time(endProp.value) ?: return null
        return if (allDay) {
            Duration.ofDays(ChronoUnit.DAYS.between(start.toLocalDate(), end.local.toLocalDate()))
        } else {
            val own = if (end.utc) ZoneOffset.UTC else IcsValues.zone(endProp.params["TZID"])
            val endWall = toWall(end, own, zone, start)
            Duration.between(start.toInstantIn(zone ?: deviceZone), endWall.toInstantIn(zone ?: deviceZone))
        }
    }

    private fun Duration.coerceIn(
        min: Duration,
        max: Duration,
    ): Duration =
        when {
            this < min -> min
            this > max -> max
            else -> this
        }

    private companion object {
        const val MAX_UID_CHARS = 128
        val PRIVATE_CLASSES = setOf("PRIVATE", "CONFIDENTIAL")
    }
}
