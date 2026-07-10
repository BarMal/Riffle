package com.riffle.app.launcher.widgets

import com.riffle.app.launcher.HostedWidgetAddAction
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.WidgetAddTarget
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

class WidgetBindingCoordinator(
    private val widgetHostGateway: WidgetHostGateway,
) {
    private var pendingAdd: PendingWidgetAdd? = null

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
            WidgetBindingResult.Bound -> {
                pendingAdd?.let { pending -> widgetHostGateway.deleteHostedWidgetId(pending.hostedWidgetId) }
                val pending =
                    PendingWidgetAdd(
                        hostedWidgetId = hostedWidgetId,
                        label = action.label,
                        preferredSpan = preferredSpan,
                        target = action.target,
                        pendingStep = PendingWidgetAddStep.CONFIGURATION,
                    )
                when (widgetHostGateway.hostedWidgetRequiresConfiguration(hostedWidgetId)) {
                    true -> {
                        pendingAdd = pending
                        WidgetAddRequestResult.RequiresConfiguration(hostedWidgetId)
                    }

                    false -> {
                        pendingAdd = null
                        WidgetAddRequestResult.Bound(pending.addHostedWidgetAction())
                    }
                }
            }

            WidgetBindingResult.RequiresPermission -> {
                pendingAdd?.let { pending -> widgetHostGateway.deleteHostedWidgetId(pending.hostedWidgetId) }
                pendingAdd =
                    PendingWidgetAdd(
                        hostedWidgetId = hostedWidgetId,
                        label = action.label,
                        preferredSpan = preferredSpan,
                        target = action.target,
                        pendingStep = PendingWidgetAddStep.PERMISSION,
                    )
                WidgetAddRequestResult.RequiresPermission(
                    hostedWidgetId = hostedWidgetId,
                    provider = action.provider,
                )
            }
        }
    }

    fun onPermissionResult(granted: Boolean): WidgetBindPermissionResult =
        when (val pending = pendingAdd) {
            null -> WidgetBindPermissionResult.Ignored
            else ->
                when (pending.pendingStep) {
                    PendingWidgetAddStep.CONFIGURATION -> WidgetBindPermissionResult.Ignored
                    PendingWidgetAddStep.PERMISSION ->
                        when (granted) {
                            true -> grantedPermissionResult(pending)
                            false -> {
                                pendingAdd = null
                                widgetHostGateway.deleteHostedWidgetId(pending.hostedWidgetId)
                                WidgetBindPermissionResult.Cancelled
                            }
                        }
                }
        }

    fun onConfigurationResult(configured: Boolean): WidgetConfigurationResult =
        when (val pending = pendingAdd) {
            null -> WidgetConfigurationResult.Ignored
            else ->
                when (pending.pendingStep) {
                    PendingWidgetAddStep.PERMISSION -> WidgetConfigurationResult.Ignored
                    PendingWidgetAddStep.CONFIGURATION -> {
                        pendingAdd = null
                        when (configured) {
                            true -> WidgetConfigurationResult.Bound(pending.addHostedWidgetAction())
                            false -> {
                                widgetHostGateway.deleteHostedWidgetId(pending.hostedWidgetId)
                                WidgetConfigurationResult.Cancelled
                            }
                        }
                    }
                }
        }

    private fun grantedPermissionResult(pending: PendingWidgetAdd): WidgetBindPermissionResult =
        when (widgetHostGateway.hostedWidgetRequiresConfiguration(pending.hostedWidgetId)) {
            true -> {
                pendingAdd = pending.copy(pendingStep = PendingWidgetAddStep.CONFIGURATION)
                WidgetBindPermissionResult.RequiresConfiguration(pending.hostedWidgetId)
            }

            false -> {
                pendingAdd = null
                WidgetBindPermissionResult.Bound(pending.addHostedWidgetAction())
            }
        }

    private fun PendingWidgetAdd.addHostedWidgetAction(): HostedWidgetAddAction =
        when (target) {
            WidgetAddTarget.HOME ->
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = hostedWidgetId,
                    label = label,
                    preferredSpan = preferredSpan,
                )

            WidgetAddTarget.DOCK ->
                LauncherShellAction.AddHostedWidgetToDock(
                    hostedWidgetId = hostedWidgetId,
                    label = label,
                )
        }
}

sealed interface WidgetAddRequestResult {
    data class Bound(
        val action: HostedWidgetAddAction,
    ) : WidgetAddRequestResult

    data class RequiresPermission(
        val hostedWidgetId: HostedWidgetId,
        val provider: WidgetProviderIdentity,
    ) : WidgetAddRequestResult

    data class RequiresConfiguration(
        val hostedWidgetId: HostedWidgetId,
    ) : WidgetAddRequestResult
}

sealed interface WidgetBindPermissionResult {
    data object Ignored : WidgetBindPermissionResult

    data object Cancelled : WidgetBindPermissionResult

    data class RequiresConfiguration(
        val hostedWidgetId: HostedWidgetId,
    ) : WidgetBindPermissionResult

    data class Bound(
        val action: HostedWidgetAddAction,
    ) : WidgetBindPermissionResult
}

sealed interface WidgetConfigurationResult {
    data object Ignored : WidgetConfigurationResult

    data object Cancelled : WidgetConfigurationResult

    data class Bound(
        val action: HostedWidgetAddAction,
    ) : WidgetConfigurationResult
}

private data class PendingWidgetAdd(
    val hostedWidgetId: HostedWidgetId,
    val label: String,
    val preferredSpan: GridSpan,
    val target: WidgetAddTarget,
    val pendingStep: PendingWidgetAddStep,
)

private enum class PendingWidgetAddStep {
    PERMISSION,
    CONFIGURATION,
}
