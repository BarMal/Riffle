package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMissingWidgetLabelJsonCodecTest {
    @Test
    fun defaultsMissingWidgetLabelToEmptyString() {
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
                          "type": "widget",
                          "id": "widget:weather",
                          "appWidgetId": 42,
                          "column": 1,
                          "row": 2,
                          "columns": 2,
                          "rows": 2
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

        val widget = decodedLayout.selectedPage.items.single() as WidgetItem

        assertEquals("widget:weather", widget.id.value)
        assertEquals("", widget.label)
    }
}
