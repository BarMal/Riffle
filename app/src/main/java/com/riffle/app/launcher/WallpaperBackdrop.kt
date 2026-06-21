package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource

@Composable
fun WallpaperBackdrop(settings: WallpaperSettings) {
    if (settings.source != WallpaperSource.SYSTEM) {
        return
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = WALLPAPER_SCRIM_ALPHA)),
    )
}

private const val WALLPAPER_SCRIM_ALPHA = 0.24f
