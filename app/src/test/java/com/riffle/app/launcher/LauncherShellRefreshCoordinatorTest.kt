package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRefreshResult
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.contextual.ContextualSettings
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellRefreshCoordinatorTest {
    @Test
    fun refreshInstalledAppsUpdatesAppCatalogOnly() {
        val installedAppRepository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val notificationRepository =
            FakeNotificationRepository(
                notifications = listOf(notification(key = "mail-1", packageName = "com.riffle.mail")),
            )
        val widgetProviderRepository = FakeWidgetProviderRepository(providers = listOf(widgetProvider("Clock")))
        val coordinator =
            coordinator(
                installedAppRepository = installedAppRepository,
                notificationRepository = notificationRepository,
                widgetProviderRepository = widgetProviderRepository,
            )

        val state = coordinator.refreshInstalledApps(LauncherShellState())

        assertEquals(listOf("Camera"), state.installedApps.map { app -> app.label })
        assertEquals(0, notificationRepository.activeNotificationReadCount)
        assertEquals(0, widgetProviderRepository.providerReadCount)
    }

    @Test
    fun singleProfileFallbackPreservesUnavailableWorkCatalogAndPersistedPlacements() {
        val personal = app(label = "Camera")
        val work = app(label = "Docs", profile = AppProfile.work())
        val repository =
            FakeInstalledAppRepository(
                refreshResult = InstalledAppRefreshResult.Partial(listOf(personal)),
            )
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val coordinator = coordinator(installedAppRepository = repository, homeLayoutRepository = homeLayoutRepository)
        val layout = layoutWithPlacements(personal.identity, work.identity)
        val state = LauncherShellState(homeLayout = layout, installedApps = listOf(personal, work))

        val refreshed = coordinator.refreshInstalledApps(state)

        assertEquals(state, refreshed)
        assertEquals(emptyList<HomeLayout>(), homeLayoutRepository.savedLayouts)
    }

    @Test
    fun unavailableInstalledAppRefreshPreservesTheLastSuccessfulCatalog() {
        val camera = app(label = "Camera")
        val repository = FakeInstalledAppRepository(refreshResult = InstalledAppRefreshResult.Unavailable)
        val coordinator = coordinator(installedAppRepository = repository)
        val state = LauncherShellState(installedApps = listOf(camera), appDrawerApps = listOf(camera))

        val refreshed = coordinator.refreshInstalledApps(state)

        assertEquals(state, refreshed)
    }

    @Test
    fun authoritativeEmptyInventoryUpdatesTheCatalogWithoutPruningPersistedPlacements() {
        val camera = app(label = "Camera")
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val coordinator =
            coordinator(
                installedAppRepository =
                    FakeInstalledAppRepository(
                        refreshResult = InstalledAppRefreshResult.Authoritative(emptyList()),
                    ),
                homeLayoutRepository = homeLayoutRepository,
            )
        val workDocs = app(label = "Docs", profile = AppProfile.work())
        val layout = layoutWithPlacements(camera.identity, workDocs.identity)

        val refreshed =
            coordinator.refreshInstalledApps(
                LauncherShellState(homeLayout = layout, installedApps = listOf(camera)),
            )

        assertEquals(emptyList<InstalledApp>(), refreshed.installedApps)
        assertEquals(layout, refreshed.homeLayout)
        assertEquals(emptyList<HomeLayout>(), homeLayoutRepository.savedLayouts)
    }

    @Test
    fun refreshNotificationsUpdatesNotificationStateOnly() {
        val installedAppRepository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val notificationRepository =
            FakeNotificationRepository(
                notifications =
                    listOf(
                        notification(
                            key = "mail-1",
                            packageName = "com.riffle.mail",
                            category = NotificationCategory.EMAIL,
                        ),
                    ),
            )
        val widgetProviderRepository = FakeWidgetProviderRepository(providers = listOf(widgetProvider("Clock")))
        val coordinator =
            coordinator(
                installedAppRepository = installedAppRepository,
                notificationRepository = notificationRepository,
                widgetProviderRepository = widgetProviderRepository,
            )

        val state = coordinator.refreshNotifications(LauncherShellState())

        assertEquals(listOf(AppPackageName("com.riffle.mail")), state.notificationGroupsByApp.map { it.packageName })
        assertEquals(1, state.notificationCountsByCategory[NotificationCategory.EMAIL])
        assertEquals(0, installedAppRepository.installedAppReadCount)
        assertEquals(0, widgetProviderRepository.providerReadCount)
    }

    @Test
    fun refreshNotificationsSelectsConfiguredCardsPageWhenContextualBehaviourIsEnabled() {
        val defaults = HomeLayoutDefaults.standard()
        val cardsPage =
            defaults.selectedPage.copy(
                id = LauncherPageId("cards"),
                type = LauncherPageType.Generated(GeneratedLauncherPageKind.NOTIFICATION_CARDS),
            )
        val layout = defaults.copy(pages = listOf(defaults.selectedPage, cardsPage))
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val coordinator =
            coordinator(
                notificationRepository =
                    FakeNotificationRepository(
                        notifications = listOf(notification(key = "mail-1", packageName = "com.riffle.mail")),
                    ),
                homeLayoutRepository = homeLayoutRepository,
            )

        val refreshed =
            coordinator.refreshNotifications(
                LauncherShellState(
                    homeLayout = layout,
                    launcherSettings = LauncherSettings(contextual = ContextualSettings(enabled = true)),
                ),
            )

        assertEquals(cardsPage.id, refreshed.homeLayout.selectedPageId)
        assertEquals(refreshed.homeLayout, homeLayoutRepository.savedLayouts.single())
    }

    @Test
    fun refreshWidgetProvidersUpdatesSortedProviderCatalogOnly() {
        val installedAppRepository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val notificationRepository =
            FakeNotificationRepository(
                notifications = listOf(notification(key = "mail-1", packageName = "com.riffle.mail")),
            )
        val widgetProviderRepository =
            FakeWidgetProviderRepository(
                providers =
                    listOf(
                        widgetProvider(label = "Clock", packageName = "com.example.clock"),
                        widgetProvider(label = "Calendar", packageName = "com.example.calendar"),
                    ),
            )
        val coordinator =
            coordinator(
                installedAppRepository = installedAppRepository,
                notificationRepository = notificationRepository,
                widgetProviderRepository = widgetProviderRepository,
            )

        val state = coordinator.refreshWidgetProviders(LauncherShellState())

        assertEquals(listOf("Calendar", "Clock"), state.installedWidgetProviders.map { provider -> provider.label })
        assertEquals(0, installedAppRepository.installedAppReadCount)
        assertEquals(0, notificationRepository.activeNotificationReadCount)
    }

    private fun coordinator(
        installedAppRepository: FakeInstalledAppRepository = FakeInstalledAppRepository(),
        notificationRepository: FakeNotificationRepository = FakeNotificationRepository(),
        widgetProviderRepository: FakeWidgetProviderRepository = FakeWidgetProviderRepository(),
        homeLayoutRepository: FakeHomeLayoutRepository = FakeHomeLayoutRepository(),
    ): LauncherShellRefreshCoordinator =
        LauncherShellRefreshCoordinator(
            installedAppDependencies =
                InstalledAppRefreshDependencies(
                    installedAppRepository = installedAppRepository,
                    appVisibilityRepository = FakeAppVisibilityRepository(),
                    appCatalog = InstalledAppCatalog(),
                    homeLayoutRepository = homeLayoutRepository,
                    appShortcutRepository = NoopAppShortcutRepository,
                ),
            notificationDependencies =
                LauncherNotificationRefreshDependencies(
                    notificationRepository = notificationRepository,
                    epochMillisProvider = FixedEpochMillisProvider(nowEpochMillis = 10 * 60 * 1_000L),
                ),
            widgetProviderDependencies =
                LauncherWidgetProviderRefreshDependencies(
                    widgetProviderRepository = widgetProviderRepository,
                ),
        )

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp> = emptyList(),
        var refreshResult: InstalledAppRefreshResult = InstalledAppRefreshResult.Authoritative(apps),
    ) : InstalledAppRepository {
        var installedAppReadCount: Int = 0

        override fun installedApps(): List<InstalledApp> {
            installedAppReadCount += 1
            return apps
        }

        override fun refreshResult(): InstalledAppRefreshResult {
            installedAppReadCount += 1
            return refreshResult
        }
    }

    private class FakeAppVisibilityRepository : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }

    private class FakeHomeLayoutRepository : HomeLayoutRepository {
        val savedLayouts = mutableListOf<HomeLayout>()

        override fun loadHomeLayout(): HomeLayout? = null

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayouts += layout
        }
    }

    private object NoopAppShortcutRepository : AppShortcutRepository {
        override fun shortcutsFor(apps: List<InstalledApp>) = emptyMap<AppIdentity, List<AppShortcut>>()
    }

    private class FakeNotificationRepository(
        var notifications: List<LauncherNotification> = emptyList(),
    ) : LauncherNotificationRepository {
        var activeNotificationReadCount: Int = 0

        override fun activeNotifications(): List<LauncherNotification> {
            activeNotificationReadCount += 1
            return notifications
        }
    }

    private class FakeWidgetProviderRepository(
        var providers: List<InstalledWidgetProvider> = emptyList(),
    ) : InstalledWidgetProviderRepository {
        var providerReadCount: Int = 0

        override fun installedWidgetProviders(): List<InstalledWidgetProvider> {
            providerReadCount += 1
            return providers
        }
    }

    private class FixedEpochMillisProvider(
        private val nowEpochMillis: Long,
    ) : EpochMillisProvider {
        override fun nowEpochMillis(): Long = nowEpochMillis
    }

    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = label,
        )

    private fun layoutWithPlacements(
        personal: AppIdentity,
        work: AppIdentity,
    ): HomeLayout =
        HomeLayoutDefaults.standard().let { defaults ->
            defaults.copy(
                pages =
                    listOf(
                        defaults.selectedPage.copy(
                            items =
                                listOf(
                                    shortcut(id = "home-camera", app = personal),
                                    FolderItem(
                                        id = LauncherItemId("work-folder"),
                                        label = "Work",
                                        items = listOf(shortcut(id = "folder-docs", app = work)),
                                    ),
                                ),
                        ),
                    ),
                dock = DockModel(capacity = 4, items = listOf(shortcut(id = "dock-docs", app = work))),
            )
        }

    private fun shortcut(
        id: String,
        app: AppIdentity,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = app,
            label = app.packageName.value,
        )

    private fun notification(
        key: String,
        packageName: String,
        category: NotificationCategory = NotificationCategory.UNKNOWN,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            category = category,
            postedAtEpochMillis = 1_000L,
        )

    private fun widgetProvider(
        label: String,
        packageName: String = "com.example.${label.lowercase()}",
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(packageName),
                    className = WidgetProviderClassName(".WidgetProvider"),
                ),
            label = label,
            dimensions = WidgetProviderDimensions(minWidthDp = 100, minHeightDp = 50),
        )
}
