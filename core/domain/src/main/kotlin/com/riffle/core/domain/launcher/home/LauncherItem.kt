package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutId

sealed interface LauncherItem {
    val id: LauncherItemId
    val placement: GridPlacement?

    fun withPlacement(placement: GridPlacement): LauncherItem
}

@JvmInline
value class LauncherItemId(val value: String)

data class AppShortcutItem(
    override val id: LauncherItemId,
    val appIdentity: AppIdentity,
    val label: String,
    val appShortcutId: AppShortcutId? = null,
    override val placement: GridPlacement? = null,
) : LauncherItem {
    override fun withPlacement(placement: GridPlacement): LauncherItem = copy(placement = placement)
}

data class FolderItem(
    override val id: LauncherItemId,
    val label: String,
    val items: List<AppShortcutItem>,
    override val placement: GridPlacement? = null,
) : LauncherItem {
    override fun withPlacement(placement: GridPlacement): LauncherItem = copy(placement = placement)
}

data class WidgetItem(
    override val id: LauncherItemId,
    val appWidgetId: HostedWidgetId,
    val label: String,
    val resizeConstraints: WidgetResizeConstraints = WidgetResizeConstraints(),
    override val placement: GridPlacement? = null,
) : LauncherItem {
    override fun withPlacement(placement: GridPlacement): LauncherItem = copy(placement = placement)
}

@JvmInline
value class HostedWidgetId(val value: Int)

/** The persisted, grid-relative provider contract for a hosted widget. */
data class WidgetResizeConstraints(
    val minSpan: GridSpan = GridSpan(),
    val maxSpan: GridSpan? = null,
    val supportsHorizontalResize: Boolean = true,
    val supportsVerticalResize: Boolean = true,
) {
    fun permits(span: GridSpan): Boolean =
        span.columns >= minSpan.columns &&
            span.rows >= minSpan.rows &&
            (maxSpan == null || (span.columns <= maxSpan.columns && span.rows <= maxSpan.rows))
}
