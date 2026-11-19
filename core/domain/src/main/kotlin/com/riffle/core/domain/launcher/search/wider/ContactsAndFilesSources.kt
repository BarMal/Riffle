package com.riffle.core.domain.launcher.search.wider

/** A contact row as a provider returns it. Held only for the length of one query. */
data class ContactRecord(
    val contactUri: String,
    val name: String,
    val number: String?,
    val photoUri: String?,
)

/** Reads contacts from the platform, off the main thread, at most [limit] rows. */
fun interface ContactsReader {
    suspend fun read(
        query: String,
        limit: Int,
    ): List<ContactRecord>
}

/**
 * Contacts by name: the whole name starts with the query, or every query word starts a word of the name; accents and
 * case are ignored. Personal profile only. It keeps nothing between queries, writes nothing anywhere and logs nothing.
 */
class ContactsSource(
    private val reader: ContactsReader,
    override val maxResults: Int = SearchSource.DEFAULT_MAX_RESULTS,
) : SearchSource {
    override val id: SearchSourceId = SearchSourceId.CONTACTS
    override val minQueryLength: Int = MIN_LENGTH
    override val debounceMillis: Long = DEBOUNCE_MILLIS

    override suspend fun query(
        query: String,
        limit: Int,
    ): List<ScoredResult> =
        reader.read(query, READ_LIMIT).mapNotNull { record ->
            WiderMatching.nameScore(record.name, query)?.let { score ->
                ScoredResult(
                    WiderSearchResult.Contact(record.contactUri, record.name, record.number, record.photoUri),
                    score,
                )
            }
        }

    companion object {
        const val MIN_LENGTH = 2
        const val DEBOUNCE_MILLIS = 150L
        const val READ_LIMIT = 40
    }
}

/** A file the launcher can see by name: a media item or a download. */
data class FileRecord(
    val uri: String,
    val name: String,
    val mimeType: String?,
    val kind: FileKind,
    val modifiedSeconds: Long,
)

/** Lists recent files whose name contains the query, newest first, at most [limit] rows. */
fun interface FilesReader {
    suspend fun read(
        query: String,
        limit: Int,
    ): List<FileRecord>
}

/**
 * Files by name. What [reader] can see depends on the permissions the person has allowed (see the Files notes in
 * `settings.md`); with none, only the "Open Files with this search" row is offered, which is always last.
 */
class FilesSource(
    private val reader: FilesReader,
    override val maxResults: Int = SearchSource.DEFAULT_MAX_RESULTS,
) : SearchSource {
    override val id: SearchSourceId = SearchSourceId.FILES
    override val minQueryLength: Int = MIN_LENGTH
    override val debounceMillis: Long = DEBOUNCE_MILLIS

    override suspend fun query(
        query: String,
        limit: Int,
    ): List<ScoredResult> {
        val hits =
            reader.read(query, READ_LIMIT).mapNotNull { file ->
                val score =
                    WiderMatching.nameScore(file.name, query)
                        ?: WiderMatching.fold(file.name).takeIf { it.contains(WiderMatching.fold(query.trim())) }
                            ?.let { WiderMatching.CONTAINS }
                // The reader returns newest first and the sort is stable: newer files lead among equal matches.
                score?.let {
                    ScoredResult(WiderSearchResult.FileHit(file.uri, file.name, file.mimeType, file.kind), it)
                }
            }
        return hits + ScoredResult(WiderSearchResult.FilesApp(query.trim()), 0)
    }

    companion object {
        const val MIN_LENGTH = 2
        const val DEBOUNCE_MILLIS = 200L
        const val READ_LIMIT = 40
    }
}
