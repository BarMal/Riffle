package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.settings.SourceRow

/** Which detail page a source's Sources row opens, and back. Pure, so JVM tests can check the whole table. */
internal object SourceDetailPages {
    private val table: Map<SourceId, SettingsPage> =
        mapOf(
            SourceIds.ALL_APPS to SettingsPage.SOURCE_APPS,
            SourceIds.RECENT_APPS to SettingsPage.SOURCE_RECENTS,
            SourceIds.QUICK_ACTIONS to SettingsPage.SOURCE_SHORTCUTS,
            SourceIds.NOTIFICATIONS to SettingsPage.SOURCE_NOTIFICATIONS,
            SourceIds.MEDIA to SettingsPage.SOURCE_MEDIA,
            SourceIds.CALENDAR to SettingsPage.SOURCE_CALENDAR,
            SourceIds.RSS to SettingsPage.SOURCE_RSS,
            SourceIds.ICS to SettingsPage.SOURCE_ICS,
        )

    val pages: Set<SettingsPage> = table.values.toSet()

    fun pageFor(id: SourceId): SettingsPage? = table[id]

    fun sourceFor(page: SettingsPage): SourceId? = table.entries.firstOrNull { it.value == page }?.key
}

/**
 * Where Back leads from [page]: the Developer pages opened from the Sources page return to it; everything else
 * keeps returning to the main page, exactly as before.
 */
internal fun settingsBackTarget(page: SettingsPage): SettingsPage =
    when {
        page in SourceDetailPages.pages || page == SettingsPage.EXCLUSIONS || page == SettingsPage.ICS_FEEDS ->
            SettingsPage.SOURCES
        else -> SettingsPage.MAIN
    }

/** Wording for the Sources page's detail pages and Add source entries. */
internal object SourceDetailText {
    const val OPEN_DETAILS = "Open details"
    const val USE_THIS_SOURCE = "Use this source"
    const val ADD_SOURCE_TITLE = "Add a source"
    const val ADD_RSS = "Add RSS feed"
    const val ADD_RSS_BODY = "Opens the RSS feeds page, where you paste a feed link"
    const val ADD_CALENDAR_FEED = "Add calendar feed"
    const val ADD_CALENDAR_FEED_BODY = "Opens Calendar feeds, where you paste a calendar link"
    const val OPEN_ALL_RULES = "Open all hidden items and rules"
    const val CONTENT_LEVEL_TITLE = "Content level"
    const val CONTENT_LEVEL_NOTE =
        "How much of each item any page, widget or dock section may show. The same choice is on Settings > Privacy."
    const val RULES_TITLE = "Hide rules"
    const val NOTIFICATION_RULES_NOTE = "Rules that hide notifications before any page can show them."
    const val APP_RULES_TITLE = "Hidden apps"
    const val APP_RULES_NOTE = "Apps hidden from your pages and the drawer. Turn a rule off to show the app again."
    const val ICS_OPEN_FEEDS = "Manage calendar feeds"
    const val CALENDAR_PRIVACY = "Private and confidential events show no text."
    const val NOT_AVAILABLE = "This source is not available in this build."
    const val ICS_NOT_AVAILABLE = "Calendar feeds are not available in this build."
    const val RECENTS_NOTE = "Needs Android's Usage access, which you grant in system settings."
    const val MEDIA_NOTE = "Reads media notifications, so it uses the same notification access as Notifications."

    /** An extra line of explanation for sources whose one-line description is not the whole story. */
    fun note(id: SourceId): String? =
        when (id) {
            SourceIds.CALENDAR -> CALENDAR_PRIVACY
            SourceIds.RECENT_APPS -> RECENTS_NOTE
            SourceIds.MEDIA -> MEDIA_NOTE
            else -> null
        }

    /** What TalkBack reads for a Sources row; it also says what a double tap does. */
    fun rowSpoken(row: SourceRow): String =
        buildString {
            append(SourcesSettingsText.statusDescription(row.title, row.status))
            append(". ").append(row.description.trimEnd('.'))
            append(". Double tap for details")
        }
}
