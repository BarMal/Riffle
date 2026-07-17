package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidUserProfilesTest {
    private val mapper = AndroidUserProfileMapper()

    @Test
    fun managedAndroidProfileMapsToAStableWorkProfile() {
        assertEquals(
            AppProfile(
                id = AppProfileId("user:42"),
                type = AppProfileType.WORK,
            ),
            mapper.map(
                AndroidUserProfile(
                    stableId = "42",
                    isCurrentUser = false,
                    type = AndroidUserProfileType.MANAGED,
                ),
            ),
        )
    }

    @Test
    fun currentAndroidUserRemainsThePersonalProfile() {
        assertEquals(
            AppProfile.personal(),
            mapper.map(
                AndroidUserProfile(
                    stableId = "0",
                    isCurrentUser = true,
                    type = AndroidUserProfileType.MANAGED,
                ),
            ),
        )
    }
}
