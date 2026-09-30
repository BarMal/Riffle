package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemGroup
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.LensGroup

/**
 * Splits an already sorted and limited item list into [ItemGroup]s.
 *
 * Groups appear in order of their first member in the sorted list, so group order follows the lens sort
 * (for example `TIME` descending puts the most recent day first). Items with no key for the grouping (no
 * group key, no time, no ext value, or a SENSITIVE item under `ByExt`, whose ext is redacted) go to one
 * trailing group with key [UNGROUPED_KEY] and a null label. Labels fall back to the key.
 */
internal object LensGrouping {
    /** Key of the trailing group that holds items the grouping cannot place. Never a real key. */
    const val UNGROUPED_KEY = ""

    fun group(
        items: List<Item>,
        group: LensGroup,
        context: LensEvaluationContext,
        redact: (Item) -> Item,
    ): List<ItemGroup> {
        val keyed = LinkedHashMap<String, MutableList<Item>>()
        val ungrouped = mutableListOf<Item>()
        items.forEach { item ->
            val key = keyOf(item, group, context)
            if (key == null) ungrouped += item else keyed.getOrPut(key) { mutableListOf() } += item
        }
        val groups =
            keyed.map { (key, members) ->
                ItemGroup(key, labelOf(key, members, group), members.map(redact))
            }
        return if (ungrouped.isEmpty()) groups else groups + ItemGroup(UNGROUPED_KEY, null, ungrouped.map(redact))
    }

    private fun keyOf(
        item: Item,
        group: LensGroup,
        context: LensEvaluationContext,
    ): String? =
        when (group) {
            LensGroup.None -> null
            LensGroup.ByGroupKey -> item.groupKey?.takeIf { it.isNotBlank() }
            LensGroup.ByDay -> item.timeEpochMillis?.let(context.dayBucketer::dayKey)
            is LensGroup.ByExt ->
                item.ext[group.key].takeIf { item.privacy != ItemPrivacy.SENSITIVE }?.let(::extText)
        }?.takeIf { it != UNGROUPED_KEY }

    private fun extText(value: ItemExtValue): String =
        when (value) {
            is ItemExtValue.Text -> value.value
            is ItemExtValue.Number -> value.value.toString()
            is ItemExtValue.Flag -> value.value.toString()
        }

    private fun labelOf(
        key: String,
        members: List<Item>,
        group: LensGroup,
    ): String =
        if (group == LensGroup.ByGroupKey) {
            members.firstNotNullOfOrNull { it.groupLabel?.takeIf(String::isNotBlank) } ?: key
        } else {
            key
        }
}
