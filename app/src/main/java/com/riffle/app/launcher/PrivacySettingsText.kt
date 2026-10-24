package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel

/** User-visible wording of Settings > Privacy and the content-level controls on the source pages. */
internal object PrivacySettingsText {
    const val ENTRY_SUBTITLE = "Screenshots and recents, how much pages show, and hidden items"

    const val SCREEN_TITLE = "Screenshots and recents"
    const val SCREEN_SWITCH = "Hide launcher content in screenshots and recents"
    const val SCREEN_ON =
        "On. Screenshots, screen recordings, casting and the recents preview of the launcher show a blank screen. " +
            "Settings can still be captured."
    const val SCREEN_OFF =
        "Off. Turning it on also blocks your own screenshots of Home, the drawer and search."

    const val LEVELS_TITLE = "How much pages show"
    const val LEVELS_NOTE =
        "Applies to every page, widget and dock section that shows the source, on every layout, and takes effect at " +
            "once. Kept on this device; not part of a backup. Hide rules still remove items completely."
    const val LEVELS_PREVIEW_OFF =
        "Content levels apply to Workspaces (preview) pages. Turn on Workspaces (preview) in Settings > Developer " +
            "to choose them."
    const val LEVELS_NONE = "No source that can show private content is available in this build."

    const val HIDDEN_TITLE = "Hidden items"
    const val SOURCES_ROW = "Sources"
    const val SOURCES_ROW_SUBTITLE = "Turn a source off, or allow the access it needs"

    const val PROFILES_TITLE = "Work and private profiles"
    const val PROFILES_BODY =
        "While a work profile is paused, its notifications show only as app icons, with no text. A locked or " +
            "unavailable profile shows nothing. Private and confidential calendar events never show their text."
    const val STORAGE_BODY =
        "Nothing a page shows is stored or backed up: backups hold your settings and layouts, never notifications, " +
            "events, articles or search text."

    const val SELECTED = "Selected"

    fun label(level: SourceContentLevel): String =
        when (level) {
            SourceContentLevel.SHOW -> "Show everything"
            SourceContentLevel.HIDE_BODY -> "Titles only"
            SourceContentLevel.HIDE_TEXT -> "App icons only"
        }

    fun summary(level: SourceContentLevel): String =
        when (level) {
            SourceContentLevel.SHOW -> "Titles, text and images, as each page chooses"
            SourceContentLevel.HIDE_BODY -> "The title and icon; no message text, details or images"
            SourceContentLevel.HIDE_TEXT -> "\"Hidden content\" with the app icon and count; no text at all"
        }

    /** What TalkBack reads for one option: the source, the option and what it shows. */
    fun optionSpoken(
        sourceTitle: String,
        level: SourceContentLevel,
    ): String = "$sourceTitle, ${label(level)}. ${summary(level)}"
}
