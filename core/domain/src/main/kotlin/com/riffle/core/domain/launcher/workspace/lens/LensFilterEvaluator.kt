package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.LensFilter

/** Evaluates a [LensFilter] against an unredacted [Item]. Only a boolean leaves this object. */
internal object LensFilterEvaluator {
    fun matches(
        filter: LensFilter,
        item: Item,
        context: LensEvaluationContext,
    ): Boolean = matches(filter, item, context, depth = 1)

    private fun matches(
        filter: LensFilter,
        item: Item,
        context: LensEvaluationContext,
        depth: Int,
    ): Boolean =
        when {
            // Fail closed: a filter nested deeper than the bound matches nothing and cannot overflow the stack.
            depth > context.maxFilterDepth -> false
            filter is LensFilter.AllOf -> filter.filters.all { matches(it, item, context, depth + 1) }
            filter is LensFilter.AnyOf -> filter.filters.any { matches(it, item, context, depth + 1) }
            filter is LensFilter.Not -> !matches(filter.filter, item, context, depth + 1)
            else -> matchesLeaf(filter, item, context)
        }

    private fun matchesLeaf(
        filter: LensFilter,
        item: Item,
        context: LensEvaluationContext,
    ): Boolean =
        when (filter) {
            is LensFilter.FromSource -> item.sourceId == filter.sourceId
            is LensFilter.GroupKeyIs -> item.groupKey == filter.key
            is LensFilter.HasActions -> item.actions.isNotEmpty() == filter.required
            is LensFilter.AgeAtMost -> ageOf(item, context)?.let { it <= filter.millis } ?: false
            is LensFilter.AgeAtLeast -> ageOf(item, context)?.let { it >= filter.millis } ?: false
            is LensFilter.PrivacyIs -> item.privacy == filter.privacy
            is LensFilter.ExtEquals -> item.ext[filter.key] == filter.value
            LensFilter.All -> true
            // Composite filters are handled by the caller.
            is LensFilter.AllOf, is LensFilter.AnyOf, is LensFilter.Not -> false
        }

    /** Age is `now - time`; items without a time have no age, so both age filters reject them. */
    private fun ageOf(
        item: Item,
        context: LensEvaluationContext,
    ): Long? = item.timeEpochMillis?.let { context.nowEpochMillis - it }
}
