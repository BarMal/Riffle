package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/**
 * Every layout's [ExclusionRuleSet], keyed by [HomeLayoutDeviceClass]: membership in a set is the scope, so
 * a rule in one layout never hides anything in another. Held apart from workspaces, so resetting or copying
 * workspaces can never un-hide anything. [legacyMigrated] records that the global hidden apps and hide rules
 * have been copied in (see [ExclusionMigration]) so a rule the user later deletes is never re-created.
 */
data class LayoutExclusionRules(
    val layouts: Map<HomeLayoutDeviceClass, ExclusionRuleSet> = emptyMap(),
    val legacyMigrated: Boolean = false,
) {
    fun forLayout(deviceClass: HomeLayoutDeviceClass): ExclusionRuleSet = layouts[deviceClass] ?: ExclusionRuleSet.EMPTY

    fun update(
        deviceClass: HomeLayoutDeviceClass,
        transform: (ExclusionRuleSet) -> ExclusionRuleSet,
    ): LayoutExclusionRules = copy(layouts = layouts + (deviceClass to transform(forLayout(deviceClass))))
}
