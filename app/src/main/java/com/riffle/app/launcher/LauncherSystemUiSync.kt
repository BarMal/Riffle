package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.homeSystemBars
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

fun ComponentActivity.startSystemUiSync(state: StateFlow<LauncherShellState>) {
    lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state
                .map(::launcherSystemUiMode)
                .distinctUntilChanged()
                .collect(::applyLauncherSystemUiMode)
        }
    }
}

private fun ComponentActivity.applyLauncherSystemUiMode(mode: LauncherSystemUiMode) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.isAppearanceLightStatusBars =
        mode.usesLightStatusBarAppearance(
            systemIsDarkTheme =
                resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES,
        )

    if (mode.shouldHideStatusBars) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
    }

    if (mode.shouldHideNavigationBars) {
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    } else {
        controller.show(WindowInsetsCompat.Type.navigationBars())
    }
}

internal fun launcherSystemUiMode(shellState: LauncherShellState): LauncherSystemUiMode =
    shellState.launcherSettings.appearance.homeSystemBars.let { homeSystemBars ->
        LauncherSystemUiMode(
            hideStatusBarOnHome = homeSystemBars.statusBarHidden,
            hideNavigationBarOnHome = homeSystemBars.navigationBarHidden,
            destination = shellState.destination,
            themeMode = shellState.launcherSettings.appearance.themeMode,
        )
    }

internal data class LauncherSystemUiMode(
    val hideStatusBarOnHome: Boolean,
    val hideNavigationBarOnHome: Boolean,
    val destination: ShellDestination,
    val themeMode: LauncherThemeMode,
) {
    val shouldHideStatusBars: Boolean =
        destination == ShellDestination.HOME && hideStatusBarOnHome

    val shouldHideNavigationBars: Boolean =
        destination == ShellDestination.HOME && hideNavigationBarOnHome

    fun usesLightStatusBarAppearance(systemIsDarkTheme: Boolean): Boolean =
        when (themeMode) {
            LauncherThemeMode.LIGHT -> true
            LauncherThemeMode.DARK -> false
            LauncherThemeMode.SYSTEM -> !systemIsDarkTheme
        }
}
