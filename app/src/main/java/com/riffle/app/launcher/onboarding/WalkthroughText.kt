package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalCatalog
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.GoalAvailability
import com.riffle.core.domain.launcher.workspace.wizard.GoalChoice
import com.riffle.core.domain.launcher.workspace.wizard.GoalRole
import com.riffle.core.domain.launcher.workspace.wizard.PermissionStatus
import com.riffle.core.domain.launcher.workspace.wizard.ReviewPage
import com.riffle.core.domain.launcher.workspace.wizard.SetupProblem
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughProblem
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughReview
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep

/**
 * The walkthrough's and the offer's copy (`workspaces-wizard-and-onboarding.md` section 5): Material 3 sentence case,
 * short, second person, one primary action per screen. Item content never appears here; only goal, app and feed names.
 */
@Suppress("TooManyFunctions") // One small wording rule per row kind; kept together so the copy reads as one table.
internal object WalkthroughText {
    const val SKIP = "Skip set-up"
    const val BACK = "Back"
    const val NEXT = "Next"
    const val GET_STARTED = "Get started"
    const val SKIP_FOR_NOW = "Skip for now"
    const val BUILD = "Build my home"
    const val READY = "Your home is ready"
    const val UNDO = "Undo"
    const val UNDONE = "Your previous home is back"
    const val UNDO_FAILED = "Couldn't undo. Your home can't be changed right now."
    const val LOADING = "Your home is still loading. This takes a moment."

    const val WELCOME_TITLE = "Set up your home"
    const val WELCOME_BODY = "Tell Riffle what you want on your home screens and it builds them for you."
    const val WELCOME_NOTE = "You can change everything later in Settings > Workspaces."

    const val GOALS_TITLE = "What do you want on your home screens?"
    const val GOALS_BODY = "Pick as many as you like."
    const val NEEDS_ACCESS = "Needs access"
    const val UNAVAILABLE = "Not available on this phone"
    const val ALREADY_THERE = "Already on this home"

    const val ARRANGE_TITLE = "Arrange your home"
    const val ARRANGE_BODY = "Choose what gets its own page and what becomes a widget."
    const val ARRANGE_NOTE = "Your app icons always come first."
    const val ROLE_PAGE = "Page"
    const val ROLE_WIDGET = "Widget"
    const val MOVE_UP = "Move up"
    const val MOVE_DOWN = "Move down"
    const val OPEN_HERE = "Open on this page"
    const val OPEN_FIRST = "Open on the first page"

    const val OPTIONS_SHOW = "What should it show?"
    const val OPTIONS_LOOK = "How should it look?"
    const val PICK_APPS = "Pick apps for your home screen"
    const val ONLY_THESE_APPS = "Only these apps"
    const val ALL_APPS_WHEN_NONE = "None picked: all apps"
    const val FEEDS = "Feeds"
    const val ALL_FEEDS_WHEN_NONE = "None picked: all feeds"
    const val NO_FEEDS = "You haven't added any. Add them in Settings, then pick them here."
    const val SEARCH_FOR = "Search for"
    const val SEARCH_HELPER = "Leave empty to use what you type in the search box"

    const val DOCK_TITLE = "Your dock"
    const val HIDE_DOCK = "Hide the dock"
    const val HIDE_DOCK_REFUSED =
        "To hide the dock, first set a gesture to open the workspace menu in Settings > Gestures."
    const val DOCK_FULL = "The dock is full. Remove an app to add another."

    const val PERMISSIONS_TITLE = "Allow access"
    const val PERMISSIONS_BODY =
        "Some of what you picked needs your permission. You can skip this and allow it later in Settings > Sources."
    const val ALLOW = "Allow"
    const val CONTINUE = "Continue"
    const val NOT_NOW = "Not now"
    const val CHECK_AGAIN = "Check again"
    const val WAITING = "Waiting for your answer. Come back here when you're done."

    const val REVIEW_TITLE = "Review your home"
    const val REPLACES = "This replaces the starting home."
    const val KEEPS = "Your other workspaces stay as they are."
    const val UPDATES = "This updates the home you set up before."
    const val OPENS_HERE = "Opens here"
    const val DOCK_HIDDEN = "Dock hidden"

    const val OFFER_TITLE = "Set up your home"
    const val OFFER_BODY = "Tell Riffle what you want on your home screens. Your current home stays."
    const val OFFER_ACCEPT = "Set up"
    const val OFFER_DISMISS = "Not now"

    const val SETTINGS_ROW = "Set up your home again"
    const val SETTINGS_ROW_SUBTITLE = "Build a workspace from what you want to see"

    /** The heading each step announces (a polite live region and a heading for TalkBack). */
    fun stepTitle(step: WalkthroughStep): String =
        when (step) {
            WalkthroughStep.WELCOME -> WELCOME_TITLE
            WalkthroughStep.GOALS -> GOALS_TITLE
            WalkthroughStep.ARRANGE -> ARRANGE_TITLE
            WalkthroughStep.OPTIONS -> "Options"
            WalkthroughStep.DOCK -> DOCK_TITLE
            WalkthroughStep.PERMISSIONS -> PERMISSIONS_TITLE
            WalkthroughStep.REVIEW -> REVIEW_TITLE
            WalkthroughStep.DONE -> READY
        }

    /** The short name in the wide layout's step list. */
    fun stepName(step: WalkthroughStep): String =
        when (step) {
            WalkthroughStep.WELCOME -> "Welcome"
            WalkthroughStep.GOALS -> "What to show"
            WalkthroughStep.ARRANGE -> "Arrange"
            WalkthroughStep.OPTIONS -> "Options"
            WalkthroughStep.DOCK -> "Dock"
            WalkthroughStep.PERMISSIONS -> "Access"
            WalkthroughStep.REVIEW -> "Review"
            WalkthroughStep.DONE -> "Done"
        }

    fun progress(
        position: Int,
        count: Int,
    ): String = "Step $position of $count"

    fun optionsProgress(
        position: Int,
        count: Int,
    ): String = "$position of $count"

    /** The primary button of [step]. */
    fun primary(step: WalkthroughStep): String =
        when (step) {
            WalkthroughStep.WELCOME -> GET_STARTED
            WalkthroughStep.REVIEW -> BUILD
            else -> NEXT
        }

    fun goalLabel(goal: ContentGoalId): String = ContentGoalCatalog.goal(goal).label

    fun goals(goals: List<ContentGoalId>): String = goals.joinToString(", ") { goalLabel(it) }

    /** A goal row's state line: "Needs access", or why it cannot be chosen; null when it is ready. */
    fun availability(choice: GoalChoice): String? =
        when (choice.availability) {
            GoalAvailability.READY -> null
            GoalAvailability.NEEDS_ACCESS -> NEEDS_ACCESS
            GoalAvailability.UNAVAILABLE -> UNAVAILABLE
            GoalAvailability.ALREADY_THERE -> ALREADY_THERE
        }

    fun dockBody(capacity: Int): String = "Pick up to $capacity apps to keep at the bottom of the screen."

    fun dockCount(
        count: Int,
        capacity: Int,
    ): String = "$count of $capacity picked"

    fun permissionName(permission: SourcePermission): String =
        when (permission) {
            SourcePermission.NOTIFICATION_ACCESS -> "Notifications and media"
            SourcePermission.CALENDAR -> "Calendar"
            SourcePermission.USAGE_ACCESS -> "Recent apps"
        }

    fun permissionRationale(permission: SourcePermission): String =
        when (permission) {
            SourcePermission.NOTIFICATION_ACCESS ->
                "Lets Riffle show your notifications and what's playing. They stay on this phone."
            SourcePermission.CALENDAR -> "Lets Riffle show your upcoming events. It never changes your calendar."
            SourcePermission.USAGE_ACCESS -> "Lets Riffle show the apps you used last."
        }

    /** What happens on "Continue": which system screen opens. */
    fun permissionNext(permission: SourcePermission): String =
        when (permission) {
            SourcePermission.NOTIFICATION_ACCESS ->
                "Android opens Notification access. Turn Riffle on there, then come back."
            SourcePermission.CALENDAR -> "Android asks whether Riffle may read your calendar."
            SourcePermission.USAGE_ACCESS -> "Android opens Usage access. Turn Riffle on there, then come back."
        }

    /** The status line of a permission row; null while nothing happened yet. */
    fun permissionStatus(status: PermissionStatus): String? =
        when (status) {
            PermissionStatus.PENDING, PermissionStatus.EXPLAINING -> null
            PermissionStatus.REQUESTED -> WAITING
            PermissionStatus.GRANTED -> "Allowed"
            PermissionStatus.DENIED -> "Not allowed"
            PermissionStatus.SKIPPED -> "Skipped"
        }

    fun neededBy(goals: List<ContentGoalId>): String = "For ${goals(goals)}"

    fun reviewPage(
        index: Int,
        page: ReviewPage,
    ): String =
        if (page.goals.size > 1 || page.role == GoalRole.WIDGET) {
            "Widgets: ${goals(page.goals)}"
        } else {
            "Page ${index + 1}: ${goals(page.goals)}"
        }

    fun reviewDock(review: WalkthroughReview): String =
        when {
            review.dockHidden -> DOCK_HIDDEN
            review.dockPins.isEmpty() -> "Dock: as it is now"
            else -> "Dock: ${apps(review.dockPins)}"
        }

    fun reviewNeedsAccess(goals: List<ContentGoalId>): String =
        "Needs access: ${goals(goals)}. They show Needs access until you allow it."

    fun reviewScope(review: WalkthroughReview): String =
        when {
            review.replacesDefault -> REPLACES
            review.updatesExisting -> "$UPDATES $KEEPS"
            else -> KEEPS
        }

    fun apps(apps: List<AppPick>): String = apps.joinToString(", ") { it.label }

    /** A problem next to the control that has it (a polite live region). */
    fun problem(problem: WalkthroughProblem): String =
        when (problem) {
            WalkthroughProblem.NoGoals -> "Pick at least one thing to show."
            is WalkthroughProblem.GoalUnavailable -> "${goalLabel(problem.goal)} isn't available on this phone."
            is WalkthroughProblem.Setup ->
                when (problem.problem) {
                    SetupProblem.PICK_AN_APP -> "Pick at least one app."
                    SetupProblem.LOOK_DOES_NOT_FIT -> "This look doesn't fit here."
                }
            WalkthroughProblem.PermissionPending -> WAITING
            WalkthroughProblem.InvalidWorkspace -> "Something went wrong building your home. Try again."
        }

    /** One sentence a row reads as (label, description, state and reason), for TalkBack. */
    fun spoken(vararg parts: String?): String = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(". ")
}
