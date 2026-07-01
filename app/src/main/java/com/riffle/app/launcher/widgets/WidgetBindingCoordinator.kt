package com.riffle.app.launcher.widgets

import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

class WidgetBindingCoordinator(
    private val widgetHostGateway: WidgetHostGateway,
) {
    private var pendingBind: PendingWidgetBind? = null

    fun requestAddWidget(
        action: LauncherShellAction.RequestAddWidget,
        grid: GridDimensions,
        availableWidthDp: Int,
        availableHeightDp: Int,
    ): WidgetAddRequestResult {
        val hostedWidgetId = widgetHostGateway.allocateHostedWidgetId()
        val preferredSpan =
            action.dimensions.preferredGridSpan(
                grid = grid,
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            )

        return when (widgetHostGateway.bindHostedWidget(hostedWidgetId, action.provider)) {
            WidgetBindingResult.Bound ->
                WidgetAddRequestResult.Bound(
                    action.addHostedWidgetAction(
                        hostedWidgetId = hostedWidgetId,
                        preferredSpan = preferredSpan,
                    ),
                )

            WidgetBindingResult.RequiresPermission -> {
                pendingBind?.let { pending -> widgetHostGateway.deleteHostedWidgetId(pending.hostedWidgetId) }
                pendingBind =
                    PendingWidgetBind(
                        hostedWidgetId = hostedWidgetId,
                        label = action.label,
                        preferredSpan = preferredSpan,
                    )
                WidgetAddRequestResult.RequiresPermission(
                    hostedWidgetId = hostedWidgetId,
                    provider = action.provider,
                )
            }
        }
    }

    fun onPermissionResult(granted: Boolean): WidgetBindPermissionResult {
        val pending = pendingBind ?: return WidgetBindPermissionResult.Ignored
        pendingBind = null

        return when (granted) {
            true ->
                WidgetBindPermissionResult.Bound(
                    LauncherShellAction.AddHostedWidgetToHome(
                        hostedWidgetId = pending.hostedWidgetId,
                        label = pending.label,
                        preferredSpan = pending.preferredSpan,
                    ),
                )

            false -> {
                widgetHostGateway.deleteHostedWidgetId(pending.hostedWidgetId)
                WidgetBindPermissionResult.Cancelled
            }
        }
    }

    private fun LauncherShellAction.RequestAddWidget.addHostedWidgetAction(
        hostedWidgetId: HostedWidgetId,
        preferredSpan: GridSpan,
    ): LauncherShellAction.AddHostedWidgetToHome =
        LauncherShellAction.AddHostedWidgetToHome(
            hostedWidgetId = hostedWidgetId,
            label = label,
            preferredSpan = preferredSpan,
        )
}

sealed interface WidgetAddRequestResult {
    data class Bound(
        val action: LauncherShellAction.AddHostedWidgetToHome,
    ) : WidgetAddRequestResult

    data class RequiresPermission(
        val hostedWidgetId: HostedWidgetId,
        val provider: WidgetProviderIdentity,
    ) : WidgetAddRequestResult
}

sealed interface WidgetBindPermissionResult {
    data object Ignored : WidgetBindPermissionResult

    data object Cancelled : WidgetBindPermissionResult

    data class Bound(
        val action: LauncherShellAction.AddHostedWidgetToHome,
    ) : WidgetBindPermissionResult
}

private data class PendingWidgetBind(
    val hostedWidgetId: HostedWidgetId,
    val label: String,
    val preferredSpan: GridSpan,
)
