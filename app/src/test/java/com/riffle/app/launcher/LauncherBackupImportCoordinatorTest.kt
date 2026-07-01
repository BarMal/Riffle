package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherBackupImportCoordinatorTest {
    @Test
    fun turnsImportedDocumentIntoSettingsAction() {
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings = LauncherSettings(),
            )
        val coordinator = LauncherBackupImportCoordinator()

        val result = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))

        assertEquals(
            LauncherBackupImportOutcome.Imported(
                LauncherShellAction.ImportLauncherBackup(document),
            ),
            result,
        )
    }

    @Test
    fun reportsFailureForFailedImport() {
        val coordinator = LauncherBackupImportCoordinator()

        val result = coordinator.handleImportResult(LauncherBackupImportResult.Failure)

        assertEquals(LauncherBackupImportOutcome.Failure, result)
    }
}
