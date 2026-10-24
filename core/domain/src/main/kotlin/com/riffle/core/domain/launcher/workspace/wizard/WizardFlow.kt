package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.LensNames
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.ContainerOptions
import com.riffle.core.domain.launcher.workspace.editor.LensDraft
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess

/**
 * Read side of the wizard: what each step offers, which steps are shown, the live preview, the suggested name and what
 * blocks a step. [WizardReducer] is the write side and [WizardApply] applies the result. Every option is derived from
 * [ContentRecipes] (so from `LensExpressionValidity`) and from the editor's `ContainerOptions` (so from
 * `WorkspaceEditor`): an option is enabled exactly when applying it would be accepted.
 */
@Suppress("TooManyFunctions") // One read-side query per step and option, like `BindingFlow`.
object WizardFlow {
    /** The initial state for [context]: the goal pre-selected by "Use this source" when there is exactly one. */
    fun start(context: WizardContext): WizardState {
        val entry = context.entry
        val save = entry !is WizardEntry.FromSource
        val destination = if (entry == WizardEntry.NewSavedLens) Destination.LENS_ONLY else null
        val base = WizardState(saveToLibrary = save, destination = destination)
        val preselected = preselectedGoal(context) ?: return base
        val state = base.copy(setup = ContentRecipes.defaultSetup(preselected))
        return state.copy(step = steps(state, context).first())
    }

    /** The goal "Use this source" pre-selects: the only enabled goal reading that source. */
    fun preselectedGoal(context: WizardContext): ContentGoal? {
        val entry = context.entry as? WizardEntry.FromSource ?: return null
        val ids = ContentGoalCatalog.goalsFor(entry.source).map { it.id }.toSet()
        return goalChoices(context).filter { it.goal.id in ids && it.enabled }.singleOrNull()?.goal
    }

    /** The steps shown for the current choices, in order, always ending with [WizardStep.DONE]. */
    fun steps(
        state: WizardState,
        context: WizardContext,
    ): List<WizardStep> {
        val goal = state.setup?.let(ContentRecipes::goal)
        val preselected = preselectedGoal(context)
        return buildList {
            if (preselected == null || goal?.id != preselected.id) add(WizardStep.SOURCE)
            if (goal == null || goal.filters.size > 1 || goal.pickers.isNotEmpty()) add(WizardStep.CONTENT)
            add(WizardStep.LOOK)
            if (context.entry != WizardEntry.NewSavedLens) add(WizardStep.PLACE)
            if (goal?.placedItems != true) add(WizardStep.SAVE)
            add(WizardStep.DONE)
        }
    }

    /** Every goal of the catalog with whether it can be chosen here; "Use this source" lists its goals first. */
    fun goalChoices(context: WizardContext): List<GoalChoice> {
        val first = (context.entry as? WizardEntry.FromSource)?.source
        return ContentGoalCatalog.all
            .sortedBy { if (first != null && first in it.sources) 0 else 1 }
            .map { goalChoice(it, context) }
    }

    fun goalChoice(
        goal: ContentGoal,
        context: WizardContext,
    ): GoalChoice {
        val base = baseChoice(goal, context.sources)
        val workspace = context.workspace
        val availability =
            when {
                base.availability == GoalAvailability.UNAVAILABLE -> GoalAvailability.UNAVAILABLE
                goal.placedItems && context.entry == WizardEntry.NewSavedLens -> GoalAvailability.UNAVAILABLE
                goal.id == ContentGoalId.HOME_ITEMS && workspace != null && WorkspaceHome.hasHomeGrid(workspace) ->
                    GoalAvailability.ALREADY_THERE
                else -> base.availability
            }
        return GoalChoice(
            goal,
            availability,
            if (availability == GoalAvailability.NEEDS_ACCESS) base.needs else emptyList(),
        )
    }

    /**
     * Whether [goal]'s sources allow it, from the source choices alone: unavailable when one is missing or not
     * selectable, "needs access" when one's access is required (`home.grid` is the pool and always there).
     */
    fun baseChoice(
        goal: ContentGoal,
        sources: List<SourceChoice>,
    ): GoalChoice {
        val offered =
            goal.sources.filter { it != WorkspaceSourceIds.HOME_GRID }.map {
                    id ->
                sources.firstOrNull { it.id == id }
            }
        val needs =
            offered.filterNotNull().filter { it.access == SourceAccess.REQUIRED }
                .mapNotNull { SourcePermission.forSource(it.id) }.distinct()
        val availability =
            when {
                offered.any { it == null || !it.selectable } -> GoalAvailability.UNAVAILABLE
                needs.isNotEmpty() -> GoalAvailability.NEEDS_ACCESS
                else -> GoalAvailability.READY
            }
        return GoalChoice(goal, availability, needs)
    }

    /** The looks of the chosen goal; enabled when they can go somewhere from this entry. */
    fun lookChoices(
        state: WizardState,
        context: WizardContext,
    ): List<LookChoice> {
        val setup = state.setup ?: return emptyList()
        return ContentRecipes.goal(setup).looks.map { look ->
            val candidate = setup.copy(lookId = look.id)
            val destinations =
                destinationChoices(state.copy(setup = candidate), context).filter {
                    it.enabled
                }.map { it.destination }
            LookChoice(look, destinations.isNotEmpty(), destinations)
        }
    }

    /**
     * The Place step's options for the chosen look: each container destination it lists, enabled exactly when the
     * editor accepts that edit on the target workspace, then "Save as a lens only". For "Save as a lens" (Settings >
     * Saved lenses > New) only [Destination.LENS_ONLY] is offered.
     */
    fun destinationChoices(
        state: WizardState,
        context: WizardContext,
    ): List<DestinationChoice> = state.setup?.let { destinationChoices(it, context) }.orEmpty()

    private fun destinationChoices(
        setup: GoalSetup,
        context: WizardContext,
    ): List<DestinationChoice> {
        val look = ContentRecipes.look(setup)
        val lensOnly =
            DestinationChoice(
                Destination.LENS_ONLY,
                ContentRecipes.fits(setup, look, Destination.LENS_ONLY, context.descriptors) &&
                    context.layout.library.lenses.size < MAX_SAVED_LENSES,
            )
        val placed = ContentRecipes.goal(setup).placedItems
        return when {
            context.entry == WizardEntry.NewSavedLens -> listOf(lensOnly)
            placed -> look.placements.map { containerChoice(setup, look, it, context) }
            else -> look.placements.map { containerChoice(setup, look, it, context) } + lensOnly
        }
    }

    /** One container destination, enabled exactly when the editor accepts the edit (placed goals: a page). */
    private fun containerChoice(
        setup: GoalSetup,
        look: GoalLook,
        destination: Destination,
        context: WizardContext,
    ): DestinationChoice {
        val workspace = context.workspace
        val fits = ContentRecipes.fits(setup, look, destination, context.descriptors)
        return when {
            workspace == null || !fits -> DestinationChoice(destination, false)
            ContentRecipes.goal(setup).placedItems -> DestinationChoice(destination, true)
            else -> editorChoice(setup, look, destination, workspace, context)
        }
    }

    private fun editorChoice(
        setup: GoalSetup,
        look: GoalLook,
        destination: Destination,
        workspace: Workspace,
        context: WizardContext,
    ): DestinationChoice {
        val binding = ContentRecipes.binding(setup, look)
        val option =
            ContainerOptions.forBinding(binding, workspace, context.edit).first { it.kind == destination.kind() }
        val editorPage = (context.entry as? WizardEntry.Editor)?.pageId
        return DestinationChoice(
            destination,
            option.enabled,
            option.rejection,
            option.widgetTargets.sortedBy { if (it.pageId != null && it.pageId == editorPage) 0 else 1 },
            replaces = destination == Destination.DOCK_SECTION && workspace.dock.dynamicSection != null,
        )
    }

    /** The destination the Place step starts on: a widget on the editor's page when asked, else the first enabled. */
    fun defaultDestination(
        state: WizardState,
        context: WizardContext,
    ): DestinationChoice? {
        val enabled = destinationChoices(state, context).filter { it.enabled }
        val onPage = (context.entry as? WizardEntry.Editor)?.pageId != null
        return enabled.firstOrNull { onPage && it.destination == Destination.WIDGET } ?: enabled.firstOrNull()
    }

    /** What the Look and Place steps draw. Null before a goal is chosen. */
    fun preview(state: WizardState): WizardPreview? {
        val setup = state.setup ?: return null
        val goal = ContentRecipes.goal(setup)
        val look = ContentRecipes.look(setup)
        val perGroup =
            state.destination == Destination.PAGE_SET ||
                (state.destination == null && look.placements.first() == Destination.PAGE_SET)
        return WizardPreview(ContentRecipes.binding(setup, look), perGroup, goal.placedItems, setup.apps)
    }

    /** The name the lens is saved under: the user's, else a unique suggestion ("Notifications, by app"). */
    fun name(
        state: WizardState,
        context: WizardContext,
    ): String = state.name ?: suggestedName(state, context)

    fun suggestedName(
        state: WizardState,
        context: WizardContext,
    ): String {
        val setup = state.setup ?: return ""
        val base = "${ContentRecipes.goal(
            setup,
        ).label}, ${ContentRecipes.look(setup).label.replaceFirstChar { it.lowercase() }}"
        return LensNames.unique(base, LensNames.of(context.layout.library.lenses)) { " ${it + 1}" }
    }

    /** Whether the lens is saved: asked for, or required ([Destination.LENS_ONLY]); never for placed goals. */
    fun saves(state: WizardState): Boolean {
        val placed = state.setup?.let { ContentRecipes.goal(it).placedItems } == true
        return !placed && (state.saveToLibrary || state.destination == Destination.LENS_ONLY)
    }

    /** What blocks [step] (the current step by default). Empty means Next is allowed. */
    fun problems(
        state: WizardState,
        context: WizardContext,
        step: WizardStep = state.step,
    ): List<WizardProblem> {
        val setup = state.setup
        return when (step) {
            WizardStep.SOURCE -> sourceProblems(state, context)
            WizardStep.CONTENT ->
                setup?.let {
                    ContentRecipes.problems(it).map(WizardProblem::Setup)
                } ?: listOf(WizardProblem.NoGoal)
            WizardStep.LOOK ->
                if (setup == null || lookChoices(state, context).none { it.look.id == setup.lookId && it.enabled }) {
                    listOf(WizardProblem.Setup(SetupProblem.LOOK_DOES_NOT_FIT))
                } else {
                    emptyList()
                }
            WizardStep.PLACE -> placeProblems(state, context)
            WizardStep.SAVE -> nameProblems(state, context)
            WizardStep.DONE -> emptyList()
        }
    }

    /** Every problem of every shown step: empty exactly when [WizardApply.apply] would apply. */
    fun allProblems(
        state: WizardState,
        context: WizardContext,
    ): List<WizardProblem> = steps(state, context).flatMap { problems(state, context, it) }.distinct()

    fun canAdvance(
        state: WizardState,
        context: WizardContext,
    ): Boolean = state.exit == null && state.step != WizardStep.DONE && problems(state, context).isEmpty()

    /** The current draft for the advanced builder, or null when there is nothing it could take. */
    fun handoff(
        state: WizardState,
        context: WizardContext,
    ): AdvancedHandoff? {
        val setup = state.setup
        val source = (context.entry as? WizardEntry.FromSource)?.source
        return when {
            setup == null -> source?.let { AdvancedHandoff(LensDraft(sources = listOf(it))) }
            ContentRecipes.goal(setup).placedItems -> null
            else -> {
                val look = ContentRecipes.look(setup)
                val atLook = state.step.ordinal >= WizardStep.LOOK.ordinal
                AdvancedHandoff(
                    draft = LensDraft.from(ContentRecipes.lens(setup, look)),
                    expression = look.expression.takeIf { atLook },
                    container = state.destination?.kind(),
                )
            }
        }
    }

    private fun sourceProblems(
        state: WizardState,
        context: WizardContext,
    ): List<WizardProblem> {
        val setup = state.setup ?: return listOf(WizardProblem.NoGoal)
        val choice = goalChoice(ContentRecipes.goal(setup), context)
        return if (choice.enabled) emptyList() else listOf(WizardProblem.GoalUnavailable(setup.goal))
    }

    private fun placeProblems(
        state: WizardState,
        context: WizardContext,
    ): List<WizardProblem> {
        val destination = state.destination
        val choice = destinationChoices(state, context).firstOrNull { it.destination == destination }
        return when {
            destination != Destination.LENS_ONLY && context.workspace == null -> listOf(WizardProblem.NoWorkspace)
            destination == null -> listOf(WizardProblem.NoDestination)
            choice == null || !choice.enabled -> listOf(WizardProblem.DestinationRejected(choice?.rejection))
            else -> emptyList()
        }
    }

    private fun nameProblems(
        state: WizardState,
        context: WizardContext,
    ): List<WizardProblem> {
        if (!saves(state)) return emptyList()
        val library = context.layout.library
        val problem =
            if (library.lenses.size >= MAX_SAVED_LENSES) {
                LibraryProblem.LIBRARY_FULL
            } else {
                library.nameProblem(
                    name(state, context),
                )
            }
        return listOfNotNull(problem?.let(WizardProblem::Name))
    }
}

/** The editor's container kind for a destination; null for [Destination.LENS_ONLY]. */
fun Destination.kind(): ContainerKind? =
    when (this) {
        Destination.PAGE -> ContainerKind.PAGE
        Destination.PAGE_SET -> ContainerKind.PAGE_SET
        Destination.WIDGET -> ContainerKind.WIDGET
        Destination.DOCK_SECTION -> ContainerKind.DOCK_SECTION
        Destination.LENS_ONLY -> null
    }
