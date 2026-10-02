package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.SourceId

/**
 * Edits of the lens being built: the part of the editor's Source step that is about the lens alone. The
 * editor flow ([BindingFlowAction]) and the Saved lenses page both feed these into [LensDraftReducer], so the
 * rules (which sources can be picked, which presets are enabled, what a query applies to) live in one place.
 */
sealed interface LensDraftAction {
    data class ToggleSource(val id: SourceId) : LensDraftAction

    data class ApplyPreset(val preset: LensPreset) : LensDraftAction

    data class SetFilter(val filter: LensFilter) : LensDraftAction

    data class SetGroup(val group: LensGroup) : LensDraftAction

    data class SetSort(val sort: LensSort) : LensDraftAction

    data class SetLimit(val limit: Int?) : LensDraftAction

    /** Sets the query of [source]; blank clears it. Ignored for a source that is not in the draft or takes no query. */
    data class SetQuery(val source: SourceId, val text: String) : LensDraftAction
}

/**
 * Write side of the lens builder. Pure: `(draft, action, sources) -> draft`. An action that is not allowed right
 * now (an unselectable source, a disabled preset, a query for a source that is not chosen) returns the draft
 * unchanged, so the UI cannot reach an invalid selection even if it forwards a stale tap.
 */
object LensDraftReducer {
    fun reduce(
        draft: LensDraft,
        action: LensDraftAction,
        sources: List<SourceChoice>,
    ): LensDraft =
        when (action) {
            is LensDraftAction.ToggleSource -> toggle(draft, action.id, sources)
            is LensDraftAction.ApplyPreset ->
                if (presetEnabled(draft, action.preset, sources)) draft.withPreset(action.preset) else draft
            is LensDraftAction.SetFilter -> draft.withFilter(action.filter)
            is LensDraftAction.SetGroup -> draft.withGroup(action.group)
            is LensDraftAction.SetSort -> draft.withSort(action.sort)
            is LensDraftAction.SetLimit -> draft.withLimit(action.limit)
            is LensDraftAction.SetQuery ->
                if (action.source in draft.sources) draft.withQuery(action.source, action.text) else draft
        }

    /** The presets for the sources chosen in [draft]; a grouped one is blocked by a source that cannot group. */
    fun presetChoices(
        draft: LensDraft,
        sources: List<SourceChoice>,
    ): List<PresetChoice> = LensDraft.presetChoices(sources.filter { it.id in draft.sources })

    private fun presetEnabled(
        draft: LensDraft,
        preset: LensPreset,
        sources: List<SourceChoice>,
    ): Boolean = presetChoices(draft, sources).firstOrNull { it.preset == preset }?.enabled == true

    /** A chosen source can always be removed; one is added only when the registry offers it and it is selectable. */
    private fun toggle(
        draft: LensDraft,
        id: SourceId,
        sources: List<SourceChoice>,
    ): LensDraft {
        val allowed = id in draft.sources || sources.any { it.id == id && it.selectable }
        return if (allowed) draft.toggleSource(id) else draft
    }
}

/** The lens-only action behind an editor flow action, or null for the flow's own navigation and choices. */
fun BindingFlowAction.toDraftAction(): LensDraftAction? =
    when (this) {
        is BindingFlowAction.ToggleSource -> LensDraftAction.ToggleSource(id)
        is BindingFlowAction.ApplyPreset -> LensDraftAction.ApplyPreset(preset)
        is BindingFlowAction.SetFilter -> LensDraftAction.SetFilter(filter)
        is BindingFlowAction.SetGroup -> LensDraftAction.SetGroup(group)
        is BindingFlowAction.SetSort -> LensDraftAction.SetSort(sort)
        is BindingFlowAction.SetLimit -> LensDraftAction.SetLimit(limit)
        is BindingFlowAction.SetQuery -> LensDraftAction.SetQuery(source, text)
        is BindingFlowAction.PickExpression,
        is BindingFlowAction.PickContainer,
        BindingFlowAction.ShowSavedLenses,
        BindingFlowAction.ShowBuilder,
        is BindingFlowAction.UseSavedLens,
        BindingFlowAction.DetachSavedLens,
        is BindingFlowAction.StartSaveAsLens,
        BindingFlowAction.CancelSaveAsLens,
        is BindingFlowAction.SetSaveAsName,
        BindingFlowAction.Next,
        BindingFlowAction.Back,
        -> null
    }

/** The flow action that carries this draft action, for hosts whose reducer is the flow's. */
fun LensDraftAction.toFlowAction(): BindingFlowAction =
    when (this) {
        is LensDraftAction.ToggleSource -> BindingFlowAction.ToggleSource(id)
        is LensDraftAction.ApplyPreset -> BindingFlowAction.ApplyPreset(preset)
        is LensDraftAction.SetFilter -> BindingFlowAction.SetFilter(filter)
        is LensDraftAction.SetGroup -> BindingFlowAction.SetGroup(group)
        is LensDraftAction.SetSort -> BindingFlowAction.SetSort(sort)
        is LensDraftAction.SetLimit -> BindingFlowAction.SetLimit(limit)
        is LensDraftAction.SetQuery -> BindingFlowAction.SetQuery(source, text)
    }
