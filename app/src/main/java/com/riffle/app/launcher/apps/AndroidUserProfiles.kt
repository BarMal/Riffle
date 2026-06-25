package com.riffle.app.launcher.apps

import android.os.Process
import android.os.UserHandle
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType

internal data class AndroidUserProfile(
    val stableId: String,
    val isCurrentUser: Boolean,
)

internal class AndroidUserProfileMapper {
    fun map(profile: AndroidUserProfile): AppProfile =
        if (profile.isCurrentUser) {
            AppProfile.personal()
        } else {
            AppProfile(
                id = AppProfileId("user:${profile.stableId}"),
                type = AppProfileType.WORK,
            )
        }
}

internal fun UserHandle.toAppProfile(
    currentUser: UserHandle = Process.myUserHandle(),
    mapper: AndroidUserProfileMapper = AndroidUserProfileMapper(),
): AppProfile =
    mapper.map(
        AndroidUserProfile(
            stableId = hashCode().toString(),
            isCurrentUser = this == currentUser,
        ),
    )

internal fun AppProfile.toUserHandle(
    userProfiles: List<UserHandle>,
    currentUser: UserHandle = Process.myUserHandle(),
): UserHandle =
    userProfiles.firstOrNull { user -> user.toAppProfile(currentUser = currentUser).id == id }
        ?: currentUser
