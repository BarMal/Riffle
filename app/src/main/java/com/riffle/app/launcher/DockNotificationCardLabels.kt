package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory

internal fun dockNotificationCardLabel(card: DockNotificationCardState): String =
    card.app?.label ?: card.group.packageName.value.dockNotificationFallbackLabel()

private fun String.dockNotificationFallbackLabel(): String =
    substringAfterLast('.')
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .takeIf { parts -> parts.isNotEmpty() }
        ?.joinToString(separator = " ") { part ->
            part.lowercase().replaceFirstChar { character -> character.titlecase() }
        }
        ?: "App"

internal val NotificationAccessStatus.emptyNotificationOverviewLabel: String
    get() =
        when (this) {
            NotificationAccessStatus.GRANTED -> "No active notifications"
            NotificationAccessStatus.NOT_GRANTED -> "Notification access is not allowed"
            NotificationAccessStatus.REVOKED -> "Notification access was revoked"
            NotificationAccessStatus.UNKNOWN -> "Notification access has not been checked"
        }

internal val NotificationAccessStatus.emptyNotificationOverviewActionLabel: String?
    get() =
        when (this) {
            NotificationAccessStatus.GRANTED -> null
            NotificationAccessStatus.NOT_GRANTED,
            NotificationAccessStatus.REVOKED,
            NotificationAccessStatus.UNKNOWN,
            -> "Open notification access"
        }

internal val NotificationCategory.label: String
    get() =
        when (this) {
            NotificationCategory.UNKNOWN -> "Unknown"
            NotificationCategory.MESSAGE -> "Message"
            NotificationCategory.EMAIL -> "Email"
            NotificationCategory.CALL -> "Call"
            NotificationCategory.MISSED_CALL -> "Missed call"
            NotificationCategory.ALARM -> "Alarm"
            NotificationCategory.EVENT -> "Event"
            NotificationCategory.REMINDER -> "Reminder"
            NotificationCategory.TRANSPORT -> "Transport"
            NotificationCategory.NAVIGATION -> "Navigation"
            NotificationCategory.LOCATION -> "Location"
            NotificationCategory.SOCIAL -> "Social"
            NotificationCategory.PROMOTION -> "Promotion"
            NotificationCategory.RECOMMENDATION -> "Recommendation"
            NotificationCategory.STATUS -> "Status"
            NotificationCategory.SYSTEM -> "System"
            NotificationCategory.SERVICE -> "Service"
            NotificationCategory.PROGRESS -> "Progress"
            NotificationCategory.ERROR -> "Error"
            NotificationCategory.STOPWATCH -> "Stopwatch"
            NotificationCategory.WORKOUT -> "Workout"
        }

internal fun InstalledApp.matches(group: AppNotificationGroup): Boolean =
    identity.packageName == group.packageName && identity.profile.id == group.profileId

internal val AppNotificationGroup.key: AppNotificationGroupKey
    get() = AppNotificationGroupKey(packageName = packageName, profileId = profileId)

internal val NotificationAgeBucket.label: String
    get() =
        when (this) {
            NotificationAgeBucket.NOW -> "Now"
            NotificationAgeBucket.RECENT -> "Recent"
            NotificationAgeBucket.TODAY -> "Today"
            NotificationAgeBucket.OLDER -> "Older"
        }
