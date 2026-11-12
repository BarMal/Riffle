package com.riffle.app.launcher.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.refreshInstalledApps
import com.riffle.core.domain.launcher.workspace.SourceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shell needs from the platform's sources (ADR 0003: sources are internal plumbing): the stored-workspace
 * repository's change counter (the home follows the stored arrangement, see `WorkspaceHome`), and the [runtime], built
 * once on first use (it subscribes to no source until a Stream section observes one and reads no platform data when
 * created).
 */
class SourcesHost internal constructor(
    internal val workspaceVersion: StateFlow<Int>,
    internal val runtime: () -> SourcesRuntime,
    /** Called only when the user taps an "Allow access" button; routes to the existing explicit flows. */
    internal val onRequestSourceAccess: (SourceId) -> Unit = {},
)

/** Stands in for an absent host, so the shell can observe "version" unconditionally. */
internal val NoWorkspaceVersion: StateFlow<Int> = MutableStateFlow(0)

/**
 * Composed by the shell whenever it has a host. It loads the stored workspaces once (never overwriting what is stored)
 * and the per-layout exclusion rules.
 */
@Composable
internal fun SourcesLayer(
    host: SourcesHost,
    viewModel: LauncherShellViewModel,
) {
    val runtime = remember(host) { host.runtime() }
    LaunchedEffect(runtime) {
        runtime.prepareExclusions()
        // The hidden apps may just have absorbed app exclusion rules (one list); redraw the app lists from it.
        viewModel.refreshInstalledApps()
        runtime.repository.initialize(WorkspaceBootstrap::seed)
    }
}
