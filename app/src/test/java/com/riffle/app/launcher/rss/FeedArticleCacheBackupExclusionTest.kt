package com.riffle.app.launcher.rss

import com.riffle.app.launcher.LauncherBackupDocument
import com.riffle.app.launcher.LauncherBackupImportCoordinator
import com.riffle.app.launcher.LauncherBackupImportOutcome
import com.riffle.app.launcher.LauncherBackupImportResult
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.encodeLauncherBackupDocument
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Confirms the offline RSS article cache (issue #1010) is device-local: it is never part of the
 * in-app JSON launcher backup, and importing a backup never touches the RSS cache or performs a
 * network refresh -- restore must never restore cache content or trigger a fetch automatically.
 */
class FeedArticleCacheBackupExclusionTest {
    @Test
    fun launcherBackupDocumentJsonNeverMentionsTheRssArticleCache() {
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings = LauncherSettings(),
            )

        val json = encodeLauncherBackupDocument(document)

        assertFalse(json.contains("rss", ignoreCase = true))
        assertFalse(json.contains(RSS_ARTICLE_CACHE_DATASTORE_NAME))
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
