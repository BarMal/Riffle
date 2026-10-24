package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.DockMenuReachability
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess

/**
 * Read side of the first-time walkthrough: the goal list and its preselection, the default arrangement, which steps
 * are shown, what each goal's quick options offer (the wizard's [ContentRecipes], compact), the permissions to ask
 * for, the review and what blocks a step. [WalkthroughReducer] is the write side, [WalkthroughApply] applies it.
 */
@Suppress("TooManyFunctions") // One read-side query per step and option, like `WizardFlow`.
object WalkthroughFlow {
    /** The id of the workspace the walkthrough builds, in every layout. Re-applying updates it in place. */
    val WORKSPACE_ID = WorkspaceId("walkthrough:home")

    const val WORKSPACE_NAME = "My home"

    private val FRESH_PRESELECTION =
        listOf(ContentGoalId.FAVOURITE_APPS, ContentGoalId.MY_APPS, ContentGoalId.NOTIFICATIONS, ContentGoalId.CALENDAR)
    private val EXISTING_PRESELECTION =
        listOf(ContentGoalId.HOME_ITEMS, ContentGoalId.MY_APPS, ContentGoalId.NOTIFICATIONS)

    /** Every goal with whether this device can offer it (a goal needing access can be chosen). */
    fun goalChoices(context: WalkthroughContext): List<GoalChoice> =
        ContentGoalCatalog.all.map { WizardFlow.baseChoice(it, context.sources) }

    /**
     * The goals ticked when the walkthrough opens. A fresh install: favourite apps (when there are apps to suggest),
     * all apps, notifications and calendar. An existing user: their current home, all apps and notifications. Only
     * goals this device can offer.
     */
    fun preselection(context: WalkthroughContext): List<ContentGoalId> {
        val enabled = goalChoices(context).filter { it.enabled }.map { it.goal.id }.toSet()
        val wanted = if (context.origin == OnboardingOrigin.FRESH_INSTALL) FRESH_PRESELECTION else EXISTING_PRESELECTION
        val canPick = context.suggestedApps.isNotEmpty()
        return wanted.filter { it in enabled && (it != ContentGoalId.FAVOURITE_APPS || canPick) }
    }

    fun start(context: WalkthroughContext): WalkthroughState {
        val chosen = preselection(context)
        val setups = chosen.associateWith { setupFor(it, context) }
        val dock = if (context.origin == OnboardingOrigin.FRESH_INSTALL) context.suggestedDock else emptyList()
        return WalkthroughState(
            goals = proposal(chosen, setups, context),
            setups = setups,
            dockPins = dock.distinctBy { it.identity }.take(context.dockCapacity),
        )
    }

    /** A new goal's setup: its defaults, Favourite apps pre-picked from the suggestions. */
    fun setupFor(
        goal: ContentGoalId,
        context: WalkthroughContext,
    ): GoalSetup = ContentRecipes.defaultSetup(ContentGoalCatalog.goal(goal), context.suggestedApps)

    /**
     * The default arrangement of [chosen]: placed goals first (one placed page), then the widgets (one widgets page),
     * then every other page, each block in catalog order. A goal's role follows its default look's first placement.
     */
    fun proposal(
        chosen: List<ContentGoalId>,
        setups: Map<ContentGoalId, GoalSetup>,
        context: WalkthroughContext,
    ): List<ArrangedGoal> {
        val inOrder = ContentGoalCatalog.all.map { it.id }.filter { it in chosen }
        val arranged = inOrder.map { id -> ArrangedGoal(id, defaultRole(setups[id] ?: setupFor(id, context), context)) }
        return arranged.sortedBy { blockOf(it) }
    }

    /** 0: placed pages, 1: widgets, 2: other pages. Placed pages are always first (the home draws them first). */
    internal fun blockOf(goal: ArrangedGoal): Int =
        when {
            ContentGoalCatalog.goal(goal.goal).placedItems -> 0
            goal.role == GoalRole.WIDGET -> 1
            else -> 2
        }

    fun defaultRole(
        setup: GoalSetup,
        context: WalkthroughContext,
    ): GoalRole {
        val first = ContentRecipes.look(setup).placements.first()
        val widget = first == Destination.WIDGET || first == Destination.DOCK_SECTION
        val preferred = if (widget) GoalRole.WIDGET else GoalRole.PAGE
        val roles = rolesFor(setup, context)
        return preferred.takeIf { it in roles } ?: roles.firstOrNull() ?: GoalRole.PAGE
    }

    /** The roles some look of [setup]'s goal can take (placed goals: a page only). */
    fun rolesFor(
        setup: GoalSetup,
        context: WalkthroughContext,
    ): List<GoalRole> =
        GoalRole.entries.filter { role ->
            ContentRecipes.goal(setup).looks.any { destination(setup.copy(lookId = it.id), role, context) != null }
        }

    /** Where [setup]'s look goes for [role]: a page (or page-set) or a widget; null when it cannot. */
    fun destination(
        setup: GoalSetup,
        role: GoalRole,
        context: WalkthroughContext,
    ): Destination? {
        val look = ContentRecipes.look(setup)
        val wanted =
            if (role == GoalRole.PAGE) {
                listOf(
                    Destination.PAGE,
                    Destination.PAGE_SET,
                )
            } else {
                listOf(Destination.WIDGET)
            }
        return look.placements.firstOrNull { it in wanted && ContentRecipes.fits(setup, look, it, context.descriptors) }
    }

    /** The looks [goal] can take in its current role (the compact Look options). */
    fun looksFor(
        state: WalkthroughState,
        goal: ContentGoalId,
        context: WalkthroughContext,
    ): List<GoalLook> {
        val setup = state.setups[goal]
        val role = state.roleOf(goal)
        if (setup == null || role == null) return emptyList()
        return ContentRecipes.goal(
            setup,
        ).looks.filter { destination(setup.copy(lookId = it.id), role, context) != null }
    }

    /** The chosen goals that have something to choose on the Options step, in arrangement order. */
    fun optionGoals(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): List<ContentGoalId> =
        state.goals.map { it.goal }.filter { id ->
            val goal = ContentGoalCatalog.goal(id)
            goal.filters.size > 1 || goal.pickers.isNotEmpty() || looksFor(state, id, context).size > 1
        }

    /** The goal the Options step shows now. */
    fun currentOptionGoal(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): ContentGoalId? = optionGoals(state, context).getOrNull(state.optionsIndex)

    /** The permissions the chosen goals need and this device has not granted, in a stable order. */
    fun neededPermissions(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): List<SourcePermission> {
        val access = accessMap(context)
        return state.goals.flatMap { ContentGoalCatalog.goal(it.goal).permissions }.distinct()
            .filter { SourcePermission.accessOf(it, access) == SourceAccess.REQUIRED }
            .sortedBy { it.ordinal }
    }

    fun permissionStatus(
        state: WalkthroughState,
        permission: SourcePermission,
        context: WalkthroughContext,
    ): PermissionStatus =
        if (SourcePermission.accessOf(permission, accessMap(context)) == SourceAccess.GRANTED) {
            PermissionStatus.GRANTED
        } else {
            state.permissions[permission] ?: PermissionStatus.PENDING
        }

    /** The chosen goals that show "Needs access" (their permission is still not granted). */
    fun needsAccess(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): List<ContentGoalId> {
        val access = accessMap(context)
        return state.goals.map { it.goal }.filter { id ->
            ContentGoalCatalog.goal(
                id,
            ).permissions.any { SourcePermission.accessOf(it, access) == SourceAccess.REQUIRED }
        }
    }

    /** Hiding the dock is offered only while a gesture still opens the workspace menu (the S7 stranding rule). */
    fun canHideDock(context: WalkthroughContext): Boolean =
        DockMenuReachability.canHideDock(Workspace(WORKSPACE_ID, WORKSPACE_NAME), context.globalGestureActions)

    fun steps(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): List<WalkthroughStep> =
        WalkthroughStep.entries.filter { step ->
            when (step) {
                WalkthroughStep.OPTIONS -> optionGoals(state, context).isNotEmpty()
                WalkthroughStep.PERMISSIONS -> neededPermissions(state, context).isNotEmpty()
                else -> true
            }
        }

    fun problems(
        state: WalkthroughState,
        context: WalkthroughContext,
        step: WalkthroughStep = state.step,
    ): List<WalkthroughProblem> =
        when (step) {
            WalkthroughStep.GOALS, WalkthroughStep.ARRANGE -> goalProblems(state, context)
            WalkthroughStep.OPTIONS ->
                currentOptionGoal(state, context)?.let { setupProblems(state, it, context) }.orEmpty()
            WalkthroughStep.PERMISSIONS ->
                if (state.pendingRequest != null) listOf(WalkthroughProblem.PermissionPending) else emptyList()
            WalkthroughStep.REVIEW ->
                goalProblems(state, context) + state.goals.flatMap { setupProblems(state, it.goal, context) }
            WalkthroughStep.WELCOME, WalkthroughStep.DOCK, WalkthroughStep.DONE -> emptyList()
        }

    fun canAdvance(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): Boolean = state.exit == null && state.step != WalkthroughStep.DONE && problems(state, context).isEmpty()

    /** The review: the pages as they will be built, the dock, and what will show "Needs access". */
    fun review(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): WalkthroughReview {
        val built = WalkthroughBuilder.build(state, context)
        val pages =
            built.workspace.pages.map { page ->
                val goals = built.goalsByPage[page.id].orEmpty()
                ReviewPage(
                    goals = goals,
                    role = if (goals.any { state.roleOf(it) == GoalRole.WIDGET }) GoalRole.WIDGET else GoalRole.PAGE,
                    placed = goals.any { ContentGoalCatalog.goal(it).placedItems },
                    isStart = page.id == (built.workspace.startPageId ?: built.workspace.pages.firstOrNull()?.id),
                )
            }
        return WalkthroughReview(
            workspaceName = WORKSPACE_NAME,
            pages = pages,
            dockPins = state.dockPins,
            dockHidden = state.dockHidden && canHideDock(context),
            needsAccess = needsAccess(state, context),
            replacesDefault = context.replacesDefault,
            updatesExisting = context.layout.find(WORKSPACE_ID) != null,
        )
    }

    private fun goalProblems(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): List<WalkthroughProblem> {
        if (state.goals.isEmpty()) return listOf(WalkthroughProblem.NoGoals)
        val choices = goalChoices(context).associateBy { it.goal.id }
        return state.goals.filter { choices[it.goal]?.enabled != true }.map {
            WalkthroughProblem.GoalUnavailable(
                it.goal,
            )
        }
    }

    private fun setupProblems(
        state: WalkthroughState,
        goal: ContentGoalId,
        context: WalkthroughContext,
    ): List<WalkthroughProblem> {
        val setup = state.setups[goal]
        val role = state.roleOf(goal)
        if (setup == null || role == null) return emptyList()
        val fits = destination(setup, role, context) != null
        return ContentRecipes.problems(setup).map { WalkthroughProblem.Setup(goal, it) } +
            if (fits) emptyList() else listOf(WalkthroughProblem.Setup(goal, SetupProblem.LOOK_DOES_NOT_FIT))
    }

    private fun accessMap(context: WalkthroughContext): Map<SourceId, SourceAccess> =
        context.sources.associate {
            it.id to it.access
        }
}
