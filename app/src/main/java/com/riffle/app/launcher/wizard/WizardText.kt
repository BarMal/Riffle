package com.riffle.app.launcher.wizard

import com.riffle.app.launcher.editor.EditorText
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.editor.WidgetTarget
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.Destination
import com.riffle.core.domain.launcher.workspace.wizard.GoalAvailability
import com.riffle.core.domain.launcher.workspace.wizard.SetupProblem
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry
import com.riffle.core.domain.launcher.workspace.wizard.WizardProblem
import com.riffle.core.domain.launcher.workspace.wizard.WizardStep

/**
 * The content wizard's wording (docs/product/workspaces-wizard-and-onboarding.md, "Copy"): Material 3 sentence case,
 * short, second person. Plain strings so JVM tests can check them. Goal, filter and look labels come from the
 * domain's catalog (`ContentGoal.label`, `GoalFilter.label`, `GoalLook.label`).
 */
@Suppress("TooManyFunctions") // One wording function per domain value, like `EditorText`.
internal object WizardText {
    const val TITLE_EDITOR = "Add to your home"
    const val TITLE_NEW_LENS = "New lens"
    const val SOURCE_HEADING = "What do you want to add?"
    const val SOURCE_SUPPORTING = "You can change it later."
    const val UNAVAILABLE = "Not available on this phone"
    const val ALREADY_THERE = "Already on this home"
    const val NEEDS_ACCESS = "Needs access"
    const val NEEDS_ACCESS_NOTE =
        "It shows Needs access until you allow it. Riffle never asks on its own: tap Review access to see what it " +
            "reads and why."
    const val REVIEW_ACCESS = "Review access"
    const val CONTENT_HEADING = "What should it show?"
    const val APPS_PLACED = "Pick apps for your home screen"
    const val APPS_FILTER = "Only these apps"
    const val ALL_APPS = "All apps"
    const val NO_APPS = "No apps to pick yet."
    const val FEEDS = "Feeds"
    const val ALL_FEEDS = "All feeds"
    const val NO_FEEDS = "No feeds yet. Add one in Settings > Sources."
    const val QUERY_LABEL = "Search for"
    const val QUERY_HELPER = "Leave empty to use what you type in the search box"
    const val LOOK_HEADING = "How should it look?"
    const val DOES_NOT_FIT = "Doesn't fit here"
    const val PREVIEW = "Preview"
    const val PREVIEW_PLACED = "Your picked apps, as icons"
    const val PREVIEW_PLACED_EMPTY = "Pick apps to see them here."
    const val PREVIEW_NONE = "Choose what to add to see a preview."
    const val PLACE_HEADING = "Where should it go?"
    const val NEW_PAGE = "New page"
    const val WIDGET_NEW_PAGE = "Widget on a new page"
    const val IN_THE_DOCK = "In the dock"
    const val DOCK_REPLACES = "Replaces what the dock shows now"
    const val LENS_ONLY = "Save as a lens only"
    const val LENS_ONLY_SUPPORTING = "Use it later from Saved lenses"
    const val SAVE_HEADING = "Save it for later?"
    const val SAVE_SWITCH = "Save to your lenses"
    const val SAVE_SUPPORTING = "Reuse it elsewhere. Editing it updates every place it's used."
    const val NAME = "Name"
    const val BACK = "Back"
    const val NEXT = "Next"
    const val ADD = "Add"
    const val SAVE = "Save"
    const val CANCEL = "Cancel"
    const val ADVANCED = "Open advanced builder"
    const val NOT_LOADED = "Your workspaces are still loading. Try again in a moment."
    const val HOLDING_EDITS = "Your home is still loading. Try again in a moment."
    const val POOL_NOT_LOADED = "Your home screen items are still loading. Try again in a moment."
    const val NEW_LENS_ADVANCED = "Open advanced builder"
    const val ADD_TO_HOME = "Add to your home"
    const val ADD_TO_HOME_SUPPORTING = "Choose how it looks and where it goes"

    fun title(entry: WizardEntry): String =
        when (entry) {
            is WizardEntry.Editor -> TITLE_EDITOR
            WizardEntry.NewSavedLens -> TITLE_NEW_LENS
            is WizardEntry.FromSource -> "Use ${EditorText.sourceLabel(entry.source)}"
        }

    /** "Step 2 of 4", counted over the steps this run shows (`WizardFlow.steps`), without the closing one. */
    fun progress(
        position: Int,
        total: Int,
    ): String = "Step $position of $total"

    fun heading(step: WizardStep): String =
        when (step) {
            WizardStep.SOURCE -> SOURCE_HEADING
            WizardStep.CONTENT -> CONTENT_HEADING
            WizardStep.LOOK -> LOOK_HEADING
            WizardStep.PLACE -> PLACE_HEADING
            WizardStep.SAVE -> SAVE_HEADING
            WizardStep.DONE -> ""
        }

    /** The primary button: "Next", or on the last step "Add" ("Save" for a lens only). */
    fun primary(
        last: Boolean,
        lensOnly: Boolean,
    ): String =
        when {
            !last -> NEXT
            lensOnly -> SAVE
            else -> ADD
        }

    /** Why a goal cannot be chosen, or its badge; null for a ready goal. */
    fun availability(availability: GoalAvailability): String? =
        when (availability) {
            GoalAvailability.READY -> null
            GoalAvailability.NEEDS_ACCESS -> NEEDS_ACCESS
            GoalAvailability.UNAVAILABLE -> UNAVAILABLE
            GoalAvailability.ALREADY_THERE -> ALREADY_THERE
        }

    fun permission(permission: SourcePermission): String =
        when (permission) {
            SourcePermission.NOTIFICATION_ACCESS ->
                "Notifications and media: lets Riffle show your notifications and what's playing. " +
                    "They stay on this phone."
            SourcePermission.CALENDAR ->
                "Calendar: lets Riffle show your upcoming events. It never changes your calendar."
            SourcePermission.USAGE_ACCESS -> "Recent apps: lets Riffle show the apps you used last."
        }

    /** A destination's label; [target] and [workspace] name a widget's page, [goal] words a page per group. */
    fun destination(
        destination: Destination,
        goal: ContentGoalId?,
        target: WidgetTarget? = null,
        workspace: Workspace? = null,
    ): String =
        when (destination) {
            Destination.PAGE -> NEW_PAGE
            Destination.PAGE_SET -> pageSet(goal)
            Destination.WIDGET -> widget(target, workspace)
            Destination.DOCK_SECTION -> IN_THE_DOCK
            Destination.LENS_ONLY -> LENS_ONLY
        }

    fun pageSet(goal: ContentGoalId?): String =
        when (goal) {
            ContentGoalId.NEWS_FEEDS -> "A page for each feed"
            ContentGoalId.CALENDAR_FEEDS -> "A page for each calendar"
            ContentGoalId.NOTIFICATIONS -> "A page for each app"
            else -> "A page for each group"
        }

    /** "Widget on page 2" for a free spot on an existing page, "Widget on a new page" otherwise. */
    fun widget(
        target: WidgetTarget?,
        workspace: Workspace?,
    ): String {
        val index = target?.pageId?.let { id -> workspace?.pages?.indexOfFirst { it.id == id } } ?: -1
        return if (index < 0) WIDGET_NEW_PAGE else "Widget on page ${index + 1}"
    }

    fun destinationSupporting(
        destination: Destination,
        replaces: Boolean,
    ): String? =
        when {
            destination == Destination.DOCK_SECTION && replaces -> DOCK_REPLACES
            destination == Destination.LENS_ONLY -> LENS_ONLY_SUPPORTING
            else -> null
        }

    fun problem(problem: WizardProblem): String =
        when (problem) {
            WizardProblem.NoGoal -> "Choose what to add."
            is WizardProblem.GoalUnavailable -> UNAVAILABLE
            is WizardProblem.Setup ->
                when (problem.problem) {
                    SetupProblem.PICK_AN_APP -> "Pick at least one app."
                    SetupProblem.LOOK_DOES_NOT_FIT -> "This look doesn't fit here."
                }
            WizardProblem.NoDestination -> "Choose where it goes."
            is WizardProblem.DestinationRejected -> "It doesn't fit on this workspace."
            is WizardProblem.Name -> name(problem.problem)
            WizardProblem.NoWorkspace -> "This workspace was deleted."
        }

    fun name(problem: LibraryProblem): String =
        when (problem) {
            LibraryProblem.BLANK_NAME -> "Enter a name."
            LibraryProblem.NAME_TAKEN -> "You already have a lens with this name."
            LibraryProblem.NAME_TOO_LONG -> "Use 40 characters or fewer."
            LibraryProblem.LIBRARY_FULL -> "You have 100 lenses. Delete one to save another."
            LibraryProblem.UNKNOWN_LENS -> "This lens no longer exists."
        }

    /** The closing announcement (the editor's Undo covers it there). */
    fun added(name: String): String = "Added $name"

    /** What one choice row reads as a whole: label, description, state and reason in one sentence. */
    fun spoken(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(". ")
}
