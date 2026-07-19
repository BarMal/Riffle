package com.riffle.app.launcher.overlay

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.RecentAppUsage
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

    @Test
    fun resolvesRecentPackagesToDistinctLaunchableShortcutsInUsageOrder() {
        val settings = OverlayDockSettings(items = listOf(shortcut("camera", cameraIdentity, "Camera")))
        val galleryIdentity =
            AppIdentity(
                AppPackageName("com.example.gallery"),
                AppActivityName(".GalleryActivity"),
            )

        val content =
            settings.contentFor(
                installedApps =
                    listOf(
                        InstalledApp(identity = cameraIdentity, label = "Camera"),
                        InstalledApp(identity = galleryIdentity, label = "Gallery"),
                    ),
                recentAppUsages =
                    listOf(
                        RecentAppUsage(AppPackageName("com.example.gallery"), 300),
                        RecentAppUsage(AppPackageName("com.example.camera"), 200),
                        RecentAppUsage(AppPackageName("com.example.gallery"), 100),
                        RecentAppUsage(AppPackageName("com.example.removed"), 50),
                    ),
            )

        assertEquals(listOf("Camera"), content.pinnedShortcuts.map { it.label })
        assertEquals(listOf("Gallery", "Camera"), content.recentShortcuts.map { it.label })
        assertEquals(
            listOf("overlay-recent:com.example.gallery", "overlay-recent:com.example.camera"),
            content.recentShortcuts.map { it.id.value },
        )
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
