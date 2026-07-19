package com.riffle.app.launcher

import com.riffle.app.launcher.notifications.NotificationDismissalGateway
import com.riffle.app.launcher.notifications.NotificationStageActionGateway
import com.riffle.app.launcher.notifications.NotificationStageActionResult
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

internal fun interface LauncherNotificationActionHandler {
    fun handle(action: LauncherShellAction): Boolean
}

internal class DefaultLauncherNotificationActionHandler(
    private val notificationDismissalGateway: NotificationDismissalGateway,
    private val refreshNotifications: () -> Unit,
    private val canPerformStageAction: (LauncherNotificationKey) -> Boolean = { true },
    private val stageActionGateway: NotificationStageActionGateway =
        NotificationStageActionGateway { _, _ ->
            NotificationStageActionResult.Unavailable
        },
) : LauncherNotificationActionHandler {
    override fun handle(action: LauncherShellAction): Boolean =
        when (val route = action.launcherNotificationActionRoute()) {
            is LauncherNotificationActionRoute.DismissNotifications -> {
                if (notificationDismissalGateway.dismissNotifications(route.action.keys)) {
                    refreshNotifications()
                }
                true
            }

            is LauncherNotificationActionRoute.PerformStageAction -> {
                val result =
                    if (canPerformStageAction(route.action.key)) {
                        stageActionGateway.perform(route.action.key, route.action.action)
                    } else {
                        NotificationStageActionResult.Unavailable
                    }
                if (result == NotificationStageActionResult.Performed) refreshNotifications()
                result == NotificationStageActionResult.Performed
            }

            null -> false
        }
}

internal sealed interface LauncherNotificationActionRoute {
    data class DismissNotifications(
        val action: LauncherShellAction.DismissNotifications,
    ) : LauncherNotificationActionRoute

    data class PerformStageAction(
        val action: LauncherShellAction.PerformNotificationStageAction,
    ) : LauncherNotificationActionRoute
}

internal fun LauncherShellAction.launcherNotificationActionRoute(): LauncherNotificationActionRoute? =
    when (this) {
        is LauncherShellAction.DismissNotifications -> LauncherNotificationActionRoute.DismissNotifications(this)
        is LauncherShellAction.PerformNotificationStageAction ->
            LauncherNotificationActionRoute.PerformStageAction(this)
        else -> null
    }

/** Keeps contextual actions unavailable when their notification is missing or profile content is redacted. */
internal fun LauncherShellState.canPerformStageAction(key: LauncherNotificationKey): Boolean =
    notificationGroupsByApp
        .asSequence()
        .flatMap { group -> group.notifications.asSequence() }
        .firstOrNull { notification -> notification.key == key }
        ?.let { notification ->
            profileContentVisibility[notification.profileId] == AppProfileContentVisibility.VISIBLE
        } == true
