package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.DockGestureSettings
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureSurface

@Composable
fun DockSwipeUpGestureSetting(
    settings: GestureSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isExpanded = remember { mutableStateOf(false) }
    val action = settings.dockGestures.swipeUp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Dock gestures",
            style = MaterialTheme.typography.bodyLarge,
        )
        dockGestureConflictSummary(settings)?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Swipe up",
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
            Column {
                TextButton(onClick = { isExpanded.value = true }) {
                    SettingsButtonText(text = action.label)
                }
                RiffleContextMenu(
                    expanded = isExpanded.value,
                    onDismissRequest = { isExpanded.value = false },
                ) {
                    DockGestureSettings.ALLOWED_SWIPE_UP_ACTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option.label) },
                            onClick = {
                                isExpanded.value = false
                                onAction(LauncherShellAction.SelectDockGestureAction(option))
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun dockGestureConflictSummary(settings: GestureSettings): String? =
    settings.conflicts
        .filter { conflict -> conflict.surface == LauncherGestureSurface.DOCK }
        .takeIf { conflicts -> conflicts.isNotEmpty() }
        ?.joinToString(separator = "\n", prefix = "Conflicting gestures: ") { conflict -> conflict.action.label }
