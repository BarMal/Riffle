package com.riffle.core.domain.launcher.workspace.container

/**
 * Remembers which page of a page-set the user is on, by page key rather than index, so a pager keeps its
 * place when groups appear, disappear or reorder. Holds no item content.
 *
 * The host calls [reconcile] when the plan changes (and scrolls to the returned index) and [settled] when
 * the pager settles on a page.
 */
class PageSetSelection {
    var selectedKey: String? = null
        private set

    /** Records the page the pager settled on. An index outside [plan] is ignored. */
    fun settled(
        plan: PageSetPlan,
        index: Int,
    ) {
        plan.pages.getOrNull(index)?.let { selectedKey = it.key }
    }

    /**
     * The index to show for [plan]: the remembered page if it still exists, else the page now at
     * [currentIndex] (clamped). The selection follows the result.
     */
    fun reconcile(
        plan: PageSetPlan,
        currentIndex: Int,
    ): Int {
        val index = plan.reconcileSelection(selectedKey, currentIndex)
        settled(plan, index)
        return index
    }
}
