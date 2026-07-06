package com.riffle.core.domain.launcher.settings

val AppearanceSettings.homeStatusBarHidden: Boolean
    get() = fullscreenHome || hideStatusBarOnHome

val AppearanceSettings.homeNavigationBarHidden: Boolean
    get() = fullscreenHome || hideNavigationBarOnHome

fun AppearanceSettings.withFullscreenHome(enabled: Boolean): AppearanceSettings =
    copy(
        fullscreenHome = enabled,
        hideStatusBarOnHome = enabled,
        hideNavigationBarOnHome = enabled,
    )

fun AppearanceSettings.withHomeStatusBarHidden(hidden: Boolean): AppearanceSettings {
    val effectiveNavigationBarHidden = homeNavigationBarHidden
    return copy(
        fullscreenHome = hidden && effectiveNavigationBarHidden,
        hideStatusBarOnHome = hidden,
        hideNavigationBarOnHome = effectiveNavigationBarHidden,
    )
}

fun AppearanceSettings.withHomeNavigationBarHidden(hidden: Boolean): AppearanceSettings {
    val effectiveStatusBarHidden = homeStatusBarHidden
    return copy(
        fullscreenHome = effectiveStatusBarHidden && hidden,
        hideStatusBarOnHome = effectiveStatusBarHidden,
        hideNavigationBarOnHome = hidden,
    )
}
