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
    fun mapsAndroidProviderFieldsToDomainProvider() {
        val provider =
            AndroidWidgetProvider(
                packageName = "com.example.weather",
                className = ".WeatherWidget",
                profile = null,
                label = "Weather",
                description = "Forecast",
                minWidthDp = 120,
                minHeightDp = 80,
                minResizeWidthDp = 80,
                minResizeHeightDp = 60,
                resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL,
            )

        val mapped = mapper.map(provider)

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
                minWidthDp = 120,
                minHeightDp = 80,
                minResizeWidthDp = 80,
                minResizeHeightDp = 60,
            ),
            mapped.dimensions,
        )
        assertTrue(mapped.supportsHorizontalResize)
        assertTrue(mapped.supportsVerticalResize)
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
                minWidthDp = 100,
                minHeightDp = 50,
                minResizeWidthDp = null,
                minResizeHeightDp = null,
                resizeMode = AppWidgetProviderInfo.RESIZE_NONE,
            )

        val mapped = mapper.map(provider)

        assertEquals("com.example.notes", mapped.label)
        assertEquals(null, mapped.description)
        assertFalse(mapped.supportsHorizontalResize)
        assertFalse(mapped.supportsVerticalResize)
    }
}
