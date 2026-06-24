package com.riffle.app.launcher.apps

import android.content.Intent
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAppLauncherTest {
    @Test
    fun launcherLaunchFlagsStartAppsInTheirOwnTask() {
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            LAUNCHER_LAUNCH_FLAGS,
        )
    }

    @Test
    fun appInfoIntentUsesAndroidPackageSettingsPieces() {
        val identity = appIdentity(packageName = "com.example.camera")

        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", APP_INFO_ACTION)
        assertEquals("package:com.example.camera", identity.appInfoPackageUriString())
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, APP_INFO_FLAGS)
    }

    @Test
    fun uninstallIntentUsesAndroidPackageDeletePieces() {
        val identity = appIdentity(packageName = "com.example.camera")

        assertEquals(Intent.ACTION_DELETE, UNINSTALL_ACTION)
        assertEquals("package:com.example.camera", identity.uninstallPackageUriString())
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, UNINSTALL_FLAGS)
    }

    private fun appIdentity(packageName: String): AppIdentity =
        AppIdentity(
            packageName = AppPackageName(packageName),
            activityName = AppActivityName(".MainActivity"),
        )
}
