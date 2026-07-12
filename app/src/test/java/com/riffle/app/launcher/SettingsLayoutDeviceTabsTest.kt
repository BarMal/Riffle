package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLayoutDeviceTabsTest {
    @Test
    fun labelsFoldableDeviceTabsAsFoldedAndUnfolded() {
        assertEquals(
            listOf("Folded", "Unfolded"),
            settingsLayoutDeviceTabs(
                setOf(
                    HomeLayoutDeviceClass.PHONE,
                    HomeLayoutDeviceClass.FOLDABLE,
                ),
            ).map { tab -> tab.label },
        )
    }

    @Test
    fun alwaysOffersFoldedAndUnfoldedConfigurationsForPhoneClass() {
        assertEquals(
            listOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            settingsLayoutDeviceTabs(setOf(HomeLayoutDeviceClass.PHONE)).map { tab -> tab.deviceClass },
        )
    }

    @Test
    fun keepsTabletOnlyConfigurationAvailableWhenNoFoldableClassExists() {
        assertEquals(
            listOf("Tablet"),
            settingsLayoutDeviceTabs(setOf(HomeLayoutDeviceClass.TABLET)).map { tab -> tab.label },
        )
    }

    @Test
    fun keepsDesktopOnlyConfigurationAvailable() {
        assertEquals(
            listOf("Desktop"),
            settingsLayoutDeviceTabs(setOf(HomeLayoutDeviceClass.DESKTOP)).map { tab -> tab.label },
        )
    }

    @Test
    fun includesTabletAfterFoldedAndUnfoldedForMixedAdaptiveClasses() {
        assertEquals(
            listOf("Folded", "Unfolded", "Tablet", "Desktop"),
            settingsLayoutDeviceTabs(
                setOf(
                    HomeLayoutDeviceClass.TABLET,
                    HomeLayoutDeviceClass.FOLDABLE,
                    HomeLayoutDeviceClass.PHONE,
                    HomeLayoutDeviceClass.DESKTOP,
                ),
            ).map { tab -> tab.label },
        )
    }
}
