package com.riffle.app.launcher

import com.riffle.app.launcher.widgets.WidgetAddRequestResult
import com.riffle.app.launcher.widgets.WidgetBindingCoordinator
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

class LauncherWidgetAddRequestHandler(
    private val widgetBindingCoordinator: WidgetBindingCoordinator,
    private val selectedGrid: () -> GridDimensions,
    private val windowSize: () -> LauncherWidgetAddWindowSize,
    private val completeWidgetAdd: (LauncherShellAction.AddHostedWidgetToHome) -> HostedWidgetAddCompletionResult,
    private val deleteHostedWidgetId: (HostedWidgetId) -> Unit,
) {
    fun handle(action: LauncherShellAction.RequestAddWidget): LauncherWidgetAddHandlingResult {
        val size = windowSize()
        return when (
            val requestResult =
                widgetBindingCoordinator.requestAddWidget(
                    action = action,
                    grid = selectedGrid(),
                    availableWidthDp = size.availableWidthDp,
                    availableHeightDp = size.availableHeightDp,
                )
        ) {
            is WidgetAddRequestResult.Bound ->
                completeWidgetAdd(requestResult.action)
                    .deleteHostedWidgetIdWhenRejected(requestResult.action, deleteHostedWidgetId)
                    .let { result ->
                        LauncherWidgetAddHandlingResult.Completed(
                            message = result.messageOrNull(),
                        )
                    }

            is WidgetAddRequestResult.RequiresPermission ->
                LauncherWidgetAddHandlingResult.RequiresPermission(
                    hostedWidgetId = requestResult.hostedWidgetId,
                    provider = requestResult.provider,
                )

            is WidgetAddRequestResult.RequiresConfiguration ->
                LauncherWidgetAddHandlingResult.RequiresConfiguration(
                    hostedWidgetId = requestResult.hostedWidgetId,
                )
        }
    }
}

data class LauncherWidgetAddWindowSize(
    val availableWidthDp: Int,
    val availableHeightDp: Int,
)

sealed interface LauncherWidgetAddHandlingResult {
    data class Completed(
        val message: String?,
    ) : LauncherWidgetAddHandlingResult

    data class RequiresPermission(
        val hostedWidgetId: HostedWidgetId,
        val provider: WidgetProviderIdentity,
    ) : LauncherWidgetAddHandlingResult

    data class RequiresConfiguration(
        val hostedWidgetId: HostedWidgetId,
    ) : LauncherWidgetAddHandlingResult
}
