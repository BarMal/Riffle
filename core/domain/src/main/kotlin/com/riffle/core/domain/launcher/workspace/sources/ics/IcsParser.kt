package com.riffle.core.domain.launcher.workspace.sources.ics

import java.time.ZoneId

/**
 * A bounded RFC 5545 reader for untrusted feed text: `VEVENT` with `RRULE`, `RDATE`, `EXDATE`,
 * `RECURRENCE-ID` overrides, `TZID` (IANA names, common Windows names, prefixed names) and all-day values.
 * It is not a general iCalendar library: other components (`VTODO`, `VJOURNAL`, `VALARM`, `VTIMEZONE`
 * definitions) are ignored, and an unknown `TZID` reads as floating. It is pure, never throws, and never
 * puts feed text in an error. See [IcsLimits] for the bounds.
 */
object IcsParser {
    private const val MAX_EVENT_PROPERTIES = 4_000

    fun parse(
        text: String,
        deviceZone: ZoneId,
    ): IcsParseResult {
        if (text.length > IcsLimits.MAX_INPUT_CHARS) return IcsParseResult.Failed(IcsParseFailure.TOO_LARGE)
        val scan = Scan(deviceZone)
        for (line in IcsContentLines.unfold(text)) {
            if (scan.built.size >= IcsLimits.MAX_EVENTS) {
                scan.truncated = true
                break
            }
            IcsContentLines.parse(line)?.let(scan::accept)
        }
        return if (scan.sawCalendar) scan.result() else IcsParseResult.Failed(IcsParseFailure.NOT_CALENDAR)
    }

    private class Scan(private val deviceZone: ZoneId) {
        val built = ArrayList<IcsVevent>()
        var sawCalendar = false
        var truncated = false
        private var skipped = 0
        private var depth = 0
        private var eventDepth = -1
        private var calendarZone: ZoneId? = null
        private var current: MutableList<IcsProperty>? = null

        fun accept(prop: IcsProperty) {
            when {
                prop.name == "BEGIN" -> begin(prop.value.trim().uppercase())
                prop.name == "END" -> end(prop.value.trim().uppercase())
                current != null && depth == eventDepth -> addToEvent(prop)
                prop.name == "X-WR-TIMEZONE" && depth == 1 -> calendarZone = IcsValues.zone(prop.value)
            }
        }

        private fun begin(component: String) {
            depth++
            if (component == "VCALENDAR") sawCalendar = true
            val within = sawCalendar && current == null && depth <= IcsLimits.MAX_COMPONENT_DEPTH
            if (component == "VEVENT" && within) {
                current = ArrayList()
                eventDepth = depth
            }
        }

        private fun end(component: String) {
            val props = current
            if (props != null && component == "VEVENT" && depth == eventDepth) {
                val vevent = IcsEventBuilder(deviceZone, calendarZone).build(props)
                if (vevent == null) skipped++ else built += vevent
                current = null
                eventDepth = -1
            }
            if (depth > 0) depth--
        }

        private fun addToEvent(prop: IcsProperty) {
            val props = current ?: return
            if (props.size < MAX_EVENT_PROPERTIES) props += prop
        }

        fun result(): IcsParseResult.Ok {
            val cancelledUids = built.filter { it.cancelled && it.recurrenceId == null }.map { it.event.uid }.toSet()
            val live = built.filterNot { it.event.uid in cancelledUids }
            val overridesByUid = live.filter { it.recurrenceId != null }.groupBy { it.event.uid }
            val masters = live.filter { it.recurrenceId == null }
            val masterUids = masters.map { it.event.uid }.toSet()
            val events =
                masters.map { master -> withOverrides(master, overridesByUid[master.event.uid].orEmpty()) } +
                    orphans(overridesByUid.filterKeys { it !in masterUids })
            return IcsParseResult.Ok(
                events = events,
                skippedEvents = skipped,
                unknownZoneEvents = built.count { it.unknownZone },
                truncated = truncated,
            )
        }

        private fun withOverrides(
            master: IcsVevent,
            overrides: List<IcsVevent>,
        ): IcsEvent =
            master.event.copy(
                overrides =
                    overrides.take(IcsLimits.MAX_OVERRIDES).mapNotNull { vevent ->
                        val id = vevent.recurrenceId ?: return@mapNotNull null
                        val event = vevent.event
                        IcsInstanceOverride(
                            recurrenceId = id,
                            cancelled = vevent.cancelled,
                            start = event.start.takeIf { it != id },
                            duration = event.duration,
                            title = event.title.takeIf(String::isNotEmpty),
                            location = event.location,
                        )
                    },
            )

        /** An override whose master is not in the feed (common for invited instances) stands alone. */
        private fun orphans(byUid: Map<String, List<IcsVevent>>): List<IcsEvent> =
            byUid.values.flatMap { group -> group.filterNot { it.cancelled }.map { it.event } }
    }
}
