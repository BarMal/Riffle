package com.riffle.app.launcher.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.pool.PoolHomeSwitch
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.resolveLiquidGlass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceSkin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shell needs to host workspaces (S9: the launcher's home, no longer a preview): the [controller] (which
 * workspace the editor is open on), the repository's change counter, and the [runtime], built once on first use (it
 * subscribes to no source until a page observes a lens and reads no platform data when created).
 */
class WorkspacePreviewHost internal constructor(
    internal val controller: WorkspacePreviewController,
    internal val workspaceVersion: StateFlow<Int>,
    internal val runtime: () -> WorkspaceRuntime,
    /** Called only when the user taps "Review access" in the editor; routes to the existing explicit flows. */
    internal val onRequestSourceAccess: (SourceId) -> Unit = {},
    /** The S4 cutover switch, the one developer fallback left (Settings > Developer > Home from placed items). */
    internal val poolHome: PoolHomeSwitch? = null,
)

/** Stands in for an absent host, so the shell can observe "version" unconditionally. */
internal val NoWorkspaceVersion: StateFlow<Int> = MutableStateFlow(0)

/**
 * Composed by the shell whenever it has a host. It loads the workspaces once (seeding the Nova default for a layout
 * with nothing stored, never overwriting what is stored) and the per-layout exclusion rules, and draws the workspace
 * editor over the home while the menu's Edit (or Settings > Workspaces > Edit) has it open. The home itself is the
 * standard home surface (`LauncherShell`); the full-screen preview surface is no longer shown.
 */
@Composable
internal fun WorkspaceLayer(
    host: WorkspacePreviewHost,
    state: LauncherShellState,
    viewModel: LauncherShellViewModel,
) {
    val runtime = remember(host) { host.runtime() }
    LaunchedEffect(runtime) {
        runtime.prepareExclusions()
        runtime.repository.initialize(WorkspaceBootstrap::seed)
        viewModel.workspaceMenu.refresh()
    }
    val editing by host.controller.editing.collectAsState()
    editing?.let { id -> WorkspaceEditorLayer(host, runtime, state, id) }
}

/**
 * The shell's theme, so a workspace surface matches the launcher's appearance settings, with [workspace]'s skin
 * override (Q15) in place of the global theme preset when it names one ([WorkspaceSkin.resolve]).
 */
@Composable
internal fun WorkspacePreviewTheme(
    state: LauncherShellState,
    workspace: Workspace?,
    content: @Composable () -> Unit,
) {
    val appearance = state.launcherSettings.appearance
    RiffleLauncherTheme(
        themeMode = appearance.themeMode,
        themePreset = WorkspaceSkin.resolve(appearance.themePreset, workspace),
        themeAccent = appearance.themeAccent,
        themeColors = appearance.themeColors,
        themeCornerStyle = appearance.themeCornerStyle,
        themeTypography = appearance.themeTypography,
        reducedTransparency = state.launcherSettings.cards.adaptiveStageAppearance.motion.reducedTransparency,
        liquidGlass = state.launcherSettings.resolveLiquidGlass(),
        content = content,
    )
}
