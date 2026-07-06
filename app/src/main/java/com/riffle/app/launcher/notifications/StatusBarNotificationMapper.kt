package com.riffle.app.launcher.notifications

import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.service.notification.StatusBarNotification
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

@Suppress("DEPRECATION")
fun StatusBarNotification.toLauncherNotification(
    currentUser: UserHandle = Process.myUserHandle(),
    userManager: UserManager? = null,
    launcherApps: LauncherApps? = null,
): LauncherNotification =
    StatusBarNotificationMapper(
        currentUser = currentUser,
        userManager = userManager,
        launcherApps = launcherApps,
    ).map(this)

internal class StatusBarNotificationMapper(
    private val profileIdForUser: (UserHandle) -> AppProfileId,
) {
    constructor(
        currentUser: UserHandle = Process.myUserHandle(),
        userManager: UserManager? = null,
        launcherApps: LauncherApps? = null,
    ) : this(
        profileIdForUser = { user ->
            user.toAppProfile(
                currentUser = currentUser,
                userManager = userManager,
                launcherApps = launcherApps,
            ).id
        },
    )

    @Suppress("DEPRECATION")
    fun map(notification: StatusBarNotification): LauncherNotification =
        map(
            StatusBarNotificationSnapshot(
                key = notification.key,
                packageName = notification.packageName,
                profileId = profileIdForUser(notification.user),
                category = notification.notification.category,
                priority = notification.notification.priority,
                canDismiss = notification.isClearable,
                postedAtEpochMillis = notification.postTime,
            ),
        )

    fun map(snapshot: StatusBarNotificationSnapshot): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(snapshot.key),
            packageName = AppPackageName(snapshot.packageName),
            profileId = snapshot.profileId,
            category = snapshot.category.toLauncherNotificationCategory(),
            priority = snapshot.priority.toLauncherNotificationPriority(),
            canDismiss = snapshot.canDismiss,
            postedAtEpochMillis = snapshot.postedAtEpochMillis,
        )
}

internal data class StatusBarNotificationSnapshot(
    val key: String,
    val packageName: String,
    val profileId: AppProfileId = AppProfile.personal().id,
    val category: String? = null,
    val priority: Int = Int.MIN_VALUE,
    val canDismiss: Boolean = false,
    val postedAtEpochMillis: Long,
)
