package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * A version 1 backup document as the build before the cut-over (ADR 0003, P8) wrote it, frozen as
 * `backups/riffle-backup-before-cutover.json`: placed items and a dock in the pool, two workspaces on the phone layout
 * (the second active, with lens pages, a Finder page, a saved-lens reference and a dock dynamic section), a saved
 * lens, a hidden app, an exclusion rule, a Stream layout, and settings that name things since removed (a gesture bound
 * to the workspace menu, a stage action, the Terminal theme preset, the Liquid glass switch, a dock effect).
 *
 * It must still import and restore Home, Dock, settings, hidden apps, exclusions and the Stream layout, and exporting
 * it again must keep writing the stored workspaces so a round trip loses nothing.
 */
class LauncherBackupBeforeCutoverTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    private val oldBackup: String =
        checkNotNull(javaClass.classLoader?.getResource("backups/riffle-backup-before-cutover.json")).readText()

    private class RecordingWorkspaceBackup : WorkspaceBackupPort {
        var workspaceSet: WorkspaceSet? = null
        var exclusions: LayoutExclusionRules? = null
        var pool: PoolStoreState? = null
        var stream: StreamLayouts? = null
        var calendarFeeds: IcsFeedSettings? = null

        override fun currentWorkspaceSet(): WorkspaceSet? = workspaceSet

        override fun currentExclusions(): LayoutExclusionRules? = exclusions

        override fun currentPool(): PoolStoreState? = pool

        override fun restoreWorkspaceSet(set: WorkspaceSet) {
            workspaceSet = set
        }

        override fun restoreExclusions(rules: LayoutExclusionRules) {
            exclusions = rules
        }

        override fun restorePool(state: PoolStoreState) {
            pool = state
        }

        override fun restoreStreamLayouts(layouts: StreamLayouts) {
            stream = layouts
        }

        override fun restoreCalendarFeeds(feeds: IcsFeedSettings) {
            calendarFeeds = feeds
        }
    }

    private fun handler(port: RecordingWorkspaceBackup) =
        LauncherBackupDocumentHandler(
            exportCoordinator = unusedExport,
            importCoordinator = LauncherBackupImportCoordinator(),
            documentGateway = LauncherBackupDocumentGateway(),
            workspaceBackup = port,
        )

    @Test
    fun theOldDocumentDecodesAndIsImportable() {
        val document = decodeLauncherBackupDocument(oldBackup)

        assertTrue(document.isImportableBackup())
        assertTrue(document.homeFromPool)
        assertEquals(1_700_000_000_000L, document.exportedAtEpochMillis)
    }

    @Test
    fun importingItSucceedsAndRestoresHomeDockSettingsHiddenAppsExclusionsAndTheStream() {
        val port = RecordingWorkspaceBackup()

        val result = handler(port).importBackup { ByteArrayInputStream(oldBackup.toByteArray(Charsets.UTF_8)) }

        val imported = result as LauncherBackupImportHandlingResult.Imported
        assertEquals(LauncherBackupMessage.IMPORTED, imported.message)
        val document = imported.action.document

        // Home and Dock: the layout set, and the pool that is the authority for the placed items.
        val home = document.homeLayoutSet.activeLayout
        assertEquals(2, home.pages.size)
        val pool = checkNotNull(port.pool).pools.getValue(phone)
        assertTrue("the pool holds Home's apps and widget", pool.items.size >= 4)
        assertEquals(1, checkNotNull(pool.dock).pins.size)

        // Hidden apps and exclusions.
        assertEquals(
            setOf(AppIdentity(AppPackageName("com.hidden"), AppActivityName("com.hidden.Main"))),
            document.hiddenAppIdentities,
        )
        assertEquals(1, checkNotNull(port.exclusions).forLayout(phone).rules.size)

        // The Stream layout.
        val stream = checkNotNull(port.stream).forPosture(phone)
        assertEquals(StreamView.LIST, stream.viewOf(StreamSection.NOW))
    }

    @Test
    fun importingTheOldDocumentLeavesTheCalendarFeedsAloneBecauseItCarriesNone() {
        val port = RecordingWorkspaceBackup()

        handler(port).importBackup { ByteArrayInputStream(oldBackup.toByteArray(Charsets.UTF_8)) }

        assertEquals(null, port.calendarFeeds)
    }

    @Test
    fun aBackupThatCarriesCalendarFeedsRestoresThemThroughThePort() {
        val port = RecordingWorkspaceBackup()
        val feeds =
            IcsFeedSettings(
                listOf(
                    IcsFeed(
                        IcsFeedId("f1"),
                        "Team",
                        FeedUrl.parse("https://calendar.example.com/team.ics").getOrThrow(),
                    ),
                ),
            )
        val document = decodeLauncherBackupDocument(oldBackup).copy(calendarFeeds = feeds)
        val encoded = encodeLauncherBackupDocument(document)

        handler(port).importBackup { ByteArrayInputStream(encoded.toByteArray(Charsets.UTF_8)) }

        assertEquals(feeds, port.calendarFeeds)
    }

    @Test
    fun settingsThatNameRemovedThingsDecodeAndTheRestIsKept() {
        val settings = decodeLauncherBackupDocument(oldBackup).launcherSettings
        val gestures = settings.gestures.homeGestures

        // Removed actions are not an error and never resolve to the removed action.
        assertNotEquals("OPEN_WORKSPACE_MENU", gestures.actionFor(HomeGesture.ONE_FINGER_UP).name)
        // Other bindings are untouched.
        assertEquals(LauncherGestureAction.OPEN_SETTINGS, gestures.actionFor(HomeGesture.TWO_FINGER_DOWN))
        assertEquals(LauncherGestureAction.OPEN_SEARCH, gestures.actionFor(HomeGesture.ONE_FINGER_DOWN))
    }

    @Test
    fun theStoredWorkspacesAreRestoredWhole() {
        val port = RecordingWorkspaceBackup()
        handler(port).importBackup { ByteArrayInputStream(oldBackup.toByteArray(Charsets.UTF_8)) }

        val layout = checkNotNull(port.workspaceSet).workspacesFor(phone)
        assertEquals(listOf("Nova", "TimeScape"), layout.workspaces.map { it.name })
        assertEquals(WorkspaceId("ws-timescape"), layout.activeId)
        assertEquals(WorkspaceId("preset:nova:compact"), layout.defaultId)

        val nova = layout.workspaces.first()
        assertTrue(nova.pages.filterIsInstance<PageContainer>().any { it.role == PageRole.FINDER })

        val timeScape = layout.active
        assertTrue(timeScape.pages.any { it is PageSetContainer })
        assertNotNull(timeScape.dock.dynamicSection)
        assertEquals(setOf(SourceIds.NOTIFICATIONS), layout.library.lenses.single().lens.sources.toSet())
        assertTrue("a placed page is still there to draw", WorkspaceHome.hasHomeGrid(timeScape))
    }

    @Test
    fun exportingItAgainKeepsWritingTheStoredWorkspacesSoARoundTripLosesNothing() {
        val document = decodeLauncherBackupDocument(oldBackup)

        val exported = org.json.JSONObject(encodeLauncherBackupDocument(document))

        assertTrue(exported.has("workspaces"))
        assertTrue(exported.has("pool"))
        assertTrue(exported.has("exclusions"))
        assertTrue(exported.has("stream"))
        assertEquals(document, decodeLauncherBackupDocument(exported.toString()))
        // And through the handler's own export path.
        val out = ByteArrayOutputStream()
        val port = RecordingWorkspaceBackup()
        port.workspaceSet = document.workspaceSet
        LauncherBackupDocumentGateway().exportDocument(document) { out }
        assertEquals(document, decodeLauncherBackupDocument(out.toString(Charsets.UTF_8.name())))
    }

    private val unusedExport: LauncherBackupExportCoordinator
        get() =
            LauncherBackupExportCoordinator(
                homeLayoutRepository = FakeLayouts,
                appVisibilityRepository = FakeVisibility,
                currentState = { com.riffle.core.domain.launcher.LauncherShellState() },
                epochMillisProvider = EpochMillisProvider { 0L },
            )

    private object FakeLayouts : com.riffle.core.domain.launcher.home.HomeLayoutRepository {
        private val set =
            com.riffle.core.domain.launcher.home.HomeLayoutSet.fromLayout(
                com.riffle.core.domain.launcher.home.HomeLayoutDefaults.standard(),
            )

        override fun loadHomeLayout() = set.activeLayout

        override fun saveHomeLayout(layout: com.riffle.core.domain.launcher.home.HomeLayout) = Unit

        override fun loadHomeLayoutSet() = set
    }

    private object FakeVisibility : com.riffle.core.domain.launcher.apps.AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }
}
