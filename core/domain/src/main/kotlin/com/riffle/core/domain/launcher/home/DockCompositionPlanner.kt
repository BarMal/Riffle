package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

/**
 * Composes the dock from its two halves: the static items a user pinned there, and a dynamic
 * notification section beside them.
 *
 * The two halves are deliberately not independent. An app that is already pinned to the dock is
 * *already on screen*, and its own icon already carries a notification badge, so repeating it as a
 * notification entry shows the same app twice in one strip -- which is exactly what the separate
 * side rail did. Anything pinned therefore wins the static side and is excluded from the dynamic
 * one; the notification section is only ever for apps the dock is not otherwise showing.
 *
 * This owns only the composition and that exclusion. Permission fallbacks, the entry cap and the
 * per-app grouping all stay with [DockNotificationCardPlanner], which this delegates to, so there is
 * one place deciding what a notification entry looks like regardless of which surface renders it.
 */
class DockCompositionPlanner(
    private val notificationCardPlanner: DockNotificationCardPlanner = DockNotificationCardPlanner(),
) {
    fun plan(
        dock: DockModel,
        groups: List<AppNotificationGroup>,
        notificationAccessStatus: NotificationAccessStatus,
        maxNotificationEntries: Int = DEFAULT_MAX_DOCK_NOTIFICATION_CARDS,
    ): DockComposition {
        if (!dock.isEnabled) return DockComposition.EMPTY
        // The section is opt-in, and asking for no dock notifications is a decision -- not a reason
        // to fall back to a permission prompt for access the surface would never use.
        val notifications =
            if (dock.showNotificationCards) {
                notificationCardPlanner.plan(
                    groups = groups.filterNot { group -> group.isPinnedTo(dock) },
                    notificationAccessStatus = notificationAccessStatus,
                    maxCards = maxNotificationEntries,
                )
            } else {
                DockNotificationCardDeckState.Hidden
            }
        return DockComposition(staticItems = dock.items, notifications = notifications)
    }
}

/**
 * One dock's rendered content: what is pinned, and whichever notification entries survived being
 * de-duplicated against it.
 */
data class DockComposition(
    val staticItems: List<LauncherItem>,
    val notifications: DockNotificationCardDeckState,
) {
    /** Entries the dynamic section will actually draw, empty for every non-content state. */
    val notificationEntries: List<DockNotificationCardModel>
        get() = (notifications as? DockNotificationCardDeckState.Content)?.cards.orEmpty()

    companion object {
        val EMPTY = DockComposition(staticItems = emptyList(), notifications = DockNotificationCardDeckState.Hidden)
    }
}

/**
 * Whether this group's app is pinned directly to [dock], and so already visible on its static side.
 *
 * Apps inside a pinned *folder* deliberately do not count. The exclusion exists to stop the same
 * icon appearing twice in one strip, and a folder shows its own icon rather than its contents' --
 * so a notification for an app buried in one is not a duplicate of anything on screen, and is
 * arguably the case where surfacing it separately helps most.
 */
private fun AppNotificationGroup.isPinnedTo(dock: DockModel): Boolean {
    val key = AppNotificationGroupKey(packageName = packageName, profileId = profileId)
    return dock.items.any { item ->
        item is AppShortcutItem &&
            AppNotificationGroupKey(
                packageName = item.appIdentity.packageName,
                profileId = item.appIdentity.profile.id,
            ) == key
    }
}
