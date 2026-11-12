package com.riffle.app.launcher

import com.riffle.app.launcher.onboarding.OnboardingText
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntryId

internal fun settingsLauncherSearchEntries() = settingsLauncherSearchEntries(SettingsOverviewStatus())

internal fun settingsLauncherSearchEntries(status: SettingsOverviewStatus) =
    settingsMainPageEntries(status).map { entry -> entry.toLauncherSearchSettingsEntry() } + walkthroughSearchEntry

/** "Set up Riffle again" is in the Set up section atop the main settings page, so searching for it opens that. */
private val walkthroughSearchEntry =
    LauncherSearchSettingsEntry(
        id = SettingsPage.MAIN.launcherSearchEntryId,
        title = OnboardingText.SETTINGS_ROW,
        subtitle = "Run the four-step set-up again",
        section = OnboardingText.SETTINGS_SECTION,
        searchAliases = listOf("walkthrough", "setup", "set up", "onboarding", "first-time setup", "tour"),
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
