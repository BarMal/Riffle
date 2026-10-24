package com.riffle.app.launcher.pool

import com.riffle.app.launcher.pool.PoolHomeFixture.homeItems
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyHomeLayoutRepositoryTest {
    private val start = PoolHomeFixture.legacySet()
    private val restored = mutableListOf<HomeLayoutSet>()
    private var now = 0L
    private var blocked = 0

    private fun repository() =
        ReadOnlyHomeLayoutRepository(start, restore = { restored += it }, clock = { now }, onBlocked = { blocked++ })

    private fun HomeLayout.mapHome(change: (LauncherPage) -> LauncherPage): HomeLayout =
        copy(pages = pages.map { if (it.type == LauncherPageType.Home) change(it) else it })

    private val widget =
        WidgetItem(LauncherItemId("w-new"), HostedWidgetId(9), "W", placement = PoolHomeFixture.at(2, 3))

    private val corner = PoolHomeFixture.at(3, 4)
    private val wide = PoolHomeFixture.at(0, 1, 3, 2)
    private val panel = LauncherPage(LauncherPageId("panel"), grid = GridDimensions(4, 2), items = listOf(widget))

    /** Every kind of home edit the shell's engines make. */
    private val edits: Map<String, (HomeLayout) -> HomeLayout> =
        mapOf(
            "add" to { l -> l.mapHome { it.copy(items = it.items + PoolHomeFixture.app("new", 3)) } },
            "move" to {
                    l ->
                l.mapHome {
                        p ->
                    p.copy(items = p.items.map { if (it.id.value == "mail") it.withPlacement(corner) else it })
                }
            },
            "remove" to { l -> l.mapHome { p -> p.copy(items = p.items.filterNot { it.id.value == "maps" }) } },
            "folder" to { l ->
                l.mapHome { p ->
                    val apps = p.items.filterIsInstance<AppShortcutItem>().map { it.copy(placement = null) }
                    p.copy(
                        items =
                            p.items.filterNot {
                                it is AppShortcutItem
                            } + FolderItem(LauncherItemId("f"), "F", apps, PoolHomeFixture.at(3, 0)),
                    )
                }
            },
            "widget add" to { l -> l.mapHome { it.copy(items = it.items + widget) } },
            "widget resize" to {
                    l ->
                l.mapHome {
                        p ->
                    p.copy(items = p.items.map { if (it is WidgetItem) it.withPlacement(wide) else it })
                }
            },
            "widget remove" to { l -> l.mapHome { p -> p.copy(items = p.items.filterNot { it is WidgetItem }) } },
            "page add" to {
                    l ->
                l.copy(pages = l.pages + LauncherPage(LauncherPageId("p2"), grid = GridDimensions(4, 5)))
            },
            "page delete" to {
                    l ->
                l.copy(
                    pages =
                        l.pages.filter {
                            it.type != LauncherPageType.Home
                        } + LauncherPage(PoolHomeFixture.home, grid = GridDimensions(4, 5)),
                )
            },
            "dock pin" to {
                    l ->
                l.copy(dock = l.dock.copy(items = l.dock.items + PoolHomeFixture.app("pin", 0).copy(placement = null)))
            },
            "dock unpin" to { l -> l.copy(dock = l.dock.copy(items = emptyList())) },
            "dock panel" to {
                    l ->
                l.copy(dock = l.dock.copy(panel = panel))
            },
        )

    @Test
    fun everyHomeEditIsRefusedAndNothingIsWritten() {
        edits.forEach { (name, edit) ->
            val repository = repository()
            val result = repository.commitHomeLayoutSet(start.withActiveLayout(edit(start.activeLayout)))
            assertSame(name, start, result)
            assertSame(name, start, repository.loadHomeLayoutSet())
        }
        assertEquals(edits.size, blocked)
        assertTrue("nothing restored or written", restored.isEmpty())
    }

    @Test
    fun theNoticeIsThrottled() {
        val repository =
            ReadOnlyHomeLayoutRepository(
                start,
                clock = { now },
                noticeIntervalMillis = 1_000,
                onBlocked = { blocked++ },
            )
        val notices = mutableListOf<Unit>()
        val collector =
            CoroutineScope(
                Dispatchers.Unconfined,
            ).launch { repository.blockedEdits.collect { notices += it } }
        val add = start.withActiveLayout(edits.getValue("add")(start.activeLayout))
        repository.commitHomeLayoutSet(add)
        now = 500
        repository.commitHomeLayoutSet(add)
        now = 1_500
        repository.commitHomeLayoutSet(add)
        collector.cancel()
        assertEquals(3, blocked)
        assertEquals(2, notices.size)
    }

    @Test
    fun selectionEditModeAndANewLayoutKeyAreKeptInMemory() {
        val repository = repository()
        val selected = start.withActiveLayout(start.activeLayout.copy(editMode = HomeEditMode.ManagingPages))
        assertSame(selected, repository.commitHomeLayoutSet(selected))
        val landscape = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE_LANDSCAPE)
        val rotated = selected.withLayout(landscape, start.activeLayout)
        assertSame(rotated, repository.commitHomeLayoutSet(rotated))
        assertTrue(restored.isEmpty())
    }

    @Test
    fun aBackupRestoreIsTheOneWriteAndIsShown() {
        val repository = repository()
        val backup =
            start.withActiveLayout(
                start.activeLayout.mapHome { it.copy(items = listOf(PoolHomeFixture.app("b", 0))) },
            )
        assertEquals(backup, repository.restoreFromBackup(backup, backupPool = null, homeFromPool = false))
        assertEquals(listOf(backup), restored)
        assertEquals(listOf("b"), repository.loadHomeLayoutSet()!!.activeLayout.homeItems().map { it.id.value })
    }

    @Test
    fun withNothingShownTheShellsDefaultsAreKept() {
        val empty = ReadOnlyHomeLayoutRepository(null)
        val defaults = HomeLayoutSet.fromLayout(start.activeLayout)
        assertSame(defaults, empty.commitHomeLayoutSet(defaults))
        assertSame(
            defaults,
            empty.commitHomeLayoutSet(defaults.withActiveLayout(edits.getValue("add")(defaults.activeLayout))),
        )
    }
}
