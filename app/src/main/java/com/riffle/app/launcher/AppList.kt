package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.containsHomeApp
import com.riffle.core.domain.launcher.home.dockShortcutIdFor

@Composable
fun AppList(
    apps: List<InstalledApp>,
    emptyText: String,
    context: AppListContext,
    modifier: Modifier = Modifier,
    showSections: Boolean = false,
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
            if (showSections) {
                AppDrawerSections.from(apps).forEach { section ->
                    item(key = "section:${section.title}") {
                        AppDrawerSectionHeader(title = section.title)
                    }
                    appRows(
                        apps = section.apps,
                        context = context,
                    )
                }
            } else {
                appRows(
                    apps = apps,
                    context = context,
                )
            }
        }
    }
}

private fun LazyListScope.appRows(
    apps: List<InstalledApp>,
    context: AppListContext,
) {
    items(
        items = apps,
        key = { app -> app.drawerKey },
    ) { app ->
        AppDrawerRow(
            app = app,
            isOnHome = context.homeLayout.containsHomeApp(app.identity),
            dockItemId = context.homeLayout.dock.dockShortcutIdFor(app.identity),
            notificationCount = context.notificationCountsByPackage[app.identity.packageName] ?: 0,
            appIconLoader = context.appIconLoader,
            onAction = context.onAction,
        )
    }
}

@Composable
private fun AppDrawerSectionHeader(title: String) {
    Text(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AppDrawerRow(
    app: InstalledApp,
    isOnHome: Boolean,
    dockItemId: LauncherItemId?,
    notificationCount: Int,
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
        if (notificationCount > 0) {
            Text(
                text = notificationCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(
            enabled = !isOnHome,
            onClick = { onAction(LauncherShellAction.AddAppToHome(app)) },
        ) {
            Text(text = if (isOnHome) "Added" else "Add")
        }
        TextButton(
            onClick = {
                when (dockItemId) {
                    null -> onAction(LauncherShellAction.AddAppToDock(app))
                    else -> onAction(LauncherShellAction.RemoveDockShortcut(dockItemId))
                }
            },
        ) {
            Text(text = if (dockItemId == null) "Dock" else "Undock")
        }
    }
}

private val InstalledApp.drawerKey: String
    get() = "${identity.profile.id.value}:${identity.packageName.value}/${identity.activityName.value}"
