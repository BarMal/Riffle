package com.riffle.app.launcher.notifications

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class RiffleNotificationListenerService : NotificationListenerService() {
    private val repository by lazy { DataStoreActiveNotificationRepository(this) }
    private val notificationMapper by lazy {
        StatusBarNotificationMapper(
            userManager = getSystemService(UserManager::class.java),
            launcherApps = getSystemService(LauncherApps::class.java),
        )
    }

    private fun diag(message: String) {
        runCatching {
            val dir = getExternalFilesDir(null) ?: return
            dir.mkdirs()
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(System.currentTimeMillis())
            File(dir, "riffle_notification_diag.txt").appendText("$stamp $message\n")
        }
    }

    override fun onListenerConnected() {
        diag("onListenerConnected")
        ignoreNotificationListenerFailure {
            RiffleNotificationListenerConnection.connect(this)
            saveActiveNotifications()
        }
    }

    override fun onListenerDisconnected() {
        ignoreNotificationListenerFailure {
            RiffleNotificationListenerConnection.disconnect(this)
            AndroidNotificationStageActionGateway.clear()
            // The platform can unbind this listener outside a permission revocation (e.g. OEM battery
            // management). Ask the platform to retry the binding rather than staying silently dead.
            requestRebind(ComponentName(this, RiffleNotificationListenerService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        diag("onNotificationPosted pkg=${sbn?.packageName} key=${sbn?.key}")
        val result =
            runCatching {
                sbn?.let { notification -> AndroidNotificationStageActionGateway.replace(this, notification) }
                saveActiveNotifications()
            }
        result.exceptionOrNull()?.let { e ->
            diag("onNotificationPosted FAILED: ${e.javaClass.simpleName}: ${e.message}")
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

    private fun saveActiveNotifications() {
        val platformSnapshot = runCatching { activeNotifications }
        diag(
            "saveActiveNotifications activeNotifications=" +
                (platformSnapshot.getOrNull()?.size?.toString() ?: "THREW:${platformSnapshot.exceptionOrNull()}"),
        )
        val mapped =
            runCatching {
                platformSnapshot.getOrNull()
                    ?.also { notifications -> AndroidNotificationStageActionGateway.replaceAll(this, notifications) }
                    ?.map(notificationMapper::map)
            }
        mapped.exceptionOrNull()?.let { e -> diag("mapping FAILED: ${e.javaClass.simpleName}: ${e.message}") }
        diag("mapped count=${mapped.getOrNull()?.size}")
        mapped.getOrNull()?.let { list ->
            val saveResult = runCatching { repository.saveActiveNotifications(list) }
            saveResult.exceptionOrNull()?.let { e ->
                diag("repository.save FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
            diag("repository.save OK, packages=${list.map { it.packageName.value }}")
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
