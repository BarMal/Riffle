package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.FolderItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMissingFolderLabelJsonCodecTest {
    @Test
    fun defaultsMissingFolderLabelToEmptyString() {
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
                          "items": []
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

        assertEquals("folder:tools", folder.id.value)
        assertEquals("", folder.label)
    }
}
