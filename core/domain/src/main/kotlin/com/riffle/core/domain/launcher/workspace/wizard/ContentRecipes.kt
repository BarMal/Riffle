package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.ExpressionCatalog
import com.riffle.core.domain.launcher.workspace.GestureAxis
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensExpressionValidity
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceParameter
import com.riffle.core.domain.launcher.workspace.homeGridLens

/**
 * The user's choices for one goal: the shared "what to show" and "how it looks" state of the wizard and of each goal
 * in the walkthrough. Holds identities and lens configuration only, never item content.
 *
 * [apps] are the apps to place (a placed goal) or the "only these apps" filter (notifications); [feeds] are feed keys
 * (none means every feed); [query] is the lens's own search text (empty follows the shared search box).
 */
data class GoalSetup(
    val goal: ContentGoalId,
    val filterId: String,
    val lookId: String,
    val apps: List<AppPick> = emptyList(),
    val feeds: List<String> = emptyList(),
    val query: String = "",
)

/** Why a goal's setup cannot be used yet. The UI words it (see the doc's copy table). */
enum class SetupProblem {
    /** A placed goal (Favourite apps) needs at least one app. */
    PICK_AN_APP,

    /** The chosen look cannot go to the chosen destination. */
    LOOK_DOES_NOT_FIT,
}

/**
 * Pure helpers over [ContentGoalCatalog] recipes: building a goal's lens and binding from a [GoalSetup], the looks
 * valid for a destination, and setup edits that keep the setup consistent. Every pairing check here is
 * `LensExpressionValidity`'s (per group for a page-set, with the container rule that a page-set's expression does not
 * scroll sideways), so nothing invalid is offered.
 */
@Suppress("TooManyFunctions") // Small pure recipe rules shared by the wizard and the walkthrough.
object ContentRecipes {
    fun goal(setup: GoalSetup): ContentGoal = ContentGoalCatalog.goal(setup.goal)

    /** The default setup for [goal]: its default filter and look, with [apps] pre-picked when it takes apps. */
    fun defaultSetup(
        goal: ContentGoal,
        apps: List<AppPick> = emptyList(),
    ): GoalSetup =
        GoalSetup(
            goal = goal.id,
            filterId = goal.defaultFilter.id,
            lookId = goal.defaultLook.id,
            apps = if (takesPlacedApps(goal)) apps.distinctBy { it.identity } else emptyList(),
        )

    fun look(setup: GoalSetup): GoalLook = goal(setup).let { it.look(setup.lookId) ?: it.defaultLook }

    fun filter(setup: GoalSetup): GoalFilter = goal(setup).let { it.filter(setup.filterId) ?: it.defaultFilter }

    /**
     * The lens [setup] draws with [look] (the setup's own by default). A placed goal reads `home.grid` page
     * [pageKey]; any other goal is its base lens refined by the filter, the pickers, the look's shaping and the query.
     */
    fun lens(
        setup: GoalSetup,
        look: GoalLook = look(setup),
        pageKey: String = PLACEHOLDER_PAGE_KEY,
    ): Lens {
        val goal = goal(setup)
        if (goal.placedItems) return homeGridLens(pageKey)
        val base = goal.baseLens
        val query = setup.query.takeIf { GoalPicker.QUERY in goal.pickers }?.let(SourceParameter::query)
        return base.copy(
            filter = combine(filter(setup).filter, pickerFilter(goal, setup)),
            group = look.group,
            sort = look.sort ?: base.sort,
            limit = look.limit ?: base.limit,
            parameters = if (query == null) base.parameters else base.parameters + (base.sources.first() to query),
        )
    }

    fun binding(
        setup: GoalSetup,
        look: GoalLook = look(setup),
        pageKey: String = PLACEHOLDER_PAGE_KEY,
    ): LensBinding = LensBinding(lens(setup, look, pageKey), look.expression)

    /** Whether [look] of [setup]'s goal can be drawn at [destination] (null: anywhere it lists). */
    fun fits(
        setup: GoalSetup,
        look: GoalLook,
        destination: Destination,
        sources: List<SourceDescriptor>? = null,
    ): Boolean {
        val goal = goal(setup)
        val lens = lens(setup, look)
        return when (destination) {
            // A saved lens is only a definition: it must be drawable as the look draws it (per group for a page-set).
            Destination.LENS_ONLY ->
                !goal.placedItems && (pairs(lens, look, sources) || pairsPerGroup(lens, look, sources))
            Destination.PAGE_SET -> destination in look.placements && pairsPerGroup(lens, look, sources)
            Destination.PAGE, Destination.WIDGET, Destination.DOCK_SECTION ->
                destination in look.placements && pairs(lens, look, sources)
        }
    }

    /** The looks of [setup]'s goal that fit [destination] (any of their own placements when null), in catalog order. */
    fun looksFor(
        setup: GoalSetup,
        destination: Destination? = null,
        sources: List<SourceDescriptor>? = null,
    ): List<GoalLook> =
        goal(setup).looks.filter { look ->
            if (destination == null) {
                look.placements.any { fits(setup, look, it, sources) }
            } else {
                fits(setup, look, destination, sources)
            }
        }

    /** The destinations [setup]'s current look fits, in the look's preference order, then [Destination.LENS_ONLY]. */
    fun destinationsFor(
        setup: GoalSetup,
        sources: List<SourceDescriptor>? = null,
    ): List<Destination> {
        val look = look(setup)
        return (look.placements + Destination.LENS_ONLY).filter { fits(setup, look, it, sources) }
    }

    /** What still blocks [setup] at [destination] (null: only the setup itself is checked). */
    fun problems(
        setup: GoalSetup,
        destination: Destination? = null,
        sources: List<SourceDescriptor>? = null,
    ): List<SetupProblem> {
        val goal = goal(setup)
        return buildList {
            if (takesPlacedApps(goal) && setup.apps.isEmpty()) {
                add(SetupProblem.PICK_AN_APP)
            }
            if (destination != null && !fits(setup, look(setup), destination, sources)) {
                add(SetupProblem.LOOK_DOES_NOT_FIT)
            }
        }
    }

    /** Selects filter [id]; an id the goal does not offer leaves [setup] unchanged. */
    fun withFilter(
        setup: GoalSetup,
        id: String,
    ): GoalSetup = if (goal(setup).filter(id) == null) setup else setup.copy(filterId = id)

    /** Selects look [id] when it fits somewhere ([destination] when given); otherwise [setup] is unchanged. */
    fun withLook(
        setup: GoalSetup,
        id: String,
        destination: Destination? = null,
        sources: List<SourceDescriptor>? = null,
    ): GoalSetup {
        val look = goal(setup).look(id) ?: return setup
        return if (look in looksFor(setup, destination, sources)) setup.copy(lookId = id) else setup
    }

    /** Adds or removes [app]; ignored when the goal takes no apps. */
    fun toggleApp(
        setup: GoalSetup,
        app: AppPick,
    ): GoalSetup =
        when {
            GoalPicker.APPS !in goal(setup).pickers -> setup
            setup.apps.any { it.identity == app.identity } ->
                setup.copy(apps = setup.apps.filterNot { it.identity == app.identity })
            else -> setup.copy(apps = setup.apps + app)
        }

    /** Adds or removes feed [key] when [offered] lists it for the goal's source; ignored otherwise. */
    fun toggleFeed(
        setup: GoalSetup,
        key: String,
        offered: List<FeedOption>,
    ): GoalSetup {
        val goal = goal(setup)
        val valid = GoalPicker.FEEDS in goal.pickers && offered.any { it.key == key && it.source in goal.sources }
        return when {
            !valid && key !in setup.feeds -> setup
            key in setup.feeds -> setup.copy(feeds = setup.feeds - key)
            else -> setup.copy(feeds = setup.feeds + key)
        }
    }

    /** Sets the kept search text; ignored when the goal takes none. Normalized like `SourceParameter.query`. */
    fun withQuery(
        setup: GoalSetup,
        text: String,
    ): GoalSetup =
        if (GoalPicker.QUERY in goal(setup).pickers) {
            setup.copy(query = SourceParameter.query(text)?.text.orEmpty())
        } else {
            setup
        }

    /**
     * [setup] with a look that fits [destination]: its own when it does, else the first that does. Unchanged when none
     * does (the caller reports [SetupProblem.LOOK_DOES_NOT_FIT]).
     */
    fun reconcile(
        setup: GoalSetup,
        destination: Destination,
        sources: List<SourceDescriptor>? = null,
    ): GoalSetup {
        if (fits(setup, look(setup), destination, sources)) return setup
        return looksFor(setup, destination, sources).firstOrNull()?.let { setup.copy(lookId = it.id) } ?: setup
    }

    private fun takesPlacedApps(goal: ContentGoal): Boolean = goal.placedItems && GoalPicker.APPS in goal.pickers

    private fun pickerFilter(
        goal: ContentGoal,
        setup: GoalSetup,
    ): LensFilter {
        val keys =
            when {
                GoalPicker.APPS in goal.pickers -> setup.apps.map { it.notificationGroupKey }
                GoalPicker.FEEDS in goal.pickers -> setup.feeds
                else -> emptyList()
            }.distinct()
        return when (keys.size) {
            0 -> LensFilter.All
            1 -> LensFilter.GroupKeyIs(keys.single())
            else -> LensFilter.AnyOf(keys.map(LensFilter::GroupKeyIs))
        }
    }

    private fun combine(
        first: LensFilter,
        second: LensFilter,
    ): LensFilter =
        when {
            first == LensFilter.All -> second
            second == LensFilter.All -> first
            else -> LensFilter.AllOf(listOf(first, second))
        }

    private fun pairs(
        lens: Lens,
        look: GoalLook,
        sources: List<SourceDescriptor>?,
    ): Boolean = LensExpressionValidity.check(lens, look.expression, sources).isValid

    private fun pairsPerGroup(
        lens: Lens,
        look: GoalLook,
        sources: List<SourceDescriptor>?,
    ): Boolean =
        lens.group != LensGroup.None &&
            LensExpressionValidity.checkPerGroup(lens, look.expression, sources).isValid &&
            ExpressionCatalog.descriptorFor(look.expression).axes.none {
                it == GestureAxis.HORIZONTAL_SCROLL || it == GestureAxis.HORIZONTAL_PAGER
            }
}
