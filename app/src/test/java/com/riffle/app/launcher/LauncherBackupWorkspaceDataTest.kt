package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.pool.Arrangement
import com.riffle.core.domain.launcher.workspace.pool.ArrangementPage
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.Placement
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class LauncherBackupWorkspaceDataTest {
    private val layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())
    private val base =
        LauncherBackupDocument(
            homeLayoutSet = layoutSet,
            launcherSettings = LauncherSettings(),
            exportedAtEpochMillis = 1L,
        )

    private fun workspaceSetWithWidget(): WorkspaceSet {
        val migrated = WorkspaceMigration.migrate(layoutSet)
        val deviceClass = migrated.layouts.keys.first()
        val layout = migrated.layouts.getValue(deviceClass)
        val widget =
            PoolWidget(
                id = PoolItemId("widget-1"),
                label = "Clock",
                provider = WidgetProviderIdentity(AppPackageName("pkg"), WidgetProviderClassName("pkg.Provider")),
                hostedId = HostedWidgetId(77),
            )
        val workspaceId = layout.activeId
        val pool =
            PlacedItemPool(
                items = mapOf(widget.id to widget),
                arrangements =
                    mapOf(
                        workspaceId to
                            Arrangement(
                                pages =
                                    listOf(
                                        ArrangementPage(
                                            id = LauncherPageId("p1"),
                                            grid = GridDimensions(4, 4),
                                            placements =
                                                listOf(
                                                    Placement(widget.id, GridPlacement(GridCell(0, 0), GridSpan(2, 1))),
                                                ),
                                        ),
                                    ),
                            ),
                    ),
            )
        return migrated.withLayout(deviceClass, layout.copy(pool = pool))
    }

    private fun exclusions() =
        LayoutExclusionRules(mapOf(HomeLayoutDeviceClass.PHONE to ExclusionRuleSet.EMPTY), legacyMigrated = true)

    @Test
    fun nothingStoredMeansTheBackupIsByteForByteTheSame() {
        val plain = encodeLauncherBackupDocument(base)
        val withEmptySections =
            encodeLauncherBackupDocument(
                base.copy(workspaceSet = WorkspaceSet(), exclusions = LayoutExclusionRules()),
            )

        assertEquals(plain, withEmptySections)
        assertEquals(plain, exportWith(FakePort()))
    }

    @Test
    fun exportWritesSectionsAndNeverAHostId() {
        val json = exportWith(FakePort(set = workspaceSetWithWidget(), rules = exclusions()))

        val root = JSONObject(json)
        assertTrue(root.has("workspaces"))
        assertTrue(root.has("exclusions"))
        assertFalse(root.getJSONObject("workspaces").toString().contains("\"host\""))
        assertEquals(1, root.getInt("version"))
    }

    @Test
    fun roundTripRestoresWidgetsAsPlaceholders() {
        val port = FakePort(set = workspaceSetWithWidget(), rules = exclusions())
        val decoded = decodeLauncherBackupDocument(exportWith(port))

        val widgets =
            decoded.workspaceSet!!.layouts.values.flatMap { it.pool.items.values }.filterIsInstance<PoolWidget>()
        assertEquals(1, widgets.size)
        assertNull(widgets.single().hostedId)
        assertEquals(exclusions(), decoded.exclusions)
    }

    @Test
    fun olderBackupWithoutSectionsLeavesCurrentWorkspaceDataUntouched() {
        val port = FakePort(set = workspaceSetWithWidget(), rules = exclusions())

        val result = handler(port).importBackup { encodeLauncherBackupDocument(base).byteInputStream() }

        assertTrue(result is LauncherBackupImportHandlingResult.Imported)
        assertEquals(emptyList<String>(), port.restores)
    }

    @Test
    fun importReplacesOnlyTheSectionsPresent() {
        val port = FakePort()
        val document = base.copy(workspaceSet = workspaceSetWithWidget())

        handler(port).importBackup { encodeLauncherBackupDocument(document).byteInputStream() }

        assertEquals(listOf("workspaces"), port.restores)
    }

    @Test
    fun aFailingStoreNeverFailsTheImport() {
        val port = FakePort(failRestore = true)
        val document = base.copy(workspaceSet = workspaceSetWithWidget(), exclusions = exclusions())

        val result = handler(port).importBackup { encodeLauncherBackupDocument(document).byteInputStream() }

        assertTrue(result is LauncherBackupImportHandlingResult.Imported)
    }

    @Test
    fun aFailingStoreNeverFailsTheExport() {
        assertEquals(encodeLauncherBackupDocument(base), exportWith(FakePort(failRead = true)))
    }

    @Test
    fun malformedSectionsDropToAbsentAndNeverFailTheRestore() {
        val samples: List<Any> =
            listOf("garbage", 3, JSONObject.NULL, JSONArray(), JSONObject(), JSONObject("{\"layouts\":5}"))
        samples.forEach { sample ->
            val json =
                JSONObject(encodeLauncherBackupDocument(base))
                    .put("workspaces", sample)
                    .put("exclusions", sample)

            val decoded = decodeLauncherBackupDocument(json.toString())

            assertEquals(layoutSet, decoded.homeLayoutSet)
            assertNull(decoded.workspaceSet)
            assertNull(decoded.exclusions)
        }
    }

    @Test
    fun hostileSectionsNeverThrow() {
        repeat(200) { seed ->
            val random = Random(seed)
            val json =
                JSONObject(encodeLauncherBackupDocument(base))
                    .put("workspaces", hostile(random, 0))
                    .put("exclusions", hostile(random, 0))

            val decoded = decodeLauncherBackupDocument(json.toString())

            assertNotNull(decoded.homeLayoutSet)
        }
    }

    private fun hostile(
        random: Random,
        depth: Int,
    ): Any =
        when (random.nextInt(if (depth > 4) 3 else 5)) {
            0 -> listOf("", "PHONE", "w1", "\u0000", "x".repeat(300)).random(random)
            1 -> listOf(0L, -1L, Long.MAX_VALUE, Long.MIN_VALUE).random(random)
            2 -> random.nextBoolean()
            3 -> JSONArray().also { array -> repeat(random.nextInt(4)) { array.put(hostile(random, depth + 1)) } }
            else ->
                JSONObject().also { obj ->
                    listOf("layouts", "deviceClass", "workspaces", "pool", "library", "rules", "items", "version")
                        .shuffled(random)
                        .take(random.nextInt(5))
                        .forEach { key -> obj.put(key, hostile(random, depth + 1)) }
                }
        }

    private fun exportWith(port: WorkspaceBackupPort): String {
        val output = ByteArrayOutputStream()
        handler(port).exportBackup { output }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun handler(port: WorkspaceBackupPort) =
        LauncherBackupDocumentHandler(
            exportCoordinator =
                LauncherBackupExportCoordinator(
                    homeLayoutRepository = FixedLayouts(layoutSet),
                    appVisibilityRepository = NoHiddenApps,
                    currentState = { LauncherShellState(homeLayout = layoutSet.activeLayout) },
                    epochMillisProvider = { 1L },
                    workspaceBackup = port,
                ),
            importCoordinator = LauncherBackupImportCoordinator(),
            documentGateway = LauncherBackupDocumentGateway(),
            workspaceBackup = port,
        )

    private class FakePort(
        private val set: WorkspaceSet? = null,
        private val rules: LayoutExclusionRules? = null,
        private val failRead: Boolean = false,
        private val failRestore: Boolean = false,
    ) : WorkspaceBackupPort {
        val restores = mutableListOf<String>()

        override fun currentWorkspaceSet(): WorkspaceSet? = if (failRead) error("read") else set

        override fun currentExclusions(): LayoutExclusionRules? = if (failRead) error("read") else rules

        override fun restoreWorkspaceSet(set: WorkspaceSet) {
            if (failRestore) error("write")
            restores += "workspaces"
        }

        override fun restoreExclusions(rules: LayoutExclusionRules) {
            if (failRestore) error("write")
            restores += "exclusions"
        }
    }

    private class FixedLayouts(private val layoutSet: HomeLayoutSet) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout = layoutSet.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) = Unit

        override fun loadHomeLayoutSet(): HomeLayoutSet = layoutSet
    }

    private object NoHiddenApps : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }
}
