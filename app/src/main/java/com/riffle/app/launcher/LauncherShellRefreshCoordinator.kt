package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.notifications.AppNotificationCounter
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationStaleFilter
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import com.riffle.core.domain.launcher.widgets.WidgetProviderCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class LauncherShellRefreshCoordinator(
    private val installedAppDependencies: InstalledAppRefreshDependencies,
    private val notificationDependencies: LauncherNotificationRefreshDependencies,
    private val widgetProviderDependencies: LauncherWidgetProviderRefreshDependencies,
) {
    fun refreshInstalledApps(currentState: LauncherShellState): LauncherShellState =
        currentState.withRefreshedInstalledApps(installedAppDependencies)

    fun refreshNotifications(currentState: LauncherShellState): LauncherShellState =
        currentState.withNotificationState(
            notificationRepository = notificationDependencies.notificationRepository,
            appNotificationCounter = notificationDependencies.appNotificationCounter,
            appNotificationGrouper = notificationDependencies.appNotificationGrouper,
            notificationStaleFilter = notificationDependencies.notificationStaleFilter,
            nowEpochMillis = notificationDependencies.epochMillisProvider.nowEpochMillis(),
        ).withReconciledCardsChapterSelection()

    fun refreshWidgetProviders(currentState: LauncherShellState): LauncherShellState =
        currentState.copy(
            installedWidgetProviders =
                widgetProviderDependencies.widgetProviderCatalog.sortedProviders(
                    widgetProviderDependencies.widgetProviderRepository.installedWidgetProviders(),
                ),
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
) {
    private var installedAppRefreshJob: Job? = null
    private var notificationRefreshJob: Job? = null
    private var widgetProviderRefreshJob: Job? = null

    fun refreshInstalledApps(beforeRefresh: () -> Unit = {}): Job =
        launchRefresh(
            cancelExisting = { installedAppRefreshJob?.cancel() },
            registerJob = { job -> installedAppRefreshJob = job },
            refreshState = {
                beforeRefresh()
                refreshCoordinator.refreshInstalledApps(currentState())
            },
        )

    fun refreshNotifications(): Job =
        launchRefresh(
            cancelExisting = { notificationRefreshJob?.cancel() },
            registerJob = { job -> notificationRefreshJob = job },
            refreshState = { refreshCoordinator.refreshNotifications(currentState()) },
        )

    fun refreshWidgetProviders(): Job =
        launchRefresh(
            cancelExisting = { widgetProviderRefreshJob?.cancel() },
            registerJob = { job -> widgetProviderRefreshJob = job },
            refreshState = { refreshCoordinator.refreshWidgetProviders(currentState()) },
        )

    private fun launchRefresh(
        cancelExisting: () -> Unit,
        registerJob: (Job) -> Unit,
        refreshState: () -> LauncherShellState,
    ): Job {
        cancelExisting()
        val job =
            coroutineScope.launch(refreshDispatcher) {
                val fallbackState = currentState()
                updateState(
                    runCatching {
                        refreshState()
                    }.getOrElse { fallbackState },
                )
            }
        registerJob(job)
        return job
    }
}

fun LauncherShellViewModel.refreshInstalledApps(): Job = refreshActions.refreshInstalledApps()

fun LauncherShellViewModel.refreshNotifications(): Job = refreshActions.refreshNotifications()

fun LauncherShellViewModel.refreshWidgetProviders(): Job = refreshActions.refreshWidgetProviders()
