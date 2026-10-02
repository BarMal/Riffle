package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.editor.LensDraft
import com.riffle.core.domain.launcher.workspace.editor.LensDraftAction
import com.riffle.core.domain.launcher.workspace.editor.LensDraftReducer
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess

/**
 * The lens being built on the detail page: [id] is the saved lens being edited (null: a new one), [baseline] what
 * was stored when it was opened, and [draft] the lens-builder state shared with the editor's Source step. The
 * name is plain text the page owns. Holds a lens definition only, never item content.
 */
data class LensSession(
    val id: LensId?,
    val baseline: SavedLens?,
    val draft: LensDraft,
) {
    /** The lens as built so far; null until a source is chosen. */
    val lens: Lens? get() = draft.toLens()

    /** The name the text field starts with. */
    val initialName: String get() = baseline?.name.orEmpty()

    /** True when the definition (not the name) differs from what was opened. */
    val definitionChanged: Boolean get() = if (baseline == null) draft.sources.isNotEmpty() else lens != baseline.lens

    fun reduce(
        action: LensDraftAction,
        sources: List<SourceChoice>,
    ): LensSession = copy(draft = LensDraftReducer.reduce(draft, action, sources))

    companion object {
        fun open(saved: SavedLens): LensSession = LensSession(saved.id, saved, LensDraft.from(saved.lens))

        fun startNew(): LensSession = LensSession(null, null, LensDraft())
    }
}

/** The builder's source rows: the registry's sources in the Sources page's order, each with its honest status. */
object LensSourceChoices {
    /**
     * [statuses] and [disabled] are what Settings > Sources shows (read without prompting). A source that needs a
     * permission or is off can still be picked (a lens is only a definition) and says so; one that is
     * [SourceStatus.UNAVAILABLE] cannot be picked.
     */
    fun build(
        descriptors: List<SourceDescriptor>,
        statuses: Map<SourceId, SourceStatus>,
        disabled: Set<SourceId>,
    ): List<SourceChoice> {
        val byId = descriptors.associateBy { it.id }
        return SourcesSettingsPlanner.plan(descriptors, statuses, disabled).mapNotNull { row ->
            byId[row.id]?.let { descriptor ->
                SourceChoice(
                    descriptor = descriptor,
                    badges = descriptor.capabilities.sortedBy { it.ordinal },
                    access = accessOf(row.status),
                    status = row.status,
                )
            }
        }
    }

    private fun accessOf(status: SourceStatus): SourceAccess =
        when (status) {
            SourceStatus.NEEDS_PERMISSION -> SourceAccess.REQUIRED
            SourceStatus.UNAVAILABLE -> SourceAccess.UNAVAILABLE
            SourceStatus.LOADING, SourceStatus.READY, SourceStatus.OFF -> SourceAccess.GRANTED
        }
}
