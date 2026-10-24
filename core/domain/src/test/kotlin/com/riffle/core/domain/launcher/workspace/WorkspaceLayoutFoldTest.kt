package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.workspace.backup.WorkspaceBackup
import com.riffle.core.domain.launcher.workspace.preset.PresetInstaller
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Owner decision N10: a landscape phone is the phone layout for every Workspaces value. */
class WorkspaceLayoutFoldTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val landscape = HomeLayoutDeviceClass.PHONE_LANDSCAPE
    private val foldable = HomeLayoutDeviceClass.FOLDABLE

    private fun page(id: String) =
        PageContainer(
            ContainerId(id),
            PageContent.Bound(LensBinding(Lens(sources = listOf(SourceId("apps"))), ExpressionKind.LIST)),
        )

    private fun ws(id: String) = Workspace(WorkspaceId(id), id, listOf(page("p-$id")))

    private fun layoutOf(vararg ids: String): LayoutWorkspaces =
        ids.drop(1).fold(LayoutWorkspaces.single(ws(ids.first()))) { acc, id -> acc.add(ws(id)) }

    @Test
    fun onlyTheLandscapePhoneMapsToAnotherClass() {
        assertEquals(phone, landscape.workspaceLayoutClass())
        HomeLayoutDeviceClass.entries.filter { it != landscape }.forEach { assertEquals(it, it.workspaceLayoutClass()) }
        assertEquals(HomeLayoutDeviceClass.entries - landscape, WorkspaceLayoutClasses.entries)
        assertEquals(setOf(phone, foldable), WorkspaceLayoutClasses.of(listOf(foldable, landscape, phone)))
        assertEquals(listOf(phone, foldable), WorkspaceLayoutClasses.of(listOf(foldable, landscape)).toList())
    }

    @Test
    fun aRotatedPhoneReadsAndWritesThePhoneWorkspaces() {
        val set = WorkspaceSet(mapOf(phone to layoutOf("a", "b")))
        assertEquals(set.workspacesFor(phone), set.workspacesFor(landscape))
        assertEquals(set.resolveActive(phone), set.resolveActive(landscape))
        assertTrue(set.hasLayout(landscape))

        val switched = set.update(landscape) { it.activate(WorkspaceId("b")) }
        assertEquals(setOf(phone), switched.layouts.keys)
        assertEquals(WorkspaceId("b"), switched.workspacesFor(phone).activeId)
        assertSame(set, set.copyFromOtherLayout(phone, landscape))
        assertSame(set, set.copyFromOtherLayout(landscape, phone))
    }

    @Test
    fun anUnstoredLandscapeReadsThePhoneDefault() {
        assertEquals(WorkspaceSet().workspacesFor(phone), WorkspaceSet().workspacesFor(landscape))
        assertFalse(WorkspaceSet().hasLayout(landscape))
    }

    @Test
    fun onlyLandscapeStoredBecomesThePhoneLayout() {
        val stored = WorkspaceSet(mapOf(landscape to layoutOf("l"), foldable to layoutOf("f")))
        val folded = WorkspaceLayoutFold.fold(stored)
        assertEquals(mapOf(phone to layoutOf("l"), foldable to layoutOf("f")), folded.layouts)
        assertEquals(folded, WorkspaceLayoutFold.fold(folded))
    }

    @Test
    fun bothStoredKeepsThePhoneLayoutAndDropsLandscape() {
        val stored = WorkspaceSet(mapOf(landscape to layoutOf("l"), phone to layoutOf("p", "q")))
        assertEquals(mapOf(phone to layoutOf("p", "q")), WorkspaceLayoutFold.fold(stored).layouts)
    }

    @Test
    fun neitherStoredIsUnchanged() {
        val stored = WorkspaceSet(mapOf(foldable to layoutOf("f")))
        assertSame(stored, WorkspaceLayoutFold.fold(stored))
        assertSame(WorkspaceSet().layouts, WorkspaceLayoutFold.fold(WorkspaceSet()).layouts)
    }

    @Test
    fun theGenericFoldMergesOnlyWhenBothExist() {
        val merged = WorkspaceLayoutFold.foldMap(mapOf(landscape to 2, phone to 1)) { own, other -> own * 10 + other }
        assertEquals(mapOf(phone to 12), merged)
        assertEquals(mapOf(phone to 2), WorkspaceLayoutFold.foldMap(mapOf(landscape to 2)) { _, _ -> error("unused") })
    }

    @Test
    fun decodingAnOlderBlobFoldsItsLandscapeLayout() {
        val onlyLandscape = WorkspaceSet(mapOf(landscape to layoutOf("l")))
        assertEquals(mapOf(phone to layoutOf("l")), WorkspaceSetCodec.decode(encodeRaw(onlyLandscape))?.layouts)

        val both = WorkspaceSet(mapOf(phone to layoutOf("p"), landscape to layoutOf("l")))
        assertEquals(mapOf(phone to layoutOf("p")), WorkspaceSetCodec.decode(encodeRaw(both))?.layouts)
        // Order in the file does not matter: the phone layout wins either way.
        val reversed = WorkspaceSet(linkedMapOf(landscape to layoutOf("l"), phone to layoutOf("p")))
        assertEquals(mapOf(phone to layoutOf("p")), WorkspaceSetCodec.decode(encodeRaw(reversed))?.layouts)
    }

    @Test
    fun hostileLayoutEntriesNeverThrowAndNeverCreateALandscapeLayout() {
        val random = Random(10)
        val good = WorkspaceCodec.encode(ws("a"))
        val junk = listOf(str("PHONE_LANDSCAPE"), num(-1), StoredValue.Bool(true), arr(emptyList()), obj(), good)
        repeat(300) {
            val entries =
                List(random.nextInt(0, 6)) {
                    obj(
                        "deviceClass" to
                            listOf("PHONE", "PHONE_LANDSCAPE", "phone_landscape", "", "X").random(random)
                                .let(::str),
                        "active" to str(listOf("a", "b", "").random(random)),
                        "workspaces" to arr(List(random.nextInt(0, 3)) { junk.random(random) }),
                    )
                }
            val decoded = WorkspaceSetCodec.decode(obj("version" to num(2), "layouts" to arr(entries)))
            assertNotNull(decoded)
            assertFalse(landscape in decoded.layouts)
        }
    }

    @Test
    fun migrationFromHomeLayoutSetNeverCreatesALandscapeLayout() {
        val landscapeKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, landscape)
        val phoneKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, phone)
        val layoutSet =
            HomeLayoutSet(
                activeKey = landscapeKey,
                layouts =
                    mapOf(
                        landscapeKey to HomeLayoutDefaults.standard(landscape).copy(viewMode = landscapeKey.viewMode),
                        phoneKey to HomeLayoutDefaults.standard(phone).copy(viewMode = phoneKey.viewMode),
                    ),
                preferredModesByDeviceClass = mapOf(landscape to landscapeKey.viewMode, phone to phoneKey.viewMode),
            )
        val migrated = WorkspaceMigration.migrate(layoutSet)
        assertEquals(setOf(phone), migrated.layouts.keys)
        // Rotated at migration time: the phone layout still shows the phone's own mode.
        assertEquals(
            HomeLayoutWorkspaceMapper.workspaceId(phone, LauncherViewMode.HOME_SCREEN_LIBRARY),
            migrated.workspacesFor(phone).activeId,
        )
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.workspaceShownMode())

        val stored = WorkspaceSet(mapOf(landscape to layoutOf("l")))
        val ensured = WorkspaceMigration.ensureMigrated(stored, layoutSet)
        assertEquals(setOf(phone), ensured.layouts.keys)
        assertEquals(layoutOf("l"), ensured.workspacesFor(phone))
    }

    @Test
    fun aLandscapeOnlyHomeLayoutSetMigratesToThePhoneLayout() {
        val key = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, landscape)
        val layoutSet = HomeLayoutSet(activeKey = key, layouts = mapOf(key to HomeLayoutDefaults.standard(landscape)))
        val migrated = WorkspaceMigration.migrate(layoutSet)
        assertEquals(setOf(phone), migrated.layouts.keys)
        assertEquals(
            HomeLayoutWorkspaceMapper.workspaceId(phone, LauncherViewMode.CARD_INTERFACE),
            migrated.workspacesFor(phone).activeId,
        )
    }

    @Test
    fun seedingDefaultsNeverOverwritesThePhoneLayoutThroughTheLandscapeClass() {
        val stored = WorkspaceSet(mapOf(phone to layoutOf("p")))
        val seeded = PresetInstaller.withDefaultsFor(stored, HomeLayoutDeviceClass.entries)
        assertEquals(layoutOf("p"), seeded.workspacesFor(phone))
        assertFalse(landscape in seeded.layouts)
    }

    @Test
    fun restoringAnOlderBackupFoldsItsLandscapeLayout() {
        val restored = WorkspaceBackup.restoreSet(WorkspaceSet(mapOf(landscape to layoutOf("l", "m"))))
        assertNotNull(restored)
        assertEquals(setOf(phone), restored.set.layouts.keys)
        assertEquals(2, restored.workspaceCount)

        val both =
            WorkspaceBackup.restoreSet(
                WorkspaceSet(mapOf(landscape to layoutOf("l", "m"), phone to layoutOf("p"))),
            )
        assertEquals(1, both?.workspaceCount)
    }

    @Test
    fun theShownModeIsTheActiveOneOffTheLandscapePhone() {
        val key = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, foldable)
        val layoutSet = HomeLayoutSet(activeKey = key, layouts = emptyMap())
        assertEquals(LauncherViewMode.CARD_INTERFACE, layoutSet.workspaceShownMode())
        val rotated = HomeLayoutSet(activeKey = key.copy(deviceClass = landscape), layouts = emptyMap())
        // No remembered phone mode: falls back to the mode shown now.
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            rotated.copy(preferredModesByDeviceClass = emptyMap()).workspaceShownMode(),
        )
    }

    /** Encodes [set] without the fold, the way a build before N10 wrote it. */
    private fun encodeRaw(set: WorkspaceSet): StoredValue.Obj = WorkspaceSetCodec.encode(set)
}
