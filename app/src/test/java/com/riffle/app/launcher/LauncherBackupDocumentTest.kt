package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONObject
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
                exportedAtEpochMillis = 123_456L,
            )

        val decodedDocument = decodeLauncherBackupDocument(encodeLauncherBackupDocument(document))

        assertEquals(layoutSet, decodedDocument.homeLayoutSet)
        assertEquals(settings, decodedDocument.launcherSettings)
        assertEquals(123_456L, decodedDocument.exportedAtEpochMillis)
    }

    @Test
    fun decodesBackupWithoutExportTimestamp() {
        val value =
            JSONObject(
                encodeLauncherBackupDocument(
                    LauncherBackupDocument(
                        homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                        launcherSettings = LauncherSettings(),
                    ),
                ),
            )
                .apply { remove("exportedAtEpochMillis") }
                .toString()

        val decodedDocument = decodeLauncherBackupDocument(value)

        assertEquals(null, decodedDocument.exportedAtEpochMillis)
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

    @Test
    fun rejectsMalformedBackupJson() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeLauncherBackupDocument("not json")
        }
    }

    @Test
    fun rejectsBackupWithoutHomeLayouts() {
        val value =
            """
            {
              "type": "riffleLauncherBackup",
              "version": 1,
              "settings": {}
            }
            """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            decodeLauncherBackupDocument(value)
        }
    }

    @Test
    fun rejectsBackupWithoutSettings() {
        val value =
            """
            {
              "type": "riffleLauncherBackup",
              "version": 1,
              "homeLayouts": {}
            }
            """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            decodeLauncherBackupDocument(value)
        }
    }
}
