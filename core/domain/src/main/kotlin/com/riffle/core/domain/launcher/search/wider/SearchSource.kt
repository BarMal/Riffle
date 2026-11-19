package com.riffle.core.domain.launcher.search.wider

/**
 * The sources that widen search beyond apps, app shortcuts and the launcher's own settings. Each is independently
 * switchable in Settings > Library > Search. [CALCULATOR] answers lead the results and are not a group a person
 * orders; the other three are the groups, in an order the person sets.
 */
enum class SearchSourceId {
    CALCULATOR,
    SYSTEM_SETTINGS,
    CONTACTS,
    FILES,
    ;

    /** True for the sources drawn as a group below the apps (the order setting is about these). */
    val isGroup: Boolean get() = this != CALCULATOR

    companion object {
        /** The default order of the groups. */
        val DEFAULT_GROUP_ORDER: List<SearchSourceId> = listOf(SYSTEM_SETTINGS, CONTACTS, FILES)
    }
}

/** What a search row asks the platform to do. The domain only names it; the app performs it. */
sealed interface WiderSearchAction {
    /** Put [text] on the clipboard and say so. */
    data class Copy(val text: String) : WiderSearchAction

    /** Open the system settings screen of [intentAction] (a `Settings.ACTION_*` string). */
    data class OpenSystemSetting(val intentAction: String) : WiderSearchAction

    data class OpenContact(val contactUri: String) : WiderSearchAction

    /** Opens the dialler with [number] filled in; it never places the call. */
    data class Dial(val number: String) : WiderSearchAction

    data class Message(val number: String) : WiderSearchAction

    data class OpenFile(val uri: String, val mimeType: String?) : WiderSearchAction

    /** The Files app, for the query: the way into documents the launcher cannot list itself. */
    data class OpenFilesApp(val query: String) : WiderSearchAction
}

/**
 * One typed kind of result per source. They are not one universal item: each has its own row and its own actions.
 * Nothing here is ever persisted.
 */
sealed interface WiderSearchResult {
    val key: String
    val source: SearchSourceId

    /** A result that stays last in its group and is not counted against the group's cap. */
    val trailing: Boolean get() = false

    /** The answer to arithmetic: tapping copies [answer]; the secondary action copies "[expression] = [answer]". */
    data class CalculatorAnswer(val expression: String, val answer: String) : WiderSearchResult {
        override val key: String = "calc:$expression"
        override val source: SearchSourceId = SearchSourceId.CALCULATOR
    }

    /** A unit conversion: [input] ("12 km") and [answer] ("7.456454 mi"). */
    data class UnitConversion(val input: String, val answer: String) : WiderSearchResult {
        override val key: String = "unit:$input"
        override val source: SearchSourceId = SearchSourceId.CALCULATOR
    }

    data class SystemSetting(
        val id: String,
        val title: String,
        val intentAction: String,
    ) : WiderSearchResult {
        override val key: String = "system-setting:$id"
        override val source: SearchSourceId = SearchSourceId.SYSTEM_SETTINGS
    }

    /** A person from the device's contacts. [number] is null when the contact has none (no call or message). */
    data class Contact(
        val contactUri: String,
        val name: String,
        val number: String? = null,
        val photoUri: String? = null,
    ) : WiderSearchResult {
        override val key: String = "contact:$contactUri"
        override val source: SearchSourceId = SearchSourceId.CONTACTS
    }

    data class FileHit(
        val uri: String,
        val name: String,
        val mimeType: String?,
        val kind: FileKind,
    ) : WiderSearchResult {
        override val key: String = "file:$uri"
        override val source: SearchSourceId = SearchSourceId.FILES
    }

    /** "Open Files with this search": always last in the Files group. */
    data class FilesApp(val query: String) : WiderSearchResult {
        override val key: String = "files-app"
        override val source: SearchSourceId = SearchSourceId.FILES
        override val trailing: Boolean = true
    }
}

enum class FileKind { IMAGE, VIDEO, AUDIO, DOWNLOAD, DOCUMENT }

/** A result and how well it matches (higher is better); only meaningful within one source. */
data class ScoredResult(
    val result: WiderSearchResult,
    val score: Int,
)

/**
 * One source of wider results. Sources are asynchronous: [query] may suspend (a content provider read) and is
 * cancelled the moment the query changes, so it must stay cancellable and never publish anything itself.
 */
interface SearchSource {
    val id: SearchSourceId

    /** The most results this source ever returns, whatever the group cap is. */
    val maxResults: Int get() = DEFAULT_MAX_RESULTS

    /** Shorter (trimmed) queries do not reach the source at all. */
    val minQueryLength: Int get() = 1

    /** How long a query must stand still before this source runs; 0 for sources that answer from memory. */
    val debounceMillis: Long get() = 0

    suspend fun query(
        query: String,
        limit: Int,
    ): List<ScoredResult>

    companion object {
        const val DEFAULT_MAX_RESULTS = 12
    }
}
