package com.riffle.app.launcher

import com.riffle.app.launcher.widgets.fitWidgetPreferredSpan
import com.riffle.app.launcher.widgets.widgetSpanAdjustmentToast
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun LauncherShellViewModel.completeWidgetAdd(action: HostedWidgetAddAction): HostedWidgetAddCompletionResult =
    when (action) {
        is LauncherShellAction.AddHostedWidgetToHome -> completeHomeWidgetAdd(action)
        is LauncherShellAction.AddHostedWidgetToDock -> completeDockWidgetAdd(action)
    }

private fun LauncherShellViewModel.completeHomeWidgetAdd(
    action: LauncherShellAction.AddHostedWidgetToHome,
): HostedWidgetAddCompletionResult {
    val hadHostedWidgetBeforeAdd = state.value.homeLayout.hasHostedWidget(action.hostedWidgetId)
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
    val hadHostedWidgetBeforeAdd = state.value.homeLayout.hasHostedWidget(action.hostedWidgetId)
    onDockEdited(action)
    val wasPlaced =
        !hadHostedWidgetBeforeAdd &&
            state.value.homeLayout.dock.hasHostedWidget(action.hostedWidgetId)
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

private fun HomeLayout.hasHostedWidget(hostedWidgetId: HostedWidgetId): Boolean =
    (
        pages
            .flatMap { page -> page.items } + dock.items
    )
        .filterIsInstance<WidgetItem>()
        .any { widget -> widget.appWidgetId == hostedWidgetId }

private fun HomeLayout.pageHasHostedWidget(
    hostedWidgetId: HostedWidgetId,
    pageId: com.riffle.core.domain.launcher.home.LauncherPageId?,
): Boolean =
    (pageId?.let { requestedPageId -> pages.firstOrNull { it.id == requestedPageId } } ?: selectedPage).items
        .filterIsInstance<WidgetItem>()
        .any { widget -> widget.appWidgetId == hostedWidgetId }

private fun com.riffle.core.domain.launcher.home.DockModel.hasHostedWidget(hostedWidgetId: HostedWidgetId): Boolean =
    items
        .filterIsInstance<WidgetItem>()
        .any { widget -> widget.appWidgetId == hostedWidgetId }
