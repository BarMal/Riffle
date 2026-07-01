package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellViewModelFactoryTest {
    @Test
    fun factoryCreatedViewModelDoesNotLoadPlatformRepositoriesDuringConstruction() {
        val installedAppRepository = CountingInstalledAppRepository()
        val appVisibilityRepository = CountingAppVisibilityRepository()
        val notificationRepository = CountingNotificationRepository()
        val widgetProviderRepository = CountingWidgetProviderRepository()
        val factory =
            LauncherShellViewModelFactory(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = installedAppRepository,
                appVisibilityRepository = appVisibilityRepository,
                homeLayoutRepository = NoopHomeLayoutRepository,
                launcherSettingsRepository = NoopLauncherSettingsRepository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        notificationRepository = notificationRepository,
                        widgetProviderRepository = widgetProviderRepository,
                    ),
            )

        factory.create(LauncherShellViewModel::class.java)

        assertEquals(0, installedAppRepository.installedAppsReadCount)
        assertEquals(0, appVisibilityRepository.hiddenAppsReadCount)
        assertEquals(0, notificationRepository.activeNotificationsReadCount)
        assertEquals(0, widgetProviderRepository.installedWidgetProvidersReadCount)
    }

    @Test
    fun rejectsUnknownViewModelClass() {
        val factory =
            LauncherShellViewModelFactory(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = CountingInstalledAppRepository(),
                appVisibilityRepository = CountingAppVisibilityRepository(),
                homeLayoutRepository = NoopHomeLayoutRepository,
                launcherSettingsRepository = NoopLauncherSettingsRepository,
            )

        val result = runCatching { factory.create(UnknownViewModel::class.java) }

        assertEquals(true, result.isFailure)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private object NoopHomeLayoutRepository : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = null

        override fun saveHomeLayout(layout: HomeLayout) = Unit
    }

    private object NoopLauncherSettingsRepository : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = null

        override fun saveLauncherSettings(settings: LauncherSettings) = Unit
    }

    private class CountingInstalledAppRepository : InstalledAppRepository {
        var installedAppsReadCount: Int = 0

        override fun installedApps(): List<InstalledApp> {
            installedAppsReadCount += 1
            return emptyList()
        }
    }

    private class CountingAppVisibilityRepository : AppVisibilityRepository {
        var hiddenAppsReadCount: Int = 0

        override fun hiddenAppIdentities(): Set<AppIdentity> {
            hiddenAppsReadCount += 1
            return emptySet()
        }

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }

    private class CountingNotificationRepository : LauncherNotificationRepository {
        var activeNotificationsReadCount: Int = 0

        override fun activeNotifications(): List<LauncherNotification> {
            activeNotificationsReadCount += 1
            return emptyList()
        }
    }

    private class CountingWidgetProviderRepository : InstalledWidgetProviderRepository {
        var installedWidgetProvidersReadCount: Int = 0

        override fun installedWidgetProviders(): List<InstalledWidgetProvider> {
            installedWidgetProvidersReadCount += 1
            return emptyList()
        }
    }

    private class UnknownViewModel : ViewModel()
}
