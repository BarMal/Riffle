package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val NOTES = SourceIds.NOTIFICATIONS
private val OTHER = SourceIds.RSS
private val KIND = ItemExtKey("demo.kind")
private const val NOW = 1_000_000_000L

/** Every content string carries one of these markers, so a leak sentinel can search the whole result. */
private const val TITLE_SECRET = "title-secret"
private const val SUBTITLE_SECRET = "subtitle-secret"
private const val BODY_SECRET = "body-secret"
private const val IMAGE_SECRET = "image-secret"
private const val EXT_SECRET = "ext-secret"
private const val ACTION_SECRET = "action-secret"

private fun secretItem(
    id: String,
    source: SourceId = NOTES,
    privacy: ItemPrivacy = ItemPrivacy.VISIBLE,
    pinned: Boolean = false,
) = Item(
    id = ItemId(id),
    sourceId = source,
    target = ItemTarget.App("pkg.$id"),
    title = "$TITLE_SECRET $id",
    subtitle = "$SUBTITLE_SECRET $id",
    body = "$BODY_SECRET $id",
    icon = ItemImageHandle("icon-$id"),
    image = ItemImageHandle("$IMAGE_SECRET-$id"),
    timeEpochMillis = NOW - id.hashCode().toLong().coerceIn(0L, 1_000L),
    groupKey = "app-${id.first()}",
    groupLabel = "App ${id.first()}",
    actions = listOf(ItemAction.Open(), ItemAction.Custom("custom", "$ACTION_SECRET $id")),
    privacy = privacy,
    ext =
        buildMap {
            put(KIND, ItemExtValue.Text("$EXT_SECRET $id"))
            if (pinned) put(PINNED_EXT_KEY, ItemExtValue.Flag(true))
        },
)

private fun levels(level: SourceContentLevel) = SourceContentLevels(mapOf(NOTES to level))

private fun eval(
    lens: Lens,
    items: List<Item>,
    level: SourceContentLevel,
) = DefaultLensEvaluator.evaluate(
    lens,
    items,
    LensEvaluationContext(nowEpochMillis = NOW, contentLevels = levels(level)),
)

private fun LensResult.items(): List<Item> =
    when (this) {
        is LensResult.Flat -> items
        is LensResult.Grouped -> groups.flatMap { it.items }
    }

/** The secrets a level must never let out, for items of the levelled source. */
private fun forbidden(level: SourceContentLevel): List<String> =
    when (level) {
        SourceContentLevel.SHOW -> emptyList()
        SourceContentLevel.HIDE_BODY -> listOf(SUBTITLE_SECRET, BODY_SECRET, IMAGE_SECRET)
        SourceContentLevel.HIDE_TEXT ->
            listOf(TITLE_SECRET, SUBTITLE_SECRET, BODY_SECRET, IMAGE_SECRET, EXT_SECRET, ACTION_SECRET)
    }

private val GROUPS = listOf(LensGroup.None, LensGroup.ByGroupKey, LensGroup.ByDay, LensGroup.ByExt(KIND))
private val SORTS =
    LensSortField.entries.flatMap { field ->
        SortDirection.entries.flatMap { direction ->
            listOf(false, true).map { pinned -> LensSort(field, direction, pinned) }
        }
    }

class SourceContentLevelTest {
    private val items =
        listOf(secretItem("b1"), secretItem("a2", pinned = true), secretItem("c3"), secretItem("a4"))

    @Test
    fun defaultIsShowAndUnknownStoredIdsAreRejected() {
        assertEquals(SourceContentLevel.SHOW, SourceContentLevel.DEFAULT)
        assertEquals(SourceContentLevel.SHOW, SourceContentLevels.ALL_SHOWN.levelOf(NOTES))
        assertEquals(ItemField.ALL, SourceContentLevels.ALL_SHOWN.ceilingOf(NOTES))
        SourceContentLevel.entries.forEach { level ->
            assertEquals(level, SourceContentLevel.fromStoredId(level.storedId))
        }
        assertNull(SourceContentLevel.fromStoredId("HIDE_TEXT"))
        assertNull(SourceContentLevel.fromStoredId(""))
    }

    @Test
    fun storedIdsAreAStableContract() {
        assertEquals(
            listOf("show", "hide_body", "hide_text"),
            SourceContentLevel.entries.map { it.storedId },
        )
    }

    @Test
    fun ceilingsMatchTheDesign() {
        assertEquals(ItemField.ALL, SourceContentLevel.SHOW.ceiling)
        assertEquals(
            setOf(ItemField.TITLE, ItemField.ICON, ItemField.TIME, ItemField.GROUP, ItemField.ACTIONS, ItemField.EXT),
            SourceContentLevel.HIDE_BODY.ceiling,
        )
        assertEquals(setOf(ItemField.ICON, ItemField.TIME, ItemField.GROUP), SourceContentLevel.HIDE_TEXT.ceiling)
    }

    @Test
    fun withDefaultRemovesTheEntry() {
        val hidden = SourceContentLevels.ALL_SHOWN.with(NOTES, SourceContentLevel.HIDE_TEXT)
        assertEquals(SourceContentLevel.HIDE_TEXT, hidden.levelOf(NOTES))
        assertEquals(SourceContentLevels.ALL_SHOWN, hidden.with(NOTES, SourceContentLevel.SHOW))
    }

    @Test
    fun showKeepsTodaysBehaviour() {
        val lens = Lens(sources = listOf(NOTES))
        val shown = eval(lens, items, SourceContentLevel.SHOW)
        val today = DefaultLensEvaluator.evaluate(lens, items, LensEvaluationContext(nowEpochMillis = NOW))
        assertEquals(today, shown)
        assertEquals(items, shown.items())
    }

    @Test
    fun hideBodyKeepsTitleAndIconAndDropsBodySubtitleAndImage() {
        val result = eval(Lens(sources = listOf(NOTES)), items, SourceContentLevel.HIDE_BODY).items()
        result.forEach { item ->
            assertNotNull(item.title)
            assertNotNull(item.icon)
            assertNull(item.body)
            assertNull(item.subtitle)
            assertNull(item.image)
            assertTrue(item.actions.isNotEmpty())
        }
    }

    @Test
    fun hideTextLeavesOnlyIconTimeGroupAndIdentity() {
        val result = eval(Lens(sources = listOf(NOTES)), items, SourceContentLevel.HIDE_TEXT).items()
        assertEquals(items.map { it.id }, result.map { it.id })
        result.zip(items).forEach { (shown, original) ->
            assertNull(shown.title)
            assertNull(shown.subtitle)
            assertNull(shown.body)
            assertNull(shown.image)
            assertTrue(shown.actions.isEmpty())
            assertTrue(shown.ext.isEmpty())
            assertEquals(original.icon, shown.icon)
            assertEquals(original.timeEpochMillis, shown.timeEpochMillis)
            assertEquals(original.groupKey, shown.groupKey)
            assertEquals(original.target, shown.target)
            assertEquals(original.privacy, shown.privacy)
        }
    }

    @Test
    fun noLevelLeaksForAnyGroupingSortOrLimit() {
        val lenses =
            GROUPS.flatMap { group ->
                SORTS.flatMap { sort ->
                    listOf(null, 1, 3).map { limit ->
                        Lens(sources = listOf(NOTES), group = group, sort = sort, limit = limit)
                    }
                }
            }
        SourceContentLevel.entries.flatMap { level -> lenses.map { level to it } }.forEach { (level, lens) ->
            val text = eval(lens, items, level).toString()
            val leaked = forbidden(level).filter { it in text }
            assertTrue(leaked.isEmpty(), "$level $lens leaked $leaked: $text")
        }
    }

    @Test
    fun levelNeverWidensALensProjection() {
        val lens = Lens(sources = listOf(NOTES), project = setOf(ItemField.ICON))
        SourceContentLevel.entries.forEach { level ->
            eval(lens, items, level).items().forEach { item ->
                assertNull(item.title)
                assertNull(item.body)
                assertNotNull(item.icon)
            }
        }
    }

    @Test
    fun sensitiveFieldsStillWinAtShow() {
        val sensitive = listOf(secretItem("s1", privacy = ItemPrivacy.SENSITIVE))
        val text = eval(Lens(sources = listOf(NOTES)), sensitive, SourceContentLevel.SHOW).toString()
        forbidden(SourceContentLevel.HIDE_TEXT).forEach { assertFalse(it in text, it) }
    }

    @Test
    fun otherSourcesKeepTheirContent() {
        val mixed = items + secretItem("r1", source = OTHER)
        val result = eval(Lens(sources = listOf(NOTES, OTHER)), mixed, SourceContentLevel.HIDE_TEXT).items()
        val rss = result.single { it.sourceId == OTHER }
        assertEquals("$TITLE_SECRET r1", rss.title)
        assertEquals("$BODY_SECRET r1", rss.body)
        assertTrue(result.filter { it.sourceId == NOTES }.all { it.title == null })
    }

    @Test
    fun titleSortCannotRevealHiddenTitles() {
        // With titles hidden every item has a missing sort key, so order falls back to the id tie-break.
        val lens = Lens(sources = listOf(NOTES), sort = LensSort(LensSortField.TITLE, SortDirection.DESCENDING))
        val ids = eval(lens, items, SourceContentLevel.HIDE_TEXT).items().map { it.id.value }
        assertEquals(items.map { it.id.value }.sorted(), ids)
    }

    @Test
    fun pinnedFlagIsIgnoredWhenTheLevelHidesExt() {
        val lens = Lens(sources = listOf(NOTES), sort = LensSort(pinnedFirst = true))
        assertEquals("a2", eval(lens, items, SourceContentLevel.HIDE_BODY).items().first().id.value)
        assertEquals("b1", eval(lens, items, SourceContentLevel.HIDE_TEXT).items().first().id.value)
    }

    @Test
    fun extGroupingCannotRevealHiddenExt() {
        val lens = Lens(sources = listOf(NOTES), group = LensGroup.ByExt(KIND))
        val hidden = eval(lens, items, SourceContentLevel.HIDE_TEXT) as LensResult.Grouped
        assertEquals(listOf(LensGrouping.UNGROUPED_KEY), hidden.groups.map { it.key })
        assertNull(hidden.groups.single().label)
        val shown = eval(lens, items, SourceContentLevel.HIDE_BODY) as LensResult.Grouped
        assertEquals(items.size, shown.groups.size)
    }

    @Test
    fun groupedByAppKeepsCountsPerAppAtEveryLevel() {
        val lens = Lens(sources = listOf(NOTES), group = LensGroup.ByGroupKey)
        val expected = (eval(lens, items, SourceContentLevel.SHOW) as LensResult.Grouped).groups
        SourceContentLevel.entries.forEach { level ->
            val groups = (eval(lens, items, level) as LensResult.Grouped).groups
            assertEquals(expected.map { it.key to it.items.size }, groups.map { it.key to it.items.size })
            assertEquals(expected.map { it.label }, groups.map { it.label })
        }
    }

    @Test
    fun searchResultsFollowTheSearchSourceLevel() {
        val results = listOf(secretItem("q1", source = SourceIds.SEARCH), secretItem("q2", source = SourceIds.SEARCH))
        val context =
            LensEvaluationContext(
                nowEpochMillis = NOW,
                contentLevels = SourceContentLevels(mapOf(SourceIds.SEARCH to SourceContentLevel.HIDE_TEXT)),
            )
        val text = DefaultLensEvaluator.evaluate(Lens(sources = listOf(SourceIds.SEARCH)), results, context).toString()
        forbidden(SourceContentLevel.HIDE_TEXT).forEach { assertFalse(it in text, it) }
    }

    @Test
    fun hiddenItemsAreStillFilteredAndCountedLikeVisibleOnes() {
        // The level redacts output only: filters, limits and exclusions see the same items at every level.
        val lens = Lens(sources = listOf(NOTES), limit = 2)
        SourceContentLevel.entries.forEach { level ->
            assertEquals(listOf("b1", "a2"), eval(lens, items, level).items().map { it.id.value })
        }
    }
}
