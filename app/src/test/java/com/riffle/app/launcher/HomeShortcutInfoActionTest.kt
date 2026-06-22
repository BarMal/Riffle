package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShortcutInfoActionTest {
    @Test
    fun openAppInfoActionUsesShortcutIdentity() {
        val identity =
            AppIdentity(
                packageName = AppPackageName("com.example.camera"),
                activityName = AppActivityName(".CameraActivity"),
            )
        val shortcut =
            AppShortcutItem(
                id = LauncherItemId("camera"),
                appIdentity = identity,
                label = "Camera",
            )

        assertEquals(LauncherShellAction.OpenAppInfo(identity), shortcut.openAppInfoAction())
    }
}
