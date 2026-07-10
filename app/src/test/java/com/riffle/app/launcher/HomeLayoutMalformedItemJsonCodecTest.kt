package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMalformedItemJsonCodecTest {
    @Test
    fun ignoresMalformedPageItems() {
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
                          "id": "app:camera:1",
                          "label": "Camera",
                          "packageName": "com.android.camera",
                          "activityName": ".CameraActivity",
                          "column": 1,
                          "row": 2,
                          "columns": 1,
                          "rows": 1
                        },
                        {
                          "type": "widget",
                          "id": "widget:broken",
                          "label": "Broken widget"
                        },
                        "not-an-object"
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

        assertEquals(1, decodedLayout.selectedPage.items.size)
        assertEquals("Camera", (decodedLayout.selectedPage.items.single() as AppShortcutItem).label)
    }

    @Test
    fun ignoresMalformedFolderShortcuts() {
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
                          "label": "Tools",
                          "items": [
                            {
                              "type": "shortcut",
                              "id": "app:camera:1",
                              "label": "Camera",
                              "packageName": "com.android.camera",
                              "activityName": ".CameraActivity"
                            },
                            {
                              "type": "shortcut",
                              "id": "app:broken:2",
                              "label": "Broken"
                            }
                          ]
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

        assertEquals(1, folder.items.size)
        assertEquals("Camera", folder.items.single().label)
    }
}
