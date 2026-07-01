package com.riffle.app.launcher

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

    fun importDocument(openInputStream: () -> InputStream?): LauncherBackupImportResult =
        runCatching {
            openInputStream()
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { reader -> reader.readText() }
                ?.let(::decodeLauncherBackupDocument)
                ?: error("Could not open backup source")
        }.fold(
            onSuccess = { document -> LauncherBackupImportResult.Imported(document) },
            onFailure = { LauncherBackupImportResult.Failure },
        )
}

sealed interface LauncherBackupExportResult {
    data object Success : LauncherBackupExportResult

    data object Failure : LauncherBackupExportResult
}

sealed interface LauncherBackupImportResult {
    data class Imported(
        val document: LauncherBackupDocument,
    ) : LauncherBackupImportResult

    data object Failure : LauncherBackupImportResult
}
