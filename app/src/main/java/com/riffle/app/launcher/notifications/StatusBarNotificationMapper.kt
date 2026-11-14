package com.riffle.app.launcher.notifications

import android.app.Notification
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.service.notification.StatusBarNotification
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.notifications.NotificationGroupSummaries
import com.riffle.core.domain.launcher.notifications.NotificationThreadSummary
import com.riffle.core.domain.launcher.notifications.NotificationTitleRule
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
    /** The app's own name, by package: a title that only repeats it is not a title (`NotificationTitleRule`). */
    private val appLabelFor: (String) -> String? = { null },
) {
    constructor(
        currentUser: UserHandle = Process.myUserHandle(),
        userManager: UserManager? = null,
        launcherApps: LauncherApps? = null,
        appLabelFor: (String) -> String? = { null },
    ) : this(
        appLabelFor = appLabelFor,
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
                titleBig = notification.notification.extraText(Notification.EXTRA_TITLE_BIG),
                conversationTitle = notification.notification.extraText(Notification.EXTRA_CONVERSATION_TITLE),
                messagingStyle = notification.notification.isMessagingStyle(),
                appLabel = runCatching { appLabelFor(notification.packageName) }.getOrNull(),
                text = notification.notification.bodyText(),
                largeIconPngBase64 = notification.notification.largeIconPngBase64(),
                messages = notification.notification.messagingStyleMessages(),
                postedAtEpochMillis = notification.postTime,
            ),
        )

    /**
     * The posted notifications a launcher lists: every one but a group's summary that has children posted beside it
     * (`NotificationGroupSummaries`), so a conversation is one card and its group's header is not a card of its own.
     */
    fun listed(posted: Array<StatusBarNotification>): List<StatusBarNotification> {
        val redundant =
            NotificationGroupSummaries.redundant(
                posted.map { sbn ->
                    NotificationGroupSummaries.Posted(
                        key = sbn.key,
                        groupKey = sbn.groupKey.takeIf { sbn.isGroup },
                        isSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                    )
                },
            )
        return posted.filter { it.key !in redundant }
    }

    fun map(snapshot: StatusBarNotificationSnapshot): LauncherNotification {
        val resolved =
            NotificationTitleRule.resolve(
                NotificationTitleRule.Input(
                    title = snapshot.title,
                    titleBig = snapshot.titleBig,
                    conversationTitle = snapshot.conversationTitle,
                    messagingStyle = snapshot.messagingStyle || snapshot.messages.isNotEmpty(),
                    lastSender = snapshot.messages.lastOrNull()?.sender,
                    text = snapshot.text,
                    appLabel = snapshot.appLabel,
                ),
            )
        return snapshot.toLauncherNotification(resolved)
    }

    private fun StatusBarNotificationSnapshot.toLauncherNotification(
        resolved: NotificationTitleRule.Resolved,
    ): LauncherNotification {
        val snapshot = this
        return LauncherNotification(
            key = LauncherNotificationKey(snapshot.key),
            packageName = AppPackageName(snapshot.packageName),
            profileId = snapshot.profileId,
            category = snapshot.category.toLauncherNotificationCategory(),
            priority = snapshot.priority.toLauncherNotificationPriority(),
            canDismiss = snapshot.canDismiss,
            isMediaSession = snapshot.isMediaSession,
            title = resolved.title,
            text = resolved.text,
            largeIconPngBase64 = snapshot.largeIconPngBase64,
            messages = snapshot.messages,
            postedAtEpochMillis = snapshot.postedAtEpochMillis,
        )
    }
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
    /** `EXTRA_TITLE_BIG`: the title of the expanded form, which a style may set instead of the title. */
    val titleBig: String? = null,
    /** `EXTRA_CONVERSATION_TITLE`: a group conversation's name. */
    val conversationTitle: String? = null,
    /** The notification uses a messaging style: its title is a person or a group. */
    val messagingStyle: Boolean = false,
    /** The posting app's own name, when the launcher can read it. */
    val appLabel: String? = null,
    val text: String = "",
    val largeIconPngBase64: String? = null,
    val messages: List<LauncherNotificationMessage> = emptyList(),
    val postedAtEpochMillis: Long,
)

@Suppress("DEPRECATION")
private fun Notification.titleText(): String = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

private fun Notification.extraText(key: String): String? =
    runCatching { extras?.getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotBlank) }.getOrNull()

private fun Notification.isMessagingStyle(): Boolean =
    runCatching { extras?.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MessagingStyle") == true }.getOrDefault(
        false,
    )

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

/**
 * Falls back to an empty list for notifications not posted with a messaging style (most
 * non-messaging apps, and group-summary notifications) -- callers already fall back to
 * [titleText]/[bodyText] in that case, so no separate heuristic is needed here. Uses the AndroidX
 * compat extractor (not the platform `Notification.MessagingStyle`, which has no equivalent
 * static parser) so this works the same way across the app's supported API levels.
 *
 * Wrapped in runCatching like [bitmapExtraOrNull]: extractMessagingStyleFromNotification parses
 * the same extras Bundle that can hold a Person/Icon/Bitmap of a type the platform's own
 * deprecated getters throw ClassCastException for rather than returning null (see
 * [bitmapExtraOrNull]'s own doc) -- an app whose MessagingStyle payload trips that, e.g. a rich
 * per-message avatar shape, would otherwise fail this notification's entire mapping and silently
 * drop it from the launcher altogether, not just lose its per-message history.
 */
private fun Notification.messagingStyleMessages(): List<LauncherNotificationMessage> =
    runCatching {
        NotificationThreadSummary.withoutEchoes(
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(this)
                ?.messages
                .orEmpty()
                .map { message ->
                    LauncherNotificationMessage(
                        sender =
                            message.person?.name?.toString()?.takeIf(String::isNotBlank)
                                ?: message.sender?.toString().orEmpty(),
                        text = message.text?.toString().orEmpty(),
                        timestampEpochMillis = message.timestamp,
                    )
                },
        ).takeLast(MAX_MESSAGING_STYLE_MESSAGES)
    }.getOrDefault(emptyList())

@Suppress("DEPRECATION")
private fun Notification.largeIconPngBase64(): String? =
    sequenceOf(
        extras?.bitmapExtraOrNull(Notification.EXTRA_LARGE_ICON_BIG),
        extras?.bitmapExtraOrNull(Notification.EXTRA_LARGE_ICON),
    ).filterNotNull()
        .mapNotNull(Bitmap::pngBase64OrNull)
        .firstOrNull()

/**
 * The platform stores a notification's large icon as either a [Bitmap] or an [android.graphics.drawable.Icon]
 * depending on how the posting app built it. The deprecated single-arg `getParcelable` throws
 * ClassCastException rather than returning null when the stored type doesn't match what's requested here,
 * so this must not use a plain `as? Bitmap` (a safe cast only guards the cast itself, not the throwing getter).
 */
@Suppress("DEPRECATION")
private fun Bundle.bitmapExtraOrNull(key: String): Bitmap? = runCatching { getParcelable(key) as? Bitmap }.getOrNull()

private fun Bitmap.pngBase64OrNull(): String? =
    ByteArrayOutputStream().use { output ->
        if (!compress(Bitmap.CompressFormat.PNG, 100, output)) {
            null
        } else {
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }

private const val MAX_MESSAGING_STYLE_MESSAGES = 3
