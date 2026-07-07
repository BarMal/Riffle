package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureConflictDetector
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

@Composable
fun HomeSwipeGestureSetting(
    settings: HomeGestureSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Home gestures",
            style = MaterialTheme.typography.bodyLarge,
        )
        homeGestureConflictSummary(settings)?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        GestureGroup(
            title = "One finger",
            rows =
                listOf(
                    GestureRowState("Swipe up", HomeGesture.ONE_FINGER_UP),
                    GestureRowState("Swipe down", HomeGesture.ONE_FINGER_DOWN),
                    GestureRowState("Swipe left", HomeGesture.ONE_FINGER_LEFT),
                    GestureRowState("Swipe right", HomeGesture.ONE_FINGER_RIGHT),
                ),
            settings = settings,
            onAction = onAction,
        )
        GestureGroup(
            title = "Two fingers",
            rows =
                listOf(
                    GestureRowState("Swipe up", HomeGesture.TWO_FINGER_UP),
                    GestureRowState("Swipe down", HomeGesture.TWO_FINGER_DOWN),
                    GestureRowState("Swipe left", HomeGesture.TWO_FINGER_LEFT),
                    GestureRowState("Swipe right", HomeGesture.TWO_FINGER_RIGHT),
                ),
            settings = settings,
            onAction = onAction,
        )
        GestureGroup(
            title = "Pinch",
            rows =
                listOf(
                    GestureRowState("Pinch in", HomeGesture.PINCH_IN),
                    GestureRowState("Pinch out", HomeGesture.PINCH_OUT),
                ),
            settings = settings,
            onAction = onAction,
        )
        TextButton(onClick = { onAction(LauncherShellAction.ResetHomeSwipeGestureActions) }) {
            SettingsButtonText(text = "Reset")
        }
    }
}

@Composable
private fun GestureGroup(
    title: String,
    rows: List<GestureRowState>,
    settings: HomeGestureSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rows.forEach { row ->
            HomeGestureRow(
                label = row.label,
                gesture = row.gesture,
                action = settings.actionFor(row.gesture),
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun HomeGestureRow(
    label: String,
    gesture: HomeGesture,
    action: LauncherGestureAction,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isExpanded = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column {
            TextButton(onClick = { isExpanded.value = true }) {
                SettingsButtonText(text = action.label)
            }
            DropdownMenu(
                expanded = isExpanded.value,
                onDismissRequest = { isExpanded.value = false },
            ) {
                LauncherGestureAction.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option.label) },
                        onClick = {
                            isExpanded.value = false
                            onAction(
                                LauncherShellAction.SelectHomeGestureAction(
                                    gesture = gesture,
                                    action = option,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

internal val LauncherGestureAction.label: String
    get() =
        when (this) {
            LauncherGestureAction.NONE -> "Disabled"
            LauncherGestureAction.OPEN_APP_DRAWER -> "Apps"
            LauncherGestureAction.OPEN_NOTIFICATIONS -> "Notifications"
            LauncherGestureAction.OPEN_SEARCH -> "Search"
            LauncherGestureAction.OPEN_SETTINGS -> "Settings"
            LauncherGestureAction.ENTER_HOME_EDIT_MODE -> "Edit home"
            LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW -> "Manage pages"
            LauncherGestureAction.SELECT_NEXT_HOME_PAGE -> "Next page"
            LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE -> "Previous page"
        }

internal val HomeGesture.label: String
    get() =
        when (this) {
            HomeGesture.ONE_FINGER_UP -> "Swipe up"
            HomeGesture.ONE_FINGER_DOWN -> "Swipe down"
            HomeGesture.ONE_FINGER_LEFT -> "Swipe left"
            HomeGesture.ONE_FINGER_RIGHT -> "Swipe right"
            HomeGesture.TWO_FINGER_UP -> "Two-finger swipe up"
            HomeGesture.TWO_FINGER_DOWN -> "Two-finger swipe down"
            HomeGesture.TWO_FINGER_LEFT -> "Two-finger swipe left"
            HomeGesture.TWO_FINGER_RIGHT -> "Two-finger swipe right"
            HomeGesture.PINCH_IN -> "Pinch in"
            HomeGesture.PINCH_OUT -> "Pinch out"
        }

internal fun homeGestureConflictSummary(settings: HomeGestureSettings): String? =
    HomeGestureConflictDetector.conflictsIn(settings)
        .takeIf { conflicts -> conflicts.isNotEmpty() }
        ?.joinToString(separator = "\n", prefix = "Conflicting gestures: ") { conflict ->
            "${conflict.action.label}: ${conflict.gestures.joinToString { gesture -> gesture.label }}"
        }

private data class GestureRowState(
    val label: String,
    val gesture: HomeGesture,
)
