package com.riffle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.riffle.app.launcher.AndroidHomeRoleGateway
import com.riffle.app.launcher.AndroidLauncherWallpaperController
import com.riffle.app.launcher.LauncherShell
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.LauncherShellViewModelFactory
import com.riffle.app.launcher.SharedPreferencesAppVisibilityRepository
import com.riffle.app.launcher.SharedPreferencesFirstRunRepository
import com.riffle.app.launcher.SharedPreferencesHomeLayoutRepository
import com.riffle.app.launcher.SharedPreferencesLauncherSettingsRepository
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.apps.AndroidAppShortcutRepository
import com.riffle.app.launcher.apps.AndroidPackageChangeObserver
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.app.launcher.handleNotificationAction
import com.riffle.app.launcher.handleSettingsAction
import com.riffle.app.launcher.isHomePageEditAction
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway
import com.riffle.app.launcher.notifications.AndroidNotificationDismissalGateway
import com.riffle.app.launcher.notifications.SharedPreferencesActiveNotificationRepository
import com.riffle.core.domain.launcher.ShellNavigationAction

class MainActivity : ComponentActivity() {
    private val shellViewModel: LauncherShellViewModel by viewModels {
        LauncherShellViewModelFactory(
            firstRunRepository = SharedPreferencesFirstRunRepository(this),
            installedAppRepository =
                PackageManagerInstalledAppRepository(
                    packageManager = packageManager,
                    appShortcutRepository = AndroidAppShortcutRepository(this),
                ),
            appVisibilityRepository = SharedPreferencesAppVisibilityRepository(this),
            homeLayoutRepository = SharedPreferencesHomeLayoutRepository(this),
            launcherSettingsRepository = SharedPreferencesLauncherSettingsRepository(this),
            notificationRepository = activeNotificationRepository,
        )
    }
    private val homeRoleGateway by lazy { AndroidHomeRoleGateway(this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val appIconLoader by lazy { PackageManagerAppIconLoader(packageManager) }
    private val packageChangeObserver by lazy {
        AndroidPackageChangeObserver(this) {
            runOnUiThread {
                shellViewModel.refreshInstalledApps()
            }
        }
    }
    private val wallpaperController by lazy { AndroidLauncherWallpaperController(window) }
    private val notificationAccessGateway by lazy { AndroidNotificationAccessGateway(this) }
    private val activeNotificationRepository by lazy { SharedPreferencesActiveNotificationRepository(this) }

    private val requestHomeRole =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            refreshPlatformStatuses()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallpaperController.showSystemWallpaper()
        activeNotificationRepository.observeActiveNotifications {
            runOnUiThread {
                shellViewModel.refreshInstalledApps()
            }
        }
        lifecycle.addObserver(packageChangeObserver)

        setContent {
            LauncherShell(
                viewModel = shellViewModel,
                appIconLoader = appIconLoader,
                onAction = ::handleAction,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        shellViewModel.refreshInstalledApps()
        refreshPlatformStatuses()
    }

    private fun refreshPlatformStatuses() {
        shellViewModel.onHomeRoleStatusChanged(
            homeRoleStatus = homeRoleGateway.getHomeRoleStatus(),
            notificationAccessStatus = notificationAccessGateway.getNotificationAccessStatus(),
        )
    }

    private fun handleAction(action: LauncherShellAction) {
        val handled =
            handleFirstRunAction(action) ||
                handleNavigationAction(action) ||
                handleHomePageAction(action) ||
                handleHomeShortcutAction(action) ||
                handleDockAction(action) ||
                action.handleNotificationAction(
                    viewModel = shellViewModel,
                    notificationDismissalGateway = AndroidNotificationDismissalGateway,
                ) ||
                action.handleSettingsAction(
                    viewModel = shellViewModel,
                    notificationAccessGateway = notificationAccessGateway,
                    openIntent = ::startActivity,
                )

        if (!handled) {
            handleAppAction(action)
        }
    }

    private fun handleFirstRunAction(action: LauncherShellAction): Boolean =
        when (action) {
            LauncherShellAction.RequestDefaultHome -> {
                shellViewModel.onDefaultHomeRequestStarted()
                requestHomeRole.launch(homeRoleGateway.createHomeRoleRequestIntent())
                true
            }

            else -> false
        }

    private fun handleNavigationAction(action: LauncherShellAction): Boolean =
        action.navigationAction()
            ?.also(shellViewModel::onNavigationActionSelected)
            ?.let { true }
            ?: false

    private fun handleHomePageAction(action: LauncherShellAction): Boolean =
        action.isHomePageEditAction()
            .also { isHomePageEditAction ->
                if (isHomePageEditAction) {
                    shellViewModel.onHomePageEdited(action)
                }
            }

    private fun handleHomeShortcutAction(action: LauncherShellAction): Boolean =
        when (action) {
            is LauncherShellAction.RemoveHomeShortcut,
            is LauncherShellAction.CreateHomeFolder,
            is LauncherShellAction.RenameHomeFolder,
            is LauncherShellAction.AddAppToFolder,
            is LauncherShellAction.RemoveAppFromFolder,
            is LauncherShellAction.MoveHomeShortcut,
            is LauncherShellAction.AddAppShortcutToHome,
            -> {
                shellViewModel.onHomeShortcutEdited(action)
                true
            }

            else -> false
        }

    private fun handleDockAction(action: LauncherShellAction): Boolean =
        when (action) {
            is LauncherShellAction.AddAppToDock,
            is LauncherShellAction.SelectDockEnabled,
            is LauncherShellAction.SelectDockCapacity,
            is LauncherShellAction.SelectDockIconSize,
            is LauncherShellAction.SelectDockBackgroundAlpha,
            is LauncherShellAction.SelectDockItemSpacing,
            is LauncherShellAction.RemoveDockShortcut,
            is LauncherShellAction.MoveDockShortcut,
            -> {
                shellViewModel.onDockEdited(action)
                true
            }

            else -> false
        }

    private fun handleAppAction(action: LauncherShellAction) {
        when (action) {
            is LauncherShellAction.LaunchApp -> appLauncher.launch(action.identity)
            is LauncherShellAction.LaunchAppShortcut -> appLauncher.launchShortcut(action.shortcut)
            is LauncherShellAction.OpenAppInfo -> appLauncher.openAppInfo(action.identity)
            is LauncherShellAction.AddAppToHome -> shellViewModel.onAddAppToHome(action.app)
            is LauncherShellAction.HideApp,
            is LauncherShellAction.UnhideApp,
            is LauncherShellAction.AppDrawerQueryChanged,
            is LauncherShellAction.AppDrawerProfileFilterSelected,
            is LauncherShellAction.SearchQueryChanged,
            is LauncherShellAction.SearchProfileFilterSelected,
            -> shellViewModel.onAppActionSelected(action)

            else -> Unit
        }
    }
}

private fun LauncherShellAction.navigationAction(): ShellNavigationAction? =
    when (this) {
        LauncherShellAction.OpenHome -> ShellNavigationAction.OpenHome
        LauncherShellAction.OpenAppDrawer -> ShellNavigationAction.OpenAppDrawer
        LauncherShellAction.OpenSearch -> ShellNavigationAction.OpenSearch
        LauncherShellAction.OpenNotifications -> ShellNavigationAction.OpenNotifications
        LauncherShellAction.OpenSettings -> ShellNavigationAction.OpenSettings
        else -> null
    }
