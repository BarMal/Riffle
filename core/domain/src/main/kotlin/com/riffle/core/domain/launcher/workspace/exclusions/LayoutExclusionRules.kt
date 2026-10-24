package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceLayoutFold
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

/**
 * Every layout's [ExclusionRuleSet], keyed by [HomeLayoutDeviceClass]: membership in a set is the scope, so
 * a rule in one layout never hides anything in another. Held apart from workspaces, so resetting or copying
 * workspaces can never un-hide anything. [legacyMigrated] records that the global hidden apps and hide rules
 * have been copied in (see [ExclusionMigration]) so a rule the user later deletes is never re-created.
 *
 * Keyed by [workspaceLayoutClass] (N10): `PHONE_LANDSCAPE` reads and writes `PHONE`'s rules, so rotating never
 * changes what is hidden. Stored data is folded once on load ([folded]).
 */
data class LayoutExclusionRules(
    val layouts: Map<HomeLayoutDeviceClass, ExclusionRuleSet> = emptyMap(),
    val legacyMigrated: Boolean = false,
) {
    fun forLayout(deviceClass: HomeLayoutDeviceClass): ExclusionRuleSet =
        layouts[deviceClass.workspaceLayoutClass()] ?: ExclusionRuleSet.EMPTY

    fun update(
        deviceClass: HomeLayoutDeviceClass,
        transform: (ExclusionRuleSet) -> ExclusionRuleSet,
    ): LayoutExclusionRules =
        copy(layouts = layouts + (deviceClass.workspaceLayoutClass() to transform(forLayout(deviceClass))))

    /**
     * This value with a `PHONE_LANDSCAPE` rule set (stored before N10) merged into `PHONE`'s, so nothing that was
     * hidden in either becomes visible: `PHONE`'s rules are kept as they are, then each landscape rule is appended
     * unless `PHONE` already has its id (`PHONE`'s wins), or an equal rule (same source and matcher) that hides at
     * least as much (enabled, or the landscape one is disabled too). Appended rules bypass the user-rule cap, as
     * dropping one could un-hide something. Pure, idempotent, never throws.
     */
    fun folded(): LayoutExclusionRules =
        if (WorkspaceLayoutFold.needsFold(layouts.keys)) {
            copy(layouts = WorkspaceLayoutFold.foldMap(layouts) { own, landscape -> own.mergedFold(landscape) })
        } else {
            this
        }
}

private fun ExclusionRuleSet.mergedFold(folded: ExclusionRuleSet): ExclusionRuleSet {
    val added =
        folded.rules.filter { rule ->
            find(rule.id) == null &&
                rules.none { it.source == rule.source && it.matcher == rule.matcher && (it.enabled || !rule.enabled) }
        }
    return if (added.isEmpty()) this else ExclusionRuleSet((rules + added).distinctBy { it.id })
}
