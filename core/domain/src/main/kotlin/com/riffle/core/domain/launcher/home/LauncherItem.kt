package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

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
    /** Set for one of Riffle's own glance widgets: drawn by the app, never bound through the widget host. */
    val glance: GlanceWidgetKind? = null,
    /**
     * A stack (ADR 0003, Amendment 14): every widget that shares this item's slot, in order, the first being this
     * item's own widget. Empty for a plain widget; otherwise two or more members ([WidgetStackEngine] keeps it so).
     * The item's own [appWidgetId], [label] and [glance] are the first member's and [resizeConstraints] is what every
     * member permits, so code that does not know about stacks still reads a consistent widget.
     */
    val stack: List<StackedWidget> = emptyList(),
    /** The member a stack shows (the last one shown), an index into [members]. Not stored for a plain widget. */
    val shownIndex: Int = 0,
    /**
     * The platform provider this widget was bound to, when known. A placeholder (no host id, see [needsSetUp]) uses it
     * to run the bind, permission and configuration steps again in place; it is not what makes a bound widget draw.
     * Null for a glance widget and for a widget whose provider was never recorded.
     */
    val provider: WidgetProviderIdentity? = null,
) : LauncherItem {
    override fun withPlacement(placement: GridPlacement): LauncherItem = copy(placement = placement)

    val isStack: Boolean get() = stack.size > 1

    /** A lone platform widget with no host id (a stack's members say it each for themselves). */
    val needsSetUp: Boolean get() = !isStack && glance == null && appWidgetId.value == 0

    /** This item's widgets in stack order: itself alone when it is a plain widget. */
    val members: List<StackedWidget>
        get() =
            if (isStack) {
                stack
            } else {
                listOf(StackedWidget(appWidgetId, label, resizeConstraints, glance, provider))
            }

    /** The member a stack shows now ([shownIndex] kept in range). */
    val shownMember: StackedWidget get() = members.let { it[shownIndex.coerceIn(0, it.lastIndex)] }

    /** Every host id the item holds (one per bound platform widget; none for a glance widget). */
    val hostIds: List<HostedWidgetId>
        get() = members.filter { it.glance == null && it.appWidgetId.value != 0 }.map { it.appWidgetId }
}

/**
 * One widget of a stack: what a [WidgetItem] holds of a single widget, without the slot (id and placement belong to the
 * stack). A glance widget has no host id ([appWidgetId] unbound) and a [glance] kind.
 */
data class StackedWidget(
    val appWidgetId: HostedWidgetId,
    val label: String,
    val resizeConstraints: WidgetResizeConstraints = WidgetResizeConstraints(),
    val glance: GlanceWidgetKind? = null,
    /** See [WidgetItem.provider]. */
    val provider: WidgetProviderIdentity? = null,
) {
    /** A platform widget with no host id: it could not be bound again (restored, activated, or the provider left). */
    val needsSetUp: Boolean get() = glance == null && appWidgetId.value == 0
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
