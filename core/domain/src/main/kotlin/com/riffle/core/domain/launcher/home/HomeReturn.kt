package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import com.riffle.core.domain.launcher.workspace.ReturnEvent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceReturnResolver

/**
 * Pure: the standard (non-preview) Home pager applying the Return setting (owner decision Q7).
 *
 * The standard layout has no workspace and no start page of its own, so "Start page" means the first page of
 * the active layout, which is also the page a Home button press has always opened. The decision reuses
 * [WorkspaceReturnResolver] over a stand-in workspace that holds the layout's pages in order (no start page, no
 * Finder), so the standard shell and the Workspaces preview cannot disagree on the rule.
 *
 * Never throws and never changes the layout, only which page is selected.
 */
object HomeReturn {
    /**
     * The page to select, or null to leave everything as it is.
     *
     * Null when the launcher is not [atTopLevel] (an overlay, the drawer, search, settings or an edit mode is
     * showing, so the system's Back and Home handling owns the screen), when the layout has no pages, or when
     * the rule names the page already selected. With [ReturnBehavior.RESTORE] a [ReturnEvent.RETURN] is
     * therefore always null: the default changes nothing.
     */
    fun landingPage(
        layout: HomeLayout,
        behavior: ReturnBehavior,
        event: ReturnEvent = ReturnEvent.RETURN,
        atTopLevel: Boolean = layout.editMode == HomeEditMode.Browsing,
    ): LauncherPageId? {
        if (!atTopLevel || layout.pages.isEmpty()) return null
        val standIn = standIn(layout)
        val selected = ContainerId(slot(layout.selectedPageIndex))
        val landed = WorkspaceReturnResolver.resolve(standIn, behavior, selected, event)
        val index = landed?.let { id -> standIn.pages.indexOfFirst { it.id == id } } ?: -1
        return layout.pages
            .getOrNull(index)
            ?.id
            ?.takeIf { it != layout.selectedPageId }
    }

    // Stand-in page ids are by position, so a blank or duplicated LauncherPageId cannot break the stand-in.
    private fun slot(index: Int) = "page-$index"

    private fun standIn(layout: HomeLayout): Workspace {
        val binding = LensBinding(Lens(sources = listOf(SourceId("home"))), ExpressionKind.LIST)
        return Workspace(
            id = WorkspaceId("standard-home"),
            name = "Home",
            pages = layout.pages.indices.map { PageContainer(ContainerId(slot(it)), PageContent.Bound(binding)) },
        )
    }
}

/**
 * The page the standard shell should select when the launcher comes back from another app, or null for none.
 *
 * Only the top level counts: Home is the destination and the layout is being browsed. The drawer, search,
 * notifications, Settings and the page editing modes are left exactly as they are, so Back and Home keep
 * closing them the way they always have. With the default setting this is always null.
 */
fun LauncherShellState.homeReturnTarget(event: ReturnEvent = ReturnEvent.RETURN): LauncherPageId? =
    HomeReturn.landingPage(
        layout = homeLayout,
        behavior = launcherSettings.home.returnBehavior,
        event = event,
        atTopLevel = destination == ShellDestination.HOME && homeLayout.editMode == HomeEditMode.Browsing,
    )
