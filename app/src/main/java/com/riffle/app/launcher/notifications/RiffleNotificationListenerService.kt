package com.riffle.app.launcher.notifications

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

class RiffleNotificationListenerService : NotificationListenerService() {
    private val repository by lazy { DataStoreActiveNotificationRepository(this) }
    private val notificationMapper by lazy {
        StatusBarNotificationMapper(
            userManager = getSystemService(UserManager::class.java),
            launcherApps = getSystemService(LauncherApps::class.java),
        )
    }

    override fun onListenerConnected() {
        ignoreNotificationListenerFailure {
            RiffleNotificationListenerConnection.connect(this)
            saveActiveNotifications()
        }
    }

    override fun onListenerDisconnected() {
        ignoreNotificationListenerFailure {
            RiffleNotificationListenerConnection.disconnect(this)
            AndroidNotificationStageActionGateway.clear()
            NotificationImageStore.clear()
            // The platform can unbind this listener outside a permission revocation (e.g. OEM battery
            // management). Ask the platform to retry the binding rather than staying silently dead.
            requestRebind(ComponentName(this, RiffleNotificationListenerService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        ignoreNotificationListenerFailure {
            // Isolated in its own runCatching, distinct from the outer ignoreNotificationListenerFailure:
            // a single malformed notification's action-target registration must not abort
            // saveActiveNotifications() below, or that notification's *content* would never sync either.
            sbn?.let { notification ->
                runCatching { AndroidNotificationStageActionGateway.replace(this, notification) }
            }
            saveActiveNotifications()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        ignoreNotificationListenerFailure {
            sbn?.key?.let(AndroidNotificationStageActionGateway::remove)
            saveActiveNotifications()
        }
    }

    fun dismissNotifications(keys: List<LauncherNotificationKey>): Boolean =
        runCatching {
            keys.forEach { key -> cancelNotification(key.value) }
            saveActiveNotifications()
        }.isSuccess

    fun snooze(
        key: LauncherNotificationKey,
        durationMillis: Long,
    ): Boolean = runCatching { snoozeNotification(key.value, durationMillis) }.isSuccess

    /**
     * Maps each notification independently so one notification that fails to map (a platform quirk
     * in a single StatusBarNotification, say) can't discard the snapshot write for every other
     * currently active notification.
     */
    private fun saveActiveNotifications() {
        val images = LinkedHashMap<String, NotificationImageBudget.Candidate>()
        val bitmaps = HashMap<String, android.graphics.Bitmap>()
        activeNotifications
            ?.also { notifications ->
                runCatching { AndroidNotificationStageActionGateway.replaceAll(this, notifications) }
            }?.mapNotNull { notification ->
                runCatching {
                    val mapped = notificationMapper.map(notification)
                    val captured = NotificationImageCapture.capture(this, notification.notification)
                    val (personHandle, pictureHandle) =
                        NotificationImageCapture.handlesOf(mapped.profileId, mapped.key.value)
                    captured.person?.let { bitmap ->
                        bitmaps[personHandle] = bitmap
                        images[personHandle] =
                            NotificationImageBudget.Candidate(
                                personHandle,
                                bitmap.allocationByteCount,
                                isPicture = false,
                                postedAtEpochMillis = mapped.postedAtEpochMillis,
                            )
                    }
                    captured.picture?.let { bitmap ->
                        bitmaps[pictureHandle] = bitmap
                        images[pictureHandle] =
                            NotificationImageBudget.Candidate(
                                pictureHandle,
                                bitmap.allocationByteCount,
                                isPicture = true,
                                postedAtEpochMillis = mapped.postedAtEpochMillis,
                            )
                    }
                    mapped.copy(
                        hasPersonIcon = captured.person != null,
                        hasPicture = captured.picture != null,
                    )
                }.getOrNull()
            }?.let { list ->
                // What the store keeps is what the budget selects; the flags say only what is kept, so a card never
                // asks for an image that is not there.
                val kept = NotificationImageBudget.select(images.values.toList())
                NotificationImageStore.replaceAll(bitmaps.filterKeys { it in kept })
                val flagged =
                    list.map { mapped ->
                        val (personHandle, pictureHandle) =
                            NotificationImageCapture.handlesOf(mapped.profileId, mapped.key.value)
                        mapped.copy(
                            hasPersonIcon = personHandle in kept,
                            hasPicture = pictureHandle in kept,
                        )
                    }
                runCatching { repository.saveActiveNotifications(flagged) }
            }
    }
}

/**
 * A notification listener may be disconnected while its platform snapshot is read. Keep the
 * persisted snapshot in that case: replacing it with an empty list would hide notifications, and
 * allowing the platform exception out of the service would crash the launcher process.
 */
internal fun <Input, Output> activeNotificationSnapshotOrNull(
    activeNotifications: () -> Array<Input>?,
    mapper: (Input) -> Output,
): List<Output>? =
    runCatching {
        activeNotifications()
            ?.map(mapper)
            .orEmpty()
    }.getOrNull()

/**
 * Notification access can connect the listener while its backing storage is unavailable. Listener
 * callbacks run in the launcher process, so persistence failures must not escape and crash it.
 */
internal fun <Input, Output> saveActiveNotificationSnapshot(
    activeNotifications: () -> Array<Input>?,
    mapper: (Input) -> Output,
    saveNotifications: (List<Output>) -> Unit,
) {
    activeNotificationSnapshotOrNull(
        activeNotifications = activeNotifications,
        mapper = mapper,
    )?.let { snapshot -> runCatching { saveNotifications(snapshot) } }
}

/**
 * Listener connection callbacks can race with permission changes and lazy platform service setup.
 * Keep every callback boundary from propagating a transient platform failure into the launcher process.
 */
internal fun ignoreNotificationListenerFailure(action: () -> Unit) {
    runCatching(action)
}
