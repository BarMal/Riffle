package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutMalformedPreferredModeJsonCodecTest {
    @Test
    fun ignoresMalformedPreferredModeEntries() {
        val decodedLayoutSet =
            decodeHomeLayoutSet(
                """
                {
                  "type": "homeLayoutSet",
                  "active": {
                    "viewMode": "STANDARD_APP_DRAWER",
                    "deviceClass": "PHONE"
                  },
                  "preferredModes": [
                    {
                      "deviceClass": "PHONE",
                      "viewMode": "CARD_INTERFACE"
                    },
                    {
                      "deviceClass": "TABLET"
                    },
                    1
                  ],
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
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(
            mapOf(HomeLayoutDeviceClass.PHONE to LauncherViewMode.CARD_INTERFACE),
            decodedLayoutSet.preferredModesByDeviceClass,
        )
    }
}
