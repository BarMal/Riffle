package com.riffle.app.launcher

class LauncherBackupImportCoordinator {
    fun handleImportResult(result: LauncherBackupImportResult): LauncherBackupImportOutcome =
        when (result) {
            is LauncherBackupImportResult.Imported ->
                when {
                    result.document.isImportableBackup() ->
                        LauncherBackupImportOutcome.Imported(
                            action = LauncherShellAction.ImportLauncherBackup(result.document),
                        )

                    else -> LauncherBackupImportOutcome.Failure
                }

            LauncherBackupImportResult.Failure -> LauncherBackupImportOutcome.Failure
        }
}

sealed interface LauncherBackupImportOutcome {
    data class Imported(
        val action: LauncherShellAction.ImportLauncherBackup,
    ) : LauncherBackupImportOutcome

    data object Failure : LauncherBackupImportOutcome
}
