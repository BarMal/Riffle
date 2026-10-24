package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughApply
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughContext
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughFlow
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughProblem
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughResult
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughState
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughTarget
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

/**
 * The stores a walkthrough is applied to: the workspace repository and the placed-items pool, plus the home's two
 * guards. [readOnly]: the home cannot be changed this process (`ReadOnlyHomeLayoutRepository`); [loading]: the
 * workspaces have not loaded, or the home still holds item edits while they do (`awaitingWorkspaces`).
 */
internal interface WalkthroughStores {
    val readOnly: Boolean

    val loading: Boolean

    fun workspaces(): WorkspaceSet?

    fun saveWorkspaces(set: WorkspaceSet)

    /** The layout's pool as it is now; null when the pool is not loaded (the legacy home). */
    fun pool(layoutClass: HomeLayoutDeviceClass): PlacedItemPool?

    /** Replaces the layout's pool (written behind, the home re-projects it); false when there is none to replace. */
    fun savePool(
        layoutClass: HomeLayoutDeviceClass,
        pool: PlacedItemPool,
    ): Boolean
}

/** How an apply ended. */
internal sealed interface WalkthroughOutcome {
    /** Saved. [result] carries the Undo values; [layoutClass] is the layout they belong to. */
    data class Applied(
        val result: WalkthroughResult.Applied,
        val layoutClass: HomeLayoutDeviceClass,
        val poolSaved: Boolean,
    ) : WalkthroughOutcome

    /** Refused: the home is read-only this process (the existing notice explains). Nothing was written. */
    data object ReadOnly : WalkthroughOutcome

    /** Refused for now: the workspaces are still loading. Nothing was written; try again in a moment. */
    data object Loading : WalkthroughOutcome

    /** The domain refused (stale state); nothing was written. */
    data class Rejected(val problems: List<WalkthroughProblem>) : WalkthroughOutcome
}

/**
 * Applies a finished walkthrough with [WalkthroughApply] against the stores as they are now, and undoes it. The pool
 * is written before the workspaces, both synchronously on the caller's (main) thread, so the home re-targets once,
 * onto a workspace whose placed items are already there.
 */
internal class WalkthroughApplier(
    private val stores: WalkthroughStores,
    private val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
) {
    fun apply(
        state: WalkthroughState,
        context: WalkthroughContext,
        layoutClass: HomeLayoutDeviceClass,
    ): WalkthroughOutcome {
        val set = stores.workspaces()
        return when {
            stores.readOnly -> WalkthroughOutcome.ReadOnly
            stores.loading || set == null -> WalkthroughOutcome.Loading
            else -> applyTo(set, state, context, layoutClass.workspaceLayoutClass())
        }
    }

    private fun applyTo(
        set: WorkspaceSet,
        state: WalkthroughState,
        context: WalkthroughContext,
        shown: HomeLayoutDeviceClass,
    ): WalkthroughOutcome {
        val before = stores.pool(shown)
        val target =
            WalkthroughTarget(
                layout = set.workspacesFor(shown),
                pool = before ?: PlacedItemPool(),
                candidates = WorkspaceHome.borrowCandidates(WalkthroughFlow.WORKSPACE_ID, shown),
                ids = ids,
            )
        return when (val result = WalkthroughApply.apply(state, context, target)) {
            is WalkthroughResult.Rejected -> WalkthroughOutcome.Rejected(result.problems)
            is WalkthroughResult.Applied -> {
                val poolSaved = before != null && result.pool != before && stores.savePool(shown, result.pool)
                stores.saveWorkspaces(set.withLayout(shown, result.layout))
                WalkthroughOutcome.Applied(result, shown, poolSaved)
            }
        }
    }

    /**
     * Puts back exactly what was stored before [applied] (its layout's workspaces and pool). False, and nothing
     * written, when the home became read-only or the workspaces are gone. Edits made in between to that layout are
     * reverted with it; Undo is offered only for the few seconds the snackbar shows.
     */
    fun undo(applied: WalkthroughOutcome.Applied): Boolean {
        val set = stores.workspaces()?.takeUnless { stores.readOnly } ?: return false
        val undo = applied.result.undo
        if (applied.poolSaved) stores.savePool(applied.layoutClass, undo.pool)
        stores.saveWorkspaces(set.withLayout(applied.layoutClass, undo.layout))
        return true
    }
}
