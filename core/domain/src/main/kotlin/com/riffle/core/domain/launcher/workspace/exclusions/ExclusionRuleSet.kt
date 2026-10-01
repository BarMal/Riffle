package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds

/**
 * The exclusion rules of ONE layout, in creation order. Every operation is pure and returns a value; an
 * operation that cannot apply (unknown or duplicate id, user-rule cap reached) returns the receiver
 * unchanged. Migrated rules are exempt from [SourceExclusionRule.MAX_USER_RULES] so migration never drops
 * anything. Undo is simply restoring the previous value.
 */
data class ExclusionRuleSet(
    val rules: List<SourceExclusionRule> = emptyList(),
) {
    val isEmpty: Boolean get() = rules.isEmpty()

    fun find(id: ExclusionRuleId): SourceExclusionRule? = rules.firstOrNull { it.id == id }

    fun forSource(source: SourceId): List<SourceExclusionRule> = rules.filter { it.source == source }

    fun add(rule: SourceExclusionRule): ExclusionRuleSet =
        when {
            find(rule.id) != null -> this
            rule.origin == ExclusionOrigin.USER && userRuleCount() >= SourceExclusionRule.MAX_USER_RULES -> this
            else -> copy(rules = rules + rule.normalized())
        }

    /** Replaces the rule [id] with [transform]'s result (the id is kept); unknown ids are ignored. */
    fun edit(
        id: ExclusionRuleId,
        transform: (SourceExclusionRule) -> SourceExclusionRule,
    ): ExclusionRuleSet =
        if (find(id) == null) {
            this
        } else {
            copy(rules = rules.map { if (it.id == id) transform(it).copy(id = id).normalized() else it })
        }

    fun setEnabled(
        id: ExclusionRuleId,
        enabled: Boolean,
    ): ExclusionRuleSet = edit(id) { it.copy(enabled = enabled) }

    fun remove(id: ExclusionRuleId): ExclusionRuleSet = copy(rules = rules.filterNot { it.id == id })

    /**
     * Adds the rules of [other] that this set does not already have by (source, matcher), under this set's
     * ids rules untouched; an incoming id that is already taken is skipped. Idempotent.
     */
    fun merge(other: ExclusionRuleSet): ExclusionRuleSet =
        other.rules.fold(this) { acc, rule ->
            if (acc.rules.any { it.source == rule.source && it.matcher == rule.matcher }) acc else acc.add(rule)
        }

    /** True when an enabled app rule hides [identity] (the legacy hidden-apps question). */
    fun isAppHidden(identity: AppIdentity): Boolean =
        rules.any { rule ->
            rule.enabled && rule.source in ExclusionFamilies.APPS &&
                rule.matcher.let { matcher ->
                    matcher is ExclusionMatcher.App && ExclusionMatching.appMatches(matcher, identity)
                }
        }

    private fun userRuleCount(): Int = rules.count { it.origin == ExclusionOrigin.USER }

    private fun SourceExclusionRule.normalized(): SourceExclusionRule =
        copy(label = label?.trim()?.take(SourceExclusionRule.MAX_LABEL_LENGTH)?.takeIf(String::isNotEmpty))

    companion object {
        val EMPTY = ExclusionRuleSet()
    }
}

/** Sources that share rules because one thing the user hides appears in all of them. */
internal object ExclusionFamilies {
    val APPS: Set<SourceId> = setOf(SourceIds.ALL_APPS, SourceIds.RECENT_APPS, SourceIds.QUICK_ACTIONS)
    val NOTIFICATIONS: Set<SourceId> = setOf(SourceIds.NOTIFICATIONS, SourceIds.MEDIA)

    fun of(source: SourceId): Set<SourceId> =
        when (source) {
            in APPS -> APPS
            in NOTIFICATIONS -> NOTIFICATIONS
            else -> setOf(source)
        }
}
