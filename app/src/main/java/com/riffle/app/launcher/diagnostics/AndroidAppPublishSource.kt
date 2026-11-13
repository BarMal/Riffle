package com.riffle.app.launcher.diagnostics

import android.app.Notification
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.riffle.app.launcher.apps.toUserHandle
import com.riffle.app.launcher.notifications.RiffleNotificationListenerConnection
import com.riffle.app.launcher.notifications.RiffleNotificationListenerService
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.math.roundToInt

/**
 * Reads what one app publishes to a launcher: shortcuts, posted notifications, media sessions and widget providers.
 * Read-only and transient: raw platform values are copied into the inputs of `AppPublishModels.kt` and reduced to
 * shapes there; nothing is stored or logged. Notifications come from the existing notification listener connection and
 * media sessions through the same listener component, so this adds no listener of its own. Blocks briefly: call it off
 * the main thread.
 */
internal class AndroidAppPublishSource(
    private val context: Context,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val mediaSessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, RiffleNotificationListenerService::class.java)

    /** Packages with a notification posted or a media session active right now. */
    fun packagesWithLiveContent(): Set<String> =
        buildSet {
            RiffleNotificationListenerConnection.activeNotifications()?.forEach { add(it.packageName) }
            activeControllers()?.forEach { add(it.packageName) }
        }

    fun load(app: InstalledApp): AppPublishReport {
        val packageName = app.identity.packageName.value
        return AppPublishReport(
            appLabel = app.label,
            packageName = packageName,
            shortcuts = shortcuts(app),
            notifications = notifications(packageName),
            media = media(packageName),
            widgets = widgets(app),
        )
    }

    private fun shortcuts(app: InstalledApp): PublishSection<ShortcutShape> {
        val apps = launcherApps
        return when {
            apps == null -> PublishSection(problem = SectionProblem.UNAVAILABLE)
            !hasShortcutAccess(apps) -> PublishSection(problem = SectionProblem.LAUNCHER_ROLE_MISSING)
            else ->
                runCatching {
                    val query =
                        LauncherApps.ShortcutQuery()
                            .setPackage(app.identity.packageName.value)
                            .setQueryFlags(SHORTCUT_QUERY_FLAGS)
                    PublishSection(
                        items = apps.getShortcuts(query, userHandle(app)).orEmpty().map { it.toInput().toShape() },
                    )
                }.getOrElse { failure ->
                    PublishSection(problem = failure.toProblem(SectionProblem.LAUNCHER_ROLE_MISSING))
                }
        }
    }

    private fun hasShortcutAccess(apps: LauncherApps): Boolean =
        runCatching { apps.hasShortcutHostPermission() }.getOrDefault(false)

    private fun notifications(packageName: String): PublishSection<NotificationShape> =
        runCatching {
            val posted =
                RiffleNotificationListenerConnection.activeNotifications()
                    ?: return PublishSection(problem = SectionProblem.NOTIFICATION_ACCESS_MISSING)
            PublishSection(
                items = posted.filter { it.packageName == packageName }.map { it.toInput().toShape() },
            )
        }.getOrElse { PublishSection(problem = SectionProblem.UNAVAILABLE) }

    private fun media(packageName: String): PublishSection<MediaShape> =
        runCatching {
            val controllers =
                activeControllers() ?: return PublishSection(problem = SectionProblem.NOTIFICATION_ACCESS_MISSING)
            PublishSection(items = controllers.filter { it.packageName == packageName }.map { it.toShape() })
        }.getOrElse { PublishSection(problem = SectionProblem.UNAVAILABLE) }

    /** Null when the platform refuses (no notification access). */
    private fun activeControllers(): List<MediaController>? =
        runCatching { mediaSessionManager?.getActiveSessions(listenerComponent) }.getOrNull()

    private fun widgets(app: InstalledApp): PublishSection<WidgetShape> =
        runCatching {
            val density = context.resources.displayMetrics.density
            val providers =
                AppWidgetManager.getInstance(context)
                    .getInstalledProvidersForPackage(app.identity.packageName.value, userHandle(app))
            PublishSection(
                items =
                    providers.map { provider ->
                        WidgetShape(
                            label = provider.loadLabel(context.packageManager).orEmpty(),
                            minWidthDp = (provider.minWidth / density).roundToInt(),
                            minHeightDp = (provider.minHeight / density).roundToInt(),
                        )
                    },
            )
        }.getOrElse { PublishSection(problem = SectionProblem.UNAVAILABLE) }

    private fun userHandle(app: InstalledApp): UserHandle =
        app.identity.profile.toUserHandle(
            userProfiles = runCatching { launcherApps?.profiles }.getOrNull() ?: userManager.userProfiles,
            currentUser = Process.myUserHandle(),
            userManager = userManager,
            launcherApps = launcherApps,
        )
}

private const val SHORTCUT_QUERY_FLAGS: Int =
    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
        LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED

private fun Throwable.toProblem(securityProblem: SectionProblem): SectionProblem =
    if (this is SecurityException) securityProblem else SectionProblem.UNAVAILABLE

private fun ShortcutInfo.toInput(): ShortcutInput =
    ShortcutInput(
        shortLabel = shortLabel,
        longLabel = longLabel,
        isStatic = isDeclaredInManifest,
        isDynamic = isDynamic,
        isPinned = isPinned,
        isCached = isCached,
    )

@Suppress("DEPRECATION")
private fun StatusBarNotification.toInput(): NotificationInput {
    val notification = notification
    val extras = notification.extras
    val style =
        runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification) }
            .getOrNull()
    return NotificationInput(
        category = notification.category,
        isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        template = extras?.getString(Notification.EXTRA_TEMPLATE),
        title = extras?.getCharSequence(Notification.EXTRA_TITLE),
        text = extras?.getCharSequence(Notification.EXTRA_TEXT),
        hasLargeIcon =
            notification.getLargeIcon() != null || extras?.containsKey(Notification.EXTRA_LARGE_ICON) == true,
        hasBigPicture =
            extras?.containsKey(Notification.EXTRA_PICTURE) == true ||
                extras?.containsKey(Notification.EXTRA_PICTURE_ICON) == true,
        hasContentView = notification.contentView != null,
        hasBigContentView = notification.bigContentView != null,
        hasHeadsUpContentView = notification.headsUpContentView != null,
        messages =
            style?.messages?.map { message ->
                MessageInput(
                    text = message.text,
                    senderName = message.person?.name,
                    hasPerson = message.person != null,
                    hasPersonIcon = message.person?.icon != null,
                )
            },
        progressMax = extras?.getInt(Notification.EXTRA_PROGRESS_MAX) ?: 0,
        progress = extras?.getInt(Notification.EXTRA_PROGRESS) ?: 0,
        progressIndeterminate = extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) == true,
        actions =
            notification.actions.orEmpty().map { action ->
                ActionInput(title = action.title, hasRemoteInput = !action.remoteInputs.isNullOrEmpty())
            },
    )
}

private fun MediaController.toShape(): MediaShape {
    val metadata = metadata
    val state = playbackState
    val queue = queue.orEmpty()
    return MediaShape(
        metadata =
            MediaMetadataPresence(
                title = metadata.hasText(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata.hasText(MediaMetadata.METADATA_KEY_ARTIST),
                album = metadata.hasText(MediaMetadata.METADATA_KEY_ALBUM),
                duration = metadata?.containsKey(MediaMetadata.METADATA_KEY_DURATION) == true,
                artBitmap =
                    listOf(
                        MediaMetadata.METADATA_KEY_ART,
                        MediaMetadata.METADATA_KEY_ALBUM_ART,
                        MediaMetadata.METADATA_KEY_DISPLAY_ICON,
                    ).any { metadata?.getBitmap(it) != null },
                artUri =
                    listOf(
                        MediaMetadata.METADATA_KEY_ART_URI,
                        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
                    ).any { metadata.hasText(it) },
            ),
        playbackState = playbackStateName(state?.state),
        actions = decodePlaybackActions(state?.actions ?: 0L),
        customActions = state?.customActions.orEmpty().map { it.name?.toString().orEmpty() },
        queueSize = queue.size,
        queueItems =
            queue.take(MAX_QUEUE_ITEMS_SHOWN).map { item ->
                val description = item.description
                QueueItemShape(
                    title = description.title?.toString(),
                    hasIconBitmap = description.iconBitmap != null,
                    hasIconUri = description.iconUri != null,
                )
            },
    )
}

private fun MediaMetadata?.hasText(key: String): Boolean = this?.getString(key)?.isNotBlank() == true
