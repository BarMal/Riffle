package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
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

    @Test
    fun profileContentVisibilityRedactsQuietAndLockedProfiles() {
        assertEquals(
            AppProfileContentVisibility.REDACTED_QUIET,
            profileContentVisibility(isQuietModeEnabled = true, isUserUnlocked = true),
        )
        assertEquals(
            AppProfileContentVisibility.REDACTED_LOCKED,
            profileContentVisibility(isQuietModeEnabled = false, isUserUnlocked = false),
        )
    }
}
