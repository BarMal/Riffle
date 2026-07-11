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

class AndroidWidgetHostGateway internal constructor(
    private val platform: AndroidWidgetHostPlatform,
) : WidgetHostGateway,
    HomeWidgetViewFactory,
    DefaultLifecycleObserver {
    constructor(context: Context) : this(FrameworkAndroidWidgetHostPlatform(context))

    override fun onStart(owner: LifecycleOwner) {
        platform.startListening()
    }

    override fun onStop(owner: LifecycleOwner) {
        platform.stopListening()
    }

    override fun allocateHostedWidgetId(): HostedWidgetId = HostedWidgetId(platform.allocateAppWidgetId())

    override fun bindHostedWidget(
        hostedWidgetId: HostedWidgetId,
        provider: WidgetProviderIdentity,
    ): WidgetBindingResult =
        when (
            platform.bindAppWidgetIdIfAllowed(
                hostedWidgetId.value,
                provider.androidBindingTarget(),
            )
        ) {
            true -> WidgetBindingResult.Bound
            false -> WidgetBindingResult.RequiresPermission
        }

    override fun createBindHostedWidgetIntent(
        hostedWidgetId: HostedWidgetId,
        provider: WidgetProviderIdentity,
    ): Intent = bindHostedWidgetIntentData(hostedWidgetId, provider).toIntent()

    override fun hostedWidgetRequiresConfiguration(hostedWidgetId: HostedWidgetId): Boolean =
        platform.configureActivity(hostedWidgetId.value) != null

    override fun createConfigureHostedWidgetIntent(hostedWidgetId: HostedWidgetId): Intent =
        configureHostedWidgetIntentData(hostedWidgetId, platform.configureActivity(hostedWidgetId.value)).toIntent()

    override fun createHostedWidgetView(
        context: Context,
        widget: WidgetItem,
    ): View? = platform.createView(context, widget.appWidgetId.value)

    override fun deleteHostedWidgetId(hostedWidgetId: HostedWidgetId) {
        platform.deleteAppWidgetId(hostedWidgetId.value)
    }
}

internal interface AndroidWidgetHostPlatform {
    fun startListening()

    fun stopListening()

    fun allocateAppWidgetId(): Int

    fun bindAppWidgetIdIfAllowed(
        appWidgetId: Int,
        provider: AndroidWidgetProviderBindingTarget,
    ): Boolean

    fun configureActivity(appWidgetId: Int): AndroidWidgetProviderBindingTarget?

    fun createView(
        context: Context,
        appWidgetId: Int,
    ): View?

    fun deleteAppWidgetId(appWidgetId: Int)
}

private class FrameworkAndroidWidgetHostPlatform(
    context: Context,
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, RIFFLE_APP_WIDGET_HOST_ID),
) : AndroidWidgetHostPlatform {
    override fun startListening() = appWidgetHost.startListening()

    override fun stopListening() = appWidgetHost.stopListening()

    override fun allocateAppWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    override fun bindAppWidgetIdIfAllowed(
        appWidgetId: Int,
        provider: AndroidWidgetProviderBindingTarget,
    ): Boolean = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.toComponentName())

    override fun configureActivity(appWidgetId: Int): AndroidWidgetProviderBindingTarget? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)?.configure?.toAndroidBindingTarget()

    override fun createView(
        context: Context,
        appWidgetId: Int,
    ): View? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)
            ?.let { providerInfo -> appWidgetHost.createView(context, appWidgetId, providerInfo) }

    override fun deleteAppWidgetId(appWidgetId: Int) = appWidgetHost.deleteAppWidgetId(appWidgetId)
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

internal data class AndroidWidgetHostIntentData(
    val action: String,
    val appWidgetId: Int,
    val provider: AndroidWidgetProviderBindingTarget? = null,
)

internal fun bindHostedWidgetIntentData(
    hostedWidgetId: HostedWidgetId,
    provider: WidgetProviderIdentity,
): AndroidWidgetHostIntentData =
    AndroidWidgetHostIntentData(
        action = AppWidgetManager.ACTION_APPWIDGET_BIND,
        appWidgetId = hostedWidgetId.value,
        provider = provider.androidBindingTarget(),
    )

internal fun configureHostedWidgetIntentData(
    hostedWidgetId: HostedWidgetId,
    configureActivity: AndroidWidgetProviderBindingTarget?,
): AndroidWidgetHostIntentData =
    AndroidWidgetHostIntentData(
        action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE,
        appWidgetId = hostedWidgetId.value,
        provider = configureActivity,
    )

private fun AndroidWidgetHostIntentData.toIntent(): Intent =
    Intent(action)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .also { intent ->
            if (action == AppWidgetManager.ACTION_APPWIDGET_BIND) {
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider?.toComponentName())
            } else {
                intent.setComponent(provider?.toComponentName())
            }
        }

private fun AndroidWidgetProviderBindingTarget.toComponentName(): ComponentName = ComponentName(packageName, className)

private fun ComponentName.toAndroidBindingTarget(): AndroidWidgetProviderBindingTarget =
    AndroidWidgetProviderBindingTarget(packageName, className)

private const val RIFFLE_APP_WIDGET_HOST_ID = 1024
