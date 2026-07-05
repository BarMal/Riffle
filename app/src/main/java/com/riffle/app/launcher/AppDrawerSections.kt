package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp

data class AppDrawerSection(
    val title: String,
    val apps: List<InstalledApp>,
) {
    val displayTitle: String = "$title (${apps.size})"
}

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
                compareBy<AppDrawerSection> { section -> section.title.profileSectionSortPrefix() }
                    .thenBy { section -> section.title.letterSectionSortKey() },
            )

    private val InstalledApp.sectionTitle: String
        get() {
            val letterSection =
                label
                    .trim()
                    .firstOrNull()
                    ?.takeIf { character -> character.isLetter() }
                    ?.uppercaseChar()
                    ?.toString()
                    ?: OTHER_SECTION_TITLE

            return identity.profile.drawerProfilePrefix()
                ?.let { prefix -> "$prefix - $letterSection" }
                ?: letterSection
        }
}

private fun String.profileSectionSortPrefix(): String = substringBefore(delimiter = " - ", missingDelimiterValue = "")

private fun String.letterSectionSortKey(): String =
    substringAfter(delimiter = " - ", missingDelimiterValue = this)
        .takeUnless { section -> section == OTHER_SECTION_TITLE }
        ?: OTHER_SECTION_SORT_TITLE

private const val OTHER_SECTION_TITLE = "#"
private const val OTHER_SECTION_SORT_TITLE = "{"
