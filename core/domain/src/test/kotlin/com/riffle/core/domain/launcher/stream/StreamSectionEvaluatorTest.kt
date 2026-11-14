package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StreamSectionEvaluatorTest {
    private val context = StreamContext(nowEpochMillis = NOW)

    private fun ready(vararg items: Item) = SourceState.Ready(items.toList())

    private fun eval(
        section: StreamSection,
        inputs: StreamSectionInputs,
        ctx: StreamContext = context,
    ) = StreamSectionEvaluator.evaluate(section, inputs, ctx)

    private fun StreamSectionResult.ids(): List<String> = (this as StreamSectionResult.Ready).items.map { it.id.value }

    // --- the honest Loading rule --------------------------------------------------------------------------------

    @Test
    fun `after the deadline an input that never answered reads as unavailable, not loading`() {
        val s = StreamSection.NOTIFICATIONS
        val waiting = StreamSectionInputs()
        assertEquals(StreamSectionResult.Loading, eval(s, waiting))
        assertEquals(StreamSectionResult.Unavailable, eval(s, waiting.withLoadingExpired(s)))
    }

    @Test
    fun `a section that has no inputs left waiting never expires into unavailable`() {
        val s = StreamSection.NOTIFICATIONS
        val answered = StreamSectionInputs(mapOf(SourceIds.NOTIFICATIONS to SourceState.PermissionRequired))
        assertEquals(eval(s, answered), eval(s, answered.withLoadingExpired(s)))
    }

    @Test
    fun `what answered before the deadline still shows when its sibling did not`() {
        val s = StreamSection.TODAY
        val inputs = StreamSectionInputs(mapOf(SourceIds.ICS to ready(), StreamSourceIds.AGENDA to SourceState.Loading))
        assertEquals(StreamSectionResult.Loading, eval(s, inputs))
        // The ICS source answered (empty): Empty beats the one that never answered.
        assertEquals(StreamSectionResult.Empty, eval(s, inputs.withLoadingExpired(s)))
    }

    @Test
    fun `the typed inputs expire too, so Tasks and the alarm do not wait for good either`() {
        val tasks = StreamSection.TODAY
        assertEquals(StreamSectionResult.Unavailable, eval(tasks, StreamSectionInputs().withLoadingExpired(tasks)))
        val now = StreamSection.NOW
        val answered = StreamSectionInputs(alarm = TypedSourceState.Ready(emptyList()))
        assertEquals(TypedSourceState.Ready(emptyList<NextAlarm>()), answered.withLoadingExpired(now).alarm)
    }

    // --- availability -----------------------------------------------------------------------------------------

    @Test
    fun `loading, permission, empty, unavailable and off`() {
        val s = StreamSection.NOTIFICATIONS
        val n = SourceIds.NOTIFICATIONS
        assertEquals(StreamSectionResult.Loading, eval(s, StreamSectionInputs()))
        assertEquals(
            StreamSectionResult.PermissionRequired(listOf(n)),
            eval(s, StreamSectionInputs(mapOf(n to SourceState.PermissionRequired))),
        )
        assertEquals(StreamSectionResult.Empty, eval(s, StreamSectionInputs(mapOf(n to ready()))))
        assertEquals(StreamSectionResult.Unavailable, eval(s, StreamSectionInputs(mapOf(n to SourceState.Unavailable))))
        assertEquals(StreamSectionResult.Off, eval(s, StreamSectionInputs(mapOf(n to SourceState.Off))))
    }

    @Test
    fun `a section reading several sources shows what exists and only reports a gap when empty`() {
        val inputs =
            StreamSectionInputs(
                items =
                    mapOf(
                        SourceIds.MEDIA to SourceState.PermissionRequired,
                        SourceIds.CALENDAR to ready(eventItem("e", NOW + HOUR)),
                        SourceIds.ICS to SourceState.Loading,
                    ),
                alarm = TypedSourceState.Ready(emptyList()),
            )
        assertEquals(listOf("calendar:e"), eval(StreamSection.NOW, inputs).ids())

        val empty = inputs.copy(items = inputs.items + (SourceIds.CALENDAR to ready()))
        assertEquals(StreamSectionResult.Loading, eval(StreamSection.NOW, empty))
        val settled = empty.copy(items = empty.items + (SourceIds.ICS to ready()))
        assertEquals(
            StreamSectionResult.PermissionRequired(listOf(SourceIds.MEDIA)),
            eval(StreamSection.NOW, settled),
        )
    }

    // --- privacy and exclusions -------------------------------------------------------------------------------

    @Test
    fun `an exclusion rule removes items before grouping and limits`() {
        val rule =
            SourceExclusionRule(
                ExclusionRuleId("r"),
                SourceIds.NOTIFICATIONS,
                ExclusionMatcher.App("chat", "personal"),
            )
        val inputs =
            StreamSectionInputs(
                mapOf(
                    SourceIds.NOTIFICATIONS to
                        ready(
                            notificationItem("a"),
                            notificationItem("b", pkg = "mail", app = "Mail"),
                        ),
                ),
            )

        val hidden =
            eval(StreamSection.NOTIFICATIONS, inputs, context.copy(exclusions = ExclusionRuleSet(listOf(rule))))
        assertEquals(listOf("notifications:personal:b"), hidden.ids())

        val onlyChat = inputs.copy(items = mapOf(SourceIds.NOTIFICATIONS to ready(notificationItem("a"))))
        assertEquals(
            StreamSectionResult.Empty,
            eval(StreamSection.NOTIFICATIONS, onlyChat, context.copy(exclusions = ExclusionRuleSet(listOf(rule)))),
        )
    }

    @Test
    fun `an app exclusion also hides the app from media and recents (same family)`() {
        val rule =
            SourceExclusionRule(ExclusionRuleId("r"), SourceIds.ALL_APPS, ExclusionMatcher.App("mail", null))
        val ctx = context.copy(exclusions = ExclusionRuleSet(listOf(rule)))
        val recents =
            eval(
                StreamSection.RECENTS,
                StreamSectionInputs(
                    mapOf(SourceIds.RECENT_APPS to ready(recentItem("mail", 5), recentItem("maps", 4))),
                ),
                ctx,
            )
        assertEquals(listOf("apps.recent:personal:maps/Main"), recents.ids())
    }

    @Test
    fun `content levels redact before the query so typed content never carries hidden text`() {
        val ctx =
            context.copy(
                contentLevels = SourceContentLevels().with(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_TEXT),
            )
        val result =
            eval(
                StreamSection.NOTIFICATIONS,
                StreamSectionInputs(mapOf(SourceIds.NOTIFICATIONS to ready(notificationItem("a")))),
                ctx,
            ) as StreamSectionResult.Ready
        val content = result.items.single() as NotificationContent
        assertNull(content.title)
        assertNull(content.text)
        assertEquals(StreamPrivacy.REDACTED, content.privacy)
        assertEquals("Chat", result.groups.single().label)
    }

    // --- Now --------------------------------------------------------------------------------------------------

    @Test
    fun `now is the media card, then the next event and the alarm by start`() {
        val inputs =
            StreamSectionInputs(
                items =
                    mapOf(
                        SourceIds.MEDIA to
                            ready(
                                notificationItem("old", posted = 1, source = SourceIds.MEDIA),
                                notificationItem("new", posted = 9, source = SourceIds.MEDIA),
                            ),
                        SourceIds.CALENDAR to
                            ready(
                                eventItem("late", NOW + 5 * HOUR),
                                eventItem("soon", NOW + 2 * HOUR),
                                eventItem("done", NOW - 3 * HOUR),
                            ),
                        SourceIds.ICS to ready(eventItem("feed", NOW + 3 * HOUR, source = SourceIds.ICS, feed = "F")),
                    ),
                alarm = TypedSourceState.Ready(listOf(NextAlarm("a1", NOW + HOUR), NextAlarm("a2", NOW + 9 * HOUR))),
            )

        assertEquals(
            listOf("media:personal:new", "alarm:a1", "calendar:soon"),
            eval(StreamSection.NOW, inputs).ids(),
        )
    }

    @Test
    fun `now options pick the media count and turn the event and alarm off`() {
        val inputs =
            StreamSectionInputs(
                items =
                    mapOf(
                        SourceIds.MEDIA to
                            ready(
                                notificationItem("a", posted = 1, source = SourceIds.MEDIA),
                                notificationItem("b", posted = 9, source = SourceIds.MEDIA),
                            ),
                        SourceIds.CALENDAR to ready(eventItem("soon", NOW + HOUR)),
                        SourceIds.ICS to ready(),
                    ),
                alarm = TypedSourceState.Ready(listOf(NextAlarm("a1", NOW + HOUR))),
            )
        val options = StreamSectionOptions(now = NowOptions(maxMedia = 2, showNextEvent = false, showAlarm = false))

        assertEquals(
            listOf("media:personal:b", "media:personal:a"),
            eval(StreamSection.NOW, inputs, context.copy(options = options)).ids(),
        )
    }

    // --- Notifications ----------------------------------------------------------------------------------------

    @Test
    fun `notifications group by app newest first, sort by priority then time, and limit`() {
        val items =
            listOf(
                notificationItem("c1", pkg = "chat", posted = NOW - 50, priority = 3),
                notificationItem("c2", pkg = "chat", posted = NOW - 40, priority = 3),
                notificationItem("c3", pkg = "chat", posted = NOW - 60, priority = 5),
                notificationItem("m1", pkg = "mail", app = "Mail", posted = NOW - 5),
            )
        val inputs = StreamSectionInputs(mapOf(SourceIds.NOTIFICATIONS to SourceState.Ready(items)))

        val full = eval(StreamSection.NOTIFICATIONS, inputs) as StreamSectionResult.Ready
        assertEquals(listOf("mail:personal", "chat:personal"), full.groups.map { it.key })
        assertEquals(
            listOf("notifications:personal:c3", "notifications:personal:c2", "notifications:personal:c1"),
            full.groups[1].items.map { it.id.value },
        )

        val limited =
            eval(
                StreamSection.NOTIFICATIONS,
                inputs,
                context.copy(
                    options = StreamSectionOptions(notifications = NotificationsOptions(maxApps = 1, maxPerApp = 2)),
                ),
            ) as StreamSectionResult.Ready
        assertEquals(listOf("mail:personal"), limited.groups.map { it.key })

        val perApp =
            eval(
                StreamSection.NOTIFICATIONS,
                inputs,
                context.copy(options = StreamSectionOptions(notifications = NotificationsOptions(maxPerApp = 2))),
            ) as StreamSectionResult.Ready
        assertEquals(2, perApp.groups[1].items.size)
        assertEquals(1, perApp.groups[1].hiddenCount)
    }

    @Test
    fun `notifications older than the age option are dropped`() {
        val inputs =
            StreamSectionInputs(
                mapOf(
                    SourceIds.NOTIFICATIONS to
                        ready(
                            notificationItem("old", posted = NOW - 5 * HOUR),
                            notificationItem("new", posted = NOW - 1000),
                        ),
                ),
            )
        val options = StreamSectionOptions(notifications = NotificationsOptions(maxAgeMillis = HOUR))

        assertEquals(
            listOf("notifications:personal:new"),
            eval(StreamSection.NOTIFICATIONS, inputs, context.copy(options = options)).ids(),
        )
    }

    // --- Recents ----------------------------------------------------------------------------------------------

    @Test
    fun `recents are most recent first, one per app, limited and aged`() {
        val inputs =
            StreamSectionInputs(
                mapOf(
                    SourceIds.RECENT_APPS to
                        ready(
                            recentItem("a", NOW - 3 * HOUR),
                            recentItem("b", NOW - 10),
                            recentItem("c", NOW - 20),
                            recentItem("d", NOW - 30),
                        ),
                ),
            )

        fun ids(options: RecentsOptions) =
            eval(
                StreamSection.RECENTS,
                inputs,
                context.copy(options = StreamSectionOptions(recents = options)),
            ).ids().map {
                it.substringAfter("personal:")
            }

        assertEquals(listOf("b/Main", "c/Main", "d/Main", "a/Main"), ids(RecentsOptions()))
        assertEquals(listOf("b/Main", "c/Main"), ids(RecentsOptions(limit = 2)))
        assertEquals(listOf("b/Main", "c/Main", "d/Main"), ids(RecentsOptions(maxAgeMillis = HOUR)))
    }

    // --- Agenda -----------------------------------------------------------------------------------------------

    @Test
    fun `today groups upcoming events by day, ongoing ones under today, and limits`() {
        val day = 24 * HOUR
        val inputs =
            StreamSectionInputs(
                mapOf(
                    StreamSourceIds.AGENDA to
                        ready(
                            eventItem("ongoing", NOW - HOUR, end = NOW + HOUR),
                            eventItem("tomorrow", NOW + day),
                            eventItem("ended", NOW - 3 * HOUR, end = NOW - 2 * HOUR),
                            eventItem("far", NOW + 30 * day),
                        ),
                    SourceIds.ICS to ready(eventItem("feed", NOW + 2 * HOUR, source = SourceIds.ICS, feed = "F")),
                ),
            )
        val result = eval(StreamSection.TODAY, inputs) as StreamSectionResult.Ready

        assertEquals(listOf("1970-01-11", "1970-01-12"), result.groups.map { it.key })
        assertEquals(listOf("calendar:ongoing", "ics:feed"), result.groups[0].items.map { it.id.value })
        assertEquals(listOf("calendar:tomorrow"), result.groups[1].items.map { it.id.value })

        val noFeeds = StreamSectionOptions(agenda = AgendaOptions(includeCalendarFeeds = false, maxEvents = 1))
        assertEquals(
            listOf("calendar:ongoing"),
            eval(StreamSection.TODAY, inputs, context.copy(options = noFeeds)).ids(),
        )

        val wide = StreamSectionOptions(agenda = AgendaOptions(daysAhead = 60))
        assertEquals(true, "calendar:far" in eval(StreamSection.TODAY, inputs, context.copy(options = wide)).ids())
    }

    // --- Today's tasks and the gaps --------------------------------------------------------------------------

    private val noCalendar = mapOf(StreamSourceIds.AGENDA to SourceState.Off, SourceIds.ICS to SourceState.Off)

    @Test
    fun `tasks are not connected until a provider says otherwise`() {
        assertEquals(
            StreamSectionResult.NotConnected(),
            eval(StreamSection.TODAY, StreamSectionInputs(noCalendar, tasks = TypedSourceState.NotConnected())),
        )
        assertEquals(StreamSectionResult.Loading, eval(StreamSection.TODAY, StreamSectionInputs(noCalendar)))
    }

    @Test
    fun `a connected task list with nothing on it is ready, so its add field stays`() {
        val result =
            eval(
                StreamSection.TODAY,
                StreamSectionInputs(noCalendar, tasks = TypedSourceState.Ready(emptyList())),
            )
        assertEquals(
            StreamSectionResult.Ready(
                emptyList(),
                listOf(StreamGap(StreamSourceIds.TASKS, StreamSectionResult.Empty)),
            ),
            result,
        )
    }

    @Test
    fun `nothing readable and nothing on it is the calm empty state`() {
        val off = StreamSectionInputs(noCalendar, tasks = TypedSourceState.Off)
        assertEquals(StreamSectionResult.Off, eval(StreamSection.TODAY, off))
        val calendarEmpty =
            StreamSectionInputs(
                mapOf(StreamSourceIds.AGENDA to ready(), SourceIds.ICS to ready()),
                tasks = TypedSourceState.Off,
            )
        assertEquals(StreamSectionResult.Empty, eval(StreamSection.TODAY, calendarEmpty))
    }

    @Test
    fun `events show beside what the tasks and the calendar could not say`() {
        val events = mapOf(StreamSourceIds.AGENDA to ready(eventItem("e", NOW + HOUR)), SourceIds.ICS to ready())
        val notConnected =
            eval(StreamSection.TODAY, StreamSectionInputs(events, tasks = TypedSourceState.NotConnected()))
        val withEvents = assertIs<StreamSectionResult.Ready>(notConnected)
        assertEquals(listOf("calendar:e"), withEvents.items.map { it.id.value })
        assertEquals(listOf(StreamGap(StreamSourceIds.TASKS, StreamSectionResult.NotConnected())), withEvents.gaps)

        val noPermission =
            StreamSectionInputs(
                noCalendar + (StreamSourceIds.AGENDA to SourceState.PermissionRequired),
                tasks = TypedSourceState.Ready(listOf(TaskContent(StreamContentId("t"), "Pay rent"))),
            )
        val tasksOnly = assertIs<StreamSectionResult.Ready>(eval(StreamSection.TODAY, noPermission))
        assertEquals(listOf("t"), tasksOnly.items.map { it.id.value })
        assertEquals(
            listOf(
                StreamGap(StreamSourceIds.AGENDA, StreamSectionResult.PermissionRequired(listOf(SourceIds.CALENDAR))),
            ),
            tasksOnly.gaps,
        )
    }

    @Test
    fun `tasks sort open first by due date with undated last, hide completed and limit`() {
        fun task(
            id: String,
            due: Long? = null,
            done: Boolean = false,
        ) = TaskContent(StreamContentId(id), id, dueEpochMillis = due, completed = done)
        val inputs =
            StreamSectionInputs(
                noCalendar,
                tasks =
                    TypedSourceState.Ready(
                        listOf(task("none"), task("later", 20), task("done", 1, done = true), task("soon", 10)),
                    ),
            )

        assertEquals(listOf("soon", "later", "none"), eval(StreamSection.TODAY, inputs).ids())
        val withDone = StreamSectionOptions(tasks = TasksOptions(showCompleted = true, limit = 3))
        assertEquals(
            listOf("soon", "later", "none"),
            eval(StreamSection.TODAY, inputs, context.copy(options = withDone)).ids(),
        )
        val all = StreamSectionOptions(tasks = TasksOptions(showCompleted = true, limit = 10))
        assertEquals("done", eval(StreamSection.TODAY, inputs, context.copy(options = all)).ids().last())
    }

    // --- Feeds ------------------------------------------------------------------------------------------------

    @Test
    fun `feeds group by feed with the freshest feed first, newest article first, and limit`() {
        val inputs =
            StreamSectionInputs(
                mapOf(
                    SourceIds.RSS to
                        ready(
                            articleItem("a1", "alpha", 10),
                            articleItem("a2", "alpha", 30),
                            articleItem("b1", "beta", 50),
                            articleItem("a3", "alpha", null),
                        ),
                ),
            )
        val result = eval(StreamSection.FEEDS, inputs) as StreamSectionResult.Ready
        assertEquals(listOf("beta", "alpha"), result.groups.map { it.key })
        assertEquals(listOf("rss:alpha:a2", "rss:alpha:a1", "rss:alpha:a3"), result.groups[1].items.map { it.id.value })

        val limited = StreamSectionOptions(feeds = FeedsOptions(maxFeeds = 1, perFeed = 1))
        val one = eval(StreamSection.FEEDS, inputs, context.copy(options = limited)) as StreamSectionResult.Ready
        assertEquals(listOf("beta"), one.groups.map { it.key })
        assertIs<ArticleContent>(one.items.single())
    }

    @Test
    fun `every section has a stored id that round trips`() {
        StreamSection.entries.forEach { assertEquals(it, StreamSection.fromStoredId(it.storedId)) }
        assertEquals(
            listOf("now", "notifications", "recents", "today", "feeds"),
            StreamSection.layoutSections.map {
                it.storedId
            },
        )
        assertNull(StreamSection.fromStoredId("nope"))
    }

    @Suppress("unused")
    private val unusedId: SourceId = SourceIds.SEARCH
}
