package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.LauncherItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PreloadLauncherAppIcons(
    identities: List<AppIdentity>,
    iconLoader: AppIconLoader,
) {
    LaunchedEffect(identities, iconLoader) {
        withContext(Dispatchers.Default) {
            iconLoader.preloadIcons(identities)
        }
    }
}

fun LauncherShellState.appIconPreloadIdentities(): List<AppIdentity> =
    (
        installedApps.map { app -> app.identity } +
            homeLayout.pages.flatMap { page ->
                page.items.flatMap { item -> item.appIconPreloadIdentities() }
            } +
            homeLayout.dock.items
                .flatMap { item -> item.appIconPreloadIdentities() } +
            launcherSettings.overlayDock.items.map { shortcut -> shortcut.appIdentity }
    ).distinct()

private fun LauncherItem.appIconPreloadIdentities(): List<AppIdentity> =
    when (this) {
        is AppShortcutItem -> listOf(appIdentity)
        is FolderItem -> items.map { shortcut -> shortcut.appIdentity }
        else -> emptyList()
    }
