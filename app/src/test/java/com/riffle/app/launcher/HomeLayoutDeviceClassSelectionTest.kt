package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLayoutDeviceClassSelectionTest {
    @Test
    fun foldableDiagnosticsSummarizeSelectionAndSignals() {
        val event =
            HomeLayoutDeviceClassEvent(
                source = "window-info",
                selection =
                    HomeLayoutDeviceClassSelection(
                        activeDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                        availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
                    ),
                windowSize = HomeLayoutWindowSize(screenWidthDp = 720, screenHeightDp = 840),
                hasFoldableHardware = true,
                configurationClass = HomeLayoutDeviceClass.FOLDABLE,
                foldablePosture = HomeLayoutFoldablePosture.UNFOLDED,
                foldingFeatures =
                    listOf(
                        HomeLayoutFoldingFeatureDebug(
                            state = "FLAT",
                            orientation = "VERTICAL",
                            isSeparating = true,
                        ),
                    ),
            )

        assertEquals(
            "source=window-info " +
                "active=FOLDABLE " +
                "available=[PHONE, FOLDABLE] " +
                "window=720x840dp " +
                "posture=UNFOLDED " +
                "configurationClass=FOLDABLE " +
                "hasFoldableHardware=true " +
                "foldingFeatures=[HomeLayoutFoldingFeatureDebug(state=FLAT, orientation=VERTICAL, isSeparating=true)]",
            event.logText,
        )
    }

    @Test
    fun convertsWindowPixelsToDpForLayoutClassification() {
        assertEquals(
            HomeLayoutWindowSize(screenWidthDp = 720, screenHeightDp = 840),
            homeLayoutWindowSizeFromPixels(
                widthPx = 1800,
                heightPx = 2100,
                density = 2.5f,
            ),
        )
    }

    @Test
    fun convertsWindowPixelsWithSafeDensityFallback() {
        assertEquals(
            HomeLayoutWindowSize(screenWidthDp = 412, screenHeightDp = 915),
            homeLayoutWindowSizeFromPixels(
                widthPx = 412,
                heightPx = 915,
                density = 0f,
            ),
        )
    }

    @Test
    fun treatsPhoneSizedFoldableHardwareWithoutCurrentFoldingFeatureAsFolded() {
        assertEquals(
            HomeLayoutFoldablePosture.FOLDED,
            homeLayoutFoldablePosture(
                hasFoldableHardware = true,
                hasFoldingFeature = false,
                hasUnfoldedFoldingFeature = false,
                configurationClass = HomeLayoutDeviceClass.PHONE,
            ),
        )
    }

    @Test
    fun treatsLargeFoldableHardwareWithoutCurrentFoldingFeatureAsUnfolded() {
        assertEquals(
            HomeLayoutFoldablePosture.UNFOLDED,
            homeLayoutFoldablePosture(
                hasFoldableHardware = true,
                hasFoldingFeature = false,
                hasUnfoldedFoldingFeature = false,
                configurationClass = HomeLayoutDeviceClass.FOLDABLE,
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
                configurationClass = HomeLayoutDeviceClass.PHONE,
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
    fun classifiesDesktopConfigurationAndExposesOnlyDesktopClass() {
        val selection =
            homeLayoutDeviceClassSelectionFromWindowLayout(
                foldablePosture = HomeLayoutFoldablePosture.NONE,
                screenWidthDp = 1_200,
                screenHeightDp = 900,
            )

        assertEquals(HomeLayoutDeviceClass.DESKTOP, selection?.activeDeviceClass)
        assertEquals(setOf(HomeLayoutDeviceClass.DESKTOP), selection?.availableDeviceClasses)
    }

    @Test
    fun classifiesWideShortDesktopConfigurationAndExposesOnlyDesktopClass() {
        val selection =
            homeLayoutDeviceClassSelectionFromWindowLayout(
                foldablePosture = HomeLayoutFoldablePosture.NONE,
                screenWidthDp = 1_200,
                screenHeightDp = 500,
            )

        assertEquals(HomeLayoutDeviceClass.DESKTOP, selection?.activeDeviceClass)
        assertEquals(setOf(HomeLayoutDeviceClass.DESKTOP), selection?.availableDeviceClasses)
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
    fun classifiesUnfoldedDesktopSizedWindowAsDesktop() {
        val selection =
            homeLayoutDeviceClassSelectionFromWindowLayout(
                foldablePosture = HomeLayoutFoldablePosture.UNFOLDED,
                screenWidthDp = 1_200,
                screenHeightDp = 900,
            )

        assertEquals(HomeLayoutDeviceClass.DESKTOP, selection?.activeDeviceClass)
        assertEquals(
            setOf(
                HomeLayoutDeviceClass.PHONE,
                HomeLayoutDeviceClass.FOLDABLE,
                HomeLayoutDeviceClass.DESKTOP,
            ),
            selection?.availableDeviceClasses,
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
