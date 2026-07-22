package com.riffle.app.launcher.widgets

import android.content.Intent
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.WidgetAddTarget
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("LargeClass")
class WidgetBindingCoordinatorTest {
    @Test
    fun boundWidgetReturnsAddHostedWidgetAction() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.Bound)
        val coordinator = WidgetBindingCoordinator(gateway)

        val result =
            coordinator.requestAddWidget(
                action = requestAddWidget(label = "Weather"),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(
            WidgetAddRequestResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            result,
        )
        assertEquals(listOf(HostedWidgetId(1)), gateway.boundHostedWidgetIds)
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun boundWidgetPreservesDroppedHomeCell() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.Bound)
        val coordinator = WidgetBindingCoordinator(gateway)
        val pageId = LauncherPageId("page-2")
        val cell = GridCell(column = 2, row = 3)

        val result =
            coordinator.requestAddWidget(
                action =
                    requestAddWidget(label = "Weather").copy(
                        targetPageId = pageId,
                        targetCell = cell,
                    ),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(
            WidgetAddRequestResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                    targetPageId = pageId,
                    targetCell = cell,
                ),
            ),
            result,
        )
    }

    @Test
    fun boundHomeWidgetCarriesMinimumResizeSpanToPlacement() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.Bound)
        val coordinator = WidgetBindingCoordinator(gateway)

        val result =
            coordinator.requestAddWidget(
                action =
                    LauncherShellAction.RequestAddWidget(
                        provider = providerIdentity,
                        label = "Weather",
                        dimensions =
                            WidgetProviderDimensions(
                                minWidthDp = 100,
                                minHeightDp = 100,
                                minResizeWidthDp = 200,
                            ),
                        supportsHorizontalResize = true,
                        supportsVerticalResize = false,
                    ),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(
            WidgetAddRequestResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 1, rows = 1),
                    resizeConstraints =
                        WidgetResizeConstraints(
                            minSpan = GridSpan(columns = 2, rows = 1),
                            maxSpan = GridSpan(columns = 4, rows = 1),
                            supportsHorizontalResize = true,
                            supportsVerticalResize = false,
                        ),
                ),
            ),
            result,
        )
    }

    @Test
    fun boundDockWidgetReturnsAddHostedWidgetToDockAction() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.Bound)
        val coordinator = WidgetBindingCoordinator(gateway)

        val result =
            coordinator.requestAddWidget(
                action = requestAddWidget(label = "Weather", target = WidgetAddTarget.DOCK),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(
            WidgetAddRequestResult.Bound(
                LauncherShellAction.AddHostedWidgetToDock(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                ),
            ),
            result,
        )
    }

    @Test
    fun permissionSuccessReturnsPendingAddHostedWidgetAction() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result = coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = true)

        assertEquals(
            WidgetBindPermissionResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Calendar",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            result,
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun permissionSuccessPreservesDockTarget() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar", target = WidgetAddTarget.DOCK),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result = coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = true)

        assertEquals(
            WidgetBindPermissionResult.Bound(
                LauncherShellAction.AddHostedWidgetToDock(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Calendar",
                ),
            ),
            result,
        )
    }

    @Test
    fun boundWidgetRequiringConfigurationWaitsForConfigurationResult() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val coordinator = WidgetBindingCoordinator(gateway)

        val result =
            coordinator.requestAddWidget(
                action = requestAddWidget(label = "Weather"),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(WidgetAddRequestResult.RequiresConfiguration(HostedWidgetId(1)), result)
        assertEquals(
            WidgetConfigurationResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = true),
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun boundWidgetRequiringConfigurationPreservesDockTarget() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val coordinator = WidgetBindingCoordinator(gateway)

        val result =
            coordinator.requestAddWidget(
                action = requestAddWidget(label = "Weather", target = WidgetAddTarget.DOCK),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(WidgetAddRequestResult.RequiresConfiguration(HostedWidgetId(1)), result)
        assertEquals(
            WidgetConfigurationResult.Bound(
                LauncherShellAction.AddHostedWidgetToDock(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                ),
            ),
            coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = true),
        )
    }

    @Test
    fun permissionSuccessForWidgetRequiringConfigurationWaitsForConfigurationResult() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.RequiresPermission,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        assertEquals(
            WidgetBindPermissionResult.RequiresConfiguration(HostedWidgetId(1)),
            coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = true),
        )
        assertEquals(
            WidgetConfigurationResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Calendar",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = true),
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun permissionThenConfigurationPreservesDockTarget() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.RequiresPermission,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar", target = WidgetAddTarget.DOCK),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        assertEquals(
            WidgetBindPermissionResult.RequiresConfiguration(HostedWidgetId(1)),
            coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = true),
        )
        assertEquals(
            WidgetConfigurationResult.Bound(
                LauncherShellAction.AddHostedWidgetToDock(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Calendar",
                ),
            ),
            coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = true),
        )
    }

    @Test
    fun configurationCancellationDeletesPendingHostedWidgetId() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Clock"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result = coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = false)

        assertEquals(WidgetConfigurationResult.Cancelled, result)
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun permissionResultIsIgnoredWhenConfigurationIsAlreadyPending() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Clock"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result = coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = false)

        assertEquals(WidgetBindPermissionResult.Ignored, result)
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun configurationRequestCannotBeReplacedWhilePlatformResultIsPending() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1), HostedWidgetId(2)),
            )
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result =
            coordinator.requestAddWidget(
                action = requestAddWidget(label = "Weather"),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
        assertEquals(WidgetAddRequestResult.AlreadyInProgress, result)
        assertEquals(
            WidgetConfigurationResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Calendar",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = true),
        )
    }

    @Test
    fun permissionCancellationDeletesPendingHostedWidgetId() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result = coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = false)

        assertEquals(WidgetBindPermissionResult.Cancelled, result)
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun configurationResultIsIgnoredWhilePermissionIsStillPending() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result = coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = false)

        assertEquals(WidgetConfigurationResult.Ignored, result)
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun permissionRequestCannotBeReplacedWhilePlatformResultIsPending() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result =
            coordinator.requestAddWidget(
                action = requestAddWidget(label = "Weather"),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
        assertEquals(WidgetAddRequestResult.AlreadyInProgress, result)
        assertEquals(
            WidgetBindPermissionResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Calendar",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = true),
        )
    }

    @Test
    fun immediateBindCannotSupersedePendingPermissionResult() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )
        gateway.bindingResult = WidgetBindingResult.Bound

        val result =
            coordinator.requestAddWidget(
                action = requestAddWidget(label = "Weather"),
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 1000,
            )

        assertEquals(listOf(HostedWidgetId(1)), gateway.boundHostedWidgetIds)
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
        assertEquals(WidgetAddRequestResult.AlreadyInProgress, result)
        assertEquals(
            WidgetBindPermissionResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Calendar",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = true),
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun permissionResultWithoutPendingRequestIsIgnored() {
        val coordinator = WidgetBindingCoordinator(FakeWidgetHostGateway())

        assertEquals(
            WidgetBindPermissionResult.Ignored,
            coordinator.onPermissionResult(hostedWidgetId = HostedWidgetId(1), granted = true),
        )
    }

    @Test
    fun configurationResultWithoutPendingRequestIsIgnored() {
        val coordinator = WidgetBindingCoordinator(FakeWidgetHostGateway())

        assertEquals(
            WidgetConfigurationResult.Ignored,
            coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = true),
        )
    }

    @Test
    fun configurationSuccessDeletesWidgetWhenHostedIdIsNoLongerBoundToSelectedProvider() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
                isBoundToSelectedProvider = false,
            )
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Weather"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        assertEquals(
            WidgetConfigurationResult.Cancelled,
            coordinator.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = true),
        )
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun freshProcessRestoresConfigurationTransactionWithoutAllocatingAnotherHostedId() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val persisted = PersistedTransactionValue()
        WidgetBindingCoordinator(
            gateway,
            transactionStore = SerializedWidgetAddTransactionStore(persisted),
        ).requestAddWidget(
            action = requestAddWidget(label = "Weather"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val restored =
            WidgetBindingCoordinator(
                gateway,
                transactionStore = SerializedWidgetAddTransactionStore(persisted),
            )

        assertEquals(
            PendingWidgetActivityResult(HostedWidgetId(1), PendingWidgetAddStep.CONFIGURATION),
            restored.pendingActivityResult,
        )
        assertEquals(
            WidgetConfigurationResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            restored.onConfigurationResult(hostedWidgetId = HostedWidgetId(1), configured = true),
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
        assertEquals(null, persisted.value)
    }

    @Test
    fun freshProcessHomeReturnResumesConfigurationWhenNoResultCallbackArrives() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val persisted = PersistedTransactionValue()
        WidgetBindingCoordinator(
            gateway,
            transactionStore = SerializedWidgetAddTransactionStore(persisted),
        ).requestAddWidget(
            action = requestAddWidget(label = "Weather"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val restored =
            WidgetBindingCoordinator(
                gateway,
                transactionStore = SerializedWidgetAddTransactionStore(persisted),
            )

        assertEquals(
            WidgetAddRecoveryResult.ResumeConfiguration(HostedWidgetId(1)),
            restored.recoverPendingAdd(),
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun freshProcessHomeReturnCleansUpPendingBindWhenNoResultCallbackArrives() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val persisted = PersistedTransactionValue()
        WidgetBindingCoordinator(
            gateway,
            transactionStore = SerializedWidgetAddTransactionStore(persisted),
        ).requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val restored =
            WidgetBindingCoordinator(
                gateway,
                transactionStore = SerializedWidgetAddTransactionStore(persisted),
            )

        assertEquals(WidgetAddRecoveryResult.Cancelled, restored.recoverPendingAdd())
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
        assertEquals(null, persisted.value)
    }

    @Test
    fun freshProcessCleansUpConfigurationTransactionWhoseProviderBindingDisappeared() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val persisted = PersistedTransactionValue()
        WidgetBindingCoordinator(
            gateway,
            transactionStore = SerializedWidgetAddTransactionStore(persisted),
        ).requestAddWidget(
            action = requestAddWidget(label = "Weather"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )
        gateway.isBoundToSelectedProvider = false

        WidgetBindingCoordinator(gateway, transactionStore = SerializedWidgetAddTransactionStore(persisted))

        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
        assertEquals(null, persisted.value)
    }

    @Test
    fun freshProcessCleansUpCorruptTransactionWithRecoverableHostedIdExactlyOnce() {
        val gateway = FakeWidgetHostGateway()
        val persisted =
            PersistedTransactionValue(
                value = "{\"version\":1,\"hostedWidgetId\":42}",
            )

        WidgetBindingCoordinator(
            gateway,
            transactionStore = SerializedWidgetAddTransactionStore(persisted),
            hostedWidgetIdReferenceState = { HostedWidgetIdReferenceState.Unreferenced },
        )
        WidgetBindingCoordinator(
            gateway,
            transactionStore = SerializedWidgetAddTransactionStore(persisted),
            hostedWidgetIdReferenceState = { HostedWidgetIdReferenceState.Unreferenced },
        )

        assertEquals(listOf(HostedWidgetId(42)), gateway.deletedHostedWidgetIds)
        assertEquals(null, persisted.value)
    }

    @Test
    fun corruptTransactionCannotDeleteHostedIdReferencedByPersistedLayout() {
        val gateway = FakeWidgetHostGateway()
        val persisted = PersistedTransactionValue(value = "{\"version\":1,\"hostedWidgetId\":42}")

        WidgetBindingCoordinator(
            gateway,
            transactionStore = SerializedWidgetAddTransactionStore(persisted),
            hostedWidgetIdReferenceState = { HostedWidgetIdReferenceState.Referenced },
        )

        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
        assertEquals(null, persisted.value)
    }

    @Test
    fun corruptTransactionRetainsHostedIdWhenLayoutReferencesAreUnknown() {
        val gateway = FakeWidgetHostGateway()
        val persisted = PersistedTransactionValue(value = "{\"version\":1,\"hostedWidgetId\":42}")

        WidgetBindingCoordinator(
            gateway,
            transactionStore = SerializedWidgetAddTransactionStore(persisted),
        )

        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
        assertEquals(null, persisted.value)
    }

    private class FakeWidgetHostGateway(
        var bindingResult: WidgetBindingResult = WidgetBindingResult.Bound,
        private val configuredWidgetIds: Set<HostedWidgetId> = emptySet(),
        var isBoundToSelectedProvider: Boolean = true,
    ) : WidgetHostGateway {
        private var nextHostedWidgetId = 1
        val boundHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()

        override fun allocateHostedWidgetId(): HostedWidgetId = HostedWidgetId(nextHostedWidgetId++)

        override fun bindHostedWidget(
            hostedWidgetId: HostedWidgetId,
            provider: WidgetProviderIdentity,
        ): WidgetBindingResult {
            boundHostedWidgetIds += hostedWidgetId
            assertEquals(providerIdentity, provider)
            return bindingResult
        }

        override fun createBindHostedWidgetIntent(
            hostedWidgetId: HostedWidgetId,
            provider: WidgetProviderIdentity,
        ) = error("Intent creation stays in MainActivity")

        override fun hostedWidgetRequiresConfiguration(hostedWidgetId: HostedWidgetId): Boolean {
            return hostedWidgetId in configuredWidgetIds
        }

        override fun isHostedWidgetBoundTo(
            hostedWidgetId: HostedWidgetId,
            provider: WidgetProviderIdentity,
        ): Boolean = isBoundToSelectedProvider

        override fun createConfigureHostedWidgetIntent(hostedWidgetId: HostedWidgetId): Intent {
            error("Intent creation stays in MainActivity")
        }

        override fun deleteHostedWidgetId(hostedWidgetId: HostedWidgetId) {
            deletedHostedWidgetIds += hostedWidgetId
        }
    }

    private class PersistedTransactionValue(
        var value: String? = null,
    )

    private class SerializedWidgetAddTransactionStore(
        private val persisted: PersistedTransactionValue,
    ) : WidgetAddTransactionStore {
        override fun read(): PendingWidgetAddTransaction? = persisted.value?.let(::decodeWidgetAddTransaction)

        override fun discardInvalidTransaction(): HostedWidgetId? =
            persisted.value
                ?.takeIf { decodeWidgetAddTransaction(it) == null }
                ?.let { value ->
                    persisted.value = null
                    decodeInvalidWidgetAddTransactionHostedId(value)
                }

        override fun write(transaction: PendingWidgetAddTransaction) {
            persisted.value = encodeWidgetAddTransaction(transaction)
        }

        override fun clear() {
            persisted.value = null
        }
    }

    private companion object {
        val providerIdentity =
            WidgetProviderIdentity(
                packageName = AppPackageName("com.example.widgets"),
                className = WidgetProviderClassName(".ExampleWidget"),
            )

        fun requestAddWidget(
            label: String,
            target: WidgetAddTarget = WidgetAddTarget.HOME,
        ) = LauncherShellAction.RequestAddWidget(
            provider = providerIdentity,
            label = label,
            dimensions =
                WidgetProviderDimensions(
                    minWidthDp = 200,
                    minHeightDp = 100,
                ),
            target = target,
        )
    }
}
