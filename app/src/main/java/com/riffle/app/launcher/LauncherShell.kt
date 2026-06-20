package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.RiffleProduct
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination

@Composable
fun LauncherShell(
    viewModel: LauncherShellViewModel,
    onAction: (LauncherShellAction) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LauncherShellContent(
        state = state,
        onAction = onAction,
    )
}

@Composable
fun LauncherShellContent(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (state.shouldShowDefaultHomePrompt) {
                DefaultHomePrompt(onAction = onAction)
            } else {
                LauncherDestination(
                    state = state,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun DefaultHomePrompt(onAction: (LauncherShellAction) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = RiffleProduct.DISPLAY_NAME,
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Choose Riffle as your default home app to continue.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onAction(LauncherShellAction.RequestDefaultHome) }) {
            Text(text = "Set as default")
        }
    }
}

@Composable
private fun LauncherDestination(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (state.destination) {
        ShellDestination.HOME ->
            StandardHome(
                layout = state.homeLayout,
                onAction = onAction,
            )

        ShellDestination.APP_DRAWER ->
            AppDrawer(
                onAction = onAction,
            )

        ShellDestination.SEARCH ->
            SearchSurface(
                onAction = onAction,
            )

        ShellDestination.SETTINGS ->
            SettingsSurface(
                onAction = onAction,
            )
    }
}

@Preview
@Composable
private fun DefaultHomePromptPreview() {
    LauncherShellContent(
        state = LauncherShellState(homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME),
        onAction = {},
    )
}

@Preview
@Composable
private fun EmptyHomePreview() {
    LauncherShellContent(
        state = LauncherShellState(firstRunStatus = FirstRunStatus.COMPLETE),
        onAction = {},
    )
}

@Preview
@Composable
private fun AppDrawerPreview() {
    LauncherShellContent(
        state =
            LauncherShellState(
                firstRunStatus = FirstRunStatus.COMPLETE,
                destination = ShellDestination.APP_DRAWER,
            ),
        onAction = {},
    )
}
