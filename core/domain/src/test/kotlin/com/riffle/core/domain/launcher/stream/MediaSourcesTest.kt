package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import com.riffle.core.domain.launcher.workspace.sources.MediaItemExt
import com.riffle.core.domain.launcher.workspace.sources.RecentPlayerItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The recent players as media content: their items, their card, what they offer and where they stand in Now. */
class MediaSourcesTest {
    private fun source(
        pkg: String,
        playedAt: Long,
        title: String? = null,
        artist: String? = null,
    ) = RecentPlayerSource(RecentPlayer(pkg, "personal", playedAt), title, artist)

    private fun items(
        vararg sources: RecentPlayerSource,
        resumable: Set<String> = emptySet(),
    ): List<Item> =
        RecentPlayerItems.build(
            sources.toList(),
            appLabel = { pkg -> pkg.replaceFirstChar(Char::uppercase) },
            canResume = { it in resumable },
        )

    private fun now(
        vararg media: Item,
        context: StreamContext = StreamContext(nowEpochMillis = NOW),
    ): List<MediaContent> =
        (
            StreamSectionEvaluator.evaluate(
                StreamSection.NOW,
                StreamSectionInputs(mapOf(SourceIds.MEDIA to SourceState.Ready(media.toList()))),
                context,
            ) as StreamSectionResult.Ready
        ).items.map { it as MediaContent }

    private fun session(
        key: String,
        pkg: String = key,
        playing: Boolean?,
        posted: Long,
    ): Item =
        notificationItem(key, pkg = pkg, posted = posted, source = SourceIds.MEDIA).let { item ->
            if (playing == null) {
                item
            } else {
                item.copy(
                    ext =
                        item.ext +
                            mapOf(
                                MediaItemExt.PLAYING to
                                    com.riffle.core.domain.launcher.workspace.ItemExtValue.Flag(playing),
                            ),
                )
            }
        }

    @Test
    fun aSourceIsMediaContentWithItsAppsNameItsLastTextAndOpenDismissAndMaybePlay() {
        val built = items(source("music", 500, "Opening theme", "Band"), resumable = setOf("music"))
        val card = StreamContentMapper.map(built.single()) as MediaContent

        assertTrue(card.source)
        assertEquals("Music", card.appLabel)
        assertEquals("Opening theme", card.title)
        assertEquals("Band", card.artist)
        assertEquals(500L, card.postedAtEpochMillis)
        assertEquals(ItemTarget.App("music", "personal"), card.target)
        assertEquals(setOf(MediaAction.OPEN, MediaAction.PLAY, MediaAction.DISMISS), card.actions)
        assertNull(card.artwork)
        assertNull(card.queueItem)
    }

    @Test
    fun anAppThatCannotBeResumedOffersOpenAndDismissOnly() {
        val card = StreamContentMapper.map(items(source("radio", 5)).single()) as MediaContent
        assertEquals(setOf(MediaAction.OPEN, MediaAction.DISMISS), card.actions)
        assertNull(card.title)
    }

    @Test
    fun theTextFollowsTheContentLevelAndADismissStaysAvailable() {
        val level = SourceContentLevels().with(SourceIds.MEDIA, SourceContentLevel.HIDE_TEXT)
        val card = StreamContentMapper.map(items(source("music", 5, "Song")).single(), level) as MediaContent
        assertNull(card.title)
        assertEquals(StreamPrivacy.REDACTED, card.privacy)
        assertTrue(StreamActionPolicy.offers(card, StreamAction.Dismiss))
        assertTrue(StreamActionPolicy.offers(card, StreamAction.Open))
    }

    @Test
    fun aSourceSwipesAwayAndAnActiveSessionStillResists() {
        val card = StreamContentMapper.map(items(source("music", 5)).single()) as MediaContent
        assertTrue(SwipeDismiss.offers(card))
        assertEquals(SwipeDismiss.Owner.DISMISS, SwipeDismiss.ownerOf(onCard = true, card, isFront = true))
        assertEquals(SwipeDismiss.Owner.RESIST, SwipeDismiss.ownerOf(onCard = true, card, isFront = false))

        val active = StreamContentMapper.map(session("a", playing = true, posted = 1)) as MediaContent
        assertFalse(SwipeDismiss.offers(active))
        assertFalse(StreamActionPolicy.offers(active, StreamAction.Dismiss))
    }

    @Test
    fun aSourceCardOpensTheAppOnATapAndHasNoOpenCardActions() {
        val card = StreamContentMapper.map(items(source("music", 5)).single()) as MediaContent
        assertFalse(StreamCardActions.opensInPlace(card))
        assertTrue(StreamCardActions.forContent(card).isEmpty())
    }

    @Test
    fun nowIsTheActiveSessionsFirstPlayingBeforePausedThenTheSourcesNewestFirst() {
        val result =
            now(
                session("paused-new", playing = false, posted = 90),
                session("playing-old", playing = true, posted = 10),
                *items(source("podcasts", 200), source("radio", 300), source("music", 100)).toTypedArray(),
            )
        assertEquals(
            listOf("media:personal:playing-old", "media:personal:paused-new"),
            result.filter { !it.source }.map { it.id.value },
        )
        assertEquals(
            listOf("media:recent:radio:personal", "media:recent:podcasts:personal", "media:recent:music:personal"),
            result.filter { it.source }.map { it.id.value },
        )
        // Active first, then the sources.
        assertEquals(listOf(false, false, true, true, true), result.map { it.source })
    }

    @Test
    fun aSourceWhoseAppHasASessionIsNotShownBesideIt() {
        val result =
            now(
                session("s1", pkg = "music", playing = false, posted = 5),
                *items(source("music", 999), source("radio", 100)).toTypedArray(),
            )
        assertEquals(listOf("media:personal:s1", "media:recent:radio:personal"), result.map { it.id.value })
    }

    @Test
    fun noSessionsAndNoSourcesIsEmpty() {
        val result =
            StreamSectionEvaluator.evaluate(
                StreamSection.NOW,
                StreamSectionInputs(mapOf(SourceIds.MEDIA to SourceState.Ready(emptyList()))),
                StreamContext(nowEpochMillis = NOW),
            )
        assertEquals(StreamSectionResult.Empty, result)
    }

    @Test
    fun turningRecentPlayersOffLeavesTheActiveSessionsAndTheEmptyState() {
        val off = StreamContext(NOW, StreamSectionOptions(now = NowOptions(recentPlayers = false)))
        val built = items(source("music", 5), source("radio", 6))
        assertEquals(
            listOf("media:personal:a"),
            now(session("a", playing = true, posted = 1), *built.toTypedArray(), context = off).map { it.id.value },
        )
        val result =
            StreamSectionEvaluator.evaluate(
                StreamSection.NOW,
                StreamSectionInputs(mapOf(SourceIds.MEDIA to SourceState.Ready(built))),
                off,
            )
        assertEquals(StreamSectionResult.Empty, result)
    }

    @Test
    fun aHideRuleForTheAppRemovesItsSourceCardToo() {
        val rule =
            SourceExclusionRule(ExclusionRuleId("r"), SourceIds.MEDIA, ExclusionMatcher.App("music", "personal"))
        val context = StreamContext(NOW, exclusions = ExclusionRuleSet(listOf(rule)))
        val built = items(source("music", 5), source("radio", 6))
        assertEquals(
            listOf("media:recent:radio:personal"),
            now(*built.toTypedArray(), context = context).map { it.id.value },
        )
    }

    @Test
    fun theMostActiveSessionsOptionStillLimitsThePlayerCardsButNotTheSources() {
        val two = StreamContext(NOW, StreamSectionOptions(now = NowOptions(maxActive = 1)))
        val result =
            now(
                session("a", playing = true, posted = 1),
                session("b", playing = true, posted = 2),
                *items(source("radio", 6)).toTypedArray(),
                context = two,
            )
        assertEquals(listOf("media:personal:b", "media:recent:radio:personal"), result.map { it.id.value })
    }
}
