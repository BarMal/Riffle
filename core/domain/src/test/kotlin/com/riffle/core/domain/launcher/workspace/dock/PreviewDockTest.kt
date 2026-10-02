package com.riffle.core.domain.launcher.workspace.dock

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewDockTest {
    private fun app(name: String) =
        AppShortcutItem(
            LauncherItemId("app:$name"),
            AppIdentity(AppPackageName("com.$name"), AppActivityName("com.$name.Main")),
            name.replaceFirstChar { it.uppercase() },
        )

    private fun folder(
        name: String,
        vararg apps: String,
    ) = FolderItem(LauncherItemId("folder:$name"), name.replaceFirstChar { it.uppercase() }, apps.map(::app))

    @Test
    fun noDockReadsAsAnEmptyShownDock() {
        val model = PreviewDock.from(null)
        assertTrue(model.pins.isEmpty())
        assertTrue(model.hasNoPins)
        assertTrue(model.isShown)
        assertEquals(PreviewDock.MIN_BAR_HEIGHT_DP, model.barHeightDp)
    }

    @Test
    fun appsAndFoldersKeepDockOrderAndWidgetsAreCountedNotDrawn() {
        val dock =
            DockModel(
                capacity = 5,
                items =
                    listOf(
                        app("phone"),
                        WidgetItem(LauncherItemId("widget:clock"), HostedWidgetId(7), "Clock"),
                        folder("social", "mail", "chat"),
                        app("maps"),
                    ),
            )
        val model = PreviewDock.from(dock)
        assertEquals(listOf("app:phone", "folder:social", "app:maps"), model.pins.map { it.key })
        assertEquals(1, model.unsupportedCount)
        assertFalse(model.hasNoPins)
    }

    @Test
    fun duplicateItemIdsAreDrawnOnce() {
        val model = PreviewDock.from(DockModel(capacity = 4, items = listOf(app("phone"), app("phone"))))
        assertEquals(1, model.pins.size)
    }

    @Test
    fun aHiddenDockDrawsNoPinsButKeepsABar() {
        val model = PreviewDock.from(DockModel(capacity = 4, items = listOf(app("phone")), isEnabled = false))
        assertFalse(model.isShown)
        assertTrue(model.hasNoPins)
        assertTrue(model.barHeightDp >= PreviewDock.MIN_BAR_HEIGHT_DP)
    }

    @Test
    fun iconSizeAndSpacingAreClampedToTheirRanges() {
        val big = PreviewDock.from(DockModel(capacity = 4, iconSizeDp = 500, itemSpacingDp = 500))
        assertEquals(MAX_DOCK_ICON_SIZE_DP, big.iconSizeDp)
        assertEquals(MAX_DOCK_ITEM_SPACING_DP, big.itemSpacingDp)
        val small = PreviewDock.from(DockModel(capacity = 4, iconSizeDp = 1, itemSpacingDp = -3))
        assertEquals(MIN_DOCK_ICON_SIZE_DP, small.iconSizeDp)
        assertEquals(0, small.itemSpacingDp)
    }

    @Test
    fun targetsAreNeverBelow48dp() {
        for (size in MIN_DOCK_ICON_SIZE_DP..MAX_DOCK_ICON_SIZE_DP) {
            assertTrue(PreviewDock.targetSizeDp(size) >= 48, "size $size")
            assertTrue(PreviewDock.barHeightDp(size) >= PreviewDock.targetSizeDp(size), "size $size")
        }
    }

    @Test
    fun spokenAndActionLabels() {
        val phone = PreviewDockPin.App(app("phone"))
        val one = PreviewDockPin.Folder(folder("solo", "mail"))
        val two = PreviewDockPin.Folder(folder("social", "mail", "chat"))
        assertEquals("Phone", PreviewDock.spokenLabel(phone))
        assertEquals("Open Phone", PreviewDock.actionLabel(phone))
        assertEquals("Solo folder, 1 app", PreviewDock.spokenLabel(one))
        assertEquals("Social folder, 2 apps", PreviewDock.spokenLabel(two))
        assertEquals("Open Social folder", PreviewDock.actionLabel(two))
    }
}
