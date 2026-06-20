package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawer(onAction: (LauncherShellAction) -> Unit) {
    LauncherPanel(
        title = "Apps",
        onAction = onAction,
    ) {
        Text(
            text = "No apps indexed yet",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun SearchSurface(onAction: (LauncherShellAction) -> Unit) {
    LauncherPanel(
        title = "Search",
        onAction = onAction,
    ) {
        Text(
            text = "Search index is empty",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun SettingsSurface(onAction: (LauncherShellAction) -> Unit) {
    LauncherPanel(
        title = "Settings",
        onAction = onAction,
    ) {
        Text(
            text = "No settings available",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun LauncherPanel(
    title: String,
    onAction: (LauncherShellAction) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
            TextButton(onClick = { onAction(LauncherShellAction.OpenHome) }) {
                Text(text = "Home")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
