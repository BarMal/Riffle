package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.stream.PairAction.CHANGE_FIRST
import com.riffle.core.domain.launcher.stream.PairAction.CHANGE_SECOND
import com.riffle.core.domain.launcher.stream.PairAction.OPEN
import com.riffle.core.domain.launcher.stream.PairAction.REMOVE
import com.riffle.core.domain.launcher.stream.PairAction.SWAP_SIDES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairsSectionTest {
    private fun app(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private val names = mapOf("notes" to "Notes", "maps" to "Maps", "mail" to "Mail", "chat" to "Chat")

    private val resolve: (AppIdentity) -> PairSide? = { identity ->
        names[identity.packageName.value]?.let { PairSide(identity, it, null) }
    }

    private fun cards(
        pairs: List<AppPair>,
        draft: PairDraft? = null,
    ) = PairsSection.result(pairs, draft, resolve).items.map { assertIs<AppPairContent>(it) }

    private val notesMaps = AppPair("a", app("notes"), app("maps"), 5)
    private val mailChat = AppPair("b", app("mail"), app("chat"), 9)

    @Test
    fun withNoPairThePageIsTheNewPairCardAlone() {
        val only = cards(emptyList()).single()

        assertEquals(PairCardKind.NEW, only.kind)
        assertEquals(setOf(PairAction.NEW_PAIR), only.actions)
    }

    @Test
    fun pairsComeInTheGivenOrderFirstAppFirstWithTheNewCardAtTheBack() {
        val all = cards(listOf(mailChat, notesMaps))

        assertEquals(listOf("pair:b", "pair:a", PairsSection.NEW_ID), all.map { it.id.value })
        assertEquals(listOf("Mail", "Chat"), all.first().let { listOf(it.first?.label, it.second?.label) })
        assertEquals(PairCardKind.NEW, all.last().kind)
    }

    @Test
    fun aDraftIsInFrontWithItsChosenHalfAndTheNewCardGivesWay() {
        val all = cards(listOf(notesMaps), PairDraft(first = app("chat")))

        assertEquals(listOf(PairsSection.DRAFT_ID, "pair:a"), all.map { it.id.value })
        val draft = all.first()
        assertEquals("Chat", draft.first?.label)
        assertNull(draft.second)
        assertEquals(setOf(PairAction.CHOOSE_FIRST, PairAction.CHOOSE_SECOND), draft.actions)
        assertNull(draft.pairId)
    }

    @Test
    fun aSavedPairOffersEveryEditAndAPairOfAnUnknownAppIsLeftOut() {
        val withGone = listOf(AppPair("x", app("notes"), app("gone")), notesMaps)

        val all = cards(withGone)

        assertEquals(listOf("pair:a", PairsSection.NEW_ID), all.map { it.id.value })
        val offered = setOf(OPEN, SWAP_SIDES, CHANGE_FIRST, CHANGE_SECOND, REMOVE)
        assertEquals(offered, all.first().actions)
    }

    @Test
    fun aPairIsDismissibleAndOpensButADraftAndTheNewCardDoNot() {
        val saved = cards(listOf(notesMaps)).first()
        val draft = cards(emptyList(), PairDraft()).first()
        val new = cards(emptyList()).first()

        assertTrue(StreamActionPolicy.offers(saved, StreamAction.Open))
        assertTrue(StreamActionPolicy.offers(saved, StreamAction.Dismiss))
        assertTrue(SwipeDismiss.offers(saved))
        assertFalse(StreamActionPolicy.offers(draft, StreamAction.Open))
        assertFalse(SwipeDismiss.offers(draft))
        assertFalse(SwipeDismiss.offers(new))
    }

    @Test
    fun aPairNeverOpensInPlace() {
        cards(listOf(notesMaps), PairDraft()).forEach { assertFalse(StreamCardActions.opensInPlace(it)) }
        assertEquals(emptyList(), StreamCardActions.forContent(cards(listOf(notesMaps)).first()))
    }

    @Test
    fun theDraftSavesWhenBothHalvesAreChosen() {
        val draft = PairDraft().choose(app("notes"), secondHalf = false)

        assertFalse(draft.isComplete)
        assertEquals(app("notes"), draft.otherHalf(secondHalf = true))
        assertTrue(draft.choose(app("maps"), secondHalf = true).isComplete)
    }
}
