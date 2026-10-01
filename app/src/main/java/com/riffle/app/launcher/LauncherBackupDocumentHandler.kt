package com.riffle.app.launcher

import java.io.InputStream
import java.io.OutputStream

class LauncherBackupDocumentHandler(
    private val exportCoordinator: LauncherBackupExportCoordinator,
    private val importCoordinator: LauncherBackupImportCoordinator,
    private val documentGateway: LauncherBackupDocumentGateway,
    private val workspaceBackup: WorkspaceBackupPort? = null,
) {
    /** Called when the user asks to export, before the document picker opens, so stored data can load meanwhile. */
    fun prepareExport() {
        workspaceBackup?.let { port -> runCatching(port::prepareExport) }
    }

    fun exportBackup(openOutputStream: () -> OutputStream?): LauncherBackupExportHandlingResult =
        when (
            documentGateway.exportDocument(
                document = exportCoordinator.currentBackupDocument(),
                openOutputStream = openOutputStream,
            )
        ) {
            LauncherBackupExportResult.Success ->
                LauncherBackupExportHandlingResult(LauncherBackupMessage.EXPORTED)

            LauncherBackupExportResult.Failure ->
                LauncherBackupExportHandlingResult(LauncherBackupMessage.EXPORT_FAILED)
        }

    fun importBackup(openInputStream: () -> InputStream?): LauncherBackupImportHandlingResult =
        when (val outcome = importCoordinator.handleImportResult(documentGateway.importDocument(openInputStream))) {
            is LauncherBackupImportOutcome.Imported -> {
                restoreWorkspaceData(outcome.action.document)
                LauncherBackupImportHandlingResult.Imported(
                    action = outcome.action,
                    message = LauncherBackupMessage.IMPORTED,
                )
            }

            LauncherBackupImportOutcome.Failure ->
                LauncherBackupImportHandlingResult.Failure(LauncherBackupMessage.IMPORT_FAILED)
        }

    /**
     * Replaces stored workspace data only for sections the backup carries; an older backup without them
     * leaves current data untouched. A failing store never fails the rest of the import.
     */
    private fun restoreWorkspaceData(document: LauncherBackupDocument) {
        val port = workspaceBackup ?: return
        document.workspaceSet?.let { set -> runCatching { port.restoreWorkspaceSet(set) } }
        document.exclusions?.let { rules -> runCatching { port.restoreExclusions(rules) } }
    }
}

data class LauncherBackupExportHandlingResult(
    val message: LauncherBackupMessage,
)

sealed interface LauncherBackupImportHandlingResult {
    val message: LauncherBackupMessage

    data class Imported(
        val action: LauncherShellAction.ImportLauncherBackup,
        override val message: LauncherBackupMessage,
    ) : LauncherBackupImportHandlingResult

    data class Failure(
        override val message: LauncherBackupMessage,
    ) : LauncherBackupImportHandlingResult
}

enum class LauncherBackupMessage(
    val text: String,
) {
    EXPORTED("Backup exported"),
    EXPORT_FAILED("Backup export failed"),
    IMPORTED("Backup imported"),
    IMPORT_FAILED("Backup import failed"),
}
