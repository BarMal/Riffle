package com.riffle.app.launcher

import com.riffle.app.launcher.widgets.WidgetAddRequestResult
import com.riffle.app.launcher.widgets.WidgetBindingCoordinator
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

class LauncherWidgetAddRequestHandler(
    private val widgetBindingCoordinator: WidgetBindingCoordinator,
    private val selectedGrid: () -> GridDimensions,
    private val windowSize: () -> LauncherWidgetAddWindowSize,
    private val completeWidgetAdd: (HostedWidgetAddAction) -> HostedWidgetAddCompletionResult,
    private val deleteHostedWidgetId: (HostedWidgetId) -> Unit,
    private val completeGlanceAdd: (LauncherShellAction.AddGlanceWidgetToHome) -> HostedWidgetAddCompletionResult =
        { HostedWidgetAddCompletionResult.Rejected },
) {
    fun handle(action: LauncherShellAction.RequestAddWidget): LauncherWidgetAddHandlingResult {
        GlanceWidgets.kindOf(action.provider)?.let { kind -> return handleGlance(kind, action) }
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
            WidgetAddRequestResult.AlreadyInProgress,
            WidgetAddRequestResult.Cancelled,
            -> LauncherWidgetAddHandlingResult.Cancelled

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

    /**
     * A glance widget is Riffle's own: nothing is allocated, bound or configured, and it goes to a Home page only
     * (the Dock and its panel hold platform widgets), so a request for them is answered, not dropped.
     */
    private fun handleGlance(
        kind: GlanceWidgetKind,
        action: LauncherShellAction.RequestAddWidget,
    ): LauncherWidgetAddHandlingResult =
        if (action.target != WidgetAddTarget.HOME) {
            LauncherWidgetAddHandlingResult.Completed(message = GLANCE_HOME_ONLY_MESSAGE)
        } else {
            completeGlanceAdd(
                LauncherShellAction.AddGlanceWidgetToHome(
                    kind = kind,
                    label = action.label,
                    preferredSpan = kind.defaultSpan,
                    targetCell = action.targetCell,
                    targetPageId = action.targetPageId,
                ),
            ).let { result ->
                when (result) {
                    is HostedWidgetAddCompletionResult.Placed ->
                        LauncherWidgetAddHandlingResult.Completed(result.message)
                    HostedWidgetAddCompletionResult.Rejected -> LauncherWidgetAddHandlingResult.Cancelled
                }
            }
        }

    private companion object {
        const val GLANCE_HOME_ONLY_MESSAGE = "Riffle widgets go on Home pages"
    }
}

data class LauncherWidgetAddWindowSize(
    val availableWidthDp: Int,
    val availableHeightDp: Int,
)

sealed interface LauncherWidgetAddHandlingResult {
    data object Cancelled : LauncherWidgetAddHandlingResult

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
