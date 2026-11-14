package com.riffle.app.launcher

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntryId

/** Reads string resources for code that has no composition (the search index). Tests give it a fake. */
internal fun interface SettingsText {
    fun text(
        @StringRes id: Int,
        vararg args: Any,
    ): String
}

internal class ResourcesSettingsText(private val resources: Resources) : SettingsText {
    override fun text(
        id: Int,
        vararg args: Any,
    ): String = if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
}

/**
 * The text source for places that have no context, which is only the launcher's own search (its reducers are pure).
 * The activity installs it once; until then [current] reads nothing and the settings are not offered.
 */
internal object SettingsTextHolder {
    @Volatile var current: SettingsText? = null
        private set

    @Volatile private var cachedEntries: List<LauncherSearchSettingsEntry>? = null

    fun install(resources: Resources) = install(ResourcesSettingsText(resources))

    fun install(text: SettingsText?) {
        current = text
        cachedEntries = null
    }

    /** The launcher search's settings entries, built once per installed text source. */
    fun launcherSearchEntries(): List<LauncherSearchSettingsEntry> =
        cachedEntries ?: current?.let(::settingsLauncherSearchEntries).orEmpty().also { cachedEntries = it }
}

@Composable
internal fun rememberSettingsText(): SettingsText {
    val context = LocalContext.current
    return remember(context) { ResourcesSettingsText(context.resources) }
}

/**
 * One searchable thing in Settings: a page ([key] null) or one setting on a page. [path] is where it lives
 * ("Stream > Notifications"), [keywords] the extra words that find it.
 */
internal data class SettingsSearchEntry(
    val page: SettingsPage,
    val key: SettingKey?,
    val label: String,
    val path: String,
    val keywords: String,
) {
    /** The id the launcher search and the Settings search share: the page's name, plus `#KEY` for a setting. */
    val id: String get() = page.name.lowercase() + (key?.let { "#${it.name}" } ?: "")

    val searchableText: String get() = "$label $path $keywords".lowercase()
}

/** The path of [page]: its ancestors' titles, outermost first, without the main page. */
internal fun settingsPagePath(
    text: SettingsText,
    page: SettingsPage,
): String = page.ancestry().asReversed().joinToString(separator = " > ") { text.text(it.titleRes) }

/** Every page below the main page and every setting, so everything in Settings can be found. */
internal fun settingsSearchIndex(text: SettingsText): List<SettingsSearchEntry> {
    val pages =
        SettingsPage.entries.filter { it != SettingsPage.MAIN }.map { page ->
            SettingsSearchEntry(
                page = page,
                key = null,
                label = text.text(page.titleRes),
                path = settingsPagePath(text, page.parent ?: SettingsPage.MAIN),
                keywords = "${text.text(page.summaryRes)} ${text.text(page.keywordsRes)}",
            )
        }
    val settings =
        SettingKey.entries.map { key ->
            SettingsSearchEntry(
                page = key.page,
                key = key,
                label = text.text(key.labelRes),
                path = settingsPagePath(text, key.page),
                keywords = text.text(key.keywordsRes),
            )
        }
    return pages + settings
}

/**
 * The entries every token of [query] matches, those whose label matches first. A blank query matches nothing here:
 * the main page shows its rows then.
 */
internal fun List<SettingsSearchEntry>.matching(query: String): List<SettingsSearchEntry> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if (tokens.isEmpty()) return emptyList()
    return filter { entry -> tokens.all { token -> entry.searchableText.contains(token) } }
        .sortedBy { entry -> if (tokens.all { entry.label.lowercase().contains(it) }) 0 else 1 }
}

/** The launcher's own search offers the same entries: page and setting, each opening Settings scrolled to it. */
internal fun settingsLauncherSearchEntries(text: SettingsText): List<LauncherSearchSettingsEntry> =
    settingsSearchIndex(text).map { entry ->
        LauncherSearchSettingsEntry(
            id = LauncherSearchSettingsEntryId(entry.id),
            title = entry.label,
            subtitle = entry.path,
            section = text.text(entry.page.titleRes),
            searchAliases = entry.keywords.split(' ').filter(String::isNotBlank),
        )
    }

/** Where a launcher search result opens Settings: a page, and the setting to scroll to when it is one. */
internal data class SettingsTarget(
    val page: SettingsPage,
    val anchor: SettingKey? = null,
)

internal fun LauncherSearchSettingsEntryId.settingsTarget(): SettingsTarget? {
    val pageName = value.substringBefore('#')
    val page = SettingsPage.entries.firstOrNull { it.name.lowercase() == pageName } ?: return null
    val anchor =
        value.substringAfter('#', "").takeIf(String::isNotEmpty)?.let { name ->
            SettingKey.entries.firstOrNull { it.name == name }
        }
    return SettingsTarget(page, anchor)
}
