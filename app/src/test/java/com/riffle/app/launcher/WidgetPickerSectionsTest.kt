package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPickerSectionsTest {
    @Test
    fun sectionsWidgetProvidersByOwningAppAndProfile() {
        val personal = widgetProvider(label = "Clock", profile = AppProfile.personal())
        val work = widgetProvider(label = "Calendar", profile = AppProfile.work())
        val private = widgetProvider(label = "Vault", profile = AppProfile.private())

        val sections = widgetPickerSectionsFor(listOf(work, personal, private))

        assertEquals(
            listOf("Personal - Clock", "Work - Calendar", "Private - Vault"),
            sections.map { section -> section.title },
        )
        assertEquals(
            listOf("Personal - Clock (1)", "Work - Calendar (1)", "Private - Vault (1)"),
            sections.map { section -> section.displayTitle },
        )
    }

    @Test
    fun groupsVariantsFromTheSameAppAndSortsThemByVisibleLabel() {
        val clock =
            widgetProvider(
                label = "Clock",
                appLabel = "Google",
                packageName = "com.example.google",
            )
        val weather =
            widgetProvider(
                label = "Weather",
                appLabel = "Google",
                packageName = "com.example.google",
            )

        val sections = widgetPickerSectionsFor(listOf(weather, clock))

        assertEquals("Personal - Google", sections.single().title)
        assertEquals(listOf(clock, weather), sections.single().providers)
    }

    @Test
    fun keepsAppsWithIdenticalLabelsInSeparateStableSections() {
        val first = widgetProvider(label = "Clock", appLabel = "Clock", packageName = "com.example.clock")
        val second = widgetProvider(label = "Clock", appLabel = "Clock", packageName = "com.other.clock")

        val sections = widgetPickerSectionsFor(listOf(first, second))

        assertEquals(2, sections.size)
        assertEquals(2, sections.map { section -> section.key }.toSet().size)
        assertEquals(listOf(first), sections[0].providers)
        assertEquals(listOf(second), sections[1].providers)
    }

    @Test
    fun appliesTheOwningProfilesContentPolicyToEveryAppSection() {
        val workProfile = AppProfile.work()
        val provider = widgetProvider(label = "Calendar", profile = workProfile)

        val section =
            widgetPickerSectionsFor(
                providers = listOf(provider),
                profileContentVisibility =
                    mapOf(workProfile.id to AppProfileContentVisibility.REDACTED_QUIET),
            ).single()

        assertEquals(AppProfileContentVisibility.REDACTED_QUIET, section.contentVisibility)
    }

    @Test
    fun treatsAProviderWithNoProfileDecisionAsUnavailable() {
        val section =
            widgetPickerSectionsFor(
                providers = listOf(widgetProvider(label = "Vault", profile = AppProfile.private())),
                profileContentVisibility = emptyMap(),
            ).single()

        assertEquals(AppProfileContentVisibility.REDACTED_UNAVAILABLE, section.contentVisibility)
    }

    @Test
    fun explainsEveryUnavailableProfileState() {
        assertEquals(
            "This profile is paused. Turn it on to preview or place its widgets.",
            widgetPickerProfileStateText(AppProfileContentVisibility.REDACTED_QUIET),
        )
        assertEquals(
            "This profile is locked. Unlock it to preview or place its widgets.",
            widgetPickerProfileStateText(AppProfileContentVisibility.REDACTED_LOCKED),
        )
        assertEquals(
            "This profile is unavailable. Its widgets can’t be previewed or placed.",
            widgetPickerProfileStateText(AppProfileContentVisibility.REDACTED_UNAVAILABLE),
        )
    }

    private fun widgetProvider(
        label: String,
        profile: AppProfile = AppProfile.personal(),
        appLabel: String = label,
        packageName: String = "com.example.${label.lowercase()}",
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(packageName),
                    className = WidgetProviderClassName(".$label"),
                    profile = profile,
                ),
            label = label,
            appLabel = appLabel,
            dimensions = WidgetProviderDimensions(minWidthDp = 120, minHeightDp = 80),
        )
}
