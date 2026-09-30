package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import java.util.Locale

/** An item plus its position in the merged input (source rank, then order within the source). */
internal data class Ranked(
    val item: Item,
    val order: Int,
)

/**
 * Ext flag (`Flag(true)`) that marks an item as pinned for `LensSort.pinnedFirst`. Sources (or, later,
 * user pinning) set it; the lens engine only reads it.
 */
val PINNED_EXT_KEY: ItemExtKey = ItemExtKey("launcher.pinned")

/**
 * Stable, total ordering for lens results.
 *
 * Sort keys are read from the item as it will be seen after redaction: a SENSITIVE item has no title and
 * is never pinned, so ordering cannot leak redacted content. Missing values sort last in both directions.
 * The final tie-breaker is always the item id ascending, so the result never depends on input order
 * (except `SOURCE_ORDER`, which is input order by definition).
 */
internal object LensSorting {
    fun sort(
        ranked: List<Ranked>,
        sort: LensSort,
    ): List<Ranked> = ranked.sortedWith(comparator(sort))

    fun isPinned(item: Item): Boolean =
        ItemField.EXT in LensProjection.visibleFields(item, ItemField.ALL) &&
            item.ext[PINNED_EXT_KEY] == ItemExtValue.Flag(true)

    private fun comparator(sort: LensSort): Comparator<Ranked> {
        val field = fieldComparator(sort)
        val ordered =
            if (sort.pinnedFirst) compareBy<Ranked> { if (isPinned(it.item)) 0 else 1 }.then(field) else field
        return ordered.thenBy { it.item.id.value }
    }

    private fun fieldComparator(sort: LensSort): Comparator<Ranked> {
        val descending = sort.direction == SortDirection.DESCENDING
        return when (sort.field) {
            LensSortField.SOURCE_ORDER ->
                if (descending) compareByDescending { it.order } else compareBy { it.order }
            LensSortField.TIME -> nullsLast(descending) { it.item.timeEpochMillis }
            LensSortField.TITLE ->
                nullsLast(descending) { titleOf(it.item)?.lowercase(Locale.ROOT) }
                    .then(nullsLast(descending) { titleOf(it.item) })
        }
    }

    private fun titleOf(item: Item): String? =
        item.title.takeIf { ItemField.TITLE in LensProjection.visibleFields(item, ItemField.ALL) }

    private fun <T : Comparable<T>> nullsLast(
        descending: Boolean,
        key: (Ranked) -> T?,
    ): Comparator<Ranked> =
        Comparator { a, b ->
            val left = key(a)
            val right = key(b)
            when {
                left == null && right == null -> 0
                left == null -> 1
                right == null -> -1
                descending -> right.compareTo(left)
                else -> left.compareTo(right)
            }
        }
}
