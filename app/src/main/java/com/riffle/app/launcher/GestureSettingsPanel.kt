package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

@Composable
fun HomeSwipeGestureSetting(
    settings: HomeSwipeGestureSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Home swipes",
            style = MaterialTheme.typography.bodyLarge,
        )
        HomeSwipeGestureRow(
            label = "Up",
            direction = HomeSwipeGestureDirection.UP,
            action = settings.up,
            onAction = onAction,
        )
        HomeSwipeGestureRow(
            label = "Down",
            direction = HomeSwipeGestureDirection.DOWN,
            action = settings.down,
            onAction = onAction,
        )
        HomeSwipeGestureRow(
            label = "Left",
            direction = HomeSwipeGestureDirection.LEFT,
            action = settings.left,
            onAction = onAction,
        )
        HomeSwipeGestureRow(
            label = "Right",
            direction = HomeSwipeGestureDirection.RIGHT,
            action = settings.right,
            onAction = onAction,
        )
        TextButton(onClick = { onAction(LauncherShellAction.ResetHomeSwipeGestureActions) }) {
            SettingsButtonText(text = "Reset")
        }
    }
}

@Composable
private fun HomeSwipeGestureRow(
    label: String,
    direction: HomeSwipeGestureDirection,
    action: LauncherGestureAction,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = {
                onAction(
                    LauncherShellAction.SelectHomeSwipeGestureAction(
                        direction = direction,
                        action = action.nextGestureAction(),
                    ),
                )
            },
        ) {
            SettingsButtonText(text = action.label)
        }
    }
}

private fun LauncherGestureAction.nextGestureAction(): LauncherGestureAction {
    val actions = LauncherGestureAction.entries
    val currentIndex = actions.indexOf(this)
    return actions[(currentIndex + 1) % actions.size]
}

private val LauncherGestureAction.label: String
    get() =
        when (this) {
            LauncherGestureAction.NONE -> "Disabled"
            LauncherGestureAction.OPEN_APP_DRAWER -> "Apps"
            LauncherGestureAction.OPEN_NOTIFICATIONS -> "Notifications"
            LauncherGestureAction.OPEN_SEARCH -> "Search"
            LauncherGestureAction.OPEN_SETTINGS -> "Settings"
            LauncherGestureAction.ENTER_HOME_EDIT_MODE -> "Edit home"
            LauncherGestureAction.SELECT_NEXT_HOME_PAGE -> "Next page"
            LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE -> "Previous page"
        }
