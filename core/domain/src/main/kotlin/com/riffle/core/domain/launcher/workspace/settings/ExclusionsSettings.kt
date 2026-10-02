package com.riffle.core.domain.launcher.workspace.settings

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

/** The groups of Settings > Hidden items and rules, in display order. */
enum class ExclusionRuleKind {
    APPS,
    FEEDS_AND_GROUPS,
    ITEMS,
    TEXT,
    EMPTY_CONTENT,
    ;

    companion object {
        fun of(matcher: ExclusionMatcher): ExclusionRuleKind =
            when (matcher) {
                is ExclusionMatcher.App -> APPS
                is ExclusionMatcher.Group -> FEEDS_AND_GROUPS
                is ExclusionMatcher.ItemKey -> ITEMS
                is ExclusionMatcher.Text -> TEXT
                is ExclusionMatcher.EmptyContent -> EMPTY_CONTENT
            }
    }
}

/**
 * One rule as the page shows it. [description] says what the rule matches in words and never contains item
 * content: only identifiers the rule already holds (a package, a feed or group key) and the text the user
 * authored for a text rule. [matchCount] is how many items the rule hides right now, or null when unknown
 * (the source is off, needs permission or has not loaded); it is computed at runtime and never stored.
 */
data class ExclusionRuleRow(
    val id: ExclusionRuleId,
    val kind: ExclusionRuleKind,
    val sourceId: SourceId,
    val sourceTitle: String,
    val label: String?,
    val description: String,
    val enabled: Boolean,
    val migrated: Boolean,
    val sourceOff: Boolean,
    val onlyOnThisLayout: Boolean,
    val matchCount: Int?,
)

data class ExclusionRuleSection(
    val kind: ExclusionRuleKind,
    val rows: List<ExclusionRuleRow>,
)

/** The page as data for one layout. [sections] omits kinds with no rules. */
data class ExclusionsSettingsModel(
    val layout: HomeLayoutDeviceClass,
    val sections: List<ExclusionRuleSection>,
    val ruleCount: Int,
    val canAdd: Boolean,
    val canApplyToOtherLayouts: Boolean,
) {
    val isEmpty: Boolean get() = ruleCount == 0

    val rows: List<ExclusionRuleRow> get() = sections.flatMap { it.rows }

    fun row(id: ExclusionRuleId): ExclusionRuleRow? = rows.firstOrNull { it.id == id }
}

/** Plain-language descriptions of rules, used by the page and by announcements. Pure; never reads items. */
object ExclusionRuleDescriber {
    const val MAX_TEXT_SHOWN = 60

    fun describe(rule: SourceExclusionRule): String = describe(rule.source, rule.matcher)

    fun describe(
        source: SourceId,
        matcher: ExclusionMatcher,
    ): String =
        when (matcher) {
            is ExclusionMatcher.App -> appPhrase(source, matcher)
            is ExclusionMatcher.Group -> "${groupNoun(source)} ${shown(matcher.groupKey)}"
            is ExclusionMatcher.ItemKey -> "One specific item"
            is ExclusionMatcher.Text -> textPhrase(matcher)
            is ExclusionMatcher.EmptyContent ->
                "Items with no title and no text" + (matcher.app?.let { " from ${appName(it)}" } ?: "")
        }

    fun fieldName(field: ExclusionTextField): String =
        when (field) {
            ExclusionTextField.TITLE -> "Title"
            ExclusionTextField.SUBTITLE -> "Subtitle"
            ExclusionTextField.BODY -> "Body"
        }

    fun modeName(mode: ExclusionMatchMode): String =
        when (mode) {
            ExclusionMatchMode.EXACT -> "is exactly"
            ExclusionMatchMode.CONTAINS -> "contains"
            ExclusionMatchMode.WILDCARD -> "matches pattern"
        }

    private fun appPhrase(
        source: SourceId,
        matcher: ExclusionMatcher.App,
    ): String {
        val lead = if (source == SourceIds.NOTIFICATIONS || source == SourceIds.MEDIA) "Everything from " else "App "
        return lead + appName(matcher)
    }

    private fun appName(app: ExclusionMatcher.App): String =
        buildString {
            append(shown(app.packageName))
            app.profileId?.let { append(" (${profileName(it)})") }
            if (app.activityName != null) append(", one launcher entry")
        }

    private fun profileName(id: String): String =
        when (id) {
            "personal" -> "personal profile"
            "work" -> "work profile"
            "private" -> "private space"
            else -> "profile ${shown(id)}"
        }

    private fun groupNoun(source: SourceId): String =
        when (source) {
            SourceIds.RSS -> "Feed"
            SourceIds.ALL_APPS, SourceIds.RECENT_APPS, SourceIds.QUICK_ACTIONS -> "Category"
            else -> "Group"
        }

    private fun textPhrase(matcher: ExclusionMatcher.Text): String =
        "${fieldName(matcher.field)} ${modeName(matcher.mode)} \"${shown(matcher.value)}\"" +
            (matcher.app?.let { " from ${appName(it)}" } ?: "")

    private fun shown(text: String): String =
        if (text.length <= MAX_TEXT_SHOWN) text else text.take(MAX_TEXT_SHOWN - 1) + "…"
}

/**
 * Builds [ExclusionsSettingsModel]s. Rules keep their creation order inside a kind; kinds follow
 * [ExclusionRuleKind]. A rule is "only on this layout" when none of the other listed layouts has an equal
 * rule (same source and matcher), which is the cue that hidden things may reappear there.
 */
object ExclusionsSettingsPlanner {
    fun plan(
        rules: LayoutExclusionRules,
        viewed: HomeLayoutDeviceClass,
        otherLayouts: Collection<HomeLayoutDeviceClass>,
        matchCounts: Map<ExclusionRuleId, Int> = emptyMap(),
        disabledSources: Set<SourceId> = emptySet(),
    ): ExclusionsSettingsModel {
        val set = rules.forLayout(viewed)
        val others = otherLayouts.filter { it != viewed }.distinct()
        val rows =
            set.rules.map { rule ->
                val off = rule.source in disabledSources
                ExclusionRuleRow(
                    id = rule.id,
                    kind = ExclusionRuleKind.of(rule.matcher),
                    sourceId = rule.source,
                    sourceTitle = SourceCatalog.infoFor(rule.source).title,
                    label = rule.label,
                    description = ExclusionRuleDescriber.describe(rule),
                    enabled = rule.enabled,
                    migrated = rule.origin != ExclusionOrigin.USER,
                    sourceOff = off,
                    onlyOnThisLayout = others.isNotEmpty() && others.none { rules.forLayout(it).hasEqual(rule) },
                    matchCount = matchCounts[rule.id].takeUnless { off },
                )
            }
        val sections =
            ExclusionRuleKind.entries.mapNotNull { kind ->
                rows.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { ExclusionRuleSection(kind, it) }
            }
        return ExclusionsSettingsModel(
            layout = viewed,
            sections = sections,
            ruleCount = rows.size,
            canAdd = set.rules.count { it.origin == ExclusionOrigin.USER } < SourceExclusionRule.MAX_USER_RULES,
            canApplyToOtherLayouts = others.isNotEmpty(),
        )
    }

    private fun ExclusionRuleSet.hasEqual(rule: SourceExclusionRule): Boolean =
        rules.any { it.source == rule.source && it.matcher == rule.matcher }
}
