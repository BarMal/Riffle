package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutJsonCodecPinningTest {
    @Test
    fun clearsLegacyPinnedStateFromStaticPages() {
        val defaults = HomeLayoutDefaults.standard()
        val layout =
            defaults.copy(
                pages =
                    listOf(
                        defaults.selectedPage.copy(isPinned = true),
                        LauncherPage(
                            id = LauncherPageId("all-apps"),
                            type = LauncherPageType.AllApps,
                            grid = GridDimensions(columns = 4, rows = 5),
                            isPinned = true,
                        ),
                    ),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertTrue(decodedLayout.pages.none { page -> page.isPinned })
    }
}
