package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMissingShortcutLabelJsonCodecTest {
    @Test
    fun defaultsMissingShortcutLabelToEmptyString() {
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
                          "type": "shortcut",
                          "id": "shortcut:mail",
                          "packageName": "com.example.mail",
                          "activityName": "com.example.mail.MainActivity",
                          "column": 1,
                          "row": 2,
                          "columns": 1,
                          "rows": 1
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

        val shortcut = decodedLayout.selectedPage.items.single() as AppShortcutItem

        assertEquals("shortcut:mail", shortcut.id.value)
        assertEquals("", shortcut.label)
    }
}
