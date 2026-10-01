package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.workspace.CURRENT_WORKSPACE_SET_SCHEMA_VERSION
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSetJsonCodecTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    private fun workspace(id: String) =
        Workspace(
            WorkspaceId(id),
            "Name $id",
            listOf(
                PageContainer(
                    ContainerId("p-$id"),
                    PageContent.Bound(
                        LensBinding(
                            Lens(
                                sources = listOf(SourceId("apps.all")),
                                filter = LensFilter.GroupKeyIs("x"),
                                group = LensGroup.ByGroupKey,
                                limit = 7,
                            ),
                            ExpressionKind.CATEGORIES,
                        ),
                    ),
                ),
            ),
            skinOverrideId = "glass",
            gestureBindings = mapOf("swipe_up" to "finder"),
        )

    @Test
    fun roundTripsThroughJsonText() {
        val layout = LayoutWorkspaces.single(workspace("a")).add(workspace("b"), activate = true)
        val set =
            WorkspaceSet(
                mapOf(phone to layout, HomeLayoutDeviceClass.TABLET to LayoutWorkspaces.single(workspace("t"))),
            )

        assertEquals(set, decodeWorkspaceSet(encodeWorkspaceSet(set)))
    }

    @Test
    fun roundTripsAMigratedSet() {
        val set = WorkspaceMigration.migrate(HomeLayoutSet.standard())

        assertEquals(set, decodeWorkspaceSet(encodeWorkspaceSet(set)))
    }

    @Test
    fun encodedJsonCarriesSchemaVersionAndNoItemContent() {
        val json = JSONObject(encodeWorkspaceSet(WorkspaceMigration.migrate(HomeLayoutSet.standard())))

        assertEquals(CURRENT_WORKSPACE_SET_SCHEMA_VERSION, json.getInt("version"))
        assertFalse(json.toString().contains("\"title\""))
    }

    @Test
    fun garbageDecodesToNullWithoutThrowing() {
        listOf("", "   ", "not json", "[1,2,3]", "\"str\"", "42", "null", "{", "{\"layouts\": [").forEach { garbage ->
            assertNull(garbage, decodeWorkspaceSet(garbage))
        }
    }

    @Test
    fun emptyOrShapelessObjectsDecodeToAnEmptySet() {
        assertEquals(WorkspaceSet(), decodeWorkspaceSet("{}"))
        assertEquals(WorkspaceSet(), decodeWorkspaceSet("""{"layouts": "nope"}"""))
        assertEquals(WorkspaceSet(), decodeWorkspaceSet("""{"layouts": [1, "x", null, [], {}]}"""))
    }

    @Test
    fun unknownEnumFilterAndGroupValuesDecodeToDefaults() {
        val set =
            decodeWorkspaceSet(
                """
                {"version": 1, "layouts": [{
                  "deviceClass": "PHONE", "active": "a", "default": "a",
                  "workspaces": [{
                    "id": "a", "name": "A",
                    "pages": [{
                      "type": "page", "id": "p", "role": "MYSTERY",
                      "content": {"type": "bound", "binding": {
                        "expression": "HOLOGRAM",
                        "lens": {
                          "sources": ["apps.all"],
                          "filter": {"type": "telepathy"},
                          "group": {"type": "by_mood"},
                          "sort": {"field": "VIBES", "direction": "SIDEWAYS"}
                        }
                      }}
                    }]
                  }]
                }]}
                """.trimIndent(),
            )

        val page = set!!.workspacesFor(phone).active.pages.single() as PageContainer
        val binding = (page.content as PageContent.Bound).binding
        assertEquals(ExpressionKind.LIST, binding.expression)
        assertEquals(LensFilter.All, binding.lens.filter)
        assertEquals(LensGroup.None, binding.lens.group)
    }

    @Test
    fun missingFieldsAndWrongTypesAreDroppedOrDefaulted() {
        val set =
            decodeWorkspaceSet(
                """
                {"layouts": [
                  {"deviceClass": "NOT_A_CLASS", "workspaces": []},
                  {"workspaces": []},
                  {"deviceClass": "PHONE", "active": 5, "default": null, "workspaces": [
                    "junk", 7, {"name": "no id"}, {"id": "empty", "pages": []},
                    {"id": "ok", "pages": [{"type": "page", "id": "p", "content": {"type": "bound", "binding": {
                      "lens": {"sources": ["media"]}}}}]}
                  ]}
                ]}
                """.trimIndent(),
            )

        val layout = set!!.workspacesFor(phone)
        assertEquals(setOf(phone), set.layouts.keys)
        assertEquals(listOf("ok"), layout.workspaces.map { it.id.value })
        assertEquals(WorkspaceId("ok"), layout.activeId)
        assertEquals("", layout.active.name)
    }

    @Test
    fun layoutWithNothingUsableReadsAsTheDefault() {
        val set = decodeWorkspaceSet("""{"layouts": [{"deviceClass": "PHONE", "workspaces": ["x"]}]}""")

        assertNotNull(set)
        assertTrue(set!!.layouts.isEmpty())
        assertEquals(WorkspaceMigration.defaultFor(phone), set.workspacesFor(phone))
    }

    @Test
    fun floatingPointAndNullValuesDoNotCrashDecoding() {
        val set =
            decodeWorkspaceSet(
                """{"version": 1.5, "layouts": [{"deviceClass": "PHONE", "workspaces": [
                  {"id": "a", "pages": [{"type": "page", "id": "p", "content": {"type": "grid",
                    "columns": 4.0, "rows": null, "widgets": null}}]}]}]}""",
            )

        assertNotNull(set)
    }
}
