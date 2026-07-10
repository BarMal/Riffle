package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDockItemsSettingTest {
    @Test
    fun uniqueFloatingDockLabelDoesNotShowSubtitle() {
        val item = shortcut(label = "Camera", packageName = "com.riffle.camera")

        assertEquals(
            null,
            item.overlayDockItemSubtitle(
                duplicateLabels = listOf(item).overlayDockDuplicateLabels(),
            ),
        )
    }

    @Test
    fun duplicateFloatingDockLabelsUseProfileAwarePackageSubtitle() {
        val personalCamera =
            shortcut(
                id = "camera-personal",
                label = "Camera",
                packageName = "com.riffle.camera",
                profile = AppProfile.personal(),
            )
        val workCamera =
            shortcut(
                id = "camera-work",
                label = "Camera",
                packageName = "com.riffle.camera.work",
                profile = AppProfile.work(),
            )
        val duplicateLabels = listOf(personalCamera, workCamera).overlayDockDuplicateLabels()

        assertEquals("com.riffle.camera", personalCamera.overlayDockItemSubtitle(duplicateLabels))
        assertEquals("Work - com.riffle.camera.work", workCamera.overlayDockItemSubtitle(duplicateLabels))
    }

    private fun shortcut(
        id: String = "camera",
        label: String,
        packageName: String,
        profile: AppProfile = AppProfile.personal(),
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = label,
        )
}
