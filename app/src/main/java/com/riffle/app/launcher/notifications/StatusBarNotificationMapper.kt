package com.riffle.app.launcher.notifications

import android.app.Notification
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.service.notification.StatusBarNotification
import android.util.Base64
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import java.io.ByteArrayOutputStream

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
                isMediaSession = notification.notification.extras?.get(Notification.EXTRA_MEDIA_SESSION) != null,
                title = notification.notification.titleText(),
                text = notification.notification.bodyText(),
                largeIconPngBase64 = notification.notification.largeIconPngBase64(),
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
            isMediaSession = snapshot.isMediaSession,
            title = snapshot.title,
            text = snapshot.text,
            largeIconPngBase64 = snapshot.largeIconPngBase64,
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
    val isMediaSession: Boolean = false,
    val title: String = "",
    val text: String = "",
    val largeIconPngBase64: String? = null,
    val postedAtEpochMillis: Long,
)

@Suppress("DEPRECATION")
private fun Notification.titleText(): String = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

@Suppress("DEPRECATION")
private fun Notification.bodyText(): String =
    sequenceOf(
        Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_TEXT,
        Notification.EXTRA_SUMMARY_TEXT,
        Notification.EXTRA_SUB_TEXT,
    ).mapNotNull { key ->
        extras?.getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotBlank)
    }.firstOrNull().orEmpty()

@Suppress("DEPRECATION")
private fun Notification.largeIconPngBase64(): String? =
    sequenceOf(
        extras?.getParcelable(Notification.EXTRA_LARGE_ICON_BIG) as? Bitmap,
        extras?.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap,
    ).filterNotNull()
        .mapNotNull(Bitmap::pngBase64OrNull)
        .firstOrNull()

private fun Bitmap.pngBase64OrNull(): String? =
    ByteArrayOutputStream().use { output ->
        if (!compress(Bitmap.CompressFormat.PNG, 100, output)) {
            null
        } else {
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }
