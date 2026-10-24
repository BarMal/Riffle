package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.workspace.pool.NewAppPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WorkspaceDockAndNewAppsCodecTest {
    private val dynamic = LensBinding(Lens(sources = listOf(SourceId("notifications"))), ExpressionKind.ICON_ROW)

    private fun ws(
        presentation: DockPresentation? = null,
        newApps: WorkspaceNewApps? = null,
    ) = Workspace(
        WorkspaceId("w"),
        "W",
        dock = WorkspaceDock(dynamic, presentation),
        newApps = newApps,
    )

    private fun roundTrip(workspace: Workspace) = WorkspaceCodec.decodeWorkspace(WorkspaceCodec.encode(workspace))

    private fun dockOf(encoded: StoredValue.Obj) = encoded.fields["dock"] as StoredValue.Obj

    @Test
    fun dockPresentationAndNewAppsRoundTrip() {
        listOf(
            ws(),
            ws(DockPresentation(hidden = true)),
            ws(DockPresentation(edge = DockPosition.LEFT, iconSizeDp = 40)),
            ws(DockPresentation(true, DockPosition.RIGHT, 56), WorkspaceNewApps(NewAppPlacement.HOME_AND_FINDER, true)),
            ws(newApps = WorkspaceNewApps(NewAppPlacement.FINDER_ONLY, autoPages = false)),
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun followingIsStoredAsNoKeySoTheEncodingIsUnchanged() {
        val encoded = WorkspaceCodec.encode(ws(DockPresentation()))
        assertEquals(setOf("dynamic"), dockOf(encoded).fields.keys)
        assertFalse("newApps" in encoded.fields)
        assertEquals(WorkspaceCodec.encode(ws()), encoded)
        assertNull(roundTrip(ws(DockPresentation()))!!.dock.presentation)
    }

    @Test
    fun theKeysAreAdditiveAndTheDynamicSectionIsWhereAnOlderDecoderReadsIt() {
        val encoded = WorkspaceCodec.encode(ws(DockPresentation(hidden = true)))
        assertEquals(num(CURRENT_WORKSPACE_SCHEMA_VERSION), encoded.fields["version"])
        val dock = dockOf(encoded)
        assertEquals(WorkspaceCodec.encode(ws()).let(::dockOf).fields["dynamic"], dock.fields["dynamic"])
        // An older build drops the unknown key; what remains decodes to "follow the shared dock".
        val older = StoredValue.Obj(encoded.fields + ("dock" to StoredValue.Obj(dock.fields - "presentation")))
        assertEquals(ws(), WorkspaceCodec.decodeWorkspace(StoredValue.Obj(older.fields - "newApps")))
    }

    @Test
    fun hostileValuesAreDroppedOrClampedNeverThrown() {
        val base = WorkspaceCodec.encode(ws())

        fun decodeWith(
            presentation: StoredValue?,
            newApps: StoredValue? = null,
        ): Workspace? {
            val dock = StoredValue.Obj(dockOf(base).fields + listOfNotNull(presentation?.let { "presentation" to it }))
            val fields = base.fields + ("dock" to dock) + listOfNotNull(newApps?.let { "newApps" to it })
            return WorkspaceCodec.decodeWorkspace(StoredValue.Obj(fields))
        }
        val bad = obj("hidden" to str("yes"), "edge" to str("DIAGONAL"), "iconSize" to num(Long.MAX_VALUE))
        assertEquals(DockPresentation(iconSizeDp = 56), decodeWith(bad)!!.dock.presentation)
        assertEquals(DockPresentation(iconSizeDp = 32), decodeWith(obj("iconSize" to num(-5)))!!.dock.presentation)
        assertNull(decodeWith(obj("edge" to num(3)))!!.dock.presentation)
        assertNull(decodeWith(str("hidden"))!!.dock.presentation)
        assertNull(decodeWith(null, obj("placement" to str("EVERYWHERE")))!!.newApps)
        assertNull(decodeWith(null, str("HOME_AND_FINDER"))!!.newApps)
        assertEquals(
            WorkspaceNewApps(NewAppPlacement.HOME_AND_FINDER, autoPages = false),
            decodeWith(null, obj("placement" to str("HOME_AND_FINDER")))!!.newApps,
        )
    }

    @Test
    fun theNewAppSettingFollowsThePresetUntilChosen() {
        assertEquals(NewAppDefaults.HOME_AND_PAGES, NewAppDefaults.resolve(ws().copy(presetId = "ios")))
        listOf(null, "nova", "niagara", "timescape", "kvaesitso", "unknown").forEach { id ->
            assertEquals(NewAppDefaults.MIGRATED, NewAppDefaults.resolve(ws().copy(presetId = id)), "$id")
        }
        val own = WorkspaceNewApps(NewAppPlacement.HOME_AND_FINDER, autoPages = false)
        assertEquals(own, NewAppDefaults.resolve(ws(newApps = own)))
        assertEquals(
            NewAppDefaults.MIGRATED,
            NewAppDefaults.resolve(ws(newApps = WorkspaceNewApps()).copy(presetId = "ios")),
        )
    }

    @Test
    fun duplicatingAWorkspaceKeepsItsOverrides() {
        val workspace = ws(DockPresentation(edge = DockPosition.LEFT), NewAppDefaults.HOME_AND_PAGES)
        var n = 0
        val copy = WorkspaceCopy.withFreshIds(workspace) { "id${++n}" }
        assertEquals(workspace.dock.presentation, copy.dock.presentation)
        assertEquals(workspace.newApps, copy.newApps)
    }
}
