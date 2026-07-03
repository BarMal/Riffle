package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LauncherBackupDocumentHandlerTest {
    @Test
    fun exportsBackupAndReportsSuccessMessage() {
        val output = ByteArrayOutputStream()
        val document = backupDocument()
        val handler = handler(document = document)

        val result = handler.exportBackup { output }

        assertEquals(LauncherBackupMessage.EXPORTED, result.message)
        assertEquals(document, decodeLauncherBackupDocument(output.toString(Charsets.UTF_8.name())))
    }

    @Test
    fun reportsExportFailureMessage() {
        val handler = handler(document = backupDocument())

        val result = handler.exportBackup { null }

        assertEquals(LauncherBackupMessage.EXPORT_FAILED, result.message)
    }

    @Test
    fun importsBackupAndReturnsSettingsAction() {
        val document = backupDocument()
        val input = ByteArrayInputStream(encodeLauncherBackupDocument(document).toByteArray(Charsets.UTF_8))
        val handler = handler(document = document)

        val result = handler.importBackup { input }

        assertEquals(
            LauncherBackupImportHandlingResult.Imported(
                action = LauncherShellAction.ImportLauncherBackup(document),
                message = LauncherBackupMessage.IMPORTED,
            ),
            result,
        )
    }

    @Test
    fun reportsImportFailureMessage() {
        val handler = handler(document = backupDocument())

        val result = handler.importBackup { null }

        assertEquals(
            LauncherBackupImportHandlingResult.Failure(LauncherBackupMessage.IMPORT_FAILED),
            result,
        )
    }

    private fun handler(document: LauncherBackupDocument): LauncherBackupDocumentHandler =
        LauncherBackupDocumentHandler(
            exportCoordinator =
                LauncherBackupExportCoordinator(
                    homeLayoutRepository = FakeHomeLayoutRepository(document.homeLayoutSet),
                    currentState = { LauncherShellState(homeLayout = document.homeLayoutSet.activeLayout) },
                    epochMillisProvider = FixedEpochMillisProvider,
                ),
            importCoordinator = LauncherBackupImportCoordinator(),
            documentGateway = LauncherBackupDocumentGateway(),
        )

    private class FakeHomeLayoutRepository(
        private val layoutSet: HomeLayoutSet,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout = layoutSet.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) = Unit

        override fun loadHomeLayoutSet(): HomeLayoutSet = layoutSet
    }

    private fun backupDocument(): LauncherBackupDocument =
        LauncherBackupDocument(
            homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
            launcherSettings = LauncherSettings(),
            exportedAtEpochMillis = 123_456L,
        )

    private object FixedEpochMillisProvider : EpochMillisProvider {
        override fun nowEpochMillis(): Long = 123_456L
    }
}
