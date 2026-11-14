package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamLayoutTest {
    @Test
    fun defaultHoldsEverySectionOnInTheFixedOrder() {
        val layout = StreamLayout.DEFAULT
        assertEquals(StreamSection.layoutSections, layout.sections.map { it.section })
        assertTrue(layout.sections.all { it.enabled })
        assertEquals(StreamSection.layoutSections, layout.enabledSections.map { it.section })
    }

    @Test
    fun defaultViewsFollowTheModelMap() {
        assertEquals(StreamView.STACK, StreamLayout.DEFAULT.viewOf(StreamSection.NOW))
        assertEquals(StreamView.STACK, StreamLayout.DEFAULT.viewOf(StreamSection.NOTIFICATIONS))
        assertEquals(StreamView.STACK, StreamLayout.DEFAULT.viewOf(StreamSection.RECENTS))
        assertEquals(StreamView.STACK, StreamLayout.DEFAULT.viewOf(StreamSection.TODAY))
        assertEquals(StreamView.STACK, StreamLayout.DEFAULT.viewOf(StreamSection.FEEDS))
    }

    @Test
    fun aLayoutMustHoldEverySectionExactlyOnce() {
        assertFailsWith<IllegalArgumentException> { StreamLayout(sections = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            StreamLayout(sections = StreamLayout.defaultSections() + StreamSectionLayout(StreamSection.NOW))
        }
        assertFailsWith<IllegalArgumentException> {
            StreamLayout(sections = StreamLayout.defaultSections().drop(1))
        }
    }

    @Test
    fun setViewChangesOnlyThatSection() {
        val after =
            StreamLayoutReducer.reduce(
                StreamLayout.DEFAULT,
                StreamLayoutAction.SetView(StreamSection.NOTIFICATIONS, StreamView.LIST),
            )
        assertEquals(StreamView.LIST, after.viewOf(StreamSection.NOTIFICATIONS))
        assertEquals(StreamView.STACK, after.viewOf(StreamSection.NOW))
        assertEquals(StreamLayout.DEFAULT.sections.map { it.section }, after.sections.map { it.section })
    }

    @Test
    fun setEnabledHidesAndShowsASection() {
        val off =
            StreamLayoutReducer.reduce(
                StreamLayout.DEFAULT,
                StreamLayoutAction.SetEnabled(StreamSection.FEEDS, false),
            )
        assertFalse(off.layoutOf(StreamSection.FEEDS).enabled)
        assertFalse(StreamSection.FEEDS in off.enabledSections.map { it.section })
        val on = StreamLayoutReducer.reduce(off, StreamLayoutAction.SetEnabled(StreamSection.FEEDS, true))
        assertEquals(StreamLayout.DEFAULT, on)
    }

    @Test
    fun moveReordersAndClampsAtTheEnds() {
        val down = StreamLayoutReducer.reduce(StreamLayout.DEFAULT, StreamLayoutAction.Move(StreamSection.NOW, 1))
        assertEquals(StreamSection.NOTIFICATIONS, down.sections[0].section)
        assertEquals(StreamSection.NOW, down.sections[1].section)
        val up = StreamLayoutReducer.reduce(down, StreamLayoutAction.Move(StreamSection.NOW, -1))
        assertEquals(StreamLayout.DEFAULT, up)
        assertSame(
            StreamLayout.DEFAULT,
            StreamLayoutReducer.reduce(StreamLayout.DEFAULT, StreamLayoutAction.Move(StreamSection.NOW, -1)),
        )
        val bottom = StreamLayoutReducer.reduce(StreamLayout.DEFAULT, StreamLayoutAction.Move(StreamSection.NOW, 99))
        assertEquals(StreamSection.NOW, bottom.sections.last().section)
    }

    @Test
    fun restoreBringsBackAnEarlierLayoutForUndo() {
        val before = StreamLayout.DEFAULT
        val after = StreamLayoutReducer.reduce(before, StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST))
        assertEquals(before, StreamLayoutReducer.reduce(after, StreamLayoutAction.Restore(before)))
    }

    @Test
    fun resetReturnsTheDefault() {
        val changed = StreamLayoutReducer.reduce(StreamLayout.DEFAULT, StreamLayoutAction.Move(StreamSection.FEEDS, -5))
        assertEquals(StreamLayout.DEFAULT, StreamLayoutReducer.reduce(changed, StreamLayoutAction.Reset))
    }

    @Test
    fun optionsAreReplacedWhole() {
        val options = StreamSectionOptions(recents = RecentsOptions(limit = 3))
        val after = StreamLayoutReducer.reduce(StreamLayout.DEFAULT, StreamLayoutAction.SetOptions(options))
        assertEquals(3, after.options.recents.limit)
    }

    @Test
    fun layoutsAreKeptPerPostureAndLandscapeSharesThePhone() {
        val phone = StreamLayout.DEFAULT.copy(options = StreamSectionOptions(recents = RecentsOptions(limit = 2)))
        val layouts = StreamLayouts().with(HomeLayoutDeviceClass.PHONE, phone)
        assertEquals(phone, layouts.forPosture(HomeLayoutDeviceClass.PHONE))
        assertEquals(phone, layouts.forPosture(HomeLayoutDeviceClass.PHONE_LANDSCAPE))
        assertEquals(StreamLayout.DEFAULT, layouts.forPosture(HomeLayoutDeviceClass.TABLET))
        assertFalse(layouts.isDefault)
        assertTrue(StreamLayouts().isDefault)
        assertTrue(StreamLayouts().with(HomeLayoutDeviceClass.TABLET, StreamLayout.DEFAULT).isDefault)
    }
}
