package com.riffle.core.domain.launcher.workspace

/**
 * Declarative, serializable view over one or more sources. Expressions consume lenses, never raw
 * sources. Holds no item content, so it is safe to persist.
 *
 * [project] is the set of fields an expression may see; privacy redaction happens there.
 */
data class Lens(
    val sources: List<SourceId>,
    val filter: LensFilter = LensFilter.All,
    val group: LensGroup = LensGroup.None,
    val sort: LensSort = LensSort(),
    val limit: Int? = null,
    val project: Set<ItemField> = ItemField.ALL,
    /** Per-source parameters (for example a search query). Empty means every source uses its default. */
    val parameters: Map<SourceId, SourceParameter> = emptyMap(),
) {
    init {
        require(sources.isNotEmpty()) { "A lens needs at least one source." }
        require(limit == null || limit > 0) { "A lens limit must be positive." }
    }

    /** The parameter for [id], or null when the lens does not set one (the source default applies). */
    fun parameterFor(id: SourceId): SourceParameter? = parameters[id]?.takeIf { id in sources }

    /** Result shapes this lens can produce. A flat lens limited to one item is also [ResultShape.SINGLE]. */
    val resultShapes: Set<ResultShape>
        get() =
            when {
                group != LensGroup.None -> setOf(ResultShape.GROUPED)
                limit == 1 -> setOf(ResultShape.SINGLE, ResultShape.FLAT)
                else -> setOf(ResultShape.FLAT)
            }
}

/** Predicate over item fields. Age is relative to evaluation time, so no clock is baked in. */
sealed interface LensFilter {
    data object All : LensFilter

    data class AllOf(val filters: List<LensFilter>) : LensFilter

    data class AnyOf(val filters: List<LensFilter>) : LensFilter

    data class Not(val filter: LensFilter) : LensFilter

    data class FromSource(val sourceId: SourceId) : LensFilter

    data class GroupKeyIs(val key: String) : LensFilter

    data class HasActions(val required: Boolean = true) : LensFilter

    data class AgeAtMost(val millis: Long) : LensFilter

    data class AgeAtLeast(val millis: Long) : LensFilter

    data class PrivacyIs(val privacy: ItemPrivacy) : LensFilter

    data class ExtEquals(val key: ItemExtKey, val value: ItemExtValue) : LensFilter
}

sealed interface LensGroup {
    data object None : LensGroup

    /** Group by [Item.groupKey] (e.g. app id, category). */
    data object ByGroupKey : LensGroup

    /** Group by calendar day of [Item.timeEpochMillis]. */
    data object ByDay : LensGroup

    data class ByExt(val key: ItemExtKey) : LensGroup
}

data class LensSort(
    val field: LensSortField = LensSortField.SOURCE_ORDER,
    val direction: SortDirection = SortDirection.ASCENDING,
    val pinnedFirst: Boolean = false,
)

enum class LensSortField {
    SOURCE_ORDER,
    TITLE,
    TIME,
}

enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

/** Shape of what a lens produces and an expression accepts. */
enum class ResultShape {
    FLAT,
    GROUPED,
    SINGLE,
}

data class ItemGroup(
    val key: String,
    val label: String?,
    val items: List<Item>,
)

sealed interface LensResult {
    val shape: ResultShape

    data class Flat(val items: List<Item>) : LensResult {
        override val shape: ResultShape = ResultShape.FLAT
    }

    data class Grouped(val groups: List<ItemGroup>) : LensResult {
        override val shape: ResultShape = ResultShape.GROUPED
    }
}
