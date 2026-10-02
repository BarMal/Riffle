package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExclusionHideMenuTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    @Test
    fun `the menu offers the four hide kinds but never empty content`() {
        val notification =
            item("n:1", SourceIds.NOTIFICATIONS, "chat", title = "Order #4821 shipped", group = "chat:personal")

        assertTrue(HideKind.EMPTY_CONTENT in ExclusionHideActions.choicesFor(notification).map { it.kind })
        assertEquals(
            listOf(HideKind.APP, HideKind.GROUP, HideKind.ITEM, HideKind.LIKE_THIS),
            ExclusionHideActions.menuChoicesFor(notification).map { it.kind },
        )
    }

    @Test
    fun `like this is offered only when the text is long enough`() {
        val short = item("n:2", SourceIds.NOTIFICATIONS, "chat", title = "Hi", group = "chat:personal")

        assertFalse(HideKind.LIKE_THIS in ExclusionHideActions.menuChoicesFor(short).map { it.kind })
    }

    @Test
    fun `sensitive items offer no text rule and the message names only a fixed phrase`() {
        val marker = "UNIQUE-SECRET-MARKER"
        val sensitive =
            item("n:3", SourceIds.NOTIFICATIONS, "chat", title = marker, body = marker, group = "chat:personal")
                .copy(privacy = ItemPrivacy.SENSITIVE)

        val kinds = ExclusionHideActions.menuChoicesFor(sensitive).map { it.kind }
        assertEquals(listOf(HideKind.APP, HideKind.GROUP, HideKind.ITEM), kinds)
        kinds.forEach { kind ->
            val change = ExclusionHideActions.apply(LayoutExclusionRules(), phone, sensitive, kind)
            assertTrue(change.applied)
            assertFalse(change.message.toString().contains(marker))
        }
        ExclusionHideActions.menuChoicesFor(sensitive).forEach { choice ->
            assertFalse(choice.label.contains(marker) || choice.what.contains(marker))
        }
    }

    @Test
    fun `an item with no app group or text still offers hiding that one item`() {
        val bare = item("x", SourceIds.CALENDAR, pkg = null)

        assertEquals(listOf(HideKind.ITEM), ExclusionHideActions.menuChoicesFor(bare).map { it.kind })
    }
}
