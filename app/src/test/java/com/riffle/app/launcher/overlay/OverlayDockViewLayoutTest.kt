package com.riffle.app.launcher.overlay

import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDockViewLayoutTest {
    @Test
    fun keepsUsageAccessActionTouchTargetAtLeastFortyEightDp() {
        assertEquals(48, USAGE_ACCESS_ACTION_MIN_TOUCH_TARGET_DP)
    }

    @Test
    fun keepsCollapsedHandleTouchTargetAtLeastFortyEightDp() {
        assertEquals(
            48,
            OverlayDockSettings(handleThicknessDp = 18).collapsedHandleTouchTargetWidthDp(),
        )
    }

    @Test
    fun letsCollapsedHandleTouchTargetGrowWithVeryThickHandle() {
        assertEquals(
            72,
            OverlayDockSettings(handleThicknessDp = 72).collapsedHandleTouchTargetWidthDp(),
        )
    }

    @Test
    fun capsTallExpandedDockHeightToAvailableHeightFraction() {
        assertEquals(
            700,
            tallExpandedDockHeightDp(
                shortcutCount = 12,
                settings = OverlayDockSettings(),
                availableHeightDp = 1000,
            ),
        )
    }

    @Test
    fun treatsEmptyTallExpandedDockAsOneItemPlusCloseButton() {
        assertEquals(
            102,
            tallExpandedDockHeightDp(
                shortcutCount = 0,
                settings = OverlayDockSettings(),
                availableHeightDp = 1000,
            ),
        )
    }

    @Test
    fun keepsTallExpandedDockHeightWithinTinyAvailableHeight() {
        assertEquals(
            21,
            tallExpandedDockHeightDp(
                shortcutCount = 1,
                settings = OverlayDockSettings(),
                availableHeightDp = 30,
            ),
        )
    }

    @Test
    fun returnsZeroTallExpandedDockHeightForDegenerateAvailableHeight() {
        assertEquals(
            0,
            tallExpandedDockHeightDp(
                shortcutCount = 1,
                settings = OverlayDockSettings(),
                availableHeightDp = 0,
            ),
        )
        assertEquals(
            0,
            tallExpandedDockHeightDp(
                shortcutCount = 1,
                settings = OverlayDockSettings(),
                availableHeightDp = -100,
            ),
        )
    }

    @Test
    fun keepsTallExpandedDockHeightNonNegativeForRawInvalidIconSize() {
        assertEquals(
            50,
            tallExpandedDockHeightDp(
                shortcutCount = 1,
                settings = OverlayDockSettings(expandedIconSizeDp = -80),
                availableHeightDp = 1000,
            ),
        )
    }
}
