package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LauncherCardCollectionTest {
    private val planner = LauncherCardCollectionPlanner()

    @Test
    fun ordersCardsByRankingThenChronologyThenStableId() {
        val collection =
            planner.plan(
                listOf(
                    card(
                        id = "zeta",
                        chronology = LauncherCardChronology(rankingScore = 1, updatedAtEpochMillis = 10L),
                    ),
                    card(
                        id = "alpha",
                        chronology = LauncherCardChronology(rankingScore = 1, updatedAtEpochMillis = 10L),
                    ),
                    card(
                        id = "mail",
                        chronology = LauncherCardChronology(rankingScore = 2, updatedAtEpochMillis = 1L),
                    ),
                ),
            )

        assertEquals(listOf("mail", "alpha", "zeta"), collection.cards.map { card -> card.id.value })
    }

    @Test
    fun resolvesDuplicateIdsToTheNewestSourceSnapshot() {
        val collection =
            planner.plan(
                listOf(
                    card(
                        id = "mail",
                        chronology = LauncherCardChronology(rankingScore = 5, updatedAtEpochMillis = 10L),
                    ),
                    card(
                        id = "mail",
                        chronology = LauncherCardChronology(rankingScore = 1, updatedAtEpochMillis = 20L),
                    ),
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
    fun projectsAmbiguousDuplicateSnapshotsStablyInEitherInputOrder() {
        val intent = LauncherCardUserIntent(isPinned = true, isFavourite = true)
        val first = card(id = "mail", content = LauncherCardContent.Text(title = "First"), userIntent = intent)
        val second = card(id = "mail", content = LauncherCardContent.Text(title = "Second"), userIntent = intent)
        val forward = planner.plan(listOf(first, second))
        val reversed = planner.plan(listOf(second, first))

        assertEquals(forward, reversed)
        assertEquals(LauncherCardState.STALE, forward.cards.single().state)
        assertNull(forward.cards.single().content)
        assertEquals(intent, forward.cards.single().userIntent)
    }

    @Test
    fun mergesConflictingAmbiguousDuplicateIntentWithoutClearingChoices() {
        val first = card(id = "mail", content = LauncherCardContent.Text(title = "First"))
        val second =
            card(
                id = "mail",
                content = LauncherCardContent.Text(title = "Second"),
                userIntent = LauncherCardUserIntent(isPinned = true, isFavourite = true),
            )
        val forward = planner.plan(listOf(first, second))
        val reversed = planner.plan(listOf(second, first))

        assertEquals(forward, reversed)
        assertEquals(LauncherCardUserIntent(isPinned = true, isFavourite = true), forward.cards.single().userIntent)
    }

    @Test
    fun preservesOlderConflictingSourcePrivacyAndIntentInTheStaleFallback() {
        val first =
            card(
                id = "mail",
                chronology = LauncherCardChronology(updatedAtEpochMillis = 20L),
                content = LauncherCardContent.Text(title = "Personal mail"),
                presentation =
                    CardPresentation(
                        sourcePackageName = "com.riffle.mail.personal",
                        supportedActions = setOf(LauncherCardAction.OPEN),
                    ),
            )
        val second =
            card(
                id = "mail",
                chronology = LauncherCardChronology(updatedAtEpochMillis = 10L),
                privacy = LauncherCardPrivacy.REDACTED,
                content = LauncherCardContent.Text(title = "Work mail"),
                userIntent = LauncherCardUserIntent(isPinned = true, isFavourite = true),
                presentation =
                    CardPresentation(
                        sourcePackageName = "com.riffle.mail.work",
                        supportedActions = setOf(LauncherCardAction.DISMISS),
                    ),
            )
        val forward = planner.plan(listOf(first, second))
        val reversed = planner.plan(listOf(second, first))

        assertEquals(forward, reversed)
        assertEquals(LauncherCardState.STALE, forward.cards.single().state)
        assertNull(forward.cards.single().content)
        assertEquals(emptySet(), forward.cards.single().supportedActions)
        assertEquals(LauncherCardPrivacy.REDACTED, forward.cards.single().privacy)
        assertEquals(LauncherCardUserIntent(isPinned = true, isFavourite = true), forward.cards.single().userIntent)
    }

    @Test
    fun filtersConflictingSourcesWhenAnOlderSnapshotIsHidden() {
        val latest =
            card(
                id = "mail",
                chronology = LauncherCardChronology(updatedAtEpochMillis = 20L),
                presentation = CardPresentation(sourcePackageName = "com.riffle.mail.personal"),
            )
        val olderHidden =
            card(
                id = "mail",
                chronology = LauncherCardChronology(updatedAtEpochMillis = 10L),
                privacy = LauncherCardPrivacy.HIDDEN,
                userIntent = LauncherCardUserIntent(isPinned = true),
                presentation = CardPresentation(sourcePackageName = "com.riffle.mail.work"),
            )

        assertEquals(emptyList(), planner.plan(listOf(latest, olderHidden)).cards)
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
    fun filtersHiddenCardsAndTheirSourceContentBeforePresentation() {
        val collection =
            planner.plan(
                listOf(
                    card(
                        id = "hidden",
                        privacy = LauncherCardPrivacy.HIDDEN,
                        content = LauncherCardContent.Text(title = "Private title", body = "Private body"),
                    ),
                    card(id = "visible", content = LauncherCardContent.Text(title = "Visible title")),
                ),
            )

        assertEquals(listOf("visible"), collection.cards.map { card -> card.id.value })
        assertEquals(0, collection.omittedCardCount)
    }

    @Test
    fun capsHighVolumeCollectionsAndReportsOmittedCards() {
        val collection =
            planner.plan(
                (1..3).map { index ->
                    card(id = "card-$index", chronology = LauncherCardChronology(rankingScore = index))
                },
                maxCards = 2,
            )

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
        chronology: LauncherCardChronology = LauncherCardChronology(),
        state: LauncherCardState = LauncherCardState.READY,
        privacy: LauncherCardPrivacy = LauncherCardPrivacy.VISIBLE,
        content: LauncherCardContent? = null,
        userIntent: LauncherCardUserIntent = LauncherCardUserIntent(),
        presentation: CardPresentation = CardPresentation(),
    ): LauncherCard {
        return LauncherCard(
            id = LauncherCardId(id),
            sourceRef =
                LauncherCardSourceRef.App(
                    appIdentity(packageName = presentation.sourcePackageName ?: "com.riffle.$id"),
                ),
            state = state,
            privacy = privacy,
            chronology = chronology,
            content = content,
            userIntent = userIntent,
            supportedActions = presentation.supportedActions,
        )
    }

    private data class CardPresentation(
        val sourcePackageName: String? = null,
        val supportedActions: Set<LauncherCardAction> = emptySet(),
    )

    private fun appIdentity(packageName: String) =
        AppIdentity(
            packageName = AppPackageName(packageName),
            activityName = AppActivityName(".MainActivity"),
            profile = AppProfile.personal(),
        )
}
