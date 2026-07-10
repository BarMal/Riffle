package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

data class AppListContext(
    val homeLayout: HomeLayout,
    val overlayDock: OverlayDockSettings,
    val notificationGroupsByApp: List<AppNotificationGroup>,
    val appShortcutsByApp: AppShortcutsByApp = emptyMap(),
    val appIconLoader: AppIconLoader,
    val haptics: LauncherHaptics = NoopLauncherHaptics,
    val onAction: (LauncherShellAction) -> Unit,
)

internal fun AppListContext.notificationCountFor(identity: AppIdentity): Int {
    return notificationGroupsByApp.notificationCountFor(identity)
}
