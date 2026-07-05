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
                .map { shellState ->
                    LauncherSystemUiMode(
                        fullscreenHome = shellState.launcherSettings.appearance.fullscreenHome,
                        destination = shellState.destination,
                    )
                }
                .distinctUntilChanged()
                .collect(::applyLauncherSystemUiMode)
        }
    }
}

private fun ComponentActivity.applyLauncherSystemUiMode(mode: LauncherSystemUiMode) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (mode.fullscreenHome && mode.destination == ShellDestination.HOME) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

private data class LauncherSystemUiMode(
    val fullscreenHome: Boolean,
    val destination: ShellDestination,
)
