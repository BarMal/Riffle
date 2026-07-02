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
    fun doesNotIncludeTabletTabUnlessTabletClassIsAvailable() {
        assertEquals(
            listOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            settingsLayoutDeviceTabs(
                setOf(
                    HomeLayoutDeviceClass.PHONE,
                    HomeLayoutDeviceClass.FOLDABLE,
                ),
            ).map { tab -> tab.deviceClass },
        )
    }

    @Test
    fun labelsSinglePhoneClassAsPhone() {
        assertEquals(
            listOf("Phone"),
            settingsLayoutDeviceTabs(setOf(HomeLayoutDeviceClass.PHONE)).map { tab -> tab.label },
        )
    }
}
