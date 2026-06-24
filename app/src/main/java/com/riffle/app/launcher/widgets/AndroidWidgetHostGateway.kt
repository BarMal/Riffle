package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

class AndroidWidgetHostGateway(
    context: Context,
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, RIFFLE_APP_WIDGET_HOST_ID),
) : WidgetHostGateway,
    HomeWidgetViewFactory,
    DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        appWidgetHost.startListening()
    }

    override fun onStop(owner: LifecycleOwner) {
        appWidgetHost.stopListening()
    }

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

    override fun createBindHostedWidgetIntent(
        hostedWidgetId: HostedWidgetId,
        provider: WidgetProviderIdentity,
    ): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, hostedWidgetId.value)
            .putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
                provider.androidBindingTarget().toComponentName(),
            )

    override fun createHostedWidgetView(
        context: Context,
        widget: WidgetItem,
    ): View? =
        appWidgetManager.getAppWidgetInfo(widget.appWidgetId.value)
            ?.let { providerInfo ->
                appWidgetHost.createView(context, widget.appWidgetId.value, providerInfo)
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
