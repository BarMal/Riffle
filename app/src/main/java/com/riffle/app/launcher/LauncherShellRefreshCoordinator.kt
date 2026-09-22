package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.WidgetProviderCatalogStatus
import com.riffle.core.domain.launcher.apps.InstalledAppRefreshResult
import com.riffle.core.domain.launcher.notifications.AppNotificationCounter
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationStaleFilter
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import com.riffle.core.domain.launcher.widgets.WidgetProviderCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Fetches for the async refresh actions below are split from applying their result to state, so
 * that the (slow, state-independent) I/O can run first and the (fast, pure) state transform can be
 * applied against whichever state is current at commit time -- see
 * [LauncherShellRefreshActions.launchRefresh] for why that ordering matters.
 */
internal class LauncherShellRefreshCoordinator(
    private val installedAppDependencies: InstalledAppRefreshDependencies,
    private val notificationDependencies: LauncherNotificationRefreshDependencies,
    private val widgetProviderDependencies: LauncherWidgetProviderRefreshDependencies,
) {
    fun refreshInstalledApps(currentState: LauncherShellState): LauncherShellState =
        applyInstalledAppRefresh(currentState, fetchInstalledAppRefreshResult())

    fun fetchInstalledAppRefreshResult(): InstalledAppRefreshResult =
        installedAppDependencies.installedAppRepository.refreshResult()

    fun applyInstalledAppRefresh(
        currentState: LauncherShellState,
        result: InstalledAppRefreshResult,
    ): LauncherShellState = currentState.withRefreshedInstalledApps(installedAppDependencies, result)

    fun refreshNotifications(currentState: LauncherShellState): LauncherShellState =
        applyNotificationRefresh(currentState, fetchActiveNotifications())

    fun fetchActiveNotifications(): List<LauncherNotification> =
        notificationDependencies.notificationRepository.activeNotifications()

    fun applyNotificationRefresh(
        currentState: LauncherShellState,
        notifications: List<LauncherNotification>,
    ): LauncherShellState {
        val refreshedState =
            currentState.withNotificationState(
                notifications = notifications,
                appNotificationCounter = notificationDependencies.appNotificationCounter,
                appNotificationGrouper = notificationDependencies.appNotificationGrouper,
                notificationStaleFilter = notificationDependencies.notificationStaleFilter,
                nowEpochMillis = notificationDependencies.epochMillisProvider.nowEpochMillis(),
            )
        return refreshedState.withRefreshedGeneratedPages(installedAppDependencies.homeLayoutRepository)
    }

    fun refreshWidgetProviders(currentState: LauncherShellState): LauncherShellState =
        applyWidgetProviderRefresh(currentState, fetchInstalledWidgetProviders())

    fun fetchInstalledWidgetProviders(): List<InstalledWidgetProvider> =
        widgetProviderDependencies.widgetProviderRepository.installedWidgetProviders()

    fun applyWidgetProviderRefresh(
        currentState: LauncherShellState,
        providers: List<InstalledWidgetProvider>,
    ): LauncherShellState =
        currentState.copy(
            installedWidgetProviders = widgetProviderDependencies.widgetProviderCatalog.sortedProviders(providers),
            widgetProviderCatalogStatus = WidgetProviderCatalogStatus.READY,
        )
}

internal data class LauncherNotificationRefreshDependencies(
    val notificationRepository: LauncherNotificationRepository,
    val epochMillisProvider: EpochMillisProvider,
    val appNotificationCounter: AppNotificationCounter = AppNotificationCounter(),
    val appNotificationGrouper: AppNotificationGrouper = AppNotificationGrouper(),
    val notificationStaleFilter: NotificationStaleFilter = NotificationStaleFilter(),
)

internal data class LauncherWidgetProviderRefreshDependencies(
    val widgetProviderRepository: InstalledWidgetProviderRepository,
    val widgetProviderCatalog: WidgetProviderCatalog = WidgetProviderCatalog(),
)

internal class LauncherShellRefreshActions(
    private val coroutineScope: CoroutineScope,
    private val refreshDispatcher: CoroutineDispatcher,
    private val currentState: () -> LauncherShellState,
    private val updateState: (LauncherShellState) -> Unit,
    private val refreshCoordinator: LauncherShellRefreshCoordinator,
    private val artworkRevisionStore: AdaptiveStageArtworkRevisionStore = adaptiveStageArtworkRevisions,
    private val beforeNotificationCommit: () -> Unit = {},
) {
    private var installedAppRefreshJob: Job? = null
    private var notificationRefreshJob: Job? = null
    private var widgetProviderRefreshJob: Job? = null
    private val notificationRefreshLock = Any()
    private var notificationRefreshGeneration = 0L

    fun refreshInstalledApps(beforeRefresh: () -> Unit = {}): Job =
        launchRefresh(
            cancelExisting = { installedAppRefreshJob?.cancel() },
            registerJob = { job -> installedAppRefreshJob = job },
            fetchResult = {
                beforeRefresh()
                refreshCoordinator.fetchInstalledAppRefreshResult()
            },
            commitState = { result, context ->
                context.ensureActive()
                updateState(refreshCoordinator.applyInstalledAppRefresh(currentState(), result))
            },
        )

    fun refreshNotifications(): Job {
        val generation =
            synchronized(notificationRefreshLock) {
                ++notificationRefreshGeneration
            }
        return launchRefresh(
            cancelExisting = { notificationRefreshJob?.cancel() },
            registerJob = { job -> notificationRefreshJob = job },
            fetchResult = { refreshCoordinator.fetchActiveNotifications() },
            commitState = { notifications, context ->
                context.ensureActive()
                beforeNotificationCommit()
                synchronized(notificationRefreshLock) {
                    context.ensureActive()
                    if (generation == notificationRefreshGeneration) {
                        val state = refreshCoordinator.applyNotificationRefresh(currentState(), notifications)
                        artworkRevisionStore.replace(state.notificationGroupsByApp)
                        updateState(state)
                    }
                }
            },
        )
    }

    fun refreshWidgetProviders(): Job {
        widgetProviderRefreshJob?.cancel()
        updateState(currentState().copy(widgetProviderCatalogStatus = WidgetProviderCatalogStatus.LOADING))
        val job =
            coroutineScope.launch(refreshDispatcher) {
                val providers = runCatching { refreshCoordinator.fetchInstalledWidgetProviders() }
                coroutineContext.ensureActive()
                val refreshedState =
                    providers.fold(
                        onSuccess = { value -> refreshCoordinator.applyWidgetProviderRefresh(currentState(), value) },
                        onFailure = {
                            currentState().copy(widgetProviderCatalogStatus = WidgetProviderCatalogStatus.FAILED)
                        },
                    )
                updateState(refreshedState)
            }
        widgetProviderRefreshJob = job
        return job
    }

    /**
     * Fetches its (slow, state-independent) I/O result first, then applies it against whichever
     * state is current right before committing, with no suspension in between -- not the state as
     * it was when the fetch started.
     *
     * A synchronous edit (a settings change, an app-visibility toggle) can land on [currentState]
     * while a fetch is in flight; committing a transform built from a state snapshot taken before
     * that edit would silently discard it the moment this refresh completes. Reading state only
     * here, immediately before [commitState] writes it back, means a refresh can only ever build on
     * top of the latest edit, never erase it.
     */
    private fun <T> launchRefresh(
        cancelExisting: () -> Unit,
        registerJob: (Job) -> Unit,
        fetchResult: () -> T,
        commitState: (T, kotlin.coroutines.CoroutineContext) -> Unit,
    ): Job {
        cancelExisting()
        val job =
            coroutineScope.launch(refreshDispatcher) {
                runCatching { fetchResult() }.onSuccess { result -> commitState(result, coroutineContext) }
            }
        registerJob(job)
        return job
    }
}

fun LauncherShellViewModel.refreshInstalledApps(): Job = refreshActions.refreshInstalledApps()

fun LauncherShellViewModel.refreshNotifications(): Job = refreshActions.refreshNotifications()

fun LauncherShellViewModel.refreshWidgetProviders(): Job = refreshActions.refreshWidgetProviders()
