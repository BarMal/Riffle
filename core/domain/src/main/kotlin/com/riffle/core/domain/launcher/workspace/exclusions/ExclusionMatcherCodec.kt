package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

/** Matcher half of [ExclusionRulesCodec]; an unknown or malformed matcher decodes to null (rule dropped). */
internal object ExclusionMatcherCodec {
    fun encode(matcher: ExclusionMatcher): StoredValue.Obj =
        when (matcher) {
            is ExclusionMatcher.App -> encodeApp(matcher)
            is ExclusionMatcher.Group -> obj("kind" to str("group"), "key" to str(matcher.groupKey))
            is ExclusionMatcher.ItemKey -> obj("kind" to str("item"), "key" to str(matcher.itemId))
            is ExclusionMatcher.Text ->
                obj(
                    "kind" to str("text"),
                    "field" to str(matcher.field.name),
                    "value" to str(matcher.value),
                    "mode" to str(matcher.mode.name),
                    "app" to matcher.app?.let(::encodeApp),
                )
            is ExclusionMatcher.EmptyContent -> obj("kind" to str("empty"), "app" to matcher.app?.let(::encodeApp))
        }

    fun decode(value: StoredValue?): ExclusionMatcher? {
        val root = value as? StoredValue.Obj ?: return null
        return when (root.string("kind")) {
            "app" -> decodeApp(root)
            "group" -> root.string("key")?.let { ExclusionMatcher.Group(it) }
            "item" -> root.string("key")?.let { ExclusionMatcher.ItemKey(it) }
            "text" -> decodeText(root)
            "empty" -> appField(root)?.let { ExclusionMatcher.EmptyContent(it.app) }
            else -> null
        }
    }

    private fun encodeApp(app: ExclusionMatcher.App): StoredValue.Obj =
        obj(
            "kind" to str("app"),
            "package" to str(app.packageName),
            "profile" to app.profileId?.let(::str),
            "activity" to app.activityName?.let(::str),
        )

    private fun decodeApp(root: StoredValue.Obj?): ExclusionMatcher.App? =
        root?.string("package")?.let { packageName ->
            ExclusionMatcher.App(packageName, root.string("profile"), root.string("activity"))
        }

    private fun decodeText(root: StoredValue.Obj): ExclusionMatcher.Text? {
        val field = enumOrNull<ExclusionTextField>(root.string("field"))
        val value = (root.fields["value"] as? StoredValue.Str)?.value
        val app = appField(root)
        return if (field == null || value == null || app == null) {
            null
        } else {
            val mode = enumOrNull<ExclusionMatchMode>(root.string("mode")) ?: ExclusionMatchMode.EXACT
            ExclusionMatcher.Text(field, value, mode, app.app)
        }
    }

    /** An absent app narrows nothing; a present but undecodable one must drop the rule, never widen it. */
    private fun appField(root: StoredValue.Obj): AppField? {
        val raw = root.fields["app"] ?: return AppField(null)
        return decodeApp(raw as? StoredValue.Obj)?.let(::AppField)
    }

    private class AppField(val app: ExclusionMatcher.App?)
}
