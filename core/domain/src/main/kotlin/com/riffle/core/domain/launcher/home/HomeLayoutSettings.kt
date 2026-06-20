package com.riffle.core.domain.launcher.home

data class HomeLayoutSettings(
    val grid: GridSettings,
) {
    companion object {
        fun standardPhone(): HomeLayoutSettings =
            HomeLayoutSettings(
                grid = GridSettings.standardPhone(),
            )
    }
}

data class GridSettings(
    val dimensions: GridDimensions,
    val margin: GridInsets = GridInsets(),
    val padding: GridInsets = GridInsets(),
    val cellSpacing: GridSpacing = GridSpacing(),
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
