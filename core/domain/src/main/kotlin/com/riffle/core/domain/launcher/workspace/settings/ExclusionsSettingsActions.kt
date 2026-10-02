package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatchMode
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionOrigin
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule

/** What the user typed to add a text rule. */
data class TextRuleDraft(
    val source: SourceId,
    val field: ExclusionTextField,
    val mode: ExclusionMatchMode,
    val value: String,
)

enum class TextRuleProblem {
    /** Fewer than [TextRuleValidator.MIN_LENGTH] characters that are not wildcards. */
    TOO_SHORT,
    TOO_LONG,
    UNSUPPORTED_SOURCE,
    ALREADY_EXISTS,
    LIMIT_REACHED,
}

/**
 * Validation of a typed text rule. The minimum matches the contextual "hide ones like this" builder, so a
 * rule that is too broad to be useful (or to be explained) is refused with a reason rather than created.
 */
object TextRuleValidator {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 120

    /** The sources a typed text rule can target: the ones whose items carry text. */
    val SOURCES: List<SourceId> =
        listOf(SourceIds.NOTIFICATIONS, SourceIds.MEDIA, SourceIds.CALENDAR, SourceIds.RSS, SourceIds.ALL_APPS)

    /** The rule [draft] describes, as stored (value normalised like the evaluator does). */
    fun matcherOf(draft: TextRuleDraft): ExclusionMatcher.Text =
        ExclusionMatcher.Text(draft.field, NotificationHideRule.normalize(draft.value), draft.mode)

    /** The first problem with [draft] against the layout's [existing] rules, or null when it can be added. */
    fun validate(
        draft: TextRuleDraft,
        existing: ExclusionRuleSet,
    ): TextRuleProblem? {
        val matcher = matcherOf(draft)
        return when {
            draft.source !in SOURCES -> TextRuleProblem.UNSUPPORTED_SOURCE
            literalLength(matcher) < MIN_LENGTH -> TextRuleProblem.TOO_SHORT
            matcher.value.length > MAX_LENGTH -> TextRuleProblem.TOO_LONG
            existing.rules.any { it.source == draft.source && it.matcher == matcher } -> TextRuleProblem.ALREADY_EXISTS
            existing.rules.count { it.origin == ExclusionOrigin.USER } >= SourceExclusionRule.MAX_USER_RULES ->
                TextRuleProblem.LIMIT_REACHED
            else -> null
        }
    }

    /** What the add dialog's button does: the action for a valid [draft], or null (button disabled) when invalid. */
    fun actionFor(
        draft: TextRuleDraft,
        existing: ExclusionRuleSet,
    ): ExclusionsSettingsAction.AddText? =
        if (validate(draft, existing) == null) ExclusionsSettingsAction.AddText(draft) else null

    /** Characters that must match literally: a wildcard's `{?}` placeholders do not count. */
    private fun literalLength(matcher: ExclusionMatcher.Text): Int =
        if (matcher.mode == ExclusionMatchMode.WILDCARD) {
            matcher.value.replace(WILDCARD_PLACEHOLDER, "").trim().length
        } else {
            matcher.value.length
        }

    const val WILDCARD_PLACEHOLDER = "{?}"
}

/** What the page can ask for. Every one is a pure operation over [LayoutExclusionRules]. */
sealed interface ExclusionsSettingsAction {
    data class SetEnabled(val id: ExclusionRuleId, val enabled: Boolean) : ExclusionsSettingsAction

    data class Delete(val id: ExclusionRuleId) : ExclusionsSettingsAction

    data class AddText(val draft: TextRuleDraft) : ExclusionsSettingsAction

    /** Adds a copy of the rule to every other layout (de-duplicated by source and matcher). */
    data class ApplyToAllLayouts(val id: ExclusionRuleId) : ExclusionsSettingsAction
}

/** The outcome of an action, for the announcement. Only the descriptions the page already shows appear. */
sealed interface ExclusionsMessage {
    data class Enabled(val description: String) : ExclusionsMessage

    data class Disabled(val description: String) : ExclusionsMessage

    data class Deleted(val description: String) : ExclusionsMessage

    data class Added(val description: String) : ExclusionsMessage

    data class AppliedToAll(val added: Int) : ExclusionsMessage

    /** Contextual hide; [what] is a fixed phrase per kind of rule, never taken from the item. */
    data class Hidden(val layout: HomeLayoutDeviceClass, val what: String) : ExclusionsMessage

    data class AlreadyHidden(val what: String) : ExclusionsMessage

    data class Problem(val problem: TextRuleProblem) : ExclusionsMessage

    data object UnknownRule : ExclusionsMessage

    /** The item cannot support the chosen rule (no app, no group, sensitive or too-short text). */
    data object CannotHide : ExclusionsMessage

    data object NothingToDo : ExclusionsMessage
}

/**
 * The result of one action. [before] is the whole stored value the action replaced, so [undo] is simply
 * putting it back; the caller must only offer it while nothing else has changed since.
 */
data class ExclusionsChange(
    val before: LayoutExclusionRules,
    val after: LayoutExclusionRules,
    val applied: Boolean,
    val undoable: Boolean,
    val message: ExclusionsMessage,
) {
    fun undo(): LayoutExclusionRules = before

    companion object {
        fun refused(
            rules: LayoutExclusionRules,
            message: ExclusionsMessage,
        ) = ExclusionsChange(rules, rules, applied = false, undoable = false, message = message)
    }
}

/** Applies an [ExclusionsSettingsAction] to one layout's rules. Pure: ids and the clock are injected. */
fun ExclusionsSettingsAction.applyTo(
    rules: LayoutExclusionRules,
    layout: HomeLayoutDeviceClass,
    ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
): ExclusionsChange {
    val set = rules.forLayout(layout)
    return when (this) {
        is ExclusionsSettingsAction.SetEnabled -> setEnabled(rules, layout, set)
        is ExclusionsSettingsAction.Delete -> delete(rules, layout, set)
        is ExclusionsSettingsAction.AddText -> addText(rules, layout, set, ids, nowEpochMillis)
        is ExclusionsSettingsAction.ApplyToAllLayouts -> applyToAll(rules, layout, set, ids)
    }
}

private fun ExclusionsSettingsAction.SetEnabled.setEnabled(
    rules: LayoutExclusionRules,
    layout: HomeLayoutDeviceClass,
    set: ExclusionRuleSet,
): ExclusionsChange {
    val rule = set.find(id)
    return when {
        rule == null -> ExclusionsChange.refused(rules, ExclusionsMessage.UnknownRule)
        rule.enabled == enabled -> ExclusionsChange.refused(rules, ExclusionsMessage.NothingToDo)
        else -> {
            val description = ExclusionRuleDescriber.describe(rule)
            ExclusionsChange(
                before = rules,
                after = rules.update(layout) { it.setEnabled(id, enabled) },
                applied = true,
                undoable = false,
                message =
                    if (enabled) ExclusionsMessage.Enabled(description) else ExclusionsMessage.Disabled(description),
            )
        }
    }
}

private fun ExclusionsSettingsAction.Delete.delete(
    rules: LayoutExclusionRules,
    layout: HomeLayoutDeviceClass,
    set: ExclusionRuleSet,
): ExclusionsChange {
    val rule = set.find(id) ?: return ExclusionsChange.refused(rules, ExclusionsMessage.UnknownRule)
    return ExclusionsChange(
        before = rules,
        after = rules.update(layout) { it.remove(id) },
        applied = true,
        undoable = true,
        message = ExclusionsMessage.Deleted(ExclusionRuleDescriber.describe(rule)),
    )
}

private fun ExclusionsSettingsAction.AddText.addText(
    rules: LayoutExclusionRules,
    layout: HomeLayoutDeviceClass,
    set: ExclusionRuleSet,
    ids: WorkspaceIdFactory,
    nowEpochMillis: () -> Long,
): ExclusionsChange {
    TextRuleValidator.validate(draft, set)?.let {
        return ExclusionsChange.refused(rules, ExclusionsMessage.Problem(it))
    }
    val rule =
        SourceExclusionRule(
            id = ExclusionRuleId(ids.next()),
            source = draft.source,
            matcher = TextRuleValidator.matcherOf(draft),
            createdAtEpochMillis = nowEpochMillis().coerceAtLeast(0L),
        )
    return ExclusionsChange(
        before = rules,
        after = rules.update(layout) { it.add(rule) },
        applied = true,
        undoable = true,
        message = ExclusionsMessage.Added(ExclusionRuleDescriber.describe(rule)),
    )
}

private fun ExclusionsSettingsAction.ApplyToAllLayouts.applyToAll(
    rules: LayoutExclusionRules,
    layout: HomeLayoutDeviceClass,
    set: ExclusionRuleSet,
    ids: WorkspaceIdFactory,
): ExclusionsChange {
    val rule = set.find(id) ?: return ExclusionsChange.refused(rules, ExclusionsMessage.UnknownRule)
    val after = rules.copyToOtherLayouts(layout, rule, ids)
    val added =
        HomeLayoutDeviceClass.entries.sumOf { other ->
            after.forLayout(other).rules.size - rules.forLayout(other).rules.size
        }
    return if (added == 0) {
        ExclusionsChange.refused(rules, ExclusionsMessage.NothingToDo)
    } else {
        ExclusionsChange(rules, after, applied = true, undoable = true, message = ExclusionsMessage.AppliedToAll(added))
    }
}

/**
 * Adds a copy of [rule] (fresh id, as a user rule, enabled state kept) to every layout except [layout], skipping
 * layouts that already have an equal rule by source and matcher. Idempotent.
 */
fun LayoutExclusionRules.copyToOtherLayouts(
    layout: HomeLayoutDeviceClass,
    rule: SourceExclusionRule,
    ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
): LayoutExclusionRules =
    HomeLayoutDeviceClass.entries.filter { it != layout }.fold(this) { acc, other ->
        val copy = rule.copy(id = ExclusionRuleId(ids.next()), origin = ExclusionOrigin.USER)
        val current = acc.forLayout(other)
        val merged = current.merge(ExclusionRuleSet(listOf(copy)))
        if (merged == current) acc else acc.update(other) { merged }
    }
