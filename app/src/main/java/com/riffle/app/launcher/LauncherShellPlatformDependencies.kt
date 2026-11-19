package com.riffle.app.launcher

import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.app.launcher.savedhome.InMemorySavedHomeLayoutsStore
import com.riffle.app.launcher.savedhome.SavedHomeLayoutsStore
import com.riffle.app.launcher.savedhome.SavedHomeWidgetBinder
import com.riffle.core.domain.launcher.home.DockDefaultsResolver
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import com.riffle.core.domain.launcher.widgets.WidgetProviderCatalog
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

data class LauncherShellPlatformDependencies(
    val notificationRepository: LauncherNotificationRepository = LauncherNotificationRepository { emptyList() },
    val widgetProviderRepository: InstalledWidgetProviderRepository = InstalledWidgetProviderRepository { emptyList() },
    val epochMillisProvider: EpochMillisProvider = SystemEpochMillisProvider,
    val loadInitialPlatformState: Boolean = false,
    val initialHomeLayoutDeviceClass: HomeLayoutDeviceClass? = null,
    val viewModeAvailability: LauncherViewModeAvailability = defaultLauncherViewModeAvailability(),
    val deleteHostedWidgetId: (HostedWidgetId) -> Unit = {},
    val feedArticleCacheRepository: FeedArticleCacheRepository = NoopFeedArticleCacheRepository,
    /** The workspace store the dock's workspace menu reads (#1351); null leaves the menu unavailable. */
    val workspaceRepository: CachedWorkspaceRepository? = null,
    /** The apps the Dock starts with on a fresh install, by role (dialer, SMS, browser, camera); none by default. */
    val dockDefaultsResolver: DockDefaultsResolver = DockDefaultsResolver { emptyList() },
    /** The saved Home layouts (Settings > Home > Home layouts); in memory by default. */
    val savedHomeLayouts: SavedHomeLayoutsStore = InMemorySavedHomeLayoutsStore(),
    /** The provider a bound widget host id belongs to; what a saved layout records instead of the id. */
    val widgetProviderOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    /** Binds a saved widget again when a saved layout is activated; none by default (every widget is a placeholder). */
    val savedHomeWidgetBinder: SavedHomeWidgetBinder = SavedHomeWidgetBinder { null },
) {
    fun installedWidgetProviders(catalog: WidgetProviderCatalog): List<InstalledWidgetProvider> =
        catalog.sortedProviders(widgetProviderRepository.installedWidgetProviders())
}

/**
 * Every mode on every device class. Since S9 the mode is not a user choice: the stored `LauncherViewMode` is migration
 * input and follows the active workspace (`WorkspaceHome.homeModeFor`), so no stored layout resolves to another mode
 * on load any more (design 3.3, #1324). The Library-only availability the alpha shipped with is retired.
 *
 * The library mode stays available on purpose, although it is retired as a mode (ADR 0003, Amendment 10): a stored
 * library layout must still be found as the layout in use at start, so that `LibraryViewRetirement` converts it.
 * Were it unavailable, the start would fall back to the Standard layout and leave the library layout, which holds
 * what the user arranged, unread. Nothing fills it or chooses it any more; it is converted as soon as it is seen.
 */
fun defaultLauncherViewModeAvailability(): LauncherViewModeAvailability =
    LauncherViewModeAvailability(
        enabledExperimentalModesByDeviceClass =
            HomeLayoutDeviceClass.entries.associateWith {
                setOf(
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                    LauncherViewMode.CARD_INTERFACE,
                )
            },
    )
