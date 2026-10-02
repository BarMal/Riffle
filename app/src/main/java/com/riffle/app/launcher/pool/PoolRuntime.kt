package com.riffle.app.launcher.pool

import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Everything the preview's placed home page needs at runtime, assembled once like `WorkspaceRuntime`: the pool
 * [repository], the [iconLoader] and [widgetViews] the standard home already uses, and the tap [actions].
 *
 * [providerOf] names the provider of a migrated widget's host id (platform side), used once by the migration.
 *
 * Nothing here starts work: the repository reads storage only when [initialize] is called, which only happens
 * while the preview is open, and [editing] does nothing until edit mode is entered.
 */
internal class PoolRuntime(
    val repository: CachedPoolRepository,
    val iconLoader: AppIconLoader,
    val widgetViews: HomeWidgetViewFactory,
    val actions: PoolItemActions,
    private val providerOf: (HostedWidgetId) -> WidgetProviderIdentity? = { null },
    /** Deletes widget host ids the pool let go of (called on the main thread by the platform wiring). */
    releaseHostIds: (Set<HostedWidgetId>) -> Unit = {},
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** Edit mode of the preview's home pages. Does nothing until the user enters it. */
    val editing = PoolEditController(repository, scope, releaseHostIds)

    /** Reads the stored pool and runs the one-time import from [layoutSet]; a repeat call does nothing. */
    suspend fun initialize(layoutSet: HomeLayoutSet) = repository.initialize(layoutSet, providerOf)

    /** Replaces the pool by a fresh import of [layoutSet]. */
    suspend fun reimport(layoutSet: HomeLayoutSet) = repository.reimport(layoutSet, providerOf)
}
