package com.riffle.app.screenshots

import com.riffle.app.launcher.SettingsLayoutDeviceTab
import com.riffle.app.launcher.settingsLayoutDeviceTabs
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatchMode
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionOrigin
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsModel
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsPlanner

/** Fixed exclusion rules for the Settings > Hidden items and rules screenshots (fakes only, no repository). */
internal object ExclusionsSettingsFixtures {
    val phone = HomeLayoutDeviceClass.PHONE
    val foldable = HomeLayoutDeviceClass.FOLDABLE

    val tabs: List<SettingsLayoutDeviceTab> = settingsLayoutDeviceTabs(setOf(phone, foldable))

    val appRule = ExclusionRuleId("app")
    val feedRule = ExclusionRuleId("feed")
    val itemRule = ExclusionRuleId("item")
    val textRule = ExclusionRuleId("text")
    val patternRule = ExclusionRuleId("pattern")
    val emptyRule = ExclusionRuleId("empty")

    private fun rule(
        id: ExclusionRuleId,
        source: SourceId,
        matcher: ExclusionMatcher,
        enabled: Boolean = true,
        origin: ExclusionOrigin = ExclusionOrigin.USER,
    ) = SourceExclusionRule(id, source, matcher, enabled = enabled, origin = origin)

    private val shared =
        listOf(
            rule(
                appRule,
                SourceIds.ALL_APPS,
                ExclusionMatcher.App("com.example.chat", "personal", "Main"),
                origin = ExclusionOrigin.MIGRATED_HIDDEN_APP,
            ),
            rule(feedRule, SourceIds.RSS, ExclusionMatcher.Group("example-blog")),
            rule(itemRule, SourceIds.RSS, ExclusionMatcher.ItemKey("rss:example-blog:digest")),
            rule(
                textRule,
                SourceIds.NOTIFICATIONS,
                ExclusionMatcher.Text(ExclusionTextField.TITLE, "flash sale", ExclusionMatchMode.CONTAINS),
            ),
            rule(
                patternRule,
                SourceIds.NOTIFICATIONS,
                ExclusionMatcher.Text(
                    ExclusionTextField.BODY,
                    "order #{?} shipped",
                    ExclusionMatchMode.WILDCARD,
                    ExclusionMatcher.App("com.example.shop", "work"),
                ),
                enabled = false,
            ),
            rule(emptyRule, SourceIds.NOTIFICATIONS, ExclusionMatcher.EmptyContent()),
        )

    private val stored =
        LayoutExclusionRules(
            mapOf(
                phone to ExclusionRuleSet(shared),
                // The other layout has only the app rule, so the rest read "Only on this layout".
                foldable to ExclusionRuleSet(shared.take(1)),
            ),
            legacyMigrated = true,
        )

    private val counts =
        mapOf(appRule to 1, feedRule to 4, itemRule to 0, textRule to 2, emptyRule to 1)

    val populated: ExclusionsSettingsModel =
        ExclusionsSettingsPlanner.plan(stored, phone, tabs.map { it.deviceClass }, counts)

    /** The notification source is off and permission is missing for nothing else: counts are withheld for its rules. */
    val sourceOff: ExclusionsSettingsModel =
        ExclusionsSettingsPlanner.plan(
            stored,
            phone,
            tabs.map { it.deviceClass },
            counts,
            setOf(SourceIds.NOTIFICATIONS),
        )

    val empty: ExclusionsSettingsModel =
        ExclusionsSettingsPlanner.plan(LayoutExclusionRules(), phone, tabs.map { it.deviceClass })

    val otherLayout: ExclusionsSettingsModel =
        ExclusionsSettingsPlanner.plan(stored, foldable, tabs.map { it.deviceClass })
}
