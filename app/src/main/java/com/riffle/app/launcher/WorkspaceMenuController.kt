package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.WorkspaceRepository
import com.riffle.core.domain.launcher.workspace.menu.PageSetGroupRef
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuEffect
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuModel
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuPlanner
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuReducer
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the menu surface draws: its open state and, while open, the model it was planned from. */
data class WorkspaceMenuUiState(
    val menu: WorkspaceMenuState = WorkspaceMenuState(),
    val model: WorkspaceMenuModel? = null,
) {
    val isVisible: Boolean get() = menu.isOpen && model != null
}

/** The menu as the shell hands it to the home surface; absent (null) when the feature is off. */
data class WorkspaceMenuHost(
    val state: WorkspaceMenuUiState,
    val onAction: (WorkspaceMenuAction) -> Unit,
)

/**
 * Holds the workspace menu's state and runs its reducer (#1351). No Android types.
 *
 * With the feature off, or no repository, or nothing loaded yet, [WorkspaceMenuPlanner] has no model
 * and the reducer refuses to open, so nothing is drawn. Switching workspace is persisted here through
 * the [repository]; page navigation, the Finder and Edit are only reported through [onEffect] (the
 * pager host and the editor own them), so this never reaches into those areas.
 */
class WorkspaceMenuController internal constructor(
    private val repository: WorkspaceRepository?,
    private val deviceClass: () -> HomeLayoutDeviceClass,
    private val isEnabled: () -> Boolean = { WorkspaceMenuFeature.enabled },
    private val capabilities: () -> LayoutCapabilities = { LayoutCapabilities() },
    private val groups: () -> Map<ContainerId, List<PageSetGroupRef>> = { emptyMap() },
    private val onEffect: (WorkspaceMenuEffect) -> Unit = {},
) {
    private val reducer = WorkspaceMenuReducer()
    private val mutableState = MutableStateFlow(WorkspaceMenuUiState())
    val state: StateFlow<WorkspaceMenuUiState> = mutableState.asStateFlow()

    /** Whether the dock should offer the menu at all: the feature is on and there is something to show. */
    fun isAvailable(): Boolean = plan() != null

    fun dispatch(action: WorkspaceMenuAction) {
        val model = plan()
        val result = reducer.reduce(mutableState.value.menu, action, model)
        mutableState.value =
            WorkspaceMenuUiState(menu = result.state, model = model.takeIf { result.state.isOpen })
        result.effect?.let(::perform)
    }

    /** Re-plans while open (the stored set or the evaluated page-set groups changed). */
    fun refresh() {
        if (mutableState.value.menu.isOpen) {
            val model = plan()
            mutableState.value =
                if (model == null) WorkspaceMenuUiState() else mutableState.value.copy(model = model)
        }
    }

    private fun plan(): WorkspaceMenuModel? =
        if (isEnabled()) {
            repository?.let { WorkspaceMenuPlanner.plan(it.load(), deviceClass(), capabilities(), groups()) }
        } else {
            null
        }

    private fun perform(effect: WorkspaceMenuEffect) {
        if (effect is WorkspaceMenuEffect.SetActiveWorkspace) {
            val store = repository
            val current = store?.load()
            if (store != null && current != null) {
                store.save(current.update(deviceClass()) { it.activate(effect.id) })
            }
        }
        onEffect(effect)
    }
}
