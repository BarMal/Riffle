package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.DiagnosticsSettings
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.RssSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetSettingsReducerTest {
    private val writes = mutableListOf<String>()
    private val saved = mutableListOf<LauncherSettings>()

    private val reducer =
        LauncherSettingsStateReducer(
            homeLayoutRepository =
                object : HomeLayoutRepository {
                    override fun loadHomeLayout(): HomeLayout? = null

                    override fun saveHomeLayout(layout: HomeLayout) {
                        writes += "home"
                    }
                },
            launcherSettingsRepository =
                object : LauncherSettingsRepository {
                    override fun loadLauncherSettings(): LauncherSettings? = null

                    override fun saveLauncherSettings(settings: LauncherSettings) {
                        saved += settings
                    }
                },
            appVisibilityRepository =
                object : AppVisibilityRepository {
                    override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

                    override fun hideApp(identity: AppIdentity) {
                        writes += "hide"
                    }

                    override fun showApp(identity: AppIdentity) {
                        writes += "show"
                    }
                },
        )

    private val feed = FeedConfiguration(FeedId("f"), FeedUrl.parse("https://example.com/feed.xml").getOrThrow())
    private val changed =
        LauncherSettings(
            features = FeatureSettings(simpleMode = true),
            diagnostics = DiagnosticsSettings(developerOptionsRevealed = true, developerOptions = true),
            rss = RssSettings(feeds = listOf(feed)),
        )

    @Test
    fun resetKeepsTheFeedsAndTouchesNeitherHomeNorHiddenApps() {
        val state = LauncherShellState(launcherSettings = changed)
        val reset = reducer.reduce(state, LauncherShellAction.ResetSettings)
        assertEquals(LauncherSettings(rss = RssSettings(feeds = listOf(feed))), reset.launcherSettings)
        assertEquals(reset.launcherSettings, saved.last())
        assertTrue("Home and hidden apps were left alone: $writes", writes.isEmpty())
        assertEquals(state.homeLayout, reset.homeLayout)
        assertEquals(state.homeLayoutSet, reset.homeLayoutSet)
    }

    @Test
    fun undoPutsThePreviousSettingsBackExactly() {
        val state = LauncherShellState(launcherSettings = changed)
        val reset = reducer.reduce(state, LauncherShellAction.ResetSettings)
        val undone = reducer.reduce(reset, LauncherShellAction.RestoreSettings(changed))
        assertEquals(changed, undone.launcherSettings)
        assertEquals(changed, saved.last())
    }
}
