package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.FolderItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMissingFolderItemsJsonCodecTest {
    @Test
    fun defaultsMissingFolderItemsToEmptyList() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "pages": [
                    {
                      "id": "home",
                      "columns": 4,
                      "rows": 5,
                      "items": [
                        {
                          "type": "folder",
                          "id": "folder:tools",
                          "label": "Tools"
                        }
                      ]
                    }
                  ],
                  "dock": {
                    "capacity": 5,
                    "items": []
                  }
                }
                """.trimIndent(),
            )

        val folder = decodedLayout.selectedPage.items.single() as FolderItem

        assertEquals("Tools", folder.label)
        assertEquals(0, folder.items.size)
    }
}
