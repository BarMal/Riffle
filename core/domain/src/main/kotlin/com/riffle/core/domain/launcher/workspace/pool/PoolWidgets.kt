package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/** Widget-only operations. A host id is a single live instance, so it is bound to at most one pool widget. */
object PoolWidgets {
    /**
     * "Add a separate copy here": a NEW pool widget for the same provider, bound to [newHostedId] (allocated by the
     * app layer) or an unbound placeholder when null. A widget without a provider cannot be cloned.
     */
    fun separateCopy(
        pool: PlacedItemPool,
        widgetId: PoolItemId,
        workspaceId: WorkspaceId,
        pageId: LauncherPageId,
        ids: WorkspaceIdFactory,
        newHostedId: HostedWidgetId?,
        at: GridPlacement? = null,
    ): PoolResult =
        when (val source = pool.items[widgetId]) {
            null -> rejected(PoolRejection.UNKNOWN_ITEM)
            !is PoolWidget -> rejected(PoolRejection.NOT_A_WIDGET)
            else ->
                if (source.provider == null) {
                    rejected(PoolRejection.PROVIDER_UNKNOWN)
                } else {
                    val copy = source.copy(id = PoolIds.fresh(pool.items.keys, ids), hostedId = newHostedId)
                    PoolPlacement.addNew(pool, workspaceId, pageId, copy, at, source.resizeConstraints.minSpan)
                }
        }

    /** Binds a placeholder to a host id the app layer allocated and configured. */
    fun bind(
        pool: PlacedItemPool,
        widgetId: PoolItemId,
        hostedId: HostedWidgetId,
    ): PoolResult =
        when (val widget = pool.items[widgetId]) {
            null -> rejected(PoolRejection.UNKNOWN_ITEM)
            !is PoolWidget -> rejected(PoolRejection.NOT_A_WIDGET)
            else ->
                when {
                    widget.hostedId != null -> rejected(PoolRejection.WIDGET_ALREADY_BOUND)
                    hostedId in PoolReferences.hostIds(pool) -> rejected(PoolRejection.HOST_ID_IN_USE)
                    else ->
                        PoolResult.Done(
                            PoolEdit(pool.copy(items = pool.items + (widgetId to widget.copy(hostedId = hostedId)))),
                        )
                }
        }

    /** Turns a widget into a placeholder (its provider is gone); the old host id is released, deferred. */
    fun unbind(
        pool: PlacedItemPool,
        widgetId: PoolItemId,
    ): PoolResult =
        when (val widget = pool.items[widgetId]) {
            null -> rejected(PoolRejection.UNKNOWN_ITEM)
            !is PoolWidget -> rejected(PoolRejection.NOT_A_WIDGET)
            else ->
                PoolResult.Done(
                    PoolEdit(
                        pool = pool.copy(items = pool.items + (widgetId to widget.copy(hostedId = null))),
                        releasedHostIds = setOfNotNull(widget.hostedId),
                    ),
                )
        }
}
