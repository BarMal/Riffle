package com.riffle.app.launcher

import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.home.restacked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stored widget stack (ADR 0003, Amendment 14): the layout JSON of data from before stacks and of stacks. */
class HomeLayoutWidgetStackJsonCodecTest {
    private val span = GridSpan(columns = 2, rows = 2)

    private fun layoutOf(vararg items: WidgetItem): HomeLayout =
        HomeLayoutDefaults.standard().let { defaults ->
            defaults.copy(pages = listOf(defaults.selectedPage.copy(items = items.toList())))
        }

    private val stack =
        WidgetItem(
            id = LauncherItemId("widget:7"),
            appWidgetId = HostedWidgetId(7),
            label = "Weather",
            placement = GridPlacement(GridCell(1, 2), span),
        ).restacked(
            listOf(
                StackedWidget(HostedWidgetId(7), "Weather", WidgetResizeConstraints(GridSpan(1, 1), GridSpan(4, 4))),
                StackedWidget(HostedWidgetId(9), "Calendar", WidgetResizeConstraints(GridSpan(2, 1), GridSpan(3, 3))),
                StackedWidget(
                    GlanceWidgets.UNBOUND_HOST_ID,
                    "Now playing",
                    GlanceWidgetKind.NOW_PLAYING.resizeConstraints,
                    GlanceWidgetKind.NOW_PLAYING,
                ),
            ),
            shown = 1,
        )

    @Test
    fun aStackRoundTripsWithItsMembersOrderAndShownWidget() {
        val decoded = decodeHomeLayout(encodeHomeLayout(layoutOf(stack))).selectedPage.items.single() as WidgetItem

        assertEquals(stack, decoded)
        assertEquals(listOf(7, 9, 0), decoded.members.map { it.appWidgetId.value })
        assertEquals(1, decoded.shownIndex)
        assertEquals(GlanceWidgetKind.NOW_PLAYING, decoded.members.last().glance)
        assertEquals(GridSpan(2, 1), decoded.resizeConstraints.minSpan)
    }

    @Test
    fun aPlainWidgetIsStoredWithoutAnyStackKey() {
        val plain = stack.copy(stack = emptyList(), shownIndex = 0)

        val encoded = encodeHomeLayout(layoutOf(plain))

        assertFalse(encoded.contains("\"stack\""))
        assertFalse(encoded.contains("\"shown\""))
    }

    @Test
    fun widgetDataWrittenBeforeStacksDecodesUnchanged() {
        val decoded =
            decodeHomeLayout(
                """
                {"selectedPageId":"home","pages":[{"id":"home","columns":4,"rows":5,"items":[
                  {"type":"widget","id":"widget:42","appWidgetId":42,"label":"Weather","column":1,"row":2,
                   "columns":2,"rows":2,"minColumns":1,"minRows":1,"maxColumns":4,"maxRows":4,
                   "supportsHorizontalResize":true,"supportsVerticalResize":false}
                ]}],"dock":{"capacity":5,"items":[]}}
                """.trimIndent(),
            ).selectedPage.items.single() as WidgetItem

        assertFalse(decoded.isStack)
        assertEquals(emptyList<StackedWidget>(), decoded.stack)
        assertEquals(0, decoded.shownIndex)
        assertEquals(HostedWidgetId(42), decoded.appWidgetId)
        assertEquals(listOf(HostedWidgetId(42)), decoded.hostIds)
        assertFalse(decoded.resizeConstraints.supportsVerticalResize)
    }

    @Test
    fun aStackWithFewerThanTwoReadableMembersIsAPlainWidget() {
        val one =
            """
            {"selectedPageId":"home","pages":[{"id":"home","columns":4,"rows":5,"items":[
              {"type":"widget","id":"widget:42","appWidgetId":42,"label":"Weather","column":1,"row":2,
               "columns":2,"rows":2,"shown":1,
               "stack":[{"appWidgetId":42,"label":"Weather"},{"label":"no host id"}]}
            ]}],"dock":{"capacity":5,"items":[]}}
            """.trimIndent()

        val decoded = decodeHomeLayout(one).selectedPage.items.single() as WidgetItem

        assertFalse(decoded.isStack)
        assertEquals(0, decoded.shownIndex)
        assertEquals(HostedWidgetId(42), decoded.appWidgetId)
    }

    @Test
    fun aShownIndexOutOfRangeIsKeptInRange() {
        val encoded = encodeHomeLayout(layoutOf(stack)).replace("\"shown\":1", "\"shown\":99")

        val decoded = decodeHomeLayout(encoded).selectedPage.items.single() as WidgetItem

        assertEquals(2, decoded.shownIndex)
        assertTrue(decoded.isStack)
    }

    @Test
    fun aStackInALayoutSetSurvivesTheSetCodec() {
        val set = com.riffle.core.domain.launcher.home.HomeLayoutSet.fromLayout(layoutOf(stack))

        val decoded = decodeHomeLayoutSet(encodeHomeLayoutSet(set))

        assertEquals(stack, decoded.activeLayout.selectedPage.items.single())
    }
}
