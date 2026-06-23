package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutRepository

fun LauncherShellState.withHomeScreenLibraryApps(homeLayoutRepository: HomeLayoutRepository): LauncherShellState =
    homeLayout
        .withHomeScreenLibraryApps(installedApps)
        .let { libraryLayout ->
            when (libraryLayout) {
                homeLayout -> this
                else ->
                    copy(homeLayout = libraryLayout)
                        .also { state -> homeLayoutRepository.saveHomeLayout(state.homeLayout) }
            }
        }
