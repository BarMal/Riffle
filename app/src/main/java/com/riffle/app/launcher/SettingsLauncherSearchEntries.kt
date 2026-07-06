package com.riffle.app.launcher

import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntryId

internal fun settingsLauncherSearchEntries() = settingsLauncherSearchEntries(SettingsOverviewStatus())

internal fun settingsLauncherSearchEntries(status: SettingsOverviewStatus) =
    settingsMainPageEntries(status).map { entry -> entry.toLauncherSearchSettingsEntry() }

internal fun LauncherSearchSettingsEntryId.settingsPage(): SettingsPage? =
    SettingsPage.entries.firstOrNull { page -> page.launcherSearchEntryId == this }

private fun SettingsPageEntry.toLauncherSearchSettingsEntry(): LauncherSearchSettingsEntry =
    LauncherSearchSettingsEntry(
        id = page.launcherSearchEntryId,
        title = label,
        subtitle = subtitle,
        section = group.title,
        searchAliases = searchAliases + page.title,
    )

private val SettingsPage.launcherSearchEntryId: LauncherSearchSettingsEntryId
    get() = LauncherSearchSettingsEntryId(name.lowercase())
