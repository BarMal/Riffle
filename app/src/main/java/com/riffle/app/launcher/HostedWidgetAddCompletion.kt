package com.riffle.app.launcher

import com.riffle.app.launcher.widgets.fitWidgetPreferredSpan
import com.riffle.app.launcher.widgets.widgetSpanAdjustmentToast
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.hostsWidget

internal fun LauncherShellViewModel.completeWidgetAdd(action: HostedWidgetAddAction): HostedWidgetAddCompletionResult =
    when (action) {
        is LauncherShellAction.AddHostedWidgetToHome -> completeHomeWidgetAdd(action)
        is LauncherShellAction.AddHostedWidgetToDock -> completeDockWidgetAdd(action)
        is LauncherShellAction.AddHostedWidgetToDockPanel -> completeDockPanelWidgetAdd(action)
    }

/**
 * Places a glance widget. Unlike a platform widget there is nothing to bind or configure, so this is the whole add:
 * the engine finds the cells (the span may come out smaller than asked, never below the kind's minimum), and a page
 * with no room leaves the layout as it was and reports [HostedWidgetAddCompletionResult.Rejected].
 */
internal fun LauncherShellViewModel.completeGlanceWidgetAdd(
    action: LauncherShellAction.AddGlanceWidgetToHome,
): HostedWidgetAddCompletionResult {
    val before = state.value.homeLayout
    val page = action.targetPageId?.let { id -> before.pages.firstOrNull { it.id == id } } ?: before.selectedPage
    val beforeIds = page.items.mapTo(HashSet()) { it.id }
    val fitted =
        action.copy(preferredSpan = action.preferredSpan.fitWidgetPreferredSpan(page.grid))
    onHomeShortcutEdited(fitted)
    val placed =
        state.value.homeLayout.pages
            .firstOrNull { it.id == page.id }
            ?.items
            .orEmpty()
            .filterIsInstance<WidgetItem>()
            .firstOrNull { it.glance == action.kind && it.id !in beforeIds }
    onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
    return if (placed == null) {
        HostedWidgetAddCompletionResult.Rejected
    } else {
        HostedWidgetAddCompletionResult.Placed(
            placed.placement?.span?.let { actual ->
                widgetSpanAdjustmentToast(label = action.label, idealSpan = action.preferredSpan, actualSpan = actual)
            },
        )
    }
}

private fun LauncherShellViewModel.completeHomeWidgetAdd(
    action: LauncherShellAction.AddHostedWidgetToHome,
): HostedWidgetAddCompletionResult {
    val hadHostedWidgetBeforeAdd = state.value.homeLayout.hostsWidget(action.hostedWidgetId)
    val fittedAction =
        action.copy(
            preferredSpan =
                action.preferredSpan.fitWidgetPreferredSpan(
                    state.value.homeLayout.selectedPage.grid,
                ),
        )
    onHomeShortcutEdited(fittedAction)
    val wasPlaced =
        !hadHostedWidgetBeforeAdd &&
            state.value.homeLayout.pageHasHostedWidget(action.hostedWidgetId, action.targetPageId)
    val adjustmentMessage =
        state.value.homeLayout.hostedWidgetSpanAdjustmentMessage(
            label = action.label,
            idealSpan = action.preferredSpan,
            hostedWidgetId = action.hostedWidgetId,
        )
    onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
    return if (wasPlaced) {
        HostedWidgetAddCompletionResult.Placed(adjustmentMessage)
    } else {
        HostedWidgetAddCompletionResult.Rejected
    }
}

private fun LauncherShellViewModel.completeDockWidgetAdd(
    action: LauncherShellAction.AddHostedWidgetToDock,
): HostedWidgetAddCompletionResult {
    val hadHostedWidgetBeforeAdd = state.value.homeLayout.hostsWidget(action.hostedWidgetId)
    onDockEdited(action)
    val wasPlaced =
        !hadHostedWidgetBeforeAdd &&
            state.value.homeLayout.dock.items.hasHostedWidget(action.hostedWidgetId)
    onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
    return if (wasPlaced) {
        HostedWidgetAddCompletionResult.Placed(message = null)
    } else {
        HostedWidgetAddCompletionResult.Rejected
    }
}

private fun LauncherShellViewModel.completeDockPanelWidgetAdd(
    action: LauncherShellAction.AddHostedWidgetToDockPanel,
): HostedWidgetAddCompletionResult {
    val hadHostedWidgetBeforeAdd = state.value.homeLayout.hostsWidget(action.hostedWidgetId)
    onHomeShortcutEdited(action)
    val wasPlaced =
        !hadHostedWidgetBeforeAdd &&
            state.value.homeLayout.dock.panel?.items.orEmpty().hasHostedWidget(action.hostedWidgetId)
    onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
    return if (wasPlaced) {
        HostedWidgetAddCompletionResult.Placed(message = null)
    } else {
        HostedWidgetAddCompletionResult.Rejected
    }
}

sealed interface HostedWidgetAddCompletionResult {
    data class Placed(
        val message: String?,
    ) : HostedWidgetAddCompletionResult

    data object Rejected : HostedWidgetAddCompletionResult
}

internal fun HostedWidgetAddCompletionResult.messageOrNull(): String? =
    when (this) {
        is HostedWidgetAddCompletionResult.Placed -> message
        HostedWidgetAddCompletionResult.Rejected -> null
    }

internal fun HostedWidgetAddCompletionResult.deleteHostedWidgetIdWhenRejected(
    action: HostedWidgetAddAction,
    deleteHostedWidgetId: (HostedWidgetId) -> Unit,
): HostedWidgetAddCompletionResult =
    also { result ->
        if (result is HostedWidgetAddCompletionResult.Rejected) {
            deleteHostedWidgetId(action.hostedWidgetId)
        }
    }

private fun HomeLayout.hostedWidgetSpanAdjustmentMessage(
    label: String,
    idealSpan: GridSpan,
    hostedWidgetId: HostedWidgetId,
): String? =
    pages
        .flatMap { page -> page.items }
        .filterIsInstance<WidgetItem>()
        .firstOrNull { widget -> widget.appWidgetId == hostedWidgetId }
        ?.placement
        ?.span
        ?.let { actualSpan ->
            widgetSpanAdjustmentToast(
                label = label,
                idealSpan = idealSpan,
                actualSpan = actualSpan,
            )
        }

private fun HomeLayout.pageHasHostedWidget(
    hostedWidgetId: HostedWidgetId,
    pageId: LauncherPageId?,
): Boolean =
    (pageId?.let { requestedPageId -> pages.firstOrNull { it.id == requestedPageId } } ?: selectedPage)
        .items
        .hasHostedWidget(hostedWidgetId)

private fun List<LauncherItem>.hasHostedWidget(hostedWidgetId: HostedWidgetId): Boolean =
    filterIsInstance<WidgetItem>()
        .any { widget -> widget.appWidgetId == hostedWidgetId }
