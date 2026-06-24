package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

class AndroidWidgetHostGateway(
    context: Context,
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, RIFFLE_APP_WIDGET_HOST_ID),
) : WidgetHostGateway {
    override fun allocateHostedWidgetId(): HostedWidgetId = HostedWidgetId(appWidgetHost.allocateAppWidgetId())

    override fun bindHostedWidget(
        hostedWidgetId: HostedWidgetId,
        provider: WidgetProviderIdentity,
    ): WidgetBindingResult =
        when (
            appWidgetManager.bindAppWidgetIdIfAllowed(
                hostedWidgetId.value,
                provider.androidBindingTarget().toComponentName(),
            )
        ) {
            true -> WidgetBindingResult.Bound
            false -> WidgetBindingResult.RequiresPermission
        }

    override fun deleteHostedWidgetId(hostedWidgetId: HostedWidgetId) {
        appWidgetHost.deleteAppWidgetId(hostedWidgetId.value)
    }
}

internal data class AndroidWidgetProviderBindingTarget(
    val packageName: String,
    val className: String,
)

internal fun WidgetProviderIdentity.androidBindingTarget(): AndroidWidgetProviderBindingTarget =
    AndroidWidgetProviderBindingTarget(
        packageName = packageName.value,
        className = className.value,
    )

private fun AndroidWidgetProviderBindingTarget.toComponentName(): ComponentName = ComponentName(packageName, className)

private const val RIFFLE_APP_WIDGET_HOST_ID = 1024
