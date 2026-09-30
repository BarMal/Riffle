package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.notifications.NotificationHideRuleFilter
import com.riffle.core.domain.launcher.notifications.NotificationStaleFilter
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds

/** Everything the notification-backed mappers need; [notifications] are the currently active ones. */
data class NotificationItemInput(
    val notifications: List<LauncherNotification>,
    val nowEpochMillis: Long,
    val hideRules: List<NotificationHideRule> = emptyList(),
    val profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility> = emptyMap(),
    /** Display name of an app, by package; the package name itself is used when this returns null. */
    val appLabel: (AppPackageName) -> String? = { null },
    /** Profile types by id when known; the well-known ids (`personal`, `work`, `private`) resolve without it. */
    val profileTypes: Map<AppProfileId, AppProfileType> = emptyMap(),
)

/**
 * Maps active notifications to [Item]s for the Notifications and Media sources, applying the same
 * rules as the existing cards: user hide rules, the stale-clearable filter, then profile content
 * visibility (locked or unavailable profiles are dropped; quiet profiles keep only app-level fields and
 * are marked [ItemPrivacy.SENSITIVE] with their content withheld here, not only at lens projection).
 *
 * Notifications carry a media session go to [mediaItems] and never appear in [notificationItems].
 * Items are grouped by app: `groupKey` is `package:profile`, `groupLabel` the app name, in the
 * existing [AppNotificationGrouper] display order.
 */
class NotificationItemMapper(
    private val grouper: AppNotificationGrouper = AppNotificationGrouper(),
    private val staleFilter: NotificationStaleFilter = NotificationStaleFilter(),
    private val hideFilter: NotificationHideRuleFilter = NotificationHideRuleFilter(),
) {
    fun notificationItems(input: NotificationItemInput): List<Item> =
        toItems(input, SourceIds.NOTIFICATIONS, media = false)

    fun mediaItems(input: NotificationItemInput): List<Item> = toItems(input, SourceIds.MEDIA, media = true)

    private fun toItems(
        input: NotificationItemInput,
        sourceId: SourceId,
        media: Boolean,
    ): List<Item> {
        val hidden = hideFilter.visible(input.notifications, input.hideRules)
        val fresh = staleFilter.activeForLauncherState(hidden, input.nowEpochMillis)
        val selected = fresh.filter { notification -> notification.isMediaSession == media }
        return grouper.groupByApp(selected, input.nowEpochMillis).flatMap { group ->
            group.notifications.mapNotNull { notification ->
                when (input.profileContentVisibility[notification.profileId]) {
                    AppProfileContentVisibility.VISIBLE -> notification.toItem(sourceId, input, redacted = false)
                    AppProfileContentVisibility.REDACTED_QUIET -> notification.toItem(sourceId, input, redacted = true)
                    else -> null
                }
            }
        }
    }

    private fun LauncherNotification.toItem(
        sourceId: SourceId,
        input: NotificationItemInput,
        redacted: Boolean,
    ): Item {
        val appName = input.appLabel(packageName) ?: packageName.value
        val base =
            Item(
                id = ItemId("${sourceId.value}:${profileId.value}:${key.value}"),
                sourceId = sourceId,
                target = ItemTarget.App(packageName.value, profileId.value),
                icon = ItemImageKeys.packageIcon(packageName, profileId),
                timeEpochMillis = postedAtEpochMillis.coerceAtLeast(0L),
                groupKey = "${packageName.value}:${profileId.value}",
                groupLabel = appName,
                privacy = if (redacted) ItemPrivacy.SENSITIVE else ItemPrivacy.VISIBLE,
                ext = appProfileExt(profileId, input.profileTypes),
            )
        return if (redacted) base else base.withContent(this, appName)
    }

    private fun Item.withContent(
        notification: LauncherNotification,
        appName: String,
    ): Item =
        copy(
            title = notification.title.takeIf(String::isNotBlank),
            subtitle = if (sourceId == SourceIds.MEDIA) notification.text.takeIf(String::isNotBlank) else null,
            body = if (sourceId == SourceIds.MEDIA) null else notification.text.takeIf(String::isNotBlank),
            image =
                notification.largeIconPngBase64
                    ?.let { ItemImageKeys.notificationArtwork(notification.profileId, notification.key.value) },
            actions =
                listOfNotNull(
                    ItemAction.Open(),
                    ItemAction.Dismiss().takeIf { notification.canDismiss },
                ),
            ext =
                ext +
                    mapOf(
                        CATEGORY_KEY to ItemExtValue.Text(notification.category.name.lowercase()),
                        PRIORITY_KEY to ItemExtValue.Number(notification.priority.rank.toLong()),
                        APP_KEY to ItemExtValue.Text(appName),
                    ),
        )

    private companion object {
        val CATEGORY_KEY = ItemExtKey("notification.category")
        val PRIORITY_KEY = ItemExtKey("notification.priority")
        val APP_KEY = ItemExtKey("notification.app")
    }
}
