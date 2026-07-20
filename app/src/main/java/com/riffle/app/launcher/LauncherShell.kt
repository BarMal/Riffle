@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import kotlinx.coroutines.delay

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
            appInfo = LauncherShellAppInfo(appVersionLabel, appBuildIdentityLabel),
            appIconLoader = appIconLoader,
            widgetRenderers = widgetRenderers,
            onAction = onAction,
            onSetupCardDismissed = viewModel::onSetupCardDismissed,
            onDockEditFeedbackDismissed = viewModel::onDockEditFeedbackDismissed,
        )
    }
}

@Composable
fun LauncherShellContent(
    state: LauncherShellState,
    viewModeAvailability: LauncherViewModeAvailability = defaultLauncherViewModeAvailability(),
    appInfo: LauncherShellAppInfo = LauncherShellAppInfo(),
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    onAction: (LauncherShellAction) -> Unit,
    onSetupCardDismissed: () -> Unit = {},
    onDockEditFeedbackDismissed: () -> Unit = {},
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
        themeColors = state.launcherSettings.appearance.themeColors,
        themeCornerStyle = state.launcherSettings.appearance.themeCornerStyle,
        themeTypography = state.launcherSettings.appearance.themeTypography,
    ) {
        val usesSystemWallpaper =
            state.launcherSettings.appearance.wallpaper.source == WallpaperSource.SYSTEM &&
                state.launcherSettings.appearance.themeColors.backgroundArgb == null
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
            LauncherDestination(
                state = state,
                settingsState =
                    state.settingsSurfaceState(
                        appVersionLabel = appInfo.versionLabel,
                        appBuildIdentityLabel = appInfo.buildIdentityLabel,
                        viewModeAvailability = viewModeAvailability,
                    ),
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                onAction = onAction,
            )
            if (state.destination == ShellDestination.HOME && state.shouldShowSetupCard) {
                PreviewSetupCard(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(16.dp),
                    homeRoleStatus = state.homeRoleStatus,
                    firstRunStatus = state.firstRunStatus,
                    onSetHome = { onAction(LauncherShellAction.RequestDefaultHome) },
                    onDismiss = onSetupCardDismissed,
                )
            }
            state.dockEditRejectionReason?.let { reason ->
                DockEditRejectionMessage(
                    reason = reason,
                    onDismissRequest = onDockEditFeedbackDismissed,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DockEditRejectionMessage(
    reason: DockEditRejectionReason,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(reason) {
        delay(DOCK_EDIT_REJECTION_TIMEOUT_MILLIS)
        onDismissRequest()
    }
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = dockEditRejectionMessage(reason),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismissRequest) { Text("Dismiss") }
        }
    }
}

private const val DOCK_EDIT_REJECTION_TIMEOUT_MILLIS = 5_000L

internal fun dockEditRejectionMessage(reason: DockEditRejectionReason): String =
    when (reason) {
        DockEditRejectionReason.NO_AVAILABLE_SLOT -> "Dock is full. Remove an item or increase capacity."
        DockEditRejectionReason.DOCK_DISABLED -> "Enable Dock before moving items."
        DockEditRejectionReason.UNSUPPORTED_ITEM -> "Only apps and folders can move between Home and Dock."
        DockEditRejectionReason.GENERATED_HOME_PAGE -> "Choose a standard Home page."
        DockEditRejectionReason.NO_AVAILABLE_HOME_CELL -> "The selected Home page has no available cells."
        DockEditRejectionReason.INVALID_HOME_PLACEMENT -> "That Home cell is unavailable. Choose another cell."
        DockEditRejectionReason.HOME_PAGE_NOT_FOUND -> "That Home page is no longer available."
        else -> "Could not update Dock. Try again."
    }

data class LauncherShellAppInfo(
    val versionLabel: String = "",
    val buildIdentityLabel: String = "",
)

@Composable
private fun PreviewSetupCard(
    modifier: Modifier = Modifier,
    homeRoleStatus: HomeRoleStatus,
    firstRunStatus: FirstRunStatus,
    onSetHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        Surface(
            modifier = Modifier.heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val presentation =
                    if (firstRunStatus == FirstRunStatus.REQUESTING_HOME_ROLE) {
                        PreviewHomeSetupPresentation(
                            statusMessage = "Checking whether Riffle is your Home app.",
                            detailMessage =
                                "Riffle is checking the result of your Home app request. " +
                                    "You can keep exploring while this updates.",
                        )
                    } else {
                        when (homeRoleStatus) {
                            HomeRoleStatus.DEFAULT_HOME ->
                                PreviewHomeSetupPresentation(
                                    statusMessage = "Riffle is your Home app.",
                                    actionLabel = "Open Home settings",
                                )
                            HomeRoleStatus.NOT_DEFAULT_HOME ->
                                PreviewHomeSetupPresentation(
                                    statusMessage = "Riffle is not your Home app yet.",
                                    actionLabel = "Set as Home app",
                                )
                            HomeRoleStatus.UNKNOWN ->
                                PreviewHomeSetupPresentation(
                                    statusMessage = "Home app status is unavailable right now.",
                                    actionLabel = "Try again",
                                )
                        }
                    }
                Text(
                    text = "Set up Riffle",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text =
                        presentation.detailMessage
                            ?: "Explore your apps and settings first. While previewing, pressing your device " +
                            "Home button may return to your current default launcher.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PreviewSetupStatusRow(
                    label = "Home app",
                    status = presentation.statusMessage,
                )
                Text(
                    text = "Optional features ask for access only when you turn them on.",
                    style = MaterialTheme.typography.bodySmall,
                )
                presentation.actionLabel?.let { actionLabel ->
                    Button(onClick = onSetHome) {
                        Text(text = actionLabel)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "Not now")
                }
            }
        }
    }
}

@Composable
private fun PreviewSetupStatusRow(
    label: String,
    status: String,
) {
    Column(
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "$label: $status"
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = status, style = MaterialTheme.typography.bodySmall)
    }
}

private data class PreviewHomeSetupPresentation(
    val statusMessage: String,
    val actionLabel: String? = null,
    val detailMessage: String? = null,
)

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
                        resultPresentation = state.launcherSettings.search.resultPresentation,
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
