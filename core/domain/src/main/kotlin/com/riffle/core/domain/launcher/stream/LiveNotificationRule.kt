package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.notifications.NotificationCategory

/**
 * Which notifications are "live": something that is going on now and that its app keeps up to date (a trip, a
 * download, a timer), whose buttons are the reason to look at it ("Next", "End trip", "Pause"). A live notification
 * shows its own action buttons on the browse card too, so they can be tapped without opening the card.
 *
 * It is live when it is ongoing and it either draws itself with a layout of its own, shows progress, or says it is
 * navigation, transport or progress. An ongoing notification with none of those (a "connected" status, a foreground
 * service's note) is not, and neither is anything that can be swiped away.
 */
object LiveNotificationRule {
    data class Facts(
        val ongoing: Boolean,
        /** The notification has custom views (a compact or an expanded layout of its own). */
        val customViews: Boolean = false,
        /** The notification shows a progress bar (a maximum, or indeterminate). */
        val progress: Boolean = false,
        val category: NotificationCategory = NotificationCategory.UNKNOWN,
    )

    private val LIVE_CATEGORIES =
        setOf(NotificationCategory.NAVIGATION, NotificationCategory.TRANSPORT, NotificationCategory.PROGRESS)

    fun isLive(facts: Facts): Boolean =
        facts.ongoing && (facts.customViews || facts.progress || facts.category in LIVE_CATEGORIES)
}
