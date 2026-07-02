package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

@Composable
internal fun OverlayDockSetting(
    settings: OverlayDockSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OverlayDockEnabledSetting(
            settings = settings,
            onAction = onAction,
        )
        OverlayDockEdgeSetting(
            edge = settings.edge,
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
    }
}

@Composable
private fun OverlayDockEnabledSetting(
    settings: OverlayDockSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Overlay dock",
            subtitle = if (settings.enabled) "Edge handle visible over apps" else "Only use the home dock",
        )
        Switch(
            checked = settings.enabled,
            onCheckedChange = { value -> onAction(LauncherShellAction.SelectOverlayDockEnabled(value)) },
        )
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
private fun OverlayDockHandleHeightSetting(
    heightDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    OverlayDockStepperSetting(
        title = "Handle size",
        subtitle = "$heightDp dp",
        decreaseEnabled = heightDp > MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
        increaseEnabled = heightDp < MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
        onDecrease = { onAction(LauncherShellAction.SelectOverlayDockHandleHeight(heightDp - OVERLAY_SIZE_STEP_DP)) },
        onIncrease = { onAction(LauncherShellAction.SelectOverlayDockHandleHeight(heightDp + OVERLAY_SIZE_STEP_DP)) },
    )
}

@Composable
private fun OverlayDockVerticalOffsetSetting(
    offsetDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    OverlayDockStepperSetting(
        title = "Handle position",
        subtitle =
            when {
                offsetDp < 0 -> "${-offsetDp} dp up"
                offsetDp > 0 -> "$offsetDp dp down"
                else -> "Centered"
            },
        decreaseEnabled = offsetDp > MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
        increaseEnabled = offsetDp < MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
        onDecrease = {
            onAction(LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp - OVERLAY_OFFSET_STEP_DP))
        },
        onIncrease = {
            onAction(LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp + OVERLAY_OFFSET_STEP_DP))
        },
    )
}

@Composable
private fun OverlayDockHandleAlphaSetting(
    alphaPercent: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    OverlayDockStepperSetting(
        title = "Handle opacity",
        subtitle = "$alphaPercent%",
        decreaseEnabled = alphaPercent > MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
        increaseEnabled = alphaPercent < MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
        onDecrease = { onAction(LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent - OVERLAY_ALPHA_STEP)) },
        onIncrease = { onAction(LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent + OVERLAY_ALPHA_STEP)) },
    )
}

@Composable
private fun OverlayDockStepperSetting(
    title: String,
    subtitle: String,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = title,
            subtitle = subtitle,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = decreaseEnabled,
                onClick = onDecrease,
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(
                enabled = increaseEnabled,
                onClick = onIncrease,
            ) {
                SettingsButtonText(text = "+")
            }
        }
    }
}

private const val OVERLAY_SIZE_STEP_DP = 8
private const val OVERLAY_OFFSET_STEP_DP = 24
private const val OVERLAY_ALPHA_STEP = 5

private val OverlayDockEdge.label: String
    get() =
        when (this) {
            OverlayDockEdge.START -> "Left"
            OverlayDockEdge.END -> "Right"
        }
