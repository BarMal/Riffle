package com.riffle.app.launcher

import com.riffle.app.launcher.LauncherShellAction.AddHostedWidgetToHome
import com.riffle.app.launcher.widgets.fitWidgetPreferredSpan
import com.riffle.app.launcher.widgets.widgetSpanAdjustmentToast
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun LauncherShellViewModel.completeWidgetAdd(action: AddHostedWidgetToHome): String? {
    val fittedAction =
        action.copy(
            preferredSpan =
                action.preferredSpan.fitWidgetPreferredSpan(
                    state.value.homeLayout.selectedPage.grid,
                ),
        )
    onHomeShortcutEdited(fittedAction)
    val adjustmentMessage =
        state.value.homeLayout.hostedWidgetSpanAdjustmentMessage(
            label = action.label,
            idealSpan = action.preferredSpan,
            hostedWidgetId = action.hostedWidgetId,
        )
    onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
    return adjustmentMessage
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
