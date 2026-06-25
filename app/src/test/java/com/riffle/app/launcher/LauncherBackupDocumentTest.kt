package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LauncherBackupDocumentTest {
    @Test
    fun roundTripsHomeLayoutSetAndLauncherSettings() {
        val layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
                haptics = HapticSettings(feedbackStrength = HapticFeedbackStrength.STRONG),
            )
        val document =
            LauncherBackupDocument(
                homeLayoutSet = layoutSet,
                launcherSettings = settings,
            )

        val decodedDocument = decodeLauncherBackupDocument(encodeLauncherBackupDocument(document))

        assertEquals(layoutSet, decodedDocument.homeLayoutSet)
        assertEquals(settings, decodedDocument.launcherSettings)
    }

    @Test
    fun rejectsUnknownBackupType() {
        val value =
            """
            {
              "type": "other",
              "version": 1,
              "homeLayouts": {},
              "settings": {}
            }
            """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            decodeLauncherBackupDocument(value)
        }
    }

    @Test
    fun rejectsUnknownBackupVersion() {
        val value =
            """
            {
              "type": "riffleLauncherBackup",
              "version": 2,
              "homeLayouts": {},
              "settings": {}
            }
            """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            decodeLauncherBackupDocument(value)
        }
    }
}
