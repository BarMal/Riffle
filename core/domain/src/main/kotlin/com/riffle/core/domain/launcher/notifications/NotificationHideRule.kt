package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId

/**
 * A durable, user-created rule that hides matching notifications from ever becoming a stage/card.
 * Rules are always scoped to a single (packageName, profileId) source app, created contextually
 * from an existing notification rather than authored freeform.
 */
data class NotificationHideRule(
    val id: NotificationHideRuleId,
    val packageName: AppPackageName,
    val profileId: AppProfileId,
    val kind: Kind,
    val value: String = "",
    val matchMode: MatchMode = MatchMode.EXACT,
) {
    enum class Kind {
        /** Hides every notification from this app, regardless of content. */
        APP,
        TITLE,
        BODY,

        /** Hides notifications whose title and text are both blank. */
        EMPTY_CONTENT,
    }

    enum class MatchMode {
        EXACT,
        CONTAINS,
        WILDCARD,
    }

    fun matches(notification: LauncherNotification): Boolean {
        if (notification.packageName != packageName || notification.profileId != profileId) return false
        return when (kind) {
            Kind.APP -> true
            Kind.TITLE -> textMatches(notification.title)
            Kind.BODY -> textMatches(notification.text)
            Kind.EMPTY_CONTENT -> notification.title.isBlank() && notification.text.isBlank()
        }
    }

    private fun textMatches(candidate: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        val normalizedValue = normalize(value)
        return when (matchMode) {
            MatchMode.EXACT -> normalizedCandidate == normalizedValue
            MatchMode.CONTAINS -> normalizedValue.isNotEmpty() && normalizedCandidate.contains(normalizedValue)
            MatchMode.WILDCARD ->
                normalizedValue.isNotEmpty() && wildcardRegex(normalizedValue).matches(normalizedCandidate)
        }
    }

    companion object {
        private val whitespace = Regex("\\s+")
        private val digitRun = Regex("\\d+")
        private const val ANY_PLACEHOLDER = "{?}"

        internal fun normalize(value: String): String = value.trim().replace(whitespace, " ").lowercase()

        /**
         * Replaces every run of digits with a wildcard placeholder, so a rule built from one
         * notification ("Order #4821 shipped") can match future notifications that only differ by a
         * number ("Order #5190 shipped"). Returns null when there's nothing to generalize, since a
         * wildcard-with-no-digits rule would be identical to an exact-match rule.
         */
        fun generalizeNumbers(text: String): String? {
            val normalized = normalize(text)
            if (normalized.isBlank() || !digitRun.containsMatchIn(normalized)) return null
            return digitRun.replace(normalized, ANY_PLACEHOLDER)
        }

        private fun wildcardRegex(pattern: String): Regex {
            val regex =
                buildString {
                    append("^")
                    var index = 0
                    while (index < pattern.length) {
                        if (pattern.startsWith(ANY_PLACEHOLDER, index)) {
                            append(".*")
                            index += ANY_PLACEHOLDER.length
                        } else {
                            append(Regex.escape(pattern[index].toString()))
                            index += 1
                        }
                    }
                    append("$")
                }
            return Regex(regex)
        }
    }
}

@JvmInline
value class NotificationHideRuleId(val value: String)

/** Drops any notification matched by at least one rule; an empty rule list is a no-op fast path. */
class NotificationHideRuleFilter {
    fun visible(
        notifications: List<LauncherNotification>,
        rules: List<NotificationHideRule>,
    ): List<LauncherNotification> =
        if (rules.isEmpty()) {
            notifications
        } else {
            notifications.filterNot { notification -> rules.any { it.matches(notification) } }
        }
}
