@file:Suppress("TooManyFunctions")

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
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

@Composable
internal fun OverlayDockSetting(
    settings: OverlayDockSettings,
    permissionStatus: OverlayDockPermissionStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OverlayDockEnabledSetting(
            settings = settings,
            permissionStatus = permissionStatus,
            onAction = onAction,
        )
        OverlayDockEdgeSetting(
            edge = settings.edge,
            onAction = onAction,
        )
        OverlayDockHandleThicknessSetting(
            thicknessDp = settings.handleThicknessDp,
            onAction = onAction,
        )
        OverlayDockHandleHeightSetting(
            heightDp = settings.handleHeightDp,
            onAction = onAction,
        )
        OverlayDockVerticalOffsetSetting(
            offsetDp = settings.verticalOffsetDp,
            onAction = onAction,
        )
        OverlayDockHandleAlphaSetting(
            alphaPercent = settings.handleAlphaPercent,
            onAction = onAction,
        )
        OverlayDockExpandedIconSizeSetting(
            sizeDp = settings.expandedIconSizeDp,
            onAction = onAction,
        )
        OverlayDockExpandedOrientationSetting(
            orientation = settings.expandedOrientation,
            onAction = onAction,
        )
        OverlayDockLabelSetting(
            showLabels = settings.showLabels,
            onAction = onAction,
        )
        OverlayDockItemsSetting(
            items = settings.items,
            onAction = onAction,
        )
    }
}

@Composable
private fun OverlayDockEnabledSetting(
    settings: OverlayDockSettings,
    permissionStatus: OverlayDockPermissionStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Floating dock",
        subtitle = if (settings.enabled) "Edge handle visible over apps" else "Only use the home dock",
        checked = settings.enabled,
        onCheckedChange = { value ->
            overlayDockEnabledActions(
                enabled = value,
                wasEnabled = settings.enabled,
                permissionStatus = permissionStatus,
            ).forEach(onAction)
        },
    )
}

internal fun overlayDockEnabledActions(
    enabled: Boolean,
    wasEnabled: Boolean,
    permissionStatus: OverlayDockPermissionStatus,
): List<LauncherShellAction> =
    buildList {
        add(LauncherShellAction.SelectOverlayDockEnabled(enabled))
        if (enabled && !wasEnabled && permissionStatus != OverlayDockPermissionStatus.GRANTED) {
            add(LauncherShellAction.RequestOverlayDockPermission)
        }
    }

@Composable
private fun OverlayDockEdgeSetting(
    edge: OverlayDockEdge,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Overlay edge",
            subtitle = edge.label,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = edge != OverlayDockEdge.START,
                onClick = { onAction(LauncherShellAction.SelectOverlayDockEdge(OverlayDockEdge.START)) },
            ) {
                SettingsButtonText(text = "Left")
            }
            TextButton(
                enabled = edge != OverlayDockEdge.END,
                onClick = { onAction(LauncherShellAction.SelectOverlayDockEdge(OverlayDockEdge.END)) },
            ) {
                SettingsButtonText(text = "Right")
            }
        }
    }
}

@Composable
private fun OverlayDockHandleThicknessSetting(
    thicknessDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    OverlayDockStepperSetting(
        title = "Handle thickness",
        value = thicknessDp,
        valueRange = MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP..MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
        valueLabel = { "$it dp" },
        onValueChange = { value ->
            onAction(
                LauncherShellAction.SelectOverlayDockHandleThickness(
                    thicknessDp = value,
                ),
            )
        },
    )
}

@Composable
private fun OverlayDockHandleHeightSetting(
    heightDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    OverlayDockStepperSetting(
        title = "Handle size",
        value = heightDp,
        valueRange = MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP..MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
        valueLabel = { "$it dp" },
        onValueChange = { value -> onAction(LauncherShellAction.SelectOverlayDockHandleHeight(value)) },
    )
}

@Composable
private fun OverlayDockVerticalOffsetSetting(
    offsetDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    OverlayDockStepperSetting(
        title = "Handle position",
        value = offsetDp,
        valueRange = MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP..MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
        valueLabel = { value ->
            when {
                value < 0 -> "${-value} dp up"
                value > 0 -> "$value dp down"
                else -> "Centered"
            }
        },
        onValueChange = { value -> onAction(LauncherShellAction.SelectOverlayDockVerticalOffset(value)) },
    )
}

@Composable
private fun OverlayDockHandleAlphaSetting(
    alphaPercent: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    OverlayDockStepperSetting(
        title = "Handle opacity",
        value = alphaPercent,
        valueRange = MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT..MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
        valueLabel = { "$it%" },
        onValueChange = { value -> onAction(LauncherShellAction.SelectOverlayDockHandleAlpha(value)) },
    )
}

@Composable
private fun OverlayDockExpandedIconSizeSetting(
    sizeDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    OverlayDockStepperSetting(
        title = "Expanded icon size",
        value = sizeDp,
        valueRange = MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP..MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
        valueLabel = { "$it dp" },
        onValueChange = { value -> onAction(LauncherShellAction.SelectOverlayDockExpandedIconSize(value)) },
    )
}

@Composable
private fun OverlayDockLabelSetting(
    showLabels: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Expanded labels",
        subtitle = if (showLabels) "Show app names" else "Icons only",
        checked = showLabels,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectOverlayDockShowLabels(value)) },
    )
}

@Composable
private fun OverlayDockStepperSetting(
    title: String,
    value: Int,
    valueRange: IntRange,
    valueLabel: (Int) -> String,
    onValueChange: (Int) -> Unit,
) = DiscreteSettingSlider(
    title = title,
    value = value,
    valueRange = valueRange,
    valueLabel = valueLabel,
    onValueChange = onValueChange,
)

private val OverlayDockEdge.label: String
    get() =
        when (this) {
            OverlayDockEdge.START -> "Left"
            OverlayDockEdge.END -> "Right"
        }
