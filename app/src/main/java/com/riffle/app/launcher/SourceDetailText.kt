package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.settings.SourcePlace
import com.riffle.core.domain.launcher.workspace.settings.SourcePlaceKind
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
            SourceIds.SEARCH to SettingsPage.SOURCE_SEARCH,
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

/** Wording for the Sources page's detail pages, Used by lists and Add source entries. */
internal object SourceDetailText {
    const val USED_BY_TITLE = "Used by"
    const val NOT_USED = "Not used by any page yet"
    const val NOT_USED_HELP =
        "No page, widget or dock section reads this source. Choose it as a lens source in the workspace editor."
    const val OPEN_DETAILS = "Open details"
    const val OPEN_WORKSPACE = "Edit workspace"
    const val USED_BY_NOTE = "Counts every layout. Tap a place to edit its workspace."
    const val USE_THIS_SOURCE = "Use this source"
    const val ADD_SOURCE_TITLE = "Add a source"
    const val ADD_RSS = "Add RSS feed"
    const val ADD_RSS_BODY = "Opens the RSS feeds page, where you paste a feed link"
    const val ADD_CALENDAR_FEED = "Add calendar feed"
    const val ADD_CALENDAR_FEED_BODY = "Opens Calendar feeds, where you paste a calendar link"
    const val LOADING_LAYOUTS = "Pages that use this source are still loading."
    const val OPEN_ALL_RULES = "Open all hidden items and rules"
    const val CONTENT_LEVEL_TITLE = "Content level"
    const val CONTENT_LEVEL_LATER =
        "Coming later: a per-source choice of how much of a notification to show. Until then, hide rules and " +
            "Android's own lock screen settings are the controls."
    const val RULES_TITLE = "Hide rules"
    const val NOTIFICATION_RULES_NOTE = "Rules that hide notifications before any page can show them."
    const val APP_RULES_TITLE = "Hidden apps"
    const val APP_RULES_NOTE = "Apps hidden from your pages and the drawer. Turn a rule off to show the app again."
    const val SEARCH_MODEL_TITLE = "How search queries work"
    const val SEARCH_MODEL_BODY =
        "Each search page or widget can carry its own query in its lens, so two pages can show two different " +
            "searches. A lens without a query reads the one shared query instead. Set a lens's query in the " +
            "workspace editor."
    const val SEARCH_GLOBAL_TITLE = "Shared query"
    const val SEARCH_GLOBAL_SET = "A shared query is set. Lenses without their own query use it."
    const val SEARCH_GLOBAL_NONE = "No shared query is set, so lenses without their own query show nothing."
    const val SEARCH_GLOBAL_PRIVACY = "The query text is kept in memory only and is never shown here or stored."
    const val SEARCH_NO_HOST = "The shared query is not available in this build."
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

    fun usedBy(count: Int): String =
        when (count) {
            0 -> NOT_USED
            1 -> "Used by 1 place"
            else -> "Used by $count places"
        }

    fun usedByHeading(count: Int): String = if (count == 0) USED_BY_TITLE else "$USED_BY_TITLE ($count)"

    /** "Nova > Page 2 > Widget 3", with the layout first when [layoutName] is given. */
    fun placeLabel(
        place: SourcePlace,
        layoutName: String?,
    ): String =
        placeParts(place, layoutName).joinToString(separator = " > ")

    /** The same place for TalkBack: commas instead of arrows. */
    fun placeSpoken(
        place: SourcePlace,
        layoutName: String?,
    ): String = placeParts(place, layoutName).joinToString(separator = ", ")

    private fun placeParts(
        place: SourcePlace,
        layoutName: String?,
    ): List<String> =
        listOfNotNull(
            layoutName,
            place.workspaceName,
            when (place.kind) {
                SourcePlaceKind.DOCK -> "Dock section"
                SourcePlaceKind.PAGE -> "Page ${place.pageNumber ?: 1}"
                SourcePlaceKind.PAGE_SET -> "Page set (page ${place.pageNumber ?: 1})"
                SourcePlaceKind.WIDGET -> "Page ${place.pageNumber ?: 1}"
            },
            place.widgetNumber?.let { "Widget $it" },
            place.savedLensName?.let { "saved lens $it" },
        )

    /** What TalkBack reads for a Sources row; it also says what a double tap does. */
    fun rowSpoken(
        row: SourceRow,
        usedByCount: Int?,
    ): String =
        buildString {
            append(SourcesSettingsText.statusDescription(row.title, row.status))
            if (usedByCount != null) append(". ").append(usedBy(usedByCount))
            append(". ").append(row.description)
            append(". Double tap for details")
        }
}
