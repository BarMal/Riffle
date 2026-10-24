package com.riffle.app.launcher

import com.riffle.app.launcher.onboarding.WalkthroughText
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntryId

internal fun settingsLauncherSearchEntries() = settingsLauncherSearchEntries(SettingsOverviewStatus())

internal fun settingsLauncherSearchEntries(status: SettingsOverviewStatus) =
    settingsMainPageEntries(status).map { entry -> entry.toLauncherSearchSettingsEntry() } + walkthroughSearchEntry

/**
 * "Set up your home again" lives in the Workspaces section at the top of the main settings page, so searching for it
 * opens that page.
 */
private val walkthroughSearchEntry =
    LauncherSearchSettingsEntry(
        id = SettingsPage.MAIN.launcherSearchEntryId,
        title = WalkthroughText.SETTINGS_ROW,
        subtitle = "Run the first-time walkthrough again",
        section = WorkspacesSettingsText.WORKSPACES_TITLE,
        searchAliases = listOf("walkthrough", "setup", "set up", "onboarding", "first-time setup"),
    )

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
