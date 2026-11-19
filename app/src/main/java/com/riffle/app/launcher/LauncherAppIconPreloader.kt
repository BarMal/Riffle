package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import com.riffle.app.launcher.icons.ThemedIconColors
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PreloadLauncherAppIcons(
    identities: List<AppIdentity>,
    iconLoader: AppIconLoader,
) {
    // A new style is drawn for every app in view and then for the rest, off the main thread.
    val version = iconLoader.styleVersion
    LaunchedEffect(identities, iconLoader, version) {
        withContext(Dispatchers.Default) {
            iconLoader.preloadIcons(identities)
        }
    }
}

/**
 * Gives the icon loader the icon style and the theme's colours (themed icons are drawn in the primary container).
 * Call it inside the launcher theme; the loader applies it cheaply and everything that drew an icon draws it again.
 */
@Composable
fun SyncIconStyle(
    iconLoader: AppIconLoader,
    style: IconStyleSettings,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = ThemedIconColors(scheme.primaryContainer.toArgb(), scheme.onPrimaryContainer.toArgb())
    SideEffect { iconLoader.applyStyle(style, colors) }
}

fun LauncherShellState.appIconPreloadIdentities(): List<AppIdentity> =
    (
        installedApps.map { app -> app.identity } +
            homeLayout.pages.flatMap { page ->
                page.items.flatMap { item -> item.appIconPreloadIdentities() }
            } +
            homeLayout.dock.items
                .flatMap { item -> item.appIconPreloadIdentities() } +
            homeLayout.dock.panel
                ?.items
                .orEmpty()
                .flatMap { item -> item.appIconPreloadIdentities() } +
            launcherSettings.overlayDock.items.map { shortcut -> shortcut.appIdentity }
    ).distinct()

private fun LauncherItem.appIconPreloadIdentities(): List<AppIdentity> =
    when (this) {
        is AppShortcutItem -> listOf(appIdentity)
        is FolderItem -> items.map { shortcut -> shortcut.appIdentity }
        else -> emptyList()
    }
