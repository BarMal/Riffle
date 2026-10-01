package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.sources.toSourceAccess

/** The existing explicit user flow that can grant a source the access it needs. */
internal enum class SourceAccessRoute {
    /** `LauncherShellAction.RequestCalendarAccess`: rationale first, system dialog only on the user's tap. */
    CALENDAR,

    /** `LauncherShellAction.RequestNotificationAccess`: opens the system notification listener settings. */
    NOTIFICATION_ACCESS,

    /** The system Usage Access settings screen. */
    USAGE_ACCESS,

    /** The source needs no access the launcher can send the user to grant. */
    NONE,
}

/** The three explicit flows, supplied by the activity; [request] runs the one a source needs, if any. */
internal class SourceAccessLaunchers(
    private val calendar: () -> Unit,
    private val notificationAccess: () -> Unit,
    private val usageAccess: () -> Unit,
) {
    fun request(id: SourceId) {
        when (sourceAccessRouteFor(id)) {
            SourceAccessRoute.CALENDAR -> calendar()
            SourceAccessRoute.NOTIFICATION_ACCESS -> notificationAccess()
            SourceAccessRoute.USAGE_ACCESS -> usageAccess()
            SourceAccessRoute.NONE -> Unit
        }
    }
}

/** Which flow the editor's "Review access" button for [id] leads to. Choosing a route never requests anything. */
internal fun sourceAccessRouteFor(id: SourceId): SourceAccessRoute =
    when (id) {
        SourceIds.CALENDAR -> SourceAccessRoute.CALENDAR
        SourceIds.NOTIFICATIONS, SourceIds.MEDIA -> SourceAccessRoute.NOTIFICATION_ACCESS
        SourceIds.RECENT_APPS -> SourceAccessRoute.USAGE_ACCESS
        else -> SourceAccessRoute.NONE
    }

/**
 * What each gated source's access currently is, for the editor's source choices. Reads only; nothing here
 * prompts. Sources not listed (apps, shortcuts, RSS, search) need no permission and read as granted.
 */
internal fun sourceAccessMap(
    notificationAccess: NotificationAccessStatus,
    calendarAccess: SourceAccess,
    recentAppsAccess: SourceAccess,
): Map<SourceId, SourceAccess> {
    val notifications = notificationAccess.toSourceAccess()
    return mapOf(
        SourceIds.NOTIFICATIONS to notifications,
        SourceIds.MEDIA to notifications,
        SourceIds.CALENDAR to calendarAccess,
        SourceIds.RECENT_APPS to recentAppsAccess,
    )
}
