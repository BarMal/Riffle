package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensNames
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceId

/** Why a saved lens cannot be used for the container being configured. The UI words it; nothing here is prose. */
sealed interface SavedLensBlock {
    /** A source of the lens is not offered here (not on this device, or unknown to the registry). */
    data class UnavailableSource(val source: SourceId) : SavedLensBlock

    /** No expression can draw the lens here. [nearest] is the closest miss, with its reasons. */
    data class CannotDraw(val nearest: ExpressionChoice?) : SavedLensBlock

    /** The definition cannot be shown by the builder (hand-edited data); it can only be used by Settings. */
    data object Unsupported : SavedLensBlock
}

/** One row of the saved-lens list on the Source step. [block] is null when it can be used. */
data class SavedLensChoice(
    val saved: SavedLens,
    /** How many containers of this layout use it right now (the draft included). */
    val usedIn: Int,
    val block: SavedLensBlock? = null,
) {
    val enabled: Boolean get() = block == null
}

/**
 * The saved-lens parts of the editor flow: which saved lenses can be used for the container being configured, using
 * one (the draft takes its lens and the binding keeps the reference), detaching, and Save as lens. Pure, and gated by
 * the same rules as the rest of the flow: a lens is offered exactly when the Expression step would have something to
 * enable for it, so "Next" can never lead nowhere.
 */
object SavedLensFlow {
    /** Every saved lens of the layout, by name, with whether it can be used here and why not. */
    fun choices(context: BindingFlowContext): List<SavedLensChoice> {
        val layout = context.layout
        return context.scope.library.lenses.sortedBy { it.name.lowercase() }.map { saved ->
            SavedLensChoice(saved, LensLibraryOps.dependents(layout, saved.id).size, blockOf(saved.lens, context))
        }
    }

    /** The saved lens the draft uses, or null when it is inline (or the reference no longer holds). */
    fun current(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): SavedLens? = state.ref?.let { id -> context.scope.library.find(id)?.takeIf { it.lens == state.draft.toLens() } }

    /** Uses saved lens [id] when it is offered and enabled at this step; null leaves the caller on the same state. */
    fun use(
        state: BindingFlowState,
        id: LensId,
        context: BindingFlowContext,
    ): BindingFlowState? {
        val choice = choices(context).firstOrNull { it.saved.id == id && it.enabled }
        return if (state.step == EditorStep.SOURCE && choice != null) {
            state.copy(draft = LensDraft.from(choice.saved.lens), ref = id, choosingSaved = false, saveAs = null)
        } else {
            null
        }
    }

    /** Drops a reference that no longer resolves to the draft's lens (an Undo removed the saved lens, say). */
    fun reconcile(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): BindingFlowState = if (state.ref != null && current(state, context) == null) state.copy(ref = null) else state

    /** Whether Save as lens is offered on Confirm: an inline binding, and room in the library. */
    fun canSaveAs(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): Boolean =
        state.step == EditorStep.CONFIRM && state.ref == null && state.draft.toLens() != null &&
            context.scope.library.lenses.size < MAX_SAVED_LENSES

    fun startSaveAs(
        state: BindingFlowState,
        base: String,
        context: BindingFlowContext,
    ): BindingFlowState =
        if (canSaveAs(state, context)) {
            val taken = LensNames.of(context.scope.library.lenses)
            state.copy(saveAs = SaveAsDraft(LensNames.unique(base, taken) { " ${it + 1}" }))
        } else {
            state
        }

    /** Why the Save as lens name (or the library) refuses; null when it would be accepted or was not asked for. */
    fun saveAsProblem(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): LibraryProblem? = state.saveAs?.let { context.scope.library.nameProblem(it.name) }

    /**
     * The saved lens Confirm would create, or the reason it cannot. Null plan with null problem: Save as lens was not
     * asked for (or does not apply).
     */
    fun plan(
        state: BindingFlowState,
        context: BindingFlowContext,
    ): Pair<SavedLensPlan?, LibraryProblem?> {
        val lens = state.draft.toLens()
        val asked = state.saveAs
        return when {
            asked == null || lens == null || state.ref != null -> null to null
            else ->
                when (val added = context.scope.library.tryAdd(asked.name, lens, context.ids)) {
                    is LibraryAdd.Rejected -> null to added.problem
                    is LibraryAdd.Added -> SavedLensPlan(added.library, added.id, asked.name.trim()) to null
                }
        }
    }

    private fun blockOf(
        lens: Lens,
        context: BindingFlowContext,
    ): SavedLensBlock? {
        val missing = lens.sources.firstOrNull { id -> context.sources.none { it.id == id && it.selectable } }
        return when {
            missing != null -> SavedLensBlock.UnavailableSource(missing)
            LensDraft.from(lens).toLens() != lens -> SavedLensBlock.Unsupported
            else -> {
                val options = BindingFlow.expressionChoicesFor(lens, context)
                if (options.any { it.enabled }) null else SavedLensBlock.CannotDraw(nearest(options))
            }
        }
    }

    private fun nearest(options: List<ExpressionChoice>): ExpressionChoice? =
        options.filterNot { it.unsupportedByLayout }
            .minByOrNull { it.lensIssues.size + it.containerIssues.size }
}
