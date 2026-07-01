package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidUserProfileMapperTest {
    private val mapper = AndroidUserProfileMapper()

    @Test
    fun mapsCurrentUserToPersonalProfile() {
        val profile =
            mapper.map(
                AndroidUserProfile(
                    stableId = "0",
                    isCurrentUser = true,
                    type = AndroidUserProfileType.PERSONAL,
                ),
            )

        assertEquals(AppProfile.personal(), profile)
    }

    @Test
    fun mapsOtherUserToWorkProfile() {
        val profile =
            mapper.map(
                AndroidUserProfile(
                    stableId = "10",
                    isCurrentUser = false,
                    type = AndroidUserProfileType.MANAGED,
                ),
            )

        assertEquals(
            AppProfile(
                id = AppProfileId("user:10"),
                type = AppProfileType.WORK,
            ),
            profile,
        )
    }

    @Test
    fun mapsPrivateUserToPrivateProfile() {
        val profile =
            mapper.map(
                AndroidUserProfile(
                    stableId = "20",
                    isCurrentUser = false,
                    type = AndroidUserProfileType.PRIVATE,
                ),
            )

        assertEquals(
            AppProfile(
                id = AppProfileId("user:20"),
                type = AppProfileType.PRIVATE,
            ),
            profile,
        )
    }

    @Test
    fun mapsSecondaryPersonalUserToPersonalProfile() {
        val profile =
            mapper.map(
                AndroidUserProfile(
                    stableId = "30",
                    isCurrentUser = false,
                    type = AndroidUserProfileType.PERSONAL,
                ),
            )

        assertEquals(
            AppProfile(
                id = AppProfileId("user:30"),
                type = AppProfileType.PERSONAL,
            ),
            profile,
        )
    }
}
