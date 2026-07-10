package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMissingPagesJsonCodecTest {
    @Test
    fun defaultsPagesWhenStoredPagesArrayIsMissing() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "viewMode": "CARD_INTERFACE",
                  "selectedPageId": "broken",
                  "dock": {
                    "capacity": 4,
                    "items": []
                  }
                }
                """.trimIndent(),
            )

        assertEquals(LauncherViewMode.CARD_INTERFACE, decodedLayout.viewMode)
        assertEquals(4, decodedLayout.dock.capacity)
        assertEquals(HomeLayoutDefaults.standard().pages, decodedLayout.pages)
        assertEquals(HomeLayoutDefaults.standard().selectedPageId, decodedLayout.selectedPageId)
    }
}
