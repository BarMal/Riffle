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
import com.riffle.app.launcher.SharedPreferencesFirstRunRepository
import com.riffle.app.launcher.SharedPreferencesHomeLayoutRepository
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.core.domain.launcher.ShellNavigationAction

class MainActivity : ComponentActivity() {
    private val shellViewModel: LauncherShellViewModel by viewModels {
        LauncherShellViewModelFactory(
            firstRunRepository = SharedPreferencesFirstRunRepository(this),
            installedAppRepository = PackageManagerInstalledAppRepository(packageManager),
            homeLayoutRepository = SharedPreferencesHomeLayoutRepository(this),
        )
    }
    private val homeRoleGateway by lazy { AndroidHomeRoleGateway(this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val appIconLoader by lazy { PackageManagerAppIconLoader(packageManager) }
    private val wallpaperController by lazy { AndroidLauncherWallpaperController(window) }

    private val requestHomeRole =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            shellViewModel.onHomeRoleStatusChanged(homeRoleGateway.getHomeRoleStatus())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallpaperController.showSystemWallpaper()

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
        shellViewModel.onHomeRoleStatusChanged(homeRoleGateway.getHomeRoleStatus())
    }

    private fun handleAction(action: LauncherShellAction) {
        val handled =
            handleFirstRunAction(action) ||
                handleNavigationAction(action) ||
                handleHomePageAction(action) ||
                handleHomeShortcutAction(action) ||
                handleDockAction(action)

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

            LauncherShellAction.CompleteFirstRun -> {
                shellViewModel.onFirstRunCompleted()
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
        when (action) {
            LauncherShellAction.EnterHomeEditMode,
            LauncherShellAction.ExitHomeEditMode,
            LauncherShellAction.AddHomePage,
            LauncherShellAction.SelectPreviousHomePage,
            LauncherShellAction.SelectNextHomePage,
            LauncherShellAction.MoveSelectedHomePageLeft,
            LauncherShellAction.MoveSelectedHomePageRight,
            LauncherShellAction.DeleteSelectedHomePage,
            -> {
                shellViewModel.onHomePageEdited(action)
                true
            }

            else -> false
        }

    private fun handleHomeShortcutAction(action: LauncherShellAction): Boolean =
        when (action) {
            is LauncherShellAction.RemoveHomeShortcut,
            is LauncherShellAction.MoveHomeShortcut,
            -> {
                shellViewModel.onHomeShortcutEdited(action)
                true
            }

            else -> false
        }

    private fun handleDockAction(action: LauncherShellAction): Boolean =
        when (action) {
            is LauncherShellAction.AddAppToDock,
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
            is LauncherShellAction.AddAppToHome -> shellViewModel.onAddAppToHome(action.app)
            is LauncherShellAction.SearchQueryChanged -> shellViewModel.onSearchQueryChanged(action.query)
            else -> Unit
        }
    }
}

private fun LauncherShellAction.navigationAction(): ShellNavigationAction? =
    when (this) {
        LauncherShellAction.OpenHome -> ShellNavigationAction.OpenHome
        LauncherShellAction.OpenAppDrawer -> ShellNavigationAction.OpenAppDrawer
        LauncherShellAction.OpenSearch -> ShellNavigationAction.OpenSearch
        LauncherShellAction.OpenSettings -> ShellNavigationAction.OpenSettings
        else -> null
    }
