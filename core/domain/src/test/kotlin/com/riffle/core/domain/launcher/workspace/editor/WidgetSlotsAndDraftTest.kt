package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetSlotsAndDraftTest {
    private fun grid(
        columns: Int,
        rows: Int,
        vararg placements: WidgetPlacement,
    ) = PageContent.WidgetGrid(columns, rows, placements.toList())

    @Test
    fun firstFreeScansInReadingOrder() {
        val g = grid(3, 2, WidgetPlacement(widget("a", 2, 1), 0, 0))
        assertEquals(WidgetSlot(2, 0), WidgetSlots.firstFree(g, WidgetSpan(1, 1)))
        assertEquals(WidgetSlot(0, 1), WidgetSlots.firstFree(g, WidgetSpan(3, 1)))
        assertNull(WidgetSlots.firstFree(g, WidgetSpan(3, 2)))
    }

    @Test
    fun aFullGridHasNoFreeSlot() {
        val g = grid(1, 1, WidgetPlacement(widget("a"), 0, 0))
        assertNull(WidgetSlots.firstFree(g, WidgetSpan(1, 1)))
    }

    @Test
    fun defaultSpansFitTheGrid() {
        ExpressionKind.entries.forEach { kind ->
            val span = WidgetSlots.defaultSpan(kind, columns = 2, rows = 1)
            assertTrue(span.columns in 1..2 && span.rows == 1, "$kind $span")
        }
    }

    @Test
    fun draftNeedsASourceAndRecognisesPresets() {
        assertNull(LensDraft().toLens())
        val draft = LensDraft(sources = listOf(APPS)).withPreset(LensPreset.LATEST_FIVE)
        val lens = assertNotNull(draft.toLens())
        assertEquals(5, lens.limit)
        assertEquals(LensPreset.LATEST_FIVE, LensDraft.from(lens).preset)
        assertNull(draft.withFilter(LensFilter.HasActions()).preset)
        assertEquals(listOf(NOTES), draft.toggleSource(APPS).toggleSource(NOTES).sources)
    }
}
