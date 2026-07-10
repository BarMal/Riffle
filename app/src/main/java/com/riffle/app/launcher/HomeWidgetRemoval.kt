package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun HomeLayout.selectedPageHostedWidgetIdForItem(itemId: LauncherItemId): HostedWidgetId? =
    selectedPage.items
        .filterIsInstance<WidgetItem>()
        .firstOrNull { widget -> widget.id == itemId }
        ?.appWidgetId

internal fun HomeLayout.selectedPageHostedWidgetIds(): List<HostedWidgetId> =
    selectedPage.items
        .filterIsInstance<WidgetItem>()
        .map { widget -> widget.appWidgetId }

internal fun HomeLayout.hostedWidgetIdForItem(itemId: LauncherItemId): HostedWidgetId? =
    selectedPageHostedWidgetIdForItem(itemId)
        ?: dock.items
            .filterIsInstance<WidgetItem>()
            .firstOrNull { widget -> widget.id == itemId }
            ?.appWidgetId
