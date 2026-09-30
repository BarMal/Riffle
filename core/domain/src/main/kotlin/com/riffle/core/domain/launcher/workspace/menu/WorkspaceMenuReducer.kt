package com.riffle.core.domain.launcher.workspace.menu

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.WorkspaceId

/** The menu's own state: whether it is open. Everything it shows comes from [WorkspaceMenuModel]. */
data class WorkspaceMenuState(val isOpen: Boolean = false)

sealed interface WorkspaceMenuAction {
    data object Open : WorkspaceMenuAction

    data object Close : WorkspaceMenuAction

    data class SwitchWorkspace(val id: WorkspaceId) : WorkspaceMenuAction

    data class JumpToPage(val key: WorkspacePageKey) : WorkspaceMenuAction

    data object OpenFinder : WorkspaceMenuAction

    data object EditWorkspace : WorkspaceMenuAction
}

/** What the shell must do after a menu action. The reducer only decides; it performs nothing. */
sealed interface WorkspaceMenuEffect {
    data class SetActiveWorkspace(val id: WorkspaceId) : WorkspaceMenuEffect

    data class NavigateToPage(val key: WorkspacePageKey) : WorkspaceMenuEffect

    data class OpenFinderPage(val pageId: ContainerId) : WorkspaceMenuEffect

    data class EditWorkspace(val id: WorkspaceId) : WorkspaceMenuEffect
}

data class WorkspaceMenuResult(
    val state: WorkspaceMenuState,
    val effect: WorkspaceMenuEffect? = null,
)

/**
 * Menu state transitions. An action the model does not offer (unknown workspace or page, Finder on a
 * workspace without one) is ignored: state unchanged, no effect. Choosing an entry closes the menu;
 * re-choosing the active workspace only closes it. With no model (workspace system off) the menu cannot
 * open, so it never leaves an empty or blocking surface.
 */
class WorkspaceMenuReducer {
    fun reduce(
        state: WorkspaceMenuState,
        action: WorkspaceMenuAction,
        model: WorkspaceMenuModel?,
    ): WorkspaceMenuResult =
        when {
            action == WorkspaceMenuAction.Close || model == null -> WorkspaceMenuResult(WorkspaceMenuState())
            action == WorkspaceMenuAction.Open -> WorkspaceMenuResult(WorkspaceMenuState(isOpen = true))
            !state.isOpen -> WorkspaceMenuResult(state)
            else -> choose(state, action, model)
        }

    private fun choose(
        state: WorkspaceMenuState,
        action: WorkspaceMenuAction,
        model: WorkspaceMenuModel,
    ): WorkspaceMenuResult {
        val effect = effectFor(action, model)
        return if (effect == null && !isSelectionOfActive(action, model)) {
            WorkspaceMenuResult(state)
        } else {
            WorkspaceMenuResult(WorkspaceMenuState(), effect)
        }
    }

    private fun effectFor(
        action: WorkspaceMenuAction,
        model: WorkspaceMenuModel,
    ): WorkspaceMenuEffect? =
        when (action) {
            is WorkspaceMenuAction.SwitchWorkspace ->
                model.switchEntries
                    .firstOrNull { it.id == action.id && !it.isActive }
                    ?.let { WorkspaceMenuEffect.SetActiveWorkspace(it.id) }
            is WorkspaceMenuAction.JumpToPage ->
                action.key
                    .takeIf { key -> model.jumpEntries.any { it.key == key } }
                    ?.let { WorkspaceMenuEffect.NavigateToPage(it) }
            WorkspaceMenuAction.OpenFinder -> model.finder?.let { WorkspaceMenuEffect.OpenFinderPage(it.pageId) }
            WorkspaceMenuAction.EditWorkspace -> WorkspaceMenuEffect.EditWorkspace(model.editTarget)
            WorkspaceMenuAction.Open, WorkspaceMenuAction.Close -> null
        }

    private fun isSelectionOfActive(
        action: WorkspaceMenuAction,
        model: WorkspaceMenuModel,
    ): Boolean =
        action is WorkspaceMenuAction.SwitchWorkspace &&
            model.switchEntries.any { it.id == action.id && it.isActive }
}
