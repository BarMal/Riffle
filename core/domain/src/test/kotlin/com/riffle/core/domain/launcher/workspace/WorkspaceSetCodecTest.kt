package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceSetCodecTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    private fun page(id: String) =
        PageContainer(
            ContainerId(id),
            PageContent.Bound(LensBinding(Lens(sources = listOf(SourceId("apps"))), ExpressionKind.LIST)),
        )

    private fun ws(id: String) = Workspace(WorkspaceId(id), id, listOf(page("p-$id")))

    private fun layoutValue(
        deviceClass: String?,
        active: String?,
        default: String?,
        vararg workspaces: StoredValue,
    ) = obj(
        "deviceClass" to deviceClass?.let(::str),
        "active" to active?.let(::str),
        "default" to default?.let(::str),
        "workspaces" to arr(workspaces.toList()),
    )

    private fun setValue(vararg layouts: StoredValue) = obj("version" to num(1), "layouts" to arr(layouts.toList()))

    @Test
    fun roundTripsAMigratedSet() {
        val set = WorkspaceMigration.migrate(HomeLayoutSet.standard())
        assertEquals(set, WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set)))
    }

    @Test
    fun roundTripsActiveDefaultAndOrder() {
        val layout =
            LayoutWorkspaces.single(ws("a")).add(ws("b")).add(ws("c"), activate = true).withDefault(WorkspaceId("b"))
        val folded = LayoutWorkspaces.single(ws("f"))
        val set = WorkspaceSet(mapOf(phone to layout, HomeLayoutDeviceClass.FOLDABLE to folded))
        assertEquals(set, WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set)))
    }

    @Test
    fun encodedSetCarriesSchemaVersion() {
        val encoded = WorkspaceSetCodec.encode(WorkspaceSet())
        assertEquals(StoredValue.Num(CURRENT_WORKSPACE_SET_SCHEMA_VERSION.toLong()), encoded.fields["version"])
    }

    @Test
    fun nonObjectDecodesToNull() {
        assertNull(WorkspaceSetCodec.decode(null))
        assertNull(WorkspaceSetCodec.decode(StoredValue.Str("garbage")))
        assertNull(WorkspaceSetCodec.decode(StoredValue.Arr(emptyList())))
    }

    @Test
    fun missingOrMalformedLayoutsDecodeToAnEmptySet() {
        assertEquals(WorkspaceSet(), WorkspaceSetCodec.decode(obj()))
        assertEquals(WorkspaceSet(), WorkspaceSetCodec.decode(obj("layouts" to str("nope"))))
        assertEquals(
            WorkspaceSet(),
            WorkspaceSetCodec.decode(setValue(str("x"), num(3), StoredValue.Arr(emptyList()), obj())),
        )
    }

    @Test
    fun unknownDeviceClassAndMissingDeviceClassAreDropped() {
        val good = WorkspaceCodec.encode(ws("a"))
        val decoded =
            WorkspaceSetCodec.decode(
                setValue(
                    layoutValue("HOLOGRAM", "a", "a", good),
                    layoutValue(null, "a", "a", good),
                    layoutValue("PHONE", "a", "a", good),
                ),
            )!!
        assertEquals(setOf(phone), decoded.layouts.keys)
    }

    @Test
    fun brokenWorkspacesAreDroppedAndStaleIdsFallBack() {
        val a = WorkspaceCodec.encode(ws("a"))
        val b = WorkspaceCodec.encode(ws("b"))
        val noPages = WorkspaceCodec.encode(Workspace(WorkspaceId("empty"), "empty"))
        val decoded =
            WorkspaceSetCodec.decode(
                setValue(layoutValue("PHONE", "gone", null, str("junk"), noPages, obj("name" to str("no id")), a, b)),
            )!!
        val layout = decoded.workspacesFor(phone)
        assertEquals(listOf("a", "b"), layout.workspaces.map { it.id.value })
        assertEquals(WorkspaceId("a"), layout.defaultId)
        assertEquals(WorkspaceId("a"), layout.activeId)
    }

    @Test
    fun layoutWithNoUsableWorkspaceIsDroppedSoItReadsAsTheDefault() {
        val decoded = WorkspaceSetCodec.decode(setValue(layoutValue("PHONE", "a", "a", str("junk"))))!!
        assertTrue(decoded.layouts.isEmpty())
        assertEquals(WorkspaceMigration.defaultFor(phone), decoded.workspacesFor(phone))
    }

    @Test
    fun duplicateWorkspaceIdsKeepTheFirst() {
        val first = WorkspaceCodec.encode(ws("a").copy(name = "first"))
        val second = WorkspaceCodec.encode(ws("a").copy(name = "second"))
        val decoded = WorkspaceSetCodec.decode(setValue(layoutValue("PHONE", "a", "a", first, second)))!!
        assertEquals(listOf("first"), decoded.workspacesFor(phone).workspaces.map { it.name })
    }

    @Test
    fun aNewerSchemaVersionIsReadBestEffort() {
        val layout = layoutValue("PHONE", "a", "a", WorkspaceCodec.encode(ws("a")))
        val value = obj("version" to num(99), "layouts" to arr(listOf(layout)))
        assertEquals(setOf(phone), WorkspaceSetCodec.decode(value)!!.layouts.keys)
    }

    @Test
    fun encodingNeverContainsItemContent() {
        // Items have no codec: the encoded tree only ever names sources by id.
        val text = WorkspaceSetCodec.encode(WorkspaceMigration.migrate(HomeLayoutSet.standard())).toString()
        assertTrue("home.grid" in text)
        assertTrue("title" !in text && "body" !in text)
    }
}
