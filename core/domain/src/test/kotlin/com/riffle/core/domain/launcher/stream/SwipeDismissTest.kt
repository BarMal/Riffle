package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.workspace.ItemTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwipeDismissTest {
    private val width = 400f
    private val density = 2f
    private val eps = 0.001f

    private fun notification(
        dismissible: Boolean = true,
        key: String? = "k",
        privacy: StreamPrivacy = StreamPrivacy.VISIBLE,
    ) = NotificationContent(
        id = StreamContentId("n"),
        app = StreamAppRef("pkg", "personal"),
        appLabel = "App",
        title = "Title",
        text = "Text",
        icon = null,
        image = null,
        postedAtEpochMillis = 0L,
        category = null,
        priority = NotificationPriority.DEFAULT,
        target = ItemTarget.None,
        actions =
            if (dismissible) {
                setOf(
                    NotificationAction.OPEN,
                    NotificationAction.DISMISS,
                )
            } else {
                setOf(NotificationAction.OPEN)
            },
        privacy = privacy,
        notificationKey = key,
    )

    private fun decide(
        offset: Float,
        velocity: Float = 0f,
    ) = SwipeDismiss.decide(offset, width, velocity, density)

    // Where the gesture exists

    @Test
    fun onlyANotificationThatOffersDismissCanBeSwiped() {
        assertTrue(SwipeDismiss.offers(notification()))
        // Ongoing and non-clearable: no Dismiss action, so no swipe.
        assertFalse(SwipeDismiss.offers(notification(dismissible = false)))
        assertFalse(SwipeDismiss.offers(notification(key = null)))
        assertFalse(SwipeDismiss.offers(notification(privacy = StreamPrivacy.REDACTED)))
    }

    @Test
    fun otherKindsAreNeverSwipedAway() {
        val media =
            MediaContent(
                id = StreamContentId("m"),
                app = StreamAppRef("pkg", "personal"),
                appLabel = null,
                title = "t",
                artist = null,
                artwork = null,
                icon = null,
                isPlaying = true,
                positionMillis = null,
                durationMillis = null,
                queue = emptyList(),
                target = ItemTarget.None,
                actions = MediaAction.entries.toSet(),
                postedAtEpochMillis = 0L,
                privacy = StreamPrivacy.VISIBLE,
            )
        assertFalse(SwipeDismiss.offers(media))
        assertFalse(SwipeDismiss.offers(TaskContent(StreamContentId("t"), "Task")))
    }

    @Test
    fun aHorizontalDragOnACardIsAlwaysTheCardsAndNeverThePagers() {
        val owner = SwipeDismiss.Owner.DISMISS
        assertEquals(owner, SwipeDismiss.ownerOf(onCard = true, notification(), isFront = true))
        // A strip, a non-clearable notification and a card that is no notification resist: they do not page.
        assertEquals(SwipeDismiss.Owner.RESIST, SwipeDismiss.ownerOf(onCard = true, notification(), isFront = false))
        assertEquals(
            SwipeDismiss.Owner.RESIST,
            SwipeDismiss.ownerOf(onCard = true, notification(dismissible = false), isFront = true),
        )
        assertEquals(SwipeDismiss.Owner.RESIST, SwipeDismiss.ownerOf(onCard = true, null, isFront = true))
        assertEquals(SwipeDismiss.Owner.RESIST, SwipeDismiss.ownerOf(onCard = true, null, isFront = false))
    }

    @Test
    fun onlyAnEmptyRoomPagesWhateverTheFrontCardIs() {
        assertEquals(SwipeDismiss.Owner.PAGER, SwipeDismiss.ownerOf(onCard = false, notification(), isFront = true))
        assertEquals(SwipeDismiss.Owner.PAGER, SwipeDismiss.ownerOf(onCard = false, null, isFront = false))
    }

    // Resisting

    @Test
    fun aCardThatResistsFollowsAtFirstAndNeverGivesMoreThanAFewDp() {
        val max = SwipeDismiss.RESIST_MAX_DP * density
        assertEquals(0f, SwipeDismiss.resist(0f, density), eps)
        // Near zero it follows the finger almost one to one.
        assertTrue(SwipeDismiss.resist(2f, density) > 1.5f)
        // Monotonic, odd, and bounded by the maximum however far the finger goes.
        var last = 0f
        for (raw in listOf(5f, 20f, 80f, 300f, 2_000f, 100_000f)) {
            val shown = SwipeDismiss.resist(raw, density)
            assertTrue(shown > last && shown < max)
            assertEquals(-shown, SwipeDismiss.resist(-raw, density), eps)
            last = shown
        }
        assertTrue(SwipeDismiss.RESIST_MAX_DP in 6f..16f, "a few dp: ${SwipeDismiss.RESIST_MAX_DP}")
    }

    @Test
    fun unresistInvertsResistSoAFingerThatTakesTheCardAgainDoesNotJump() {
        for (raw in listOf(-90f, -10f, 0f, 4f, 30f, 200f)) {
            val shown = SwipeDismiss.resist(raw, density)
            assertEquals(raw, SwipeDismiss.unresist(shown, density), 0.05f + kotlin.math.abs(raw) * 0.001f)
        }
        assertTrue(SwipeDismiss.unresist(SwipeDismiss.RESIST_MAX_DP * density * 2f, density).isFinite())
    }

    // Looking different

    @Test
    fun theCueAppearsOnlyPastTheThresholdAndTheLiftComesInWithTravel() {
        assertFalse(SwipeDismiss.isArmed(width * 0.39f, width))
        assertTrue(SwipeDismiss.isArmed(width * 0.4f, width))
        assertTrue(SwipeDismiss.isArmed(-width * 0.5f, width))
        assertEquals(0f, SwipeDismiss.cueAlpha(width * 0.39f, width), eps)
        assertEquals(0f, SwipeDismiss.cueAlpha(width * 0.4f, width), eps)
        assertTrue(SwipeDismiss.cueAlpha(width * 0.43f, width) in 0.1f..0.9f)
        assertEquals(1f, SwipeDismiss.cueAlpha(-width * 0.6f, width), eps)
        assertEquals(1f, SwipeDismiss.scale(0f, density), eps)
        assertEquals(SwipeDismiss.LIFT_SCALE, SwipeDismiss.scale(width, density), eps)
        assertTrue(SwipeDismiss.scale(10f, density) in 1.001f..SwipeDismiss.LIFT_SCALE)
    }

    // The release

    @Test
    fun farEnoughDismissesInTheDirectionItWasTakenEitherWay() {
        assertEquals(SwipeDismiss.Outcome.DISMISS, decide(width * 0.41f))
        assertEquals(SwipeDismiss.Outcome.DISMISS, decide(-width * 0.41f))
    }

    @Test
    fun notFarEnoughAndNotFastSettlesBack() {
        assertEquals(SwipeDismiss.Outcome.SETTLE, decide(width * 0.39f))
        assertEquals(SwipeDismiss.Outcome.SETTLE, decide(-width * 0.2f, velocity = -300f))
        assertEquals(SwipeDismiss.Outcome.SETTLE, decide(0f))
    }

    @Test
    fun aFastFlingDismissesWhateverTheDistanceOnceTheCardHasMovedALittle() {
        val fast = SwipeDismiss.FLING_VELOCITY_DP_PER_SECOND * density * 1.2f
        assertEquals(SwipeDismiss.Outcome.DISMISS, decide(width * 0.15f, velocity = fast))
        assertEquals(SwipeDismiss.Outcome.DISMISS, decide(-width * 0.15f, velocity = -fast))
        // A flick that barely moved the card is not a dismissal.
        assertEquals(SwipeDismiss.Outcome.SETTLE, decide(10f * density, velocity = fast))
    }

    @Test
    fun aFlickBackTowardWhereItStartedSettlesEvenWhenFarOrFast() {
        val fast = SwipeDismiss.FLING_VELOCITY_DP_PER_SECOND * density * 2f
        assertEquals(SwipeDismiss.Outcome.SETTLE, decide(width * 0.6f, velocity = -fast))
        assertEquals(SwipeDismiss.Outcome.SETTLE, decide(-width * 0.2f, velocity = fast))
    }

    @Test
    fun aSlowDragIsNotAFling() {
        val slow = SwipeDismiss.FLING_VELOCITY_DP_PER_SECOND * density * 0.5f
        assertEquals(SwipeDismiss.Outcome.SETTLE, decide(width * 0.3f, velocity = slow))
    }

    // How it looks

    @Test
    fun theCardFadesSlightlyAsItGoesAndNeverBelowItsMinimum() {
        assertEquals(1f, SwipeDismiss.alpha(0f, width), eps)
        assertEquals(0.75f, SwipeDismiss.alpha(width / 2f, width), eps)
        assertEquals(SwipeDismiss.MIN_ALPHA, SwipeDismiss.alpha(width, width), eps)
        assertEquals(SwipeDismiss.MIN_ALPHA, SwipeDismiss.alpha(-width * 3f, width), eps)
        assertEquals(1f, SwipeDismiss.alpha(100f, 0f), eps)
    }

    @Test
    fun aCardSentOffEndsPastTheEdgeItWasTakenToward() {
        assertEquals(width * SwipeDismiss.FLY_OFF_WIDTHS, SwipeDismiss.flyOffTarget(120f, 0f, width), eps)
        assertEquals(-width * SwipeDismiss.FLY_OFF_WIDTHS, SwipeDismiss.flyOffTarget(-120f, 900f, width), eps)
        assertEquals(-width * SwipeDismiss.FLY_OFF_WIDTHS, SwipeDismiss.flyOffTarget(0f, -900f, width), eps)
    }
}
