package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

internal enum class SettingsPage(
    val title: String,
) {
    MAIN("Settings"),
    LAYOUT("Layout"),
    DOCK("Dock"),
    APPEARANCE("Appearance"),
    FLOATING_DOCK("Floating dock"),
    GESTURES("Gestures"),
    MOTION("Motion"),
    HAPTICS("Haptics"),
    PERMISSIONS("Permissions"),
    APPS("App drawer"),
    BACKUP("Backup"),
    HIDDEN_APPS("Hidden apps"),
    VERSION("About"),
}

internal enum class SettingsPageGroup(
    val title: String,
) {
    HOME("Home screen"),
    INTERACTION("Interaction"),
    APPS("Apps"),
    SYSTEM("System"),
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

internal fun settingsMainPageEntries(status: SettingsOverviewStatus = SettingsOverviewStatus()) =
    listOf(
        SettingsPageEntry(
            label = "Layout",
            subtitle = "Home mode, grid, and labels",
            page = SettingsPage.LAYOUT,
            group = SettingsPageGroup.HOME,
        ),
        SettingsPageEntry(
            label = "Dock",
            subtitle = "Home dock visibility, slots, and sizing",
            page = SettingsPage.DOCK,
            group = SettingsPageGroup.HOME,
        ),
        SettingsPageEntry(
            label = "Appearance",
            subtitle = "Wallpaper and system bars",
            page = SettingsPage.APPEARANCE,
            group = SettingsPageGroup.HOME,
            searchAliases = listOf("status bar", "navigation bar", "fullscreen"),
        ),
        SettingsPageEntry(
            label = "Floating dock",
            subtitle = "Overlay handle and floating shortcuts",
            page = SettingsPage.FLOATING_DOCK,
            group = SettingsPageGroup.HOME,
        ),
        SettingsPageEntry(
            label = "Gestures",
            subtitle = "Home swipe actions",
            page = SettingsPage.GESTURES,
            group = SettingsPageGroup.INTERACTION,
        ),
        SettingsPageEntry(
            label = "Motion",
            subtitle = "Reduced motion",
            page = SettingsPage.MOTION,
            group = SettingsPageGroup.INTERACTION,
        ),
        SettingsPageEntry(
            label = "Haptics",
            subtitle = "Feedback strength",
            page = SettingsPage.HAPTICS,
            group = SettingsPageGroup.INTERACTION,
        ),
        SettingsPageEntry(
            label = "App drawer",
            subtitle = "Refresh installed apps",
            page = SettingsPage.APPS,
            group = SettingsPageGroup.APPS,
        ),
        SettingsPageEntry(
            label = "Hidden apps",
            subtitle = status.hiddenAppsSummary(),
            page = SettingsPage.HIDDEN_APPS,
            group = SettingsPageGroup.APPS,
        ),
        SettingsPageEntry(
            label = "Permissions",
            subtitle = status.permissionsSummary(),
            page = SettingsPage.PERMISSIONS,
            group = SettingsPageGroup.SYSTEM,
            searchAliases = listOf("default home", "home app"),
        ),
        SettingsPageEntry(
            label = "Backup",
            subtitle = "Import and export launcher data",
            page = SettingsPage.BACKUP,
            group = SettingsPageGroup.SYSTEM,
            searchAliases = listOf("restore"),
        ),
        SettingsPageEntry(
            label = "About",
            subtitle = "Version and build information",
            page = SettingsPage.VERSION,
            group = SettingsPageGroup.SYSTEM,
        ),
    )

internal fun settingsMainPageGroups(): List<SettingsPageGroup> =
    listOf(
        SettingsPageGroup.HOME,
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

private fun SettingsOverviewStatus.hiddenAppsSummary(): String =
    when (hiddenAppCount) {
        0 -> "No hidden apps"
        1 -> "1 hidden app"
        else -> "$hiddenAppCount hidden apps"
    }

private fun SettingsOverviewStatus.permissionsSummary(): String =
    listOf(
        homeRoleStatus.settingsOverviewLabel(),
        "notifications ${notificationAccessStatus.settingsNotificationAccessLabel().lowercase()}",
        "overlay ${overlayDockPermissionStatus.settingsOverlayDockPermissionLabel().lowercase()}",
    ).joinToString(separator = ", ")

private fun HomeRoleStatus.settingsOverviewLabel(): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> "Home set"
        HomeRoleStatus.NOT_DEFAULT_HOME -> "Home not set"
        HomeRoleStatus.UNKNOWN -> "Home unknown"
    }
