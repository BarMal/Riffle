package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun HomeLayout.selectedPageHostedWidgetIdForItem(itemId: LauncherItemId): HostedWidgetId? =
    selectedPage.items
        .filterIsInstance<WidgetItem>()
        .firstOrNull { widget -> widget.id == itemId }
        ?.takeIf { widget -> widget.glance == null }
        ?.appWidgetId

internal fun HomeLayout.dockHostedWidgetIdForItem(itemId: LauncherItemId): HostedWidgetId? =
    dock.items
        .filterIsInstance<WidgetItem>()
        .firstOrNull { widget -> widget.id == itemId }
        ?.takeIf { widget -> widget.glance == null }
        ?.appWidgetId

/** Every host id the item holds (one per platform widget of a stack), for its removal. */
internal fun HomeLayout.selectedPageHostedWidgetIdsForItem(itemId: LauncherItemId): List<HostedWidgetId> =
    selectedPage.items
        .filterIsInstance<WidgetItem>()
        .firstOrNull { widget -> widget.id == itemId }
        ?.hostIds
        .orEmpty()

/** The host ids of the widget [itemId] on the selected page, else on any page (a drop removes from the page it left). */
internal fun HomeLayout.hostedWidgetIdsForItem(itemId: LauncherItemId): List<HostedWidgetId> =
    selectedPageHostedWidgetIdsForItem(itemId).ifEmpty {
        pages.asSequence().flatMap { it.items.asSequence() }.filterIsInstance<WidgetItem>()
            .firstOrNull { it.id == itemId }?.hostIds.orEmpty()
    }

internal fun HomeLayout.selectedPageHostedWidgetIds(): List<HostedWidgetId> =
    selectedPage.items
        .filterIsInstance<WidgetItem>()
        .flatMap { widget -> widget.hostIds }

internal fun HomeLayout.hostedWidgetIdForItem(itemId: LauncherItemId): HostedWidgetId? =
    selectedPageHostedWidgetIdForItem(itemId)
        ?: dockHostedWidgetIdForItem(itemId)
