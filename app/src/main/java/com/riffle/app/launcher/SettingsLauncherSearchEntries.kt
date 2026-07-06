package com.riffle.app.launcher

import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntryId

internal fun settingsLauncherSearchEntries() = settingsLauncherSearchEntries(SettingsOverviewStatus())

internal fun settingsLauncherSearchEntries(status: SettingsOverviewStatus) =
    settingsMainPageEntries(status).map { entry -> entry.toLauncherSearchSettingsEntry() }

private fun SettingsPageEntry.toLauncherSearchSettingsEntry(): LauncherSearchSettingsEntry =
    LauncherSearchSettingsEntry(
        id = LauncherSearchSettingsEntryId(page.name.lowercase()),
        title = label,
        subtitle = subtitle,
        section = group.title,
        searchAliases = searchAliases + page.title,
    )
