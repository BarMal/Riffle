package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey

internal fun List<AppNotificationGroup>.notificationCountFor(identity: AppIdentity): Int =
    firstOrNull { group ->
        AppNotificationGroupKey(
            packageName = identity.packageName,
            profileId = identity.profile.id,
        ) == AppNotificationGroupKey(group.packageName, group.profileId)
    }?.count ?: 0

internal fun List<AppNotificationGroup>.notificationCountFor(item: LauncherItem): Int =
    when (item) {
        is AppShortcutItem -> notificationCountFor(item.appIdentity)
        is FolderItem -> item.items.sumOf { shortcut -> notificationCountFor(shortcut.appIdentity) }
        is WidgetItem -> 0
    }
