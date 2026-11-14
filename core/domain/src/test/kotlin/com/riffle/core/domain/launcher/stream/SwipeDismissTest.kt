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
    fun aHorizontalDragIsTheCardsOnlyOnTheFrontCardOfADismissibleNotification() {
        assertTrue(SwipeDismiss.claimsDrag(notification(), isFront = true))
        // A strip (not the front card), the empty room (no card), a non-clearable notification: the pager's.
        assertFalse(SwipeDismiss.claimsDrag(notification(), isFront = false))
        assertFalse(SwipeDismiss.claimsDrag(null, isFront = true))
        assertFalse(SwipeDismiss.claimsDrag(notification(dismissible = false), isFront = true))
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
