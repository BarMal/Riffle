package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamCardSizesTest {
    private val folded = StreamCardSizes.FOLDED
    private val unfolded = StreamCardSizes.UNFOLDED

    @Test
    fun theFoldedSizesAreWhatTheStackHasAlwaysDrawn() {
        assertEquals(StreamCardStackSpec.PITCH_DP, folded.pitchDp)
        assertEquals(StreamCardStackSpec.VISIBLE_PEEKS, folded.maxPeeks)
        assertEquals(StreamCardStackSpec.SCALE_STEP, folded.scaleStep)
        assertEquals(1f, folded.textScale)
        assertEquals(BrowseCardHeight.MAX_BODY_LINES, folded.maxBodyLines)
        assertEquals(BrowseCardHeight.MAX_THREAD_LINES, folded.maxThreadLines)
        assertEquals(168f, folded.cardHeightDp(168f, textDriven = true))
        assertEquals(264f, folded.cardHeightDp(264f, textDriven = false))
        assertEquals(StreamCardSizes.FOLDED, StreamCardSizes.forPosture(unfolded = false))
        assertEquals(StreamCardSizes.UNFOLDED, StreamCardSizes.forPosture(unfolded = true))
    }

    @Test
    fun foldedAlwaysShowsUpToThreeStripsWhateverTheRoom() {
        assertEquals(3, folded.peeksFor(count = 10, areaHeightDp = 900f, frontHeightDp = 168f, bottomInsetDp = 16f))
        assertEquals(3, folded.peeksFor(count = 10, areaHeightDp = 300f, frontHeightDp = 168f, bottomInsetDp = 16f))
        assertEquals(2, folded.peeksFor(count = 3, areaHeightDp = 900f, frontHeightDp = 168f, bottomInsetDp = 16f))
        assertEquals(0, folded.peeksFor(count = 1, areaHeightDp = 900f, frontHeightDp = 168f, bottomInsetDp = 16f))
    }

    @Test
    fun unfoldedShowsAsManyStripsAsFitUpToSixAndNeverFewerThanThree() {
        // 700 dp: 700 - 16 - 227 - 16 = 441 of room, a 64 dp strip each: six fit (384).
        assertEquals(6, unfolded.peeksFor(count = 20, areaHeightDp = 700f, frontHeightDp = 227f, bottomInsetDp = 16f))
        // 900 dp would fit more, but six is the cap.
        assertEquals(6, unfolded.peeksFor(count = 20, areaHeightDp = 900f, frontHeightDp = 227f, bottomInsetDp = 16f))
        // 512 dp (the least): 512 - 16 - 227 - 16 = 253: three fit (192).
        assertEquals(3, unfolded.peeksFor(count = 20, areaHeightDp = 512f, frontHeightDp = 227f, bottomInsetDp = 16f))
        // A short front card would let a fourth strip fit above it, but an open card then has less than the 240 dp it
        // needs: 512 - 8 - 16 - 240 = 248 holds three strips only.
        assertEquals(3, unfolded.peeksFor(count = 20, areaHeightDp = 512f, frontHeightDp = 120f, bottomInsetDp = 16f))
        // Taller, the open card has room for more.
        assertEquals(5, unfolded.peeksFor(count = 20, areaHeightDp = 600f, frontHeightDp = 120f, bottomInsetDp = 16f))
        // No room at all keeps the three that the least height always had.
        assertEquals(3, unfolded.peeksFor(count = 20, areaHeightDp = 200f, frontHeightDp = 227f, bottomInsetDp = 16f))
        // A short stack shows its cards only.
        assertEquals(2, unfolded.peeksFor(count = 3, areaHeightDp = 900f, frontHeightDp = 227f, bottomInsetDp = 16f))
        assertEquals(0, unfolded.peeksFor(count = 1, areaHeightDp = 900f, frontHeightDp = 227f, bottomInsetDp = 16f))
    }

    @Test
    fun unfoldedCardsAreBiggerStripsTallerAndTheTextLarger() {
        assertTrue(unfolded.pitchDp > folded.pitchDp)
        assertEquals(1.35f, unfolded.maxHeightFactor)
        assertEquals(6, unfolded.maxBodyLines)
        assertTrue(unfolded.maxThreadLines > folded.maxThreadLines)
        assertTrue(unfolded.textScale > 1f)
        assertEquals(168f * 1.35f, unfolded.cardHeightDp(168f, textDriven = true), 0.01f)
        assertTrue(unfolded.cardHeightDp(264f, textDriven = false) in 264f..264f * 1.35f)
        // The header still lies wholly in the strip.
        assertTrue(unfolded.pitchDp >= BrowseCardHeight.HEADER_DP)
    }

    @Test
    fun aNotificationCardReachesTheLargerMaximumWithMoreLines() {
        val line = BrowseCardHeight.LINE_DP * unfolded.textScale
        val six = BrowseCardHeight.notification(bodyDp = line * 6, sizes = unfolded)
        val four = BrowseCardHeight.notification(bodyDp = line * 4, sizes = unfolded)
        assertTrue(six > four)
        assertTrue(six <= BrowseCardHeight.MAX_DP * unfolded.maxHeightFactor)
        assertTrue(six > BrowseCardHeight.MAX_DP, "taller than the folded maximum")
        // Folded does not change: a body of six lines is held to the folded maximum.
        assertEquals(BrowseCardHeight.MAX_DP, BrowseCardHeight.notification(bodyDp = line * 6))
        assertEquals(
            BrowseCardHeight.MAX_WITH_ACTIONS_DP * unfolded.maxHeightFactor,
            BrowseCardHeight.notification(bodyDp = 600f, hasActions = true, sizes = unfolded),
        )
    }

    @Test
    fun theBrowseTextTakesMoreLinesAndMessagesUnfolded() {
        val text = notification()
        assertEquals(4, NotificationBrowseText.blocks(text).single().maxLines)
        assertEquals(6, NotificationBrowseText.blocks(text, unfolded).single().maxLines)
        val messages =
            List(6) { NotificationMessage(sender = "S$it", text = "m$it", timestampEpochMillis = it.toLong()) }
        val thread = notification(thread = messages)
        assertEquals(3, NotificationBrowseText.blocks(thread).size)
        assertEquals(4, NotificationBrowseText.blocks(thread, unfolded).size)
    }

    @Test
    fun aStackGeometryTakesItsPeeksAndPitchFromTheSizes() {
        val geometry =
            StackGeometry(
                heightsDp = List(10) { 200f },
                pitchDp = unfolded.pitchDp,
                visiblePeeks = 6,
                scaleStep = unfolded.scaleStep,
            )
        assertEquals(200f + 64f * 5f, geometry.topDp(5f))
        assertTrue(geometry.frame(6, 0f).isVisible)
        assertTrue(!geometry.frame(8, 0f).isVisible)
        assertEquals(1f - unfolded.scaleStep * 6f, geometry.frame(6, 0f).scale, 0.0001f)
        assertEquals(0..6, geometry.composedRange(0f, 10))
        // The folded default is three peeks.
        val plain = StackGeometry(heightsDp = List(10) { 200f })
        assertTrue(!plain.frame(4, 0f).isVisible)
        assertEquals(0..3, plain.composedRange(0f, 10))
    }

    @Test
    fun theOpenCardsStripsFollowThePeeks() {
        assertEquals(192f, OpenCardLayout.stripsPx(cardsBehind = 9, pitchPx = 64f, visiblePeeks = 3))
        assertEquals(384f, OpenCardLayout.stripsPx(cardsBehind = 9, pitchPx = 64f, visiblePeeks = 6))
        assertEquals(168f, OpenCardLayout.stripsPx(cardsBehind = 3, pitchPx = 56f))
    }

    private fun notification(thread: List<NotificationMessage> = emptyList()) =
        NotificationContent(
            id = StreamContentId("n"),
            app = StreamAppRef("pkg", "personal"),
            appLabel = "App",
            title = "Title",
            text = "Text",
            icon = null,
            image = null,
            postedAtEpochMillis = 0L,
            category = null,
            priority = com.riffle.core.domain.launcher.notifications.NotificationPriority.DEFAULT,
            target = com.riffle.core.domain.launcher.workspace.ItemTarget.None,
            actions = setOf(NotificationAction.OPEN),
            privacy = StreamPrivacy.VISIBLE,
            notificationKey = "k",
            thread = thread,
        )
}
