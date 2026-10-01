package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatchMode
import com.riffle.core.domain.launcher.workspace.settings.ExclusionRuleKind
import com.riffle.core.domain.launcher.workspace.settings.ExclusionRuleRow

/** User-visible wording for Settings > Hidden items and rules. Plain strings so JVM tests can check them. */
internal object ExclusionsSettingsText {
    const val TITLE = "Hidden items and rules"
    const val ROW_SUBTITLE = "Rules that hide apps, feeds, items and text from your pages"
    const val INTRO =
        "Hidden items are removed before any page or lens can show them. Each layout keeps its own rules, so " +
            "something hidden on one layout can reappear on another."
    const val NOT_LOADED = "Rules are still loading. Open this page again in a moment."
    const val EMPTY_TITLE = "Nothing is hidden on this layout"
    const val EMPTY_BODY =
        "Rules appear here when you hide something, or add a text rule below. Nothing is hidden until a rule exists."
    const val ADD_TEXT_RULE = "Add text rule..."
    const val ADD_TEXT_RULE_BODY = "Hide items whose title, subtitle or body matches text you type"
    const val ADD_TITLE = "Add text rule"
    const val ADD_CONFIRM = "Add rule"
    const val SOURCE_LABEL = "Applies to"
    const val FIELD_LABEL = "Text field"
    const val MODE_LABEL = "How it matches"
    const val VALUE_LABEL = "Text to match"
    const val WILDCARD_HELP = "In a pattern, {?} stands for any text."
    const val ADD_HELP = "Letters are matched without regard to case and extra spaces."
    const val DELETE = "Delete"
    const val DELETE_CONFIRM = "Delete"
    const val DELETE_TITLE = "Delete rule?"
    const val CANCEL = "Cancel"
    const val UNDO = "Undo"
    const val APPLY_TO_ALL = "Apply to all layouts"
    const val TURN_ON = "Turn on"
    const val TURN_OFF = "Turn off"
    const val MIGRATED = "From earlier settings"
    const val ONLY_THIS_LAYOUT = "Only on this layout"
    const val TURNED_OFF = "Turned off"
    const val SOURCE_IS_OFF = "Source is off"
    const val COUNT_UNAVAILABLE = "Hidden count unavailable"
    const val LAYOUT_NOTE_PREFIX = "Rules for"

    fun kindTitle(kind: ExclusionRuleKind): String =
        when (kind) {
            ExclusionRuleKind.APPS -> "Apps"
            ExclusionRuleKind.FEEDS_AND_GROUPS -> "Feeds and groups"
            ExclusionRuleKind.ITEMS -> "Single items"
            ExclusionRuleKind.TEXT -> "Text rules"
            ExclusionRuleKind.EMPTY_CONTENT -> "Items with no content"
        }

    fun layoutSummary(
        layoutName: String,
        ruleCount: Int,
    ): String =
        when (ruleCount) {
            0 -> "$LAYOUT_NOTE_PREFIX $layoutName: no rules"
            1 -> "$LAYOUT_NOTE_PREFIX $layoutName: 1 rule"
            else -> "$LAYOUT_NOTE_PREFIX $layoutName: $ruleCount rules"
        }

    fun moreActionsDescription(row: ExclusionRuleRow): String = "More actions for ${title(row)}"

    fun title(row: ExclusionRuleRow): String = row.label ?: row.description

    fun hidesCount(count: Int): String =
        when (count) {
            0 -> "Hides nothing right now"
            1 -> "Hides 1 item right now"
            else -> "Hides $count items right now"
        }

    /** The status line under a rule: the count, "Turned off", or why there is no count. */
    fun statusLine(row: ExclusionRuleRow): String? {
        val count = row.matchCount
        return when {
            !row.enabled -> TURNED_OFF
            row.sourceOff -> SOURCE_IS_OFF
            count != null -> hidesCount(count)
            else -> null
        }
    }

    /** Chips under a rule, as words (never colour alone). */
    fun badges(row: ExclusionRuleRow): List<String> =
        listOfNotNull(
            row.sourceTitle,
            MIGRATED.takeIf { row.migrated },
            ONLY_THIS_LAYOUT.takeIf { row.onlyOnThisLayout },
        )

    /** What TalkBack reads for a rule row, which also says what tapping it does. */
    fun rowSpokenSummary(
        row: ExclusionRuleRow,
        position: Int,
        total: Int,
    ): String =
        buildString {
            append(title(row))
            if (row.label != null) append(", ${row.description}")
            append(". ")
            append(kindTitle(row.kind))
            append(", ")
            append(badges(row).joinToString(separator = ", "))
            append(". ")
            append(statusLine(row) ?: COUNT_UNAVAILABLE)
            append(". ")
            append("Rule $position of $total. ")
            append(if (row.enabled) "On. Double tap to turn off" else "Off. Double tap to turn on")
        }

    fun modeName(mode: ExclusionMatchMode): String =
        when (mode) {
            ExclusionMatchMode.EXACT -> "Exactly"
            ExclusionMatchMode.CONTAINS -> "Contains"
            ExclusionMatchMode.WILDCARD -> "Pattern"
        }

    fun deleteBody(row: ExclusionRuleRow): String =
        "Delete the rule \"${title(row)}\"? Items it hides will show again where a page uses them. You can undo " +
            "right after."
}
