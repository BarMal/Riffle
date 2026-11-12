package com.riffle.core.domain.launcher.settings

enum class HomeSystemBar {
    STATUS,
    NAVIGATION,
}

/**
 * Which system bars Home hides (Home > Full screen). The old all-in-one "Fullscreen home" flag is gone: it was the
 * same as hiding both bars, which is how [withBothHidden] (the full-screen gesture action) and the decoder of older
 * data treat it.
 */
data class HomeSystemBars(
    val hideStatusBarOnHome: Boolean = false,
    val hideNavigationBarOnHome: Boolean = false,
) {
    val statusBarHidden: Boolean
        get() = hideStatusBarOnHome

    val navigationBarHidden: Boolean
        get() = hideNavigationBarOnHome

    fun withBothHidden(hidden: Boolean): HomeSystemBars =
        copy(hideStatusBarOnHome = hidden, hideNavigationBarOnHome = hidden)

    fun withSystemBarHidden(
        systemBar: HomeSystemBar,
        hidden: Boolean,
    ): HomeSystemBars =
        when (systemBar) {
            HomeSystemBar.STATUS -> copy(hideStatusBarOnHome = hidden)
            HomeSystemBar.NAVIGATION -> copy(hideNavigationBarOnHome = hidden)
        }
}

val AppearanceSettings.homeSystemBars: HomeSystemBars
    get() =
        HomeSystemBars(
            hideStatusBarOnHome = hideStatusBarOnHome,
            hideNavigationBarOnHome = hideNavigationBarOnHome,
        )

fun AppearanceSettings.withHomeSystemBars(homeSystemBars: HomeSystemBars): AppearanceSettings =
    copy(
        hideStatusBarOnHome = homeSystemBars.hideStatusBarOnHome,
        hideNavigationBarOnHome = homeSystemBars.hideNavigationBarOnHome,
    )

val AppearanceSettings.homeStatusBarHidden: Boolean
    get() = hideStatusBarOnHome

val AppearanceSettings.homeNavigationBarHidden: Boolean
    get() = hideNavigationBarOnHome

/** Hides or shows both bars at once: what the full-screen gesture action does. */
fun AppearanceSettings.withFullscreenHome(enabled: Boolean): AppearanceSettings =
    withHomeSystemBars(homeSystemBars.withBothHidden(enabled))

fun AppearanceSettings.withHomeStatusBarHidden(hidden: Boolean): AppearanceSettings =
    withHomeSystemBars(homeSystemBars.withSystemBarHidden(HomeSystemBar.STATUS, hidden))

fun AppearanceSettings.withHomeNavigationBarHidden(hidden: Boolean): AppearanceSettings =
    withHomeSystemBars(homeSystemBars.withSystemBarHidden(HomeSystemBar.NAVIGATION, hidden))
