package com.riffle.app.launcher

import com.riffle.app.launcher.LauncherShellAction.AddHostedWidgetToHome
import com.riffle.app.launcher.widgets.fitWidgetPreferredSpan
import com.riffle.app.launcher.widgets.widgetSpanAdjustmentToast
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun LauncherShellViewModel.completeWidgetAdd(action: AddHostedWidgetToHome): HostedWidgetAddCompletionResult {
    val fittedAction =
        action.copy(
            preferredSpan =
                action.preferredSpan.fitWidgetPreferredSpan(
                    state.value.homeLayout.selectedPage.grid,
                ),
        )
    onHomeShortcutEdited(fittedAction)
    val wasPlaced = state.value.homeLayout.hasHostedWidget(action.hostedWidgetId)
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
    action: AddHostedWidgetToHome,
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
    pages
        .flatMap { page -> page.items }
        .filterIsInstance<WidgetItem>()
        .any { widget -> widget.appWidgetId == hostedWidgetId }
