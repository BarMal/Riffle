package com.riffle.app.launcher

import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry

/**
 * The settings the launcher's search can offer, read through the text source the activity installed; none until then
 * (and in tests that install nothing). The Privacy and access and Library pages carry their live state, as their rows
 * on the main page do.
 */
internal fun settingsLauncherSearchEntries(
    status: SettingsOverviewStatus = SettingsOverviewStatus(),
): List<LauncherSearchSettingsEntry> {
    val text = SettingsTextHolder.current ?: return emptyList()
    return SettingsTextHolder.launcherSearchEntries().map { entry ->
        val page = entry.id.settingsTarget()?.takeIf { it.anchor == null }?.page
        if (page == SettingsPage.PRIVACY || page == SettingsPage.LIBRARY) {
            entry.copy(subtitle = status.rowSummary(page, text))
        } else {
            entry
        }
    }
}
