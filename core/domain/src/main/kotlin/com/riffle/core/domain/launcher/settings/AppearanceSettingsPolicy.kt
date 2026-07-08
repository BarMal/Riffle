package com.riffle.core.domain.launcher.settings

val AppearanceSettings.homeStatusBarHidden: Boolean
    get() = fullscreenHome || hideStatusBarOnHome

val AppearanceSettings.homeNavigationBarHidden: Boolean
    get() = fullscreenHome || hideNavigationBarOnHome

fun AppearanceSettings.withFullscreenHome(enabled: Boolean): AppearanceSettings =
    if (enabled) {
        copy(fullscreenHome = true)
    } else {
        // When both bars are already hidden, clearing fullscreen should reveal both bars
        // instead of leaving the setting visually off but effectively unchanged.
        val clearIndependentSystemBars = hideStatusBarOnHome && hideNavigationBarOnHome
        copy(
            fullscreenHome = false,
            hideStatusBarOnHome = if (clearIndependentSystemBars) false else hideStatusBarOnHome,
            hideNavigationBarOnHome = if (clearIndependentSystemBars) false else hideNavigationBarOnHome,
        )
    }

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
