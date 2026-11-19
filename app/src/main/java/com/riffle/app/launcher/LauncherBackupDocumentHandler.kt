package com.riffle.app.launcher

import androidx.annotation.StringRes
import com.riffle.app.R
import java.io.InputStream
import java.io.OutputStream

class LauncherBackupDocumentHandler(
    private val exportCoordinator: LauncherBackupExportCoordinator,
    private val importCoordinator: LauncherBackupImportCoordinator,
    private val documentGateway: LauncherBackupDocumentGateway,
    private val workspaceBackup: WorkspaceBackupPort? = null,
    /** Where the copy of what an import replaces is kept; null keeps none (and imports without one). */
    private val beforeImport: BeforeImportCopies? = null,
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

    /**
     * Imports the backup [openInputStream] reads. A file that is not an importable backup changes nothing. Before
     * anything is replaced, the current set-up is written, durably, as the one "before import" copy
     * ([BeforeImportCopies.keep], the same document an export writes); if that cannot be written the import does not
     * happen. [keepCopy] is false for the Undo of an import, which re-imports that copy and then forgets it.
     */
    fun importBackup(
        keepCopy: Boolean = true,
        openInputStream: () -> InputStream?,
    ): LauncherBackupImportHandlingResult {
        val read = documentGateway.importDocument(openInputStream)
        return when (val outcome = importCoordinator.handleImportResult(read)) {
            is LauncherBackupImportOutcome.Imported ->
                if (keepCopy && !keepCurrentSetUp()) {
                    LauncherBackupImportHandlingResult.Failure(LauncherBackupMessage.IMPORT_NOT_KEPT)
                } else {
                    restoreWorkspaceData(outcome.action.document)
                    LauncherBackupImportHandlingResult.Imported(
                        action = outcome.action,
                        message = LauncherBackupMessage.IMPORTED,
                    )
                }

            LauncherBackupImportOutcome.Failure ->
                LauncherBackupImportHandlingResult.Failure(failureMessage(read))
        }
    }

    private fun keepCurrentSetUp(): Boolean {
        val copies = beforeImport ?: return true
        return runCatching { copies.keep(encodeLauncherBackupDocument(exportCoordinator.currentBackupDocument())) }
            .isSuccess
    }

    private fun failureMessage(read: LauncherBackupImportResult): LauncherBackupMessage =
        when (read) {
            // The file was read and decoded, but the document is not importable (an empty or invalid layout).
            is LauncherBackupImportResult.Imported -> LauncherBackupMessage.IMPORT_DAMAGED
            is LauncherBackupImportResult.Rejected ->
                when (read.reason) {
                    LauncherBackupImportFailure.NOT_A_BACKUP -> LauncherBackupMessage.IMPORT_NOT_A_BACKUP
                    LauncherBackupImportFailure.NEWER_VERSION -> LauncherBackupMessage.IMPORT_NEWER_VERSION
                    LauncherBackupImportFailure.DAMAGED -> LauncherBackupMessage.IMPORT_DAMAGED
                }
            LauncherBackupImportResult.Failure -> LauncherBackupMessage.IMPORT_FAILED
        }

    /**
     * Replaces stored workspace data only for sections the backup carries; an older backup without them
     * leaves current data untouched. A failing store never fails the rest of the import.
     */
    private fun restoreWorkspaceData(document: LauncherBackupDocument) {
        val port = workspaceBackup ?: return
        document.workspaceSet?.let { set -> runCatching { port.restoreWorkspaceSet(set) } }
        document.exclusions?.let { rules -> runCatching { port.restoreExclusions(rules) } }
        document.pool?.let { state -> runCatching { port.restorePool(state) } }
        document.stream?.let { layouts -> runCatching { port.restoreStreamLayouts(layouts) } }
        document.calendarFeeds?.let { feeds -> runCatching { port.restoreCalendarFeeds(feeds) } }
        document.pairs?.let { pairs -> runCatching { port.restorePairs(pairs) } }
        document.savedHomeLayouts?.let { saved -> runCatching { port.restoreSavedHomeLayouts(saved) } }
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

/** What the user is told; the text is in resources (`backup_message_*`). */
enum class LauncherBackupMessage(
    @StringRes val textRes: Int,
) {
    EXPORTED(R.string.backup_message_exported),
    EXPORT_FAILED(R.string.backup_message_export_failed),
    IMPORTED(R.string.backup_message_imported),
    IMPORT_FAILED(R.string.backup_message_import_failed),
    IMPORT_NOT_A_BACKUP(R.string.backup_message_import_not_a_backup),
    IMPORT_NEWER_VERSION(R.string.backup_message_import_newer_version),
    IMPORT_DAMAGED(R.string.backup_message_import_damaged),
    IMPORT_NOT_KEPT(R.string.backup_message_import_not_kept),
}
