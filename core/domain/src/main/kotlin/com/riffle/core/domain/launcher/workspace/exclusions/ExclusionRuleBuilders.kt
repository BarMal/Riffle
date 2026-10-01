package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/**
 * Builds the rule behind a contextual "Hide ..." action from the item the user acted on. Each builder
 * returns null when the item cannot support that rule (no app, no group, sensitive or too-short text), so
 * the caller never creates a rule it could not explain. The caller adds the rule through
 * [ExclusionRuleSet.add] and keeps the previous set as the Undo token.
 */
class ExclusionRuleBuilders(
    private val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    /** "Hide this app / these notifications / this media": the item's app, any activity. */
    fun hideApp(item: Item): SourceExclusionRule? = appOf(item)?.let { rule(item, it) }

    /** "Hide this feed / category": everything sharing the item's group key. */
    fun hideGroup(item: Item): SourceExclusionRule? = item.groupKey?.let { rule(item, ExclusionMatcher.Group(it)) }

    /** "Hide this article / event / result": exactly this item. */
    fun hideItem(item: Item): SourceExclusionRule = rule(item, ExclusionMatcher.ItemKey(item.id.value))

    /** "Hide items with no content" for the item's app. Null for sensitive items, whose content is unknown. */
    fun hideEmptyContent(item: Item): SourceExclusionRule? =
        item.takeIf { it.privacy == ItemPrivacy.VISIBLE }?.let { rule(it, ExclusionMatcher.EmptyContent(appOf(it))) }

    /**
     * "Hide ones like this": a text rule from the item's [field]. With [generalizeNumbers] a text with digits
     * becomes a wildcard over them (as the legacy rule did); otherwise it is an exact match. Null for
     * sensitive items and for text shorter than [MIN_TEXT_LENGTH].
     */
    fun hideLike(
        item: Item,
        field: ExclusionTextField,
        generalizeNumbers: Boolean = true,
    ): SourceExclusionRule? {
        val text =
            when (field) {
                ExclusionTextField.TITLE -> item.title
                ExclusionTextField.SUBTITLE -> item.subtitle
                ExclusionTextField.BODY -> item.body
            }
        if (item.privacy != ItemPrivacy.VISIBLE || text == null) return null
        val wildcard = if (generalizeNumbers) NotificationHideRule.generalizeNumbers(text) else null
        val value = wildcard ?: NotificationHideRule.normalize(text)
        val mode = if (wildcard != null) ExclusionMatchMode.WILDCARD else ExclusionMatchMode.EXACT
        return value.takeIf { it.length >= MIN_TEXT_LENGTH }
            ?.let { rule(item, ExclusionMatcher.Text(field, it, mode, appOf(item))) }
    }

    private fun appOf(item: Item): ExclusionMatcher.App? =
        when (val target = item.target) {
            is ItemTarget.App -> ExclusionMatcher.App(target.packageName, target.profileId)
            is ItemTarget.Shortcut -> ExclusionMatcher.App(target.packageName, target.profileId)
            else -> null
        }

    private fun rule(
        item: Item,
        matcher: ExclusionMatcher,
    ): SourceExclusionRule =
        SourceExclusionRule(
            id = ExclusionRuleId(ids.next()),
            source = item.sourceId,
            matcher = matcher,
            createdAtEpochMillis = nowEpochMillis().coerceAtLeast(0L),
        )

    companion object {
        const val MIN_TEXT_LENGTH = 3
    }
}
