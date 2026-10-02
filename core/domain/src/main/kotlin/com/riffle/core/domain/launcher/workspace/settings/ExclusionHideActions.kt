package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleBuilders
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule

/** The contextual "Hide ..." choices an item can offer. */
enum class HideKind {
    APP,
    GROUP,
    ITEM,
    LIKE_THIS,
    EMPTY_CONTENT,
}

/**
 * One contextual Hide choice for an item: [label] is the action's text (its vocabulary follows the item's source,
 * section 14.4 of the design) and [what] the fixed phrase announcements use ("Hidden on Phone: [what]"). Neither
 * ever contains item content.
 */
data class HideChoice(
    val kind: HideKind,
    val label: String,
    val what: String,
)

/**
 * The pure core of the contextual Hide action (design 14.7): which choices an item supports and what applying one
 * does. The caller (a long-press or overflow menu, a TalkBack custom action) shows [choicesFor], calls [apply] with
 * the user's pick and announces the returned message with Undo. Nothing here creates a rule on its own: every rule
 * is the result of a user's explicit choice, and the change always carries its own Undo ([ExclusionsChange.undo]).
 */
object ExclusionHideActions {
    /** The choices [item] supports, in menu order. Sensitive items only offer the structural ones. */
    fun choicesFor(
        item: Item,
        builders: ExclusionRuleBuilders = ExclusionRuleBuilders(),
    ): List<HideChoice> =
        HideKind.entries.mapNotNull { kind ->
            if (build(builders, item, kind) == null) null else choice(kind, item.sourceId)
        }

    /** Hides what [kind] describes for [item] on [layout] (and every layout when [allLayouts]). */
    fun apply(
        rules: LayoutExclusionRules,
        layout: HomeLayoutDeviceClass,
        item: Item,
        kind: HideKind,
        allLayouts: Boolean = false,
        builders: ExclusionRuleBuilders = ExclusionRuleBuilders(),
    ): ExclusionsChange {
        val built = build(builders, item, kind)
        val what = choice(kind, item.sourceId).what
        if (built == null) return ExclusionsChange.refused(rules, ExclusionsMessage.CannotHide)
        val set = rules.forLayout(layout)
        val existing = set.rules.firstOrNull { it.source == built.source && it.matcher == built.matcher }
        val onThisLayout =
            when {
                existing == null -> rules.update(layout) { it.add(built) }
                existing.enabled -> rules
                else -> rules.update(layout) { it.setEnabled(existing.id, true) }
            }
        val representative: SourceExclusionRule = existing ?: built
        val after =
            if (allLayouts) {
                onThisLayout.copyToOtherLayouts(
                    layout,
                    representative.copy(enabled = true),
                )
            } else {
                onThisLayout
            }
        return if (after == rules) {
            ExclusionsChange.refused(rules, ExclusionsMessage.AlreadyHidden(what))
        } else {
            ExclusionsChange(rules, after, applied = true, undoable = true, ExclusionsMessage.Hidden(layout, what))
        }
    }

    private fun build(
        builders: ExclusionRuleBuilders,
        item: Item,
        kind: HideKind,
    ): SourceExclusionRule? =
        when (kind) {
            HideKind.APP -> builders.hideApp(item)
            HideKind.GROUP -> builders.hideGroup(item)
            HideKind.ITEM -> builders.hideItem(item)
            HideKind.EMPTY_CONTENT -> builders.hideEmptyContent(item)
            HideKind.LIKE_THIS ->
                listOf(ExclusionTextField.TITLE, ExclusionTextField.BODY, ExclusionTextField.SUBTITLE)
                    .firstNotNullOfOrNull { builders.hideLike(item, it) }
        }

    private fun choice(
        kind: HideKind,
        source: SourceId,
    ): HideChoice {
        val (label, what) = VOCABULARY.getValue(kind).let { it.bySource[source] ?: it.default }
        return HideChoice(kind, label, what)
    }

    /** A choice's label and the phrase announcements use ("Hide this app" to "this app"). */
    private class Words(
        val default: Pair<String, String>,
        val bySource: Map<SourceId, Pair<String, String>> = emptyMap(),
    )

    /** Per-source vocabulary of design section 14.4; sources without an entry use the generic words. */
    private val VOCABULARY: Map<HideKind, Words> =
        mapOf(
            HideKind.APP to
                Words(
                    "Hide this app" to "this app",
                    mapOf(
                        SourceIds.NOTIFICATIONS to
                            ("Hide notifications from this app" to "notifications from this app"),
                        SourceIds.MEDIA to ("Hide media from this app" to "media from this app"),
                    ),
                ),
            HideKind.GROUP to
                Words(
                    "Hide this group" to "this group",
                    mapOf(
                        SourceIds.RSS to ("Hide this feed" to "this feed"),
                        SourceIds.NOTIFICATIONS to ("Hide this notification group" to "this notification group"),
                        SourceIds.ALL_APPS to ("Hide this category" to "this category"),
                        SourceIds.RECENT_APPS to ("Hide this category" to "this category"),
                        SourceIds.QUICK_ACTIONS to ("Hide this category" to "this category"),
                    ),
                ),
            HideKind.ITEM to
                Words(
                    "Hide this item" to "this item",
                    mapOf(
                        SourceIds.RSS to ("Hide this article" to "this article"),
                        SourceIds.CALENDAR to ("Hide this event" to "this event"),
                        SourceIds.SEARCH to ("Hide this result" to "this result"),
                        SourceIds.NOTIFICATIONS to ("Hide this notification" to "this notification"),
                    ),
                ),
            HideKind.LIKE_THIS to
                Words(
                    "Hide items like this" to "items like this",
                    mapOf(
                        SourceIds.CALENDAR to ("Hide events like this" to "events like this"),
                        SourceIds.NOTIFICATIONS to ("Hide notifications like this" to "notifications like this"),
                    ),
                ),
            HideKind.EMPTY_CONTENT to
                Words(
                    "Hide items with no content" to "items with no content",
                    mapOf(SourceIds.NOTIFICATIONS to ("Hide empty notifications" to "empty notifications")),
                ),
        )
}
