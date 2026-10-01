package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.workspace.SourceId

@JvmInline
value class ExclusionRuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "Exclusion rule ids must not be blank." }
    }
}

/** Same semantics as the legacy `NotificationHideRule.MatchMode`. */
enum class ExclusionMatchMode {
    EXACT,
    CONTAINS,
    WILDCARD,
}

enum class ExclusionTextField {
    TITLE,
    SUBTITLE,
    BODY,
}

/** Where a rule came from; migrated rules are exempt from [SourceExclusionRule.MAX_USER_RULES]. */
enum class ExclusionOrigin {
    USER,
    MIGRATED_HIDDEN_APP,
    MIGRATED_HIDE_RULE,
}

/**
 * What a rule matches. Matchers describe structure the user chose (an app, a group, one item) or text the
 * user typed or generalised; they never hold item content beyond that. A null [App.profileId] or
 * [App.activityName] means "any".
 */
sealed interface ExclusionMatcher {
    data class App(
        val packageName: String,
        val profileId: String? = null,
        val activityName: String? = null,
    ) : ExclusionMatcher

    /** Everything in an item group: a feed id, a category, a `package:profile` notification group. */
    data class Group(val groupKey: String) : ExclusionMatcher

    /** One item by its source-stable id. */
    data class ItemKey(val itemId: String) : ExclusionMatcher

    /** Text in a field, optionally narrowed to one app. Never matches a sensitive (redacted) item. */
    data class Text(
        val field: ExclusionTextField,
        val value: String,
        val mode: ExclusionMatchMode = ExclusionMatchMode.EXACT,
        val app: App? = null,
    ) : ExclusionMatcher

    /** An item with neither title nor body text, optionally for one app. Never matches a sensitive item. */
    data class EmptyContent(val app: App? = null) : ExclusionMatcher
}

data class SourceExclusionRule(
    val id: ExclusionRuleId,
    val source: SourceId,
    val matcher: ExclusionMatcher,
    val enabled: Boolean = true,
    val label: String? = null,
    val origin: ExclusionOrigin = ExclusionOrigin.USER,
    val createdAtEpochMillis: Long = 0L,
) {
    init {
        require(createdAtEpochMillis >= 0L) { "createdAt cannot be negative." }
    }

    companion object {
        const val MAX_USER_RULES = 500
        const val MAX_LABEL_LENGTH = 80
    }
}
