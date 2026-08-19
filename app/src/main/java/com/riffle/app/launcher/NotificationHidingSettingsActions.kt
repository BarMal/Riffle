package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.NotificationHidingSettings
import com.riffle.core.domain.launcher.settings.withRule
import com.riffle.core.domain.launcher.settings.withoutRule

@Suppress("MaxLineLength")
private fun NotificationHidingSettings.withNotificationHidingAction(
    action: LauncherShellAction,
): NotificationHidingSettings =
    when (action) {
        is LauncherShellAction.AddNotificationHideRule ->
            withRule(
                packageName = action.packageName,
                profileId = action.profileId,
                kind = action.kind,
                value = action.value,
                matchMode = action.matchMode,
            )

        is LauncherShellAction.RemoveNotificationHideRule -> withoutRule(action.id)
        else -> this
    }

internal fun LauncherShellState.withNotificationHidingAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState =
    withLauncherSettings(
        settings =
            launcherSettings.copy(
                notificationHiding = launcherSettings.notificationHiding.withNotificationHidingAction(action),
            ),
        launcherSettingsRepository = launcherSettingsRepository,
    )
