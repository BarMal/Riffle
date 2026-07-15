package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutJsonCodecGeneratedOverflowTest {
    @Test
    fun roundTripsGeneratedContentOverflowCountAndDefaultsLegacyPages() {
        val generatedPage =
            LauncherPage(
                id = LauncherPageId("generated:apps"),
                type = LauncherPageType.Generated(GeneratedLauncherPageKind.APP),
                grid = GridDimensions(columns = 4, rows = 5),
                generatedContentOverflowCount = 3,
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(generatedPage),
                selectedPageId = generatedPage.id,
            )

        val roundTrippedPage = decodeHomeLayout(encodeHomeLayout(layout)).selectedPage
        val legacyPage =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "pages": [
                    {
                      "id": "home",
                      "columns": 4,
                      "rows": 5,
                      "items": []
                    }
                  ]
                }
                """.trimIndent(),
            ).selectedPage

        assertEquals(3, roundTrippedPage.generatedContentOverflowCount)
        assertEquals(0, legacyPage.generatedContentOverflowCount)
    }
}
