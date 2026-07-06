package com.riffle.app.launcher

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
        val completedActions = mutableListOf<LauncherShellAction.AddHostedWidgetToHome>()
        val handler =
            LauncherWidgetAddRequestHandler(
                widgetBindingCoordinator = WidgetBindingCoordinator(FakeWidgetHostGateway()),
                selectedGrid = { GridDimensions(columns = 4, rows = 5) },
                windowSize = { LauncherWidgetAddWindowSize(availableWidthDp = 400, availableHeightDp = 1000) },
                completeWidgetAdd = { action ->
                    completedActions += action
                    "Weather ideal size is 2x1; added as 1x1"
                },
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
    fun completedBindAfterPendingPermissionCompletesOnlyReplacementAndDeletesPendingWidgetId() {
        val completedActions = mutableListOf<LauncherShellAction.AddHostedWidgetToHome>()
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
                    "${action.label} added"
                },
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
        assertEquals(LauncherWidgetAddHandlingResult.Completed("Weather added"), replacementResult)
        assertEquals(listOf(HostedWidgetId(1)), gateway.deletedHostedWidgetIds)
        assertEquals(
            listOf(
                LauncherShellAction.AddHostedWidgetToHome(
                    hostedWidgetId = HostedWidgetId(2),
                    label = "Weather",
                    preferredSpan = GridSpan(columns = 2, rows = 1),
                ),
            ),
            completedActions,
        )
    }

    private class FakeWidgetHostGateway(
        bindingResult: WidgetBindingResult = WidgetBindingResult.Bound,
        private val bindingResults: List<WidgetBindingResult> = listOf(bindingResult),
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

        fun requestAddWidget(label: String): LauncherShellAction.RequestAddWidget =
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
