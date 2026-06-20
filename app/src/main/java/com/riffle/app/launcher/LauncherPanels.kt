package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp

@Composable
fun AppDrawer(
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title = "Apps",
        onAction = onAction,
    ) {
        if (apps.isEmpty()) {
            Text(
                text = "No launchable apps found",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = apps,
                    key = { app -> app.drawerKey },
                ) { app ->
                    AppDrawerRow(app = app)
                }
            }
        }
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
private fun AppDrawerRow(app: InstalledApp) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconPlaceholder(label = app.label)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = app.identity.packageName.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppIconPlaceholder(label: String) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private val InstalledApp.drawerKey: String
    get() = "${identity.profile.id.value}:${identity.packageName.value}/${identity.activityName.value}"

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
