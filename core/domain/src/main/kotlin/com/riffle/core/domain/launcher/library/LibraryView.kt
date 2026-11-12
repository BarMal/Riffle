package com.riffle.core.domain.launcher.library

import com.riffle.core.domain.launcher.settings.AppDrawerPresentation

/**
 * How the Library draws every installed app (ADR 0003, Views): iOS-style category tiles (the default), an A-Z list
 * with a scrubber, or an icon grid. One is stored per device class.
 *
 * [storedId] is a stored contract: never rename one, only add new ids.
 */
enum class LibraryView(
    val storedId: String,
) {
    Categories("categories"),
    AlphaList("alpha_list"),
    Grid("grid"),
    ;

    companion object {
        val DEFAULT: LibraryView = Categories

        fun fromStoredId(id: String?): LibraryView? = entries.firstOrNull { it.storedId == id }

        /**
         * The view for a stored value that may be the new id, an older app-drawer presentation name (LIST becomes the
         * A-Z list, ICONS the grid) or anything else (the default). Never throws.
         */
        fun decode(stored: String?): LibraryView =
            fromStoredId(stored)
                ?: when (stored) {
                    AppDrawerPresentation.LIST.name -> AlphaList
                    AppDrawerPresentation.ICONS.name -> Grid
                    else -> DEFAULT
                }

        /** The view an older app-drawer presentation maps to. */
        fun fromLegacy(presentation: AppDrawerPresentation): LibraryView =
            when (presentation) {
                AppDrawerPresentation.LIST -> AlphaList
                AppDrawerPresentation.ICONS -> Grid
            }
    }
}
