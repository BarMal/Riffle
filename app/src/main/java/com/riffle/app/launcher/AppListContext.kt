package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.containsHomeApp
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
    /** Where a held app can be carried to Home as a drag; null: not offered (Simple mode, over the Stream). */
    val homeDrag: LibraryHomeDrag? = null,
)

internal fun AppListContext.notificationCountFor(identity: AppIdentity): Int {
    return notificationGroupsByApp.notificationCountFor(identity)
}

/** The Library's drag to Home for [app]: offered only while Home does not show the app (as "Add to Home" is). */
internal fun AppListContext.homeDragFor(app: com.riffle.core.domain.launcher.apps.InstalledApp): LibraryHomeDrag? =
    homeDrag.takeUnless { homeLayout.containsHomeApp(app.identity) }
