package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMissingPlacementSpanJsonCodecTest {
    @Test
    fun defaultsMissingPlacementSpanToSingleCell() {
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
                          "row": 2
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

        assertEquals(LauncherItemId("app:camera:1"), shortcut.id)
        assertEquals(
            GridPlacement(cell = GridCell(column = 1, row = 2)),
            shortcut.placement,
        )
    }
}
