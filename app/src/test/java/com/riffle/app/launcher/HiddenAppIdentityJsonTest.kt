package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenAppIdentityJsonTest {
    @Test
    fun roundTripsHiddenAppIdentitiesWithProfileContext() {
        val personalCamera =
            identity(
                packageName = "com.riffle.camera",
                activityName = ".MainActivity",
                profile = AppProfile.personal(),
            )
        val workCamera =
            identity(
                packageName = "com.riffle.camera",
                activityName = ".MainActivity",
                profile = AppProfile(AppProfileId("company"), AppProfileType.WORK),
            )

        assertEquals(
            setOf(personalCamera, workCamera),
            decodeHiddenAppIdentities(encodeHiddenAppIdentities(setOf(personalCamera, workCamera))),
        )
    }

    private fun identity(
        packageName: String,
        activityName: String,
        profile: AppProfile,
    ): AppIdentity =
        AppIdentity(
            packageName = AppPackageName(packageName),
            activityName = AppActivityName(activityName),
            profile = profile,
        )
}
