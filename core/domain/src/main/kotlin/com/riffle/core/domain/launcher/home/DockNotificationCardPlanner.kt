package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory

class DockNotificationCardPlanner {
    fun plan(
        groups: List<AppNotificationGroup>,
        notificationAccessStatus: NotificationAccessStatus,
        maxCards: Int = DEFAULT_MAX_DOCK_NOTIFICATION_CARDS,
    ): DockNotificationCardDeckState =
        when (notificationAccessStatus) {
            NotificationAccessStatus.UNKNOWN ->
                DockNotificationCardDeckState.PermissionFallback(DockNotificationPermissionFallbackReason.NOT_CHECKED)
            NotificationAccessStatus.NOT_GRANTED ->
                DockNotificationCardDeckState.PermissionFallback(DockNotificationPermissionFallbackReason.DENIED)
            NotificationAccessStatus.REVOKED ->
                DockNotificationCardDeckState.PermissionFallback(DockNotificationPermissionFallbackReason.REVOKED)
            NotificationAccessStatus.GRANTED ->
                contentState(groups = groups, maxCards = maxCards)
        }

    private fun contentState(
        groups: List<AppNotificationGroup>,
        maxCards: Int,
    ): DockNotificationCardDeckState {
        if (groups.isEmpty() || maxCards <= 0) return DockNotificationCardDeckState.Hidden

        return DockNotificationCardDeckState.Content(
            cards =
                groups
                    .take(maxCards)
                    .map { group -> group.toDockNotificationCard() },
        )
    }
}

sealed interface DockNotificationCardDeckState {
    data object Hidden : DockNotificationCardDeckState

    data class PermissionFallback(
        val reason: DockNotificationPermissionFallbackReason,
    ) : DockNotificationCardDeckState

    data class Content(
        val cards: List<DockNotificationCardModel>,
    ) : DockNotificationCardDeckState
}

data class DockNotificationCardModel(
    val key: AppNotificationGroupKey,
    val count: Int,
    val clearableCount: Int,
    val latestCategory: NotificationCategory,
    val latestAgeBucket: NotificationAgeBucket,
)

enum class DockNotificationPermissionFallbackReason {
    NOT_CHECKED,
    DENIED,
    REVOKED,
}

const val DEFAULT_MAX_DOCK_NOTIFICATION_CARDS = 3

private fun AppNotificationGroup.toDockNotificationCard(): DockNotificationCardModel =
    DockNotificationCardModel(
        key = AppNotificationGroupKey(packageName = packageName, profileId = profileId),
        count = count,
        clearableCount = clearableCount,
        latestCategory = latestCategory,
        latestAgeBucket = latestAgeBucket,
    )
