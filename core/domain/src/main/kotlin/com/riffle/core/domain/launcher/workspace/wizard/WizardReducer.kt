package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.editor.WidgetTarget

/**
 * Write side of the wizard. Pure: `(state, action, context) -> state`, like the editor's `BindingFlowReducer`. An
 * action that does not apply right now (another step's choice, a disabled option, a closed wizard) returns the state
 * unchanged, so a stale tap can never reach an invalid selection. Choices are only accepted on their own step.
 */
object WizardReducer {
    fun reduce(
        state: WizardState,
        action: WizardAction,
        context: WizardContext,
    ): WizardState =
        when {
            state.exit != null -> state
            action == WizardAction.Next -> next(state, context)
            action == WizardAction.Back -> back(state, context)
            action == WizardAction.Cancel -> state.copy(exit = WizardExit.Cancelled)
            action == WizardAction.OpenAdvanced ->
                WizardFlow.handoff(state, context)?.let { state.copy(exit = WizardExit.Advanced(it)) } ?: state
            else -> choose(state, action, context)
        }

    private fun choose(
        state: WizardState,
        action: WizardAction,
        context: WizardContext,
    ): WizardState =
        when (action) {
            is WizardAction.ChooseGoal -> onGoal(state, action, context)
            is WizardAction.ChooseFilter -> onContent(state, context) { ContentRecipes.withFilter(it, action.id) }
            is WizardAction.ToggleApp -> onContent(state, context) { ContentRecipes.toggleApp(it, action.app) }
            is WizardAction.ToggleFeed ->
                onContent(state, context) { ContentRecipes.toggleFeed(it, action.key, context.feeds) }
            is WizardAction.SetQuery -> onContent(state, context) { ContentRecipes.withQuery(it, action.text) }
            is WizardAction.ChooseLook -> onLook(state, action, context)
            is WizardAction.ChooseDestination -> onDestination(state, action, context)
            is WizardAction.SetSaveToLibrary ->
                if (state.step == WizardStep.SAVE) state.copy(saveToLibrary = action.save) else state
            is WizardAction.SetName -> if (state.step == WizardStep.SAVE) state.copy(name = action.name) else state
            else -> state
        }

    private fun onGoal(
        state: WizardState,
        action: WizardAction.ChooseGoal,
        context: WizardContext,
    ): WizardState {
        val choice = WizardFlow.goalChoices(context).firstOrNull { it.goal.id == action.id }
        return when {
            state.step != WizardStep.SOURCE || choice == null || !choice.enabled -> state
            state.setup?.goal == action.id -> state
            else -> {
                val keepLensOnly = if (state.destination == Destination.LENS_ONLY) Destination.LENS_ONLY else null
                state.copy(
                    setup = ContentRecipes.defaultSetup(choice.goal),
                    destination = keepLensOnly,
                    widgetTarget = null,
                    name = null,
                )
            }
        }
    }

    /** A content edit; the look is kept when it still fits somewhere, else the first look that does is taken. */
    private fun onContent(
        state: WizardState,
        context: WizardContext,
        edit: (GoalSetup) -> GoalSetup,
    ): WizardState {
        val setup = state.setup?.takeIf { state.step == WizardStep.CONTENT } ?: return state
        val edited = edit(setup)
        val looks = ContentRecipes.looksFor(edited, sources = context.descriptors)
        val look = looks.firstOrNull { it.id == edited.lookId } ?: looks.firstOrNull()
        return if (edited == setup) state else state.copy(setup = look?.let { edited.copy(lookId = it.id) } ?: edited)
    }

    private fun onLook(
        state: WizardState,
        action: WizardAction.ChooseLook,
        context: WizardContext,
    ): WizardState {
        val setup = state.setup?.takeIf { state.step == WizardStep.LOOK }
        val choice = WizardFlow.lookChoices(state, context).firstOrNull { it.look.id == action.id && it.enabled }
        return if (setup == null || choice == null) {
            state
        } else {
            val keep = state.destination?.takeIf { it in choice.destinations }
            state.copy(setup = setup.copy(lookId = action.id), destination = keep, widgetTarget = null)
        }
    }

    private fun onDestination(
        state: WizardState,
        action: WizardAction.ChooseDestination,
        context: WizardContext,
    ): WizardState {
        val choice = WizardFlow.destinationChoices(state, context).firstOrNull { it.destination == action.destination }
        return if (state.step != WizardStep.PLACE || choice == null || !choice.enabled) {
            state
        } else {
            state.copy(destination = action.destination, widgetTarget = targetOf(choice, action.target))
        }
    }

    private fun targetOf(
        choice: DestinationChoice,
        wanted: WidgetTarget?,
    ): WidgetTarget? =
        if (choice.destination == Destination.WIDGET) {
            wanted?.takeIf { it in choice.widgetTargets } ?: choice.widgetTargets.firstOrNull()
        } else {
            null
        }

    private fun next(
        state: WizardState,
        context: WizardContext,
    ): WizardState {
        if (!WizardFlow.canAdvance(state, context)) return state
        val steps = WizardFlow.steps(state, context)
        val following = steps.getOrNull(steps.indexOf(state.step) + 1) ?: WizardStep.DONE
        return when (following) {
            WizardStep.DONE ->
                if (WizardFlow.allProblems(state, context).isEmpty()) {
                    state.copy(step = WizardStep.DONE, exit = WizardExit.Finished)
                } else {
                    state
                }
            WizardStep.PLACE -> enterPlace(state.copy(step = WizardStep.PLACE), context)
            else -> state.copy(step = following)
        }
    }

    /** Keeps an enabled destination; otherwise starts on the default one. */
    private fun enterPlace(
        state: WizardState,
        context: WizardContext,
    ): WizardState {
        val choices = WizardFlow.destinationChoices(state, context)
        val current = choices.firstOrNull { it.destination == state.destination && it.enabled }
        val choice = current ?: WizardFlow.defaultDestination(state, context)
        return state.copy(
            destination = choice?.destination,
            widgetTarget = choice?.let { targetOf(it, state.widgetTarget) },
        )
    }

    private fun back(
        state: WizardState,
        context: WizardContext,
    ): WizardState {
        val steps = WizardFlow.steps(state, context)
        val index = steps.indexOf(state.step)
        return if (index <= 0) state.copy(exit = WizardExit.Cancelled) else state.copy(step = steps[index - 1])
    }
}
