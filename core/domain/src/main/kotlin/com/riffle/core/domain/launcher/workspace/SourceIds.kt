package com.riffle.core.domain.launcher.workspace

/**
 * Canonical ids of the built-in [ItemSource]s. Persisted lenses refer to sources by these strings, so
 * the values are a stored contract: never rename one, only add new ids.
 */
object SourceIds {
    val ALL_APPS = SourceId("apps.all")
    val RECENT_APPS = SourceId("apps.recent")
    val NOTIFICATIONS = SourceId("notifications")
    val QUICK_ACTIONS = SourceId("shortcuts")
    val MEDIA = SourceId("media")
    val CALENDAR = SourceId("calendar")
    val RSS = SourceId("rss")
    val SEARCH = SourceId("search")
    val ICS = SourceId("ics")

    /** Every built-in id, in a stable order. */
    val BUILT_IN: List<SourceId> =
        listOf(ALL_APPS, RECENT_APPS, NOTIFICATIONS, QUICK_ACTIONS, MEDIA, CALENDAR, RSS, SEARCH, ICS)
}
