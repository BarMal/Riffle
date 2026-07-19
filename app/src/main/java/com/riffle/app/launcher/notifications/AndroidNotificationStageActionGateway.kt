package com.riffle.app.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
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
                canDismiss = notification.isClearable,
            )
    }

    fun replaceAll(
        context: Context,
        notifications: Array<StatusBarNotification>,
    ) {
        targets.clear()
        notifications.forEach { notification -> replace(context, notification) }
    }

    fun clear() {
        targets.clear()
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
            if (isMedia) {
                target.mediaController
                    ?.playbackState
                    ?.let(::mediaCommandsForPlaybackState)
                    ?.forEach { command -> add(NotificationStageAction.MediaControl(command)) }
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
    val canDismiss: Boolean,
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
            if (canDismiss && AndroidNotificationDismissalGateway.dismissNotifications(listOf(key))) {
                NotificationStageActionResult.Performed
            } else {
                NotificationStageActionResult.Failed
            }
        is NotificationStageAction.ProviderAction ->
            providerActions[action.id]?.sendResult(action.replyText) ?: NotificationStageActionResult.Unavailable
        is NotificationStageAction.MediaControl ->
            mediaController
                ?.takeIf { controller ->
                    action.command in mediaCommandsForPlaybackState(controller.playbackState)
                }
                ?.sendResult(action.command)
                ?: NotificationStageActionResult.Unavailable
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

internal fun mediaCommandsForPlaybackState(playbackState: PlaybackState?): Set<MediaCommand> =
    buildSet {
        val actions = playbackState?.actions ?: return@buildSet
        if (actions and PlaybackState.ACTION_PLAY != 0L) add(MediaCommand.PLAY)
        if (actions and PlaybackState.ACTION_PAUSE != 0L) add(MediaCommand.PAUSE)
        if (actions and PlaybackState.ACTION_PLAY_PAUSE != 0L) {
            add(
                if (playbackState.state == PlaybackState.STATE_PLAYING) {
                    MediaCommand.PAUSE
                } else {
                    MediaCommand.PLAY
                },
            )
        }
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) add(MediaCommand.PREVIOUS)
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) add(MediaCommand.NEXT)
    }
