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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.app.launcher.workspace.LensesSettingsController
import com.riffle.app.launcher.workspace.LocalWorkspaceHomePages
import com.riffle.app.launcher.workspace.NoWorkspaceVersion
import com.riffle.app.launcher.workspace.PreviewHideHost
import com.riffle.app.launcher.workspace.SourceContentLevelsController
import com.riffle.app.launcher.workspace.SourcesSettingsController
import com.riffle.app.launcher.workspace.WorkspaceHomeState
import com.riffle.app.launcher.workspace.WorkspaceLayer
import com.riffle.app.launcher.workspace.WorkspacePreviewHost
import com.riffle.app.launcher.workspace.WorkspacesSettingsController
import com.riffle.app.launcher.workspace.rememberWorkspaceHome
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.settings.resolveLiquidGlass
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceSkin
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuEffect
import com.riffle.core.domain.launcher.workspace.withWorkspaceHome
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
    workspacePreview: WorkspacePreviewHost? = null,
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
    var adaptiveStageContext by rememberSaveable(stateSaver = AdaptiveStageInteractionContextSaver) {
        mutableStateOf(AdaptiveStageInteractionContext())
    }
    // S9: workspaces are the home. Absent only while no workspaces are loaded (or the shell has no host).
    val menuState by viewModel.workspaceMenu.state.collectAsState()
    val workspaceVersion by (workspacePreview?.workspaceVersion ?: NoWorkspaceVersion).collectAsState()
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val menuAvailable = remember(workspaceVersion, deviceClass) { viewModel.workspaceMenu.isAvailable() }
    // While the workspaces load the dock pull is inert (it must not switch modes; the menu is not there yet).
    val workspacesLoading = remember(workspaceVersion) { workspacePreview != null && !viewModel.workspacesLoaded() }
    val workspaceMenu =
        if (menuAvailable) {
            WorkspaceMenuHost(state = menuState, onAction = viewModel.workspaceMenu::dispatch)
        } else {
            null
        }
    val workspaceHome = rememberWorkspaceHome(workspacePreview, state)
    val poolHomeSetting = rememberPoolHomeSetting(workspacePreview)
    val settingsHost =
        rememberWorkspaceSettingsHost(workspacePreview, deviceClass.workspaceLayoutClass(), viewModel)

    HomeReturnEffect(state = state, suppressed = false, onAction = onAction, startPage = viewModel::workspaceStartPage)
    WorkspaceMenuEffects(viewModel, workspacePreview, workspaceHome, state, onAction)

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalPoolHomeSetting provides poolHomeSetting,
            LocalWorkspaceSettingsHost provides settingsHost,
            LocalWorkspaceHomePages provides workspaceHome?.pages,
            LocalHomeSelectionRequests provides viewModel.homeSelectionRequests,
            LocalDockModePullEnabled provides !workspacesLoading,
        ) {
            PreviewHideHost(workspaceHome?.hideController, deviceClass.workspaceLayoutClass(), 0.dp) {
                LauncherShellContent(
                    state = state,
                    viewModeAvailability = viewModel.viewModeAvailability,
                    appInfo = LauncherShellAppInfo(appVersionLabel, appBuildIdentityLabel),
                    appIconLoader = appIconLoader,
                    widgetRenderers = widgetRenderers,
                    adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                    adaptiveStageContext = adaptiveStageContext,
                    onAdaptiveStageContextChanged = { adaptiveStageContext = it },
                    workspaceMenu = workspaceMenu,
                    onAction = onAction,
                    onSetupCardDismissed = viewModel::onSetupCardDismissed,
                    onDockEditFeedbackDismissed = viewModel::onDockEditFeedbackDismissed,
                    workspace = workspaceHome?.workspace,
                )
            }
        }
        // Loads the workspaces and draws the workspace editor over the home while it is open.
        if (workspacePreview != null) {
            WorkspaceLayer(workspacePreview, state, viewModel)
        }
    }
}

/**
 * The workspace menu's choices the shell carries out on the home (S9): a jump selects the placed page (or scrolls to
 * the lens page) it names, the Finder entry opens the app drawer (the Finder of the default Nova workspace is the
 * drawer), and Edit opens the editor. Switching workspace was already saved by the menu; the view model follows it.
 */
@Composable
private fun WorkspaceMenuEffects(
    viewModel: LauncherShellViewModel,
    host: WorkspacePreviewHost?,
    home: WorkspaceHomeState?,
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val latestHome by rememberUpdatedState(home)
    val latestState by rememberUpdatedState(state)
    val latestOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(viewModel, host) {
        viewModel.workspaceMenuEffects.collect { effect ->
            when (effect) {
                is WorkspaceMenuEffect.NavigateToPage -> {
                    val containerId = effect.key.containerId
                    val target = latestHome?.navigate(containerId, latestState.homeLayout)
                    if (target != null || latestHome?.isExtraPage(containerId) == true) {
                        latestOnAction(LauncherShellAction.OpenHome)
                    }
                    target?.let { page -> latestOnAction(LauncherShellAction.SelectHomePage(page)) }
                }
                is WorkspaceMenuEffect.OpenFinderPage -> latestOnAction(LauncherShellAction.OpenAppDrawer)
                is WorkspaceMenuEffect.EditWorkspace -> host?.controller?.onEffect(effect)
                is WorkspaceMenuEffect.SetActiveWorkspace -> Unit
            }
        }
    }
}

/** The Developer section's "Home from placed items" switch; null when the shell has no host or switch. */
@Composable
private fun rememberPoolHomeSetting(host: WorkspacePreviewHost?): PoolHomeSetting? {
    val switch = host?.poolHome ?: return null
    val enabled by switch.enabled.collectAsState()
    return remember(switch, enabled) { PoolHomeSetting(enabled, switch.activeThisProcess, switch::setEnabled) }
}

/**
 * The Workspaces and Sources settings pages' view of the runtime. Null (so neither page exists) only when the shell
 * has no host; the runtime it reads is the one the home and the editor share.
 */
@Composable
private fun rememberWorkspaceSettingsHost(
    host: WorkspacePreviewHost?,
    currentLayout: HomeLayoutDeviceClass,
    viewModel: LauncherShellViewModel,
): WorkspaceSettingsHost? =
    if (host == null) {
        null
    } else {
        val runtime = remember(host) { host.runtime() }
        // Not keyed by the layout: a rotation changes it, and the lens being built must survive that.
        val lenses =
            remember(host, runtime) {
                LensesSettingsController(
                    repository = runtime.repository,
                    descriptors = { runtime.registry.descriptors() },
                    onChanged = { viewModel.workspaceMenu.refresh() },
                )
            }
        remember(host, runtime, currentLayout) {
            WorkspaceSettingsHost(
                workspaces =
                    WorkspacesSettingsController(
                        repository = runtime.repository,
                        onChanged = { viewModel.workspaceMenu.refresh() },
                    ),
                sources =
                    SourcesSettingsController(
                        monitorFor = runtime::sourceStatusMonitor,
                        enablement = runtime.enablement,
                        descriptors = { runtime.registry.descriptors() },
                    ),
                icsFeeds = runtime.icsFeeds,
                lenses = lenses,
                previewServices = { reducedMotion -> runtime.services(reducedMotion) },
                globalSearchQuery = runtime.searchQuery,
                contentLevels =
                    SourceContentLevelsController(
                        privacy = runtime.contentPrivacy,
                        descriptors = { runtime.registry.descriptors() },
                    ),
                version = host.workspaceVersion,
                currentLayout = currentLayout,
                onEdit = { id -> host.controller.onEffect(WorkspaceMenuEffect.EditWorkspace(id)) },
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
    adaptiveStageContext: AdaptiveStageInteractionContext = AdaptiveStageInteractionContext(),
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit = {},
    workspaceMenu: WorkspaceMenuHost? = null,
    /**
     * The active workspace (S9): its skin override applies to the launcher's theme ([WorkspaceSkin.resolve]), and its
     * gesture bindings and dock override to the home ([withWorkspaceHome]). Null draws the global settings.
     */
    workspace: Workspace? = null,
) {
    val haptics = rememberLauncherHaptics(state.launcherSettings.haptics.feedbackStrength)

    LauncherShellBackHandling(state = state, onAction = onAction)

    PreloadLauncherAppIcons(
        identities = state.appIconPreloadIdentities(),
        iconLoader = appIconLoader,
    )

    RiffleLauncherTheme(
        themeMode = state.launcherSettings.appearance.themeMode,
        themePreset = WorkspaceSkin.resolve(state.launcherSettings.appearance.themePreset, workspace),
        themeAccent = state.launcherSettings.appearance.themeAccent,
        themeColors = state.launcherSettings.appearance.themeColors,
        themeCornerStyle = state.launcherSettings.appearance.themeCornerStyle,
        themeTypography = state.launcherSettings.appearance.themeTypography,
        reducedTransparency = state.launcherSettings.cards.adaptiveStageAppearance.motion.reducedTransparency,
        liquidGlass = state.launcherSettings.resolveLiquidGlass(),
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
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
                haptics = haptics,
                workspaceMenu = workspaceMenu,
                onAction = onAction,
                workspace = workspace,
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
        onAction(LauncherShellAction.OpenHome)
    }
    // S9: Back never leaves Library for another mode (the mode pair retired with workspaces, #1323).
}

private const val DOCK_EDIT_REJECTION_TIMEOUT_MILLIS = 5_000L

internal val AdaptiveStageInteractionContextSaver =
    Saver<AdaptiveStageInteractionContext, List<String>>(
        save = { context ->
            listOf(
                context.selectedStageKey.orEmpty(),
                context.detailStageKey.orEmpty(),
                context.focusedCardKey.orEmpty(),
                context.detailCardKey.orEmpty(),
                context.templateId.orEmpty(),
                context.scrollOffsetPx.toString(),
                context.allNotificationsSelected.toString(),
            )
        },
        restore = { saved ->
            if (saved.size >= 7) {
                AdaptiveStageInteractionContext(
                    selectedStageKey = saved.getOrNull(0)?.takeIf(String::isNotBlank),
                    detailStageKey = saved.getOrNull(1)?.takeIf(String::isNotBlank),
                    focusedCardKey = saved.getOrNull(2)?.takeIf(String::isNotBlank),
                    detailCardKey = saved.getOrNull(3)?.takeIf(String::isNotBlank),
                    templateId = saved.getOrNull(4)?.takeIf(String::isNotBlank),
                    scrollOffsetPx = saved.getOrNull(5)?.toIntOrNull() ?: 0,
                    allNotificationsSelected = saved.getOrNull(6).toBoolean(),
                )
            } else if (saved.size >= 6) {
                AdaptiveStageInteractionContext(
                    selectedStageKey = saved.getOrNull(0)?.takeIf(String::isNotBlank),
                    detailStageKey = saved.getOrNull(1)?.takeIf(String::isNotBlank),
                    focusedCardKey = saved.getOrNull(2)?.takeIf(String::isNotBlank),
                    detailCardKey = saved.getOrNull(3)?.takeIf(String::isNotBlank),
                    templateId = saved.getOrNull(4)?.takeIf(String::isNotBlank),
                    scrollOffsetPx = saved.getOrNull(5)?.toIntOrNull() ?: 0,
                )
            } else {
                AdaptiveStageInteractionContext(
                    selectedStageKey = saved.getOrNull(0)?.takeIf(String::isNotBlank),
                    focusedCardKey = saved.getOrNull(1)?.takeIf(String::isNotBlank),
                    detailCardKey = saved.getOrNull(2)?.takeIf(String::isNotBlank),
                    templateId = saved.getOrNull(3)?.takeIf(String::isNotBlank),
                    scrollOffsetPx = saved.getOrNull(4)?.toIntOrNull() ?: 0,
                )
            }
        },
    )

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
                    text = "Set Riffle as your Home app to open it when you press the device Home button.",
                    style = MaterialTheme.typography.bodySmall,
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
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout?,
    adaptiveStageContext: AdaptiveStageInteractionContext,
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit,
    haptics: LauncherHaptics,
    workspaceMenu: WorkspaceMenuHost?,
    onAction: (LauncherShellAction) -> Unit,
    workspace: Workspace? = null,
) {
    val settingsPageActionRouter = rememberSettingsPageActionRouter(onAction)
    // Ephemeral, not part of durable LauncherShellState -- it's a settings-page-triggered
    // overlay, never persisted or restored, so it doesn't need reducer/action-system plumbing.
    // Owned here (not hoisted to LauncherShellContent) so its dismiss callback can route back to
    // the Adaptive Stage Appearance settings page through settingsPageActionRouter -- that
    // router's own initialSettingsPage state is itself scoped to this composable, unreachable
    // from outside it.
    var adaptiveStageAppearanceTuning by remember { mutableStateOf(false) }

    when (state.destination) {
        ShellDestination.HOME ->
            HomeDestination(
                // Render only: the workspace's gestures and dock override; Settings keeps the global values.
                state = remember(state, workspace) { state.withWorkspaceHome(workspace) },
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
                haptics = haptics,
                workspaceMenu = workspaceMenu,
                onAction = settingsPageActionRouter.onAction,
            )

        ShellDestination.APP_DRAWER ->
            AppDrawer(
                state =
                    AppDrawerState(
                        query = state.appDrawerQuery,
                        profileFilter = state.appDrawerProfileFilter,
                        installedApps = state.installedApps,
                        apps = state.appDrawerApps,
                        presentation = state.launcherSettings.appDrawer.presentation,
                        iconGridColumns = state.launcherSettings.appDrawer.iconGridColumns,
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
                onRequestAdaptiveStageAppearanceTuning = {
                    onAction(LauncherShellAction.OpenHome)
                    adaptiveStageAppearanceTuning = true
                },
            )
    }
    if (adaptiveStageAppearanceTuning) {
        AdaptiveStageAppearanceTuningOverlay(
            state = settingsState,
            // The real actions, so the surface behind is showing the setting rather than a copy of
            // it. Routed like the settings page's own edits so nothing about where a change came
            // from is special.
            onAction = settingsPageActionRouter.onAction,
            onDismiss = {
                adaptiveStageAppearanceTuning = false
                // Not back to SettingsPage.ADAPTIVE_STAGE_APPEARANCE: that page now only launches
                // this same overlay (#1177), so returning to it would reopen the sheet immediately.
                settingsPageActionRouter.onAction(LauncherShellAction.OpenSettingsPage(SettingsPage.MAIN))
            },
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
