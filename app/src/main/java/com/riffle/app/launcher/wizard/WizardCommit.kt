package com.riffle.app.launcher.wizard

import com.riffle.app.launcher.pool.READ_ONLY_HOME_NOTICE
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEdit
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.wizard.ContentRecipes
import com.riffle.core.domain.launcher.workspace.wizard.DEFAULT_HOME_GRID
import com.riffle.core.domain.launcher.workspace.wizard.WizardApply
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardResult
import com.riffle.core.domain.launcher.workspace.wizard.WizardState
import com.riffle.core.domain.launcher.workspace.wizard.WizardTarget

/**
 * Where a finished wizard is written outside the editor's own session: the workspace store and the pool store, plus
 * the two home rules a write must respect. Implemented over the cached repositories in the app, faked in tests.
 */
internal interface WizardStores {
    /** The stored workspaces, or null until they have loaded. */
    fun workspaces(): WorkspaceSet?

    fun saveWorkspaces(set: WorkspaceSet)

    /** [layout]'s placed items, or null while the pool is not loaded. */
    fun pool(layout: HomeLayoutDeviceClass): PlacedItemPool?

    /** Replaces [layout]'s placed items (in memory at once, written behind); false when nothing could change. */
    fun savePool(
        layout: HomeLayoutDeviceClass,
        pool: PlacedItemPool,
    ): Boolean

    /** The home is read-only this process (`ReadOnlyHomeLayoutRepository`): placed items must not change. */
    val homeReadOnly: Boolean

    /** S9: the workspaces did not load before the first frame, so item edits are held (`awaitingWorkspaces`). */
    val holdingItemEdits: Boolean
}

/** What the host does after a finish: announce [name] (it was saved), or keep the wizard open with [message]. */
internal sealed interface WizardCommitResult {
    data class Saved(
        val result: WizardResult.Applied,
        val name: String,
    ) : WizardCommitResult

    data class Refused(val message: String) : WizardCommitResult
}

/**
 * The editor's part of a finish: [edit] goes into the editor session with [library] as one step (so its Undo, Redo,
 * Revert and discard prompt cover it); picked apps were already written to the pool (it has its own store).
 */
internal sealed interface EditorWizardResult {
    data class Apply(
        val edit: WorkspaceEdit?,
        val library: LensLibrary,
        val name: String,
    ) : EditorWizardResult

    data class Refused(val message: String) : EditorWizardResult
}

/** What [WizardTarget] needs besides the layout and the pool: which arrangements a borrower shows, ids and the grid. */
internal data class WizardPlacement(
    val layout: HomeLayoutDeviceClass,
    val candidates: List<WorkspaceId> = emptyList(),
    val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    val grid: GridDimensions = DEFAULT_HOME_GRID,
)

/**
 * Applies a finished wizard through [WizardApply] (which re-validates) and writes the result, refusing instead of
 * writing whatever the home rules forbid: placed items while the home is read-only (with the existing notice) or
 * while S9 holds item edits. Pure apart from the [WizardStores] calls; nothing is written unless everything is.
 */
internal object WizardCommit {
    /** Settings > Saved lenses and a source page: saves to the workspace store (and the pool) at once. */
    fun commit(
        state: WizardState,
        context: WizardContext,
        stores: WizardStores,
        placement: WizardPlacement,
    ): WizardCommitResult {
        val set = stores.workspaces() ?: return WizardCommitResult.Refused(WizardText.NOT_LOADED)
        val stored = set.workspacesFor(placement.layout)
        // Validated against what is stored now, so a change made meanwhile is caught.
        val fresh = context.copy(layout = stored)
        return when (val applied = apply(state, fresh, stores, placement, stored)) {
            is Applying.Refused -> WizardCommitResult.Refused(applied.message)
            is Applying.Done -> {
                if (applied.result.layout != stored) {
                    stores.saveWorkspaces(set.withLayout(placement.layout, applied.result.layout))
                }
                WizardCommitResult.Saved(applied.result, WizardHolder.addedName(state, fresh))
            }
        }
    }

    /**
     * The workspace editor: [context] is the session's view of the layout (`LensScope.layoutWith(draft)`); the
     * workspace part comes back as an edit for the session instead of being saved.
     */
    fun forEditor(
        state: WizardState,
        context: WizardContext,
        stores: WizardStores?,
        placement: WizardPlacement,
    ): EditorWizardResult =
        when (val applied = apply(state, context, stores, placement, context.layout)) {
            is Applying.Refused -> EditorWizardResult.Refused(applied.message)
            is Applying.Done ->
                EditorWizardResult.Apply(
                    edit = applied.result.edit,
                    library = applied.result.layout.library,
                    name = WizardHolder.addedName(state, context),
                )
        }

    private sealed interface Applying {
        data class Done(val result: WizardResult.Applied) : Applying

        data class Refused(val message: String) : Applying
    }

    private fun apply(
        state: WizardState,
        context: WizardContext,
        stores: WizardStores?,
        placement: WizardPlacement,
        layout: LayoutWorkspaces,
    ): Applying {
        val placed = state.setup?.let { ContentRecipes.goal(it).placedItems } == true
        val pool = stores?.pool(placement.layout)
        if (placed && pool == null) return Applying.Refused(WizardText.POOL_NOT_LOADED)
        val target = WizardTarget(layout, pool ?: PlacedItemPool(), placement.candidates, placement.ids, placement.grid)
        return when (val result = WizardApply.apply(state, context, target)) {
            is WizardResult.Rejected ->
                Applying.Refused(result.problems.firstOrNull()?.let(WizardText::problem) ?: WizardText.NOT_LOADED)
            is WizardResult.Applied -> writePool(result, stores, placement.layout)
        }
    }

    private fun writePool(
        result: WizardResult.Applied,
        stores: WizardStores?,
        layout: HomeLayoutDeviceClass,
    ): Applying =
        when {
            !result.poolChanged -> Applying.Done(result)
            stores == null -> Applying.Refused(WizardText.POOL_NOT_LOADED)
            stores.homeReadOnly -> Applying.Refused(READ_ONLY_HOME_NOTICE)
            stores.holdingItemEdits -> Applying.Refused(WizardText.HOLDING_EDITS)
            !stores.savePool(layout, result.pool) -> Applying.Refused(WizardText.POOL_NOT_LOADED)
            else -> Applying.Done(result)
        }
}
