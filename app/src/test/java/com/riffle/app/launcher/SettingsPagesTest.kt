package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPagesTest {
    @Test
    fun mainSettingsPageIncludesLayoutEntry() {
        assertEquals(
            listOf(
                SettingsPageEntry(label = "Layout", page = SettingsPage.LAYOUT),
                SettingsPageEntry(label = "Appearance", page = SettingsPage.APPEARANCE),
                SettingsPageEntry(label = "Floating dock", page = SettingsPage.FLOATING_DOCK),
                SettingsPageEntry(label = "Gestures", page = SettingsPage.GESTURES),
                SettingsPageEntry(label = "Haptics", page = SettingsPage.HAPTICS),
                SettingsPageEntry(label = "Permissions", page = SettingsPage.PERMISSIONS),
                SettingsPageEntry(label = "Apps", page = SettingsPage.APPS),
                SettingsPageEntry(label = "Backup", page = SettingsPage.BACKUP),
                SettingsPageEntry(label = "Hidden apps", page = SettingsPage.HIDDEN_APPS),
                SettingsPageEntry(label = "Version", page = SettingsPage.VERSION),
            ),
            settingsMainPageEntries(),
        )
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
    fun layoutPageOmitsTabsForSingleLayoutDevices() {
        assertEquals(
            emptyList<SettingsLayoutDeviceTab>(),
            settingsLayoutPageTabs(setOf(HomeLayoutDeviceClass.PHONE)),
        )
    }
}
