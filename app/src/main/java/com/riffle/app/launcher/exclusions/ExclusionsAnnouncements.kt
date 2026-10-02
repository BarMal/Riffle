package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsMessage
import com.riffle.core.domain.launcher.workspace.settings.TextRuleProblem
import com.riffle.core.domain.launcher.workspace.settings.TextRuleValidator

/** Wording for what the Hidden items page announces (snackbar and the add dialog's live problem line). */
internal object ExclusionsAnnouncements {
    fun problem(problem: TextRuleProblem): String =
        when (problem) {
            TextRuleProblem.TOO_SHORT ->
                "Use at least ${TextRuleValidator.MIN_LENGTH} characters (wildcards do not count)."
            TextRuleProblem.TOO_LONG -> "Use at most ${TextRuleValidator.MAX_LENGTH} characters."
            TextRuleProblem.UNSUPPORTED_SOURCE -> "Text rules cannot target that source."
            TextRuleProblem.ALREADY_EXISTS -> "That rule already exists on this layout."
            TextRuleProblem.LIMIT_REACHED -> "You have reached the limit of rules. Delete one first."
        }

    /** The announcement for an outcome. Only descriptions the page already shows ever appear in it. */
    fun message(
        message: ExclusionsMessage,
        layoutName: (HomeLayoutDeviceClass) -> String,
    ): String =
        when (message) {
            is ExclusionsMessage.Enabled -> "Turned on: ${message.description}"
            is ExclusionsMessage.Disabled -> "Turned off: ${message.description}"
            is ExclusionsMessage.Deleted -> "Deleted: ${message.description}"
            is ExclusionsMessage.Added -> "Added: ${message.description}"
            is ExclusionsMessage.AppliedToAll ->
                if (message.added == 1) "Added to 1 other layout" else "Added to ${message.added} other layouts"
            is ExclusionsMessage.Hidden -> "Hidden on ${layoutName(message.layout)}: ${message.what}"
            is ExclusionsMessage.AlreadyHidden -> "Already hidden: ${message.what}"
            is ExclusionsMessage.Problem -> problem(message.problem)
            ExclusionsMessage.UnknownRule -> "That rule no longer exists."
            ExclusionsMessage.CannotHide -> "That can't be hidden this way."
            ExclusionsMessage.NothingToDo -> "Nothing changed."
        }

    const val UNDONE = "Undone"
    const val CANNOT_UNDO = "Rules changed since, so there is nothing to undo."
}
