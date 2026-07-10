package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutSetMalformedEntryJsonCodecTest {
    @Test
    fun ignoresMalformedLayoutEntries() {
        val decodedLayoutSet =
            decodeHomeLayoutSet(
                """
                {
                  "type": "homeLayoutSet",
                  "active": {
                    "viewMode": "STANDARD_APP_DRAWER",
                    "deviceClass": "PHONE"
                  },
                  "layouts": [
                    {
                      "key": {
                        "viewMode": "STANDARD_APP_DRAWER",
                        "deviceClass": "PHONE"
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
                    },
                    {
                      "key": {
                        "viewMode": "CARD_INTERFACE",
                        "deviceClass": "PHONE"
                      },
                      "layout": {
                        "viewMode": "CARD_INTERFACE",
                        "selectedPageId": "cards"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)),
            decodedLayoutSet.layouts.keys.toList(),
        )
        assertEquals("home", decodedLayoutSet.activeLayout.selectedPageId.value)
    }
}
