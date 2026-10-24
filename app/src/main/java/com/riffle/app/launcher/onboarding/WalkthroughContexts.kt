package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.workspace.DockMenuReachability
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.FeedOption
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingOrigin
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughContext
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass
import java.net.URI

/** Everything a [WalkthroughContext] is built from, read from the shell and the stores at one moment. */
internal data class WalkthroughInputs(
    val origin: OnboardingOrigin,
    val firstRun: Boolean,
    /** `WorkspaceRuntime.sourceChoices()`: every source with its current access, read without prompting. */
    val sources: List<SourceChoice>,
    val workspaces: WorkspaceSet?,
    val layoutClass: HomeLayoutDeviceClass,
    val pool: PlacedItemPool?,
    val installedApps: List<InstalledApp> = emptyList(),
    val rssFeeds: List<FeedConfiguration> = emptyList(),
    val icsFeeds: List<IcsFeed> = emptyList(),
    val gestures: HomeGestureSettings = HomeGestureSettings(),
)

/** Builds the walkthrough's context, and the app lists its pickers offer. Pure. */
internal object WalkthroughContexts {
    /** How many apps are suggested for Favourite apps when the dock has none to offer. */
    const val SUGGESTED_APP_COUNT = 4

    fun build(inputs: WalkthroughInputs): WalkthroughContext {
        val layoutClass = inputs.layoutClass.workspaceLayoutClass()
        val pool = inputs.pool ?: PlacedItemPool()
        val apps = pickableApps(inputs.installedApps)
        val dock = dockApps(pool, apps)
        return WalkthroughContext(
            origin = inputs.origin,
            sources = inputs.sources,
            layout = (inputs.workspaces ?: WorkspaceSet()).workspacesFor(layoutClass),
            pool = pool,
            suggestedApps = dock.ifEmpty { apps.take(SUGGESTED_APP_COUNT) },
            suggestedDock = dock,
            feeds = feeds(inputs.rssFeeds, inputs.icsFeeds),
            globalGestureActions = DockMenuReachability.globalActions(inputs.gestures),
            capabilities = LayoutCapabilities(),
            firstRun = inputs.firstRun,
        )
    }

    /** The apps the pickers list: enabled, shown in the drawer, one entry per app, by label. */
    fun pickableApps(installed: List<InstalledApp>): List<AppPick> =
        installed
            .filter { it.enabled && it.visibility == AppVisibility.VISIBLE }
            .distinctBy { it.identity }
            .sortedBy { it.label.lowercase() }
            .map { AppPick(it.identity, it.label) }

    /**
     * The apps the layout's dock pins now (not shortcuts), in dock order, keeping only apps still installed when the
     * app list is known.
     */
    fun dockApps(
        pool: PlacedItemPool,
        installed: List<AppPick> = emptyList(),
    ): List<AppPick> {
        val known = installed.map { it.identity }.toSet()
        return pool.dock?.pins.orEmpty()
            .mapNotNull { (pool.items[it] as? PoolApp)?.takeIf { app -> app.appShortcutId == null } }
            .filter { known.isEmpty() || it.appIdentity in known }
            .map { AppPick(it.appIdentity, it.label) }
            .distinctBy { it.identity }
    }

    /** The feeds the FEEDS picker offers: RSS feeds (named by their host) and enabled ICS calendars. */
    fun feeds(
        rss: List<FeedConfiguration>,
        ics: List<IcsFeed>,
    ): List<FeedOption> =
        rss.filter { it.enabled }.map { FeedOption(SourceIds.RSS, it.id.value, hostOf(it.url.value)) } +
            ics.filter { it.enabled }.map { FeedOption(SourceIds.ICS, it.id.value, it.name.ifBlank { it.host }) }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url
}

/**
 * The source whose existing explicit flow grants this permission (`SourceAccessLaunchers.request`): the calendar
 * dialog, the notification listener settings, the Usage access settings. The same map as `sourceAccessRouteFor`.
 */
internal fun SourcePermission.flowSource(): SourceId =
    when (this) {
        SourcePermission.NOTIFICATION_ACCESS -> SourceIds.NOTIFICATIONS
        SourcePermission.CALENDAR -> SourceIds.CALENDAR
        SourcePermission.USAGE_ACCESS -> SourceIds.RECENT_APPS
    }
