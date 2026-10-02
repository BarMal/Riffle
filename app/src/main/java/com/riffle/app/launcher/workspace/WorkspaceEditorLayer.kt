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
import com.riffle.core.domain.launcher.workspace.editor.LensScope
import com.riffle.core.domain.launcher.workspace.editor.LensScopeChange

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
    // The layout's library and its other workspaces come along, for saved lenses (preview only, like the editor).
    val loaded =
        remember(workspaceId, deviceClass) {
            runtime.repository.load()?.workspacesFor(deviceClass)?.let { layout ->
                layout.find(workspaceId)?.let { it to LensScope.of(layout, workspaceId) }
            }
        }
    if (loaded == null) {
        LaunchedEffect(workspaceId) { host.controller.closeEditor() }
    } else {
        // Access can change while the editor is open (the user returns from a settings screen).
        val sources =
            remember(state.notificationAccessStatus, state.calendarAccessStatus) { runtime.sourceChoices() }
        val (workspace, scope) = loaded
        val reducedMotion = state.launcherSettings.motion.reducedMotion
        val services = remember(runtime, reducedMotion) { runtime.services(reducedMotion) }
        WorkspacePreviewTheme(state) {
            EditorEntry(
                workspace = workspace,
                sources = sources,
                services = services,
                onSave = { saved, change -> runtime.saveEdited(deviceClass, workspaceId, saved, change) },
                onClose = host.controller::closeEditor,
                capabilities = LayoutCapabilities(),
                onRequestSourceAccess = host.onRequestSourceAccess,
                scope = scope,
            )
        }
    }
}

/**
 * Persists [edited] as workspace [id] of [deviceClass]'s layout. [change] is what the session changed outside it (the
 * layout's saved lenses, and other workspaces that adopted one): written in the same single save, so a Save as lens
 * and the references to it can never be stored apart. Everything else is untouched.
 */
internal fun WorkspaceRuntime.saveEdited(
    deviceClass: HomeLayoutDeviceClass,
    id: WorkspaceId,
    edited: Workspace,
    change: LensScopeChange = LensScopeChange(),
) {
    val current = repository.load() ?: return
    repository.save(
        current.update(deviceClass) { layout -> change.applyTo(layout.replace(id) { edited }) },
    )
}
