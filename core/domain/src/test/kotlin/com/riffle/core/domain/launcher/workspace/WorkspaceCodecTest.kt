package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceCodecTest {
    private val lens =
        Lens(
            sources = listOf(SourceId("notifications"), SourceId("media")),
            filter =
                LensFilter.AllOf(
                    listOf(
                        LensFilter.Not(LensFilter.GroupKeyIs("x")),
                        LensFilter.AnyOf(listOf(LensFilter.HasActions(), LensFilter.AgeAtMost(1_000))),
                        LensFilter.ExtEquals(ItemExtKey("media.playing"), ItemExtValue.Flag(true)),
                        LensFilter.PrivacyIs(ItemPrivacy.VISIBLE),
                        LensFilter.FromSource(SourceId("media")),
                    ),
                ),
            group = LensGroup.ByExt(ItemExtKey("app.profile")),
            sort = LensSort(LensSortField.TIME, SortDirection.DESCENDING, pinnedFirst = true),
            limit = 5,
            project = setOf(ItemField.TITLE, ItemField.ICON),
        )

    private val workspace =
        Workspace(
            id = WorkspaceId("timescape"),
            name = "TimeScape",
            pages =
                listOf(
                    PageContainer(
                        ContainerId("now"),
                        PageContent.WidgetGrid(
                            4,
                            4,
                            listOf(
                                WidgetPlacement(
                                    WidgetContainer(
                                        ContainerId("w1"),
                                        WidgetSpan(2, 1),
                                        LensBinding(lens.copy(group = LensGroup.None), ExpressionKind.LIST),
                                    ),
                                    column = 1,
                                    row = 2,
                                ),
                            ),
                        ),
                    ),
                    PageSetContainer(ContainerId("inbox"), LensBinding(lens, ExpressionKind.INDEX)),
                    PageContainer(
                        ContainerId("finder"),
                        PageContent.Bound(LensBinding(lens, ExpressionKind.CATEGORIES)),
                        PageRole.FINDER,
                    ),
                ),
            dock = WorkspaceDock(LensBinding(lens, ExpressionKind.ICON_ROW)),
            gestureBindings = mapOf("dock_pull" to "workspace_menu"),
            skinOverrideId = "glass",
        )

    @Test
    fun lensRoundTrips() {
        assertEquals(lens, WorkspaceCodec.decodeLens(WorkspaceCodec.encode(lens)))
    }

    @Test
    fun workspaceRoundTrips() {
        assertEquals(workspace, WorkspaceCodec.decodeWorkspace(WorkspaceCodec.encode(workspace)))
    }

    @Test
    fun defaultLensRoundTrips() {
        val plain = Lens(sources = listOf(SourceId("apps")))
        assertEquals(plain, WorkspaceCodec.decodeLens(WorkspaceCodec.encode(plain)))
    }

    private fun mutate(
        value: StoredValue.Obj,
        change: (Map<String, StoredValue>) -> Map<String, StoredValue>,
    ) = StoredValue.Obj(change(value.fields))

    @Test
    fun unknownEnumsFilterAndGroupTypesFallBackToDefaults() {
        val encoded = WorkspaceCodec.encode(lens)
        val corrupted =
            mutate(encoded) {
                it +
                    mapOf(
                        "filter" to StoredValue.Obj(mapOf("type" to StoredValue.Str("from_the_future"))),
                        "group" to StoredValue.Obj(mapOf("type" to StoredValue.Str("quantum"))),
                        "sort" to
                            StoredValue.Obj(
                                mapOf("field" to StoredValue.Str("VIBES"), "direction" to StoredValue.Str("SIDEWAYS")),
                            ),
                        "project" to StoredValue.Arr(listOf(StoredValue.Str("TITLE"), StoredValue.Str("NOPE"))),
                    )
            }
        val decoded = assertNotNull(WorkspaceCodec.decodeLens(corrupted))
        assertEquals(LensFilter.All, decoded.filter)
        assertEquals(LensGroup.None, decoded.group)
        assertEquals(LensSort(), decoded.sort)
        assertEquals(setOf(ItemField.TITLE), decoded.project)
    }

    @Test
    fun invalidStoredNumbersAreDroppedRatherThanThrown() {
        val encoded = WorkspaceCodec.encode(lens)
        val decoded =
            assertNotNull(WorkspaceCodec.decodeLens(mutate(encoded) { it + ("limit" to StoredValue.Num(-3)) }))
        assertNull(decoded.limit)
    }

    @Test
    fun lensWithoutUsableSourcesDoesNotDecode() {
        val encoded = WorkspaceCodec.encode(lens)
        assertNull(
            WorkspaceCodec.decodeLens(
                mutate(encoded) {
                    it + ("sources" to StoredValue.Arr(listOf(StoredValue.Str(" "))))
                },
            ),
        )
        assertNull(WorkspaceCodec.decodeLens(StoredValue.Str("nope")))
        assertNull(WorkspaceCodec.decodeLens(null))
    }

    @Test
    fun unknownExpressionFallsBackToListAndUnknownContainersAreDropped() {
        val encoded = WorkspaceCodec.encode(workspace)
        val pages = (encoded.fields.getValue("pages") as StoredValue.Arr).items.toMutableList()
        val setPage = pages[1] as StoredValue.Obj
        val binding = setPage.fields.getValue("binding") as StoredValue.Obj
        val unknownExpression = binding.fields + ("expression" to StoredValue.Str("HOLOGRAM"))
        pages[1] = StoredValue.Obj(setPage.fields + ("binding" to StoredValue.Obj(unknownExpression)))
        pages += StoredValue.Obj(mapOf("type" to StoredValue.Str("page_cube"), "id" to StoredValue.Str("x")))
        pages += StoredValue.Str("garbage")

        val decoded =
            assertNotNull(WorkspaceCodec.decodeWorkspace(mutate(encoded) { it + ("pages" to StoredValue.Arr(pages)) }))
        assertEquals(3, decoded.pages.size)
        assertEquals(ExpressionKind.LIST, (decoded.pages[1] as PageSetContainer).binding.expression)
    }

    @Test
    fun workspaceWithoutAnIdDoesNotDecodeAndGarbageNeverThrows() {
        assertNull(WorkspaceCodec.decodeWorkspace(StoredValue.Obj(emptyMap())))
        assertNull(WorkspaceCodec.decodeWorkspace(StoredValue.Arr(emptyList())))
        val junk =
            StoredValue.Obj(
                mapOf("id" to StoredValue.Str("w"), "pages" to StoredValue.Num(3), "dock" to StoredValue.Str("x")),
            )
        val decoded = assertNotNull(WorkspaceCodec.decodeWorkspace(junk))
        assertTrue(decoded.pages.isEmpty())
        assertNull(decoded.dock.dynamicSection)
    }

    @Test
    fun deeplyNestedFiltersAreBounded() {
        var filter: StoredValue.Obj =
            StoredValue.Obj(
                mapOf("type" to StoredValue.Str("group_key_is"), "key" to StoredValue.Str("k")),
            )
        repeat(50) { filter = StoredValue.Obj(mapOf("type" to StoredValue.Str("not"), "filter" to filter)) }
        val encoded = WorkspaceCodec.encode(lens)
        assertNotNull(WorkspaceCodec.decodeLens(mutate(encoded) { it + ("filter" to filter) }))
    }
}
