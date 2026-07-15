package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LauncherCardCollectionTest {
    private val planner = LauncherCardCollectionPlanner()

    @Test
    fun ordersCardsByRankingThenChronologyThenStableId() {
        val collection =
            planner.plan(
                listOf(
                    card(id = "zeta", score = 1, updatedAt = 10L),
                    card(id = "alpha", score = 1, updatedAt = 10L),
                    card(id = "mail", score = 2, updatedAt = 1L),
                ),
            )

        assertEquals(listOf("mail", "alpha", "zeta"), collection.cards.map { card -> card.id.value })
    }

    @Test
    fun resolvesDuplicateIdsToTheNewestSourceSnapshot() {
        val collection =
            planner.plan(
                listOf(
                    card(id = "mail", score = 5, updatedAt = 10L),
                    card(id = "mail", score = 1, updatedAt = 20L),
                ),
            )

        assertEquals(1, collection.cards.size)
        assertEquals(20L, collection.cards.single().chronology.updatedAtEpochMillis)
    }

    @Test
    fun breaksEqualDuplicateUpdatesBySourceState() {
        val collection =
            planner.plan(
                listOf(
                    card(id = "mail", state = LauncherCardState.ACTION_FAILED),
                    card(id = "mail", state = LauncherCardState.READY),
                ),
            )

        assertEquals(LauncherCardState.READY, collection.cards.single().state)
    }

    @Test
    fun filtersRemovedCardsWithoutHidingRecoverableUnavailableCards() {
        val collection =
            planner.plan(
                listOf(
                    card(id = "removed", state = LauncherCardState.REMOVED),
                    card(id = "unavailable", state = LauncherCardState.UNAVAILABLE),
                ),
            )

        assertEquals(listOf("unavailable"), collection.cards.map { card -> card.id.value })
    }

    @Test
    fun capsHighVolumeCollectionsAndReportsOmittedCards() {
        val collection = planner.plan((1..3).map { index -> card(id = "card-$index", score = index) }, maxCards = 2)

        assertEquals(listOf("card-3", "card-2"), collection.cards.map { card -> card.id.value })
        assertEquals(1, collection.omittedCardCount)
    }

    @Test
    fun rejectsNegativeCapsAndInvalidCollections() {
        assertFailsWith<IllegalArgumentException> { planner.plan(emptyList(), maxCards = -1) }
        assertFailsWith<IllegalArgumentException> {
            LauncherCardCollection(cards = listOf(card(id = "mail"), card(id = "mail")))
        }
    }

    private fun card(
        id: String,
        score: Int = 0,
        updatedAt: Long = 0L,
        state: LauncherCardState = LauncherCardState.READY,
    ): LauncherCard {
        return LauncherCard(
            id = LauncherCardId(id),
            sourceRef = LauncherCardSourceRef.App(appIdentity(packageName = "com.riffle.$id")),
            state = state,
            chronology = LauncherCardChronology(updatedAtEpochMillis = updatedAt, rankingScore = score),
        )
    }

    private fun appIdentity(packageName: String) =
        AppIdentity(
            packageName = AppPackageName(packageName),
            activityName = AppActivityName(".MainActivity"),
            profile = AppProfile.personal(),
        )
}
