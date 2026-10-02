package com.riffle.app.launcher.workspace

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.WorkspaceMenuHost
import com.riffle.app.launcher.pool.PlacedHomeContent
import com.riffle.app.launcher.pool.PoolEditUi
import com.riffle.app.launcher.pool.PoolRuntime
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.resolveLiquidGlass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeView
import com.riffle.core.domain.launcher.workspace.pool.PoolHostIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * What the shell needs to host the Workspaces (preview): its [controller] and the repository's change
 * counter, plus the [runtime], which is only built (and so only touches sources and storage) once the
 * preview setting is on and the layer composes.
 */
class WorkspacePreviewHost internal constructor(
    internal val controller: WorkspacePreviewController,
    internal val workspaceVersion: StateFlow<Int>,
    internal val runtime: () -> WorkspaceRuntime,
    /** Called only when the user taps "Review access" in the editor; routes to the existing explicit flows. */
    internal val onRequestSourceAccess: (SourceId) -> Unit = {},
    /** The placed-items pool behind the home pages; null keeps the placeholder. Built only when the preview opens. */
    internal val pool: (() -> PoolRuntime)? = null,
)

/** Stands in for an absent host, so the shell can observe "enabled" and "version" unconditionally. */
internal val NeverEnabled: StateFlow<Boolean> = MutableStateFlow(false)
internal val NoWorkspaceVersion: StateFlow<Int> = MutableStateFlow(0)

/**
 * Composed by the shell only while the preview setting is on. It loads the workspaces once (seeding the
 * defaults, never overwriting what is stored), forwards the dock menu's jump, Finder and Edit choices to the
 * controller, and shows the full-screen preview while the controller says it is open.
 */
@Composable
internal fun WorkspacePreviewLayer(
    host: WorkspacePreviewHost,
    state: LauncherShellState,
    viewModel: LauncherShellViewModel,
    menu: WorkspaceMenuHost?,
) {
    val runtime = remember(host) { host.runtime() }
    LaunchedEffect(runtime) {
        runtime.prepareExclusions()
        runtime.repository.initialize(WorkspaceBootstrap::seed)
        viewModel.workspaceMenu.refresh()
    }
    ReturnToLauncherEffect(host.controller)
    LaunchedEffect(host) { viewModel.workspaceMenuEffects.collect { effect -> host.controller.onEffect(effect) } }
    val open by host.controller.isOpen.collectAsState()
    val editing by host.controller.editing.collectAsState()
    if (open) WorkspacePreviewContent(host, runtime, state, menu)
    if (open) {
        editing?.let { id -> WorkspaceEditorLayer(host, runtime, state, id) }
    }
}

/** Coming back from another app (the activity stopped, then started) is a Return for the open preview. */
@Composable
private fun ReturnToLauncherEffect(controller: WorkspacePreviewController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        var stopped = false
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> stopped = true
                    Lifecycle.Event.ON_START -> {
                        if (stopped) controller.onReturn()
                        stopped = false
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun WorkspacePreviewContent(
    host: WorkspacePreviewHost,
    runtime: WorkspaceRuntime,
    state: LauncherShellState,
    menu: WorkspaceMenuHost?,
) {
    val version by host.workspaceVersion.collectAsState()
    val navigation by host.controller.navigation.collectAsState()
    val returnRequest by host.controller.returnRequest.collectAsState()
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val workspace = remember(version, deviceClass) { runtime.repository.load()?.resolveActive(deviceClass)?.workspace }
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val servicesFor =
        remember(runtime, reducedMotion) {
            { padding: PaddingValues -> runtime.services(reducedMotion, padding) }
        }
    val placedHome = rememberPlacedHome(host, state, workspace?.id)
    WorkspacePreviewTheme(state) {
        WorkspacePreviewSurface(
            workspace = workspace,
            servicesFor = servicesFor,
            menu = menu,
            reducedMotion = reducedMotion,
            navigation = navigation,
            onNavigationConsumed = host.controller::navigationConsumed,
            returnBehavior = state.launcherSettings.home.returnBehavior,
            returnRequest = returnRequest,
            onReturnConsumed = host.controller::returnConsumed,
            onExit = host.controller::close,
            placedHome = placedHome,
        )
    }
}

/**
 * The read-only placed home content, or null when the host has no pool. Loads the pool once the preview is open
 * (the one-time import from the standard home runs here), and rebuilds when the pool or the active workspace changes.
 */
@Composable
private fun rememberPlacedHome(
    host: WorkspacePreviewHost,
    state: LauncherShellState,
    workspaceId: WorkspaceId?,
): PlacedHomeContent? {
    val pool = remember(host) { host.pool?.invoke() }
    val layoutSet by rememberUpdatedState(state.homeLayoutSet)
    val scope = rememberCoroutineScope()
    LaunchedEffect(pool) { pool?.initialize(layoutSet) }
    val version by (pool?.repository?.version ?: NoWorkspaceVersion).collectAsState()
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val shownMode = state.homeLayoutSet.activeKey.viewMode
    val labels = state.homeLayoutSet.activeLayout.settings.labels
    val installedApps = state.installedApps
    return remember(pool, version, workspaceId, deviceClass, shownMode, labels, installedApps) {
        pool?.let { runtime ->
            val candidates = workspaceId?.let { PoolHomeView.candidates(it, deviceClass, shownMode) }
            val placed = runtime.repository.pool(deviceClass)
            val editWorkspace =
                if (candidates == null || placed == null) {
                    null
                } else {
                    PoolHomeView.arrangementOf(
                        placed,
                        candidates,
                    )
                }
            PlacedHomeContent(
                resolve = { page ->
                    val candidates = workspaceId?.let { PoolHomeView.candidates(it, deviceClass, shownMode) }
                    val placed = runtime.repository.pool(deviceClass)
                    if (candidates == null || placed == null) null else PoolHomeView.resolve(placed, page, candidates)
                },
                iconLoader = runtime.iconLoader,
                widgetViews = runtime.widgetViews,
                labelSettings = labels,
                onOpen = { item -> runtime.actions.open(item) },
                // Refresh replaces the preview's edits, so it asks first (the dialog is the editor overlay's).
                onReimport = { runtime.editing.requestReimport() },
                edit =
                    PoolEditUi(
                        controller = runtime.editing,
                        deviceClass = deviceClass,
                        pool = placed,
                        workspaceId = editWorkspace,
                        installedApps = installedApps,
                        standardHostIds = { PoolHostIds.standardHome(layoutSet) },
                        onReimportConfirmed = { scope.launch { runtime.reimport(layoutSet) } },
                    ),
            )
        }
    }
}

/** The shell's theme, so the preview matches the launcher's appearance settings. */
@Composable
internal fun WorkspacePreviewTheme(
    state: LauncherShellState,
    content: @Composable () -> Unit,
) {
    val appearance = state.launcherSettings.appearance
    RiffleLauncherTheme(
        themeMode = appearance.themeMode,
        themePreset = appearance.themePreset,
        themeAccent = appearance.themeAccent,
        themeColors = appearance.themeColors,
        themeCornerStyle = appearance.themeCornerStyle,
        themeTypography = appearance.themeTypography,
        reducedTransparency = state.launcherSettings.cards.adaptiveStageAppearance.motion.reducedTransparency,
        liquidGlass = state.launcherSettings.resolveLiquidGlass(),
        content = content,
    )
}
