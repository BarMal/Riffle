package com.riffle.app.launcher

import android.content.Intent
import com.riffle.app.launcher.widgets.WidgetBindingCoordinator
import com.riffle.app.launcher.widgets.WidgetBindingResult
import com.riffle.app.launcher.widgets.WidgetHostGateway
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherWidgetAddRequestHandlerTest {
    @Test
    fun completesBoundWidgetRequest() {
        val completedActions = mutableListOf<HostedWidgetAddAction>()
        val handler =
            LauncherWidgetAddRequestHandler(
                widgetBindingCoordinator = WidgetBindingCoordinator(FakeWidgetHostGateway()),
                selectedGrid = { GridDimensions(columns = 4, rows = 5) },
                windowSize = { LauncherWidgetAddWindowSize(availableWidthDp = 400, availableHeightDp = 1000) },
                completeWidgetAdd = { action ->
                    completedActions += action
                    HostedWidgetAddCompletionResult.Placed("Weather ideal size is 2x1; added as 1x1")
                },
                deleteHostedWidgetId = gatewayDeleteShouldNotRun,
            )

        val result = handler.handle(requestAddWidget(label = "Weather"))

        assertEquals(
            LauncherWidgetAddHandlingResult.Completed("Weather ideal size is 2x1; added as 1x1"),
            result,
        )
        assertEquals(
            listOf(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            completedActions,
        )
    }

    @Test
    fun returnsPermissionRequestWhenBindingRequiresPermission() {
        val gateway = FakeWidgetHostGateway(bindingResult = WidgetBindingResult.RequiresPermission)
        val handler =
            LauncherWidgetAddRequestHandler(
                widgetBindingCoordinator = WidgetBindingCoordinator(gateway),
                selectedGrid = { GridDimensions(columns = 4, rows = 5) },
                windowSize = { LauncherWidgetAddWindowSize(availableWidthDp = 400, availableHeightDp = 1000) },
                completeWidgetAdd = { error("Widget completion waits for permission result") },
                deleteHostedWidgetId = gateway::deleteHostedWidgetId,
            )

        val result = handler.handle(requestAddWidget(label = "Calendar"))

        assertEquals(
            LauncherWidgetAddHandlingResult.RequiresPermission(
                hostedWidgetId = HostedWidgetId(1),
                provider = providerIdentity,
            ),
            result,
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun returnsConfigurationRequestWhenBoundWidgetRequiresConfiguration() {
        val gateway =
            FakeWidgetHostGateway(
                bindingResult = WidgetBindingResult.Bound,
                configuredWidgetIds = setOf(HostedWidgetId(1)),
            )
        val handler =
            LauncherWidgetAddRequestHandler(
                widgetBindingCoordinator = WidgetBindingCoordinator(gateway),
                selectedGrid = { GridDimensions(columns = 4, rows = 5) },
                windowSize = { LauncherWidgetAddWindowSize(availableWidthDp = 400, availableHeightDp = 1000) },
                completeWidgetAdd = { error("Widget completion waits for configuration result") },
                deleteHostedWidgetId = gateway::deleteHostedWidgetId,
            )

        val result = handler.handle(requestAddWidget(label = "Weather"))

        assertEquals(
            LauncherWidgetAddHandlingResult.RequiresConfiguration(HostedWidgetId(1)),
            result,
        )
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
    }

    @Test
    fun completesBoundDockWidgetRequest() {
        val completedActions = mutableListOf<HostedWidgetAddAction>()
        val handler =
            LauncherWidgetAddRequestHandler(
                widgetBindingCoordinator = WidgetBindingCoordinator(FakeWidgetHostGateway()),
                selectedGrid = { GridDimensions(columns = 4, rows = 5) },
                windowSize = { LauncherWidgetAddWindowSize(availableWidthDp = 400, availableHeightDp = 1000) },
                completeWidgetAdd = { action ->
                    completedActions += action
                    HostedWidgetAddCompletionResult.Placed(null)
                },
                deleteHostedWidgetId = gatewayDeleteShouldNotRun,
            )

        val result = handler.handle(requestAddWidget(label = "Weather", target = WidgetAddTarget.DOCK))

        assertEquals(LauncherWidgetAddHandlingResult.Completed(message = null), result)
        assertEquals(
            listOf(
                LauncherShellAction.AddHostedWidgetToDock(
                    hostedWidgetId = HostedWidgetId(1),
                    label = "Weather",
                ),
            ),
            completedActions,
        )
    }

    @Test
    fun addRequestIsRejectedWhilePlatformPermissionResultIsPending() {
        val completedActions = mutableListOf<HostedWidgetAddAction>()
        val gateway =
            FakeWidgetHostGateway(
                bindingResults =
                    listOf(
                        WidgetBindingResult.RequiresPermission,
                        WidgetBindingResult.Bound,
                    ),
            )
        val handler =
            LauncherWidgetAddRequestHandler(
                widgetBindingCoordinator = WidgetBindingCoordinator(gateway),
                selectedGrid = { GridDimensions(columns = 4, rows = 5) },
                windowSize = { LauncherWidgetAddWindowSize(availableWidthDp = 400, availableHeightDp = 1000) },
                completeWidgetAdd = { action ->
                    completedActions += action
                    HostedWidgetAddCompletionResult.Placed(
                        "${(action as LauncherShellAction.AddHostedWidgetToHome).label} added",
                    )
                },
                deleteHostedWidgetId = gateway::deleteHostedWidgetId,
            )

        val pendingResult = handler.handle(requestAddWidget(label = "Calendar"))
        val replacementResult = handler.handle(requestAddWidget(label = "Weather"))

        assertEquals(
            LauncherWidgetAddHandlingResult.RequiresPermission(
                hostedWidgetId = HostedWidgetId(1),
                provider = providerIdentity,
            ),
            pendingResult,
        )
        assertEquals(LauncherWidgetAddHandlingResult.Cancelled, replacementResult)
        assertEquals(emptyList<HostedWidgetId>(), gateway.deletedHostedWidgetIds)
        assertEquals(emptyList<HostedWidgetAddAction>(), completedActions)
    }

    @Test
    fun deletesBoundWidgetIdWhenImmediatePlacementIsRejected() {
        val gateway = FakeWidgetHostGateway()
        val handler =
            LauncherWidgetAddRequestHandler(
                widgetBindingCoordinator = WidgetBindingCoordinator(gateway),
                selectedGrid = { GridDimensions(columns = 4, rows = 5) },
                windowSize = { LauncherWidgetAddWindowSize(availableWidthDp = 400, availableHeightDp = 1000) },
                completeWidgetAdd = { HostedWidgetAddCompletionResult.Rejected },
                deleteHostedWidgetId = gateway::deleteHostedWidgetId,
            )

        val result = handler.handle(requestAddWidget(label = "Weather"))

        assertEquals(LauncherWidgetAddHandlingResult.Completed(message = null), result)
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
    }

    private class FakeWidgetHostGateway(
        bindingResult: WidgetBindingResult = WidgetBindingResult.Bound,
        private val bindingResults: List<WidgetBindingResult> = listOf(bindingResult),
        private val configuredWidgetIds: Set<HostedWidgetId> = emptySet(),
    ) : WidgetHostGateway {
        private var nextHostedWidgetId = 1
        private var nextBindingResultIndex = 0
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()

        override fun allocateHostedWidgetId(): HostedWidgetId = HostedWidgetId(nextHostedWidgetId++)

        override fun bindHostedWidget(
            hostedWidgetId: HostedWidgetId,
            provider: WidgetProviderIdentity,
        ): WidgetBindingResult {
            assertEquals(providerIdentity, provider)
            val result = bindingResults.getOrElse(nextBindingResultIndex) { bindingResults.last() }
            nextBindingResultIndex += 1
            return result
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

        fun requestAddWidget(
            label: String,
            target: WidgetAddTarget = WidgetAddTarget.HOME,
        ): LauncherShellAction.RequestAddWidget =
            LauncherShellAction.RequestAddWidget(
                provider = providerIdentity,
                label = label,
                dimensions =
                    WidgetProviderDimensions(
                        minWidthDp = 200,
                        minHeightDp = 100,
                    ),
                target = target,
            )

        val gatewayDeleteShouldNotRun: (HostedWidgetId) -> Unit = {
            error("Placed widgets should not be deleted")
        }
    }
}
