package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsResetTest {
    private val feed = FeedConfiguration(FeedId("f"), FeedUrl.parse("https://example.com/feed.xml").getOrThrow())
    private val floating =
        AppShortcutItem(
            LauncherItemId("o"),
            AppIdentity(AppPackageName("com.example.notes"), AppActivityName("Main")),
            "Notes",
        )

    @Test
    fun everySettingReturnsToItsDefault() {
        val changed =
            LauncherSettings(
                features = FeatureSettings(simpleMode = true, stream = false, library = false),
                diagnostics = DiagnosticsSettings(developerOptionsRevealed = true, developerOptions = true),
                hints = HintSettings(dockPressSeen = true),
                home = HomeBehaviourSettings(addNewApps = false, compactArrange = false),
                haptics = HapticSettings(feedbackStrength = HapticFeedbackStrength.STRONG),
            )
        assertEquals(LauncherSettings(), SettingsReset.reset(changed))
    }

    @Test
    fun theFeedListAndTheFloatingDocksAppsAreKept() {
        val changed =
            LauncherSettings(
                rss = RssSettings(feeds = listOf(feed), backgroundWifiOnly = false),
                overlayDock = OverlayDockSettings(enabled = true, items = listOf(floating), showLabels = true),
            )
        val reset = SettingsReset.reset(changed)
        assertEquals(listOf(feed), reset.rss.feeds)
        assertEquals(RssSettings().backgroundWifiOnly, reset.rss.backgroundWifiOnly)
        assertEquals(listOf(floating), reset.overlayDock.items)
        assertEquals(false, reset.overlayDock.enabled)
        assertEquals(false, reset.overlayDock.showLabels)
    }

    @Test
    fun developerOptionsAreHiddenAgain() {
        val reset =
            SettingsReset.reset(
                LauncherSettings(
                    diagnostics = DiagnosticsSettings(developerOptionsRevealed = true, developerOptions = true),
                ),
            )
        assertEquals(false, reset.diagnostics.developerOptionsRevealed)
        assertEquals(false, reset.diagnostics.developerOptions)
    }

    @Test
    fun resettingTwiceChangesNothing() {
        val once = SettingsReset.reset(LauncherSettings(rss = RssSettings(feeds = listOf(feed))))
        assertEquals(once, SettingsReset.reset(once))
    }
}
