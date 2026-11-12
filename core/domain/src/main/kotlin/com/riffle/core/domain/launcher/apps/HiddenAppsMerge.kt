package com.riffle.core.domain.launcher.apps

import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatching
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule

/** What [HiddenAppsMerge.merge] decided: the one hidden list, the rules that are left, and whether anything moved. */
data class HiddenAppsMergeResult(
    val hidden: Set<AppIdentity>,
    val rules: LayoutExclusionRules,
) {
    /** True when [hidden] or [rules] differ from what went in, so there is something to store. */
    fun changed(
        storedHidden: Set<AppIdentity>,
        storedRules: LayoutExclusionRules,
    ): Boolean = hidden != storedHidden || rules != storedRules
}

/**
 * One hidden-apps list (ADR 0003, Library). Before the Library there were two: the hidden apps
 * ([AppVisibilityRepository]) that the app drawer read, and the app exclusion rules (source `apps.all`, an app matcher)
 * that workspace Finder pages read. The Library reads only the first, so every enabled all-apps exclusion rule is
 * folded into it, in whichever layout it was set.
 *
 * Nothing hidden becomes visible: a rule's apps are added to the list before the rule is dropped, and a rule that
 * matches no installed app (so it has nothing to add) is kept as it is. Nothing visible becomes hidden: only enabled
 * rules count, and only apps a rule matches. Rules of other sources (a recents or notification hide) are left alone.
 * A rule is per layout and the list is global, so an app hidden in one layout is hidden in all of them (the safe
 * direction for privacy). Pure and idempotent: a second run over its own result changes nothing.
 */
object HiddenAppsMerge {
    /** [installed] is every launchable app, hidden ones included. */
    fun merge(
        stored: Set<AppIdentity>,
        rules: LayoutExclusionRules,
        installed: List<InstalledApp>,
    ): HiddenAppsMergeResult {
        var hidden = stored
        val mergedLayouts =
            rules.layouts.mapValues { (_, set) ->
                val kept =
                    set.rules.filterNot { rule ->
                        val matches = rule.appHides(installed)
                        if (matches.isEmpty()) {
                            false
                        } else {
                            hidden = hidden + matches
                            true
                        }
                    }
                if (kept.size == set.rules.size) set else ExclusionRuleSet(kept)
            }
        return HiddenAppsMergeResult(hidden = hidden, rules = rules.copy(layouts = mergedLayouts))
    }

    /** The installed apps [this] rule hides from all apps; empty for any other kind of rule. */
    private fun SourceExclusionRule.appHides(installed: List<InstalledApp>): Set<AppIdentity> {
        val matcher = matcher
        if (!enabled || source != SourceIds.ALL_APPS || matcher !is ExclusionMatcher.App) return emptySet()
        return installed
            .filter { app -> ExclusionMatching.appMatches(matcher, app.identity) }
            .mapTo(LinkedHashSet()) { app -> app.identity }
    }
}
