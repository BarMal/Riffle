package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.sources.MediaControl
import com.riffle.core.domain.launcher.workspace.sources.MediaItemExt
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionItems
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionQueueEntry
import com.riffle.core.domain.launcher.workspace.sources.RecentPlayerItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One stack per player (ADR 0003, Amendment 16): the player's card in front, the tracks after the one playing as
 * cards behind it in queue order, and the same stack while its tracks change.
 */
class MediaQueueCardsTest {
    private fun queue(vararg ids: Long) =
        ids.map { MediaSessionQueueEntry("Track $it", "Artist $it", queueId = it, hasArtwork = it % 2 == 0L) }

    private fun session(
        pkg: String,
        vararg upcoming: Long,
    ) = MediaSessionInfo(
        packageName = pkg,
        title = "Playing in $pkg",
        isPlaying = true,
        controls = setOf(MediaControl.PAUSE, MediaControl.NEXT),
        queue = queue(*upcoming),
    )

    private fun inputs(
        sessions: List<MediaSessionInfo>,
        players: List<String> = sessions.map { it.packageName },
    ): StreamSectionInputs {
        val items =
            players.mapIndexed { i, pkg ->
                notificationItem("k$pkg", pkg = pkg, app = pkg.uppercase(), posted = 100L - i, source = SourceIds.MEDIA)
            }
        return StreamSectionInputs(
            items = mapOf(SourceIds.MEDIA to SourceState.Ready(MediaSessionItems.enrich(items, sessions))),
        )
    }

    private fun now(
        inputs: StreamSectionInputs,
        options: NowOptions = NowOptions(),
    ) = StreamSectionEvaluator.evaluate(
        StreamSection.NOW,
        inputs,
        StreamContext(NOW, options = StreamSectionOptions(now = options)),
    ) as StreamSectionResult.Ready

    @Test
    fun `each active player is a stack of its card then its queue in queue order`() {
        val result = now(inputs(listOf(session("alpha", 11, 12, 13), session("beta"))))

        assertEquals(listOf("player:alpha:personal", "player:beta:personal"), result.groups.map { it.key })
        val alpha = result.groups.first().items.map { it as MediaContent }
        assertNull(alpha.first().queueItem)
        assertEquals(listOf(11L, 12L, 13L), alpha.drop(1).map { it.queueItem?.queueId })
        assertEquals(listOf(0, 1, 2), alpha.drop(1).map { it.queueItem?.position })
        assertEquals(1, result.groups.last().items.size)
    }

    @Test
    fun `a queue card says its track, its artist, its app and offers one typed play action`() {
        val card = now(inputs(listOf(session("alpha", 11)))).groups.single().items.last() as MediaContent

        assertEquals("Track 11", card.title)
        assertEquals("Artist 11", card.artist)
        assertEquals("ALPHA", card.appLabel)
        assertNull(card.artwork)
        assertEquals(setOf(MediaAction.PLAY_QUEUE_ITEM), card.actions)
        assertTrue(StreamActionPolicy.offers(card, StreamAction.PlayQueueItem(11)))
        assertFalse(StreamActionPolicy.offers(card, StreamAction.PlayQueueItem(12)), "another track's card")
        assertFalse(StreamActionPolicy.offers(card, StreamAction.Open), "play is its own action, never Open")
        assertFalse(StreamActionPolicy.offers(card, StreamAction.Play))
        assertFalse(SwipeDismiss.offers(card))
        assertFalse(StreamCardActions.opensInPlace(card))
        assertEquals(emptyList(), StreamCardActions.forContent(card))
    }

    @Test
    fun `a player that cannot skip to a track still shows its queue as read-only cards`() {
        val readOnly = session("alpha", 11, 12, 13).copy(queuePlayable = false)
        val cards = now(inputs(listOf(readOnly))).groups.single().items.drop(1).map { it as MediaContent }

        assertEquals(listOf(11L, 12L, 13L), cards.map { it.queueItem?.queueId })
        cards.forEach { card ->
            assertEquals(emptySet(), card.actions)
            assertEquals("Track ${card.queueItem?.queueId}", card.title)
            assertFalse(StreamActionPolicy.offers(card, StreamAction.PlayQueueItem(card.queueItem!!.queueId)))
            assertFalse(StreamActionPolicy.offers(card, StreamAction.Open))
            assertFalse(StreamCardActions.opensInPlace(card))
        }
    }

    @Test
    fun `read-only cards keep the cap the order and the privacy rules`() {
        val many = session("alpha", *(1L..15L).toList().toLongArray()).copy(queuePlayable = false)
        val shown = now(inputs(listOf(many))).groups.single().items.drop(1)
        assertEquals((1L..10L).toList(), shown.map { (it as MediaContent).queueItem?.queueId })

        val off = now(inputs(listOf(many)), NowOptions(showUpNext = false))
        assertEquals(1, off.groups.single().items.size)
    }

    @Test
    fun `a queue entry with artwork carries the handle the platform loader resolves`() {
        val withArt = now(inputs(listOf(session("alpha", 12)))).groups.single().items.last() as MediaContent
        assertEquals(MediaItemExt.queueArtwork("alpha", 12), withArt.artwork)
    }

    @Test
    fun `at most ten tracks are cards and the order is the queue's`() {
        val result = now(inputs(listOf(session("alpha", *(1L..15L).toList().toLongArray()))))
        val cards = result.groups.single().items.drop(1).map { (it as MediaContent).queueItem?.queueId }
        assertEquals((1L..10L).toList(), cards)
    }

    @Test
    fun `show up next off leaves a single card per player`() {
        val result = now(inputs(listOf(session("alpha", 11, 12))), NowOptions(showUpNext = false))
        assertEquals(1, result.groups.single().items.size)
    }

    @Test
    fun `a player with no queue is a single card`() {
        assertEquals(1, now(inputs(listOf(session("alpha")))).groups.single().items.size)
    }

    @Test
    fun `the stack keeps its key and the cards that remain keep theirs when the track advances`() {
        val before = now(inputs(listOf(session("alpha", 11, 12, 13))))
        val after = now(inputs(listOf(session("alpha", 12, 13, 14))))

        assertEquals(before.groups.single().key, after.groups.single().key)
        val beforeIds = before.groups.single().items.map { it.id.value }
        val afterIds = after.groups.single().items.map { it.id.value }
        assertEquals(beforeIds.first(), afterIds.first(), "the player card is the same card")
        assertEquals(beforeIds.drop(2), afterIds.drop(1).dropLast(1), "the played track's card leaves, the rest stay")
        assertTrue(afterIds.last().endsWith(":q:14"))
        // The page the stack is on is the same page.
        val key = { r: StreamSectionResult.Ready ->
            nowPages(r).map { it.key }
        }
        assertEquals(key(before), key(after))
    }

    @Test
    fun `the stack's position does not depend on the other players coming and going`() {
        val both = now(inputs(listOf(session("alpha", 11), session("beta", 21))))
        val alone = now(inputs(listOf(session("alpha", 11))))
        assertEquals(
            both.groups.first { it.key == "player:alpha:personal" }.items.map { it.id },
            alone.groups.single().items.map { it.id },
        )
    }

    @Test
    fun `recent players stay together as one stack after the active players`() {
        val items =
            MediaSessionItems.enrich(
                listOf(notificationItem("ka", pkg = "alpha", app = "ALPHA", source = SourceIds.MEDIA)),
                listOf(session("alpha", 11)),
            ) +
                RecentPlayerItems.build(
                    listOf(
                        RecentPlayerSource(RecentPlayer("gamma", "personal", 50L), "Old", null),
                        RecentPlayerSource(RecentPlayer("delta", "personal", 40L), null, null),
                    ),
                    appLabel = { it.uppercase() },
                    canResume = { true },
                )
        val input = StreamSectionInputs(items = mapOf(SourceIds.MEDIA to SourceState.Ready(items)))
        val result = now(input)

        assertEquals(listOf("player:alpha:personal", StreamGroupKeys.NOW), result.groups.map { it.key })
        assertTrue(result.groups.last().items.all { (it as MediaContent).source })
        val pages = nowPages(result)
        assertEquals(listOf("now/player:alpha:personal", "now"), pages.map { it.key })
        assertEquals("ALPHA", pages.first().label)
    }

    @Test
    fun `with nothing playing there is one now page with the section's own key`() {
        val result = StreamSectionResult.Ready(listOf(StreamGroup(StreamGroupKeys.NOW, null, listOf(playerCard()))))
        val pages = nowPages(result)
        assertEquals(listOf("now"), pages.map { it.key })
        assertNotNull(pages.single().lead)
    }

    @Test
    fun `a sensitive player has no queue cards`() {
        val sensitive =
            notificationItem("k", pkg = "alpha", source = SourceIds.MEDIA, privacy = ItemPrivacy.SENSITIVE)
        val items = MediaSessionItems.enrich(listOf(sensitive), listOf(session("alpha", 11)))
        assertEquals(1, items.size)
    }

    @Test
    fun `a second notification of the same app does not repeat the queue`() {
        val two =
            listOf(
                notificationItem("k1", pkg = "alpha", source = SourceIds.MEDIA),
                notificationItem("k2", pkg = "alpha", source = SourceIds.MEDIA),
            )
        val items = MediaSessionItems.enrich(two, listOf(session("alpha", 11, 12)))
        assertEquals(2 + 2, items.size)
        assertIs<MediaContent>(StreamContentMapper.map(items.last()))
    }

    private fun playerCard() =
        MediaContent(
            id = StreamContentId("media:personal:k"),
            app = StreamAppRef("alpha", "personal"),
            appLabel = "Alpha",
            title = "T",
            artist = null,
            artwork = null,
            icon = null,
            target = ItemTarget.App("alpha", "personal"),
            actions = setOf(MediaAction.OPEN),
            privacy = StreamPrivacy.VISIBLE,
        )

    private fun nowPages(result: StreamSectionResult.Ready) =
        StreamPagePlanner.plan(StreamLayout.DEFAULT, mapOf(StreamSection.NOW to result))
            .filter { it.section == StreamSection.NOW }
}
