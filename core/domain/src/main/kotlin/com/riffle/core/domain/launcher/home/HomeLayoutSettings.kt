package com.riffle.core.domain.launcher.home

data class HomeLayoutSettings(
    val grid: GridSettings,
    val wallpaper: WallpaperSettings = WallpaperSettings.system(),
    val labels: HomeLabelSettings = HomeLabelSettings.standard(),
) {
    companion object {
        fun standardPhone(): HomeLayoutSettings =
            HomeLayoutSettings(
                grid = GridSettings.standardPhone(),
                wallpaper = WallpaperSettings.system(),
                labels = HomeLabelSettings.standard(),
            )
    }
}

data class HomeLabelSettings(
    val backgroundAlphaPercent: Int = DEFAULT_HOME_LABEL_BACKGROUND_ALPHA_PERCENT,
    val textSizeSp: Int = DEFAULT_HOME_LABEL_TEXT_SIZE_SP,
    val showText: Boolean = true,
    val maxWidthDp: Int = DEFAULT_HOME_LABEL_MAX_WIDTH_DP,
    val maxLines: Int = DEFAULT_HOME_LABEL_MAX_LINES,
    val sizing: HomeLabelSizing = HomeLabelSizing.FIXED,
) {
    companion object {
        fun standard(): HomeLabelSettings = HomeLabelSettings()
    }
}

enum class HomeLabelSizing {
    FIXED,
    DYNAMIC,
}

data class WallpaperSettings(
    val source: WallpaperSource,
) {
    companion object {
        fun system(): WallpaperSettings = WallpaperSettings(source = WallpaperSource.SYSTEM)
    }
}

enum class WallpaperSource {
    SYSTEM,
    SOLID_COLOR,
}

data class GridSettings(
    val dimensions: GridDimensions,
    val margin: GridInsets = GridInsets(),
    val padding: GridInsets = GridInsets(),
    val cellSpacing: GridSpacing = GridSpacing(),
    val compactLibraryPages: Boolean = false,
) {
    companion object {
        fun standardPhone(): GridSettings =
            GridSettings(
                dimensions = GridDimensions(columns = 4, rows = 5),
            )
    }
}

data class GridInsets(
    val start: Int = 0,
    val top: Int = 0,
    val end: Int = 0,
    val bottom: Int = 0,
)

data class GridSpacing(
    val horizontal: Int = 0,
    val vertical: Int = 0,
)

const val DEFAULT_HOME_LABEL_BACKGROUND_ALPHA_PERCENT = 54
const val MIN_HOME_LABEL_BACKGROUND_ALPHA_PERCENT = 0
const val MAX_HOME_LABEL_BACKGROUND_ALPHA_PERCENT = 100
const val DEFAULT_HOME_LABEL_TEXT_SIZE_SP = 11
const val MIN_HOME_LABEL_TEXT_SIZE_SP = 9
const val MAX_HOME_LABEL_TEXT_SIZE_SP = 16
const val DEFAULT_HOME_LABEL_MAX_WIDTH_DP = 76
const val MIN_HOME_LABEL_MAX_WIDTH_DP = 48
const val MAX_HOME_LABEL_MAX_WIDTH_DP = 120
const val DEFAULT_HOME_LABEL_MAX_LINES = 1
const val MIN_HOME_LABEL_MAX_LINES = 1
const val MAX_HOME_LABEL_MAX_LINES = 2
