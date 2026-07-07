package com.riffle.app.launcher.widgets

import android.content.Intent
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun permissionSuccessReturnsPendingAddHostedWidgetAction() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val coordinator = WidgetBindingCoordinator(gateway)
        coordinator.requestAddWidget(
            action = requestAddWidget(label = "Calendar"),
            grid = GridDimensions(columns = 4, rows = 5),
            availableWidthDp = 400,
            availableHeightDp = 1000,
        )

        val result = coordinator.onPermissionResult(granted = true)

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
            coordinator.onConfigurationResult(configured = true),
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
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
            coordinator.onPermissionResult(granted = true),
        )
        assertEquals(
            WidgetConfigurationResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Calendar",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            coordinator.onConfigurationResult(configured = true),
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
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

        val result = coordinator.onConfigurationResult(configured = false)

        assertEquals(WidgetConfigurationResult.Cancelled, result)
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun replacementConfigurationRequestDeletesPreviousPendingHostedWidgetId() {
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

        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
        assertEquals(WidgetAddRequestResult.RequiresConfiguration(HostedWidgetId(2)), result)
        assertEquals(
            WidgetConfigurationResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(2),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            coordinator.onConfigurationResult(configured = true),
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

        val result = coordinator.onPermissionResult(granted = false)

        assertEquals(WidgetBindPermissionResult.Cancelled, result)
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun replacementPermissionRequestDeletesPreviousPendingHostedWidgetId() {
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

        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
        assertEquals(
            WidgetAddRequestResult.RequiresPermission(
                hostedWidgetId = HostedWidgetId(2),
                provider = providerIdentity,
            ),
            result,
        )
        assertEquals(
            WidgetBindPermissionResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(2),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            coordinator.onPermissionResult(granted = true),
        )
    }

    @Test
    fun immediateBindAfterPendingPermissionDeletesPendingHostedWidgetIdAndIgnoresLaterPermissionResult() {
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

        assertEquals(listOf(HostedWidgetId(1), HostedWidgetId(2)), gateway.boundHostedWidgetIds)
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
        assertEquals(
            WidgetAddRequestResult.Bound(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(2),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            result,
        )
        assertEquals(WidgetBindPermissionResult.Ignored, coordinator.onPermissionResult(granted = true))
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun permissionResultWithoutPendingRequestIsIgnored() {
        val coordinator = WidgetBindingCoordinator(FakeWidgetHostGateway())

        assertEquals(WidgetBindPermissionResult.Ignored, coordinator.onPermissionResult(granted = true))
    }

    @Test
    fun configurationResultWithoutPendingRequestIsIgnored() {
        val coordinator = WidgetBindingCoordinator(FakeWidgetHostGateway())

        assertEquals(WidgetConfigurationResult.Ignored, coordinator.onConfigurationResult(configured = true))
    }

    private class FakeWidgetHostGateway(
        var bindingResult: WidgetBindingResult = WidgetBindingResult.Bound,
        private val configuredWidgetIds: Set<HostedWidgetId> = emptySet(),
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

        override fun createConfigureHostedWidgetIntent(hostedWidgetId: HostedWidgetId): Intent {
            error("Intent creation stays in MainActivity")
        }

        override fun deleteHostedWidgetId(hostedWidgetId: HostedWidgetId) {
            deletedHostedWidgetIds += hostedWidgetId
        }
    }

    private companion object {
        val providerIdentity =
            WidgetProviderIdentity(
                packageName = AppPackageName("com.example.widgets"),
                className = WidgetProviderClassName(".ExampleWidget"),
            )

        fun requestAddWidget(label: String) =
            LauncherShellAction.RequestAddWidget(
                provider = providerIdentity,
                label = label,
                dimensions =
                    WidgetProviderDimensions(
                        minWidthDp = 200,
                        minHeightDp = 100,
                    ),
            )
    }
}
