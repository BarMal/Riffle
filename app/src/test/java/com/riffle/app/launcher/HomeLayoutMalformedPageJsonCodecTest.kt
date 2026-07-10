package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMalformedPageJsonCodecTest {
    @Test
    fun ignoresMalformedPagesAndKeepsValidSelection() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "valid",
                  "pages": [
                    {
                      "id": "broken",
                      "columns": 4,
                      "items": []
                    },
                    {
                      "id": "valid",
                      "columns": 4,
                      "rows": 5,
                      "items": []
                    },
                    1
                  ],
                  "dock": {
                    "capacity": 5,
                    "items": []
                  }
                }
                """.trimIndent(),
            )

        assertEquals(listOf("valid"), decodedLayout.pages.map { page -> page.id.value })
        assertEquals("valid", decodedLayout.selectedPageId.value)
    }
}
