package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutGridJsonCodecTest {
    @Test
    fun decodesPageGridsFromLayoutGridSettings() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "settings": {
                    "grid": {
                      "columns": 5,
                      "rows": 6,
                      "compactLibraryPages": true
                    }
                  },
                  "pages": [
                    {
                      "id": "home",
                      "columns": 4,
                      "rows": 5,
                      "items": []
                    },
                    {
                      "id": "home-2",
                      "columns": 4,
                      "rows": 5,
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

        assertEquals(GridDimensions(columns = 5, rows = 6), decodedLayout.settings.grid.dimensions)
        assertEquals(
            listOf(GridDimensions(columns = 5, rows = 6)),
            decodedLayout.pages.map { page -> page.grid }.distinct(),
        )
    }

    @Test
    fun decodesLayoutSetEntryPageGridsFromDeviceClassDefaults() {
        val decodedLayoutSet =
            decodeHomeLayoutSet(
                """
                {
                  "type": "homeLayoutSet",
                  "active": {
                    "viewMode": "STANDARD_APP_DRAWER",
                    "deviceClass": "FOLDABLE"
                  },
                  "layouts": [
                    {
                      "key": {
                        "viewMode": "STANDARD_APP_DRAWER",
                        "deviceClass": "FOLDABLE"
                      },
                      "layout": {
                        "viewMode": "STANDARD_APP_DRAWER",
                        "selectedPageId": "home",
                        "pages": [
                          {
                            "id": "home",
                            "columns": 4,
                            "rows": 5,
                            "items": []
                          }
                        ],
                        "dock": {
                          "capacity": 5,
                          "items": []
                        }
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(GridDimensions(columns = 5, rows = 6), decodedLayoutSet.activeLayout.settings.grid.dimensions)
        assertEquals(
            listOf(GridDimensions(columns = 5, rows = 6)),
            decodedLayoutSet.activeLayout.pages.map { page -> page.grid }.distinct(),
        )
    }
}
