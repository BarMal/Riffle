package com.riffle.core.domain.launcher.workspace

/**
 * Which page the active workspace shows when the launcher comes back (owner decision Q7). Global, not
 * per workspace. The default is [RESTORE].
 */
enum class ReturnBehavior {
    /** Back to the page that was showing. */
    RESTORE,

    /** The first pager page, never the Finder. */
    FIRST_PAGE,

    /** The workspace's start page. */
    START_PAGE,
}

/** What brought the launcher to the front. Android makes these different. */
enum class ReturnEvent {
    /** Back from an app launch, Recents, a process restart or a posture change. */
    RETURN,

    /** The Home button pressed while the launcher is already in front. */
    HOME_PRESS,

    /** The Home button pressed while the Finder is open: close it and go back to where it was opened from. */
    HOME_PRESS_FINDER_OPEN,
}

/**
 * Pure: picks the page of one workspace to show. It never changes the workspace, only the page, and never
 * throws: ids that name no page are ignored and the result falls back to the start page, then the first pager
 * page. Null only for a workspace with no pages.
 *
 * - [ReturnEvent.RETURN]: RESTORE shows the last page (else the start page), FIRST_PAGE the first pager page,
 *   START_PAGE the start page.
 * - [ReturnEvent.HOME_PRESS]: the start page, whatever the behavior.
 * - [ReturnEvent.HOME_PRESS_FINDER_OPEN]: the page the Finder was opened from (else the start page), whatever
 *   the behavior.
 *
 * [lastVisited] is the page that was showing (for [ReturnEvent.HOME_PRESS_FINDER_OPEN], the page the Finder
 * was opened from). The Finder is a surface, not a place to come back to, so a [lastVisited] that names the
 * Finder is ignored.
 */
object WorkspaceReturnResolver {
    fun resolve(
        workspace: Workspace,
        behavior: ReturnBehavior,
        lastVisited: ContainerId?,
        event: ReturnEvent = ReturnEvent.RETURN,
    ): ContainerId? {
        val start = workspace.effectiveStartPageId
        val last =
            lastVisited?.takeIf { id -> workspace.pages.any { it.id == id } && !workspace.isFinder(id) }
        return when (event) {
            ReturnEvent.HOME_PRESS -> start
            ReturnEvent.HOME_PRESS_FINDER_OPEN -> last ?: start
            ReturnEvent.RETURN ->
                when (behavior) {
                    ReturnBehavior.RESTORE -> last ?: start
                    ReturnBehavior.FIRST_PAGE -> workspace.firstPageId ?: start
                    ReturnBehavior.START_PAGE -> start
                }
        }
    }
}
