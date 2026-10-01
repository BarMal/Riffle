package com.riffle.core.domain.launcher.workspace

/**
 * The Finder is a page role, not a pager position: it opens through the Finder entry or the gesture bound to
 * it, never by swiping. These views are the one place that rule lives, so the shell, the menu and the return
 * logic agree on it.
 */
val Workspace.finderPage: PageContainer?
    get() = pages.firstOrNull { it.isFinderPage() } as PageContainer?

/**
 * The pages the horizontal pager swipes through, in order: every page except the Finder. A workspace made of
 * nothing but a Finder has nothing else to show, so it is the pager's only page rather than an empty pager.
 */
val Workspace.pagerPages: List<PageHost>
    get() = pages.filterNot { it.isFinderPage() }.ifEmpty { pages }

/** The first pager page: what "first page" means. Never the Finder unless nothing else exists. */
val Workspace.firstPageId: ContainerId?
    get() = pagerPages.firstOrNull()?.id

/**
 * The page the launcher opens on: [Workspace.startPageId] when it names a page of this workspace (the Finder
 * included), otherwise the first pager page. A stale id is ignored, never an error.
 */
val Workspace.effectiveStartPageId: ContainerId?
    get() = startPageId?.takeIf { id -> pages.any { it.id == id } } ?: firstPageId

/** True when [id] names this workspace's Finder page. */
fun Workspace.isFinder(id: ContainerId): Boolean = finderPage?.id == id

internal fun PageHost.isFinderPage(): Boolean = this is PageContainer && role == PageRole.FINDER
