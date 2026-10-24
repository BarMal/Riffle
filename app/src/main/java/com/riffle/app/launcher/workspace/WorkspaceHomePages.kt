package com.riffle.app.launcher.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.containers.PageContainerHost
import com.riffle.app.launcher.containers.PageSetContainerHost
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The active workspace's lens and container pages (S9, [WorkspaceHome.extraPages]) that the standard home pager draws
 * after its placed pages, with the existing container hosts. [jumpRequest] is the index of an extra page the
 * workspace menu asked to show, for the pager to scroll to once and [consume].
 */
internal class WorkspaceHomePages(
    val pages: List<PageHost>,
    val services: ContainerServices,
    val jumpRequest: StateFlow<Int?>,
    private val jumps: MutableStateFlow<Int?>,
) {
    /** Clears [index] when it is still the pending jump, so a newer request is never dropped. */
    fun consume(index: Int) = jumps.update { current -> if (current == index) null else current }
}

/** Provided by the shell around the home; null (no workspace, nothing extra) draws the placed pages only. */
internal val LocalWorkspaceHomePages = staticCompositionLocalOf<WorkspaceHomePages?> { null }

/** Draws extra page [index] of [pages] with the container hosts; nothing for an index it does not have. */
@Composable
internal fun WorkspaceHomePage(
    pages: WorkspaceHomePages,
    index: Int,
    modifier: Modifier = Modifier,
) {
    when (val page = pages.pages.getOrNull(index)) {
        is PageSetContainer -> PageSetContainerHost(page, pages.services, modifier)
        is PageContainer -> PageContainerHost(page, pages.services, modifier)
        null -> Unit
    }
}

/**
 * What the shell takes from the workspaces for its home: the [workspace] the current layout draws (null while
 * nothing is loaded), its [pages] beyond the placed ones, and the hide controller the lens pages' "More options"
 * use. [navigate] carries out the workspace menu's "jump to page".
 */
internal class WorkspaceHomeState(
    val workspace: Workspace?,
    val pages: WorkspaceHomePages?,
    val hideController: ExclusionsSettingsController?,
    private val jumps: MutableStateFlow<Int?>,
) {
    /**
     * Where the menu's jump to [containerId] lands: a placed page of [layout] (returned, for the shell to select), or
     * an extra page (scrolled to through [WorkspaceHomePages.jumpRequest]; null is returned). Null for anything else.
     */
    fun navigate(
        containerId: ContainerId,
        layout: HomeLayout,
    ): LauncherPageId? {
        val shown = workspace ?: return null
        val placed = WorkspaceHome.layoutPageOf(shown, containerId, layout)
        if (placed == null) {
            val index = pages?.pages?.indexOfFirst { it.id == containerId } ?: -1
            if (index >= 0) jumps.value = index
        }
        return placed
    }

    /** Whether [containerId] is one of the extra pages (so the shell only has to bring Home to the front). */
    fun isExtraPage(containerId: ContainerId): Boolean = pages?.pages?.any { it.id == containerId } == true
}

/** The [WorkspaceHomeState] of [host] for [state]'s layout, following the stored workspaces. */
@Composable
internal fun rememberWorkspaceHome(
    host: WorkspacePreviewHost?,
    state: LauncherShellState,
): WorkspaceHomeState? {
    if (host == null) return null
    val runtime = remember(host) { host.runtime() }
    val version by host.workspaceVersion.collectAsState()
    // N10: the Workspaces layout, so a rotation changes geometry only.
    val layout = state.homeLayoutSet.activeKey.deviceClass.workspaceLayoutClass()
    val workspace =
        remember(runtime, version, layout) { WorkspaceHome.activeWorkspace(runtime.repository.load(), layout) }
    val extras = remember(workspace, layout) { WorkspaceHome.extraPages(workspace, layout) }
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val services = remember(runtime, reducedMotion) { runtime.services(reducedMotion) }
    val jumps = remember { MutableStateFlow<Int?>(null) }
    val pages =
        remember(extras, services) {
            if (extras.isEmpty()) null else WorkspaceHomePages(extras, services, jumps, jumps)
        }
    val hideController = remember(runtime) { runtime.hideController() }
    return remember(workspace, pages, hideController) { WorkspaceHomeState(workspace, pages, hideController, jumps) }
}
