package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationPriority

internal fun dockNotificationCardLabel(card: DockNotificationCardState): String =
    card.app?.label ?: card.group.packageName.value.dockNotificationFallbackLabel()

internal fun notificationOverviewGroupLabel(
    app: InstalledApp?,
    group: AppNotificationGroup,
): String = app?.label ?: group.packageName.value.dockNotificationFallbackLabel()

internal fun AppNotificationGroup.notificationOverviewMetadataLabel(label: String): String =
    "$label - ${latestCategory.label} - ${highestPriority.label} - $clearableLabel"

internal val AppNotificationGroup.clearableLabel: String
    get() =
        clearableCount.let { clearable ->
            when {
                clearable == 0 -> "Pinned"
                clearable == count -> "Clearable $count/$count"
                else -> "Clearable $clearable/$count"
            }
        }

internal val NotificationPriority.label: String
    get() =
        when (this) {
            NotificationPriority.UNKNOWN -> "Priority unknown"
            NotificationPriority.MIN -> "Min priority"
            NotificationPriority.LOW -> "Low priority"
            NotificationPriority.DEFAULT -> "Default priority"
            NotificationPriority.HIGH -> "High priority"
            NotificationPriority.MAX -> "Max priority"
        }

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
