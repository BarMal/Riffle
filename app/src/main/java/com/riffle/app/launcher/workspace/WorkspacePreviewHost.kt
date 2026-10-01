package com.riffle.app.launcher.workspace

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.WorkspaceMenuHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.resolveLiquidGlass
import com.riffle.core.domain.launcher.workspace.SourceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    LaunchedEffect(host) { viewModel.workspaceMenuEffects.collect { effect -> host.controller.onEffect(effect) } }
    val open by host.controller.isOpen.collectAsState()
    val editing by host.controller.editing.collectAsState()
    if (open) WorkspacePreviewContent(host, runtime, state, menu)
    if (open) {
        editing?.let { id -> WorkspaceEditorLayer(host, runtime, state, id) }
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
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val workspace = remember(version, deviceClass) { runtime.repository.load()?.resolveActive(deviceClass)?.workspace }
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val servicesFor =
        remember(runtime, reducedMotion) {
            { padding: PaddingValues -> runtime.services(reducedMotion, padding) }
        }
    WorkspacePreviewTheme(state) {
        WorkspacePreviewSurface(
            workspace = workspace,
            servicesFor = servicesFor,
            menu = menu,
            reducedMotion = reducedMotion,
            navigation = navigation,
            onNavigationConsumed = host.controller::navigationConsumed,
            onExit = host.controller::close,
        )
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
