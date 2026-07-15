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
    private val hostedWidgetIdReferenceState: (HostedWidgetId) -> HostedWidgetIdReferenceState = {
        HostedWidgetIdReferenceState.Unknown
    },
    private val epochMillisProvider: () -> Long = System::currentTimeMillis,
) {
    private var pendingAdd: PendingWidgetAddTransaction? = transactionStore.read()

    init {
        val restoredTransaction = pendingAdd
        if (restoredTransaction == null) {
            transactionStore.discardInvalidTransaction()?.let { hostedWidgetId ->
                if (hostedWidgetIdReferenceState(hostedWidgetId) == HostedWidgetIdReferenceState.Unreferenced) {
                    widgetHostGateway.deleteHostedWidgetId(hostedWidgetId)
                }
            }
        } else if (
            restoredTransaction.step == PendingWidgetAddStep.CONFIGURATION &&
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
        if (pendingAdd != null) return WidgetAddRequestResult.AlreadyInProgress
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

    val pendingActivityResult: PendingWidgetActivityResult?
        get() = pendingAdd?.let { PendingWidgetActivityResult(it.hostedWidgetId, it.step) }

    fun onPermissionResult(
        hostedWidgetId: HostedWidgetId,
        granted: Boolean,
    ): WidgetBindPermissionResult {
        val pending = pendingAdd
        if (pending?.step != PendingWidgetAddStep.PERMISSION || pending.hostedWidgetId != hostedWidgetId) {
            return WidgetBindPermissionResult.Ignored
        }
        return if (granted) {
            grantedPermissionResult(pending)
        } else {
            abandon(pending)
            WidgetBindPermissionResult.Cancelled
        }
    }

    fun onConfigurationResult(
        hostedWidgetId: HostedWidgetId,
        configured: Boolean,
    ): WidgetConfigurationResult =
        when (val pending = pendingAdd) {
            null -> WidgetConfigurationResult.Ignored
            else ->
                when (pending.step) {
                    PendingWidgetAddStep.PERMISSION -> WidgetConfigurationResult.Ignored
                    PendingWidgetAddStep.CONFIGURATION ->
                        if (pending.hostedWidgetId == hostedWidgetId) {
                            completeConfiguration(pending, configured)
                        } else {
                            WidgetConfigurationResult.Ignored
                        }
                }
        }

    /**
     * Resolves work restored after Riffle returns without the system activity result.
     *
     * A bind permission request cannot safely be resumed, so its host ID is released. A valid
     * provider configuration can be restarted because its provider binding is already known.
     */
    fun recoverPendingAdd(): WidgetAddRecoveryResult =
        when (val pending = pendingAdd) {
            null -> WidgetAddRecoveryResult.None
            else ->
                when (pending.step) {
                    PendingWidgetAddStep.PERMISSION -> {
                        abandon(pending)
                        WidgetAddRecoveryResult.Cancelled
                    }

                    PendingWidgetAddStep.CONFIGURATION ->
                        if (widgetHostGateway.isHostedWidgetBoundTo(pending.hostedWidgetId, pending.provider)) {
                            WidgetAddRecoveryResult.ResumeConfiguration(pending.hostedWidgetId)
                        } else {
                            abandon(pending)
                            WidgetAddRecoveryResult.Cancelled
                        }
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

enum class HostedWidgetIdReferenceState {
    Referenced,
    Unreferenced,
    Unknown,
}

data class PendingWidgetActivityResult(
    val hostedWidgetId: HostedWidgetId,
    val step: PendingWidgetAddStep,
)

sealed interface WidgetAddRequestResult {
    data object AlreadyInProgress : WidgetAddRequestResult

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

sealed interface WidgetAddRecoveryResult {
    data object None : WidgetAddRecoveryResult

    data object Cancelled : WidgetAddRecoveryResult

    data class ResumeConfiguration(
        val hostedWidgetId: HostedWidgetId,
    ) : WidgetAddRecoveryResult
}
