package com.riffle.app.launcher.stream

import com.riffle.app.launcher.pairs.AppPairsRepository
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.dock.PairLaunch
import com.riffle.core.domain.launcher.dock.PairLauncher
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.pairs.AppPairsStore
import com.riffle.core.domain.launcher.stream.AppPairContent
import com.riffle.core.domain.launcher.stream.PairSide
import com.riffle.core.domain.launcher.stream.PairsSection
import com.riffle.core.domain.launcher.stream.StreamAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** What the Pairs page does: launch order, the bump to the front, remove and Undo, the draft and the picker. */
class PairsControllerTest {
    private class MemoryStore(var stored: List<AppPair> = emptyList()) : AppPairsStore {
        override fun read() = stored

        override fun write(pairs: List<AppPair>) {
            stored = pairs
        }
    }

    private class FakeLauncher(var accepts: Boolean = true) : PairLauncher {
        val launches = mutableListOf<PairLaunch>()

        override fun launch(pair: PairLaunch): Boolean {
            if (accepts) launches += pair
            return accepts
        }
    }

    private fun app(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private var now = 100L
    private var next = 0
    private val store = MemoryStore()
    private val repository = AppPairsRepository(store, { now }, { "id${next++}" })
    private val launcher = FakeLauncher()
    private val controller = PairsController(repository, launcher)

    private val labels =
        listOf("notes", "maps", "mail", "chat").associate {
            app(it) to it.replaceFirstChar(Char::uppercase)
        }

    private fun contentOf(id: String): AppPairContent =
        PairsSection.result(repository.pairs.value, null) { identity ->
            labels[identity]?.let { PairSide(identity, it, null) }
        }.items.filterIsInstance<AppPairContent>().first { it.pairId == id }

    private fun ids() = repository.pairs.value.map { it.id }

    @Test
    fun aTapOpensTheFirstAppThenTheSecondAndBumpsThePairToTheFront() {
        repository.add(app("notes"), app("maps"))
        now = 200
        repository.add(app("mail"), app("chat"))
        now = 300

        controller.perform(contentOf("id0"), StreamAction.Open)

        assertEquals(
            listOf("Notes", "Maps"),
            launcher.launches.single().let { listOf(it.first.label, it.second.label) },
        )
        assertEquals(listOf("id0", "id1"), ids())
        assertEquals(300L, repository.pairs.value.first().lastOpenedEpochMillis)
    }

    @Test
    fun aLaunchThatIsRefusedBecauseOneIsInFlightChangesNothing() {
        repository.add(app("notes"), app("maps"))
        repository.add(app("mail"), app("chat"))
        launcher.accepts = false
        val before = repository.pairs.value

        controller.perform(contentOf("id0"), StreamAction.Open)

        assertEquals(before, repository.pairs.value)
    }

    @Test
    fun removingAPairIsUndoneAtTheSamePlace() {
        repository.add(app("notes"), app("maps"))
        now = 200
        repository.add(app("mail"), app("chat"))
        now = 300
        repository.add(app("chat"), app("notes"))
        val before = repository.pairs.value

        controller.perform(contentOf("id1"), StreamAction.Dismiss)
        val removal = controller.removed!!

        assertEquals(listOf("id2", "id0"), ids())
        controller.restore(removal)
        assertEquals(before, repository.pairs.value)
        assertNull(controller.removed)
    }

    @Test
    fun theDraftIsSavedAsTheFrontPairWhenBothHalvesAreChosen() {
        repository.add(app("notes"), app("maps"))
        controller.edit(PairEdit.NewPair)
        controller.edit(PairEdit.ChooseDraftHalf(secondHalf = false))
        controller.pick(app("mail"))
        assertEquals(true, controller.draft != null)
        assertEquals(listOf("id0"), ids())

        controller.edit(PairEdit.ChooseDraftHalf(secondHalf = true))
        // The picker leaves out the app the other half holds.
        assertEquals(app("mail"), controller.picker?.exclude)
        controller.pick(app("chat"))

        assertNull(controller.draft)
        assertEquals(listOf("id1", "id0"), ids())
        assertEquals(
            listOf(app("mail"), app("chat")),
            repository.pairs.value.first().let { listOf(it.first, it.second) },
        )
    }

    @Test
    fun aHalfMadeDraftIsDroppedWhenThePageIsLeftAndNeverStored() {
        controller.edit(PairEdit.NewPair)
        controller.edit(PairEdit.ChooseDraftHalf(secondHalf = false))
        controller.pick(app("mail"))

        controller.leave()

        assertNull(controller.draft)
        assertEquals(emptyList<AppPair>(), store.stored)
    }

    @Test
    fun cancelDropsTheDraft() {
        controller.edit(PairEdit.NewPair)

        controller.edit(PairEdit.DiscardDraft)

        assertNull(controller.draft)
    }

    @Test
    fun swapAndChangeEditASavedPair() {
        repository.add(app("notes"), app("maps"))

        controller.edit(PairEdit.SwapSides("id0"))
        assertEquals(app("maps"), repository.pairs.value.single().first)

        controller.edit(PairEdit.ChangeApp("id0", secondHalf = true))
        assertEquals(app("maps"), controller.picker?.exclude)
        controller.pick(app("chat"))

        assertEquals(
            listOf(app("maps"), app("chat")),
            repository.pairs.value.single().let { listOf(it.first, it.second) },
        )
        assertFalse(controller.picker != null)
    }

    @Test
    fun anUninstalledAppRemovesItsPairs() {
        repository.add(app("notes"), app("maps"))
        repository.add(app("mail"), app("chat"))

        repository.pruneUninstalled(setOf(app("notes"), app("maps"), app("mail")))

        assertEquals(listOf("id0"), ids())
    }
}
