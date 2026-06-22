package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppProfileLabelsTest {
    @Test
    fun defaultPersonalAppsShowPackageOnly() {
        assertEquals("com.riffle.camera", app(profile = AppProfile.personal()).drawerSubtitle())
    }

    @Test
    fun workAppsShowProfilePrefix() {
        assertEquals("Work - com.riffle.camera", app(profile = AppProfile.work()).drawerSubtitle())
    }

    @Test
    fun customPersonalProfilesShowProfilePrefix() {
        val profile = AppProfile(id = AppProfileId("private"), type = AppProfileType.PERSONAL)

        assertEquals("Personal - com.riffle.camera", app(profile = profile).drawerSubtitle())
    }

    private fun app(profile: AppProfile): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.camera"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = "Camera",
        )
}
