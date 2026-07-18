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
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.search.LauncherSearchResult

@Composable
fun LauncherShell(
    viewModel: LauncherShellViewModel,
    appVersionLabel: String,
    appBuildIdentityLabel: String,
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    onAction: (LauncherShellAction) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LauncherShellContent(
            state = state,
            viewModeAvailability = viewModel.viewModeAvailability,
            appVersionLabel = appVersionLabel,
            appBuildIdentityLabel = appBuildIdentityLabel,
            appIconLoader = appIconLoader,
            widgetRenderers = widgetRenderers,
            onAction = onAction,
        )
    }
}

@Composable
fun LauncherShellContent(
    state: LauncherShellState,
    viewModeAvailability: LauncherViewModeAvailability = defaultLauncherViewModeAvailability(),
    appVersionLabel: String = "",
    appBuildIdentityLabel: String = "",
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    onAction: (LauncherShellAction) -> Unit,
) {
    val haptics = rememberLauncherHaptics(state.launcherSettings.haptics.feedbackStrength)

    BackHandler(enabled = state.destination != ShellDestination.HOME) {
        onAction(LauncherShellAction.OpenHome)
    }

    PreloadLauncherAppIcons(
        identities = state.appIconPreloadIdentities(),
        iconLoader = appIconLoader,
    )

    RiffleLauncherTheme(
        themeMode = state.launcherSettings.appearance.themeMode,
        themePreset = state.launcherSettings.appearance.themePreset,
        themeAccent = state.launcherSettings.appearance.themeAccent,
        customTheme = state.launcherSettings.appearance.customTheme,
    ) {
        val usesSystemWallpaper = state.launcherSettings.appearance.wallpaper.source == WallpaperSource.SYSTEM
        val rootModifier =
            if (usesSystemWallpaper) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            }

        Box(
            modifier = rootModifier,
        ) {
            if (state.shouldShowDefaultHomePrompt) {
                DefaultHomePrompt(onAction = onAction)
            } else {
                LauncherDestination(
                    state = state,
                    settingsState =
                        state.settingsSurfaceState(
                            appVersionLabel = appVersionLabel,
                            appBuildIdentityLabel = appBuildIdentityLabel,
                            viewModeAvailability = viewModeAvailability,
                        ),
                    appIconLoader = appIconLoader,
                    widgetRenderers = widgetRenderers,
                    haptics = haptics,
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

@Suppress("LongMethod")
@Composable
private fun LauncherDestination(
    state: LauncherShellState,
    settingsState: SettingsSurfaceState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
    val settingsPageActionRouter = rememberSettingsPageActionRouter(onAction)

    when (state.destination) {
        ShellDestination.HOME ->
            HomeDestination(
                state = state,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                onAction = settingsPageActionRouter.onAction,
            )

        ShellDestination.APP_DRAWER ->
            AppDrawer(
                query = state.appDrawerQuery,
                profileFilter = state.appDrawerProfileFilter,
                installedApps = state.installedApps,
                apps = state.appDrawerApps,
                appListContext =
                    AppListContext(
                        homeLayout = state.homeLayout,
                        overlayDock = state.launcherSettings.overlayDock,
                        notificationGroupsByApp = state.notificationGroupsByApp,
                        appShortcutsByApp = state.appShortcutsByApp,
                        appIconLoader = appIconLoader,
                        haptics = haptics,
                        onAction = settingsPageActionRouter.onAction,
                    ),
                onAction = settingsPageActionRouter.onAction,
            )

        ShellDestination.SEARCH ->
            SearchSurface(
                state =
                    SearchSurfaceState(
                        query = state.searchQuery,
                        filters = state.searchFilters,
                        installedApps = state.installedApps,
                        results = state.searchResults,
                        shortcutResults = state.searchShortcutResults,
                        settingsResults = state.searchSettingsResults,
                        homeLayout = state.homeLayout,
                    ),
                appListContext =
                    AppListContext(
                        homeLayout = state.homeLayout,
                        overlayDock = state.launcherSettings.overlayDock,
                        notificationGroupsByApp = state.notificationGroupsByApp,
                        appShortcutsByApp = state.appShortcutsByApp,
                        appIconLoader = appIconLoader,
                        haptics = haptics,
                        onAction = settingsPageActionRouter.onAction,
                    ),
                onAction = settingsPageActionRouter.onAction,
            )

        ShellDestination.NOTIFICATIONS ->
            NotificationOverviewSurface(
                groups = state.notificationGroupsByApp,
                categoryCounts = state.notificationCountsByCategory,
                notificationAccessStatus = state.notificationAccessStatus,
                presentation =
                    NotificationOverviewPresentation(
                        apps = state.installedApps,
                        appIconLoader = appIconLoader,
                        reducedMotion = state.launcherSettings.motion.reducedMotion,
                    ),
                onAction = settingsPageActionRouter.onAction,
            )

        ShellDestination.SETTINGS ->
            SettingsSurface(
                state = settingsState,
                initialPage = settingsPageActionRouter.initialSettingsPage,
                onAction = settingsPageActionRouter.onAction,
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
                appDrawerApps = samplePreviewApps(),
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
                searchSettingsResults =
                    settingsLauncherSearchEntries()
                        .filter { entry -> entry.title == "Appearance" }
                        .map { entry -> LauncherSearchResult.Setting(entry) },
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
