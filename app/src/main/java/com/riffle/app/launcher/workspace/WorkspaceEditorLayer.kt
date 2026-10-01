package com.riffle.app.launcher.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.riffle.app.launcher.editor.EditorEntry
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet

/**
 * The workspace editor over the preview, for the workspace the dock menu's Edit chose. Saving replaces just
 * that workspace in the device class's layout and persists through the repository, after which the preview
 * re-renders. Source choices carry each source's current access, read without prompting; "Review access" is
 * handed to the host, which maps it to the existing explicit flows. A workspace that has vanished closes the
 * editor instead of showing an empty one.
 */
@Composable
internal fun WorkspaceEditorLayer(
    host: WorkspacePreviewHost,
    runtime: WorkspaceRuntime,
    state: LauncherShellState,
    workspaceId: WorkspaceId,
) {
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val workspace =
        remember(workspaceId, deviceClass) { runtime.repository.load()?.findWorkspace(deviceClass, workspaceId) }
    if (workspace == null) {
        LaunchedEffect(workspaceId) { host.controller.closeEditor() }
    } else {
        // Access can change while the editor is open (the user returns from a settings screen).
        val sources =
            remember(state.notificationAccessStatus, state.calendarAccessStatus) { runtime.sourceChoices() }
        val reducedMotion = state.launcherSettings.motion.reducedMotion
        val services = remember(runtime, reducedMotion) { runtime.services(reducedMotion) }
        WorkspacePreviewTheme(state) {
            EditorEntry(
                workspace = workspace,
                sources = sources,
                services = services,
                onSave = { saved -> runtime.saveEdited(deviceClass, workspaceId, saved) },
                onClose = host.controller::closeEditor,
                capabilities = LayoutCapabilities(),
                onRequestSourceAccess = host.onRequestSourceAccess,
            )
        }
    }
}

private fun WorkspaceSet.findWorkspace(
    deviceClass: HomeLayoutDeviceClass,
    id: WorkspaceId,
): Workspace? = workspacesFor(deviceClass).find(id)

/** Persists [edited] as workspace [id] of [deviceClass]'s layout; every other workspace is untouched. */
internal fun WorkspaceRuntime.saveEdited(
    deviceClass: HomeLayoutDeviceClass,
    id: WorkspaceId,
    edited: Workspace,
) {
    val current = repository.load() ?: return
    repository.save(current.update(deviceClass) { layout -> layout.replace(id) { edited } })
}
