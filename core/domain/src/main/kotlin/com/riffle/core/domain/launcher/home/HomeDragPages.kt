package com.riffle.core.domain.launcher.home

/**
 * The new page a Home drag can offer past the last one (the Home drag, "Edges"). Pure.
 *
 * Held at the trailing edge on the last page, a drag offers an empty page after it. The page is only a candidate: it is
 * created for the drag, kept if the item is dropped on it and discarded otherwise. It is offered when the page the drag
 * is over is the last, takes placed items, and is not itself empty (an empty last page is already a place to drop).
 */
object HomeDragPages {
    /** Whether holding at the trailing edge over [targetIndex] of [pages] offers a new page after the last. */
    fun offersNewPage(
        pages: List<LauncherPage>,
        targetIndex: Int,
    ): Boolean {
        val last = pages.lastOrNull() ?: return false
        return targetIndex == pages.lastIndex && last.type !is LauncherPageType.Generated && last.items.isNotEmpty()
    }

    /** Whether the candidate [candidateId] is kept: the drag ended with that page as its target. */
    fun keepsCandidate(
        candidateId: LauncherPageId,
        endedOnPageId: LauncherPageId?,
    ): Boolean = endedOnPageId == candidateId
}
