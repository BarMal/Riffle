package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
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

    @Test
    fun rejectsImportWhenActiveLayoutIsMissing() {
        val document =
            LauncherBackupDocument(
                homeLayoutSet =
                    HomeLayoutSet(
                        activeKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE),
                        layouts = emptyMap(),
                    ),
                launcherSettings = LauncherSettings(),
            )
        val coordinator = LauncherBackupImportCoordinator()

        val result = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))

        assertEquals(LauncherBackupImportOutcome.Failure, result)
    }

    @Test
    fun rejectsImportWhenPageGridIsInvalid() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages =
                    listOf(
                        HomeLayoutDefaults.standard().selectedPage.copy(
                            grid = GridDimensions(columns = 0, rows = 5),
                        ),
                    ),
            )
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(layout),
                launcherSettings = LauncherSettings(),
            )
        val coordinator = LauncherBackupImportCoordinator()

        val result = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))

        assertEquals(LauncherBackupImportOutcome.Failure, result)
    }
}
