package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

/** Actions explicitly available for the focused live notification only. */
sealed interface NotificationStageAction {
    data object Open : NotificationStageAction

    data class ProviderAction(
        val id: String,
        val replyText: String? = null,
    ) : NotificationStageAction {
        init {
            require(id.isNotBlank()) { "Provider action ids cannot be blank." }
        }
    }

    data object Dismiss : NotificationStageAction

    data class MediaControl(val command: MediaCommand) : NotificationStageAction
}

enum class MediaCommand { PLAY, PAUSE, PREVIOUS, NEXT }

sealed interface NotificationStageActionResult {
    data object Performed : NotificationStageActionResult

    data object Unavailable : NotificationStageActionResult

    data object Failed : NotificationStageActionResult
}

/** Platform boundary for notification PendingIntents and media sessions. Never persists handles. */
fun interface NotificationStageActionGateway {
    fun perform(
        key: LauncherNotificationKey,
        action: NotificationStageAction,
    ): NotificationStageActionResult
}

fun interface NotificationStageActionAvailability {
    fun actionsFor(
        key: LauncherNotificationKey,
        isMedia: Boolean,
    ): Set<NotificationStageAction>

    data object None : NotificationStageActionAvailability {
        override fun actionsFor(
            key: LauncherNotificationKey,
            isMedia: Boolean,
        ): Set<NotificationStageAction> = emptySet()
    }
}
