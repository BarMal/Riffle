package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMissingPageItemsJsonCodecTest {
    @Test
    fun defaultsMissingPageItemsToEmptyList() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "pages": [
                    {
                      "id": "home",
                      "columns": 4,
                      "rows": 5
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
        assertEquals(0, decodedLayout.selectedPage.items.size)
    }
}
