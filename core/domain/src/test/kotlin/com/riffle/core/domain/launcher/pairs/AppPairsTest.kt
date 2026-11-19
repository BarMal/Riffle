package com.riffle.core.domain.launcher.pairs

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.pairs.AppPairs.ordered
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppPairsTest {
    private fun app(
        pkg: String,
        profile: AppProfile = AppProfile.personal(),
    ) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"), profile)

    private fun pair(
        id: String,
        first: String,
        second: String,
        opened: Long = 0L,
    ) = AppPair(id, app(first), app(second), opened)

    private val notesMaps = pair("a", "notes", "maps", 10)
    private val mapsMail = pair("b", "maps", "mail", 20)

    private fun ids(pairs: List<AppPair>) = pairs.map { it.id }

    @Test
    fun aNewPairGoesToTheFront() {
        val added = AppPairs.add(listOf(notesMaps), pair("c", "mail", "chat", 30))

        assertEquals(listOf("c", "a"), ids(added))
    }

    @Test
    fun theSameOrderedPairIsNotAddedTwiceButComesToTheFront() {
        val added = AppPairs.add(listOf(notesMaps, mapsMail), pair("z", "maps", "mail", 99))

        assertEquals(listOf("b", "a"), ids(added))
        assertEquals(99L, added.first().lastOpenedEpochMillis)
    }

    @Test
    fun aThenBAndBThenAAreDifferentPairs() {
        val added = AppPairs.add(listOf(notesMaps), pair("r", "maps", "notes", 50))

        assertEquals(listOf("r", "a"), ids(added))
    }

    @Test
    fun anAppIsNotPairedWithItself() {
        assertEquals(listOf(notesMaps), AppPairs.add(listOf(notesMaps), pair("x", "chat", "chat")))
    }

    @Test
    fun theCapDropsTheOldestWhenAnotherIsAdded() {
        val full = (1..AppPairs.CAP).map { pair("l$it", "p$it", "q$it", it.toLong()) }.reversed()

        val added = AppPairs.add(full, pair("new", "x", "y", 100))

        assertEquals(AppPairs.CAP, added.size)
        assertEquals("new", added.first().id)
        assertTrue(added.none { it.id == "l1" })
    }

    @Test
    fun removeThenRestoreBringsItBackAtTheSamePosition() {
        val three = listOf(notesMaps, mapsMail, pair("c", "mail", "chat"))

        val removed = AppPairs.remove(three, "b")
        val restored = AppPairs.restore(removed, mapsMail, index = 1)

        assertEquals(listOf("a", "c"), ids(removed))
        assertEquals(three, restored)
        assertEquals(removed, AppPairs.restore(removed, notesMaps, 0))
    }

    @Test
    fun restoreClampsAnIndexPastTheEnd() {
        assertEquals(listOf("a", "b"), ids(AppPairs.restore(listOf(notesMaps), mapsMail, 9)))
    }

    @Test
    fun swapExchangesTheSides() {
        val swapped = AppPairs.swap(listOf(notesMaps), "a").single()

        assertEquals(app("maps"), swapped.first)
        assertEquals(app("notes"), swapped.second)
    }

    @Test
    fun swappingIntoAnExistingPairKeepsOnlyOneOfThem() {
        val both = listOf(notesMaps, pair("r", "maps", "notes", 5))

        assertEquals(listOf("r"), ids(AppPairs.swap(both, "a")))
    }

    @Test
    fun changeFirstAndSecondEditOneSide() {
        val changedFirst = AppPairs.changeFirst(listOf(notesMaps), "a", app("mail")).single()
        val changedSecond = AppPairs.changeSecond(listOf(notesMaps), "a", app("chat")).single()

        assertEquals(listOf(app("mail"), app("maps")), listOf(changedFirst.first, changedFirst.second))
        assertEquals(listOf(app("notes"), app("chat")), listOf(changedSecond.first, changedSecond.second))
    }

    @Test
    fun theOtherSidesAppCannotBeChosen() {
        assertEquals(listOf(notesMaps), AppPairs.changeFirst(listOf(notesMaps), "a", app("maps")))
        assertEquals(listOf(notesMaps), AppPairs.changeSecond(listOf(notesMaps), "a", app("notes")))
    }

    @Test
    fun anUnknownIdChangesNothing() {
        val pairs = listOf(notesMaps)

        assertEquals(pairs, AppPairs.swap(pairs, "nope"))
        assertEquals(pairs, AppPairs.opened(pairs, "nope", 5))
        assertEquals(pairs, AppPairs.changeFirst(pairs, "nope", app("mail")))
    }

    @Test
    fun openingOneBringsItToTheFrontWithTheTime() {
        val opened = AppPairs.opened(listOf(notesMaps, mapsMail), "a", 500)

        assertEquals(listOf("a", "b"), ids(opened))
        assertEquals(500L, opened.first().lastOpenedEpochMillis)
    }

    @Test
    fun orderingIsMostRecentlyOpenedFirstAndStableOnTies() {
        val tied = listOf(pair("x", "p", "q", 0), pair("y", "r", "s", 0), pair("z", "t", "u", 7))

        assertEquals(listOf("z", "x", "y"), ids(tied.ordered()))
    }

    @Test
    fun uninstallingEitherAppRemovesThePair() {
        val pairs = listOf(notesMaps, mapsMail, pair("c", "mail", "chat"))

        val noMaps = AppPairs.removeApp(pairs, AppPackageName("maps"), AppProfile.personal())

        assertEquals(listOf("c"), ids(noMaps))
    }

    @Test
    fun aSameNamedAppOfAnotherProfileIsNotTheOneUninstalled() {
        val work = AppProfile(AppProfileId("work"), AppProfileType.WORK)
        val pairs = listOf(notesMaps, AppPair("w", app("notes", work), app("mail", work)))

        val pruned = AppPairs.removeApp(pairs, AppPackageName("notes"), AppProfile.personal())

        assertEquals(listOf("w"), ids(pruned))
    }

    @Test
    fun pruningToTheInstalledAppsKeepsAllWhenTheyAreNotKnown() {
        val pairs = listOf(notesMaps, mapsMail)

        assertEquals(pairs, AppPairs.pruneUninstalled(pairs, null))
        assertEquals(listOf("a"), ids(AppPairs.pruneUninstalled(pairs, setOf(app("notes"), app("maps")))))
    }

    @Test
    fun theCodecRoundTripsTheStacksOrderAndTimes() {
        val pairs = listOf(mapsMail, notesMaps)

        assertEquals(pairs, AppPairsCodec.decode(AppPairsCodec.encode(pairs)))
    }

    @Test
    fun decodingNeverFailsOnBadData() {
        assertEquals(emptyList(), AppPairsCodec.decode(null))
        assertEquals(emptyList(), AppPairsCodec.decode(StoredValue.Str("nope")))
        val broken =
            StoredValue.Obj(
                mapOf(
                    "pairs" to
                        StoredValue.Arr(
                            listOf(
                                StoredValue.Num(3),
                                StoredValue.Obj(mapOf("id" to StoredValue.Str("half"))),
                                AppPairsCodec.encode(listOf(notesMaps)).fields.getValue("pairs")
                                    .let { (it as StoredValue.Arr).items.single() },
                            ),
                        ),
                ),
            )

        assertEquals(listOf(notesMaps), AppPairsCodec.decode(broken))
    }

    @Test
    fun decodingDropsARepeatedPairAndAppliesTheCap() {
        val twice = listOf(notesMaps, notesMaps.copy(id = "dup"))
        assertEquals(listOf("a"), ids(AppPairsCodec.decode(AppPairsCodec.encode(twice))))

        val many = (1..AppPairs.CAP + 5).map { pair("l$it", "p$it", "q$it") }
        assertEquals(AppPairs.CAP, AppPairsCodec.decode(AppPairsCodec.encode(many)).size)
    }

    @Test
    fun migratedPairsGoBehindWhatIsThereAndTwiceIsOnce() {
        val migrated = listOf(pair("p1", "notes", "maps"), pair("p2", "mail", "chat"))

        val once = AppPairs.addMigrated(listOf(mapsMail), migrated)
        val twice = AppPairs.addMigrated(once, migrated)

        assertEquals(listOf("b", "p1", "p2"), ids(once))
        assertEquals(once, twice)
        assertNull(once.firstOrNull { it.id == "nope" })
    }
}
