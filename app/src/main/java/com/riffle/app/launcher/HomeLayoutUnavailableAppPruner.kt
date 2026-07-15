package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.WidgetItem

fun LauncherShellState.withoutUnavailableApps(homeLayoutRepository: HomeLayoutRepository): LauncherShellState =
    homeLayout
        .keepingApps(installedApps.map { app -> app.identity }.toSet())
        .let { prunedLayout ->
            when (prunedLayout) {
                homeLayout -> this
                else -> withHomeLayout(prunedLayout, homeLayoutRepository)
            }
        }

fun LauncherShellState.withoutConfirmedPackage(
    packageName: AppPackageName,
    profile: AppProfile,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    homeLayout
        .keepingApps { identity -> identity.packageName != packageName || identity.profile != profile }
        .let { prunedLayout ->
            when (prunedLayout) {
                homeLayout -> this
                else -> withHomeLayout(prunedLayout, homeLayoutRepository)
            }
        }

fun HomeLayout.keepingApps(identities: Set<AppIdentity>): HomeLayout = keepingApps { identity -> identity in identities }

private fun HomeLayout.keepingApps(shouldKeep: (AppIdentity) -> Boolean): HomeLayout =
    copy(
        pages =
            pages.map { page ->
                page.copy(items = page.items.mapNotNull { item -> item.keepingApps(shouldKeep) })
            },
        dock =
            dock.copy(
                items = dock.items.mapNotNull { item -> item.keepingApps(shouldKeep) },
            ),
    ).withoutTrailingEmptyLibraryPages()

private fun LauncherItem.keepingApps(shouldKeep: (AppIdentity) -> Boolean): LauncherItem? =
    when (this) {
        is AppShortcutItem -> takeIf { item -> shouldKeep(item.appIdentity) }
        is FolderItem ->
            copy(items = items.filter { item -> shouldKeep(item.appIdentity) })
                .takeIf { folder -> folder.items.isNotEmpty() }
        is WidgetItem -> this
    }
