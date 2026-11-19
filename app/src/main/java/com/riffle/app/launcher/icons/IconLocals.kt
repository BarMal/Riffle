package com.riffle.app.launcher.icons

import androidx.compose.runtime.compositionLocalOf
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.EmptyAppIconLoader

/** The icon loader the launcher draws with, for the places that preview icons (Settings); empty where none is given. */
internal val LocalAppIconLoader = compositionLocalOf<AppIconLoader> { EmptyAppIconLoader }

/** The icon pipeline, for the pages that list icon packs; the process's own where a test does not give one. */
internal val LocalAppIconPipeline = compositionLocalOf<AppIconPipeline?> { null }
