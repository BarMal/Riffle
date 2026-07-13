package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutSettingsTest {
    @Test
    fun standardPhoneSettingsUseTheConventionalHomeGrid() {
        val settings = HomeLayoutSettings.standardPhone()

        assertEquals(GridDimensions(columns = 4, rows = 5), settings.grid.dimensions)
        assertEquals(standardHomeGridMargin, settings.grid.margin)
        assertEquals(GridInsets(), settings.grid.padding)
        assertEquals(GridSpacing(), settings.grid.cellSpacing)
        assertEquals(false, settings.grid.compactLibraryPages)
        assertEquals(WallpaperSettings.system(), settings.wallpaper)
        assertEquals(HomeLabelSettings.standard(), settings.labels)
        assertEquals(HomeLabelSizing.FIXED, settings.labels.sizing)
        assertEquals(DEFAULT_HOME_ICON_SIZE_DP, settings.labels.iconSizeDp)
    }

    @Test
    fun standardFoldableSettingsUseAnExpandedGrid() {
        val settings = HomeLayoutSettings.standardFoldable()

        assertEquals(GridDimensions(columns = 5, rows = 6), settings.grid.dimensions)
        assertEquals(WallpaperSettings.system(), settings.wallpaper)
        assertEquals(HomeLabelSettings.standard(), settings.labels)
    }

    @Test
    fun standardTabletSettingsUseTheLargestDefaultGrid() {
        val settings = HomeLayoutSettings.standardTablet()

        assertEquals(GridDimensions(columns = 6, rows = 6), settings.grid.dimensions)
        assertEquals(WallpaperSettings.system(), settings.wallpaper)
        assertEquals(HomeLabelSettings.standard(), settings.labels)
    }

    @Test
    fun standardDesktopSettingsUseTheWidestDefaultGrid() {
        val settings = HomeLayoutSettings.standardDesktop()

        assertEquals(GridDimensions(columns = 8, rows = 6), settings.grid.dimensions)
        assertEquals(WallpaperSettings.system(), settings.wallpaper)
        assertEquals(HomeLabelSettings.standard(), settings.labels)
    }

    @Test
    fun gridSettingsCanConfigureSpacingWithoutChangingGridOccupancy() {
        val settings =
            GridSettings(
                dimensions = GridDimensions(columns = 5, rows = 6),
                margin = GridInsets(start = 1, top = 2, end = 3, bottom = 4),
                padding = GridInsets(start = 5, top = 6, end = 7, bottom = 8),
                cellSpacing = GridSpacing(horizontal = 9, vertical = 10),
            )

        assertEquals(GridDimensions(columns = 5, rows = 6), settings.dimensions)
        assertEquals(GridInsets(start = 1, top = 2, end = 3, bottom = 4), settings.margin)
        assertEquals(GridInsets(start = 5, top = 6, end = 7, bottom = 8), settings.padding)
        assertEquals(GridSpacing(horizontal = 9, vertical = 10), settings.cellSpacing)
        assertEquals(false, settings.compactLibraryPages)
    }

    @Test
    fun wallpaperSettingsCanUseASolidColourFallback() {
        val settings = WallpaperSettings(source = WallpaperSource.SOLID_COLOR)

        assertEquals(WallpaperSource.SOLID_COLOR, settings.source)
    }
}
