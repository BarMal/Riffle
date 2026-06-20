package com.riffle.app.launcher

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp

@Composable
fun AppDrawer(
    apps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title = "Apps",
        onAction = onAction,
    ) {
        AppList(
            apps = apps,
            appIconLoader = appIconLoader,
            emptyText = "No launchable apps found",
            onAction = onAction,
        )
    }
}

@Composable
fun SearchSurface(
    query: String,
    results: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title = "Search",
        onAction = onAction,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { value -> onAction(LauncherShellAction.SearchQueryChanged(value)) },
                singleLine = true,
                label = { Text(text = "Search apps") },
            )
            Spacer(modifier = Modifier.height(16.dp))
            AppList(
                modifier = Modifier.weight(1f),
                apps = results,
                appIconLoader = appIconLoader,
                emptyText = "No matching apps",
                onAction = onAction,
            )
        }
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
private fun AppList(
    apps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    emptyText: String,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = apps,
                key = { app -> app.drawerKey },
            ) { app ->
                AppDrawerRow(
                    app = app,
                    appIconLoader = appIconLoader,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun AppDrawerRow(
    app: InstalledApp,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable { onAction(LauncherShellAction.LaunchApp(app.identity)) }
                .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherAppIcon(
            identity = app.identity,
            label = app.label,
            iconLoader = appIconLoader,
            modifier = Modifier.launcherIconSize(),
            shape = CircleShape,
        )
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
        TextButton(onClick = { onAction(LauncherShellAction.AddAppToHome(app)) }) {
            Text(text = "Add")
        }
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
