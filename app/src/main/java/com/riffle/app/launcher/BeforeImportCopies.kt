package com.riffle.app.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * The one "before import" copy (Settings > Backup > Undo last import): the set-up an import replaced, written by the
 * same document an export writes, in a file of its own. Local to this install and excluded from Android's backup and
 * device transfer (`backup_rules.xml`, `data_extraction_rules.xml`). One copy only: [keep] replaces the previous one,
 * Undo and Dismiss delete it.
 *
 * [keep] returns only once the file is durably written (a temporary file, synced, then renamed over the copy), so an
 * import that goes on to replace Home and settings always has its way back on disk first.
 */
class BeforeImportCopies(
    private val file: File,
    /** Opens and closes the window in which Home's widget host ids are kept for Undo (one pair per import). */
    private val holdWidgetIds: () -> Unit = {},
    private val resumeWidgetIds: () -> Unit = {},
) {
    private val state = MutableStateFlow(readInfo())

    /** When the kept copy was taken; null when there is none. Drives the "Undo last import" row. */
    val keptAtEpochMillis: StateFlow<Long?> = state.asStateFlow()

    private val announcement = MutableStateFlow(false)

    /** True from a successful import until the page has shown "Backup imported · Undo". */
    val announcementPending: StateFlow<Boolean> = announcement.asStateFlow()

    @Synchronized
    fun keep(json: String) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        check(temporary.renameTo(file)) { "Could not keep the copy" }
        state.value = readInfo()
    }

    /** The kept copy as a stream for the validated import path, or null when there is none. */
    @Synchronized
    fun open(): InputStream? = file.takeIf { it.isFile }?.inputStream()

    @Synchronized
    fun clear() {
        file.delete()
        state.value = null
    }

    /** An import went through: the page announces it with Undo. */
    fun announceImport() {
        holdWidgetIds()
        announcement.value = true
    }

    fun consumeAnnouncement() {
        announcement.value = false
    }

    /** Ends the window [announceImport] opened (Undo, timeout, another message or leaving Settings). */
    fun closeUndoWindow() = resumeWidgetIds()

    private fun readInfo(): Long? =
        file.takeIf { it.isFile }
            ?.let { stored ->
                runCatching { JSONObject(stored.readText(Charsets.UTF_8)).optLong("exportedAtEpochMillis", 0L) }
                    .getOrNull()?.takeIf { it > 0L } ?: stored.lastModified()
            }
}
