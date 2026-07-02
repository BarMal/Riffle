package com.riffle.app.launcher.overlay

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDockShortcutsTest {
    @Test
    fun returnsOnlyInstalledFloatingDockShortcuts() {
        val cameraShortcut = shortcut(id = "camera", identity = cameraIdentity, label = "Camera")
        val missingShortcut = shortcut(id = "missing", identity = missingIdentity, label = "Missing")
        val settings = OverlayDockSettings(items = listOf(cameraShortcut, missingShortcut))

        val visibleShortcuts =
            settings.visibleOverlayDockShortcuts(
                installedApps = listOf(InstalledApp(identity = cameraIdentity, label = "Camera")),
            )

        assertEquals(listOf(cameraShortcut), visibleShortcuts)
    }

    private fun shortcut(
        id: String,
        identity: AppIdentity,
        label: String,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = identity,
            label = label,
        )

    private companion object {
        val cameraIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.camera"),
                activityName = AppActivityName(".CameraActivity"),
            )
        val missingIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.missing"),
                activityName = AppActivityName(".MissingActivity"),
            )
    }
}
