package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreLauncherSettingsRepositoryTest {
    @Test
    fun decodeStoredLauncherSettingsReturnsNullWhenMissing() {
        assertNull(decodeStoredLauncherSettings(null))
    }

    @Test
    fun decodeStoredLauncherSettingsDecodesValidSettings() {
        val settings = LauncherSettings()
        val decodedSettings = decodeStoredLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings, decodedSettings)
    }

    @Test
    fun decodeStoredLauncherSettingsReturnsNullForCorruptPayload() {
        val decodedSettings =
            decodeStoredLauncherSettings(
                """{"appearance":""",
            )

        assertNull(decodedSettings)
    }

    @Test
    fun decodeStoredLauncherSettingsPreservesBackwardCompatiblePayloads() {
        val decodedSettings =
            decodeStoredLauncherSettings(
                """
                {
                  "appearance": {
                    "wallpaper": {
                      "source": "SOLID_COLOR"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(WallpaperSource.SOLID_COLOR, decodedSettings?.appearance?.wallpaper?.source)
    }
}
