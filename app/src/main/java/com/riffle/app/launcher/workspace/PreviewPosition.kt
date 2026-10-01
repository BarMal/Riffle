package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import com.riffle.core.domain.launcher.workspace.ReturnEvent
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceReturnResolver
import com.riffle.core.domain.launcher.workspace.firstPageId
import com.riffle.core.domain.launcher.workspace.isFinder
import com.riffle.core.domain.launcher.workspace.pagerPages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where the preview is within one workspace: the pager page showing ([page], never the Finder) and whether the
 * Finder surface is open on top of it ([finderOpen]). The Finder is not in the pager, so opening it leaves
 * [page] where it was; that page is "where the Finder was opened from". Never persisted, and no Android types.
 */
internal data class PreviewPosition(
    val page: ContainerId? = null,
    val finderOpen: Boolean = false,
)

/** The launcher came back (Return) or Home was pressed while it was in front ([homePress]). */
internal data class ReturnRequest(
    val homePress: Boolean,
    /** Distinguishes two identical requests, so the second is not swallowed by a state flow. */
    val token: Long,
)

/** The pending [ReturnRequest]: latest wins, consuming a stale one leaves a newer one in place. */
internal class ReturnRequests {
    private val mutableCurrent = MutableStateFlow<ReturnRequest?>(null)
    private var token = 0L

    val current: StateFlow<ReturnRequest?> = mutableCurrent.asStateFlow()

    fun raise(homePress: Boolean) {
        mutableCurrent.value = ReturnRequest(homePress, ++token)
    }

    fun consume(request: ReturnRequest) {
        mutableCurrent.update { pending -> if (pending == request) null else pending }
    }

    fun clear() {
        mutableCurrent.value = null
    }
}

/** Pure transitions of [PreviewPosition]. Every one returns the receiver's position or a new one, never throws. */
internal object PreviewPositions {
    /** The first position a workspace shows when the preview opens: the Return rule with nothing visited yet. */
    fun initial(
        workspace: Workspace,
        behavior: ReturnBehavior,
    ): PreviewPosition =
        landing(workspace, PreviewPosition(), WorkspaceReturnResolver.resolve(workspace, behavior, null))

    /** The pager settled on [page]. The Finder cannot be a pager page; an unknown id is ignored. */
    fun settled(
        position: PreviewPosition,
        workspace: Workspace,
        page: ContainerId,
    ): PreviewPosition = if (workspace.pagerIndexOf(page) >= 0) position.copy(page = page) else position

    /** A menu jump or Finder entry: the Finder opens over the current page, any other page becomes current. */
    fun navigated(
        position: PreviewPosition,
        workspace: Workspace,
        target: ContainerId,
    ): PreviewPosition =
        when {
            workspace.isFinder(target) && target !in workspace.pagerIds() -> position.copy(finderOpen = true)
            workspace.pagerIndexOf(target) >= 0 -> PreviewPosition(page = target, finderOpen = false)
            else -> position
        }

    fun finderClosed(position: PreviewPosition): PreviewPosition = position.copy(finderOpen = false)

    /** Applies the Return setting to a [request]. The workspace is never changed, only the position in it. */
    fun returned(
        position: PreviewPosition,
        workspace: Workspace,
        behavior: ReturnBehavior,
        request: ReturnRequest,
    ): PreviewPosition {
        val event =
            when {
                !request.homePress -> ReturnEvent.RETURN
                position.finderOpen -> ReturnEvent.HOME_PRESS_FINDER_OPEN
                else -> ReturnEvent.HOME_PRESS
            }
        val target = WorkspaceReturnResolver.resolve(workspace, behavior, position.page, event)
        return landing(workspace, position, target)
    }

    /** A target that is the Finder (a start page may name it) opens it over the first pager page. */
    private fun landing(
        workspace: Workspace,
        from: PreviewPosition,
        target: ContainerId?,
    ): PreviewPosition =
        when {
            target == null -> from.copy(finderOpen = false)
            workspace.isFinder(target) && target !in workspace.pagerIds() ->
                PreviewPosition(page = from.page ?: workspace.firstPageId, finderOpen = true)
            else -> PreviewPosition(page = target, finderOpen = false)
        }
}

/** The pager index of [containerId] among the pages the pager swipes through (never the Finder), or -1. */
internal fun Workspace.pagerIndexOf(containerId: ContainerId): Int = pagerPages.indexOfFirst { it.id == containerId }

private fun Workspace.pagerIds(): List<ContainerId> = pagerPages.map { it.id }
