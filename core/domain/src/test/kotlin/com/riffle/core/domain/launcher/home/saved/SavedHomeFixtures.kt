package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

internal val GRID = GridDimensions(columns = 4, rows = 3)

internal fun identity(name: String) = AppIdentity(AppPackageName("com.example.$name"), AppActivityName("Main"))

internal fun at(
    column: Int,
    row: Int,
    columns: Int = 1,
    rows: Int = 1,
) = GridPlacement(GridCell(column, row), GridSpan(columns, rows))

internal fun app(
    name: String,
    column: Int,
    row: Int,
    shortcut: String? = null,
) = AppShortcutItem(
    id = LauncherItemId("item-$name"),
    appIdentity = identity(name),
    label = name,
    appShortcutId = shortcut?.let(::AppShortcutId),
    placement = at(column, row),
)

internal fun folder(
    name: String,
    column: Int,
    row: Int,
    vararg apps: String,
) = FolderItem(
    id = LauncherItemId("folder-$name"),
    label = name,
    items = apps.map { AppShortcutItem(LauncherItemId("entry-$it"), identity(it), it) },
    placement = at(column, row),
)

internal fun provider(name: String) =
    WidgetProviderIdentity(AppPackageName("com.example.$name"), WidgetProviderClassName("com.example.$name.Widget"))

internal fun widget(
    name: String,
    host: Int,
    column: Int,
    row: Int,
    columns: Int = 2,
    rows: Int = 1,
) = WidgetItem(
    id = LauncherItemId("widget-$name"),
    appWidgetId = HostedWidgetId(host),
    label = name,
    placement = at(column, row, columns, rows),
)

internal fun stack(
    name: String,
    column: Int,
    row: Int,
    vararg members: Pair<String, Int>,
): WidgetItem {
    val stacked = members.map { (label, host) -> StackedWidget(HostedWidgetId(host), label) }
    return WidgetItem(
        id = LauncherItemId("stack-$name"),
        appWidgetId = stacked.first().appWidgetId,
        label = stacked.first().label,
        placement = at(column, row, 2, 2),
        stack = stacked,
    )
}

internal fun page(
    id: String,
    vararg items: LauncherItem,
    pinned: Boolean = false,
) = LauncherPage(LauncherPageId(id), grid = GRID, items = items.toList(), isPinned = pinned)

/** Provider of a live host id, by a table. */
internal fun providers(vararg entries: Pair<Int, String>): (HostedWidgetId) -> WidgetProviderIdentity? {
    val table = entries.associate { (host, name) -> HostedWidgetId(host) to provider(name) }
    return { table[it] }
}
