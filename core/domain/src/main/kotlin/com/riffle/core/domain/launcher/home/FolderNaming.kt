package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.library.LibraryCategories

/** The name a folder made by a drop starts with (the owner then edits it in place). Pure. */
object FolderNaming {
    const val DEFAULT_LABEL = "Folder"

    /**
     * The shared category's label when both apps have the same one (the category the Library's Categories view groups
     * by, [LibraryCategories.keyOf]; the fallback bucket is not a name), otherwise [fallback].
     */
    fun suggested(
        first: InstalledApp?,
        second: InstalledApp?,
        fallback: String = DEFAULT_LABEL,
    ): String {
        if (first == null || second == null) return fallback
        val key = LibraryCategories.keyOf(first)
        return key.takeIf { it != LibraryCategories.OTHER_KEY && it == LibraryCategories.keyOf(second) } ?: fallback
    }

    /** The shared category's label when every one of [apps] has the same one (two or more), otherwise [fallback]. */
    fun suggestedForAll(
        apps: List<InstalledApp?>,
        fallback: String = DEFAULT_LABEL,
    ): String {
        if (apps.size < 2 || apps.any { it == null }) return fallback
        val keys = apps.filterNotNull().map { LibraryCategories.keyOf(it) }.toSet()
        return keys.singleOrNull()?.takeIf { it != LibraryCategories.OTHER_KEY } ?: fallback
    }
}
