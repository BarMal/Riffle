package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.HomeLayout

data class AppListContext(
    val homeLayout: HomeLayout,
    val notificationCountsByPackage: Map<AppPackageName, Int>,
    val appShortcutsByApp: AppShortcutsByApp = emptyMap(),
    val appIconLoader: AppIconLoader,
    val onAction: (LauncherShellAction) -> Unit,
)
