package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.workspace.SourceIds

/**
 * Copies the legacy, global hidden apps and [NotificationHideRule]s into EVERY layout's rule set, once.
 *
 * Deterministic: ids are `mig:<layout>:app:<profile>:<package>/<activity>` and `mig:<layout>:notif:<ruleId>`
 * and the output order follows a stable sort of the inputs, so the same inputs give the same value.
 * Idempotent: once [LayoutExclusionRules.legacyMigrated] is set the input is returned unchanged, so a rule
 * the user deleted or edited afterwards is never re-created or overwritten; before that, rules whose id is
 * already present are kept as they are and migrated rules are exempt from the user-rule cap. The legacy
 * stores are only read, never changed.
 */
object ExclusionMigration {
    fun migrate(
        existing: LayoutExclusionRules,
        hiddenApps: Set<AppIdentity>,
        hideRules: List<NotificationHideRule>,
    ): LayoutExclusionRules {
        if (existing.legacyMigrated) return existing
        val sortedApps =
            hiddenApps.sortedWith(
                compareBy({
                    it.profile.id.value
                }, { it.packageName.value }, { it.activityName.value }),
            )
        val migrated =
            HomeLayoutDeviceClass.entries.fold(existing) { acc, layout ->
                val rules = sortedApps.map { appRule(layout, it) } + hideRules.map { hideRule(layout, it) }
                acc.update(layout) { set -> rules.fold(set) { s, rule -> s.addMigrated(rule) } }
            }
        return migrated.copy(legacyMigrated = true)
    }

    private fun ExclusionRuleSet.addMigrated(rule: SourceExclusionRule): ExclusionRuleSet =
        if (find(rule.id) != null) this else copy(rules = rules + rule)

    private fun appRule(
        layout: HomeLayoutDeviceClass,
        identity: AppIdentity,
    ): SourceExclusionRule {
        val profile = identity.profile.id.value
        val pkg = identity.packageName.value
        val activity = identity.activityName.value
        return SourceExclusionRule(
            id = ExclusionRuleId("mig:${layout.name}:app:$profile:$pkg/$activity"),
            source = SourceIds.ALL_APPS,
            matcher = ExclusionMatcher.App(pkg, profile, activity),
            origin = ExclusionOrigin.MIGRATED_HIDDEN_APP,
        )
    }

    private fun hideRule(
        layout: HomeLayoutDeviceClass,
        rule: NotificationHideRule,
    ): SourceExclusionRule {
        val app = ExclusionMatcher.App(rule.packageName.value, rule.profileId.value)
        val mode = ExclusionMatchMode.valueOf(rule.matchMode.name)
        val matcher =
            when (rule.kind) {
                NotificationHideRule.Kind.APP -> app
                NotificationHideRule.Kind.TITLE ->
                    ExclusionMatcher.Text(
                        ExclusionTextField.TITLE,
                        rule.value,
                        mode,
                        app,
                    )
                NotificationHideRule.Kind.BODY -> ExclusionMatcher.Text(ExclusionTextField.BODY, rule.value, mode, app)
                NotificationHideRule.Kind.EMPTY_CONTENT -> ExclusionMatcher.EmptyContent(app)
            }
        return SourceExclusionRule(
            id = ExclusionRuleId("mig:${layout.name}:notif:${rule.id.value}"),
            source = SourceIds.NOTIFICATIONS,
            matcher = matcher,
            origin = ExclusionOrigin.MIGRATED_HIDE_RULE,
        )
    }
}
