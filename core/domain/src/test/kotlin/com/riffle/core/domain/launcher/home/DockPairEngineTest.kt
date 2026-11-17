package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DockPairEngineTest {
    private val engine = DockEngine()

    @Test
    fun droppingOneAppOnAnotherMakesOnePairAtTheTargetsPosition() {
        val layout = layoutOf(pin("notes"), pin("maps"), pin("mail"), pin("clock"))

        val result = updated(engine.pairDockApps(layout, LauncherItemId("maps"), LauncherItemId("clock")))

        val items = result.dock.items
        assertEquals(3, items.size)
        assertEquals("notes", items[0].id.value)
        val pair = assertIs<AppPairItem>(items[1])
        assertEquals(listOf("maps", "clock"), listOf(pair.first.label, pair.second.label))
        assertEquals("mail", items[2].id.value)
    }

    @Test
    fun theDraggedAppBecomesTheSecondEvenWhenItWasBeforeTheTarget() {
        val layout = layoutOf(pin("notes"), pin("maps"), pin("mail"))

        val result = updated(engine.pairDockApps(layout, LauncherItemId("mail"), LauncherItemId("notes")))

        val pair = result.dock.items.filterIsInstance<AppPairItem>().single()
        assertEquals(listOf("mail", "notes"), listOf(pair.first.label, pair.second.label))
        assertEquals(listOf("maps", pair.id.value), result.dock.items.map { it.id.value })
    }

    @Test
    fun anAppIsNeverPairedWithItselfNorWithTheSameApp() {
        val layout = layoutOf(pin("notes"), pin("notes2", pkg = "notes"))

        assertEquals(
            DockEditRejectionReason.SAME_APP,
            rejected(engine.pairDockApps(layout, LauncherItemId("notes"), LauncherItemId("notes"))),
        )
        assertEquals(
            DockEditRejectionReason.SAME_APP,
            rejected(engine.pairDockApps(layout, LauncherItemId("notes"), LauncherItemId("notes2"))),
        )
        assertEquals(
            DockEditRejectionReason.SAME_APP,
            rejected(engine.pairDockAppWith(layout, LauncherItemId("notes"), installed("notes"))),
        )
    }

    @Test
    fun pairsDoNotNestAndNothingIsDroppedOntoAPair() {
        val layout = layoutOf(pin("notes"), pin("maps"), pin("mail"))
        val paired = updated(engine.pairDockApps(layout, LauncherItemId("notes"), LauncherItemId("maps")))
        val pairId = paired.dock.items.filterIsInstance<AppPairItem>().single().id

        assertEquals(
            DockEditRejectionReason.UNSUPPORTED_ITEM,
            rejected(engine.pairDockApps(paired, pairId, LauncherItemId("mail"))),
        )
        assertEquals(
            DockEditRejectionReason.UNSUPPORTED_ITEM,
            rejected(engine.pairDockApps(paired, LauncherItemId("mail"), pairId)),
        )
        assertEquals(
            DockEditRejectionReason.UNSUPPORTED_ITEM,
            rejected(engine.pairDockAppWith(paired, pairId, installed("mail"))),
        )
    }

    @Test
    fun anAppShortcutAndAWidgetCannotBePaired() {
        val shortcut = pin("notes").copy(appShortcutId = com.riffle.core.domain.launcher.apps.AppShortcutId("s"))
        val layout = layoutOf(shortcut, pin("maps"))

        assertEquals(
            DockEditRejectionReason.UNSUPPORTED_ITEM,
            rejected(engine.pairDockApps(layout, LauncherItemId("maps"), LauncherItemId("notes"))),
        )
    }

    @Test
    fun pairWithReplacesThePinnedAppAtTheSamePosition() {
        val layout = layoutOf(pin("notes"), pin("maps"), pin("mail"))

        val result = updated(engine.pairDockAppWith(layout, LauncherItemId("maps"), installed("clock")))

        assertEquals(3, result.dock.items.size)
        val pair = assertIs<AppPairItem>(result.dock.items[1])
        assertEquals(listOf("maps", "clock"), listOf(pair.first.label, pair.second.label))
    }

    @Test
    fun aPairMayExistAlongsideItsAppsPinnedSingly() {
        val layout = layoutOf(pin("notes"), pin("maps"))
        val paired = updated(engine.pairDockAppWith(layout, LauncherItemId("notes"), installed("maps")))

        assertTrue(paired.dock.items.any { it is AppPairItem })
        val again = updated(engine.addAppToDock(paired, installed("notes")))
        val ids = again.dock.items.map { it.id.value }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(3, again.dock.items.size)
    }

    @Test
    fun unpairRestoresTheTwoAppsFirstThenSecondAtThePairsPosition() {
        val layout = layoutOf(pin("notes"), pin("maps"), pin("mail"))
        val paired = updated(engine.pairDockApps(layout, LauncherItemId("maps"), LauncherItemId("mail")))
        val pairId = paired.dock.items.filterIsInstance<AppPairItem>().single().id

        val result = updated(engine.unpairDockItem(paired, pairId))

        assertEquals(listOf("notes", "maps", "mail"), result.dock.items.map { it.id.value })
    }

    @Test
    fun unpairKeepsIdsUniqueWhenASecondAppIsAlsoPinnedSingly() {
        val layout = layoutOf(pin("notes"), pin("maps"))
        val paired = updated(engine.pairDockAppWith(layout, LauncherItemId("notes"), installed("maps")))
        val pairId = paired.dock.items.filterIsInstance<AppPairItem>().single().id

        val result = updated(engine.unpairDockItem(paired, pairId))

        val ids = result.dock.items.map { it.id.value }
        assertEquals(3, ids.size)
        assertEquals(3, ids.toSet().size)
    }

    @Test
    fun swapReversesTheOrderAndKeepsTheIdAndPosition() {
        val layout = layoutOf(pin("notes"), pin("maps"), pin("mail"))
        val paired = updated(engine.pairDockApps(layout, LauncherItemId("notes"), LauncherItemId("maps")))
        val before = paired.dock.items.filterIsInstance<AppPairItem>().single()

        val after = updated(engine.swapDockPair(paired, before.id)).dock.items.first() as AppPairItem

        assertEquals(before.id, after.id)
        assertEquals(before.first, after.second)
        assertEquals(before.second, after.first)
        assertNotEquals(before, after)
        assertEquals(
            before,
            (
                updated(engine.swapDockPair(updated(engine.swapDockPair(paired, before.id)), before.id))
                    .dock.items.first()
            ),
        )
    }

    @Test
    fun pairingNeverIncreasesTheItemCountAndRemoveDropsTheWholePair() {
        val layout = layoutOf(pin("notes"), pin("maps"), pin("mail"))
        val paired = updated(engine.pairDockApps(layout, LauncherItemId("notes"), LauncherItemId("maps")))
        assertTrue(paired.dock.items.size < layout.dock.items.size)
        val pairId = paired.dock.items.first().id

        val removed = updated(engine.removeDockItem(paired, pairId))

        assertEquals(listOf("mail"), removed.dock.items.map { it.id.value })
    }

    @Test
    fun anAppOfAnotherProfileCannotBePaired() {
        val work =
            pin("work").let {
                it.copy(
                    appIdentity = it.appIdentity.copy(profile = com.riffle.core.domain.launcher.apps.AppProfile.work()),
                )
            }
        val layout = layoutOf(pin("notes"), work)

        assertEquals(
            DockEditRejectionReason.UNSUPPORTED_ITEM,
            rejected(engine.pairDockApps(layout, LauncherItemId("notes"), LauncherItemId("work"))),
        )
        assertEquals(
            DockEditRejectionReason.UNSUPPORTED_ITEM,
            rejected(engine.pairDockApps(layout, LauncherItemId("work"), LauncherItemId("notes"))),
        )
        val workApp =
            InstalledApp(
                work.appIdentity,
                label = "work",
            )
        assertEquals(
            DockEditRejectionReason.UNSUPPORTED_ITEM,
            rejected(engine.pairDockAppWith(layout, LauncherItemId("notes"), workApp)),
        )
    }

    @Test
    fun aPairCannotBeMovedToHome() {
        val layout = layoutOf(pin("notes"), pin("maps"))
        val paired = updated(engine.pairDockApps(layout, LauncherItemId("notes"), LauncherItemId("maps")))
        val pairId = paired.dock.items.first().id

        assertEquals(DockEditRejectionReason.UNSUPPORTED_ITEM, rejected(engine.moveDockItemToHome(paired, pairId)))
    }

    @Test
    fun movingByVisibleIndexKeepsHiddenPairsBesideTheirNeighbours() {
        val layout = layoutOf(pin("a"), pin("b"), pin("c"), pin("d"))
        val paired = updated(engine.pairDockApps(layout, LauncherItemId("b"), LauncherItemId("c")))
        val pairId = paired.dock.items[1].id
        val hidden = setOf(pairId)

        // stored: a, pair, d ; visible: a, d
        fun order(result: DockEditResult) = updated(result).dock.items.map { it.id.value }

        assertEquals(
            listOf("dock-pair:1", "d", "a"),
            order(engine.moveDockItemToIndex(paired, LauncherItemId("a"), 1, hidden)),
        )
        assertEquals(
            listOf("d", "a", "dock-pair:1"),
            order(engine.moveDockItemToIndex(paired, LauncherItemId("d"), 0, hidden)),
        )
        assertEquals(
            listOf("dock-pair:1", "d", "a"),
            order(engine.moveDockItem(paired, LauncherItemId("a"), DockItemMoveDirection.RIGHT, hidden)),
        )
        assertEquals(
            DockEditRejectionReason.INDEX_OUT_OF_BOUNDS,
            rejected(engine.moveDockItem(paired, LauncherItemId("a"), DockItemMoveDirection.LEFT, hidden)),
        )
    }

    private fun updated(result: DockEditResult): HomeLayout = assertIs<DockEditResult.Updated>(result).layout

    private fun rejected(result: DockEditResult): DockEditRejectionReason =
        assertIs<DockEditResult.Rejected>(result).reason

    private fun layoutOf(vararg items: LauncherItem): HomeLayout =
        HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 5, items = items.toList()))

    private fun pin(
        id: String,
        pkg: String = id,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = AppIdentity(AppPackageName("com.riffle.$pkg"), AppActivityName(".Main")),
            label = id,
        )

    private fun installed(pkg: String): InstalledApp =
        InstalledApp(AppIdentity(AppPackageName("com.riffle.$pkg"), AppActivityName(".Main")), label = pkg)
}
