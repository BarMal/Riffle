package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.LauncherViewMode

enum class HomeSurfaceKind {
    GRID,
    CARDS,
}

fun LauncherViewMode.homeSurfaceKind(): HomeSurfaceKind =
    when (this) {
        LauncherViewMode.STANDARD_APP_DRAWER,
        LauncherViewMode.HOME_SCREEN_LIBRARY,
        -> HomeSurfaceKind.GRID

        LauncherViewMode.CARD_INTERFACE -> HomeSurfaceKind.CARDS
    }
