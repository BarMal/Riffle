package com.riffle.app.launcher.onboarding

import com.riffle.app.launcher.onboarding.WalkthroughFixtures.app
import com.riffle.app.launcher.workspace.SourceAccessRoute
import com.riffle.app.launcher.workspace.sourceAccessRouteFor
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.FeedOption
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingOrigin
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughFlow
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughContextsTest {
    @Test
    fun pickersListVisibleEnabledAppsOnceByLabel() {
        val mail = app("mail")
        val installed =
            listOf(
                InstalledApp(app("zed").identity, "Zed"),
                InstalledApp(mail.identity, "mail"),
                InstalledApp(mail.identity, "mail"),
                InstalledApp(app("hidden").identity, "Hidden", visibility = AppVisibility.HIDDEN),
                InstalledApp(app("off").identity, "Off", enabled = false),
            )
        assertEquals(listOf("mail", "Zed"), WalkthroughContexts.pickableApps(installed).map { it.label })
    }

    @Test
    fun theDockSuggestsFavouritesAndAFreshDock() {
        val context = WalkthroughFixtures.context()
        assertEquals(listOf("Phone"), context.suggestedApps.map { it.label })
        assertEquals(listOf("Phone"), context.suggestedDock.map { it.label })
        assertTrue(ContentGoalId.FAVOURITE_APPS in WalkthroughFlow.start(context).chosen)
    }

    @Test
    fun withoutADockTheFirstAppsAreSuggested() {
        val context =
            WalkthroughContexts.build(
                WalkthroughInputs(
                    origin = OnboardingOrigin.FRESH_INSTALL,
                    firstRun = true,
                    sources = WalkthroughFixtures.sources(),
                    workspaces = null,
                    layoutClass = WalkthroughFixtures.PHONE,
                    pool = PlacedItemPool(),
                    installedApps = WalkthroughFixtures.installed("e", "d", "c", "b", "a"),
                ),
            )
        assertEquals(listOf("A", "B", "C", "D"), context.suggestedApps.map { it.label })
        assertTrue(context.suggestedDock.isEmpty())
    }

    @Test
    fun aDockAppThatIsGoneIsNotSuggested() {
        val pool = WalkthroughFixtures.freshPool()
        assertEquals(emptyList<String>(), WalkthroughContexts.dockApps(pool, listOf(app("mail"))).map { it.label })
        assertEquals(listOf("Phone"), WalkthroughContexts.dockApps(pool).map { it.label })
    }

    @Test
    fun aReRunNeverReplacesAndTheOriginIsKept() {
        val context = WalkthroughFixtures.context(OnboardingOrigin.FRESH_INSTALL, firstRun = false)
        assertFalse(context.replacesDefault)
        assertTrue(WalkthroughFixtures.context().replacesDefault)
        assertFalse(WalkthroughFixtures.context(OnboardingOrigin.EXISTING_USER).replacesDefault)
    }

    @Test
    fun permissionsShowOnlyWhenSomethingNeedsAccess() {
        val granted = WalkthroughFixtures.context()
        assertFalse(WalkthroughStep.PERMISSIONS in WalkthroughFlow.steps(WalkthroughFlow.start(granted), granted))
        val asking = WalkthroughFixtures.context(access = WalkthroughFixtures.NOTHING_GRANTED)
        assertTrue(WalkthroughStep.PERMISSIONS in WalkthroughFlow.steps(WalkthroughFlow.start(asking), asking))
    }

    @Test
    fun eachPermissionOpensTheSameExplicitFlowAsSettingsSources() {
        val routes = SourcePermission.entries.associateWith { sourceAccessRouteFor(it.flowSource()) }
        assertEquals(
            mapOf(
                SourcePermission.NOTIFICATION_ACCESS to SourceAccessRoute.NOTIFICATION_ACCESS,
                SourcePermission.CALENDAR to SourceAccessRoute.CALENDAR,
                SourcePermission.USAGE_ACCESS to SourceAccessRoute.USAGE_ACCESS,
            ),
            routes,
        )
        SourcePermission.entries.forEach { assertEquals(it, SourcePermission.forSource(it.flowSource())) }
    }

    @Test
    fun feedsAreNamedAndOnlyEnabledOnesOffered() {
        val url = FeedUrl.parse("https://www.example.com/feed.xml").getOrThrow()
        val rss =
            listOf(
                FeedConfiguration(FeedId("news"), url),
                FeedConfiguration(FeedId("off"), url, enabled = false),
            )
        val ics =
            listOf(
                IcsFeed(IcsFeedId("work"), "Work", url),
                IcsFeed(IcsFeedId("old"), "Old", url, enabled = false),
            )
        assertEquals(
            listOf(FeedOption(SourceIds.RSS, "news", "example.com"), FeedOption(SourceIds.ICS, "work", "Work")),
            WalkthroughContexts.feeds(rss, ics),
        )
    }
}
