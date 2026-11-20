package com.riffle.app.launcher.search.wider

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.riffle.app.launcher.BoundedCache
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.core.domain.launcher.search.wider.ContactRecord
import com.riffle.core.domain.launcher.search.wider.ContactsReader
import com.riffle.core.domain.launcher.search.wider.FileKind
import com.riffle.core.domain.launcher.search.wider.FileRecord
import com.riffle.core.domain.launcher.search.wider.FilesReader
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Contacts of the personal profile whose name the provider's own filter matches, at most [limit] rows, read off the
 * main thread. The row's first phone number (the super primary one when there is one) is looked up in one more bounded
 * query. Work-profile and directory contacts are not read in this batch. Returned rows are held only for the query.
 */
internal class AndroidContactsReader(
    private val resolver: ContentResolver,
) : ContactsReader {
    override suspend fun read(
        query: String,
        limit: Int,
    ): List<ContactRecord> =
        withContext(Dispatchers.IO) {
            val uri =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
                    .buildUpon()
                    .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit.toString())
                    .build()
            val rows = mutableListOf<ContactRow>()
            runCatching {
                resolver.query(uri, CONTACT_PROJECTION, null, null, ContactsContract.Contacts.SORT_KEY_PRIMARY)
            }.getOrNull()?.use { cursor ->
                while (cursor.moveToNext() && rows.size < limit) {
                    coroutineContext.ensureActive()
                    val id = cursor.getLong(0)
                    rows +=
                        ContactRow(
                            id = id,
                            lookupUri = ContactsContract.Contacts.getLookupUri(id, cursor.getString(1)).toString(),
                            name = cursor.getString(2).orEmpty(),
                            photo = cursor.getString(3),
                            hasNumber = cursor.getInt(4) > 0,
                        )
                }
            }
            val numbers = numbersFor(rows.filter { it.hasNumber }.map { it.id })
            rows.map { row -> ContactRecord(row.lookupUri, row.name, numbers[row.id], row.photo) }
        }

    private fun numbersFor(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (${ids.joinToString(",") { "?" }})"
        val found = linkedMapOf<Long, String>()
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                selection,
                ids.map(Long::toString).toTypedArray(),
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, " +
                    "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
            )
        }.getOrNull()?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(1)
                if (!number.isNullOrBlank()) found.putIfAbsent(cursor.getLong(0), number)
            }
        }
        return found
    }

    private class ContactRow(
        val id: Long,
        val lookupUri: String,
        val name: String,
        val photo: String?,
        val hasNumber: Boolean,
    )

    private companion object {
        val CONTACT_PROJECTION =
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
            )
    }
}

/**
 * Recent files whose name contains the query: photos, videos and audio through their `MediaStore` collections and
 * downloads through `MediaStore.Downloads`, newest first. What the launcher can list is what the media permission the
 * person allowed lets it see: with none, `MediaStore` shows only files this app made itself (none), so nothing is read
 * and the Files source offers only its "Open Files with this search" row. Rows are bounded and read off the main
 * thread.
 */
internal class AndroidFilesReader(
    private val resolver: ContentResolver,
    private val hasAccess: () -> Boolean,
) : FilesReader {
    override suspend fun read(
        query: String,
        limit: Int,
    ): List<FileRecord> {
        if (!hasAccess()) return emptyList()
        return withContext(Dispatchers.IO) {
            COLLECTIONS.flatMap { (kind, collection) ->
                coroutineContext.ensureActive()
                readCollection(collection, kind, query, limit)
            }.sortedByDescending { it.modifiedSeconds }.take(limit)
        }
    }

    private fun readCollection(
        collection: Uri,
        kind: FileKind,
        query: String,
        limit: Int,
    ): List<FileRecord> {
        val args =
            Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
                )
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%${escaped(query)}%"))
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                )
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
        val rows = mutableListOf<FileRecord>()
        runCatching { resolver.query(collection, FILE_PROJECTION, args, null) }.getOrNull()?.use { cursor ->
            while (cursor.moveToNext()) {
                rows +=
                    FileRecord(
                        uri = ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(),
                        name = cursor.getString(1).orEmpty(),
                        mimeType = cursor.getString(2),
                        kind = kind,
                        modifiedSeconds = cursor.getLong(3),
                    )
            }
        }
        return rows
    }

    private fun escaped(query: String): String =
        query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        const val NAME_SELECTION = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'"
        val FILE_PROJECTION =
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
        val COLLECTIONS =
            listOf(
                FileKind.DOWNLOAD to MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                FileKind.IMAGE to MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                FileKind.VIDEO to MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                FileKind.AUDIO to MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            )
    }
}

/**
 * Contact photo thumbnails for the rows, through the same [ExpressionImageLoader] and `ItemImage` the Stream's images
 * use: decoded off the main thread, scaled down while decoding to what is drawn, and held in a small in-memory cache
 * only. A handle is `contact-photo:<content uri>`; nothing is ever written.
 */
internal class ContactPhotoLoader(
    private val resolver: ContentResolver,
) : ExpressionImageLoader {
    private val cache = BoundedCache<String, ImageBitmap>(MAX_CACHED_PHOTOS)

    override suspend fun load(
        handle: ItemImageHandle,
        sizePx: Int,
    ): ImageBitmap? {
        val uri = handle.key.removePrefix(PREFIX).takeIf { handle.key.startsWith(PREFIX) } ?: return null
        val key = "$uri@$sizePx"
        return cache[key]
            ?: withContext(Dispatchers.IO) { decode(Uri.parse(uri), sizePx.coerceIn(1, MAX_SIZE_PX)) }
                ?.also { cache[key] = it }
    }

    private fun decode(
        uri: Uri,
        size: Int,
    ): ImageBitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
        }.getOrNull()

    private companion object {
        const val PREFIX = "contact-photo:"
        const val MAX_CACHED_PHOTOS = 48
        const val MAX_SIZE_PX = 256
    }
}
