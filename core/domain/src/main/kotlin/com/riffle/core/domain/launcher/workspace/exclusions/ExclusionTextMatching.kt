package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.notifications.NotificationHideRule

/** Text matching with exactly the legacy `NotificationHideRule` normalisation and wildcard semantics. */
internal object ExclusionTextMatching {
    fun matches(
        candidate: String,
        value: String,
        mode: ExclusionMatchMode,
    ): Boolean {
        val normalizedCandidate = NotificationHideRule.normalize(candidate)
        val normalizedValue = NotificationHideRule.normalize(value)
        return when (mode) {
            ExclusionMatchMode.EXACT -> normalizedCandidate == normalizedValue
            ExclusionMatchMode.CONTAINS ->
                normalizedValue.isNotEmpty() && normalizedCandidate.contains(normalizedValue)
            ExclusionMatchMode.WILDCARD ->
                normalizedValue.isNotEmpty() &&
                    NotificationHideRule.wildcardRegex(normalizedValue).matches(normalizedCandidate)
        }
    }
}
