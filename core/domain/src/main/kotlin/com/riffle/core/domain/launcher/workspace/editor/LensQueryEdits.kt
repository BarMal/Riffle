package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceParameter

sealed interface QueryEditRefusal {
    /** [FlowMode.Add] has no binding yet: set the query in the flow ([BindingFlowAction.SetQuery]) instead. */
    data object NoExistingBinding : QueryEditRefusal

    /** The source takes no query. */
    data object UnsupportedSource : QueryEditRefusal

    /** The binding's lens does not read the source. */
    data object SourceNotInLens : QueryEditRefusal
}

sealed interface QueryEditResult {
    /** [step] is the session after the edit; a rejection by the editor gate shows in `step.result`. */
    data class Attempted(val step: SessionStep) : QueryEditResult

    data class Refused(val reason: QueryEditRefusal) : QueryEditResult
}

/**
 * Set and clear a lens's query as ordinary workspace edits, so the existing editor gate (`WorkspaceEditor`) and
 * the session's Undo/Redo apply with no new edit type. Pure and framework-free.
 *
 * A binding that references a saved lens is detached when its query changes: the query is part of the lens
 * definition, so a changed copy is no longer the saved one (a saved lens's own query is changed through
 * `LensLibraryEditor.editSavedLens` with [withQuery]).
 *
 * The query is user-authored lens configuration and is stored with it; results are never stored.
 */
object LensQueryEdits {
    private val QUERYABLE = setOf(SourceIds.SEARCH)

    /** Whether lenses may carry a query for [id]. A stored contract: ids are only ever added. */
    fun supportsQuery(id: SourceId): Boolean = id in QUERYABLE

    /** [lens] with [source]'s query set to [text] (blank clears it), or null when it cannot take one. */
    fun withQuery(
        lens: Lens,
        source: SourceId,
        text: String,
    ): Lens? {
        if (!supportsQuery(source) || source !in lens.sources) return null
        val parameter = SourceParameter.query(text)
        val parameters = if (parameter == null) lens.parameters - source else lens.parameters + (source to parameter)
        return lens.copy(parameters = parameters)
    }

    fun clearQuery(
        lens: Lens,
        source: SourceId,
    ): Lens? = withQuery(lens, source, "")

    /** Sets (or, for blank [text], clears) the query of the lens bound at [target] and applies it to [session]. */
    fun setQuery(
        session: WorkspaceEditSession,
        target: FlowMode,
        source: SourceId,
        text: String,
        context: EditContext = EditContext(),
    ): QueryEditResult {
        val existing = FlowEdits.existingBinding(session.draft, target)
        val lens = existing?.let { withQuery(it.lens, source, text) }
        return when {
            existing == null -> QueryEditResult.Refused(QueryEditRefusal.NoExistingBinding)
            !supportsQuery(source) -> QueryEditResult.Refused(QueryEditRefusal.UnsupportedSource)
            lens == null -> QueryEditResult.Refused(QueryEditRefusal.SourceNotInLens)
            else -> {
                val binding = LensBinding(lens, existing.expression, existing.ref.takeIf { lens == existing.lens })
                QueryEditResult.Attempted(session.apply(FlowEdits.editFor(target, binding), context))
            }
        }
    }

    fun clearQuery(
        session: WorkspaceEditSession,
        target: FlowMode,
        source: SourceId,
        context: EditContext = EditContext(),
    ): QueryEditResult = setQuery(session, target, source, "", context)
}
