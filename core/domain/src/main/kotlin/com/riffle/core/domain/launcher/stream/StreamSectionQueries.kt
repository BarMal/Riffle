package com.riffle.core.domain.launcher.stream

/**
 * The one fixed query per section, as plain code: what it keeps, how it groups, how it sorts and how many it
 * returns (ADR 0003: no query language). Every function is pure over already excluded and redacted content;
 * ordering is total (ties break on the content id) so a refresh never shuffles equal items.
 *
 * - Now: media newest first (up to `maxMedia`), then the next event and the next alarm by start; one group.
 * - Notifications: not older than `maxAgeMillis`; grouped by app, newest app first; within an app by priority
 *   then newest; limited to `maxApps` apps and `maxPerApp` each.
 * - Recents: not older than `maxAgeMillis`; one per app, most recently used first, `limit`; then up to
 *   `frequentLimit` other apps with the most foreground time, as a second group.
 * - Agenda: events not ended and starting within `daysAhead`; by start, `maxEvents`; grouped by day.
 * - Tasks: open ones (completed too if asked); open first, due soonest, no date last; `limit`.
 * - Feeds: grouped by feed, the feed with the newest article first; newest article first; `maxFeeds`, `perFeed`.
 */
internal object StreamSectionQueries {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun run(
        section: StreamSection,
        content: List<StreamContent>,
        context: StreamContext,
    ): List<StreamGroup> =
        when (section) {
            StreamSection.NOW -> now(content, context)
            StreamSection.NOTIFICATIONS -> notifications(content, context)
            StreamSection.RECENTS -> recents(content, context)
            StreamSection.AGENDA -> agenda(content, context)
            StreamSection.TASKS -> tasks(content, context)
            StreamSection.FEEDS -> feeds(content, context)
        }.filter { it.items.isNotEmpty() }

    fun now(
        content: List<StreamContent>,
        context: StreamContext,
    ): List<StreamGroup> {
        val options = context.options.now
        val media =
            content.filterIsInstance<MediaContent>()
                .sortedWith(compareByDescending<MediaContent> { it.postedAtEpochMillis ?: 0L }.thenBy { it.id.value })
                .take(options.maxMedia)
        val upcoming =
            content.filterIsInstance<EventContent>()
                .filter { event ->
                    when (event.kind) {
                        EventKind.ALARM -> options.showAlarm && event.startEpochMillis >= context.nowEpochMillis
                        else -> options.showNextEvent && event.hasNotEnded(context.nowEpochMillis)
                    }
                }
        val nextEvent = upcoming.filter { it.kind != EventKind.ALARM }.sortedWith(byStart).take(1)
        val alarm = upcoming.filter { it.kind == EventKind.ALARM }.sortedWith(byStart).take(1)
        return listOf(StreamGroup(NOW_GROUP, null, media + (nextEvent + alarm).sortedWith(byStart)))
    }

    fun notifications(
        content: List<StreamContent>,
        context: StreamContext,
    ): List<StreamGroup> {
        val options = context.options.notifications
        val cutoff = options.maxAgeMillis?.let { context.nowEpochMillis - it }
        val byApp =
            content.filterIsInstance<NotificationContent>()
                .filter { cutoff == null || it.postedAtEpochMillis >= cutoff }
                .groupBy { "${it.app.packageName}:${it.app.profileId.orEmpty()}" }
        return byApp.entries
            .map { (key, group) ->
                val sorted =
                    group.sortedWith(
                        compareByDescending<NotificationContent> { it.priority?.rank ?: 0 }
                            .thenByDescending { it.postedAtEpochMillis }
                            .thenBy { it.id.value },
                    )
                StreamGroup(key, sorted.first().appLabel, sorted.take(options.maxPerApp), sorted.size)
            }
            .sortedWith(
                compareByDescending<StreamGroup> { group -> group.items.maxOf { it.timeEpochMillis ?: 0L } }
                    .thenBy { it.key },
            )
            .take(options.maxApps)
    }

    fun recents(
        content: List<StreamContent>,
        context: StreamContext,
    ): List<StreamGroup> {
        val options = context.options.recents
        val cutoff = options.maxAgeMillis?.let { context.nowEpochMillis - it }
        val apps =
            content.filterIsInstance<RecentAppContent>()
                .filter { cutoff == null || (it.lastUsedEpochMillis ?: 0L) >= cutoff }
                .sortedWith(
                    compareByDescending<RecentAppContent> { it.lastUsedEpochMillis ?: 0L }.thenBy { it.id.value },
                )
                .distinctBy { it.app }
        val recent = apps.take(options.limit)
        val frequent =
            apps.drop(options.limit)
                .filter { it.foregroundMillis > 0L }
                .sortedWith(compareByDescending<RecentAppContent> { it.foregroundMillis }.thenBy { it.id.value })
                .take(options.frequentLimit)
        return listOf(
            StreamGroup(RECENTS_GROUP, null, recent, apps.size),
            StreamGroup(FREQUENT_GROUP, null, frequent),
        )
    }

    fun agenda(
        content: List<StreamContent>,
        context: StreamContext,
    ): List<StreamGroup> {
        val options = context.options.agenda
        val horizon = context.nowEpochMillis + options.daysAhead * DAY_MILLIS
        val events =
            content.filterIsInstance<EventContent>()
                .filter { it.kind != EventKind.ALARM }
                .filter { options.includeCalendarFeeds || it.kind != EventKind.CALENDAR_FEED }
                .filter { it.hasNotEnded(context.nowEpochMillis) && it.startEpochMillis < horizon }
                .sortedWith(byStart)
                .take(options.maxEvents)
        // An event already under way belongs to today, not to the day it started.
        return events
            .groupBy { context.dayBucketer.dayKey(maxOf(it.startEpochMillis, context.nowEpochMillis)) }
            .map { (day, items) -> StreamGroup(day, null, items) }
            .sortedBy { it.key }
    }

    fun tasks(
        content: List<StreamContent>,
        context: StreamContext,
    ): List<StreamGroup> {
        val options = context.options.tasks
        val tasks =
            content.filterIsInstance<TaskContent>()
                .filter { options.showCompleted || !it.completed }
                .sortedWith(
                    compareBy<TaskContent> { it.completed }
                        .thenBy { it.dueEpochMillis ?: Long.MAX_VALUE }
                        .thenBy { it.id.value },
                )
        return listOf(StreamGroup(TASKS_GROUP, null, tasks.take(options.limit), tasks.size))
    }

    fun feeds(
        content: List<StreamContent>,
        context: StreamContext,
    ): List<StreamGroup> {
        val options = context.options.feeds
        return content.filterIsInstance<ArticleContent>()
            .groupBy { it.feedId }
            .map { (feedId, articles) ->
                val sorted =
                    articles.sortedWith(
                        compareByDescending<ArticleContent> { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                            .thenBy { it.id.value },
                    )
                StreamGroup(feedId, sorted.first().feedName, sorted.take(options.perFeed), sorted.size)
            }
            .sortedWith(
                compareByDescending<StreamGroup> { group -> group.items.maxOf { it.timeEpochMillis ?: Long.MIN_VALUE } }
                    .thenBy { it.key },
            )
            .take(options.maxFeeds)
    }

    private val byStart: Comparator<EventContent> =
        compareBy<EventContent> { it.startEpochMillis }.thenBy { it.id.value }

    /** An event whose end was withheld (a private one) is kept: the source already dropped ended events. */
    private fun EventContent.hasNotEnded(now: Long): Boolean = endEpochMillis?.let { it > now } ?: true

    const val NOW_GROUP = "now"
    const val RECENTS_GROUP = "recents"
    const val FREQUENT_GROUP = "frequent"
    const val TASKS_GROUP = "tasks"
}
