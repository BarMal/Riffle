package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceId

/**
 * Turns the items of a lens's sources into a [LensResult]. Implementations must be pure: no threading,
 * no clock, no Android, no mutation of inputs. [items] is every item the caller currently has for the
 * lens's sources, in per-source emission order; items from other sources are ignored.
 */
fun interface LensEvaluator {
    fun evaluate(
        lens: Lens,
        items: List<Item>,
        context: LensEvaluationContext,
    ): LensResult
}

/**
 * The reference evaluator. Pipeline, in order:
 *
 * 1. **Merge.** Keep items whose source is in `lens.sources`; order them by the source's position in
 *    `lens.sources` (repeats ignored), then by input order within the source.
 * 2. **Dedupe by [ItemId].** The first item in merged order wins: an earlier source in the lens beats a
 *    later one; within one source the first emitted wins. Dedupe happens before filtering, so
 *    `FromSource(b)` does not resurrect a duplicate that source `a` already won.
 * 3. **Bound.** Only the first `context.maxInputItems` merged items are considered.
 * 4. **Filter** on the unredacted item.
 * 5. **Sort** (see [LensSorting]); ties always break by item id.
 * 6. **Limit**: `lens.limit` caps the total number of items, applied after sorting and before grouping.
 *    For grouped results it is a total across all groups, not per group, so `limit = n` never yields
 *    more than n items whatever the shape, and it matches `Lens.resultShapes` (limit 1 means SINGLE).
 * 7. **Group** (see [LensGrouping]).
 * 8. **Project and redact** (see [LensProjection]); the only place redaction happens.
 */
object DefaultLensEvaluator : LensEvaluator {
    override fun evaluate(
        lens: Lens,
        items: List<Item>,
        context: LensEvaluationContext,
    ): LensResult {
        val filtered =
            LensMerge.merge(lens.sources, items, context.maxInputItems)
                .filter { LensFilterEvaluator.matches(lens.filter, it.item, context) }
        val limited =
            LensSorting.sort(filtered, lens.sort).map { it.item }.let { sorted ->
                lens.limit?.let(sorted::take) ?: sorted
            }
        val redact = { item: Item -> LensProjection.redact(item, lens.project) }
        return if (lens.group == LensGroup.None) {
            LensResult.Flat(limited.map(redact))
        } else {
            LensResult.Grouped(LensGrouping.group(limited, lens.group, context, redact))
        }
    }
}

internal object LensMerge {
    fun merge(
        sources: List<SourceId>,
        items: List<Item>,
        maxItems: Int,
    ): List<Ranked> {
        val rankOf = HashMap<SourceId, Int>()
        sources.forEach { rankOf.putIfAbsent(it, rankOf.size) }
        val seen = HashSet<ItemId>()
        return items
            .filter { it.sourceId in rankOf }
            .sortedBy { rankOf.getValue(it.sourceId) } // stable: keeps emission order within a source
            .filter { seen.add(it.id) }
            .take(maxItems)
            .mapIndexed { index, item -> Ranked(item, index) }
    }
}
