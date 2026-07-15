package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidInstalledWidgetProviderMapperTest {
    private val mapper = AndroidInstalledWidgetProviderMapper()

    @Test
    fun mapsAndroidProviderPixelsToDensityIndependentDomainDimensions() {
        val provider =
            AndroidWidgetProvider(
                packageName = "com.example.weather",
                className = ".WeatherWidget",
                profile = null,
                label = "Weather",
                description = "Forecast",
                minWidthPx = 1350,
                minHeightPx = 300,
                minResizeWidthPx = 240,
                minResizeHeightPx = 180,
                maxResizeWidthPx = 1500,
                maxResizeHeightPx = 600,
                targetCellWidth = 3,
                targetCellHeight = 2,
                resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL,
                hasConfigurationActivity = true,
                supportsReconfiguration = true,
            )

        val mapped = mapper.map(provider, density = 3f)

        assertEquals(
            WidgetProviderIdentity(
                packageName = AppPackageName("com.example.weather"),
                className = WidgetProviderClassName(".WeatherWidget"),
            ),
            mapped.identity,
        )
        assertEquals("Weather", mapped.label)
        assertEquals("Forecast", mapped.description)
        assertEquals(
            WidgetProviderDimensions(
                minWidthDp = 450,
                minHeightDp = 100,
                minResizeWidthDp = 80,
                minResizeHeightDp = 60,
                maxResizeWidthDp = 500,
                maxResizeHeightDp = 200,
                targetCellWidth = 3,
                targetCellHeight = 2,
            ),
            mapped.dimensions,
        )
        assertTrue(mapped.supportsHorizontalResize)
        assertTrue(mapped.supportsVerticalResize)
        assertTrue(mapped.hasConfigurationActivity)
        assertTrue(mapped.supportsReconfiguration)
    }

    @Test
    fun fallsBackToPackageNameForBlankLabelsAndDropsBlankDescriptions() {
        val provider =
            AndroidWidgetProvider(
                packageName = "com.example.notes",
                className = ".NotesWidget",
                profile = null,
                label = "",
                description = "",
                minWidthPx = 100,
                minHeightPx = 50,
                minResizeWidthPx = null,
                minResizeHeightPx = null,
                targetCellWidth = null,
                targetCellHeight = null,
                resizeMode = AppWidgetProviderInfo.RESIZE_NONE,
            )

        val mapped = mapper.map(provider, density = 1f)

        assertEquals("com.example.notes", mapped.label)
        assertEquals(null, mapped.description)
        assertFalse(mapped.supportsHorizontalResize)
        assertFalse(mapped.supportsVerticalResize)
    }

    @Test
    fun roundsProviderPixelsUpAtFractionalDensitiesWithoutUnderstatingMinimums() {
        val provider =
            AndroidWidgetProvider(
                packageName = "com.example.clock",
                className = ".ClockWidget",
                profile = null,
                label = "Clock",
                description = null,
                minWidthPx = 401,
                minHeightPx = 201,
                minResizeWidthPx = 200,
                minResizeHeightPx = 100,
                targetCellWidth = null,
                targetCellHeight = null,
                resizeMode = AppWidgetProviderInfo.RESIZE_NONE,
            )

        val dimensions = mapper.map(provider, density = 2.5f).dimensions

        assertEquals(161, dimensions.minWidthDp)
        assertEquals(81, dimensions.minHeightDp)
        assertEquals(80, dimensions.minResizeWidthDp)
        assertEquals(40, dimensions.minResizeHeightDp)
    }

    @Test
    fun dropsMalformedOptionalGeometryAndKeepsMinimumsSafe() {
        val provider =
            AndroidWidgetProvider(
                packageName = "com.example.broken",
                className = ".BrokenWidget",
                profile = null,
                label = "Broken",
                description = null,
                minWidthPx = 0,
                minHeightPx = Int.MAX_VALUE,
                minResizeWidthPx = 400,
                minResizeHeightPx = -1,
                maxResizeWidthPx = 20,
                maxResizeHeightPx = Int.MAX_VALUE,
                targetCellWidth = 0,
                targetCellHeight = 101,
                resizeMode = AppWidgetProviderInfo.RESIZE_NONE,
            )

        val dimensions = mapper.map(provider, density = 2f).dimensions

        assertEquals(1, dimensions.minWidthDp)
        assertEquals(1, dimensions.minHeightDp)
        assertEquals(null, dimensions.minResizeWidthDp)
        assertEquals(null, dimensions.minResizeHeightDp)
        assertEquals(10, dimensions.maxResizeWidthDp)
        assertEquals(null, dimensions.maxResizeHeightDp)
        assertEquals(null, dimensions.targetCellWidth)
        assertEquals(null, dimensions.targetCellHeight)
    }
}
