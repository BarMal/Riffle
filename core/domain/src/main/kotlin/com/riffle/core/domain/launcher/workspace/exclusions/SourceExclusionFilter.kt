package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.workspace.Item

/**
 * The pre-lens step: drops every item hidden by an enabled rule of the layout's [ExclusionRuleSet], order
 * preserved, before any lens filter, sort, limit or projection runs, so a lens can never show something
 * excluded. It only removes items; it never adds, reorders or exposes fields, and it keeps no item content.
 *
 * A rule applies to items of its own source and, for app and text matchers, of the sources in the same
 * family (apps, recent apps and shortcuts; notifications and media), because the legacy hidden-app and
 * notification-hide behaviour hid an app everywhere it appeared.
 */
object SourceExclusionFilter {
    /** Empty or fully-disabled rules return [items] itself. */
    fun apply(
        rules: ExclusionRuleSet,
        items: List<Item>,
    ): List<Item> {
        val active = rules.rules.filter { it.enabled }
        return if (active.isEmpty()) items else items.filterNot { item -> active.any { matches(it, item) } }
    }

    fun matches(
        rule: SourceExclusionRule,
        item: Item,
    ): Boolean = rule.enabled && appliesTo(rule, item) && ExclusionMatching.matches(rule.matcher, item)

    /** For the rules list: how many of [items] each enabled rule hides on its own (counts only, never stored). */
    fun matchCounts(
        rules: ExclusionRuleSet,
        items: List<Item>,
    ): Map<ExclusionRuleId, Int> =
        rules.rules.associate { rule -> rule.id to items.count { item -> matches(rule, item) } }

    private fun appliesTo(
        rule: SourceExclusionRule,
        item: Item,
    ): Boolean =
        rule.source == item.sourceId ||
            (ExclusionMatching.spansFamily(rule.matcher) && item.sourceId in ExclusionFamilies.of(rule.source))
}
