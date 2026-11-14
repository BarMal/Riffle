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

    /**
     * The launcher shortcuts of a pinned app. Not a source anyone subscribes to: it names the gap of a shortcuts page
     * when the launcher role is missing (shortcuts can only be read by the Home app).
     */
    val SHORTCUTS = SourceId("shortcuts")

    /**
     * The Agenda's own view of the device calendar: its window is today and the next seven days, not the few
     * events a lens page reads. It is not one of the user-facing sources: it follows [SourceIds.CALENDAR]
     * (on/off, Hide rules, content level, permission), and its items are `calendar` items.
     */
    val AGENDA = SourceId("calendar.agenda")

    /** The user-facing source whose access, enablement and privacy [id] follows. */
    fun userSourceOf(id: SourceId): SourceId = if (id == AGENDA) SourceIds.CALENDAR else id
}

/**
 * The Stream's fixed sections (ADR 0003). [storedId] is a stored contract (P4 persists the layout by it): never
 * rename one, only add new ids. The layout holds [layoutSections] (each exactly once); [SHORTCUTS] is not one of them:
 * its pages are derived from the Dock's pinned apps, not chosen, hidden or ordered by the user.
 *
 * The retired section ids `agenda` and `tasks` (one page each before the Today page) are not entries any more: a
 * stored layout that names them still decodes, see [LegacyStreamSections] and `StreamLayoutCodec`.
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

    /**
     * Today: the next event with its countdown, a "To do today" checklist, later events and a summary of tomorrow
     * (one page where Agenda and Tasks were two). It reads the device calendar and calendar feeds (by day) and the
     * tasks.
     */
    TODAY("today", listOf(StreamSourceIds.AGENDA, SourceIds.ICS), readsTasks = true),

    FEEDS("feeds", listOf(SourceIds.RSS)),

    /** A pinned app's shortcuts (one page per pinned app with nothing live). Derived; never in a layout. */
    SHORTCUTS("shortcuts", emptyList()),
    ;

    /** Whether the user's layout holds this section (everything but [SHORTCUTS]). */
    val inLayout: Boolean get() = this != SHORTCUTS

    companion object {
        /** The sections a layout holds, in their default order. */
        val layoutSections: List<StreamSection> = entries.filter { it.inLayout }

        /** The section stored as [id], or null for an unknown or blank id (the retired ones included). */
        fun fromStoredId(id: String): StreamSection? = entries.firstOrNull { it.storedId == id }
    }
}

/**
 * The stored ids of the sections the Today page replaced. A layout written before it names them; decoding maps them to
 * [StreamSection.TODAY] (see `StreamLayoutCodec`): Today takes the place of Agenda (or, with no Agenda, of Tasks), is
 * hidden only when every one of them was hidden, and shows as the Agenda was shown.
 */
object LegacyStreamSections {
    const val AGENDA_ID = "agenda"
    const val TASKS_ID = "tasks"

    fun isLegacy(id: String): Boolean = id == AGENDA_ID || id == TASKS_ID
}
