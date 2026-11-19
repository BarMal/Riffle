package com.riffle.app.launcher

import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.io.OutputStream

class LauncherBackupDocumentGateway {
    fun exportDocument(
        document: LauncherBackupDocument,
        openOutputStream: () -> OutputStream?,
    ): LauncherBackupExportResult =
        runCatching {
            val backupJson = encodeLauncherBackupDocument(document)
            openOutputStream()?.use { output ->
                output.write(backupJson.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open backup destination")
        }.fold(
            onSuccess = { LauncherBackupExportResult.Success },
            onFailure = { LauncherBackupExportResult.Failure },
        )

    fun importDocument(openInputStream: () -> InputStream?): LauncherBackupImportResult {
        val text =
            runCatching { openInputStream()?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() } }
                .getOrNull()
                ?: return LauncherBackupImportResult.Failure
        return runCatching { decodeLauncherBackupDocument(text) }.fold(
            onSuccess = { document -> LauncherBackupImportResult.Imported(document) },
            onFailure = { LauncherBackupImportResult.Rejected(classifyImportFailure(text)) },
        )
    }
}

/** Why a file that was read is not a backup this build can import. */
enum class LauncherBackupImportFailure {
    /** Not JSON, or JSON that is not a Riffle backup. */
    NOT_A_BACKUP,

    /** A Riffle backup written by a newer version of the app. */
    NEWER_VERSION,

    /** A Riffle backup of a version this build knows, with something missing or unusable in it. */
    DAMAGED,
}

internal fun classifyImportFailure(text: String): LauncherBackupImportFailure {
    val json = runCatching { JSONObject(JSONTokener(text)) }.getOrNull()
    return when {
        json == null || json.optString("type") != LAUNCHER_BACKUP_DOCUMENT_TYPE ->
            LauncherBackupImportFailure.NOT_A_BACKUP
        json.optInt("version") > LAUNCHER_BACKUP_DOCUMENT_VERSION -> LauncherBackupImportFailure.NEWER_VERSION
        else -> LauncherBackupImportFailure.DAMAGED
    }
}

sealed interface LauncherBackupExportResult {
    data object Success : LauncherBackupExportResult

    data object Failure : LauncherBackupExportResult
}

sealed interface LauncherBackupImportResult {
    data class Imported(
        val document: LauncherBackupDocument,
    ) : LauncherBackupImportResult

    /** The file could not be opened or read. */
    data object Failure : LauncherBackupImportResult

    /** The file was read and is not an importable backup. */
    data class Rejected(
        val reason: LauncherBackupImportFailure,
    ) : LauncherBackupImportResult
}
