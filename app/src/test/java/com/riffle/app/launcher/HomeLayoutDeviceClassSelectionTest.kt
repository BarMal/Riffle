package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLayoutDeviceClassSelectionTest {
    @Test
    fun ignoresUnavailableConfigurationDimensions() {
        assertNull(homeLayoutDeviceClassFromConfiguration(screenWidthDp = 0, screenHeightDp = 900))
        assertNull(homeLayoutDeviceClassFromConfiguration(screenWidthDp = 700, screenHeightDp = 0))
        assertNull(homeLayoutDeviceClassFromConfiguration(screenWidthDp = -1, screenHeightDp = 900))
    }

    @Test
    fun classifiesFoldedConfigurationAsPhone() {
        assertEquals(
            HomeLayoutDeviceClass.PHONE,
            homeLayoutDeviceClassFromConfiguration(screenWidthDp = 412, screenHeightDp = 915),
        )
    }

    @Test
    fun classifiesUnfoldedConfigurationAsFoldable() {
        assertEquals(
            HomeLayoutDeviceClass.FOLDABLE,
            homeLayoutDeviceClassFromConfiguration(screenWidthDp = 720, screenHeightDp = 840),
        )
    }

    @Test
    fun classifiesLargeConfigurationAsTablet() {
        assertEquals(
            HomeLayoutDeviceClass.TABLET,
            homeLayoutDeviceClassFromConfiguration(screenWidthDp = 900, screenHeightDp = 1200),
        )
    }

    @Test
    fun classifiesWindowWithFoldingFeatureAsFoldableEvenWhenConfigurationIsTabletSized() {
        assertEquals(
            HomeLayoutDeviceClass.FOLDABLE,
            homeLayoutDeviceClassFromWindowLayout(
                hasFoldingFeature = true,
                screenWidthDp = 900,
                screenHeightDp = 1200,
            ),
        )
    }

    @Test
    fun classifiesPhoneSizedWindowWithFoldingFeatureAsPhone() {
        assertEquals(
            HomeLayoutDeviceClass.PHONE,
            homeLayoutDeviceClassFromWindowLayout(
                hasFoldingFeature = true,
                screenWidthDp = 412,
                screenHeightDp = 915,
            ),
        )
    }

    @Test
    fun classifiesWindowWithoutFoldingFeatureFromConfiguration() {
        assertEquals(
            HomeLayoutDeviceClass.TABLET,
            homeLayoutDeviceClassFromWindowLayout(
                hasFoldingFeature = false,
                screenWidthDp = 900,
                screenHeightDp = 1200,
            ),
        )
    }
}
