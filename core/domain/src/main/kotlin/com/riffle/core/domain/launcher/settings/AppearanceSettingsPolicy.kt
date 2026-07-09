package com.riffle.core.domain.launcher.settings

enum class HomeSystemBar {
    STATUS,
    NAVIGATION,
}

data class HomeSystemBars(
    val fullscreenHome: Boolean = false,
    val hideStatusBarOnHome: Boolean = false,
    val hideNavigationBarOnHome: Boolean = false,
) {
    val statusBarHidden: Boolean
        get() = fullscreenHome || hideStatusBarOnHome

    val navigationBarHidden: Boolean
        get() = fullscreenHome || hideNavigationBarOnHome

    fun withFullscreenHome(enabled: Boolean): HomeSystemBars =
        if (enabled) {
            copy(fullscreenHome = true)
        } else {
            // Clearing fullscreen should only preserve an independent bar choice
            // when that choice existed before fullscreen was enabled.
            val clearIndependentSystemBars = hideStatusBarOnHome && hideNavigationBarOnHome
            copy(
                fullscreenHome = false,
                hideStatusBarOnHome = if (clearIndependentSystemBars) false else hideStatusBarOnHome,
                hideNavigationBarOnHome = if (clearIndependentSystemBars) false else hideNavigationBarOnHome,
            )
        }

    fun withSystemBarHidden(
        systemBar: HomeSystemBar,
        hidden: Boolean,
    ): HomeSystemBars =
        when (systemBar) {
            HomeSystemBar.STATUS -> {
                val effectiveNavigationBarHidden = navigationBarHidden
                copy(
                    fullscreenHome = hidden && effectiveNavigationBarHidden,
                    hideStatusBarOnHome = hidden,
                    hideNavigationBarOnHome = effectiveNavigationBarHidden,
                )
            }

            HomeSystemBar.NAVIGATION -> {
                val effectiveStatusBarHidden = statusBarHidden
                copy(
                    fullscreenHome = effectiveStatusBarHidden && hidden,
                    hideStatusBarOnHome = effectiveStatusBarHidden,
                    hideNavigationBarOnHome = hidden,
                )
            }
        }

    fun setting(systemBar: HomeSystemBar): HomeSystemBarSetting =
        HomeSystemBarSetting(
            checked =
                when (systemBar) {
                    HomeSystemBar.STATUS -> statusBarHidden
                    HomeSystemBar.NAVIGATION -> navigationBarHidden
                },
            enabled = !fullscreenHome,
        )
}

data class HomeSystemBarSetting(
    val checked: Boolean,
    val enabled: Boolean,
)

val AppearanceSettings.homeSystemBars: HomeSystemBars
    get() =
        HomeSystemBars(
            fullscreenHome = fullscreenHome,
            hideStatusBarOnHome = hideStatusBarOnHome,
            hideNavigationBarOnHome = hideNavigationBarOnHome,
        )

fun AppearanceSettings.withHomeSystemBars(homeSystemBars: HomeSystemBars): AppearanceSettings =
    copy(
        fullscreenHome = homeSystemBars.fullscreenHome,
        hideStatusBarOnHome = homeSystemBars.hideStatusBarOnHome,
        hideNavigationBarOnHome = homeSystemBars.hideNavigationBarOnHome,
    )

val AppearanceSettings.homeStatusBarHidden: Boolean
    get() = homeSystemBars.statusBarHidden

val AppearanceSettings.homeNavigationBarHidden: Boolean
    get() = homeSystemBars.navigationBarHidden

fun AppearanceSettings.withFullscreenHome(enabled: Boolean): AppearanceSettings =
    withHomeSystemBars(homeSystemBars.withFullscreenHome(enabled))

fun AppearanceSettings.withHomeStatusBarHidden(hidden: Boolean): AppearanceSettings =
    withHomeSystemBars(homeSystemBars.withSystemBarHidden(HomeSystemBar.STATUS, hidden))

fun AppearanceSettings.withHomeNavigationBarHidden(hidden: Boolean): AppearanceSettings =
    withHomeSystemBars(homeSystemBars.withSystemBarHidden(HomeSystemBar.NAVIGATION, hidden))
