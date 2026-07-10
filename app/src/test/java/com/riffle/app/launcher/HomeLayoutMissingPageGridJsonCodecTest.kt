package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMissingPageGridJsonCodecTest {
    @Test
    fun defaultsMissingPageGridToSettingsDimensions() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "settings": {
                    "grid": {
                      "columns": 5,
                      "rows": 6
                    }
                  },
                  "pages": [
                    {
                      "id": "home",
                      "items": []
                    }
                  ],
                  "dock": {
                    "capacity": 5,
                    "items": []
                  }
                }
                """.trimIndent(),
            )

        assertEquals(listOf("home"), decodedLayout.pages.map { page -> page.id.value })
        assertEquals(GridDimensions(columns = 5, rows = 6), decodedLayout.selectedPage.grid)
    }
}
