@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.app.launcher.onboarding.LocalWalkthroughLauncher
import com.riffle.app.launcher.onboarding.OnboardingHost
import com.riffle.app.launcher.onboarding.OnboardingLayer
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.workspace.NoWorkspaceVersion
import com.riffle.app.launcher.workspace.SourceContentLevelsController
import com.riffle.app.launcher.workspace.SourcesHost
import com.riffle.app.launcher.workspace.SourcesLayer
import com.riffle.app.launcher.workspace.SourcesSettingsController
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.settings.resolveLiquidGlass
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass
import kotlinx.coroutines.delay

@Composable
fun LauncherShell(
    viewModel: LauncherShellViewModel,
    appVersionLabel: String,
    appBuildIdentityLabel: String,
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout? = null,
    sources: SourcesHost? = null,
    onboarding: OnboardingHost? = null,
    stream: StreamHost? = null,
    onAction: (LauncherShellAction) -> Unit,
) {
    val storedState by viewModel.state.collectAsState()
    // The one place the platform's animation state meets the stored preference; every surface below
    // reads the resolved `launcherSettings.motion.reducedMotion`.
    val systemReducedMotion = rememberSystemReducedMotion()
    val state =
        remember(storedState, systemReducedMotion) {
            storedState.withSystemReducedMotion(systemReducedMotion)
        }
    // The sources load once; until they have, the dock pull is inert and onboarding waits.
    val workspaceVersion by (sources?.workspaceVersion ?: NoWorkspaceVersion).collectAsState()
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val workspacesLoading = remember(workspaceVersion) { sources != null && !viewModel.workspacesLoaded() }
    val settingsHost = rememberWorkspaceSettingsHost(sources, deviceClass.workspaceLayoutClass())

    HomeReturnEffect(state = state, suppressed = false, onAction = onAction, startPage = viewModel::workspaceStartPage)

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalWorkspaceSettingsHost provides settingsHost,
            LocalHomeSelectionRequests provides viewModel.homeSelectionRequests,
            LocalWalkthroughLauncher provides onboarding?.controller?.let { controller -> controller::rerun },
            LocalHintsHost provides remember(onboarding) { onboarding?.controller?.let(HintsHost::of) },
        ) {
            LauncherShellContent(
                state = state,
                viewModeAvailability = viewModel.viewModeAvailability,
                appInfo = LauncherShellAppInfo(appVersionLabel, appBuildIdentityLabel),
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                onAction = onAction,
                onSetupCardDismissed = viewModel::onSetupCardDismissed,
                onDockEditFeedbackDismissed = viewModel::onDockEditFeedbackDismissed,
                onHomeHeldAnItem = {
                    viewModel.onLauncherSettingsActionSelected(LauncherShellAction.DismissEmptyHomeHint)
                },
                stream = stream,
            )
        }
        // Loads the stored workspaces and the exclusion rules.
        if (sources != null) {
            SourcesLayer(sources, viewModel)
        }
        // The first-run set-up, after the Home-role card (see OnboardingLayer).
        if (onboarding != null) {
            OnboardingLayer(
                onboarding,
                state,
                workspacesLoaded = !workspacesLoading,
                onAction = onAction,
                onSeedHome = viewModel::seedHome,
            )
        }
    }
}

/**
 * The Sources, Privacy, Hidden items and Calendar feeds pages' view of the runtime. Null (so none of them exists) only
 * when the shell has no host.
 */
@Composable
private fun rememberWorkspaceSettingsHost(
    host: SourcesHost?,
    currentLayout: HomeLayoutDeviceClass,
): WorkspaceSettingsHost? =
    if (host == null) {
        null
    } else {
        val runtime = remember(host) { host.runtime() }
        remember(host, runtime, currentLayout) {
            WorkspaceSettingsHost(
                sources =
                    SourcesSettingsController(
                        monitorFor = runtime::sourceStatusMonitor,
                        enablement = runtime.enablement,
                        descriptors = { runtime.registry.descriptors() },
                    ),
                icsFeeds = runtime.icsFeeds,
                globalSearchQuery = runtime.searchQuery,
                contentLevels =
                    SourceContentLevelsController(
                        privacy = runtime.contentPrivacy,
                        // Tasks (Todoist) is not an item source, but its text has a content level like any other.
                        descriptors = {
                            runtime.registry.descriptors() +
                                SourceDescriptor(StreamSourceIds.TASKS, setOf(SourceCapability.PRIVACY_SENSITIVE))
                        },
                    ),
                version = host.workspaceVersion,
                currentLayout = currentLayout,
                onRequestSourceAccess = host.onRequestSourceAccess,
                exclusions =
                    runtime.exclusions?.let { repository ->
                        ExclusionsSettingsController(
                            repository = repository,
                            counterFor = runtime::exclusionMatchCounter,
                            disabledSources = { runtime.enablement.disabledIds() },
                            layoutName = WorkspacesSettingsText::layoutName,
                        )
                    },
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
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout? = null,
    onAction: (LauncherShellAction) -> Unit,
    onSetupCardDismissed: () -> Unit = {},
    onDockEditFeedbackDismissed: () -> Unit = {},
    /** Home holds an item: the empty-Home hint is never shown again (recorded directly, not as an action). */
    onHomeHeldAnItem: () -> Unit = {},
    /** The Stream's content, layouts and actions; null draws Home even when the Stream is the destination. */
    stream: StreamHost? = null,
) {
    val haptics = rememberLauncherHaptics(state.launcherSettings.haptics.feedbackStrength)

    LauncherShellBackHandling(state = state, onAction = onAction)

    PreloadLauncherAppIcons(
        identities = state.appIconPreloadIdentities(),
        iconLoader = appIconLoader,
    )

    RiffleLauncherTheme(
        themeMode = state.launcherSettings.appearance.themeMode,
        themeAccent = state.launcherSettings.appearance.themeAccent,
        themeColors = state.launcherSettings.appearance.themeColors,
        themeCornerStyle = state.launcherSettings.appearance.themeCornerStyle,
        themeTypography = state.launcherSettings.appearance.themeTypography,
        reducedTransparency = state.launcherSettings.appearance.reducedTransparency,
        liquidGlass = state.launcherSettings.resolveLiquidGlass(),
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
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                haptics = haptics,
                onAction = onAction,
                stream = stream,
            )
            if (state.destination == ShellDestination.HOME && state.shouldShowSetupCard) {
                PreviewSetupCard(
                    requestDeclined = state.homeRoleRequestDeclined,
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
            HomeFillOverlay(state = state, onAction = onAction, onHomeHeldAnItem = onHomeHeldAnItem)
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
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

/**
 * System Back returns from any non-Home destination to Home. A focused/expanded AdaptiveStage card
 * unwinds first via its own nested BackHandler, which composes later and therefore takes priority
 * over this one.
 */
@Composable
private fun LauncherShellBackHandling(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    BackHandler(enabled = state.destination != ShellDestination.HOME) {
        // The Library closes back to whatever it opened over: Home or the Stream.
        onAction(
            if (state.destination.isLibrary && state.libraryOverStream) {
                LauncherShellAction.OpenStream
            } else {
                LauncherShellAction.OpenHome
            },
        )
    }
    // S9: Back never leaves Library for another mode (the mode pair retired with workspaces, #1323).
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

@Suppress("LongMethod")
@Composable
private fun LauncherDestination(
    state: LauncherShellState,
    settingsState: SettingsSurfaceState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout?,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
    stream: StreamHost? = null,
) {
    val settingsPageActionRouter = rememberSettingsPageActionRouter(onAction)

    when (state.destination) {
        // The Stream replaces Home and keeps the Dock under it, so both are the one HomeDestination; without a Stream
        // host the destination falls back to Home (Back and the Home button return there either way). The Library is
        // a sheet over Home or the Stream, so it is drawn by the same HomeDestination, with the Dock still under it.
        ShellDestination.HOME, ShellDestination.STREAM, ShellDestination.APP_DRAWER, ShellDestination.SEARCH ->
            HomeDestination(
                state = state,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                haptics = haptics,
                stream = stream,
                streamShown =
                    stream != null &&
                        (
                            state.destination == ShellDestination.STREAM ||
                                (state.destination.isLibrary && state.libraryOverStream)
                        ),
                onAction = settingsPageActionRouter.onAction,
                // The hints wait for the set-up card to be done with.
                hints = LocalHintsHost.current.takeUnless { state.shouldShowSetupCard },
            )

        ShellDestination.SETTINGS ->
            CompositionLocalProvider(
                LocalStreamLayoutRepository provides stream?.layouts,
                LocalTasksSettings provides stream?.taskSettings,
                LocalRecentPlayers provides stream?.recentPlayers,
            ) {
                SettingsSurface(
                    state = settingsState,
                    initialPage = settingsPageActionRouter.initialSettingsPage,
                    initialAnchor = settingsPageActionRouter.initialSettingsAnchor,
                    onAction = settingsPageActionRouter.onAction,
                )
            }
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
