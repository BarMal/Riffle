package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp

data class AppDrawerSection(
    val title: String,
    val apps: List<InstalledApp>,
) {
    val displayTitle: String = "$title (${apps.size})"
}

/**
 * Manual validation:
 * - On phone and large or folded/unfolded widths, verify category headings and app rows do not
 *   clip or overlap in light and dark themes.
 * - For Personal, Work, and Private profile filters, verify category sections only contain apps
 *   from the selected profile.
 * - Search for categorized and uncategorized apps, then clear the query to verify category and
 *   alphabetical sections are restored correctly.
 */
object AppDrawerSections {
    fun from(apps: List<InstalledApp>): List<AppDrawerSection> =
        apps
            .groupBy { app -> app.sectionTitle }
            .map { (title, sectionApps) ->
                AppDrawerSection(
                    title = title,
                    apps = sectionApps,
                )
            }
            .sortedWith(
                compareBy<AppDrawerSection> { section -> section.profileBucketSortKey() }
                    .thenBy { section -> section.title.sectionSortKey() },
            )

    private val InstalledApp.sectionTitle: String
        get() {
            val section = category.normalizedCategory() ?: label.letterSectionTitle()

            return identity.profile.drawerProfilePrefix()
                ?.let { prefix -> "$prefix - $section" }
                ?: section
        }
}

private fun AppDrawerSection.profileBucketSortKey(): Int {
    val profile = apps.firstOrNull()?.identity?.profile ?: AppProfile.personal()

    return when {
        profile.type == AppProfileType.PERSONAL && profile.id == AppProfile.personal().id -> 0
        profile.type == AppProfileType.PERSONAL -> 1
        profile.type == AppProfileType.WORK -> 2
        profile.type == AppProfileType.PRIVATE -> 3
        else -> 4
    }
}

private fun String.sectionSortKey(): String =
    substringAfter(delimiter = " - ", missingDelimiterValue = this)
        .takeUnless { section -> section == OTHER_SECTION_TITLE }
        ?: OTHER_SECTION_SORT_TITLE

private fun String?.normalizedCategory(): String? = this?.trim()?.takeIf { category -> category.isNotEmpty() }

private fun String.letterSectionTitle(): String =
    trim()
        .firstOrNull()
        ?.takeIf { character -> character.isLetter() }
        ?.uppercaseChar()
        ?.toString()
        ?: OTHER_SECTION_TITLE

private const val OTHER_SECTION_TITLE = "#"
private const val OTHER_SECTION_SORT_TITLE = "{"
