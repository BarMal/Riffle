package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic coverage for the dock's template default per device class ([templateDockPosition]) and
 * how it layers under a user's configured edge through [resolveDockPosition]. The rendered dock is
 * covered by the side-dock androidTests; this just pins the policy StandardHome now routes through.
 */
class DockTemplatePositionTest {
    @Test
    fun narrowPosturesDefaultToTheBottomDock() {
        assertEquals(DockPosition.BOTTOM, HomeLayoutDeviceClass.PHONE.templateDockPosition)
        assertEquals(DockPosition.BOTTOM, HomeLayoutDeviceClass.PHONE_LANDSCAPE.templateDockPosition)
    }

    @Test
    fun widePosturesDefaultToTheLeadingRail() {
        assertEquals(DockPosition.LEADING, HomeLayoutDeviceClass.FOLDABLE.templateDockPosition)
        assertEquals(DockPosition.LEADING, HomeLayoutDeviceClass.TABLET.templateDockPosition)
        assertEquals(DockPosition.LEADING, HomeLayoutDeviceClass.DESKTOP.templateDockPosition)
    }

    @Test
    fun everyDeviceClassIsMappedSoTheLeadingFallbackStaysUnreached() {
        HomeLayoutDeviceClass.entries.forEach { deviceClass ->
            assertEquals(
                "resolveDockPosition should defer to the class template default for $deviceClass",
                deviceClass.templateDockPosition,
                resolveDockPosition(
                    configuredDockPosition = null,
                    templateDockPosition = deviceClass.templateDockPosition,
                ),
            )
        }
    }

    @Test
    fun aConfiguredEdgeOverridesTheTabletTemplateRail() {
        assertEquals(
            DockPosition.BOTTOM,
            resolveDockPosition(
                configuredDockPosition = DockPosition.BOTTOM,
                templateDockPosition = HomeLayoutDeviceClass.TABLET.templateDockPosition,
            ),
        )
    }
}
