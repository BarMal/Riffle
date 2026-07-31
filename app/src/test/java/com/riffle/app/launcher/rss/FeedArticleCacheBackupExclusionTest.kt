package com.riffle.app.launcher.rss

import com.riffle.app.launcher.LauncherBackupDocument
import com.riffle.app.launcher.LauncherBackupImportCoordinator
import com.riffle.app.launcher.LauncherBackupImportOutcome
import com.riffle.app.launcher.LauncherBackupImportResult
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.encodeLauncherBackupDocument
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.RssSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Confirms the offline RSS article cache (issue #1010) is device-local: it is never part of the
 * in-app JSON launcher backup, and importing a backup never touches the RSS cache or performs a
 * network refresh -- restore must never restore cache content or trigger a fetch automatically.
 *
 * Feed *configuration* (issue #1013) legitimately lives inside `settings.rss` and is included in
 * backups by design -- only the separate, device-local article cache and its content must never
 * appear.
 */
class FeedArticleCacheBackupExclusionTest {
    @Test
    fun launcherBackupDocumentJsonNeverIncludesTheRssArticleCacheOrItsContent() {
        val feedUrl = FeedUrl.parse("https://example.com/feed.xml").getOrThrow()
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings =
                    LauncherSettings(
                        rss =
                            RssSettings(
                                feeds = listOf(FeedConfiguration(id = FeedId("feed-1"), url = feedUrl)),
                            ),
                    ),
            )

        val json = encodeLauncherBackupDocument(document)

        assertFalse(json.contains(RSS_ARTICLE_CACHE_DATASTORE_NAME))
        assertFalse(json.contains("cachedArticle", ignoreCase = true))
        assertFalse(json.contains("articleCache", ignoreCase = true))
        assertFalse(json.contains("responseHeader", ignoreCase = true))
        assertTrue(JSONObject(json).getJSONObject("settings").has("rss"))
        assertEquals(
            setOf("type", "version", "homeLayouts", "settings", "hiddenApps"),
            JSONObject(json).keySet(),
        )
    }

    @Test
    fun importingABackupNeverTouchesTheRssArticleCacheOrTriggersARefresh() {
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings = LauncherSettings(),
            )
        // LauncherBackupImportCoordinator takes no dependencies at all -- structurally it cannot
        // reach a FeedArticleCacheRepository or a FeedTransport, so handling an import result can
        // only ever translate the document into an action; it cannot cache or fetch anything.
        assertEquals(0, LauncherBackupImportCoordinator::class.java.declaredConstructors.single().parameterCount)
        val coordinator = LauncherBackupImportCoordinator()

        val outcome = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))

        assertEquals(
            LauncherBackupImportOutcome.Imported(LauncherShellAction.ImportLauncherBackup(document)),
            outcome,
        )
    }
}
