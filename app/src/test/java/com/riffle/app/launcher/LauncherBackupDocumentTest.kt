package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
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
                motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON),
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
    fun roundTripsValidRssFeedConfigurationInBackup() {
        val feed =
            FeedConfiguration(
                id = FeedId("feed-1"),
                url = FeedUrl.parse("https://example.com/feed.xml").getOrThrow(),
                enabled = true,
            )
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings =
                    LauncherSettings(
                        rss = RssSettings(feeds = listOf(feed), refreshInterval = FeedRefreshIntervalOption.HOURS_3),
                    ),
            )

        val decodedDocument = decodeLauncherBackupDocument(encodeLauncherBackupDocument(document))

        assertEquals(document.launcherSettings.rss, decodedDocument.launcherSettings.rss)
    }

    @Test
    fun dropsInvalidRssFeedEntriesOnBackupRestoreWithoutTriggeringAnyNetworkCall() {
        val validFeed =
            FeedConfiguration(
                id = FeedId("valid"),
                url = FeedUrl.parse("https://example.com/feed.xml").getOrThrow(),
            )
        val validJson =
            JSONObject(
                encodeLauncherBackupDocument(
                    LauncherBackupDocument(
                        homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                        launcherSettings = LauncherSettings(rss = RssSettings(feeds = listOf(validFeed))),
                    ),
                ),
            )
        val rssJson = validJson.getJSONObject("settings").getJSONObject("rss")
        val invalidFeedJson =
            JSONObject()
                .put("id", "invalid")
                .put("url", "http://example.com/insecure.xml")
                .put("profileId", "personal")
                .put("profileType", "PERSONAL")
        rssJson.getJSONArray("feeds").put(invalidFeedJson)

        val decodedDocument = decodeLauncherBackupDocument(validJson.toString())

        // Restore validates each feed URL, drops the invalid entry, and keeps the valid one -- with
        // an empty cache by construction, since the article cache is never part of this document.
        assertEquals(listOf(validFeed), decodedDocument.launcherSettings.rss.feeds)
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
                        motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON),
                    ),
            )

        val settings = JSONObject(encodeLauncherBackupDocument(document)).getJSONObject("settings")
        val appearance = settings.getJSONObject("appearance")
        val motion = settings.getJSONObject("motion")

        assertEquals(false, appearance.has("fullscreenHome"))
        assertEquals(true, appearance.getBoolean("hideStatusBarOnHome"))
        assertEquals(false, appearance.getBoolean("hideNavigationBarOnHome"))
        assertEquals(true, motion.getBoolean("reducedMotion"))
        assertEquals("ON", motion.getString("reducedMotionPreference"))
        assertEquals(homeSystemBars, document.launcherSettings.appearance.homeSystemBars)
    }

    @Test
    fun decodesLegacyFullscreenHomeBackupIntoBothSystemBars() {
        val value =
            backupDocumentJson()
                .also { document ->
                    document
                        .getJSONObject("settings")
                        .put("version", 10)
                        .getJSONObject("appearance")
                        .apply {
                            put("fullscreenHome", true)
                            remove("hideStatusBarOnHome")
                            remove("hideNavigationBarOnHome")
                        }
                }
                .toString()

        val decodedSettings = decodeLauncherBackupDocument(value).launcherSettings

        assertEquals(
            HomeSystemBars(hideStatusBarOnHome = true, hideNavigationBarOnHome = true),
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
    fun ignoresMalformedHiddenAppsPayload() {
        val value =
            backupDocumentJson()
                .put("hiddenApps", JSONObject().put("broken", true))
                .toString()

        val decodedDocument = decodeLauncherBackupDocument(value)

        assertEquals(emptySet<AppIdentity>(), decodedDocument.hiddenAppIdentities)
    }

    @Test
    fun defaultsMalformedSettingsWhilePreservingHomeLayouts() {
        val layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
        val value = backupDocumentJson().put("settings", "broken").toString()

        val decodedDocument = decodeLauncherBackupDocument(value)

        assertEquals(layoutSet, decodedDocument.homeLayoutSet)
        assertEquals(LauncherSettings(), decodedDocument.launcherSettings)
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
    fun ignoresStringExportedAtEpochMillis() {
        val value =
            backupDocumentJson()
                .put("exportedAtEpochMillis", "987654")
                .toString()

        val decodedDocument = decodeLauncherBackupDocument(value)

        assertEquals(null, decodedDocument.exportedAtEpochMillis)
    }

    @Test
    fun ignoresObjectExportedAtEpochMillis() {
        val value =
            backupDocumentJson()
                .put("exportedAtEpochMillis", JSONObject().put("value", 987_654L))
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

    @Test
    fun roundTripsCalendarFeedsAndWhichStreamSectionsAreOn() {
        val feeds =
            IcsFeedSettings(
                listOf(
                    IcsFeed(
                        id = IcsFeedId("feed-1"),
                        name = "Work",
                        url = FeedUrl.parse("https://calendar.example.com/work.ics").getOrThrow(),
                        enabled = false,
                    ),
                ),
            )
        val stream =
            StreamLayouts().with(
                HomeLayoutDeviceClass.PHONE,
                StreamLayoutReducer.reduce(
                    StreamLayout.DEFAULT,
                    StreamLayoutAction.SetEnabled(StreamSection.TASKS, enabled = false),
                ),
            )
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings = LauncherSettings(),
                calendarFeeds = feeds,
                stream = stream,
            )

        val decoded = decodeLauncherBackupDocument(encodeLauncherBackupDocument(document))

        assertEquals(feeds, decoded.calendarFeeds)
        assertEquals(
            false,
            decoded.stream?.forPosture(HomeLayoutDeviceClass.PHONE)?.layoutOf(StreamSection.TASKS)?.enabled,
        )
        assertEquals(
            true,
            decoded.stream?.forPosture(HomeLayoutDeviceClass.PHONE)?.layoutOf(StreamSection.NOW)?.enabled,
        )
    }

    @Test
    fun theBackupHoldsFeedLinksButNeverEventsOrAnyCachedContent() {
        val feeds =
            IcsFeedSettings(
                listOf(
                    IcsFeed(
                        id = IcsFeedId("feed-1"),
                        name = "Work",
                        url = FeedUrl.parse("https://calendar.example.com/work.ics").getOrThrow(),
                    ),
                ),
            )
        val encoded =
            encodeLauncherBackupDocument(
                LauncherBackupDocument(
                    homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                    launcherSettings = LauncherSettings(),
                    calendarFeeds = feeds,
                ),
            )
        val section = JSONObject(encoded).getJSONObject("calendarFeeds")

        assertEquals(setOf("version", "feeds"), section.keys().asSequence().toSet())
        assertEquals(
            setOf("id", "name", "url", "enabled"),
            section.getJSONArray("feeds").getJSONObject(0).keys().asSequence().toSet(),
        )
    }

    @Test
    fun aBackupWithoutCalendarFeedsOrWithAnEmptyListWritesNoSection() {
        val encoded =
            JSONObject(
                encodeLauncherBackupDocument(
                    LauncherBackupDocument(
                        homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                        launcherSettings = LauncherSettings(),
                        calendarFeeds = IcsFeedSettings(),
                    ),
                ),
            )

        assertEquals(false, encoded.has("calendarFeeds"))
    }

    @Test
    fun anOlderBackupWithoutTheSectionStillImportsAndAMalformedSectionReadsAsAbsent() {
        val older = backupDocumentJson().toString()
        val malformed = backupDocumentJson().put("calendarFeeds", "nonsense").toString()
        val invalidLinks =
            backupDocumentJson()
                .put(
                    "calendarFeeds",
                    JSONObject(
                        """
                        {"version":1,"feeds":[{"id":"a","name":"x","url":"http://insecure.example.com/a.ics"}]}
                        """.trimIndent(),
                    ),
                ).toString()

        assertEquals(null, decodeLauncherBackupDocument(older).calendarFeeds)
        assertEquals(null, decodeLauncherBackupDocument(malformed).calendarFeeds)
        assertEquals(null, decodeLauncherBackupDocument(invalidLinks).calendarFeeds)
    }

    @Test
    fun aBackupWrittenByTheP8BuildWithTheRemovedSettingsKeysStillImports() {
        val document =
            backupDocumentJson().also { json ->
                json.put(
                    "settings",
                    JSONObject(
                        """
                        {
                          "version": 10,
                          "appDrawer": { "libraryViews": { "PHONE": "alpha_list" }, "afterLeavingLibrary": "HOME" },
                          "appearance": { "fullscreenHome": true, "themeMode": "LIGHT" },
                          "cards": { "showStageSpine": true, "timeScapeAppearance": { "version": 2 } },
                          "contextual": { "enabled": true }
                        }
                        """.trimIndent(),
                    ),
                )
            }.toString()

        val settings = decodeLauncherBackupDocument(document).launcherSettings

        assertEquals(LauncherThemeMode.LIGHT, settings.appearance.themeMode)
        assertEquals(true, settings.appearance.homeSystemBars.statusBarHidden)
        assertEquals(LibraryView.AlphaList, settings.appDrawer.libraryViewFor(HomeLayoutDeviceClass.PHONE))
    }

    @Test
    fun anUnknownNewerSettingsVersionInsideABackupStillDecodes() {
        val document =
            backupDocumentJson().also { json ->
                json.put(
                    "settings",
                    JSONObject("""{ "version": 99, "appearance": { "themeMode": "DARK" }, "x": [] }"""),
                )
            }.toString()

        assertEquals(
            LauncherThemeMode.DARK,
            decodeLauncherBackupDocument(document).launcherSettings.appearance.themeMode,
        )
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
