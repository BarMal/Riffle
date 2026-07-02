package com.riffle.app

import android.app.Activity
import com.riffle.app.launcher.AndroidHomeLayoutDeviceClassObserver
import com.riffle.app.launcher.AndroidHomeRoleGateway
import com.riffle.app.launcher.AndroidLauncherWallpaperController
import com.riffle.app.launcher.AndroidWidgetAddWindowSizeProvider
import com.riffle.app.launcher.DataStoreHomeLayoutRepository
import com.riffle.app.launcher.DataStoreLauncherSettingsRepository
import com.riffle.app.launcher.LauncherBackupDocumentGateway
import com.riffle.app.launcher.LauncherBackupDocumentHandler
import com.riffle.app.launcher.LauncherBackupExportCoordinator
import com.riffle.app.launcher.LauncherBackupImportCoordinator
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellPlatformDependencies
import com.riffle.app.launcher.LauncherWidgetAddRequestHandler
import com.riffle.app.launcher.SharedPreferencesAppVisibilityRepository
import com.riffle.app.launcher.SharedPreferencesFirstRunRepository
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.apps.AndroidAppShortcutRepository
import com.riffle.app.launcher.apps.AndroidPackageChangeObserver
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.app.launcher.homeLayoutDeviceClassFromConfiguration
import com.riffle.app.launcher.notifications.ActiveNotificationRefreshCoordinator
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway
import com.riffle.app.launcher.notifications.DataStoreActiveNotificationRepository
import com.riffle.app.launcher.overlay.AndroidOverlayDockPermissionGateway
import com.riffle.app.launcher.overlay.AndroidOverlayDockServiceController
import com.riffle.app.launcher.widgets.AndroidInstalledWidgetProviderRepository
import com.riffle.app.launcher.widgets.AndroidWidgetHostGateway
import com.riffle.app.launcher.widgets.WidgetBindingCoordinator
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.GridDimensions

internal class MainActivityDependencies(
    private val activity: Activity,
) {
    val homeLayoutRepository by lazy { DataStoreHomeLayoutRepository(activity) }
    val launcherSettingsRepository by lazy { DataStoreLauncherSettingsRepository(activity) }
    val firstRunRepository by lazy { SharedPreferencesFirstRunRepository(activity) }
    val installedAppRepository by lazy {
        PackageManagerInstalledAppRepository(
            context = activity,
            appShortcutRepository = AndroidAppShortcutRepository(activity),
        )
    }
    val appVisibilityRepository by lazy { SharedPreferencesAppVisibilityRepository(activity) }
    val homeRoleGateway by lazy { AndroidHomeRoleGateway(activity) }
    val appLauncher by lazy { AndroidAppLauncher(activity) }
    val appIconLoader by lazy { PackageManagerAppIconLoader(activity.packageManager) }
    val wallpaperController by lazy { AndroidLauncherWallpaperController(activity.window) }
    val notificationAccessGateway by lazy { AndroidNotificationAccessGateway(activity) }
    val overlayDockPermissionGateway by lazy { AndroidOverlayDockPermissionGateway(activity) }
    val overlayDockServiceController by lazy { AndroidOverlayDockServiceController(activity) }
    val homeLayoutDeviceClassObserver by lazy { AndroidHomeLayoutDeviceClassObserver(activity) }
    val activeNotificationRepository by lazy { DataStoreActiveNotificationRepository(activity) }
    val widgetHostGateway by lazy { AndroidWidgetHostGateway(activity) }
    val widgetBindingCoordinator by lazy { WidgetBindingCoordinator(widgetHostGateway) }
    val widgetAddWindowSizeProvider by lazy { AndroidWidgetAddWindowSizeProvider(activity) }

    fun platformDependencies(): LauncherShellPlatformDependencies =
        LauncherShellPlatformDependencies(
            notificationRepository = activeNotificationRepository,
            widgetProviderRepository = AndroidInstalledWidgetProviderRepository(activity),
            initialHomeLayoutDeviceClass =
                homeLayoutDeviceClassFromConfiguration(
                    screenWidthDp = activity.resources.configuration.screenWidthDp,
                    screenHeightDp = activity.resources.configuration.screenHeightDp,
                ),
        )

    fun packageChangeObserver(refreshInstalledApps: () -> Unit): AndroidPackageChangeObserver =
        AndroidPackageChangeObserver(activity) {
            activity.runOnUiThread(refreshInstalledApps)
        }

    fun activeNotificationRefreshCoordinator(refreshNotifications: () -> Unit): ActiveNotificationRefreshCoordinator =
        ActiveNotificationRefreshCoordinator(
            notificationChangeSource = activeNotificationRepository,
            dispatchOnMainThread = { action -> activity.runOnUiThread { action() } },
            refreshNotifications = refreshNotifications,
        )

    fun backupDocumentHandler(currentState: () -> LauncherShellState): LauncherBackupDocumentHandler =
        LauncherBackupDocumentHandler(
            exportCoordinator =
                LauncherBackupExportCoordinator(
                    homeLayoutRepository = homeLayoutRepository,
                    currentState = currentState,
                ),
            importCoordinator = LauncherBackupImportCoordinator(),
            documentGateway = LauncherBackupDocumentGateway(),
        )

    fun widgetAddRequestHandler(
        selectedGrid: () -> GridDimensions,
        completeWidgetAdd: (LauncherShellAction.AddHostedWidgetToHome) -> String?,
    ): LauncherWidgetAddRequestHandler =
        LauncherWidgetAddRequestHandler(
            widgetBindingCoordinator = widgetBindingCoordinator,
            selectedGrid = selectedGrid,
            windowSize = widgetAddWindowSizeProvider::windowSize,
            completeWidgetAdd = completeWidgetAdd,
        )
}
