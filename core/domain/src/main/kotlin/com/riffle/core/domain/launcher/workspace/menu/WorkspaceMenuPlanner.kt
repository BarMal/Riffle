package com.riffle.core.domain.launcher.workspace.menu

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceResolution
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.isFinderPage

/** One group of a page-set, as the lens engine evaluated it. Transient: the planner never stores it. */
data class PageSetGroupRef(
    val key: String,
    val label: String?,
)

/**
 * Plans the dock's workspace menu from a layout's [WorkspaceSet]. Pure: no Android types, no I/O.
 *
 * - No set, or a set with no stored layouts (the workspace system is off or not migrated yet): null,
 *   so no surface is drawn and standard launcher mode is untouched.
 * - Switch entries list every workspace of the device class in display order; the stored active one is
 *   marked. When the active workspace cannot be drawn the default is displayed instead and
 *   [WorkspaceMenuModel.fallback] carries the reasons.
 * - Jump entries cover the displayed workspace's pager pages in order (page numbers skip the Finder); a
 *   page-set expands to its groups (from `groups`, in the given order) bounded by `maxGroupsPerPageSet`.
 *   The Finder page has its own entry, so it is not repeated as a jump entry.
 * - Finder is hidden when the displayed workspace has no Finder page (decision: never guess a
 *   "default Finder", because opening a page the workspace does not define would surprise the user).
 */
object WorkspaceMenuPlanner {
    const val DEFAULT_MAX_GROUPS_PER_PAGE_SET = 8

    fun plan(
        set: WorkspaceSet?,
        deviceClass: HomeLayoutDeviceClass,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
        groups: Map<ContainerId, List<PageSetGroupRef>> = emptyMap(),
        maxGroupsPerPageSet: Int = DEFAULT_MAX_GROUPS_PER_PAGE_SET,
    ): WorkspaceMenuModel? {
        if (set == null || set.layouts.isEmpty()) return null
        val layout = set.workspacesFor(deviceClass)
        val resolution = set.resolveActive(deviceClass, capabilities)
        val displayed = resolution.workspace
        val jump = jumpEntries(displayed, groups, maxGroupsPerPageSet.coerceAtLeast(0))
        return WorkspaceMenuModel(
            switchEntries =
                layout.workspaces.map {
                    WorkspaceSwitchEntry(
                        id = it.id,
                        name = it.name,
                        isActive = it.id == layout.activeId,
                        isDisplayed = it.id == displayed.id,
                    )
                },
            jumpEntries = jump.entries,
            omittedGroupCount = jump.omitted,
            finder = finderOf(displayed),
            editTarget = displayed.id,
            fallback =
                (resolution as? WorkspaceResolution.FellBack)?.let {
                    WorkspaceMenuFallback(it.requested, it.workspace.id, it.issues)
                },
        )
    }

    private class Jump(
        val entries: List<WorkspaceJumpEntry>,
        val omitted: Int,
    )

    private fun finderOf(workspace: Workspace): WorkspaceFinderEntry? =
        workspace.pages
            .firstOrNull { it.isFinder() }
            ?.let { WorkspaceFinderEntry(it.id) }

    private fun jumpEntries(
        workspace: Workspace,
        groups: Map<ContainerId, List<PageSetGroupRef>>,
        maxGroups: Int,
    ): Jump {
        var omitted = 0
        var pagerPosition = 0
        val entries =
            workspace.pages.flatMap { page ->
                // The Finder is not in the pager, so it takes no page number.
                val number = if (page.isFinder()) 0 else ++pagerPosition
                when {
                    page.isFinder() -> emptyList()
                    page is PageSetContainer -> {
                        val all = groups[page.id].orEmpty().distinctBy { it.key }
                        omitted += (all.size - maxGroups).coerceAtLeast(0)
                        all.take(maxGroups).map {
                            WorkspaceJumpEntry(WorkspacePageKey.Group(page.id, it.key), number, it.label)
                        }
                    }
                    else -> listOf(WorkspaceJumpEntry(WorkspacePageKey.Page(page.id), number))
                }
            }
        return Jump(entries, omitted)
    }

    private fun PageHost.isFinder(): Boolean = isFinderPage()
}
