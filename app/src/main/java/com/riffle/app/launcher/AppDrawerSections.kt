package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp

data class AppDrawerSection(
    val title: String,
    val apps: List<InstalledApp>,
)

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

private const val OTHER_SECTION_TITLE = "#"
