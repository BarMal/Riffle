package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.notifications.NotificationHideRuleId
import java.util.UUID

/** Durable, user-created rules that hide matching notifications from ever becoming a stage/card. */
data class NotificationHidingSettings(
    val rules: List<NotificationHideRule> = emptyList(),
)

/** Bounds the number of rules a user can accumulate, mirroring other bounded settings lists. */
const val MAX_NOTIFICATION_HIDE_RULES = 200

/** Adds a new rule for the given source app/content match, ignoring an exact duplicate. */
fun NotificationHidingSettings.withRule(
    packageName: AppPackageName,
    profileId: AppProfileId,
    kind: NotificationHideRule.Kind,
    value: String = "",
    matchMode: NotificationHideRule.MatchMode = NotificationHideRule.MatchMode.EXACT,
): NotificationHidingSettings {
    val hasNoValue = kind == NotificationHideRule.Kind.APP || kind == NotificationHideRule.Kind.EMPTY_CONTENT
    val normalizedValue = if (hasNoValue) "" else value
    val isDuplicate =
        rules.any { rule ->
            rule.packageName == packageName &&
                rule.profileId == profileId &&
                rule.kind == kind &&
                rule.matchMode == matchMode &&
                rule.value == normalizedValue
        }
    if (isDuplicate) return this
    val rule =
        NotificationHideRule(
            id = NotificationHideRuleId(UUID.randomUUID().toString()),
            packageName = packageName,
            profileId = profileId,
            kind = kind,
            value = normalizedValue,
            matchMode = matchMode,
        )
    return copy(rules = (rules + rule).take(MAX_NOTIFICATION_HIDE_RULES))
}

fun NotificationHidingSettings.withoutRule(id: NotificationHideRuleId): NotificationHidingSettings =
    copy(rules = rules.filterNot { it.id == id })
