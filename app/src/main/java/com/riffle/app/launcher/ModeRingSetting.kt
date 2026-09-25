package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeRing

/**
 * The "Modes" section (#1225): which modes the next/previous-mode gestures move between on the
 * device class being configured, and in what order. Enabled modes are listed first, in ring order,
 * with Up/Down buttons to reorder them; a ring keeps two or three modes, so a switch that would
 * leave fewer than two is disabled.
 */
@Composable
internal fun ModeRingSetting(
    ring: ModeRing,
    availableViewModes: List<LauncherViewMode>,
    onAction: (LauncherShellAction) -> Unit,
) {
    val disabledModes = availableViewModes.filterNot { mode -> mode in ring }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsTextColumn(
            title = "Modes",
            subtitle = "Swipe with three fingers, or up on the dock, to move between these in order.",
        )
        ring.modes.forEachIndexed { index, mode ->
            ModeRingModeRow(
                mode = mode,
                subtitle = "${index + 1} of ${ring.modes.size}",
                enabled = true,
                canToggle = ring.modes.size > ModeRing.MIN_MODES,
                canMoveUp = index > 0,
                canMoveDown = index < ring.modes.lastIndex,
                onAction = onAction,
            )
        }
        disabledModes.forEach { mode ->
            ModeRingModeRow(
                mode = mode,
                subtitle = "Off",
                enabled = false,
                canToggle = ring.modes.size < ModeRing.MAX_MODES,
                canMoveUp = false,
                canMoveDown = false,
                onAction = onAction,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ModeRingModeRow(
    mode: LauncherViewMode,
    subtitle: String,
    enabled: Boolean,
    canToggle: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = mode.label,
            subtitle = subtitle,
        )
        if (enabled) {
            ModeRingMoveButton(label = "Up", description = "Move ${mode.label} up", enabled = canMoveUp) {
                onAction(LauncherShellAction.MoveModeRingMode(mode = mode, offset = -1))
            }
            ModeRingMoveButton(label = "Down", description = "Move ${mode.label} down", enabled = canMoveDown) {
                onAction(LauncherShellAction.MoveModeRingMode(mode = mode, offset = 1))
            }
        }
        Switch(
            modifier = Modifier.semantics { contentDescription = "${mode.label} in mode ring" },
            checked = enabled,
            enabled = canToggle,
            onCheckedChange = { checked ->
                onAction(LauncherShellAction.SelectModeRingModeEnabled(mode = mode, enabled = checked))
            },
        )
    }
}

@Composable
private fun ModeRingMoveButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = Modifier.semantics { contentDescription = description },
        enabled = enabled,
        onClick = onClick,
    ) {
        SettingsButtonText(text = label)
    }
}
