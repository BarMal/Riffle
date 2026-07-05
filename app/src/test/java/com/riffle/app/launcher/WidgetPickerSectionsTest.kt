package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPickerSectionsTest {
    @Test
    fun sectionsWidgetProvidersByProfileLabel() {
        val personal = widgetProvider(label = "Clock", profile = AppProfile.personal())
        val work = widgetProvider(label = "Calendar", profile = AppProfile.work())
        val private = widgetProvider(label = "Vault", profile = AppProfile.private())

        val sections = widgetPickerSectionsFor(listOf(personal, work, private))

        assertEquals(
            listOf("Personal", "Work", "Private"),
            sections.map { section -> section.title },
        )
        assertEquals(
            listOf("Personal (1)", "Work (1)", "Private (1)"),
            sections.map { section -> section.displayTitle },
        )
    }

    @Test
    fun keepsProviderOrderWithinProfileSections() {
        val clock = widgetProvider(label = "Clock", profile = AppProfile.personal())
        val weather = widgetProvider(label = "Weather", profile = AppProfile.personal())

        val sections = widgetPickerSectionsFor(listOf(clock, weather))

        assertEquals(listOf(clock, weather), sections.single().providers)
    }

    private fun widgetProvider(
        label: String,
        profile: AppProfile,
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase()}"),
                    className = WidgetProviderClassName(".$label"),
                    profile = profile,
                ),
            label = label,
            dimensions = WidgetProviderDimensions(minWidthDp = 120, minHeightDp = 80),
        )
}
