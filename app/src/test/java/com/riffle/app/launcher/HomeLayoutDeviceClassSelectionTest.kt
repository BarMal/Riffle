package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLayoutDeviceClassSelectionTest {
    @Test
    fun treatsFoldableHardwareWithoutCurrentFoldingFeatureAsFolded() {
        assertEquals(
            HomeLayoutFoldablePosture.FOLDED,
            homeLayoutFoldablePosture(
                hasFoldableHardware = true,
                hasFoldingFeature = false,
                hasUnfoldedFoldingFeature = false,
            ),
        )
    }

    @Test
    fun treatsFlatOrSeparatingFoldingFeatureAsUnfolded() {
        assertEquals(
            HomeLayoutFoldablePosture.UNFOLDED,
            homeLayoutFoldablePosture(
                hasFoldableHardware = true,
                hasFoldingFeature = true,
                hasUnfoldedFoldingFeature = true,
            ),
        )
    }

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
    fun classifiesUnfoldedWindowAsFoldableEvenWhenConfigurationIsTabletSized() {
        assertEquals(
            HomeLayoutDeviceClass.FOLDABLE,
            homeLayoutDeviceClassFromWindowLayout(
                foldablePosture = HomeLayoutFoldablePosture.UNFOLDED,
                screenWidthDp = 900,
                screenHeightDp = 1200,
            ),
        )
    }

    @Test
    fun classifiesFoldedWindowAsPhoneEvenWhenConfigurationIsLarge() {
        assertEquals(
            HomeLayoutDeviceClass.PHONE,
            homeLayoutDeviceClassFromWindowLayout(
                foldablePosture = HomeLayoutFoldablePosture.FOLDED,
                screenWidthDp = 720,
                screenHeightDp = 840,
            ),
        )
    }

    @Test
    fun exposesFoldedAndUnfoldedClassesForFoldingHardware() {
        assertEquals(
            setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            homeLayoutDeviceClassSelectionFromWindowLayout(
                foldablePosture = HomeLayoutFoldablePosture.FOLDED,
                screenWidthDp = 412,
                screenHeightDp = 915,
            )?.availableDeviceClasses,
        )
    }

    @Test
    fun exposesOnlyTabletClassForTabletHardware() {
        assertEquals(
            setOf(HomeLayoutDeviceClass.TABLET),
            homeLayoutDeviceClassSelectionFromWindowLayout(
                foldablePosture = HomeLayoutFoldablePosture.NONE,
                screenWidthDp = 900,
                screenHeightDp = 1200,
            )?.availableDeviceClasses,
        )
    }

    @Test
    fun classifiesWindowWithoutFoldingFeatureFromConfiguration() {
        assertEquals(
            HomeLayoutDeviceClass.TABLET,
            homeLayoutDeviceClassFromWindowLayout(
                foldablePosture = HomeLayoutFoldablePosture.NONE,
                screenWidthDp = 900,
                screenHeightDp = 1200,
            ),
        )
    }
}
