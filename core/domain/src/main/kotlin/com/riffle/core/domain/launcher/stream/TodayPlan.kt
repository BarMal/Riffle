package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.SourceId
import java.time.LocalDate
import java.time.ZoneId

/**
 * One card of the Today page's stack (ADR 0003, Amendment 1, "Today"), front to back in the order [TodayPlanner]
 * gives them. Transient, like the content in it.
 */
sealed interface TodayCard {
    /** The next upcoming (or current) event, drawn with a live countdown and its actions. */
    data class NextEvent(val event: EventContent) : TodayCard

    /**
     * "To do today": [tasks] (today's and overdue, the order the Tasks source gives), with [left] not yet done and
     * [overdue] of them overdue. Empty when a connected task list has nothing on it: the card still offers its add
     * field.
     */
    data class ToDo(val tasks: List<TaskContent>, val left: Int, val overdue: Int) : TodayCard

    /** An event later today. */
    data class LaterEvent(val event: EventContent) : TodayCard

    /**
     * Tomorrow in one card: how many events and tasks, the first event and, when there is no event, the first task
     * (what a tap on the card opens).
     */
    data class Tomorrow(
        val eventCount: Int,
        val taskCount: Int,
        val firstEvent: EventContent?,
        val firstTask: TaskContent? = null,
    ) : TodayCard

    /** An input that gave nothing and why, with the way out (allow access, connect, retry), after the events. */
    data class Notice(val notice: TodayNotice) : TodayCard
}

/** What a [TodayCard.Notice] says. */
sealed interface TodayNotice {
    data class Permission(val sources: List<SourceId>) : TodayNotice

    data class TasksNotConnected(val reason: NotConnectedReason) : TodayNotice

    data object TasksUnavailable : TodayNotice
}

/**
 * Composes the Today page's cards from what the Today section delivered (events grouped by day, then the tasks), a
 * clock and the day: pure, so the order and every combination of data are tested without a screen.
 *
 * Front to back: the next event (the first timed event that has not ended, else the first all-day one), the "To do
 * today" card (when a task list is connected, even with nothing on it), the other events today, a "Tomorrow" card (when
 * tomorrow has an event or a task) and last the notices for the inputs that gave nothing (calendar access, no task
 * account). With no events the "To do today" card is in front. Events that have ended since the section was evaluated
 * are left out, so the next event moves on as the clock does.
 */
object TodayPlanner {
    fun plan(
        content: StreamPageBody.Content,
        nowEpochMillis: Long,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TodayCard> {
        val tomorrow = today.plusDays(1)
        val events = content.groups.filter { it.key != StreamGroupKeys.TASKS }
        val todayEvents =
            events.firstOrNull { it.key == today.toString() }?.items.orEmpty()
                .filterIsInstance<EventContent>()
                .filter { EventTiming.of(it, nowEpochMillis) != EventTiming.Ended }
        val tomorrowEvents =
            events.firstOrNull { it.key == tomorrow.toString() }?.items.orEmpty().filterIsInstance<EventContent>()
        val tasks =
            content.groups.firstOrNull { it.key == StreamGroupKeys.TASKS }?.items.orEmpty()
                .filterIsInstance<TaskContent>()
        val tasksConnected = tasks.isNotEmpty() || content.gaps.any { it.isEmptyTasks() }

        val timed = todayEvents.filter { !it.allDay }.sortedBy { it.startEpochMillis }
        val allDay = todayEvents.filter { it.allDay }
        val ordered = timed + allDay
        val next = ordered.firstOrNull()

        val startOfTomorrow = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli()
        val startAfterTomorrow = tomorrow.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dueTomorrow =
            tasks.filter { task -> task.dueEpochMillis?.let { it in startOfTomorrow until startAfterTomorrow } == true }
        val toDo = tasks.filter { task -> task.dueEpochMillis?.let { it < startOfTomorrow } != false }

        return buildList {
            next?.let { add(TodayCard.NextEvent(it)) }
            if (tasksConnected) {
                add(
                    TodayCard.ToDo(
                        tasks = toDo,
                        left = toDo.count { !it.completed },
                        overdue = toDo.count { it.overdue && !it.completed },
                    ),
                )
            }
            ordered.drop(1).forEach { add(TodayCard.LaterEvent(it)) }
            if (tomorrowEvents.isNotEmpty() || dueTomorrow.isNotEmpty()) {
                add(
                    TodayCard.Tomorrow(
                        eventCount = tomorrowEvents.size,
                        taskCount = dueTomorrow.size,
                        firstEvent = (
                            tomorrowEvents.filter { !it.allDay }.minByOrNull { it.startEpochMillis }
                                ?: tomorrowEvents.firstOrNull()
                        ),
                        firstTask = dueTomorrow.firstOrNull(),
                    ),
                )
            }
            addAll(notices(content.gaps))
        }
    }

    private fun notices(gaps: List<StreamGap>): List<TodayCard> =
        gaps.mapNotNull { gap ->
            when (val state = gap.state) {
                is StreamSectionResult.PermissionRequired ->
                    TodayCard.Notice(TodayNotice.Permission(state.sources))
                is StreamSectionResult.NotConnected ->
                    TodayCard.Notice(TodayNotice.TasksNotConnected(state.reason))
                StreamSectionResult.Unavailable ->
                    if (gap.source == StreamSourceIds.TASKS) TodayCard.Notice(TodayNotice.TasksUnavailable) else null
                else -> null
            }
        }

    private fun StreamGap.isEmptyTasks(): Boolean =
        source == StreamSourceIds.TASKS && state == StreamSectionResult.Empty
}

/**
 * The Today countdown (ADR 0003: "in 25 min", "now", "ends in 10 min"): a plain value the UI words, so the boundaries
 * are tested. [of] says it for [now]; [minutes] are whole minutes, rounded up, at least 1.
 */
sealed interface TodayCountdown {
    /** Starts in [minutes] minutes (under a minute away reads as 1). */
    data class StartsIn(val minutes: Long) : TodayCountdown

    /** In progress for under a minute, or with no known end. */
    data object Now : TodayCountdown

    /** In progress, ends in [minutes] minutes. */
    data class EndsIn(val minutes: Long) : TodayCountdown

    data object AllDay : TodayCountdown

    data object Ended : TodayCountdown

    companion object {
        private const val MINUTE = 60_000L

        fun of(
            event: EventContent,
            nowEpochMillis: Long,
        ): TodayCountdown =
            when (val timing = EventTiming.of(event, nowEpochMillis)) {
                is EventTiming.Upcoming -> StartsIn(timing.minutes)
                is EventTiming.Ongoing ->
                    if (nowEpochMillis - event.startEpochMillis < MINUTE || timing.minutesLeft == null) {
                        Now
                    } else {
                        EndsIn(timing.minutesLeft)
                    }
                EventTiming.AllDay -> AllDay
                EventTiming.Ended -> Ended
            }

        /** How long until the words change again, so a ticker wakes no more often than it must (at least a second). */
        fun millisUntilChange(
            event: EventContent,
            nowEpochMillis: Long,
        ): Long {
            val end = event.endEpochMillis
            val boundary =
                when {
                    event.allDay -> return MINUTE
                    nowEpochMillis < event.startEpochMillis -> (event.startEpochMillis - nowEpochMillis) % MINUTE
                    nowEpochMillis < event.startEpochMillis + MINUTE -> event.startEpochMillis + MINUTE - nowEpochMillis
                    end != null && nowEpochMillis < end -> (end - nowEpochMillis) % MINUTE
                    else -> MINUTE
                }
            return (if (boundary == 0L) MINUTE else boundary).coerceIn(1_000L, MINUTE)
        }
    }
}
