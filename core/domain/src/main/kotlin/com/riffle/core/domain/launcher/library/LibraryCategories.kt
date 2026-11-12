package com.riffle.core.domain.launcher.library

import com.riffle.core.domain.launcher.apps.InstalledApp
import java.util.Locale

/** One category tile of the Library: its [name] (a platform category label, or [OTHER_KEY]) and its apps, A-Z. */
data class LibraryCategory(
    val key: String,
    val apps: List<InstalledApp>,
) {
    /** Whether this is the fallback bucket, whose display name the UI takes from a string resource. */
    val isOther: Boolean get() = key == LibraryCategories.OTHER_KEY
}

/**
 * Groups apps into the Library's category tiles. A category comes from the platform's `ApplicationInfo.category`, which
 * the installed-app repository maps to a label; apps without one fall into the [OTHER_KEY] bucket. No lookup of any
 * kind leaves the device. Pure, so the grouping rules are unit tested.
 */
object LibraryCategories {
    const val OTHER_KEY: String = "other"

    /** How many icons a tile previews before the rest are behind its name. */
    const val PREVIEW_COUNT: Int = 4

    /** The order platform categories take on the Library when they are present; any other follows A-Z, then Other. */
    private val preferredOrder: List<String> =
        listOf("Social", "Productivity", "Audio", "Video", "Image", "Game", "News", "Maps")

    fun keyOf(app: InstalledApp): String = app.category?.trim()?.takeIf(String::isNotEmpty) ?: OTHER_KEY

    /** Tiles for [apps], their apps sorted A-Z by label; the empty ones are never produced. */
    fun from(apps: List<InstalledApp>): List<LibraryCategory> =
        apps
            .groupBy(::keyOf)
            .map { (key, members) -> LibraryCategory(key, members.sortedWith(appOrder)) }
            .sortedWith(compareBy<LibraryCategory> { categoryRank(it.key) }.thenBy { it.key.lowercase(Locale.ROOT) })

    private fun categoryRank(key: String): Int =
        when (key) {
            OTHER_KEY -> Int.MAX_VALUE
            else -> preferredOrder.indexOf(key).let { index -> if (index < 0) preferredOrder.size else index }
        }

    /** The one order the Library lists apps in: label A-Z, then package, activity and profile for a stable tie. */
    val appOrder: Comparator<InstalledApp> =
        compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }
            .thenBy { it.identity.packageName.value }
            .thenBy { it.identity.activityName.value }
            .thenBy { it.identity.profile.id.value }
}
