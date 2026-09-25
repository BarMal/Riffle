package com.riffle.core.domain.launcher.home

/**
 * Every item the layout holds, wherever it holds it.
 *
 * The dock's panel is not one of [HomeLayout.pages] -- it hangs off the dock -- so a traversal
 * written as "pages plus the dock" silently skips whatever the user put on the panel. Anything
 * that means "all of this layout's items" should ask here rather than assemble its own list.
 */
fun HomeLayout.allItems(): List<LauncherItem> {
    val panelItems = dock.panel?.items.orEmpty()
    return pages.flatMap { page -> page.items } + dock.items + panelItems
}

/** Whether any of the layout's items hosts [hostedWidgetId]. */
fun HomeLayout.hostsWidget(hostedWidgetId: HostedWidgetId): Boolean =
    allItems()
        .filterIsInstance<WidgetItem>()
        .any { widget -> widget.appWidgetId == hostedWidgetId }

/**
 * Whether anything in the set hosts [hostedWidgetId]: any stored layout, or any device class's shared
 * dock (#1205). Stored layouts are checked as stored, not through [HomeLayoutSet.layoutFor], so a
 * widget is only reported unreferenced -- and its host id released -- when nothing holds it at all.
 */
fun HomeLayoutSet.hostsWidget(hostedWidgetId: HostedWidgetId): Boolean =
    layouts.values.any { layout -> layout.hostsWidget(hostedWidgetId) } ||
        docks.values.any { dock ->
            (dock.items + dock.panel?.items.orEmpty())
                .any { item -> item is WidgetItem && item.appWidgetId == hostedWidgetId }
        }
