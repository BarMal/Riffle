package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPagesTest {
    @Test
    fun mainSettingsPageUsesLauncherStyleGroups() {
        assertEquals(
            listOf(
                "Layout" to SettingsPage.LAYOUT,
                "Dock" to SettingsPage.DOCK,
                "Appearance" to SettingsPage.APPEARANCE,
                "Floating dock" to SettingsPage.FLOATING_DOCK,
                "Gestures" to SettingsPage.GESTURES,
                "Haptics" to SettingsPage.HAPTICS,
                "App drawer" to SettingsPage.APPS,
                "Hidden apps" to SettingsPage.HIDDEN_APPS,
                "Permissions" to SettingsPage.PERMISSIONS,
                "Backup" to SettingsPage.BACKUP,
                "About" to SettingsPage.VERSION,
            ),
            settingsMainPageEntries().map { entry ->
                entry.label to entry.page
            },
        )

        assertEquals(
            listOf(
                SettingsPageGroup.HOME,
                SettingsPageGroup.INTERACTION,
                SettingsPageGroup.APPS,
                SettingsPageGroup.SYSTEM,
            ),
            settingsMainPageGroups(),
        )
    }

    @Test
    fun filtersMainSettingsEntriesByMultipleTokens() {
        assertEquals(
            listOf(SettingsPage.LAYOUT),
            settingsMainPageEntriesMatching("home grid").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.FLOATING_DOCK),
            settingsMainPageEntriesMatching("overlay shortcuts").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.BACKUP),
            settingsMainPageEntriesMatching("system export").map { entry -> entry.page },
        )
    }

    @Test
    fun returnsNoMainSettingsEntriesForUnknownSearch() {
        assertEquals(emptyList<SettingsPageEntry>(), settingsMainPageEntriesMatching("missing setting"))
    }

    @Test
    fun layoutPageUsesFoldedAndUnfoldedTabsForFoldableDevices() {
        assertEquals(
            listOf("Folded", "Unfolded"),
            settingsLayoutPageTabs(
                availableDeviceClasses =
                    setOf(
                        HomeLayoutDeviceClass.PHONE,
                        HomeLayoutDeviceClass.FOLDABLE,
                    ),
            ).map { tab -> tab.label },
        )
    }

    @Test
    fun layoutPageShowsFoldedAndUnfoldedTabsForSingleLayoutDevices() {
        assertEquals(
            listOf("Folded", "Unfolded"),
            settingsLayoutPageTabs(setOf(HomeLayoutDeviceClass.PHONE)).map { tab -> tab.label },
        )
    }
}
