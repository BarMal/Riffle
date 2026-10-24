package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolDockSettings

enum class WalkthroughStep {
    WELCOME,

    /** What do you want on your home screens? Multi-select from the catalog. */
    GOALS,

    /** Which goals are pages and which are widgets, their order, and the start page. */
    ARRANGE,

    /** Per-goal quick options (the wizard's Content and Look steps, compact), one goal at a time. */
    OPTIONS,

    /** Dock pins from your apps, and hiding the dock where a gesture still opens the menu. */
    DOCK,

    /** Only the permissions the chosen goals need, each explained before its system dialog. Skippable. */
    PERMISSIONS,
    REVIEW,

    /** Finished: apply [WalkthroughApply.apply], persist, offer Undo. */
    DONE,
}

/** What a goal becomes on the new home. */
enum class GoalRole {
    /** A page of its own (a page-set for a look that draws one page per group). */
    PAGE,

    /** A widget on the widgets page. */
    WIDGET,
}

data class ArrangedGoal(
    val goal: ContentGoalId,
    val role: GoalRole,
)

enum class PermissionStatus {
    PENDING,

    /** The rationale is on screen; the system dialog opens only from here. */
    EXPLAINING,

    /** The system flow is open ([WalkthroughState.pendingRequest]); waiting for its result. */
    REQUESTED,
    GRANTED,

    /** The user said no in the system flow. The content stays and shows "Needs access". */
    DENIED,

    /** Skipped here. The content stays and shows "Needs access". */
    SKIPPED,
}

/** Everything the walkthrough reads that is not its own state. */
data class WalkthroughContext(
    val origin: OnboardingOrigin,
    /** `SourceChoices.build(registry.descriptors(), access)`; rebuild it after a permission result. */
    val sources: List<SourceChoice>,
    /** The layout being set up (the shown device class's). */
    val layout: LayoutWorkspaces,
    val pool: PlacedItemPool = PlacedItemPool(),
    /** Apps to pre-pick for Favourite apps (for example the most used, or the current dock). */
    val suggestedApps: List<AppPick> = emptyList(),
    /** Apps to pre-pick for the dock on a fresh install. */
    val suggestedDock: List<AppPick> = emptyList(),
    val feeds: List<FeedOption> = emptyList(),
    /** What the global home gestures do (`DockMenuReachability.globalActions`), for the hide-dock rule. */
    val globalGestureActions: Collection<LauncherGestureAction> = emptyList(),
    val capabilities: LayoutCapabilities = LayoutCapabilities(),
    /** False for a re-run from Settings (or after an earlier completion): nothing is replaced, only added. */
    val firstRun: Boolean = true,
) {
    /** Applying replaces the seeded default and the dock pins: only the first run of a fresh install. */
    val replacesDefault: Boolean get() = origin == OnboardingOrigin.FRESH_INSTALL && firstRun

    /** The known source descriptors with `home.grid` (the pool) always known; null when the host passes none. */
    val descriptors: List<SourceDescriptor>?
        get() =
            sources.takeIf { it.isNotEmpty() }?.map { it.descriptor }?.let { known ->
                if (known.any {
                        it.id == WorkspaceSourceIds.HOME_GRID
                    }
                ) {
                    known
                } else {
                    known + SourceDescriptor(WorkspaceSourceIds.HOME_GRID)
                }
            }

    /** How many apps the dock holds. */
    val dockCapacity: Int get() = pool.dock?.settings?.capacity ?: PoolDockSettings.DEFAULT_CAPACITY
}

/**
 * The walkthrough's choices. [goals] is the arrangement, in page order (placed goals always first: the home draws its
 * placed pages before its lens pages). [setups] holds a setup for every goal ever chosen, so unchecking and re-checking
 * a goal keeps its options. [arranged] turns true once the user changes the arrangement by hand; until then it follows
 * the default proposal. [pendingRequest] asks the host to open that permission's system flow now.
 */
data class WalkthroughState(
    val step: WalkthroughStep = WalkthroughStep.WELCOME,
    val goals: List<ArrangedGoal> = emptyList(),
    val setups: Map<ContentGoalId, GoalSetup> = emptyMap(),
    val startGoal: ContentGoalId? = null,
    val arranged: Boolean = false,
    val optionsIndex: Int = 0,
    val dockPins: List<AppPick> = emptyList(),
    val dockHidden: Boolean = false,
    val permissions: Map<SourcePermission, PermissionStatus> = emptyMap(),
    val pendingRequest: SourcePermission? = null,
    val exit: WalkthroughExit? = null,
) {
    val chosen: List<ContentGoalId> get() = goals.map { it.goal }

    fun roleOf(goal: ContentGoalId): GoalRole? = goals.firstOrNull { it.goal == goal }?.role
}

sealed interface WalkthroughExit {
    /** Closed without applying. Call `Onboarding.skipped`. */
    data object Cancelled : WalkthroughExit

    /** Apply [WalkthroughApply.apply], persist, call `Onboarding.completed`, show Undo. */
    data object Finished : WalkthroughExit
}

sealed interface WalkthroughAction {
    data class ToggleGoal(val goal: ContentGoalId) : WalkthroughAction

    data class SetRole(
        val goal: ContentGoalId,
        val role: GoalRole,
    ) : WalkthroughAction

    /** Moves [goal] to [toIndex] within its block (placed pages stay first). The button form of a drag. */
    data class MoveGoal(
        val goal: ContentGoalId,
        val toIndex: Int,
    ) : WalkthroughAction

    /** Null opens on the first page. */
    data class SetStartGoal(val goal: ContentGoalId?) : WalkthroughAction

    data class SetFilter(
        val goal: ContentGoalId,
        val id: String,
    ) : WalkthroughAction

    data class SetLook(
        val goal: ContentGoalId,
        val id: String,
    ) : WalkthroughAction

    data class ToggleApp(
        val goal: ContentGoalId,
        val app: AppPick,
    ) : WalkthroughAction

    data class ToggleFeed(
        val goal: ContentGoalId,
        val key: String,
    ) : WalkthroughAction

    data class SetQuery(
        val goal: ContentGoalId,
        val text: String,
    ) : WalkthroughAction

    data class ToggleDockPin(val app: AppPick) : WalkthroughAction

    /** Ignored unless a gesture still opens the workspace menu ([WalkthroughFlow.canHideDock]). */
    data class SetDockHidden(val hidden: Boolean) : WalkthroughAction

    /** "Allow" on a permission row: shows its rationale. */
    data class ExplainPermission(val permission: SourcePermission) : WalkthroughAction

    /** "Continue" under the rationale: asks the host to open the system flow ([WalkthroughState.pendingRequest]). */
    data class RequestPermission(val permission: SourcePermission) : WalkthroughAction

    /** The host's report once the system flow closed. */
    data class PermissionResult(
        val permission: SourcePermission,
        val granted: Boolean,
    ) : WalkthroughAction

    data class SkipPermission(val permission: SourcePermission) : WalkthroughAction

    data object Next : WalkthroughAction

    data object Back : WalkthroughAction

    data object Cancel : WalkthroughAction
}

/** Why the walkthrough cannot advance or apply. */
sealed interface WalkthroughProblem {
    data object NoGoals : WalkthroughProblem

    data class GoalUnavailable(val goal: ContentGoalId) : WalkthroughProblem

    data class Setup(
        val goal: ContentGoalId,
        val problem: SetupProblem,
    ) : WalkthroughProblem

    /** Waiting for a permission flow the host opened. */
    data object PermissionPending : WalkthroughProblem

    /** The built workspace did not validate (should not happen; reported rather than applied). */
    data object InvalidWorkspace : WalkthroughProblem
}

/** One page of the review: its goals (one, or the widgets on the widgets page) and whether the home opens on it. */
data class ReviewPage(
    val goals: List<ContentGoalId>,
    val role: GoalRole,
    val placed: Boolean,
    val isStart: Boolean,
)

data class WalkthroughReview(
    val workspaceName: String,
    val pages: List<ReviewPage>,
    val dockPins: List<AppPick>,
    val dockHidden: Boolean,
    /** Goals that will show "Needs access" until their permission is granted. */
    val needsAccess: List<ContentGoalId>,
    /** Fresh install, first run: the seeded default is replaced. Otherwise a workspace is added (or updated). */
    val replacesDefault: Boolean,
    /** A previous walkthrough's workspace is updated in place rather than a new one added. */
    val updatesExisting: Boolean,
)
