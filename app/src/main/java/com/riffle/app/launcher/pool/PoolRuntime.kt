package com.riffle.app.launcher.pool

import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

/**
 * Everything the preview's placed home page needs at runtime, assembled once like `WorkspaceRuntime`: the pool
 * [repository], the [iconLoader] and [widgetViews] the standard home already uses, and the tap [actions].
 *
 * [providerOf] names the provider of a migrated widget's host id (platform side), used once by the migration.
 *
 * Nothing here starts work: the repository reads storage only when [initialize] is called, which only happens
 * while the preview is open.
 */
internal class PoolRuntime(
    val repository: CachedPoolRepository,
    val iconLoader: AppIconLoader,
    val widgetViews: HomeWidgetViewFactory,
    val actions: PoolItemActions,
    private val providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
) {
    /** Reads the stored pool and runs the one-time import from [layoutSet]; a repeat call does nothing. */
    suspend fun initialize(layoutSet: HomeLayoutSet) = repository.initialize(layoutSet, providerOf)

    /** Replaces the pool by a fresh import of [layoutSet]. */
    suspend fun reimport(layoutSet: HomeLayoutSet) = repository.reimport(layoutSet, providerOf)
}
