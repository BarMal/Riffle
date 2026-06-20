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
import com.riffle.core.domain.launcher.ShellNavigationAction

class MainActivity : ComponentActivity() {
    private val shellViewModel: LauncherShellViewModel by viewModels {
        LauncherShellViewModelFactory(
            firstRunRepository = SharedPreferencesFirstRunRepository(this),
        )
    }
    private val homeRoleGateway by lazy { AndroidHomeRoleGateway(this) }

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
                onAction = ::handleAction,
            )
        }
    }

    override fun onResume() {
        super.onResume()
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
        }
    }
}
