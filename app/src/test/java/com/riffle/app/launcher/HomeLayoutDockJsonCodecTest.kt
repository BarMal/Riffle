package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutDockJsonCodecTest {
    @Test
    fun roundTripsDockNotificationCardsVisibility() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(showNotificationCards = false),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(false, decodedLayout.dock.showNotificationCards)
    }

    @Test
    fun roundTripsDockShapeAndGridControlsSpacing() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock =
                    HomeLayoutDefaults.standard().dock.copy(
                        cornerRadiusDp = 18,
                        homeControlsSpacingDp = 20,
                    ),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(18, decodedLayout.dock.cornerRadiusDp)
        assertEquals(20, decodedLayout.dock.homeControlsSpacingDp)
    }
}
