package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.MAX_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP
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
        DockCapacitySetting(
            capacity = dock.capacity,
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
        DockItemSpacingSetting(
            spacingDp = dock.itemSpacingDp,
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
    title = "Dock icon size",
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
            title = "Dock background size",
            subtitle =
                when (sizing) {
                    DockBackgroundSizing.DYNAMIC -> "Dynamic"
                    DockBackgroundSizing.FIXED -> "Fixed"
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = sizing != DockBackgroundSizing.DYNAMIC,
                onClick = { onAction(LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.DYNAMIC)) },
            ) {
                SettingsButtonText(text = "Dynamic")
            }
            TextButton(
                enabled = sizing != DockBackgroundSizing.FIXED,
                onClick = { onAction(LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.FIXED)) },
            ) {
                SettingsButtonText(text = "Fixed")
            }
        }
    }
}

@Composable
private fun DockItemSpacingSetting(
    spacingDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Dock item spacing",
    value = spacingDp,
    valueRange = MIN_DOCK_ITEM_SPACING_DP..MAX_DOCK_ITEM_SPACING_DP,
    valueLabel = { "$it dp" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockItemSpacing(value)) },
)

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
private fun DockCapacitySetting(
    capacity: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Dock slots",
            subtitle = capacity.toString(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = capacity > 0,
                onClick = { onAction(LauncherShellAction.SelectDockCapacity(capacity - 1)) },
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(onClick = { onAction(LauncherShellAction.SelectDockCapacity(capacity + 1)) }) {
                SettingsButtonText(text = "+")
            }
        }
    }
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
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectDockNotificationCardsEnabled(value)) },
    )
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
