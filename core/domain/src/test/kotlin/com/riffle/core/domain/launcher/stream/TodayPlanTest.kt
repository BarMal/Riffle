package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.ItemTarget
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TodayPlanTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 14)
    private val tomorrow = today.plusDays(1)
    private val minute = 60_000L
    private val hour = 60 * minute
    private val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()

    // 10:00 on the day.
    private val now = dayStart + 10 * hour

    private fun event(
        id: String,
        startMinutesFromNow: Long,
        lengthMinutes: Long = 30,
        allDay: Boolean = false,
        day: LocalDate = today,
    ): EventContent {
        val start =
            if (allDay) day.atStartOfDay(zone).toInstant().toEpochMilli() else now + startMinutesFromNow * minute
        return EventContent(
            id = StreamContentId("calendar:$id"),
            kind = EventKind.CALENDAR,
            title = id,
            location = null,
            calendarName = null,
            startEpochMillis = start,
            endEpochMillis = if (allDay) start + 24 * hour else start + lengthMinutes * minute,
            allDay = allDay,
            recurring = false,
            target = ItemTarget.None,
            actions = setOf(EventAction.OPEN),
            privacy = StreamPrivacy.VISIBLE,
        )
    }

    private fun task(
        id: String,
        overdue: Boolean = false,
        done: Boolean = false,
        dueDay: LocalDate? = today,
    ) = TaskContent(
        id = StreamContentId("todoist:$id"),
        title = id,
        dueEpochMillis = dueDay?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
        overdue = overdue,
        completed = done,
    )

    private fun content(
        events: List<EventContent> = emptyList(),
        tasks: List<TaskContent>? = null,
        gaps: List<StreamGap> = emptyList(),
    ): StreamPageBody.Content {
        val days =
            events.groupBy { event ->
                maxOf(event.startEpochMillis, now).let {
                    java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
                }
            }.toSortedMap().map { (key, items) -> StreamGroup(key, null, items.sortedBy { it.startEpochMillis }) }
        val taskGroup = tasks?.takeIf { it.isNotEmpty() }?.let { StreamGroup(StreamGroupKeys.TASKS, null, it) }
        val tasksGap =
            if (tasks != null && tasks.isEmpty()) {
                listOf(StreamGap(StreamSourceIds.TASKS, StreamSectionResult.Empty))
            } else {
                emptyList()
            }
        return StreamPageBody.Content(days + listOfNotNull(taskGroup), gaps + tasksGap)
    }

    private fun plan(body: StreamPageBody.Content) = TodayPlanner.plan(body, now, today, zone)

    private fun TodayCard.name(): String =
        when (this) {
            is TodayCard.NextEvent -> "next:${event.title}"
            is TodayCard.ToDo -> "todo:${tasks.size}"
            is TodayCard.LaterEvent -> "later:${event.title}"
            is TodayCard.Tomorrow -> "tomorrow:$eventCount/$taskCount"
            is TodayCard.Notice -> "notice:${notice::class.simpleName}"
        }

    private fun names(body: StreamPageBody.Content) = plan(body).map { it.name() }

    @Test
    fun theNextEventIsInFrontThenToDoThenLaterEventsThenTomorrow() {
        val body =
            content(
                events =
                    listOf(
                        event("lunch", startMinutesFromNow = 120),
                        event("standup", startMinutesFromNow = 25),
                        event("review", startMinutesFromNow = 300),
                        event("breakfast", startMinutesFromNow = 0, day = tomorrow).copy(
                            startEpochMillis = dayStart + 33 * hour,
                            endEpochMillis = dayStart + 34 * hour,
                        ),
                    ),
                tasks = listOf(task("pay rent", overdue = true), task("call mum")),
            )
        assertEquals(
            listOf("next:standup", "todo:2", "later:lunch", "later:review", "tomorrow:1/0"),
            names(body),
        )
    }

    @Test
    fun theToDoCardCountsWhatIsLeftAndWhatIsOverdue() {
        val body =
            content(
                tasks = listOf(task("a", overdue = true), task("b"), task("c"), task("done", done = true)),
            )
        val todo = assertIs<TodayCard.ToDo>(plan(body).single())
        assertEquals(3, todo.left)
        assertEquals(1, todo.overdue)
        assertEquals(4, todo.tasks.size)
    }

    @Test
    fun withNoEventsToDoIsInFront() {
        assertEquals(listOf("todo:1"), names(content(tasks = listOf(task("a")))))
    }

    @Test
    fun withNoTasksAtAllAndNoAccountThereIsNoToDoCardAndTheConnectCardComesAfterTheEvents() {
        val body =
            content(
                events = listOf(event("standup", 25), event("lunch", 120)),
                gaps = listOf(StreamGap(StreamSourceIds.TASKS, StreamSectionResult.NotConnected())),
            )
        assertEquals(listOf("next:standup", "later:lunch", "notice:TasksNotConnected"), names(body))
        val notice = plan(body).last() as TodayCard.Notice
        assertEquals(
            TodayNotice.TasksNotConnected(NotConnectedReason.NO_ACCOUNT),
            notice.notice,
        )
    }

    @Test
    fun aRejectedTokenIsSaidAsSuch() {
        val body =
            content(
                events = listOf(event("a", 10)),
                gaps =
                    listOf(
                        StreamGap(
                            StreamSourceIds.TASKS,
                            StreamSectionResult.NotConnected(NotConnectedReason.TOKEN_REJECTED),
                        ),
                    ),
            )
        assertEquals(
            TodayNotice.TasksNotConnected(NotConnectedReason.TOKEN_REJECTED),
            (plan(body).last() as TodayCard.Notice).notice,
        )
    }

    @Test
    fun aConnectedTaskListWithNothingOnItStillOffersItsCardAndNoEventsIsFine() {
        val todo = assertIs<TodayCard.ToDo>(plan(content(tasks = emptyList())).single())
        assertEquals(0, todo.left)
        assertTrue(todo.tasks.isEmpty())
    }

    @Test
    fun calendarPermissionAndTaskTroublesAreNoticesAfterWhatThereIs() {
        val body =
            content(
                tasks = listOf(task("a")),
                gaps =
                    listOf(
                        StreamGap(
                            StreamSourceIds.AGENDA,
                            StreamSectionResult.PermissionRequired(
                                listOf(com.riffle.core.domain.launcher.workspace.SourceIds.CALENDAR),
                            ),
                        ),
                        StreamGap(StreamSourceIds.TASKS, StreamSectionResult.Unavailable),
                    ),
            )
        assertEquals(listOf("todo:1", "notice:Permission", "notice:TasksUnavailable"), names(body))
    }

    @Test
    fun overdueTasksAreInTheToDoCardAndTomorrowsAreCountedInTomorrowsCard() {
        val body =
            content(
                tasks =
                    listOf(
                        task("old", overdue = true, dueDay = today.minusDays(2)),
                        task("soon", dueDay = tomorrow),
                    ),
            )
        val cards = plan(body)
        assertEquals(listOf("todo:1", "tomorrow:0/1"), cards.map { it.name() })
        assertEquals(1, (cards[0] as TodayCard.ToDo).overdue)
    }

    @Test
    fun tomorrowNamesItsFirstTimedEventAndCountsThemAll() {
        val first =
            event(
                "early",
                0,
                day = tomorrow,
            ).copy(startEpochMillis = dayStart + 33 * hour, endEpochMillis = dayStart + 34 * hour)
        val second =
            first.copy(
                id = StreamContentId("calendar:late"),
                title = "late",
                startEpochMillis = dayStart + 40 * hour,
            )
        val allDay = event("holiday", 0, allDay = true, day = tomorrow)
        val card = plan(content(events = listOf(allDay, second, first))).single() as TodayCard.Tomorrow
        assertEquals(3, card.eventCount)
        assertEquals("early", card.firstEvent?.title)
    }

    @Test
    fun anEventThatHasEndedSinceTheSectionWasEvaluatedIsNotShown() {
        val ended = event("ended", startMinutesFromNow = -60, lengthMinutes = 30)
        val upcoming = event("upcoming", 45)
        assertEquals(listOf("next:upcoming"), names(content(events = listOf(ended, upcoming))))
    }

    @Test
    fun anEventUnderWayIsTheNextOneAndTimedEventsComeBeforeAllDayOnes() {
        val underway = event("underway", startMinutesFromNow = -10, lengthMinutes = 60)
        val allDay = event("birthday", 0, allDay = true)
        val later = event("later", 90)
        assertEquals(
            listOf("next:underway", "later:later", "later:birthday"),
            names(content(events = listOf(allDay, later, underway))),
        )
        // With nothing timed, the all-day event is the next one.
        assertEquals(listOf("next:birthday"), names(content(events = listOf(allDay))))
    }

    @Test
    fun noEventsAndNoTasksPlansNothingForThePageToSayAsAnEmptyState() {
        assertEquals(emptyList(), plan(StreamPageBody.Content(emptyList())))
    }

    // The countdown ------------------------------------------------------------------------------------------------

    private fun countdown(
        start: Long,
        end: Long?,
        at: Long,
        allDay: Boolean = false,
    ) = TodayCountdown.of(
        event("x", 0).copy(startEpochMillis = start, endEpochMillis = end, allDay = allDay),
        at,
    )

    @Test
    fun theCountdownReadsInMinutesThenNowThenEndsInThenEnded() {
        val start = now + 25 * minute
        val end = start + 60 * minute
        assertEquals(TodayCountdown.StartsIn(25), countdown(start, end, now))
        // Rounded up: one second short of 25 minutes is still 25; a minute later it is 24.
        assertEquals(TodayCountdown.StartsIn(25), countdown(start, end, now + 1_000))
        assertEquals(TodayCountdown.StartsIn(24), countdown(start, end, now + minute))
        assertEquals(TodayCountdown.StartsIn(1), countdown(start, end, start - 1))
        assertEquals(TodayCountdown.StartsIn(1), countdown(start, end, start - minute + 1))
        // The moment it starts, and for its first minute, it reads "now".
        assertEquals(TodayCountdown.Now, countdown(start, end, start))
        assertEquals(TodayCountdown.Now, countdown(start, end, start + minute - 1))
        assertEquals(TodayCountdown.EndsIn(59), countdown(start, end, start + minute))
        assertEquals(TodayCountdown.EndsIn(10), countdown(start, end, end - 10 * minute))
        assertEquals(TodayCountdown.EndsIn(1), countdown(start, end, end - 1))
        assertEquals(TodayCountdown.Ended, countdown(start, end, end))
    }

    @Test
    fun anEventWithNoEndIsNowOnceItHasStartedAndAnAllDayEventSaysSo() {
        assertEquals(TodayCountdown.Now, countdown(now - hour, null, now))
        assertEquals(TodayCountdown.AllDay, countdown(dayStart, dayStart + 24 * hour, now, allDay = true))
    }

    @Test
    fun theTickerWakesAtTheNextChangeAndNeverMoreOftenThanASecond() {
        val start = now + 25 * minute + 20_000
        val e = event("x", 0).copy(startEpochMillis = start, endEpochMillis = start + hour)
        // 25 min 20 s away reads "in 26 min" until it is 25 min away: 20 s from now.
        assertEquals(20_000L, TodayCountdown.millisUntilChange(e, now))
        // On a minute boundary the next change is a minute away.
        assertEquals(minute, TodayCountdown.millisUntilChange(e, now + 20_000))
        // Just before a boundary it never asks for less than a second.
        assertEquals(1_000L, TodayCountdown.millisUntilChange(e, now + 20_000 - 10))
        // At the start the "now" lasts a minute.
        assertEquals(minute, TodayCountdown.millisUntilChange(e, start))
    }
}
