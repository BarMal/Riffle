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

    fun isHostedWidgetBoundTo(
        hostedWidgetId: HostedWidgetId,
        provider: WidgetProviderIdentity,
    ): Boolean = true

    fun createConfigureHostedWidgetIntent(hostedWidgetId: HostedWidgetId): Intent

    /**
     * Reports the currently committed host bounds to the provider.
     *
     * Values are density-independent pixels, matching the Android AppWidget options contract.
     */
    fun updateHostedWidgetOptions(
        hostedWidgetId: HostedWidgetId,
        size: WidgetSizeOptions,
    ) = Unit

    fun deleteHostedWidgetId(hostedWidgetId: HostedWidgetId)
}

data class WidgetSizeOptions(
    val minWidthDp: Int,
    val minHeightDp: Int,
    val maxWidthDp: Int = minWidthDp,
    val maxHeightDp: Int = minHeightDp,
) {
    init {
        require(minWidthDp > 0) { "Widget minimum width must be positive." }
        require(minHeightDp > 0) { "Widget minimum height must be positive." }
        require(maxWidthDp >= minWidthDp) { "Widget maximum width cannot be smaller than minimum width." }
        require(maxHeightDp >= minHeightDp) { "Widget maximum height cannot be smaller than minimum height." }
    }
}

sealed interface WidgetBindingResult {
    data object Bound : WidgetBindingResult

    data object RequiresPermission : WidgetBindingResult
}
