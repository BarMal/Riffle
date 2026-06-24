package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem

fun HomeLayout.visibleTo(apps: List<InstalledApp>): HomeLayout =
    visibleTo(
        visibleAppIdentities = apps.map { app -> app.identity }.toSet(),
    )

private fun HomeLayout.visibleTo(visibleAppIdentities: Set<AppIdentity>): HomeLayout =
    copy(
        pages =
            pages.map { page ->
                page.visibleTo(visibleAppIdentities)
            },
        dock =
            dock.copy(
                items = dock.items.mapNotNull { item -> item.visibleTo(visibleAppIdentities) },
            ),
    ).withoutTrailingEmptyLibraryPages()

private fun LauncherPage.visibleTo(visibleAppIdentities: Set<AppIdentity>): LauncherPage =
    copy(
        items = items.mapNotNull { item -> item.visibleTo(visibleAppIdentities) },
    )

private fun LauncherItem.visibleTo(visibleAppIdentities: Set<AppIdentity>): LauncherItem? =
    when (this) {
        is AppShortcutItem -> takeIf { item -> item.appIdentity in visibleAppIdentities }
        is FolderItem ->
            copy(items = items.filter { item -> item.appIdentity in visibleAppIdentities })
                .takeIf { folder -> items.isEmpty() || folder.items.isNotEmpty() }
        is WidgetItem -> this
    }
