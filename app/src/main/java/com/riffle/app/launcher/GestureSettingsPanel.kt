package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

@Composable
fun HomeSwipeGestureSetting(settings: HomeSwipeGestureSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Home swipes",
            style = MaterialTheme.typography.bodyLarge,
        )
        HomeSwipeGestureRow(label = "Up", action = settings.up)
        HomeSwipeGestureRow(label = "Down", action = settings.down)
        HomeSwipeGestureRow(label = "Left", action = settings.left)
        HomeSwipeGestureRow(label = "Right", action = settings.right)
    }
}

@Composable
private fun HomeSwipeGestureRow(
    label: String,
    action: LauncherGestureAction,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
