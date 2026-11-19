package com.riffle.core.domain.launcher.layouts

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.layouts.AppLayouts.ordered
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLayoutsTest {
    private fun app(
        pkg: String,
        profile: AppProfile = AppProfile.personal(),
    ) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"), profile)

    private fun layout(
        id: String,
        first: String,
        second: String,
        opened: Long = 0L,
    ) = AppLayout(id, app(first), app(second), opened)

    private val notesMaps = layout("a", "notes", "maps", 10)
    private val mapsMail = layout("b", "maps", "mail", 20)

    private fun ids(layouts: List<AppLayout>) = layouts.map { it.id }

    @Test
    fun aNewLayoutGoesToTheFront() {
        val added = AppLayouts.add(listOf(notesMaps), layout("c", "mail", "chat", 30))

        assertEquals(listOf("c", "a"), ids(added))
    }

    @Test
    fun theSameOrderedPairIsNotAddedTwiceButComesToTheFront() {
        val added = AppLayouts.add(listOf(notesMaps, mapsMail), layout("z", "maps", "mail", 99))

        assertEquals(listOf("b", "a"), ids(added))
        assertEquals(99L, added.first().lastOpenedEpochMillis)
    }

    @Test
    fun aThenBAndBThenAAreDifferentLayouts() {
        val added = AppLayouts.add(listOf(notesMaps), layout("r", "maps", "notes", 50))

        assertEquals(listOf("r", "a"), ids(added))
    }

    @Test
    fun anAppIsNotPairedWithItself() {
        assertEquals(listOf(notesMaps), AppLayouts.add(listOf(notesMaps), layout("x", "chat", "chat")))
    }

    @Test
    fun theCapDropsTheOldestWhenAnotherIsAdded() {
        val full = (1..AppLayouts.CAP).map { layout("l$it", "p$it", "q$it", it.toLong()) }.reversed()

        val added = AppLayouts.add(full, layout("new", "x", "y", 100))

        assertEquals(AppLayouts.CAP, added.size)
        assertEquals("new", added.first().id)
        assertTrue(added.none { it.id == "l1" })
    }

    @Test
    fun removeThenRestoreBringsItBackAtTheSamePosition() {
        val three = listOf(notesMaps, mapsMail, layout("c", "mail", "chat"))

        val removed = AppLayouts.remove(three, "b")
        val restored = AppLayouts.restore(removed, mapsMail, index = 1)

        assertEquals(listOf("a", "c"), ids(removed))
        assertEquals(three, restored)
        assertEquals(removed, AppLayouts.restore(removed, notesMaps, 0))
    }

    @Test
    fun restoreClampsAnIndexPastTheEnd() {
        assertEquals(listOf("a", "b"), ids(AppLayouts.restore(listOf(notesMaps), mapsMail, 9)))
    }

    @Test
    fun swapExchangesTheSides() {
        val swapped = AppLayouts.swap(listOf(notesMaps), "a").single()

        assertEquals(app("maps"), swapped.first)
        assertEquals(app("notes"), swapped.second)
    }

    @Test
    fun swappingIntoAnExistingPairKeepsOnlyOneOfThem() {
        val both = listOf(notesMaps, layout("r", "maps", "notes", 5))

        assertEquals(listOf("r"), ids(AppLayouts.swap(both, "a")))
    }

    @Test
    fun changeFirstAndSecondEditOneSide() {
        val changedFirst = AppLayouts.changeFirst(listOf(notesMaps), "a", app("mail")).single()
        val changedSecond = AppLayouts.changeSecond(listOf(notesMaps), "a", app("chat")).single()

        assertEquals(listOf(app("mail"), app("maps")), listOf(changedFirst.first, changedFirst.second))
        assertEquals(listOf(app("notes"), app("chat")), listOf(changedSecond.first, changedSecond.second))
    }

    @Test
    fun theOtherSidesAppCannotBeChosen() {
        assertEquals(listOf(notesMaps), AppLayouts.changeFirst(listOf(notesMaps), "a", app("maps")))
        assertEquals(listOf(notesMaps), AppLayouts.changeSecond(listOf(notesMaps), "a", app("notes")))
    }

    @Test
    fun anUnknownIdChangesNothing() {
        val layouts = listOf(notesMaps)

        assertEquals(layouts, AppLayouts.swap(layouts, "nope"))
        assertEquals(layouts, AppLayouts.opened(layouts, "nope", 5))
        assertEquals(layouts, AppLayouts.changeFirst(layouts, "nope", app("mail")))
    }

    @Test
    fun openingOneBringsItToTheFrontWithTheTime() {
        val opened = AppLayouts.opened(listOf(notesMaps, mapsMail), "a", 500)

        assertEquals(listOf("a", "b"), ids(opened))
        assertEquals(500L, opened.first().lastOpenedEpochMillis)
    }

    @Test
    fun orderingIsMostRecentlyOpenedFirstAndStableOnTies() {
        val tied = listOf(layout("x", "p", "q", 0), layout("y", "r", "s", 0), layout("z", "t", "u", 7))

        assertEquals(listOf("z", "x", "y"), ids(tied.ordered()))
    }

    @Test
    fun uninstallingEitherAppRemovesTheLayout() {
        val layouts = listOf(notesMaps, mapsMail, layout("c", "mail", "chat"))

        val noMaps = AppLayouts.removeApp(layouts, AppPackageName("maps"), AppProfile.personal())

        assertEquals(listOf("c"), ids(noMaps))
    }

    @Test
    fun aSameNamedAppOfAnotherProfileIsNotTheOneUninstalled() {
        val work = AppProfile(AppProfileId("work"), AppProfileType.WORK)
        val layouts = listOf(notesMaps, AppLayout("w", app("notes", work), app("mail", work)))

        val pruned = AppLayouts.removeApp(layouts, AppPackageName("notes"), AppProfile.personal())

        assertEquals(listOf("w"), ids(pruned))
    }

    @Test
    fun pruningToTheInstalledAppsKeepsAllWhenTheyAreNotKnown() {
        val layouts = listOf(notesMaps, mapsMail)

        assertEquals(layouts, AppLayouts.pruneUninstalled(layouts, null))
        assertEquals(listOf("a"), ids(AppLayouts.pruneUninstalled(layouts, setOf(app("notes"), app("maps")))))
    }

    @Test
    fun theCodecRoundTripsTheStacksOrderAndTimes() {
        val layouts = listOf(mapsMail, notesMaps)

        assertEquals(layouts, AppLayoutsCodec.decode(AppLayoutsCodec.encode(layouts)))
    }

    @Test
    fun decodingNeverFailsOnBadData() {
        assertEquals(emptyList(), AppLayoutsCodec.decode(null))
        assertEquals(emptyList(), AppLayoutsCodec.decode(StoredValue.Str("nope")))
        val broken =
            StoredValue.Obj(
                mapOf(
                    "layouts" to
                        StoredValue.Arr(
                            listOf(
                                StoredValue.Num(3),
                                StoredValue.Obj(mapOf("id" to StoredValue.Str("half"))),
                                AppLayoutsCodec.encode(listOf(notesMaps)).fields.getValue("layouts")
                                    .let { (it as StoredValue.Arr).items.single() },
                            ),
                        ),
                ),
            )

        assertEquals(listOf(notesMaps), AppLayoutsCodec.decode(broken))
    }

    @Test
    fun decodingDropsARepeatedPairAndAppliesTheCap() {
        val twice = listOf(notesMaps, notesMaps.copy(id = "dup"))
        assertEquals(listOf("a"), ids(AppLayoutsCodec.decode(AppLayoutsCodec.encode(twice))))

        val many = (1..AppLayouts.CAP + 5).map { layout("l$it", "p$it", "q$it") }
        assertEquals(AppLayouts.CAP, AppLayoutsCodec.decode(AppLayoutsCodec.encode(many)).size)
    }

    @Test
    fun migratedLayoutsGoBehindWhatIsThereAndTwiceIsOnce() {
        val migrated = listOf(layout("p1", "notes", "maps"), layout("p2", "mail", "chat"))

        val once = AppLayouts.addMigrated(listOf(mapsMail), migrated)
        val twice = AppLayouts.addMigrated(once, migrated)

        assertEquals(listOf("b", "p1", "p2"), ids(once))
        assertEquals(once, twice)
        assertNull(once.firstOrNull { it.id == "nope" })
    }
}
