package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLabelSettingTest {
    @Test
    fun fixedWidthDescriptionUsesConfiguredWidth() {
        assertEquals(
            "Fixed 112 dp",
            homeLabelWidthDescription(
                HomeLabelSettings(
                    maxWidthDp = 112,
                    sizing = HomeLabelSizing.FIXED,
                ),
            ),
        )
    }

    @Test
    fun dynamicWidthDescriptionUsesConfiguredMaximumWidth() {
        assertEquals(
            "Up to 112 dp",
            homeLabelWidthDescription(
                HomeLabelSettings(
                    maxWidthDp = 112,
                    sizing = HomeLabelSizing.DYNAMIC,
                ),
            ),
        )
    }
}
