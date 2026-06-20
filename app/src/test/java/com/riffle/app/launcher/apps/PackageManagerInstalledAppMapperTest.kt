package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIconKey
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PackageManagerInstalledAppMapperTest {
    private val mapper = PackageManagerInstalledAppMapper()

    @Test
    fun mapsLaunchableActivityToInstalledAppIdentity() {
        val app =
            mapper.map(
                LaunchableActivity(
                    packageName = "com.android.camera",
                    activityName = ".CameraActivity",
                    label = "Camera",
                    profile = AppProfile.work(),
                ),
            )

        assertEquals(AppPackageName("com.android.camera"), app.identity.packageName)
        assertEquals(AppActivityName(".CameraActivity"), app.identity.activityName)
        assertEquals(AppProfile.work(), app.identity.profile)
        assertEquals("Camera", app.label)
    }

    @Test
    fun fallsBackToPackageNameWhenLabelIsBlank() {
        val app =
            mapper.map(
                LaunchableActivity(
                    packageName = "com.android.camera",
                    activityName = ".CameraActivity",
                    label = "",
                ),
            )

        assertEquals("com.android.camera", app.label)
    }

    @Test
    fun usesStablePackageAndActivityIconKey() {
        val app =
            mapper.map(
                LaunchableActivity(
                    packageName = "com.android.camera",
                    activityName = ".CameraActivity",
                    label = "Camera",
                ),
            )

        assertEquals(AppIconKey("com.android.camera/.CameraActivity"), app.iconKey)
    }

    @Test
    fun preservesDisabledState() {
        val app =
            mapper.map(
                LaunchableActivity(
                    packageName = "com.android.camera",
                    activityName = ".CameraActivity",
                    label = "Camera",
                    enabled = false,
                ),
            )

        assertFalse(app.enabled)
    }
}
