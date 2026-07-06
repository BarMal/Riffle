package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.WallpaperSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

fun ComponentActivity.startWallpaperOffsetSync(
    state: StateFlow<LauncherShellState>,
    wallpaperController: LauncherWallpaperController,
) {
    lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state
                .map(::launcherWallpaperOffsetCommand)
                .distinctUntilChanged()
                .collect { command -> command?.let(wallpaperController::applyOffset) }
        }
    }
}

internal fun launcherWallpaperOffsetCommand(state: LauncherShellState): LauncherWallpaperOffsetCommand? {
    val wallpaper = state.launcherSettings.appearance.wallpaper
    if (wallpaper.source != WallpaperSource.SYSTEM) return null

    return wallpaperOffsetCommand(
        scrollMode = wallpaper.scrollMode,
        selectedPageIndex = state.homeLayout.selectedPageIndex,
        pageCount = state.homeLayout.pages.size,
    )
}
