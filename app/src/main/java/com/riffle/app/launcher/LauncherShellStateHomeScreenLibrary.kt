package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.SettledLayoutMemo

/**
 * Library relayout runs after every home and mode action, but only does work when its inputs moved:
 * the layout (pages, grid, mode) or the installed-app list. A layout it already left untouched for
 * the same apps is returned as is, without re-running placement.
 */
private val settledLibraryLayouts = SettledLayoutMemo<List<InstalledApp>>()

fun LauncherShellState.withHomeScreenLibraryApps(homeLayoutRepository: HomeLayoutRepository): LauncherShellState =
    settledLibraryLayouts
        .transformUnlessSettled(layout = homeLayout, inputs = installedApps) { layout ->
            layout.withHomeScreenLibraryApps(installedApps)
        }
        .let { libraryLayout ->
            when (libraryLayout) {
                homeLayout -> this
                else -> withHomeLayout(libraryLayout, homeLayoutRepository)
            }
        }
