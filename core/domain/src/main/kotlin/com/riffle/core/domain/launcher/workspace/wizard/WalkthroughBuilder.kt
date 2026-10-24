package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.editor.WidgetSlots

/** The workspace a walkthrough state describes, with which goals each page holds and what goes into the pool. */
data class BuiltHome(
    val workspace: Workspace,
    val goalsByPage: Map<ContainerId, List<ContentGoalId>>,
    /** The apps to place (Favourite apps), on [WalkthroughBuilder.APPS_PAGE]. */
    val apps: List<AppPick>,
    /** Keep the user's current home ("Widgets from my home"): a placed page that borrows, or a fork of, it. */
    val keepHome: Boolean,
)

/**
 * Builds the walkthrough's workspace, deterministically: the same state gives the same workspace with the same ids
 * (`walkthrough:home:<goal>`), which is what makes re-applying idempotent. Placed goals share one `home.grid` page;
 * widget goals fill a widgets page (a new one when it is full) in arrangement order, each at its default span in the
 * first free cell (`WidgetSlots`); every other goal is a page, or a page-set for a per-group look.
 */
object WalkthroughBuilder {
    /** The arrangement page Favourite apps go on. */
    val APPS_PAGE = LauncherPageId("walkthrough-apps")

    fun build(
        state: WalkthroughState,
        context: WalkthroughContext,
    ): BuiltHome {
        val chosen = state.chosen.toSet()
        val keepHome = ContentGoalId.HOME_ITEMS in chosen
        val pages = Pages(WalkthroughFlow.WORKSPACE_ID.value, keepHome)
        state.goals.forEach { arranged ->
            val setup = state.setups[arranged.goal]
            val destination = setup?.let { WalkthroughFlow.destination(it, arranged.role, context) }
            if (setup != null && destination != null) pages.add(setup, destination)
        }
        val hidden = state.dockHidden && WalkthroughFlow.canHideDock(context)
        val workspace =
            Workspace(
                id = WalkthroughFlow.WORKSPACE_ID,
                name = WalkthroughFlow.WORKSPACE_NAME,
                pages = pages.hosts.toList(),
                dock = WorkspaceDock(presentation = if (hidden) DockPresentation(hidden = true) else null),
                startPageId = state.startGoal?.let(pages.pageOfGoal::get),
            )
        val favourites = state.setups[ContentGoalId.FAVOURITE_APPS]?.takeIf { ContentGoalId.FAVOURITE_APPS in chosen }
        return BuiltHome(
            workspace,
            pages.goalsByPage.mapValues { it.value.toList() },
            favourites?.apps.orEmpty(),
            keepHome,
        )
    }

    private class Pages(
        private val base: String,
        private val keepHome: Boolean,
    ) {
        val hosts = mutableListOf<PageHost>()
        val goalsByPage = LinkedHashMap<ContainerId, MutableList<ContentGoalId>>()
        val pageOfGoal = HashMap<ContentGoalId, ContainerId>()
        private var widgetPage: PageContainer? = null
        private var widgetPages = 0

        fun add(
            setup: GoalSetup,
            destination: Destination,
        ) {
            val goal = ContentRecipes.goal(setup)
            val pageId =
                when {
                    goal.placedItems -> placed(setup)
                    destination == Destination.WIDGET -> widget(setup)
                    else -> page(setup, destination)
                }
            goalsByPage.getOrPut(pageId) { mutableListOf() } += goal.id
            pageOfGoal[goal.id] = pageId
        }

        private fun placed(setup: GoalSetup): ContainerId {
            val id = ContainerId("$base:home")
            if (hosts.none { it.id == id }) {
                val key = if (keepHome) PLACEHOLDER_PAGE_KEY else APPS_PAGE.value
                hosts += PageContainer(id, PageContent.Bound(ContentRecipes.binding(setup, pageKey = key)))
            }
            return id
        }

        private fun page(
            setup: GoalSetup,
            destination: Destination,
        ): ContainerId {
            val id = ContainerId("$base:${setup.goal.key}")
            val binding = ContentRecipes.binding(setup)
            hosts +=
                if (destination == Destination.PAGE_SET) {
                    PageSetContainer(id, binding)
                } else {
                    PageContainer(id, PageContent.Bound(binding))
                }
            return id
        }

        /** Into the current widgets page when it has room, else onto a new one. */
        private fun widget(setup: GoalSetup): ContainerId {
            val binding = ContentRecipes.binding(setup)
            val span = WidgetSlots.defaultSpan(binding.expression)
            val widget = WidgetContainer(ContainerId("$base:w-${setup.goal.key}"), span, binding)
            val current = widgetPage
            val grid = current?.content as? PageContent.WidgetGrid
            val slot = grid?.let { WidgetSlots.firstFree(it, span) }
            val page =
                if (current != null && grid != null && slot != null) {
                    val placements = grid.placements + WidgetPlacement(widget, slot.column, slot.row)
                    current.copy(content = grid.copy(placements = placements)).also { updated ->
                        hosts.replaceAll { if (it.id == updated.id) updated else it }
                    }
                } else {
                    widgetPages += 1
                    val id = ContainerId(if (widgetPages == 1) "$base:widgets" else "$base:widgets-$widgetPages")
                    val content =
                        PageContent.WidgetGrid(
                            WidgetSlots.DEFAULT_COLUMNS,
                            WidgetSlots.DEFAULT_ROWS,
                            listOf(WidgetPlacement(widget, 0, 0)),
                        )
                    PageContainer(id, content).also { hosts += it }
                }
            widgetPage = page
            return page.id
        }
    }
}
