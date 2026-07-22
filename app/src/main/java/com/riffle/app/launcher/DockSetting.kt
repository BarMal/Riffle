@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.MAX_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

@Composable
internal fun DockSetting(
    dock: DockModel,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DockVisibilitySetting(
            enabled = dock.isEnabled,
            onAction = onAction,
        )
        DockNotificationCardsSetting(
            enabled = dock.showNotificationCards,
            notificationAccessStatus = notificationAccessStatus,
            onAction = onAction,
        )
        DockIconSizeSetting(
            sizeDp = dock.iconSizeDp,
            onAction = onAction,
        )
        DockBackgroundAlphaSetting(
            alphaPercent = dock.backgroundAlphaPercent,
            onAction = onAction,
        )
        DockVisualEffectSetting(
            effect = dock.visualEffect,
            onAction = onAction,
        )
        DockBackgroundSizingSetting(
            sizing = dock.backgroundSizing,
            onAction = onAction,
        )
        DockAlignmentSetting(
            alignment = dock.alignment,
            onAction = onAction,
        )
    }
}

@Composable
private fun DockVisualEffectSetting(
    effect: DockVisualEffect,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsTextColumn(
            title = "Dock effect",
            subtitle = "${effect.name.lowercase().replaceFirstChar(Char::uppercase)} Material treatment",
        )
        DockVisualEffect.entries.forEach { candidate ->
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = candidate != effect,
                onClick = { onAction(LauncherShellAction.SelectDockVisualEffect(candidate)) },
            ) {
                SettingsButtonText(text = candidate.name.lowercase().replaceFirstChar(Char::uppercase))
            }
        }
    }
}

@Composable
private fun DockIconSizeSetting(
    sizeDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Dock height",
    value = sizeDp,
    valueRange = MIN_DOCK_ICON_SIZE_DP..MAX_DOCK_ICON_SIZE_DP,
    valueLabel = { "$it dp" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockIconSize(value)) },
)

@Composable
private fun DockBackgroundAlphaSetting(
    alphaPercent: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Dock background",
    value = alphaPercent,
    valueRange = MIN_DOCK_BACKGROUND_ALPHA_PERCENT..MAX_DOCK_BACKGROUND_ALPHA_PERCENT,
    valueLabel = { "$it%" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockBackgroundAlpha(value)) },
)

@Composable
private fun DockBackgroundSizingSetting(
    sizing: DockBackgroundSizing,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Dock width",
            subtitle =
                when (sizing) {
                    DockBackgroundSizing.DYNAMIC -> "Fits dock items"
                    DockBackgroundSizing.FIXED -> "Uses available width"
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = sizing != DockBackgroundSizing.DYNAMIC,
                onClick = { onAction(LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.DYNAMIC)) },
            ) {
                SettingsButtonText(text = "Fit content")
            }
            TextButton(
                enabled = sizing != DockBackgroundSizing.FIXED,
                onClick = { onAction(LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.FIXED)) },
            ) {
                SettingsButtonText(text = "Full width")
            }
        }
    }
}

@Composable
private fun DockAlignmentSetting(
    alignment: DockAlignment,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsTextColumn(
            title = "Dock alignment",
            subtitle = "Places a content-sized dock on the home screen",
        )
        DockAlignment.entries.forEach { candidate ->
            TextButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { selected = candidate == alignment },
                enabled = candidate != alignment,
                onClick = { onAction(LauncherShellAction.SelectDockAlignment(candidate)) },
            ) {
                SettingsButtonText(
                    text =
                        when (candidate) {
                            DockAlignment.START -> "Start"
                            DockAlignment.CENTER -> "Center"
                            DockAlignment.END -> "End"
                        },
                )
            }
        }
    }
}

@Composable
private fun DockVisibilitySetting(
    enabled: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Show dock",
        subtitle = if (enabled) "Dock visible on home" else "Home grid uses dock space",
        checked = enabled,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectDockEnabled(value)) },
    )
}

@Composable
private fun DockNotificationCardsSetting(
    enabled: Boolean,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Expanded dock cards",
        subtitle = dockNotificationCardsSettingSubtitle(enabled, notificationAccessStatus),
        checked = enabled,
        onCheckedChange = { value ->
            dockNotificationCardsEnabledActions(
                enabled = value,
                wasEnabled = enabled,
                notificationAccessStatus = notificationAccessStatus,
            ).forEach(onAction)
        },
    )
}

internal fun dockNotificationCardsEnabledActions(
    enabled: Boolean,
    wasEnabled: Boolean,
    notificationAccessStatus: NotificationAccessStatus,
): List<LauncherShellAction> =
    buildList {
        add(LauncherShellAction.SelectDockNotificationCardsEnabled(enabled))
        if (enabled && !wasEnabled && notificationAccessStatus != NotificationAccessStatus.GRANTED) {
            add(LauncherShellAction.RequestNotificationAccess)
        }
    }

internal fun dockNotificationCardsSettingSubtitle(
    enabled: Boolean,
    notificationAccessStatus: NotificationAccessStatus,
): String {
    if (!enabled) {
        return "Expanded dock only shows shortcuts and widgets"
    }

    return when (notificationAccessStatus) {
        NotificationAccessStatus.GRANTED -> "Expanded dock can show notification cards"
        NotificationAccessStatus.NOT_GRANTED -> "Notification cards are on, but access is not allowed"
        NotificationAccessStatus.REVOKED -> "Notification cards are on, but access was revoked"
        NotificationAccessStatus.UNKNOWN -> "Notification cards are on, but access has not been checked"
    }
}
