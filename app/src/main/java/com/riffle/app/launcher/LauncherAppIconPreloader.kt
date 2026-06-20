package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
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
                page.items
                    .filterIsInstance<AppShortcutItem>()
                    .map { shortcut -> shortcut.appIdentity }
            } +
            homeLayout.dock.items
                .filterIsInstance<AppShortcutItem>()
                .map { shortcut -> shortcut.appIdentity }
    ).distinct()
