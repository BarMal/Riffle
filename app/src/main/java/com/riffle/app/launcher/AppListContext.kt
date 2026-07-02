package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

data class AppListContext(
    val homeLayout: HomeLayout,
    val overlayDock: OverlayDockSettings,
    val notificationCountsByPackage: Map<AppPackageName, Int>,
    val appShortcutsByApp: AppShortcutsByApp = emptyMap(),
    val appIconLoader: AppIconLoader,
    val haptics: LauncherHaptics = NoopLauncherHaptics,
    val onAction: (LauncherShellAction) -> Unit,
)
