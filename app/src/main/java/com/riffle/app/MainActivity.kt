package com.riffle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.riffle.app.launcher.AndroidHomeRoleGateway
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

    private val requestHomeRole =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            shellViewModel.onHomeRoleStatusChanged(homeRoleGateway.getHomeRoleStatus())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        when (action) {
            LauncherShellAction.RequestDefaultHome -> {
                shellViewModel.onDefaultHomeRequestStarted()
                requestHomeRole.launch(homeRoleGateway.createHomeRoleRequestIntent())
            }

            LauncherShellAction.CompleteFirstRun -> shellViewModel.onFirstRunCompleted()
            LauncherShellAction.OpenHome ->
                shellViewModel.onNavigationActionSelected(ShellNavigationAction.OpenHome)
            LauncherShellAction.OpenAppDrawer ->
                shellViewModel.onNavigationActionSelected(ShellNavigationAction.OpenAppDrawer)
            LauncherShellAction.OpenSearch ->
                shellViewModel.onNavigationActionSelected(ShellNavigationAction.OpenSearch)
            LauncherShellAction.OpenSettings ->
                shellViewModel.onNavigationActionSelected(ShellNavigationAction.OpenSettings)
            LauncherShellAction.EnterHomeEditMode -> shellViewModel.onEnterHomeEditMode()
            LauncherShellAction.ExitHomeEditMode -> shellViewModel.onExitHomeEditMode()
            is LauncherShellAction.LaunchApp -> appLauncher.launch(action.identity)
            is LauncherShellAction.AddAppToHome -> shellViewModel.onAddAppToHome(action.app)
            is LauncherShellAction.RemoveHomeShortcut -> shellViewModel.onHomeShortcutEdited(action)
            is LauncherShellAction.MoveHomeShortcut -> shellViewModel.onHomeShortcutEdited(action)
            is LauncherShellAction.SearchQueryChanged -> shellViewModel.onSearchQueryChanged(action.query)
        }
    }
}
