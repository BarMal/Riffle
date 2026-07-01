package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LauncherBackupDocumentGatewayTest {
    @Test
    fun exportDocumentWritesEncodedBackupJson() {
        val output = ByteArrayOutputStream()
        val document = backupDocument()
        val gateway = LauncherBackupDocumentGateway()

        val result = gateway.exportDocument(document) { output }

        assertEquals(LauncherBackupExportResult.Success, result)
        assertEquals(document, decodeLauncherBackupDocument(output.toString(Charsets.UTF_8.name())))
    }

    @Test
    fun exportDocumentFailsWhenDestinationCannotOpen() {
        val gateway = LauncherBackupDocumentGateway()

        val result = gateway.exportDocument(backupDocument()) { null }

        assertEquals(LauncherBackupExportResult.Failure, result)
    }

    @Test
    fun importDocumentReturnsDecodedBackupDocument() {
        val document = backupDocument()
        val input = ByteArrayInputStream(encodeLauncherBackupDocument(document).toByteArray(Charsets.UTF_8))
        val gateway = LauncherBackupDocumentGateway()

        val result = gateway.importDocument { input }

        assertEquals(LauncherBackupImportResult.Imported(document), result)
    }

    @Test
    fun importDocumentFailsWhenSourceCannotOpen() {
        val gateway = LauncherBackupDocumentGateway()

        val result = gateway.importDocument { null }

        assertEquals(LauncherBackupImportResult.Failure, result)
    }

    @Test
    fun importDocumentFailsWhenSourceIsNotBackupJson() {
        val input = ByteArrayInputStream("not backup json".toByteArray(Charsets.UTF_8))
        val gateway = LauncherBackupDocumentGateway()

        val result = gateway.importDocument { input }

        assertEquals(LauncherBackupImportResult.Failure, result)
    }

    private fun backupDocument(): LauncherBackupDocument =
        LauncherBackupDocument(
            homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
            launcherSettings = LauncherSettings(),
        )
}
