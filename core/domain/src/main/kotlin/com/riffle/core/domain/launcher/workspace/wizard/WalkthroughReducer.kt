package com.riffle.core.domain.launcher.workspace.wizard

/**
 * Write side of the walkthrough. Pure: `(state, action, context) -> state`. Like the wizard, a choice is only
 * accepted on its own step and only when it is valid right now; anything else returns the state unchanged.
 */
object WalkthroughReducer {
    fun reduce(
        state: WalkthroughState,
        action: WalkthroughAction,
        context: WalkthroughContext,
    ): WalkthroughState =
        when {
            state.exit != null -> state
            action == WalkthroughAction.Next -> WalkthroughNavigation.next(state, context)
            action == WalkthroughAction.Back -> WalkthroughNavigation.back(state, context)
            action == WalkthroughAction.Cancel -> state.copy(exit = WalkthroughExit.Cancelled)
            else -> WalkthroughChoices.reduce(state, action, context)
        }
}

/** The choices of each step, accepted only on that step. */
internal object WalkthroughChoices {
    fun reduce(
        state: WalkthroughState,
        action: WalkthroughAction,
        context: WalkthroughContext,
    ): WalkthroughState =
        when (state.step) {
            WalkthroughStep.GOALS ->
                if (action is WalkthroughAction.ToggleGoal) toggleGoal(state, action.goal, context) else state
            WalkthroughStep.ARRANGE -> WalkthroughArrange.reduce(state, action, context)
            WalkthroughStep.OPTIONS -> options(state, action, context)
            WalkthroughStep.DOCK -> dock(state, action, context)
            WalkthroughStep.PERMISSIONS -> WalkthroughPermissions.reduce(state, action, context)
            WalkthroughStep.WELCOME, WalkthroughStep.REVIEW, WalkthroughStep.DONE -> state
        }

    private fun toggleGoal(
        state: WalkthroughState,
        goal: ContentGoalId,
        context: WalkthroughContext,
    ): WalkthroughState {
        val enabled = WalkthroughFlow.goalChoices(context).firstOrNull { it.goal.id == goal }?.enabled == true
        return when {
            goal in state.chosen -> {
                val goals = state.goals.filterNot { it.goal == goal }
                val kept =
                    if (state.arranged) {
                        goals
                    } else {
                        WalkthroughFlow.proposal(
                            goals.map { it.goal },
                            state.setups,
                            context,
                        )
                    }
                state.copy(goals = kept, startGoal = state.startGoal?.takeIf { it != goal })
            }
            !enabled -> state
            else -> {
                val setups = state.setups + (goal to (state.setups[goal] ?: WalkthroughFlow.setupFor(goal, context)))
                val goals =
                    if (state.arranged) {
                        WalkthroughArrange.insert(
                            state.goals,
                            ArrangedGoal(goal, WalkthroughFlow.defaultRole(setups.getValue(goal), context)),
                        )
                    } else {
                        WalkthroughFlow.proposal(state.chosen + goal, setups, context)
                    }
                state.copy(goals = goals, setups = setups)
            }
        }
    }

    private fun options(
        state: WalkthroughState,
        action: WalkthroughAction,
        context: WalkthroughContext,
    ): WalkthroughState =
        when (action) {
            is WalkthroughAction.SetFilter ->
                edit(
                    state,
                    action.goal,
                    context,
                ) { ContentRecipes.withFilter(it, action.id) }
            is WalkthroughAction.SetLook -> edit(state, action.goal, context) { setLook(state, action, context, it) }
            is WalkthroughAction.ToggleApp ->
                edit(
                    state,
                    action.goal,
                    context,
                ) { ContentRecipes.toggleApp(it, action.app) }
            is WalkthroughAction.ToggleFeed ->
                edit(state, action.goal, context) { ContentRecipes.toggleFeed(it, action.key, context.feeds) }
            is WalkthroughAction.SetQuery ->
                edit(
                    state,
                    action.goal,
                    context,
                ) { ContentRecipes.withQuery(it, action.text) }
            else -> state
        }

    private fun setLook(
        state: WalkthroughState,
        action: WalkthroughAction.SetLook,
        context: WalkthroughContext,
        setup: GoalSetup,
    ): GoalSetup {
        val offered = WalkthroughFlow.looksFor(state, action.goal, context).any { it.id == action.id }
        return if (offered) setup.copy(lookId = action.id) else setup
    }

    /** An option edit of a chosen goal; the look then follows the goal's role. */
    private fun edit(
        state: WalkthroughState,
        goal: ContentGoalId,
        context: WalkthroughContext,
        change: (GoalSetup) -> GoalSetup,
    ): WalkthroughState {
        val setup = state.setups[goal] ?: return state
        val role = state.roleOf(goal)
        val edited = change(setup)
        return if (role == null || edited == setup) {
            state
        } else {
            state.copy(setups = state.setups + (goal to WalkthroughArrange.fitRole(edited, role, context)))
        }
    }

    private fun dock(
        state: WalkthroughState,
        action: WalkthroughAction,
        context: WalkthroughContext,
    ): WalkthroughState =
        when (action) {
            is WalkthroughAction.ToggleDockPin -> toggleDock(state, action.app, context)
            is WalkthroughAction.SetDockHidden ->
                if (!action.hidden || WalkthroughFlow.canHideDock(context)) {
                    state.copy(
                        dockHidden = action.hidden,
                    )
                } else {
                    state
                }
            else -> state
        }

    private fun toggleDock(
        state: WalkthroughState,
        app: AppPick,
        context: WalkthroughContext,
    ): WalkthroughState =
        when {
            state.dockPins.any { it.identity == app.identity } ->
                state.copy(dockPins = state.dockPins.filterNot { it.identity == app.identity })
            state.dockPins.size >= context.dockCapacity -> state
            else -> state.copy(dockPins = state.dockPins + app)
        }
}

/** The Arrange step: roles, order (placed pages stay first) and the start page. */
internal object WalkthroughArrange {
    fun reduce(
        state: WalkthroughState,
        action: WalkthroughAction,
        context: WalkthroughContext,
    ): WalkthroughState =
        when (action) {
            is WalkthroughAction.SetRole -> setRole(state, action, context)
            is WalkthroughAction.MoveGoal -> move(state, action)
            is WalkthroughAction.SetStartGoal ->
                if (action.goal == null || action.goal in state.chosen) state.copy(startGoal = action.goal) else state
            else -> state
        }

    /** Placed goals stay first; anything else goes at the end. */
    fun insert(
        goals: List<ArrangedGoal>,
        added: ArrangedGoal,
    ): List<ArrangedGoal> {
        val index = if (isPlaced(added.goal)) goals.count { isPlaced(it.goal) } else goals.size
        return goals.toMutableList().apply { add(index, added) }
    }

    /** [setup] with a look that suits [role]: its own when it does, else the first that does. */
    fun fitRole(
        setup: GoalSetup,
        role: GoalRole,
        context: WalkthroughContext,
    ): GoalSetup {
        val fits = { candidate: GoalSetup -> WalkthroughFlow.destination(candidate, role, context) != null }
        val look = ContentRecipes.goal(setup).looks.firstOrNull { fits(setup.copy(lookId = it.id)) }
        return if (fits(setup) || look == null) setup else setup.copy(lookId = look.id)
    }

    private fun setRole(
        state: WalkthroughState,
        action: WalkthroughAction.SetRole,
        context: WalkthroughContext,
    ): WalkthroughState {
        val setup = state.setups[action.goal]?.takeIf { action.goal in state.chosen }
        return if (setup == null || action.role !in WalkthroughFlow.rolesFor(setup, context)) {
            state
        } else {
            state.copy(
                goals = state.goals.map { if (it.goal == action.goal) it.copy(role = action.role) else it },
                setups = state.setups + (action.goal to fitRole(setup, action.role, context)),
                arranged = true,
            )
        }
    }

    private fun move(
        state: WalkthroughState,
        action: WalkthroughAction.MoveGoal,
    ): WalkthroughState {
        val moving = state.goals.firstOrNull { it.goal == action.goal } ?: return state
        val without = state.goals.filterNot { it.goal == action.goal }
        val placedCount = without.count { isPlaced(it.goal) }
        val range = if (isPlaced(moving.goal)) 0..placedCount else placedCount..without.size
        val index = action.toIndex.coerceIn(range)
        return state.copy(goals = without.toMutableList().apply { add(index, moving) }, arranged = true)
    }

    private fun isPlaced(goal: ContentGoalId): Boolean = ContentGoalCatalog.goal(goal).placedItems
}

/** Next and Back, including the Options step's walk through its goals. */
internal object WalkthroughNavigation {
    fun next(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): WalkthroughState {
        val lastOption = WalkthroughFlow.optionGoals(state, context).lastIndex
        val left =
            if (state.step == WalkthroughStep.PERMISSIONS) {
                WalkthroughPermissions.skipOpen(
                    state,
                    context,
                )
            } else {
                state
            }
        val steps = WalkthroughFlow.steps(left, context)
        // By order, not by index: the current step may have just left the sequence (Permissions, once every
        // permission it asked for was granted).
        val following = steps.firstOrNull { it.ordinal > left.step.ordinal } ?: WalkthroughStep.DONE
        return when {
            !WalkthroughFlow.canAdvance(state, context) -> state
            state.step == WalkthroughStep.OPTIONS && state.optionsIndex < lastOption ->
                state.copy(optionsIndex = state.optionsIndex + 1)
            following == WalkthroughStep.DONE -> left.copy(step = WalkthroughStep.DONE, exit = WalkthroughExit.Finished)
            following == WalkthroughStep.OPTIONS -> left.copy(step = following, optionsIndex = 0)
            else -> left.copy(step = following)
        }
    }

    fun back(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): WalkthroughState {
        val steps = WalkthroughFlow.steps(state, context)
        val previous = steps.lastOrNull { it.ordinal < state.step.ordinal }
        val lastOption = (WalkthroughFlow.optionGoals(state, context).size - 1).coerceAtLeast(0)
        return when {
            state.step == WalkthroughStep.OPTIONS && state.optionsIndex > 0 ->
                state.copy(
                    optionsIndex = state.optionsIndex - 1,
                )
            state.pendingRequest != null -> state
            previous == null -> state.copy(exit = WalkthroughExit.Cancelled)
            previous == WalkthroughStep.OPTIONS -> state.copy(step = previous, optionsIndex = lastOption)
            else -> state.copy(step = previous)
        }
    }
}

/** The Permissions step: an explanation before every system dialog, and Skip everywhere. */
internal object WalkthroughPermissions {
    fun reduce(
        state: WalkthroughState,
        action: WalkthroughAction,
        context: WalkthroughContext,
    ): WalkthroughState =
        when (action) {
            is WalkthroughAction.ExplainPermission -> explain(state, action.permission, context)
            is WalkthroughAction.RequestPermission -> request(state, action.permission, context)
            is WalkthroughAction.PermissionResult -> result(state, action)
            is WalkthroughAction.SkipPermission -> skip(state, action.permission, context)
            else -> state
        }

    /** Leaving the step: every permission still pending (or being explained) counts as skipped. */
    fun skipOpen(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): WalkthroughState {
        val open = WalkthroughFlow.neededPermissions(state, context).filter { status(state, it, context) in OPEN }
        return state.copy(permissions = state.permissions + open.associateWith { PermissionStatus.SKIPPED })
    }

    private fun explain(
        state: WalkthroughState,
        permission: SourcePermission,
        context: WalkthroughContext,
    ): WalkthroughState =
        if (state.pendingRequest == null && needed(state, permission, context) &&
            status(state, permission, context) in ASKABLE
        ) {
            state.with(permission, PermissionStatus.EXPLAINING)
        } else {
            state
        }

    /** The system flow opens only from the rationale ([PermissionStatus.EXPLAINING]). */
    private fun request(
        state: WalkthroughState,
        permission: SourcePermission,
        context: WalkthroughContext,
    ): WalkthroughState =
        if (state.pendingRequest == null && status(state, permission, context) == PermissionStatus.EXPLAINING) {
            state.with(permission, PermissionStatus.REQUESTED).copy(pendingRequest = permission)
        } else {
            state
        }

    private fun result(
        state: WalkthroughState,
        action: WalkthroughAction.PermissionResult,
    ): WalkthroughState =
        if (state.pendingRequest == action.permission) {
            val status = if (action.granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
            state.with(action.permission, status).copy(pendingRequest = null)
        } else {
            state
        }

    private fun skip(
        state: WalkthroughState,
        permission: SourcePermission,
        context: WalkthroughContext,
    ): WalkthroughState {
        val skippable =
            state.pendingRequest != permission && status(state, permission, context) != PermissionStatus.GRANTED
        return if (needed(state, permission, context) && skippable) {
            state.with(
                permission,
                PermissionStatus.SKIPPED,
            )
        } else {
            state
        }
    }

    private fun needed(
        state: WalkthroughState,
        permission: SourcePermission,
        context: WalkthroughContext,
    ): Boolean = permission in WalkthroughFlow.neededPermissions(state, context)

    private fun status(
        state: WalkthroughState,
        permission: SourcePermission,
        context: WalkthroughContext,
    ): PermissionStatus = WalkthroughFlow.permissionStatus(state, permission, context)

    private fun WalkthroughState.with(
        permission: SourcePermission,
        status: PermissionStatus,
    ): WalkthroughState = copy(permissions = permissions + (permission to status))

    private val ASKABLE = setOf(PermissionStatus.PENDING, PermissionStatus.SKIPPED, PermissionStatus.DENIED)
    private val OPEN = setOf(PermissionStatus.PENDING, PermissionStatus.EXPLAINING)
}
