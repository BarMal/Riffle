package com.riffle.app.launcher.workspace

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.editor.EditorWizardSupport
import com.riffle.app.launcher.wizard.ContentWizard
import com.riffle.app.launcher.wizard.WizardCommit
import com.riffle.app.launcher.wizard.WizardCommitResult
import com.riffle.app.launcher.wizard.WizardInputs
import com.riffle.app.launcher.wizard.WizardOutcome
import com.riffle.app.launcher.wizard.WizardPlacement
import com.riffle.app.launcher.wizard.WizardRequest
import com.riffle.app.launcher.wizard.WizardStores
import com.riffle.app.launcher.wizard.WizardText
import com.riffle.app.launcher.wizard.WorkspaceOnlyWizardStores
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuEffect
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeView
import com.riffle.core.domain.launcher.workspace.wizard.FeedOption
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry

/** Where a wizard writes: the host's stores (the pool and the home's rules), else the workspace store alone. */
internal fun WorkspacePreviewHost.wizardStoresFor(runtime: WorkspaceRuntime): WizardStores =
    wizardStores ?: WorkspaceOnlyWizardStores(runtime.repository)

/** The feeds the wizard's feed picker offers: RSS feeds from the settings, calendar feeds as last loaded. */
internal fun WorkspaceRuntime.wizardFeeds(state: LauncherShellState): List<FeedOption> =
    WizardInputs.feeds(
        state.launcherSettings.rss,
        icsFeeds?.state?.value?.rows.orEmpty().map { it.id.value to it.name },
    )

/** Where picked apps go for [workspaceId] of [layout]: the arrangements it borrows and the home's grid. */
internal fun wizardPlacement(
    state: LauncherShellState,
    layout: HomeLayoutDeviceClass,
    workspaceId: WorkspaceId?,
): WizardPlacement =
    WizardPlacement(
        layout = layout,
        candidates =
            workspaceId?.let { PoolHomeView.candidates(it, layout, state.homeLayoutSet.activeKey.viewMode) }.orEmpty(),
        grid = state.homeLayout.selectedPage.grid,
    )

/** The editor's wizard support for [workspaceId] (the workspace the editor is open on). */
internal fun WorkspacePreviewHost.editorWizardSupport(
    runtime: WorkspaceRuntime,
    state: LauncherShellState,
    layout: HomeLayoutDeviceClass,
    workspaceId: WorkspaceId,
): EditorWizardSupport =
    EditorWizardSupport(
        stores = wizardStoresFor(runtime),
        placement = wizardPlacement(state, layout, workspaceId),
        apps = WizardInputs.apps(state.installedApps),
        feeds = runtime.wizardFeeds(state),
    )

/**
 * The content wizard Settings opened (Saved lenses > New, a source page's "Use this source"), over Settings. A
 * finish is saved through the workspace and pool repositories at once ([WizardCommit], which refuses what the home
 * forbids) and announced; "Open advanced builder" goes to the Saved lenses builder or, for a source, the editor of
 * the active workspace on the advanced flow. Closes itself when the workspaces are not loaded.
 */
@Composable
internal fun WorkspaceWizardLayer(
    host: WorkspacePreviewHost,
    runtime: WorkspaceRuntime,
    state: LauncherShellState,
    viewModel: LauncherShellViewModel,
    request: WizardRequest,
) {
    val version by host.workspaceVersion.collectAsState()
    val layout = remember(version, request) { runtime.repository.load()?.workspacesFor(request.layout) }
    if (layout == null) {
        LaunchedEffect(request) { host.wizardRequests.close() }
        return
    }
    val sources = remember(state.notificationAccessStatus, state.calendarAccessStatus) { runtime.sourceChoices() }
    val feeds = remember(runtime, state.launcherSettings.rss) { runtime.wizardFeeds(state) }
    val apps = remember(state.installedApps) { WizardInputs.apps(state.installedApps) }
    // "Use this source" adds to the active workspace, read when the wizard opened (a switch meanwhile is caught on
    // apply: the context's workspace is looked up again in what is stored then).
    val entry =
        remember(request) {
            when (request) {
                is WizardRequest.NewSavedLens -> WizardEntry.NewSavedLens
                is WizardRequest.FromSource -> WizardEntry.FromSource(request.source, layout.activeId)
            }
        }
    val context = WizardContext(entry, sources, layout, LayoutCapabilities(), feeds)
    val workspaceId = (entry as? WizardEntry.FromSource)?.workspaceId
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val services = remember(runtime, reducedMotion) { runtime.services(reducedMotion) }
    val androidContext = LocalContext.current
    val stores = remember(host, runtime) { host.wizardStoresFor(runtime) }
    WorkspacePreviewTheme(state, workspaceId?.let(layout::find)) {
        ContentWizard(
            context = context,
            onFinished = { finished ->
                val placement = wizardPlacement(state, request.layout, workspaceId)
                when (val result = WizardCommit.commit(finished, context, stores, placement)) {
                    is WizardCommitResult.Saved -> {
                        viewModel.workspaceMenu.refresh()
                        host.wizardRequests.close()
                        Toast.makeText(androidContext, WizardText.added(result.name), Toast.LENGTH_SHORT).show()
                        WizardOutcome.Closed
                    }
                    is WizardCommitResult.Refused -> WizardOutcome.Refused(result.message)
                }
            },
            onCancelled = host.wizardRequests::close,
            onAdvanced = { handoff ->
                if (!host.wizardRequests.advance(handoff) && workspaceId != null) {
                    host.controller.onEffect(WorkspaceMenuEffect.EditWorkspace(workspaceId))
                }
            },
            services = services,
            apps = apps,
            onRequestSourceAccess = host.onRequestSourceAccess,
            reducedMotion = reducedMotion,
        )
    }
}
