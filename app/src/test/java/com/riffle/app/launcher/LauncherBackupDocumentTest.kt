package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
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
                        hideStatusBarOnHome = true,
                        hideNavigationBarOnHome = false,
                    ),
                haptics = HapticSettings(feedbackStrength = HapticFeedbackStrength.STRONG),
                motion = MotionSettings(reducedMotion = true),
            )
        val document =
            LauncherBackupDocument(
                homeLayoutSet = layoutSet,
                launcherSettings = settings,
                hiddenAppIdentities = setOf(cameraIdentity),
                exportedAtEpochMillis = 123_456L,
            )

        val decodedDocument = decodeLauncherBackupDocument(encodeLauncherBackupDocument(document))

        assertEquals(layoutSet, decodedDocument.homeLayoutSet)
        assertEquals(settings, decodedDocument.launcherSettings)
        assertEquals(setOf(cameraIdentity), decodedDocument.hiddenAppIdentities)
        assertEquals(123_456L, decodedDocument.exportedAtEpochMillis)
    }

    @Test
    fun encodesHomeSystemBarAndMotionSettings() {
        val homeSystemBars =
            HomeSystemBars(
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = false,
            )
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings =
                    LauncherSettings(
                        appearance = AppearanceSettings().withHomeSystemBars(homeSystemBars),
                        motion = MotionSettings(reducedMotion = true),
                    ),
            )

        val settings = JSONObject(encodeLauncherBackupDocument(document)).getJSONObject("settings")
        val appearance = settings.getJSONObject("appearance")
        val motion = settings.getJSONObject("motion")

        assertEquals(false, appearance.getBoolean("fullscreenHome"))
        assertEquals(true, appearance.getBoolean("hideStatusBarOnHome"))
        assertEquals(false, appearance.getBoolean("hideNavigationBarOnHome"))
        assertEquals(true, motion.getBoolean("reducedMotion"))
        assertEquals(homeSystemBars, document.launcherSettings.appearance.homeSystemBars)
    }

    @Test
    fun decodesLegacyFullscreenHomeBackupIntoIndependentSystemBarSettings() {
        val value =
            JSONObject(
                encodeLauncherBackupDocument(
                    LauncherBackupDocument(
                        homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                        launcherSettings =
                            LauncherSettings(
                                appearance =
                                    AppearanceSettings(
                                        fullscreenHome = true,
                                        hideStatusBarOnHome = true,
                                        hideNavigationBarOnHome = true,
                                    ),
                            ),
                    ),
                ),
            )
                .also { document ->
                    document
                        .getJSONObject("settings")
                        .getJSONObject("appearance")
                        .apply {
                            remove("hideStatusBarOnHome")
                            remove("hideNavigationBarOnHome")
                        }
                }
                .toString()

        val decodedSettings = decodeLauncherBackupDocument(value).launcherSettings

        assertEquals(
            HomeSystemBars(
                fullscreenHome = true,
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = true,
            ),
            decodedSettings.appearance.homeSystemBars,
        )
    }

    @Test
    fun decodesBackupWithoutOptionalMetadata() {
        val value =
            backupDocumentJson()
                .apply { remove("exportedAtEpochMillis") }
                .apply { remove("hiddenApps") }
                .toString()

        val decodedDocument = decodeLauncherBackupDocument(value)

        assertEquals(null, decodedDocument.exportedAtEpochMillis)
        assertEquals(emptySet<AppIdentity>(), decodedDocument.hiddenAppIdentities)
    }

    @Test
    fun decodesBackupWithMissingExportedAtEpochMillis() {
        val value =
            backupDocumentJson()
                .apply { remove("exportedAtEpochMillis") }
                .toString()

        val decodedDocument = decodeLauncherBackupDocument(value)

        assertEquals(null, decodedDocument.exportedAtEpochMillis)
    }

    @Test
    fun decodesBackupWithNullExportedAtEpochMillis() {
        val value =
            backupDocumentJson()
                .put("exportedAtEpochMillis", JSONObject.NULL)
                .toString()

        val decodedDocument = decodeLauncherBackupDocument(value)

        assertEquals(null, decodedDocument.exportedAtEpochMillis)
    }

    @Test
    fun decodesBackupWithNumericExportedAtEpochMillis() {
        val value =
            backupDocumentJson()
                .put("exportedAtEpochMillis", 987_654L)
                .toString()

        val decodedDocument = decodeLauncherBackupDocument(value)

        assertEquals(987_654L, decodedDocument.exportedAtEpochMillis)
    }

    @Test
    fun rejectsBackupWithStringExportedAtEpochMillis() {
        val value =
            backupDocumentJson()
                .put("exportedAtEpochMillis", "987654")
                .toString()

        assertThrows(IllegalArgumentException::class.java) {
            decodeLauncherBackupDocument(value)
        }
    }

    @Test
    fun rejectsBackupWithObjectExportedAtEpochMillis() {
        val value =
            backupDocumentJson()
                .put("exportedAtEpochMillis", JSONObject().put("value", 987_654L))
                .toString()

        assertThrows(IllegalArgumentException::class.java) {
            decodeLauncherBackupDocument(value)
        }
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

    private companion object {
        val cameraIdentity =
            AppIdentity(
                packageName = AppPackageName("com.riffle.camera"),
                activityName = AppActivityName(".MainActivity"),
                profile = AppProfile.personal(),
            )

        fun backupDocumentJson(): JSONObject =
            JSONObject(
                encodeLauncherBackupDocument(
                    LauncherBackupDocument(
                        homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                        launcherSettings = LauncherSettings(),
                    ),
                ),
            )
    }
}
