package com.riffle.core.domain.launcher.home

sealed interface LauncherPageType {
    data object Home : LauncherPageType

    data object AllApps : LauncherPageType

    data class Generated(val kind: GeneratedLauncherPageKind) : LauncherPageType
}

enum class GeneratedLauncherPageKind {
    APP,
    CATEGORY,
    TODAY,
    WORK,
    PERSONAL,
    FAVOURITES,
    FREQUENTLY_USED,
    NOTIFICATION_CARDS,
}
