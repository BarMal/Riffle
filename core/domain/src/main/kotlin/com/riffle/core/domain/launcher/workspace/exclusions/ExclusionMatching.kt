package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds

/**
 * Matcher semantics over an [Item]. Structure matchers (app, group, item key) work on any item; content
 * matchers (text, empty content) never match a [ItemPrivacy.SENSITIVE] item, which carries no content to
 * confirm, so a rule can never be used to probe redacted text.
 */
internal object ExclusionMatching {
    fun matches(
        matcher: ExclusionMatcher,
        item: Item,
    ): Boolean =
        when (matcher) {
            is ExclusionMatcher.App -> appMatches(matcher, item)
            is ExclusionMatcher.Group -> item.groupKey == matcher.groupKey
            is ExclusionMatcher.ItemKey -> item.id.value == matcher.itemId
            is ExclusionMatcher.Text -> textMatches(matcher, item)
            is ExclusionMatcher.EmptyContent -> emptyMatches(matcher, item)
        }

    /** App and content matchers apply across a source family; group and item keys are source-specific. */
    fun spansFamily(matcher: ExclusionMatcher): Boolean =
        matcher !is ExclusionMatcher.Group && matcher !is ExclusionMatcher.ItemKey

    fun appMatches(
        matcher: ExclusionMatcher.App,
        identity: AppIdentity,
    ): Boolean =
        matcher.packageName == identity.packageName.value &&
            (matcher.profileId == null || matcher.profileId == identity.profile.id.value) &&
            (matcher.activityName == null || matcher.activityName == identity.activityName.value)

    private fun appMatches(
        matcher: ExclusionMatcher.App?,
        item: Item,
    ): Boolean =
        matcher == null ||
            appOf(item)?.let { (packageName, profileId) ->
                matcher.packageName == packageName &&
                    (matcher.profileId == null || matcher.profileId == profileId) &&
                    (matcher.activityName == null || matcher.activityName == activityOf(item, packageName, profileId))
            } == true

    private fun appOf(item: Item): Pair<String, String?>? =
        when (val target = item.target) {
            is ItemTarget.App -> target.packageName to target.profileId
            is ItemTarget.Shortcut -> target.packageName to target.profileId
            else -> null
        }

    /**
     * The launcher activity of an app or shortcut item, read from the id the app mappers build
     * (`<source>:<profile>:<package>/<activity>` and `<source>:<profile>:<package>:<activity>:<shortcut>`).
     */
    private fun activityOf(
        item: Item,
        packageName: String,
        profileId: String?,
    ): String? {
        val prefix = "${item.sourceId.value}:$profileId:$packageName"
        if (!item.id.value.startsWith(prefix)) return null
        val rest = item.id.value.substring(prefix.length)
        return when {
            item.sourceId == SourceIds.QUICK_ACTIONS && rest.startsWith(":") -> rest.drop(1).substringBefore(':')
            rest.startsWith("/") -> rest.drop(1)
            else -> null
        }
    }

    private fun textMatches(
        matcher: ExclusionMatcher.Text,
        item: Item,
    ): Boolean =
        item.privacy == ItemPrivacy.VISIBLE &&
            appMatches(matcher.app, item) &&
            ExclusionTextMatching.matches(fieldText(item, matcher.field).orEmpty(), matcher.value, matcher.mode)

    private fun emptyMatches(
        matcher: ExclusionMatcher.EmptyContent,
        item: Item,
    ): Boolean =
        item.privacy == ItemPrivacy.VISIBLE &&
            appMatches(matcher.app, item) &&
            fieldText(item, ExclusionTextField.TITLE).isNullOrBlank() &&
            fieldText(item, ExclusionTextField.BODY).isNullOrBlank()

    /** Media items carry a notification's text as the subtitle, so BODY reads it there. */
    private fun fieldText(
        item: Item,
        field: ExclusionTextField,
    ): String? =
        when (field) {
            ExclusionTextField.TITLE -> item.title
            ExclusionTextField.SUBTITLE -> item.subtitle
            ExclusionTextField.BODY -> if (item.sourceId == SourceIds.MEDIA) item.subtitle else item.body
        }
}
