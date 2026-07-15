package com.riffle.app.launcher.widgets

import com.riffle.app.launcher.HostedWidgetAddAction
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.WidgetAddTarget
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

class WidgetBindingCoordinator(
    private val widgetHostGateway: WidgetHostGateway,
    private val transactionStore: WidgetAddTransactionStore = InMemoryWidgetAddTransactionStore(),
    private val epochMillisProvider: () -> Long = System::currentTimeMillis,
) {
    private var pendingAdd: PendingWidgetAddTransaction? = transactionStore.read()

    init {
        val restoredTransaction = pendingAdd
        if (
            restoredTransaction?.step == PendingWidgetAddStep.CONFIGURATION &&
            !widgetHostGateway.isHostedWidgetBoundTo(
                restoredTransaction.hostedWidgetId,
                restoredTransaction.provider,
            )
        ) {
            abandon(restoredTransaction)
        }
    }

    fun requestAddWidget(
        action: LauncherShellAction.RequestAddWidget,
        grid: GridDimensions,
        availableWidthDp: Int,
        availableHeightDp: Int,
    ): WidgetAddRequestResult {
        pendingAdd?.let(::abandon)
        val hostedWidgetId = widgetHostGateway.allocateHostedWidgetId()
        val preferredSpan =
            action.dimensions.preferredGridSpan(
                grid = grid,
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            )

        return when (widgetHostGateway.bindHostedWidget(hostedWidgetId, action.provider)) {
            WidgetBindingResult.Bound -> {
                val pending =
                    PendingWidgetAddTransaction(
                        hostedWidgetId = hostedWidgetId,
                        provider = action.provider,
                        label = action.label,
                        preferredSpan = preferredSpan,
                        target = action.target,
                        step = PendingWidgetAddStep.CONFIGURATION,
                        createdAtEpochMillis = epochMillisProvider(),
                    )
                when (widgetHostGateway.hostedWidgetRequiresConfiguration(hostedWidgetId)) {
                    true -> {
                        savePending(pending)
                        WidgetAddRequestResult.RequiresConfiguration(hostedWidgetId)
                    }

                    false -> {
                        if (widgetHostGateway.isHostedWidgetBoundTo(hostedWidgetId, action.provider)) {
                            clearPending()
                            WidgetAddRequestResult.Bound(pending.addHostedWidgetAction())
                        } else {
                            abandon(pending)
                            WidgetAddRequestResult.Cancelled
                        }
                    }
                }
            }

            WidgetBindingResult.RequiresPermission -> {
                savePending(
                    PendingWidgetAddTransaction(
                        hostedWidgetId = hostedWidgetId,
                        provider = action.provider,
                        label = action.label,
                        preferredSpan = preferredSpan,
                        target = action.target,
                        step = PendingWidgetAddStep.PERMISSION,
                        createdAtEpochMillis = epochMillisProvider(),
                    ),
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
                when (pending.step) {
                    PendingWidgetAddStep.CONFIGURATION -> WidgetBindPermissionResult.Ignored
                    PendingWidgetAddStep.PERMISSION ->
                        when (granted) {
                            true -> grantedPermissionResult(pending)
                            false -> {
                                abandon(pending)
                                WidgetBindPermissionResult.Cancelled
                            }
                        }
                }
        }

    fun onConfigurationResult(configured: Boolean): WidgetConfigurationResult =
        when (val pending = pendingAdd) {
            null -> WidgetConfigurationResult.Ignored
            else ->
                when (pending.step) {
                    PendingWidgetAddStep.PERMISSION -> WidgetConfigurationResult.Ignored
                    PendingWidgetAddStep.CONFIGURATION -> completeConfiguration(pending, configured)
                }
        }

    private fun completeConfiguration(
        pending: PendingWidgetAddTransaction,
        configured: Boolean,
    ): WidgetConfigurationResult =
        if (configured && widgetHostGateway.isHostedWidgetBoundTo(pending.hostedWidgetId, pending.provider)) {
            clearPending()
            WidgetConfigurationResult.Bound(pending.addHostedWidgetAction())
        } else {
            abandon(pending)
            WidgetConfigurationResult.Cancelled
        }

    private fun grantedPermissionResult(pending: PendingWidgetAddTransaction): WidgetBindPermissionResult =
        when (widgetHostGateway.isHostedWidgetBoundTo(pending.hostedWidgetId, pending.provider)) {
            false -> {
                abandon(pending)
                WidgetBindPermissionResult.Cancelled
            }

            true -> {
                when (widgetHostGateway.hostedWidgetRequiresConfiguration(pending.hostedWidgetId)) {
                    true -> {
                        savePending(pending.copy(step = PendingWidgetAddStep.CONFIGURATION))
                        WidgetBindPermissionResult.RequiresConfiguration(pending.hostedWidgetId)
                    }

                    false -> {
                        clearPending()
                        WidgetBindPermissionResult.Bound(pending.addHostedWidgetAction())
                    }
                }
            }
        }

    private fun savePending(transaction: PendingWidgetAddTransaction) {
        pendingAdd = transaction
        transactionStore.write(transaction)
    }

    private fun clearPending() {
        pendingAdd = null
        transactionStore.clear()
    }

    private fun abandon(transaction: PendingWidgetAddTransaction) {
        if (pendingAdd?.hostedWidgetId == transaction.hostedWidgetId) clearPending()
        widgetHostGateway.deleteHostedWidgetId(transaction.hostedWidgetId)
    }

    private fun PendingWidgetAddTransaction.addHostedWidgetAction(): HostedWidgetAddAction =
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
    data object Cancelled : WidgetAddRequestResult

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
