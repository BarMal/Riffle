package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItem

fun LauncherShellState.withoutHiddenApps(homeLayoutRepository: HomeLayoutRepository): LauncherShellState =
    homeLayout
        .withoutApps(hiddenApps.map { app -> app.identity }.toSet())
        .let { prunedLayout ->
            when (prunedLayout) {
                homeLayout -> this
                else ->
                    copy(homeLayout = prunedLayout)
                        .also { state -> homeLayoutRepository.saveHomeLayout(state.homeLayout) }
            }
        }

fun HomeLayout.withoutApps(identities: Set<AppIdentity>): HomeLayout =
    copy(
        pages =
            pages.map { page ->
                page.copy(items = page.items.mapNotNull { item -> item.withoutApps(identities) })
            },
        dock =
            dock.copy(
                items = dock.items.mapNotNull { item -> item.withoutApps(identities) },
            ),
    )

private fun LauncherItem.withoutApps(identities: Set<AppIdentity>): LauncherItem? =
    when (this) {
        is AppShortcutItem -> takeUnless { item -> item.appIdentity in identities }
        is FolderItem ->
            copy(items = items.filterNot { item -> item.appIdentity in identities })
                .takeIf { folder -> folder.items.isNotEmpty() }
    }
