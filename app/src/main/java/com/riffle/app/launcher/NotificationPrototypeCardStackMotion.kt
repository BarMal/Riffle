package com.riffle.app.launcher

import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.notifications.LauncherNotification

internal data class NotificationPrototypeCardStackMotion(
    val animationProfile: CardStackAnimationProfile,
    val reducedMotion: Boolean,
)

internal fun notificationPrototypeCardStackMotion(reducedMotion: Boolean): NotificationPrototypeCardStackMotion {
    return NotificationPrototypeCardStackMotion(
        animationProfile = CardStackAnimationProfile.CARD_FLIGHT,
        reducedMotion = reducedMotion,
    )
}

internal fun notificationOverviewScrollProgress(
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
): Float =
    if (firstVisibleItemSize <= 0) {
        0f
    } else {
        (firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize).coerceIn(0f, 1f)
    }

internal fun notificationOverviewNotificationTitle(
    notification: LauncherNotification,
    fallbackLabel: String,
): String = notification.title.ifBlank { fallbackLabel }
