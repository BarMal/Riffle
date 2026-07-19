package com.riffle.app.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.StatusBarNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local registry of live notification handles. It intentionally keeps no handles in the
 * notification datastore: a removed notification can therefore only return [NotificationStageActionResult.Unavailable].
 */
object AndroidNotificationStageActionGateway : NotificationStageActionGateway, NotificationStageActionAvailability {
    private val targets = ConcurrentHashMap<LauncherNotificationKey, Targets>()

    @Suppress("DEPRECATION")
    fun replace(
        context: Context,
        notification: StatusBarNotification,
    ) {
        val platformNotification = notification.notification
        val providerActions =
            platformNotification.actions
                ?.mapIndexedNotNull { index, action ->
                    action.actionIntent?.let { pendingIntent ->
                        "provider:$index" to ProviderTarget(action, pendingIntent)
                    }
                }.orEmpty().toMap()
        val token =
            platformNotification.extras?.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        targets[LauncherNotificationKey(notification.key)] =
            Targets(
                contentIntent = platformNotification.contentIntent,
                providerActions = providerActions,
                mediaController = token?.let { MediaController(context, it) },
            )
    }

    fun remove(key: String) {
        targets.remove(LauncherNotificationKey(key))
    }

    override fun actionsFor(
        key: LauncherNotificationKey,
        isMedia: Boolean,
    ): Set<NotificationStageAction> {
        val target = targets[key] ?: return emptySet()
        return buildSet {
            if (target.contentIntent != null) add(NotificationStageAction.Open)
            target.providerActions.keys.forEach { id -> add(NotificationStageAction.ProviderAction(id)) }
            if (isMedia && target.mediaController != null) {
                add(NotificationStageAction.MediaControl(MediaCommand.PLAY))
                add(NotificationStageAction.MediaControl(MediaCommand.PAUSE))
                add(NotificationStageAction.MediaControl(MediaCommand.PREVIOUS))
                add(NotificationStageAction.MediaControl(MediaCommand.NEXT))
            }
        }
    }

    override fun perform(
        key: LauncherNotificationKey,
        action: NotificationStageAction,
    ): NotificationStageActionResult = targets[key]?.perform(key, action) ?: NotificationStageActionResult.Unavailable
}

private data class Targets(
    val contentIntent: PendingIntent?,
    val providerActions: Map<String, ProviderTarget>,
    val mediaController: MediaController?,
)

private data class ProviderTarget(
    val action: Notification.Action,
    val pendingIntent: PendingIntent,
) {
    fun send(replyText: String?) {
        if (replyText.isNullOrBlank() || action.remoteInputs.isNullOrEmpty()) {
            pendingIntent.send()
            return
        }
        val intent = Intent()
        val results = android.os.Bundle()
        action.remoteInputs.orEmpty().forEach { input ->
            results.putCharSequence(input.resultKey, replyText)
        }
        android.app.RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
        pendingIntent.send(null, 0, intent)
    }
}

private fun Targets.perform(
    key: LauncherNotificationKey,
    action: NotificationStageAction,
): NotificationStageActionResult =
    when (action) {
        NotificationStageAction.Open -> contentIntent?.sendResult() ?: NotificationStageActionResult.Unavailable
        NotificationStageAction.Dismiss ->
            if (AndroidNotificationDismissalGateway.dismissNotifications(listOf(key))) {
                NotificationStageActionResult.Performed
            } else {
                NotificationStageActionResult.Failed
            }
        is NotificationStageAction.ProviderAction ->
            providerActions[action.id]?.sendResult(action.replyText) ?: NotificationStageActionResult.Unavailable
        is NotificationStageAction.MediaControl ->
            mediaController?.sendResult(action.command) ?: NotificationStageActionResult.Unavailable
    }

private fun PendingIntent.sendResult(): NotificationStageActionResult =
    runCatching { send() }
        .fold(
            onSuccess = { NotificationStageActionResult.Performed },
            onFailure = { NotificationStageActionResult.Failed },
        )

private fun ProviderTarget.sendResult(replyText: String?): NotificationStageActionResult =
    runCatching { send(replyText) }
        .fold(
            onSuccess = { NotificationStageActionResult.Performed },
            onFailure = { NotificationStageActionResult.Failed },
        )

private fun MediaController.sendResult(command: MediaCommand): NotificationStageActionResult =
    runCatching { perform(command) }
        .fold(
            onSuccess = { NotificationStageActionResult.Performed },
            onFailure = { NotificationStageActionResult.Failed },
        )

private fun MediaController.perform(command: MediaCommand) {
    when (command) {
        MediaCommand.PLAY -> transportControls.play()
        MediaCommand.PAUSE -> transportControls.pause()
        MediaCommand.PREVIOUS -> transportControls.skipToPrevious()
        MediaCommand.NEXT -> transportControls.skipToNext()
    }
}
