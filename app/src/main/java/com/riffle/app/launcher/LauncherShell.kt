package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.RiffleProduct
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.WallpaperSource

@Composable
fun LauncherShell(
    viewModel: LauncherShellViewModel,
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LauncherShellContent(
        state = state,
        appIconLoader = appIconLoader,
        onAction = onAction,
    )
}

@Composable
fun LauncherShellContent(
    state: LauncherShellState,
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    BackHandler(enabled = state.destination != ShellDestination.HOME) {
        onAction(LauncherShellAction.OpenHome)
    }

    PreloadLauncherAppIcons(
        identities = state.appIconPreloadIdentities(),
        iconLoader = appIconLoader,
    )

    MaterialTheme {
        val backdropColor =
            if (state.launcherSettings.appearance.wallpaper.source == WallpaperSource.SYSTEM) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backdropColor),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
            ) {
                if (state.shouldShowDefaultHomePrompt) {
                    DefaultHomePrompt(onAction = onAction)
                } else {
                    LauncherDestination(
                        state = state,
                        appIconLoader = appIconLoader,
                        onAction = onAction,
                    )
                }
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
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
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (state.destination) {
        ShellDestination.HOME ->
            StandardHome(
                layout = state.homeLayout,
                installedApps = state.installedApps,
                homeSwipeGestures = state.launcherSettings.gestures.homeSwipe,
                notificationCountsByPackage = state.notificationCountsByPackage,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )

        ShellDestination.APP_DRAWER ->
            AppDrawer(
                apps = state.installedApps,
                homeLayout = state.homeLayout,
                notificationCountsByPackage = state.notificationCountsByPackage,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )

        ShellDestination.SEARCH ->
            SearchSurface(
                query = state.searchQuery,
                results = state.searchResults,
                homeLayout = state.homeLayout,
                notificationCountsByPackage = state.notificationCountsByPackage,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )

        ShellDestination.NOTIFICATIONS ->
            NotificationOverviewSurface(
                groups = state.notificationGroupsByApp,
                categoryCounts = state.notificationCountsByCategory,
                apps = state.installedApps,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )

        ShellDestination.SETTINGS ->
            SettingsSurface(
                settings = state.launcherSettings,
                notificationAccessStatus = state.notificationAccessStatus,
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
                installedApps = samplePreviewApps(),
            ),
        onAction = {},
    )
}

@Preview
@Composable
private fun SearchPreview() {
    LauncherShellContent(
        state =
            LauncherShellState(
                firstRunStatus = FirstRunStatus.COMPLETE,
                destination = ShellDestination.SEARCH,
                searchQuery = "ca",
                searchResults = samplePreviewApps().take(2),
            ),
        onAction = {},
    )
}

private fun samplePreviewApps(): List<InstalledApp> =
    listOf(
        previewApp(label = "Camera", packageName = "com.android.camera"),
        previewApp(label = "Calendar", packageName = "com.android.calendar"),
        previewApp(label = "Maps", packageName = "com.google.android.apps.maps"),
    )

private fun previewApp(
    label: String,
    packageName: String,
): InstalledApp =
    InstalledApp(
        identity =
            AppIdentity(
                packageName = AppPackageName(packageName),
                activityName = AppActivityName(".MainActivity"),
            ),
        label = label,
    )
