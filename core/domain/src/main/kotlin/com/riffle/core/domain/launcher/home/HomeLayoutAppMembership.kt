package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutId

fun HomeLayout.containsHomeApp(identity: AppIdentity): Boolean = pages.any { page -> page.containsHomeApp(identity) }

fun HomeLayout.containsHomeAppShortcut(
    identity: AppIdentity,
    shortcutId: AppShortcutId,
): Boolean =
    pages.any { page ->
        page.containsHomeAppShortcut(identity = identity, shortcutId = shortcutId)
    }

fun DockModel.containsDockApp(identity: AppIdentity): Boolean = items.any { item -> item.containsApp(identity) }

fun DockModel.dockShortcutIdFor(identity: AppIdentity): LauncherItemId? =
    items
        .filterIsInstance<AppShortcutItem>()
        .firstOrNull { item -> item.appIdentity == identity }
        ?.id

private fun LauncherPage.containsHomeApp(identity: AppIdentity): Boolean {
    return items.any { item -> item.containsApp(identity) }
}

private fun LauncherPage.containsHomeAppShortcut(
    identity: AppIdentity,
    shortcutId: AppShortcutId,
): Boolean =
    items.any { item ->
        item.containsAppShortcut(identity = identity, shortcutId = shortcutId)
    }

private fun LauncherItem.containsApp(identity: AppIdentity): Boolean =
    when (this) {
        is AppShortcutItem -> appIdentity == identity && appShortcutId == null
        is FolderItem ->
            items.any { shortcut ->
                shortcut.appIdentity == identity && shortcut.appShortcutId == null
            }
    }

private fun LauncherItem.containsAppShortcut(
    identity: AppIdentity,
    shortcutId: AppShortcutId,
): Boolean =
    when (this) {
        is AppShortcutItem -> appIdentity == identity && appShortcutId == shortcutId
        is FolderItem ->
            items.any { shortcut ->
                shortcut.appIdentity == identity && shortcut.appShortcutId == shortcutId
            }
    }
