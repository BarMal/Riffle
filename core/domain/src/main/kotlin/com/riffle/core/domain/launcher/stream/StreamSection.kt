package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds

/**
 * Ids of the Stream's typed-only sources: they have no [com.riffle.core.domain.launcher.workspace.ItemSource], but
 * they are keyed into the same per-source content levels (Settings > Privacy) as the built-in ones.
 */
object StreamSourceIds {
    val TASKS = SourceId("tasks")
    val ALARM = SourceId("alarm")
}

/**
 * The Stream's six fixed sections (ADR 0003). [storedId] is a stored contract (P4 persists the layout by it):
 * never rename one, only add new ids.
 *
 * [itemSources] are the built-in item sources a section reads, each through one shared subscription;
 * [readsTasks] and [readsAlarm] say that the section also reads the typed task and next-alarm providers.
 */
enum class StreamSection(
    val storedId: String,
    val itemSources: List<SourceId>,
    val readsTasks: Boolean = false,
    val readsAlarm: Boolean = false,
) {
    /** Media with its queue, the next event and the next alarm. */
    NOW("now", listOf(SourceIds.MEDIA, SourceIds.CALENDAR, SourceIds.ICS), readsAlarm = true),

    NOTIFICATIONS("notifications", listOf(SourceIds.NOTIFICATIONS)),

    RECENTS("recents", listOf(SourceIds.RECENT_APPS)),

    /** Upcoming events from the device calendar and calendar feeds, by day. */
    AGENDA("agenda", listOf(SourceIds.CALENDAR, SourceIds.ICS)),

    TASKS("tasks", emptyList(), readsTasks = true),

    FEEDS("feeds", listOf(SourceIds.RSS)),
    ;

    companion object {
        /** The section stored as [id], or null for an unknown or blank id. */
        fun fromStoredId(id: String): StreamSection? = entries.firstOrNull { it.storedId == id }
    }
}
