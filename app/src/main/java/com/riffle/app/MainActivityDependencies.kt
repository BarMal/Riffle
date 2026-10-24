package com.riffle.app

import android.app.Activity
import android.content.ComponentName
import android.content.ContentResolver
import android.service.notification.NotificationListenerService
import com.riffle.app.launcher.AndroidHomeLayoutDeviceClassObserver
import com.riffle.app.launcher.AndroidHomeRoleGateway
import com.riffle.app.launcher.AndroidLauncherWallpaperController
import com.riffle.app.launcher.AndroidWallpaperPickerGateway
import com.riffle.app.launcher.AndroidWebSearchLauncher
import com.riffle.app.launcher.AndroidWidgetAddWindowSizeProvider
import com.riffle.app.launcher.AndroidWorkspaceBackupPort
import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.DataStoreLauncherSettingsRepository
import com.riffle.app.launcher.DataStoreWorkspaceStore
import com.riffle.app.launcher.HomeLayoutRepositories
import com.riffle.app.launcher.HomeLayoutRuntime
import com.riffle.app.launcher.HostedWidgetAddAction
import com.riffle.app.launcher.HostedWidgetAddCompletionResult
import com.riffle.app.launcher.LauncherBackupDocumentGateway
import com.riffle.app.launcher.LauncherBackupDocumentHandler
import com.riffle.app.launcher.LauncherBackupExportCoordinator
import com.riffle.app.launcher.LauncherBackupImportCoordinator
import com.riffle.app.launcher.LauncherShellPlatformDependencies
import com.riffle.app.launcher.LauncherWidgetAddRequestHandler
import com.riffle.app.launcher.SharedPreferencesAppVisibilityRepository
import com.riffle.app.launcher.SharedPreferencesFirstRunRepository
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.apps.AndroidAppShortcutRepository
import com.riffle.app.launcher.apps.AndroidPackageChangeObserver
import com.riffle.app.launcher.apps.AndroidRecentAppRepository
import com.riffle.app.launcher.apps.AppCatalogChange
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.app.launcher.calendar.AndroidCalendarAccessGateway
import com.riffle.app.launcher.calendar.CalendarAccessChanges
import com.riffle.app.launcher.calendar.SharedPreferencesCalendarDenialHistory
import com.riffle.app.launcher.calendar.sourceAccess
import com.riffle.app.launcher.exclusions.CachedExclusionRepository
import com.riffle.app.launcher.exclusions.DataStoreExclusionStore
import com.riffle.app.launcher.homeLayoutDeviceClassFromConfiguration
import com.riffle.app.launcher.ics.CachedIcsFeedRepository
import com.riffle.app.launcher.ics.DataStoreIcsStore
import com.riffle.app.launcher.ics.ICS_MAX_RESPONSE_BYTES
import com.riffle.app.launcher.ics.IcsFeedsController
import com.riffle.app.launcher.ics.IcsRefreshCoordinator
import com.riffle.app.launcher.ics.defaultIcsEngine
import com.riffle.app.launcher.libraryOnlyLauncherViewModeAvailability
import com.riffle.app.launcher.notifications.ActiveNotificationRefreshCoordinator
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway
import com.riffle.app.launcher.notifications.DataStoreActiveNotificationRepository
import com.riffle.app.launcher.notifications.RiffleNotificationListenerConnection
import com.riffle.app.launcher.notifications.RiffleNotificationListenerService
import com.riffle.app.launcher.overlay.AndroidOverlayDockPermissionGateway
import com.riffle.app.launcher.overlay.AndroidOverlayDockServiceController
import com.riffle.app.launcher.pool.PoolItemActions
import com.riffle.app.launcher.pool.PoolRuntime
import com.riffle.app.launcher.rss.AndroidFeedParser
import com.riffle.app.launcher.rss.AndroidFeedTransport
import com.riffle.app.launcher.rss.DataStoreFeedArticleCacheRepository
import com.riffle.app.launcher.rss.FeedBackgroundRefreshScheduler
import com.riffle.app.launcher.rss.FeedRefreshCoordinator
import com.riffle.app.launcher.rss.SettingsBackedConfiguredFeedSource
import com.riffle.app.launcher.sources.ContentSourceDependencies
import com.riffle.app.launcher.sources.FeedSourceDependencies
import com.riffle.app.launcher.sources.IcsSourceDependencies
import com.riffle.app.launcher.sources.SearchSourceDependencies
import com.riffle.app.launcher.sources.androidCalendarSourceDependencies
import com.riffle.app.launcher.sources.androidItemSources
import com.riffle.app.launcher.widgets.AndroidInstalledWidgetProviderRepository
import com.riffle.app.launcher.widgets.AndroidWidgetHostGateway
import com.riffle.app.launcher.widgets.AndroidWidgetPreviewImageLoader
import com.riffle.app.launcher.widgets.HostedWidgetIdReferenceState
import com.riffle.app.launcher.widgets.PersistentWidgetAddTransactionStore
import com.riffle.app.launcher.widgets.WidgetBindingCoordinator
import com.riffle.app.launcher.workspace.AndroidExpressionImageLoader
import com.riffle.app.launcher.workspace.AndroidItemLaunchPort
import com.riffle.app.launcher.workspace.SharedPreferencesContentLevelStore
import com.riffle.app.launcher.workspace.SharedPreferencesDisabledSourcesStore
import com.riffle.app.launcher.workspace.SourceControls
import com.riffle.app.launcher.workspace.WorkspaceItemActions
import com.riffle.app.launcher.workspace.WorkspaceRuntime
import com.riffle.app.launcher.workspace.sourceAccessMap
import com.riffle.app.launcher.workspace.workspaceLensProvider
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.workspace.container.SharedSourceRegistry
import com.riffle.core.domain.launcher.workspace.pool.PoolHostIds
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceContentPrivacy
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.EnablementSourceRegistry
import com.riffle.core.domain.launcher.workspace.sources.SearchQueryHolder
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.Executors

internal class MainActivityDependencies(
    private val activity: Activity,
) {
    /** The process's home layout repositories (and, once cut over, the pool-backed home): opened once. */
    val homeLayoutRuntime by lazy { HomeLayoutRepositories.runtime(activity) }
    val homeLayoutRepository: HomeLayoutRepository get() = homeLayoutRuntime.repository
    val launcherSettingsRepository by lazy { DataStoreLauncherSettingsRepository(activity) }
    val firstRunRepository by lazy { SharedPreferencesFirstRunRepository(activity) }
    val installedAppRepository by lazy {
        PackageManagerInstalledAppRepository(
            context = activity,
            appShortcutRepository = AndroidAppShortcutRepository(activity),
        )
    }
    val exclusionRepository by lazy { CachedExclusionRepository(DataStoreExclusionStore(activity)) }

    /**
     * The process's one pool repository, shared by the standard home (once cut over it is loaded at startup), the
     * preview and backup, so they never hold diverging copies.
     */
    private val poolRepository by lazy { HomeLayoutRepositories.pool(activity) }
    val appVisibilityRepository by lazy { SharedPreferencesAppVisibilityRepository(activity) }
    val feedArticleCacheRepository by lazy { DataStoreFeedArticleCacheRepository(activity) }

    /**
     * User-triggered RSS refresh (#1374). Constructing it does no I/O and starts nothing; the network is touched
     * only when a refresh is requested, on its own background thread. Feeds are read from the saved settings.
     */
    val feedRefreshCoordinator by lazy {
        FeedRefreshCoordinator(
            transport = AndroidFeedTransport(),
            parser = AndroidFeedParser(),
            cache = feedArticleCacheRepository,
            configuredFeeds = { launcherSettingsRepository.loadLauncherSettings()?.rss?.feeds.orEmpty() },
            executor =
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "riffle-feed-refresh").apply { isDaemon = true }
                },
        )
    }

    /** Keeps WorkManager's periodic feed work in step with the RSS settings (#1393); only cancels while Off. */
    val feedBackgroundRefreshScheduler by lazy { FeedBackgroundRefreshScheduler(activity) }

    /**
     * The ICS calendar feed list and its parsed-event cache (#1409), in a device-local DataStore file that is
     * never backed up. Created only when the workspace preview runtime is, and it reads nothing else.
     */
    val icsFeedRepository by lazy { CachedIcsFeedRepository(DataStoreIcsStore(activity)) }
    val icsEngine by lazy { defaultIcsEngine() }

    /** User-triggered ICS refresh: no I/O until asked, on its own background thread. */
    val icsRefreshCoordinator by lazy {
        IcsRefreshCoordinator(
            transport = AndroidFeedTransport(maxResponseBytes = ICS_MAX_RESPONSE_BYTES),
            engine = icsEngine,
            repository = icsFeedRepository,
            executor =
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "riffle-ics-refresh").apply { isDaemon = true }
                },
        )
    }

    /**
     * The explicit wiring point from the DataStore workspace store to the shell (#1351). Built lazily and
     * read only when the workspace menu is switched on; its writes outlive the activity on their own scope.
     */
    val workspaceRepository by lazy {
        CachedWorkspaceRepository(
            store = DataStoreWorkspaceStore(activity),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
    val recentAppRepository by lazy { AndroidRecentAppRepository(activity) }
    val homeRoleGateway by lazy { AndroidHomeRoleGateway(activity) }
    val appLauncher by lazy { AndroidAppLauncher(activity) }
    val webSearchLauncher by lazy { AndroidWebSearchLauncher(activity) }
    val appIconLoader by lazy { PackageManagerAppIconLoader(activity.packageManager) }
    val wallpaperController by lazy { AndroidLauncherWallpaperController(activity.window) }
    val wallpaperPickerGateway by lazy { AndroidWallpaperPickerGateway(activity) }
    val notificationAccessGateway by lazy { AndroidNotificationAccessGateway(activity) }
    val calendarAccessGateway by lazy {
        AndroidCalendarAccessGateway(activity, SharedPreferencesCalendarDenialHistory(activity))
    }
    val calendarAccessChanges by lazy { CalendarAccessChanges() }
    val overlayDockPermissionGateway by lazy { AndroidOverlayDockPermissionGateway(activity) }
    val overlayDockServiceController by lazy { AndroidOverlayDockServiceController(activity) }
    val homeLayoutDeviceClassObserver by lazy { AndroidHomeLayoutDeviceClassObserver(activity) }
    val activeNotificationRepository by lazy { DataStoreActiveNotificationRepository(activity) }
    val widgetHostGateway by lazy { AndroidWidgetHostGateway(activity) }
    val widgetBindingCoordinator by lazy {
        WidgetBindingCoordinator(
            widgetHostGateway = widgetHostGateway,
            transactionStore = PersistentWidgetAddTransactionStore(activity),
            hostedWidgetIdReferenceState = { hostedWidgetId -> hostedWidgetIdReferenceState(hostedWidgetId) },
        )
    }
    val widgetAddWindowSizeProvider by lazy { AndroidWidgetAddWindowSizeProvider(activity) }
    val widgetPreviewImageLoader by lazy { AndroidWidgetPreviewImageLoader(activity) }

    fun platformDependencies(): LauncherShellPlatformDependencies {
        attachHostIdReleaser()
        return LauncherShellPlatformDependencies(
            notificationRepository = activeNotificationRepository,
            widgetProviderRepository = AndroidInstalledWidgetProviderRepository(activity),
            initialHomeLayoutDeviceClass =
                homeLayoutDeviceClassFromConfiguration(
                    screenWidthDp = activity.resources.configuration.screenWidthDp,
                    screenHeightDp = activity.resources.configuration.screenHeightDp,
                ),
            viewModeAvailability = libraryOnlyLauncherViewModeAvailability(),
            deleteHostedWidgetId = hostIdDeletion::unlessReferenced,
            feedArticleCacheRepository = feedArticleCacheRepository,
            workspaceRepository = workspaceRepository,
        )
    }

    /** Every widget host id delete of the process goes through these checks (see [HostIdDeletion]). */
    val hostIdDeletion by lazy { HostIdDeletion({ homeLayoutRuntime }, widgetHostGateway::deleteHostedWidgetId) }

    /** Host ids the pool released after its write landed: deleted on the main thread, rechecked there. */
    private fun attachHostIdReleaser() {
        homeLayoutRuntime.poolHome?.hostIdReleaser = { ids ->
            activity.runOnUiThread { ids.forEach(hostIdDeletion::unlessReferenced) }
        }
    }

    /**
     * The startup check on a discarded widget-add transaction: referenced by the pool (any layout, arrangement or
     * dock) or the shell's layout set. Unknown (so nothing is deleted) while neither can be read.
     */
    private fun hostedWidgetIdReferenceState(hostedWidgetId: HostedWidgetId): HostedWidgetIdReferenceState {
        val layoutSet = homeLayoutRepository.loadHomeLayoutSet()
        val poolState = poolRepository.current()
        val poolHome = homeLayoutRuntime.poolHome
        return when {
            !homeLayoutRuntime.allowsHostIdDeletion -> HostedWidgetIdReferenceState.Unknown
            poolHome != null && poolState == null -> HostedWidgetIdReferenceState.Unknown
            layoutSet == null && poolState == null -> HostedWidgetIdReferenceState.Unknown
            PoolHostIds.isReferenced(hostedWidgetId, poolState, layoutSet) -> HostedWidgetIdReferenceState.Referenced
            else -> HostedWidgetIdReferenceState.Unreferenced
        }
    }

    /**
     * The workspace preview's runtime (debug-only experience). Call it only once the preview is switched on:
     * nothing in it subscribes to a source until a container observes a lens, and creating it reads no
     * platform data. [launcherSettings] supplies the notification hide rules and RSS feeds.
     */
    fun workspaceRuntime(launcherSettings: () -> LauncherSettings): WorkspaceRuntime {
        val searchQuery = SearchQueryHolder()
        val registry = workspaceItemSources(activity.contentResolver, launcherSettings, searchQuery)
        val lensExecutor =
            Executors.newSingleThreadExecutor { task ->
                Thread(task, "riffle-lens-evaluation").apply { isDaemon = true }
            }
        // A source the user turned off emits Off without reading its repository; the shared registry on top
        // gives containers and the Sources page one connection per source.
        val enablement = StoredSourceEnablement(SharedPreferencesDisabledSourcesStore(activity))
        // Per-source content levels (Settings > Privacy), applied at lens projection; device-local like enablement.
        val contentPrivacy = StoredSourceContentPrivacy(SharedPreferencesContentLevelStore(activity))
        val shared = SharedSourceRegistry(EnablementSourceRegistry(registry, enablement))
        return WorkspaceRuntime(
            repository = workspaceRepository,
            registry = registry,
            provider =
                workspaceLensProvider(
                    shared,
                    lensExecutor,
                    exclusionChanges = exclusionRepository,
                    contentPrivacy = contentPrivacy,
                    exclusions = {
                        exclusionRepository.rules(
                            homeLayoutDeviceClassFromConfiguration(
                                screenWidthDp = activity.resources.configuration.screenWidthDp,
                                screenHeightDp = activity.resources.configuration.screenHeightDp,
                            ) ?: HomeLayoutDeviceClass.PHONE,
                        )
                    },
                ),
            imageLoader = AndroidExpressionImageLoader(activity.packageManager),
            itemActions = WorkspaceItemActions(AndroidItemLaunchPort(activity, appLauncher)),
            exclusionLoader = {
                exclusionRepository.initialize(
                    hiddenApps = appVisibilityRepository.hiddenAppIdentities(),
                    hideRules = launcherSettings().notificationHiding.rules,
                )
            },
            sourceAccess = {
                sourceAccessMap(
                    notificationAccess = notificationAccessGateway.getNotificationAccessStatus(),
                    calendarAccess = calendarAccessGateway.sourceAccess(),
                    recentAppsAccess =
                        if (recentAppRepository.canReadRecentApps()) SourceAccess.GRANTED else SourceAccess.REQUIRED,
                )
            },
            sourceControls =
                SourceControls(statusRegistry = shared, enablement = enablement, contentPrivacy = contentPrivacy),
            exclusions = exclusionRepository,
            icsFeeds = IcsFeedsController(icsFeedRepository, icsRefreshCoordinator),
            searchQuery = searchQuery,
        )
    }

    /**
     * The Workspaces (preview) placed-items pool runtime. Call it only once the preview opens: building it
     * reads no storage; the store is read when the preview calls `initialize`. Item taps go through the same
     * app launcher the standard home uses.
     */
    fun poolRuntime(): PoolRuntime =
        PoolRuntime(
            repository = poolRepository,
            iconLoader = appIconLoader,
            widgetViews = widgetHostGateway,
            actions = PoolItemActions(launchApp = appLauncher::launch, launchShortcut = appLauncher::launchShortcut),
            providerOf = widgetHostGateway::hostedWidgetProvider,
            releaseHostIds = { ids ->
                activity.runOnUiThread { ids.forEach(hostIdDeletion::unlessReferenced) }
            },
        )

    fun packageChangeObserver(onCatalogChanged: (AppCatalogChange) -> Unit): AndroidPackageChangeObserver =
        AndroidPackageChangeObserver(activity) { change ->
            activity.runOnUiThread { onCatalogChanged(change) }
        }

    fun activeNotificationRefreshCoordinator(
        refreshNotifications: () -> Unit,
        refreshPlatformStatuses: () -> Unit,
    ): ActiveNotificationRefreshCoordinator =
        ActiveNotificationRefreshCoordinator(
            notificationChangeSource = activeNotificationRepository,
            dispatchOnMainThread = { action -> activity.runOnUiThread { action() } },
            refreshNotifications = refreshNotifications,
            refreshPlatformStatuses = refreshPlatformStatuses,
            isListenerConnected = RiffleNotificationListenerConnection::isConnected,
            requestListenerRebind = {
                NotificationListenerService.requestRebind(
                    ComponentName(activity, RiffleNotificationListenerService::class.java),
                )
            },
        )

    fun backupDocumentHandler(currentState: () -> LauncherShellState): LauncherBackupDocumentHandler =
        LauncherBackupDocumentHandler(
            exportCoordinator =
                LauncherBackupExportCoordinator(
                    homeLayoutRepository = homeLayoutRepository,
                    appVisibilityRepository = appVisibilityRepository,
                    currentState = currentState,
                    workspaceBackup = workspaceBackupPort,
                ),
            importCoordinator = LauncherBackupImportCoordinator(),
            documentGateway = LauncherBackupDocumentGateway(),
            workspaceBackup = workspaceBackupPort,
        )

    /** Workspaces, exclusions and placed items in the backup: reads/writes only what the stores hold (see docs). */
    private val workspaceBackupPort by lazy {
        AndroidWorkspaceBackupPort(
            workspaces = workspaceRepository,
            exclusions = exclusionRepository,
            pool = poolRepository,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            poolOwnsHome = { homeLayoutRuntime.poolHome != null },
        )
    }

    fun widgetAddRequestHandler(
        selectedGrid: () -> GridDimensions,
        completeWidgetAdd: (HostedWidgetAddAction) -> HostedWidgetAddCompletionResult,
    ): LauncherWidgetAddRequestHandler =
        LauncherWidgetAddRequestHandler(
            widgetBindingCoordinator = widgetBindingCoordinator,
            selectedGrid = selectedGrid,
            windowSize = widgetAddWindowSizeProvider::windowSize,
            completeWidgetAdd = completeWidgetAdd,
            deleteHostedWidgetId = hostIdDeletion::ifAllowed,
        )
}

/** The built-in item sources behind the workspace preview, each reading its repository lazily. */
private fun MainActivityDependencies.workspaceItemSources(
    contentResolver: ContentResolver,
    launcherSettings: () -> LauncherSettings,
    searchQuery: SearchQueryHolder,
) = androidItemSources(
    installedApps = installedAppRepository,
    appVisibility = appVisibilityRepository,
    recentApps = recentAppRepository,
    notificationRepository = activeNotificationRepository,
    notificationAccess = notificationAccessGateway,
    hideRules = { launcherSettings().notificationHiding.rules },
    calendar =
        androidCalendarSourceDependencies(
            contentResolver = contentResolver,
            access = calendarAccessGateway,
            accessChanges = calendarAccessChanges,
        ),
    content =
        ContentSourceDependencies(
            search = SearchSourceDependencies(query = searchQuery),
            feeds =
                FeedSourceDependencies(
                    configuredFeeds = SettingsBackedConfiguredFeedSource(launcherSettings),
                    cache = feedArticleCacheRepository,
                    changes = feedRefreshCoordinator.cacheChanges,
                ),
            ics =
                IcsSourceDependencies(
                    feeds = { icsFeedRepository.takeIf { it.isLoaded }?.settings()?.feeds },
                    cachedEvents = { id -> icsFeedRepository.cached(id)?.events },
                    engine = icsEngine,
                    changes = icsFeedRepository,
                ),
        ),
)

/**
 * The widget host id deletes of the process. Nothing is deleted while the process is read-only or damaged
 * ([HomeLayoutRuntime.allowsHostIdDeletion]). Once the home renders from the pool, a widget removed from one workspace
 * may still be shown by another (the same instance), so [unlessReferenced] keeps an id any arrangement, the dock or the
 * shell's set still references; on the legacy path the shell deletes as before.
 */
internal class HostIdDeletion(
    private val runtime: () -> HomeLayoutRuntime,
    private val delete: (HostedWidgetId) -> Unit,
) {
    fun unlessReferenced(hostedWidgetId: HostedWidgetId) {
        val poolHome = runtime().poolHome
        if (poolHome == null || !poolHome.isHostIdReferenced(hostedWidgetId)) ifAllowed(hostedWidgetId)
    }

    /** Deletes [hostedWidgetId] (a freshly allocated id the platform refused) unless deletes are off. */
    fun ifAllowed(hostedWidgetId: HostedWidgetId) {
        if (runtime().allowsHostIdDeletion) delete(hostedWidgetId)
    }
}
