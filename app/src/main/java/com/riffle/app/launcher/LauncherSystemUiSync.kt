package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
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
    LauncherSystemUiMode(
        hideStatusBarOnHome =
            shellState.launcherSettings.appearance.fullscreenHome ||
                shellState.launcherSettings.appearance.hideStatusBarOnHome,
        hideNavigationBarOnHome =
            shellState.launcherSettings.appearance.fullscreenHome ||
                shellState.launcherSettings.appearance.hideNavigationBarOnHome,
        destination = shellState.destination,
    )

internal data class LauncherSystemUiMode(
    val hideStatusBarOnHome: Boolean,
    val hideNavigationBarOnHome: Boolean,
    val destination: ShellDestination,
) {
    val shouldHideStatusBars: Boolean =
        destination == ShellDestination.HOME && hideStatusBarOnHome

    val shouldHideNavigationBars: Boolean =
        destination == ShellDestination.HOME && hideNavigationBarOnHome
}
