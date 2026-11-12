package com.riffle.app.launcher.sources

import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.sources.CalendarItemMapper
import com.riffle.core.domain.launcher.workspace.sources.SharedSourceStream
import com.riffle.core.domain.launcher.workspace.sources.sourceStateFor

/**
 * The built-in sources plus the Stream's own agenda view of the calendar ([StreamSourceIds.AGENDA]). Kept apart from
 * [builtInSourceRegistry] so the user-facing source list is unchanged: the agenda source follows the calendar's
 * enablement, hide rules, content level and permission (see `AliasedSourceEnablement`).
 */
internal fun streamSourceRegistry(deps: BuiltInSourceDependencies): SourceRegistry =
    builtInSourceRegistry(deps, listOf(agendaSource(deps)))

/** The Stream's agenda: the device calendar over today and the next seven days, not the lens page's few events. */
private fun agendaSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    stream(deps, StreamSourceIds.AGENDA, setOf(SourceCapability.PRIVACY_SENSITIVE), deps.calendar.changes) {
        sourceStateFor(deps.calendar.access()) {
            val now = deps.nowEpochMillis()
            CalendarItemMapper().nextEvents(
                deps.calendar.agendaRepository.upcomingEvents(now, AGENDA_QUERY_LIMIT),
                now,
                limit = AGENDA_QUERY_LIMIT,
            )
        }
    }

/** Enough for a busy week; the Stream's own option caps what it shows. */
private const val AGENDA_QUERY_LIMIT = 60
