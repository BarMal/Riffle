package com.riffle.core.domain.launcher.home

import kotlin.math.hypot

/**
 * The rules of a widget stack (ADR 0003, Amendment 14): several widgets of one span share one Home slot. A stack is a
 * [WidgetItem] whose [WidgetItem.stack] holds two or more members; one left alone becomes a plain widget again. Pure;
 * every edit is all or nothing (a rejection leaves the layout as it was) and only Home pages are touched (the Dock and
 * its panel never hold a stack; a generated page takes none).
 *
 * Moving, resizing and removing act on the whole item, so they need nothing here except the shared resize constraints:
 * the item's [WidgetItem.resizeConstraints] is what [constraintsOf] says every member permits, kept in step by
 * [restacked].
 */
@Suppress("TooManyFunctions", "ReturnCount")
class WidgetStackEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    /** Whether [dragged] (a lone widget) may be dropped on [target] (a widget or stack): same span, room, no repeat. */
    fun canStack(
        target: WidgetItem,
        dragged: WidgetItem,
    ): Boolean = rejectionFor(target, dragged) == null

    private fun rejectionFor(
        target: WidgetItem,
        dragged: WidgetItem,
    ): WidgetStackRejection? {
        val span = target.placement?.span
        return when {
            target.id == dragged.id -> WidgetStackRejection.SAME_ITEM
            span == null || dragged.placement == null -> WidgetStackRejection.ITEM_NOT_FOUND
            dragged.isStack -> WidgetStackRejection.DRAGGED_IS_STACK
            dragged.placement?.span != span -> WidgetStackRejection.DIFFERENT_SPAN
            target.members.size >= MAX_MEMBERS -> WidgetStackRejection.STACK_FULL
            !dragged.resizeConstraints.permits(span) || !target.resizeConstraints.permits(span) ->
                WidgetStackRejection.SPAN_NOT_SUPPORTED
            dragged.hostIds.any { it in target.hostIds } -> WidgetStackRejection.DUPLICATE_WIDGET
            else -> null
        }
    }

    /** Drops [draggedId] on [targetId] (both on one Home page): the dragged widget joins the stack and is shown. */
    fun stackOnto(
        layout: HomeLayout,
        draggedId: LauncherItemId,
        targetId: LauncherItemId,
    ): WidgetStackResult {
        val page = layout.pages.firstOrNull { p -> p.items.any { it.id == targetId } }
        val target = page?.items?.firstOrNull { it.id == targetId } as? WidgetItem
        val dragged = page?.items?.firstOrNull { it.id == draggedId } as? WidgetItem
        return when {
            page == null || target == null || dragged == null -> reject(WidgetStackRejection.ITEM_NOT_FOUND)
            page.type is LauncherPageType.Generated -> reject(WidgetStackRejection.GENERATED_PAGE)
            else ->
                rejectionFor(target, dragged)?.let(::reject)
                    ?: run {
                        val members = target.members + dragged.members
                        val merged = target.restacked(members, shown = members.lastIndex)
                        val without = gridPlacementEngine.removeItem(page, draggedId)
                        updated(layout, without.replacing(merged))
                    }
        }
    }

    /**
     * Adds [member] (a widget the person just picked) to the stack of [itemId], or makes a stack of a lone widget, and
     * shows it. The member must permit the stack's span.
     */
    fun addMember(
        layout: HomeLayout,
        itemId: LauncherItemId,
        member: StackedWidget,
    ): WidgetStackResult {
        val (page, item) = locate(layout, itemId)
        val span = item?.placement?.span
        return when {
            page == null || item == null || span == null -> reject(WidgetStackRejection.ITEM_NOT_FOUND)
            page.type is LauncherPageType.Generated -> reject(WidgetStackRejection.GENERATED_PAGE)
            item.members.size >= MAX_MEMBERS -> reject(WidgetStackRejection.STACK_FULL)
            !member.resizeConstraints.permits(span) -> reject(WidgetStackRejection.SPAN_NOT_SUPPORTED)
            layout.allItems().filterIsInstance<WidgetItem>().any { other ->
                member.glance == null && member.appWidgetId.value != 0 && member.appWidgetId in other.hostIds
            } -> reject(WidgetStackRejection.DUPLICATE_WIDGET)
            else -> {
                val members = item.members + member
                updated(layout, page.replacing(item.restacked(members, shown = members.lastIndex)))
            }
        }
    }

    /** Remembers which member the stack shows. An index out of range is kept in range. */
    fun show(
        layout: HomeLayout,
        itemId: LauncherItemId,
        index: Int,
    ): WidgetStackResult {
        val (page, item) = locate(layout, itemId)
        return if (page == null || item == null) {
            reject(WidgetStackRejection.ITEM_NOT_FOUND)
        } else if (!item.isStack) {
            reject(WidgetStackRejection.NOT_A_STACK)
        } else {
            updated(layout, page.replacing(item.copy(shownIndex = index.coerceIn(0, item.members.lastIndex))))
        }
    }

    /** Moves the member at [from] to [to]. The member that was shown stays shown. */
    fun reorder(
        layout: HomeLayout,
        itemId: LauncherItemId,
        from: Int,
        to: Int,
    ): WidgetStackResult {
        val (page, item) = locate(layout, itemId)
        return when {
            page == null || item == null -> reject(WidgetStackRejection.ITEM_NOT_FOUND)
            !item.isStack -> reject(WidgetStackRejection.NOT_A_STACK)
            from !in item.members.indices || to !in item.members.indices ->
                reject(WidgetStackRejection.INDEX_OUT_OF_RANGE)
            else -> {
                val members = item.members.toMutableList()
                members.add(to, members.removeAt(from))
                val shown = item.members[item.shownIndex.coerceIn(0, item.members.lastIndex)]
                val shownIndex = members.indexOf(shown).coerceAtLeast(0)
                updated(layout, page.replacing(item.restacked(members, shown = shownIndex)))
            }
        }
    }

    /**
     * Removes the member at [index] from the stack (the caller deletes its host id, as for any removed widget). A
     * stack left with one widget is a plain widget again. The last widget of an item cannot be removed here: that is
     * removing the item.
     */
    fun remove(
        layout: HomeLayout,
        itemId: LauncherItemId,
        index: Int,
    ): WidgetStackResult {
        val (page, item) = locate(layout, itemId)
        return when {
            page == null || item == null -> reject(WidgetStackRejection.ITEM_NOT_FOUND)
            !item.isStack -> reject(WidgetStackRejection.NOT_A_STACK)
            index !in item.members.indices -> reject(WidgetStackRejection.INDEX_OUT_OF_RANGE)
            else -> {
                val removed = item.members[index]
                val members = item.members.filterIndexed { i, _ -> i != index }
                val shown = if (index < item.shownIndex) item.shownIndex - 1 else item.shownIndex
                WidgetStackResult.Updated(
                    layout = layout.withUpdatedPage(page.replacing(item.restacked(members, shown))),
                    member = removed,
                )
            }
        }
    }

    /**
     * "Move out to Home": the member at [index] becomes a widget of its own in the nearest free cells of the stack's
     * page that take its span (nearest to the stack, ties in reading order, the engine's collision rules). With no room
     * the layout is unchanged and the result is [WidgetStackRejection.NO_ROOM].
     */
    fun moveOut(
        layout: HomeLayout,
        itemId: LauncherItemId,
        index: Int,
    ): WidgetStackResult {
        val (page, item) = locate(layout, itemId)
        val placement = item?.placement
        if (page == null || item == null || placement == null) return reject(WidgetStackRejection.ITEM_NOT_FOUND)
        if (!item.isStack) return reject(WidgetStackRejection.NOT_A_STACK)
        if (index !in item.members.indices) return reject(WidgetStackRejection.INDEX_OUT_OF_RANGE)
        val member = item.members[index]
        val members = item.members.filterIndexed { i, _ -> i != index }
        val shown = if (index < item.shownIndex) item.shownIndex - 1 else item.shownIndex
        val remaining = page.replacing(item.restacked(members, shown))
        val fresh = member.asItem(freshItemId(layout, member))
        val landing =
            (0 until remaining.grid.rows)
                .flatMap { row -> (0 until remaining.grid.columns).map { column -> GridCell(column, row) } }
                .sortedWith(
                    compareBy<GridCell> {
                        hypot((it.column - placement.cell.column).toDouble(), (it.row - placement.cell.row).toDouble())
                    }.thenBy { it.row }.thenBy { it.column },
                )
                .firstNotNullOfOrNull { cell ->
                    (
                        gridPlacementEngine.placeItem(
                            remaining,
                            fresh.copy(placement = GridPlacement(cell, placement.span)),
                        ) as? PlaceLauncherItemResult.Placed
                    )?.page
                }
        return if (landing == null) {
            reject(WidgetStackRejection.NO_ROOM)
        } else {
            WidgetStackResult.Updated(layout.withUpdatedPage(landing), member, fresh.id)
        }
    }

    private fun locate(
        layout: HomeLayout,
        itemId: LauncherItemId,
    ): Pair<LauncherPage?, WidgetItem?> {
        val page = layout.pages.firstOrNull { p -> p.items.any { it.id == itemId } }
        return page to (page?.items?.firstOrNull { it.id == itemId } as? WidgetItem)
    }

    private fun LauncherPage.replacing(item: WidgetItem): LauncherPage =
        copy(items = items.map { if (it.id == item.id) item else it })

    private fun HomeLayout.withUpdatedPage(page: LauncherPage): HomeLayout =
        copy(pages = pages.map { if (it.id == page.id) page else it })

    private fun updated(
        layout: HomeLayout,
        page: LauncherPage,
    ): WidgetStackResult = WidgetStackResult.Updated(layout.withUpdatedPage(page))

    private fun reject(reason: WidgetStackRejection): WidgetStackResult = WidgetStackResult.Rejected(reason)

    private fun freshItemId(
        layout: HomeLayout,
        member: StackedWidget,
    ): LauncherItemId {
        val taken = layout.allItems().mapTo(HashSet()) { it.id.value }
        val base = member.glance?.let { "glance:${it.storedId}" } ?: "widget:${member.appWidgetId.value}"
        return generateSequence(1) { it + 1 }
            .map { n -> LauncherItemId(if (n == 1 && member.glance == null) base else "$base:$n") }
            .first { it.value !in taken }
    }

    companion object {
        /** A stack holds at most this many widgets. */
        const val MAX_MEMBERS = 10

        /** What every one of [members] permits: the largest minimum, the smallest maximum, resize only if all can. */
        fun constraintsOf(members: List<StackedWidget>): WidgetResizeConstraints {
            if (members.size == 1) return members.single().resizeConstraints
            val all = members.map { it.resizeConstraints }
            val maxima = all.mapNotNull { it.maxSpan }
            return WidgetResizeConstraints(
                minSpan = GridSpan(all.maxOf { it.minSpan.columns }, all.maxOf { it.minSpan.rows }),
                maxSpan =
                    maxima.takeIf { it.isNotEmpty() }
                        ?.let { GridSpan(columns = it.minOf { s -> s.columns }, rows = it.minOf { s -> s.rows }) },
                supportsHorizontalResize = all.all { it.supportsHorizontalResize },
                supportsVerticalResize = all.all { it.supportsVerticalResize },
            )
        }
    }
}

/**
 * This item holding [members] (in order) with [shown] shown: the first member becomes the item's own widget, the
 * constraints are what all of them permit, and a single member leaves a plain widget (no stack, no shown index).
 */
fun WidgetItem.restacked(
    members: List<StackedWidget>,
    shown: Int,
): WidgetItem {
    val lead = members.first()
    return copy(
        appWidgetId = lead.appWidgetId,
        label = lead.label,
        glance = lead.glance,
        resizeConstraints = WidgetStackEngine.constraintsOf(members),
        stack = if (members.size > 1) members else emptyList(),
        shownIndex = if (members.size > 1) shown.coerceIn(0, members.lastIndex) else 0,
    )
}

/** A member as a widget item of its own (unplaced). */
fun StackedWidget.asItem(id: LauncherItemId): WidgetItem =
    WidgetItem(
        id = id,
        appWidgetId = appWidgetId,
        label = label,
        resizeConstraints = resizeConstraints,
        glance = glance,
    )

sealed interface WidgetStackResult {
    /** [member] is the widget an edit took out or put in; [newItemId] the id a moved-out widget now has. */
    data class Updated(
        val layout: HomeLayout,
        val member: StackedWidget? = null,
        val newItemId: LauncherItemId? = null,
    ) : WidgetStackResult

    data class Rejected(val reason: WidgetStackRejection) : WidgetStackResult
}

enum class WidgetStackRejection {
    ITEM_NOT_FOUND,
    NOT_A_STACK,
    SAME_ITEM,
    DRAGGED_IS_STACK,
    DIFFERENT_SPAN,
    STACK_FULL,
    SPAN_NOT_SUPPORTED,
    DUPLICATE_WIDGET,
    INDEX_OUT_OF_RANGE,
    GENERATED_PAGE,
    NO_ROOM,
}
