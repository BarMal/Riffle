package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity

fun HomeLayout.containsHomeApp(identity: AppIdentity): Boolean = pages.any { page -> page.containsHomeApp(identity) }

fun DockModel.containsDockApp(identity: AppIdentity): Boolean = items.any { item -> item.containsApp(identity) }

private fun LauncherPage.containsHomeApp(identity: AppIdentity): Boolean {
    return items.any { item -> item.containsApp(identity) }
}

private fun LauncherItem.containsApp(identity: AppIdentity): Boolean =
    when (this) {
        is AppShortcutItem -> appIdentity == identity
        is FolderItem -> items.any { shortcut -> shortcut.appIdentity == identity }
    }
