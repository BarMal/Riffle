package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

enum class SettingsPage(
    val title: String,
) {
    MAIN("Settings"),
    LAYOUT("Layout"),
    DOCK("Dock"),
    APPEARANCE("Appearance"),
    ADAPTIVE_STAGE_APPEARANCE("Cards appearance"),
    FLOATING_DOCK("Floating dock"),
    GESTURES("Gestures"),
    CONTEXTUAL("Contextual"),
    MOTION("Motion & haptics"),
    PERMISSIONS("Permissions"),
    APPS("App drawer"),
    RSS("RSS feeds"),
    BACKUP("Backup"),
    HIDDEN_APPS("Hidden apps"),
    VERSION("About"),
}

internal enum class SettingsPageGroup(
    val title: String,
) {
    HOME("Home & layout"),
    APPEARANCE("Appearance"),
    INTERACTION("Interaction & accessibility"),
    APPS("Apps & content"),
    SYSTEM("Permissions, privacy & backup"),
}

internal data class SettingsPageEntry(
    val label: String,
    val subtitle: String,
    val page: SettingsPage,
    val group: SettingsPageGroup,
    val searchAliases: List<String> = emptyList(),
)

internal data class SettingsOverviewStatus(
    val homeRoleStatus: HomeRoleStatus = HomeRoleStatus.UNKNOWN,
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val overlayDockPermissionStatus: OverlayDockPermissionStatus = OverlayDockPermissionStatus.UNKNOWN,
    val hiddenAppCount: Int = 0,
)

internal fun SettingsSurfaceState.settingsOverviewStatus(): SettingsOverviewStatus =
    SettingsOverviewStatus(
        homeRoleStatus = homeRoleStatus,
        notificationAccessStatus = notificationAccessStatus,
        overlayDockPermissionStatus = overlayDockPermissionStatus,
        hiddenAppCount = hiddenApps.size,
    )

internal fun settingsMainPageGroups(): List<SettingsPageGroup> =
    listOf(
        SettingsPageGroup.HOME,
        SettingsPageGroup.APPEARANCE,
        SettingsPageGroup.INTERACTION,
        SettingsPageGroup.APPS,
        SettingsPageGroup.SYSTEM,
    )

internal fun settingsMainPageEntriesMatching(
    query: String,
    status: SettingsOverviewStatus = SettingsOverviewStatus(),
): List<SettingsPageEntry> {
    val tokens =
        query
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
    if (tokens.isEmpty()) return settingsMainPageEntries(status)

    return settingsMainPageEntries(status)
        .filter { entry ->
            val searchableText =
                listOf(
                    entry.label,
                    entry.subtitle,
                    entry.group.title,
                    entry.page.title,
                    entry.searchAliases.joinToString(separator = " "),
                ).joinToString(separator = " ").lowercase()
            tokens.all { token -> searchableText.contains(token) }
        }
}

internal fun settingsSearchSummaryText(
    query: String,
    resultCount: Int,
): String? =
    query
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let { trimmedQuery -> "${resultCount.settingsResultCountLabel()} matching \"$trimmedQuery\"" }

internal fun settingsLayoutPageTabs(availableDeviceClasses: Set<HomeLayoutDeviceClass>): List<SettingsLayoutDeviceTab> =
    settingsLayoutDeviceTabs(availableDeviceClasses)

private fun Int.settingsResultCountLabel(): String =
    when (this) {
        1 -> "1 setting"
        else -> "$this settings"
    }
