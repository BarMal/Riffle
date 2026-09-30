package com.riffle.core.domain.launcher.workspace.menu

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue

/** Where a "jump to page" entry lands: a whole page, or one group's page of a page-set. */
sealed interface WorkspacePageKey {
    val containerId: ContainerId

    data class Page(override val containerId: ContainerId) : WorkspacePageKey

    /** One group of a page-set. [groupKey] is the lens group key (transient: never persisted). */
    data class Group(
        override val containerId: ContainerId,
        val groupKey: String,
    ) : WorkspacePageKey
}

/** One workspace the menu can switch to. */
data class WorkspaceSwitchEntry(
    val id: WorkspaceId,
    val name: String,
    /** The layout's stored active workspace. Exactly one entry is active. */
    val isActive: Boolean,
    /** The workspace actually drawn: differs from the active one only while [WorkspaceMenuFallback] applies. */
    val isDisplayed: Boolean,
)

/**
 * One page the menu can jump to. [pageNumber] is the 1-based position of the hosting page among the
 * displayed workspace's pages, so the UI can label unnamed pages ("Page 3"). For a page-set group,
 * [groupLabel] is the group's own label (null for the ungrouped bucket; the UI supplies the wording).
 */
data class WorkspaceJumpEntry(
    val key: WorkspacePageKey,
    val pageNumber: Int,
    val groupLabel: String? = null,
)

/** The Finder entry: opens the displayed workspace's Finder page, [pageId]. */
data class WorkspaceFinderEntry(val pageId: ContainerId)

/** The active workspace cannot be drawn on this layout, so [displayed] is shown instead, for [reasons]. */
data class WorkspaceMenuFallback(
    val requested: WorkspaceId,
    val displayed: WorkspaceId,
    val reasons: List<WorkspaceIssue>,
)

/**
 * What the dock's workspace menu shows, as data. Built by [WorkspaceMenuPlanner]; holds no item content
 * beyond transient page-set group labels supplied by the caller.
 */
data class WorkspaceMenuModel(
    val switchEntries: List<WorkspaceSwitchEntry>,
    val jumpEntries: List<WorkspaceJumpEntry>,
    /** Page-set groups left out of [jumpEntries] by the bound; reachable by paging. */
    val omittedGroupCount: Int,
    /** Null when the displayed workspace has no Finder page: the entry is hidden, not defaulted. */
    val finder: WorkspaceFinderEntry?,
    /** The workspace Edit opens: the displayed one. */
    val editTarget: WorkspaceId,
    val fallback: WorkspaceMenuFallback?,
) {
    /** A single workspace leaves nothing to switch to. */
    val hasSwitchChoice: Boolean get() = switchEntries.size > 1
}
