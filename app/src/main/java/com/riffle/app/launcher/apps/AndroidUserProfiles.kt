package com.riffle.app.launcher.apps

import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType

internal data class AndroidUserProfile(
    val stableId: String,
    val isCurrentUser: Boolean,
    val type: AndroidUserProfileType = AndroidUserProfileType.MANAGED,
    val contentVisibility: AppProfileContentVisibility = AppProfileContentVisibility.VISIBLE,
)

internal enum class AndroidUserProfileType {
    PERSONAL,
    MANAGED,
    PRIVATE,
}

internal class AndroidUserProfileMapper {
    fun map(profile: AndroidUserProfile): AppProfile =
        if (profile.isCurrentUser) {
            AppProfile.personal()
        } else {
            AppProfile(
                id = AppProfileId("user:${profile.stableId}"),
                type =
                    when (profile.type) {
                        AndroidUserProfileType.PERSONAL -> AppProfileType.PERSONAL
                        AndroidUserProfileType.MANAGED -> AppProfileType.WORK
                        AndroidUserProfileType.PRIVATE -> AppProfileType.PRIVATE
                    },
                contentVisibility = profile.contentVisibility,
            )
        }
}

internal fun UserHandle.toAppProfile(
    currentUser: UserHandle = Process.myUserHandle(),
    userManager: UserManager? = null,
    launcherApps: LauncherApps? = null,
    mapper: AndroidUserProfileMapper = AndroidUserProfileMapper(),
): AppProfile =
    if (this == currentUser) {
        AppProfile.personal()
    } else {
        mapper.map(
            AndroidUserProfile(
                stableId = stableProfileId(userManager = userManager, launcherApps = launcherApps),
                isCurrentUser = false,
                type = androidUserProfileType(launcherApps = launcherApps),
                contentVisibility = profileContentVisibility(userManager),
            ),
        )
    }

internal fun AppProfile.toUserHandle(
    userProfiles: List<UserHandle>,
    currentUser: UserHandle = Process.myUserHandle(),
    userManager: UserManager? = null,
    launcherApps: LauncherApps? = null,
): UserHandle =
    userProfiles.firstOrNull { user ->
        user.toAppProfile(
            currentUser = currentUser,
            userManager = userManager,
            launcherApps = launcherApps,
        ).id == id
    }
        ?: currentUser

private fun UserHandle.stableProfileId(
    userManager: UserManager?,
    launcherApps: LauncherApps?,
): String =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
            launcherApps?.launcherUserInfo(this)
                ?.userSerialNumber
                ?.toString()
                ?: serialFromUserManager(userManager)

        else -> serialFromUserManager(userManager)
    }

private fun UserHandle.serialFromUserManager(userManager: UserManager?): String =
    userManager
        ?.getSerialNumberForUser(this)
        ?.takeIf { serial -> serial >= 0L }
        ?.toString()
        ?: hashCode().toString()

private fun UserHandle.androidUserProfileType(launcherApps: LauncherApps?): AndroidUserProfileType =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        when (launcherApps?.launcherUserInfo(this)?.userType) {
            UserManager.USER_TYPE_PROFILE_MANAGED -> AndroidUserProfileType.MANAGED
            UserManager.USER_TYPE_PROFILE_PRIVATE -> AndroidUserProfileType.PRIVATE
            else -> AndroidUserProfileType.PERSONAL
        }
    } else {
        AndroidUserProfileType.MANAGED
    }

private fun UserHandle.profileContentVisibility(userManager: UserManager?): AppProfileContentVisibility =
    runCatching {
        when {
            userManager?.isQuietModeEnabled(this) == true -> AppProfileContentVisibility.REDACTED_QUIET
            userManager?.isUserUnlocked(this) == false -> AppProfileContentVisibility.REDACTED_LOCKED
            else -> AppProfileContentVisibility.VISIBLE
        }
    }.getOrDefault(AppProfileContentVisibility.REDACTED_UNAVAILABLE)

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun LauncherApps.launcherUserInfo(user: UserHandle) = runCatching { getLauncherUserInfo(user) }.getOrNull()
