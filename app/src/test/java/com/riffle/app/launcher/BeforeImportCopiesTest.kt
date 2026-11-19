package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BeforeImportCopiesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val events = mutableListOf<String>()

    private fun document(columns: Int = 4) =
        LauncherBackupDocument(
            homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
            launcherSettings = LauncherSettings(),
            exportedAtEpochMillis = 100L * columns,
        )

    private fun handler(
        copies: BeforeImportCopies?,
        current: LauncherBackupDocument,
        port: WorkspaceBackupPort? = null,
    ) = LauncherBackupDocumentHandler(
        exportCoordinator =
            LauncherBackupExportCoordinator(
                homeLayoutRepository = FakeHome(current.homeLayoutSet),
                appVisibilityRepository = NoHidden,
                currentState = {
                    LauncherShellState(
                        homeLayout = current.homeLayoutSet.activeLayout,
                        launcherSettings = current.launcherSettings,
                    )
                },
                epochMillisProvider = { 555L },
            ),
        importCoordinator = LauncherBackupImportCoordinator(),
        documentGateway = LauncherBackupDocumentGateway(),
        workspaceBackup = port,
        beforeImport = copies,
    )

    private class FakeHome(private val set: HomeLayoutSet) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout = set.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) = Unit

        override fun loadHomeLayoutSet(): HomeLayoutSet = set
    }

    private object NoHidden : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }

    private fun stream(doc: LauncherBackupDocument) =
        encodeLauncherBackupDocument(doc).toByteArray(Charsets.UTF_8).inputStream()

    @Test
    fun importKeepsTheCurrentSetUpFirstAndTheCopyIsTheDocumentAnExportWrites() {
        val file = File(folder.root, "before.json")
        val copies = BeforeImportCopies(file)
        val result = handler(copies, current = document()).importBackup { stream(document()) }
        assertTrue(result is LauncherBackupImportHandlingResult.Imported)
        assertEquals(555L, copies.keptAtEpochMillis.value)
        val kept = decodeLauncherBackupDocument(file.readText())
        assertEquals(document().homeLayoutSet, kept.homeLayoutSet)
    }

    @Test
    fun theCopyIsWrittenBeforeAnythingIsReplaced() {
        val file = File(folder.root, "before.json")
        val copies = BeforeImportCopies(file)
        val port =
            object : WorkspaceBackupPort {
                override fun currentWorkspaceSet() = null

                override fun currentExclusions() = null

                override fun currentPool() = null

                override fun restoreWorkspaceSet(set: com.riffle.core.domain.launcher.workspace.WorkspaceSet) = Unit

                override fun restoreExclusions(
                    rules: com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules,
                ) {
                    events += "restore:copy-exists=${file.isFile}"
                }

                override fun restorePool(state: com.riffle.core.domain.launcher.workspace.pool.PoolStoreState) = Unit
            }
        val withWorkspaces =
            document().copy(
                exclusions =
                    com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules(
                        legacyMigrated = true,
                    ),
            )
        handler(copies, current = document(), port = port).importBackup { stream(withWorkspaces) }
        assertEquals(listOf("restore:copy-exists=true"), events)
    }

    @Test
    fun ifTheCopyCannotBeWrittenNothingIsImported() {
        val blocked = File(folder.root, "missing-dir/before.json")
        val copies = BeforeImportCopies(blocked)
        val result = handler(copies, current = document()).importBackup { stream(document()) }
        assertEquals(
            LauncherBackupImportHandlingResult.Failure(LauncherBackupMessage.IMPORT_NOT_KEPT),
            result,
        )
        assertNull(copies.keptAtEpochMillis.value)
    }

    @Test
    fun undoReImportsTheCopyThroughTheNormalPathWithoutReplacingIt() {
        val file = File(folder.root, "before.json")
        val copies = BeforeImportCopies(file)
        val before = document(columns = 4)
        val handler = handler(copies, current = before)
        handler.importBackup { stream(document(columns = 5)) }
        val undone = handler.importBackup(keepCopy = false) { copies.open() }
        assertTrue(undone is LauncherBackupImportHandlingResult.Imported)
        assertEquals(
            before.homeLayoutSet,
            (undone as LauncherBackupImportHandlingResult.Imported).action.document.homeLayoutSet,
        )
        copies.clear()
        assertFalse(file.exists())
        assertNull(copies.keptAtEpochMillis.value)
        assertNull(copies.open())
    }

    @Test
    fun aFileThatIsNotABackupLeavesNoCopyAndSaysWhy() {
        val copies = BeforeImportCopies(File(folder.root, "b.json"))
        val h = handler(copies, current = document())
        assertEquals(
            LauncherBackupImportHandlingResult.Failure(LauncherBackupMessage.IMPORT_NOT_A_BACKUP),
            h.importBackup { """{"hello":1}""".byteInputStream() },
        )
        assertEquals(
            LauncherBackupImportHandlingResult.Failure(LauncherBackupMessage.IMPORT_NEWER_VERSION),
            h.importBackup { """{"type":"riffleLauncherBackup","version":2}""".byteInputStream() },
        )
        assertEquals(
            LauncherBackupImportHandlingResult.Failure(LauncherBackupMessage.IMPORT_DAMAGED),
            h.importBackup { """{"type":"riffleLauncherBackup","version":1}""".byteInputStream() },
        )
        assertNull(copies.keptAtEpochMillis.value)
    }

    @Test
    fun theCopyIsExcludedFromAndroidBackupAndDeviceTransfer() {
        val path = "before_import_backup.json"
        val legacy = File("src/main/res/xml/backup_rules.xml").readText()
        val modern = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(legacy.contains("""<exclude domain="file" path="$path" />"""))
        assertTrue(modern.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>").contains(path))
        assertTrue(modern.substringAfter("<device-transfer>").substringBefore("</device-transfer>").contains(path))
    }

    @Test
    fun theUndoWindowHoldsAndReleasesWidgetIdsOncePerImport() {
        val copies = BeforeImportCopies(File(folder.root, "b.json"), { events += "hold" }, { events += "resume" })
        copies.announceImport()
        assertTrue(copies.announcementPending.value)
        copies.consumeAnnouncement()
        copies.closeUndoWindow()
        assertEquals(listOf("hold", "resume"), events)
    }
}
