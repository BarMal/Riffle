package com.riffle.app

import android.app.Activity
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import com.riffle.app.launcher.AndroidHomeLayoutDeviceClassObserver
import com.riffle.app.launcher.AndroidHomeRoleGateway
import com.riffle.app.launcher.AndroidLauncherWallpaperController
import com.riffle.app.launcher.AndroidWallpaperPickerGateway
import com.riffle.app.launcher.AndroidWebSearchLauncher
import com.riffle.app.launcher.AndroidWidgetAddWindowSizeProvider
import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.DataStoreLauncherSettingsRepository
import com.riffle.app.launcher.DataStoreWorkspaceStore
import com.riffle.app.launcher.HomeLayoutRepositories
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
import com.riffle.app.launcher.libraryOnlyLauncherViewModeAvailability
import com.riffle.app.launcher.notifications.ActiveNotificationRefreshCoordinator
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway
import com.riffle.app.launcher.notifications.DataStoreActiveNotificationRepository
import com.riffle.app.launcher.notifications.RiffleNotificationListenerConnection
import com.riffle.app.launcher.notifications.RiffleNotificationListenerService
import com.riffle.app.launcher.overlay.AndroidOverlayDockPermissionGateway
import com.riffle.app.launcher.overlay.AndroidOverlayDockServiceController
import com.riffle.app.launcher.rss.AndroidFeedParser
import com.riffle.app.launcher.rss.AndroidFeedTransport
import com.riffle.app.launcher.rss.DataStoreFeedArticleCacheRepository
import com.riffle.app.launcher.rss.FeedRefreshCoordinator
import com.riffle.app.launcher.rss.SettingsBackedConfiguredFeedSource
import com.riffle.app.launcher.sources.ContentSourceDependencies
import com.riffle.app.launcher.sources.FeedSourceDependencies
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
import com.riffle.app.launcher.workspace.WorkspaceItemActions
import com.riffle.app.launcher.workspace.WorkspaceRuntime
import com.riffle.app.launcher.workspace.sourceAccessMap
import com.riffle.app.launcher.workspace.workspaceLensProvider
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.hostsWidget
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.Executors

internal class MainActivityDependencies(
    private val activity: Activity,
) {
    val homeLayoutRepository by lazy { HomeLayoutRepositories.writeBehind(activity) }
    val launcherSettingsRepository by lazy { DataStoreLauncherSettingsRepository(activity) }
    val firstRunRepository by lazy { SharedPreferencesFirstRunRepository(activity) }
    val installedAppRepository by lazy {
        PackageManagerInstalledAppRepository(
            context = activity,
            appShortcutRepository = AndroidAppShortcutRepository(activity),
        )
    }
    val exclusionRepository by lazy { CachedExclusionRepository(DataStoreExclusionStore(activity)) }
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
            hostedWidgetIdReferenceState = { hostedWidgetId ->
                homeLayoutRepository.loadHomeLayoutSet()
                    ?.hostedWidgetIdReferenceState(hostedWidgetId)
                    ?: HostedWidgetIdReferenceState.Unknown
            },
        )
    }
    val widgetAddWindowSizeProvider by lazy { AndroidWidgetAddWindowSizeProvider(activity) }
    val widgetPreviewImageLoader by lazy { AndroidWidgetPreviewImageLoader(activity) }

    fun platformDependencies(): LauncherShellPlatformDependencies =
        LauncherShellPlatformDependencies(
            notificationRepository = activeNotificationRepository,
            widgetProviderRepository = AndroidInstalledWidgetProviderRepository(activity),
            initialHomeLayoutDeviceClass =
                homeLayoutDeviceClassFromConfiguration(
                    screenWidthDp = activity.resources.configuration.screenWidthDp,
                    screenHeightDp = activity.resources.configuration.screenHeightDp,
                ),
            viewModeAvailability = libraryOnlyLauncherViewModeAvailability(),
            deleteHostedWidgetId = widgetHostGateway::deleteHostedWidgetId,
            feedArticleCacheRepository = feedArticleCacheRepository,
            workspaceRepository = workspaceRepository,
        )

    /**
     * The workspace preview's runtime (debug-only experience). Call it only once the preview is switched on:
     * nothing in it subscribes to a source until a container observes a lens, and creating it reads no
     * platform data. [launcherSettings] supplies the notification hide rules and RSS feeds.
     */
    fun workspaceRuntime(launcherSettings: () -> LauncherSettings): WorkspaceRuntime {
        val registry =
            androidItemSources(
                installedApps = installedAppRepository,
                appVisibility = appVisibilityRepository,
                recentApps = recentAppRepository,
                notificationRepository = activeNotificationRepository,
                notificationAccess = notificationAccessGateway,
                hideRules = { launcherSettings().notificationHiding.rules },
                calendar =
                    androidCalendarSourceDependencies(
                        contentResolver = activity.contentResolver,
                        access = calendarAccessGateway,
                        accessChanges = calendarAccessChanges,
                    ),
                content =
                    ContentSourceDependencies(
                        feeds =
                            FeedSourceDependencies(
                                configuredFeeds = SettingsBackedConfiguredFeedSource(launcherSettings),
                                cache = feedArticleCacheRepository,
                                changes = feedRefreshCoordinator.cacheChanges,
                            ),
                    ),
            )
        val lensExecutor =
            Executors.newSingleThreadExecutor { task ->
                Thread(task, "riffle-lens-evaluation").apply { isDaemon = true }
            }
        return WorkspaceRuntime(
            repository = workspaceRepository,
            registry = registry,
            provider =
                workspaceLensProvider(
                    registry,
                    lensExecutor,
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
        )
    }

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
                ),
            importCoordinator = LauncherBackupImportCoordinator(),
            documentGateway = LauncherBackupDocumentGateway(),
        )

    fun widgetAddRequestHandler(
        selectedGrid: () -> GridDimensions,
        completeWidgetAdd: (HostedWidgetAddAction) -> HostedWidgetAddCompletionResult,
    ): LauncherWidgetAddRequestHandler =
        LauncherWidgetAddRequestHandler(
            widgetBindingCoordinator = widgetBindingCoordinator,
            selectedGrid = selectedGrid,
            windowSize = widgetAddWindowSizeProvider::windowSize,
            completeWidgetAdd = completeWidgetAdd,
            deleteHostedWidgetId = widgetHostGateway::deleteHostedWidgetId,
        )
}

private fun HomeLayoutSet.hostedWidgetIdReferenceState(hostedWidgetId: HostedWidgetId): HostedWidgetIdReferenceState =
    hostsWidget(hostedWidgetId)
        .let { referenced ->
            if (referenced) {
                HostedWidgetIdReferenceState.Referenced
            } else {
                HostedWidgetIdReferenceState.Unreferenced
            }
        }
