package com.riffle.app.launcher.widgets

import android.content.Intent
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

interface WidgetHostGateway {
    fun allocateHostedWidgetId(): HostedWidgetId

    fun bindHostedWidget(
        hostedWidgetId: HostedWidgetId,
        provider: WidgetProviderIdentity,
    ): WidgetBindingResult

    fun createBindHostedWidgetIntent(
        hostedWidgetId: HostedWidgetId,
        provider: WidgetProviderIdentity,
    ): Intent

    fun hostedWidgetRequiresConfiguration(hostedWidgetId: HostedWidgetId): Boolean

    fun createConfigureHostedWidgetIntent(hostedWidgetId: HostedWidgetId): Intent

    fun deleteHostedWidgetId(hostedWidgetId: HostedWidgetId)
}

sealed interface WidgetBindingResult {
    data object Bound : WidgetBindingResult

    data object RequiresPermission : WidgetBindingResult
}
