package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.notifications.NotificationHideRuleId
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.random.Random

internal val PACKAGES = listOf("chat", "mail", "maps")
internal val PROFILES = listOf(AppProfile.personal(), AppProfile.work())
internal val TEXTS = listOf("", " ", "Order #4821 shipped", "order #5190 shipped", "Sale today", "Hello", "Hi")
internal const val NOW = 1_000_000L

internal fun appIdentity(
    pkg: String,
    activity: String = "Main",
    profile: AppProfile = AppProfile.personal(),
) = AppIdentity(AppPackageName(pkg), AppActivityName(activity), profile)

internal fun installed(identity: AppIdentity) = InstalledApp(identity, label = identity.packageName.value)

internal fun notification(
    key: String,
    pkg: String = "chat",
    profile: AppProfileId = AppProfile.personal().id,
    title: String = "",
    text: String = "",
    media: Boolean = false,
) = LauncherNotification(
    key = LauncherNotificationKey(key),
    packageName = AppPackageName(pkg),
    profileId = profile,
    isMediaSession = media,
    title = title,
    text = text,
    postedAtEpochMillis = NOW - 10,
)

internal fun item(
    id: String,
    source: SourceId = SourceIds.NOTIFICATIONS,
    pkg: String? = "chat",
    profile: String = "personal",
    title: String? = null,
    body: String? = null,
    group: String? = null,
) = Item(
    id = ItemId(id),
    sourceId = source,
    target = pkg?.let { ItemTarget.App(it, profile) } ?: ItemTarget.None,
    title = title,
    body = body,
    groupKey = group,
)

internal fun rule(
    id: String,
    matcher: ExclusionMatcher,
    source: SourceId = SourceIds.NOTIFICATIONS,
    enabled: Boolean = true,
) = SourceExclusionRule(ExclusionRuleId(id), source, matcher, enabled)

internal fun randomHideRules(
    random: Random,
    count: Int,
): List<NotificationHideRule> =
    List(count) { index ->
        NotificationHideRule(
            id = NotificationHideRuleId("r$index"),
            packageName = AppPackageName(PACKAGES.random(random)),
            profileId = PROFILES.random(random).id,
            kind = NotificationHideRule.Kind.entries.random(random),
            value = listOf("", "sale today", "order #{?} shipped", "hel", "{?}", "Hello ").random(random),
            matchMode = NotificationHideRule.MatchMode.entries.random(random),
        )
    }

internal fun randomNotifications(
    random: Random,
    count: Int,
): List<LauncherNotification> =
    List(count) { index ->
        notification(
            key = "n$index",
            pkg = PACKAGES.random(random),
            profile = PROFILES.random(random).id,
            title = TEXTS.random(random),
            text = TEXTS.random(random),
            media = random.nextInt(4) == 0,
        )
    }
