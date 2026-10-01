package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceId

/** The small set of lens shapes the Source step offers first. Anything else is reachable through the custom builder. */
enum class LensPreset(
    val filter: LensFilter = LensFilter.All,
    val group: LensGroup = LensGroup.None,
    val sort: LensSort = LensSort(),
    val limit: Int? = null,
) {
    EVERYTHING,
    NEWEST_FIRST(sort = NEWEST),
    A_TO_Z(sort = LensSort(LensSortField.TITLE, SortDirection.ASCENDING)),
    WITH_ACTIONS(filter = LensFilter.HasActions()),
    GROUPED(group = LensGroup.ByGroupKey),
    GROUPED_BY_DAY(group = LensGroup.ByDay, sort = NEWEST),
    LATEST_FIVE(sort = NEWEST, limit = 5),
    LATEST_ONE(sort = NEWEST, limit = 1),
    ;

    /** Grouping by group key needs every source to be groupable (see `LensExpressionValidity`). */
    val needsGroupableSources: Boolean get() = group == LensGroup.ByGroupKey
}

private val NEWEST = LensSort(LensSortField.TIME, SortDirection.DESCENDING)

/** A preset offered for the selected sources: [blockedBy] names the first source that rules it out. */
data class PresetChoice(
    val preset: LensPreset,
    val blockedBy: SourceId? = null,
) {
    val enabled: Boolean get() = blockedBy == null
}

/**
 * The lens being built. Holds the full [Lens] model, so any lens is reachable; [preset] is non-null while
 * the draft is exactly a preset applied to the chosen sources, which is what the UI shows by default
 * (progressive disclosure: presets first, custom controls on request). Contains no item content.
 */
data class LensDraft(
    val sources: List<SourceId> = emptyList(),
    val preset: LensPreset? = LensPreset.EVERYTHING,
    val filter: LensFilter = LensFilter.All,
    val group: LensGroup = LensGroup.None,
    val sort: LensSort = LensSort(),
    val limit: Int? = null,
    val project: Set<ItemField> = ItemField.ALL,
) {
    /** Null until at least one source is chosen (a lens needs one). */
    fun toLens(): Lens? = if (sources.isEmpty()) null else Lens(sources.distinct(), filter, group, sort, limit, project)

    fun toggleSource(id: SourceId): LensDraft = copy(sources = if (id in sources) sources - id else sources + id)

    fun withPreset(preset: LensPreset): LensDraft =
        copy(
            preset = preset,
            filter = preset.filter,
            group = preset.group,
            sort = preset.sort,
            limit = preset.limit,
        )

    fun withFilter(filter: LensFilter): LensDraft = copy(preset = null, filter = filter)

    fun withGroup(group: LensGroup): LensDraft = copy(preset = null, group = group)

    fun withSort(sort: LensSort): LensDraft = copy(preset = null, sort = sort)

    /** A non-positive limit means no limit. */
    fun withLimit(limit: Int?): LensDraft = copy(preset = null, limit = limit?.takeIf { it > 0 })

    fun withProject(project: Set<ItemField>): LensDraft = copy(project = project)

    companion object {
        /** Starts from an existing lens (editing a binding); recognised presets show as such. */
        fun from(lens: Lens): LensDraft {
            val preset =
                LensPreset.entries.firstOrNull {
                    it.filter == lens.filter && it.group == lens.group && it.sort == lens.sort && it.limit == lens.limit
                }
            return LensDraft(lens.sources, preset, lens.filter, lens.group, lens.sort, lens.limit, lens.project)
        }

        /** Presets for the [chosen] sources; grouped ones are disabled when a source cannot be grouped. */
        fun presetChoices(chosen: List<SourceChoice>): List<PresetChoice> =
            LensPreset.entries.map { preset ->
                PresetChoice(
                    preset,
                    blockedBy = chosen.firstOrNull { preset.needsGroupableSources && !it.groupable }?.id,
                )
            }
    }
}
