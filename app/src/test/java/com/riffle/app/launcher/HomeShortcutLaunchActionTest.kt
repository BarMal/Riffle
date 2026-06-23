package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShortcutLaunchActionTest {
    @Test
    fun launchesAppWhenShortcutHasNoPlatformShortcutId() {
        val shortcut = shortcutItem()

        val action = shortcut.launchAction()

        assertEquals(LauncherShellAction.LaunchApp(shortcut.appIdentity), action)
    }

    @Test
    fun launchesPlatformShortcutWhenShortcutHasPlatformShortcutId() {
        val shortcut = shortcutItem().copy(appShortcutId = AppShortcutId("compose"))

        val action = shortcut.launchAction()

        val launchAction = action as LauncherShellAction.LaunchAppShortcut
        assertEquals(AppShortcutId("compose"), launchAction.shortcut.id)
        assertEquals(shortcut.appIdentity, launchAction.shortcut.appIdentity)
        assertEquals(shortcut.label, launchAction.shortcut.shortLabel)
    }

    private fun shortcutItem(): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:messages"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.messages"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = "Messages",
        )
}
