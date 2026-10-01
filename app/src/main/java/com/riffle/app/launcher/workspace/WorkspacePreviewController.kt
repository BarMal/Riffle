package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Whether the owner turned the Workspaces (preview) developer setting on. Persisted; default off. */
internal interface WorkspacePreviewPreference {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}

/** Where the open preview should scroll to. */
internal sealed interface PreviewNavigation {
    data class ToPage(val containerId: ContainerId) : PreviewNavigation
}

/**
 * The state behind the Workspaces (preview) experience, with no Android types.
 *
 * - [enabled] is the persisted developer setting. While it is off nothing in the workspace system is
 *   started: the host does not build the runtime, read the store or subscribe to a source.
 * - [isOpen] is whether the full-screen preview is showing. It is never persisted, so a restart always
 *   lands on the standard launcher, and turning the setting off closes it.
 * - Menu effects the shell does not perform itself arrive through [onEffect]: jumps and the Finder open the
 *   preview and leave a [navigation] for the pager to consume once; Edit opens the preview and sets
 *   [editing] to the workspace to edit.
 *
 * [onEnabledChanged] runs before the state flows change, so a flag derived from the setting (the dock menu's
 * `WorkspaceMenuFeature`) is already consistent when observers recompose.
 */
internal class WorkspacePreviewController(
    private val preference: WorkspacePreviewPreference,
    private val onEnabledChanged: (Boolean) -> Unit = {},
) {
    private val mutableEnabled = MutableStateFlow(preference.isEnabled())
    private val mutableOpen = MutableStateFlow(false)
    private val mutableNavigation = MutableStateFlow<PreviewNavigation?>(null)
    private val mutableEditing = MutableStateFlow<WorkspaceId?>(null)

    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    val isOpen: StateFlow<Boolean> = mutableOpen.asStateFlow()
    val navigation: StateFlow<PreviewNavigation?> = mutableNavigation.asStateFlow()

    /** The workspace the editor is open on, or null. */
    val editing: StateFlow<WorkspaceId?> = mutableEditing.asStateFlow()

    init {
        onEnabledChanged(mutableEnabled.value)
    }

    fun setEnabled(value: Boolean) {
        if (value == mutableEnabled.value) return
        preference.setEnabled(value)
        onEnabledChanged(value)
        mutableEnabled.value = value
        if (!value) close()
    }

    fun open() {
        if (mutableEnabled.value) mutableOpen.value = true
    }

    /** Leaves the preview (and the editor) for the standard launcher. Always available. */
    fun close() {
        mutableEditing.value = null
        mutableNavigation.value = null
        mutableOpen.value = false
    }

    fun onEffect(effect: WorkspaceMenuEffect) {
        if (!mutableEnabled.value) return
        when (effect) {
            is WorkspaceMenuEffect.NavigateToPage -> navigate(PreviewNavigation.ToPage(effect.key.containerId))
            is WorkspaceMenuEffect.OpenFinderPage -> navigate(PreviewNavigation.ToPage(effect.pageId))
            is WorkspaceMenuEffect.EditWorkspace -> {
                mutableEditing.value = effect.id
                mutableOpen.value = true
            }
            // Persisted by the menu controller; the surface re-reads the repository.
            is WorkspaceMenuEffect.SetActiveWorkspace -> Unit
        }
    }

    fun navigationConsumed(consumed: PreviewNavigation) {
        mutableNavigation.update { current -> if (current == consumed) null else current }
    }

    fun closeEditor() {
        mutableEditing.value = null
    }

    private fun navigate(target: PreviewNavigation) {
        mutableNavigation.value = target
        mutableOpen.value = true
    }
}

/** The pager index of [containerId] among the workspace's pages, or -1. */
internal fun Workspace.pageIndexOf(containerId: ContainerId): Int = pages.indexOfFirst { it.id == containerId }
