package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.Container
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceCodec
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceResolution
import com.riffle.core.domain.launcher.workspace.WorkspaceResolver
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkspacePresetsTest {
    /** What the WS1 adapters declare in BuiltInItemSources.kt, plus home.grid (owned by HomeLayout). */
    private val sources =
        listOf(
            SourceDescriptor(
                SourceIds.ALL_APPS,
                setOf(SourceCapability.GROUPABLE, SourceCapability.SEARCHABLE, SourceCapability.LIVE),
            ),
            SourceDescriptor(SourceIds.RECENT_APPS),
            SourceDescriptor(SourceIds.QUICK_ACTIONS, setOf(SourceCapability.GROUPABLE, SourceCapability.LIVE)),
            SourceDescriptor(
                SourceIds.NOTIFICATIONS,
                setOf(
                    SourceCapability.GROUPABLE,
                    SourceCapability.ACTIONABLE,
                    SourceCapability.PRIVACY_SENSITIVE,
                    SourceCapability.LIVE,
                ),
            ),
            SourceDescriptor(SourceIds.MEDIA, setOf(SourceCapability.PRIVACY_SENSITIVE, SourceCapability.LIVE)),
            SourceDescriptor(SourceIds.CALENDAR, setOf(SourceCapability.PRIVACY_SENSITIVE, SourceCapability.LIVE)),
            SourceDescriptor(WorkspaceSourceIds.HOME_GRID),
        )

    private val variants =
        WorkspacePresets.all.flatMap { preset -> PresetPosture.entries.map { preset to it } }

    private class Counter : WorkspaceIdFactory {
        var n = 0

        override fun next() = "id-${n++}"
    }

    @Test
    fun `catalog has the five presets with unique ids and Nova first`() {
        assertEquals(listOf("nova", "ios", "timescape", "niagara", "kvaesitso"), WorkspacePresets.all.map { it.id })
        assertEquals(WorkspacePresets.nova, WorkspacePresets.default)
        assertEquals("nova", WorkspacePresets.DEFAULT_ID)
        assertNotNull(WorkspacePresets.byId("ios"))
    }

    @Test
    fun `every variant validates with default capabilities and the real source descriptors`() {
        variants.forEach { (preset, posture) ->
            val ws = preset.variant(posture)
            assertEquals(emptyList(), WorkspaceValidation.validate(ws, sources = sources), "${preset.id} $posture")
            assertEquals(emptyList(), WorkspaceValidation.validate(ws), "${preset.id} $posture (no sources)")
        }
    }

    @Test
    fun `every variant resolves without fallback`() {
        val fallback = WorkspacePresets.defaultFor(HomeLayoutDeviceClass.PHONE)
        variants.forEach { (preset, posture) ->
            val resolution = WorkspaceResolver.resolve(preset.variant(posture), fallback, sources = sources)
            assertTrue(resolution is WorkspaceResolution.Resolved, "${preset.id} $posture")
        }
    }

    @Test
    fun `every variant round trips through the codec`() {
        variants.forEach { (preset, posture) ->
            val ws = preset.variant(posture)
            assertEquals(ws, WorkspaceCodec.decodeWorkspace(WorkspaceCodec.encode(ws)), "${preset.id} $posture")
        }
    }

    @Test
    fun `presets are lenses and containers only`() {
        variants.forEach { (preset, posture) ->
            val encoded = WorkspaceCodec.encode(preset.variant(posture))
            val keys = HashSet<String>().also { collectKeys(encoded, it) }
            assertTrue(keys.none { it in ITEM_CONTENT_KEYS }, "${preset.id} $posture $keys")
        }
    }

    @Test
    fun `variants have unique stable ids and distinct workspace ids`() {
        val ids = variants.map { (p, posture) -> p.variant(posture).id }
        assertEquals(ids.size, ids.toSet().size)
        variants.forEach { (preset, posture) ->
            val ws = preset.variant(posture)
            assertEquals(ws, preset.variant(posture))
            assertTrue(containerIds(ws).let { it.size == it.toSet().size })
            assertTrue(ws.id.value.startsWith("preset:${preset.id}:"))
        }
    }

    @Test
    fun `at most one finder page and every variant has one`() {
        variants.forEach { (preset, posture) ->
            val finders = preset.variant(posture).pages.filter { it is PageContainer && it.role == PageRole.FINDER }
            assertEquals(1, finders.size, "${preset.id} $posture")
        }
    }

    @Test
    fun `finder expressions match the documented choices`() {
        fun finder(preset: WorkspacePreset) =
            (
                (preset.compact.pages.first { it is PageContainer && it.role == PageRole.FINDER } as PageContainer)
                    .content as PageContent.Bound
            ).binding.expression
        assertEquals(ExpressionKind.ALPHA_LIST, finder(WorkspacePresets.nova))
        assertEquals(ExpressionKind.CATEGORIES, finder(WorkspacePresets.ios))
        assertEquals(ExpressionKind.CATEGORIES, finder(WorkspacePresets.timeScape))
        assertEquals(ExpressionKind.ALPHA_LIST, finder(WorkspacePresets.niagara))
        assertEquals(ExpressionKind.ALPHA_LIST, finder(WorkspacePresets.kvaesitso))
    }

    @Test
    fun `timescape follows the worked example`() {
        val compact = WorkspacePresets.timeScape.compact
        assertTrue(compact.pages[1] is PageSetContainer)
        assertEquals(ExpressionKind.ICON_ROW, compact.dock.dynamicSection?.expression)
        assertEquals(listOf(SourceIds.NOTIFICATIONS), compact.dock.dynamicSection?.lens?.sources)
        val now = (compact.pages[0] as PageContainer).content as PageContent.WidgetGrid
        assertEquals(
            listOf(ExpressionKind.CARD, ExpressionKind.CARD, ExpressionKind.ICON_ROW),
            now.placements.map { it.widget.binding.expression },
        )
        val expandedExpressions = WorkspaceValidation.expressionsUsed(WorkspacePresets.timeScape.expanded)
        assertTrue(ExpressionKind.INDEX in expandedExpressions && ExpressionKind.CARD_STACK in expandedExpressions)
    }

    @Test
    fun `presets reference only known sources and need no permission at install`() {
        val known = sources.map { it.id }.toSet()
        variants.forEach { (preset, posture) ->
            assertTrue(lensSources(preset.variant(posture)).all { it in known }, "${preset.id} $posture")
        }
        // Installing is pure data: it neither observes a source nor asks for access.
        val installed = WorkspacePresets.installPreset(WorkspacePresets.timeScape, PresetPosture.COMPACT)
        val used = lensSources(installed)
        assertTrue(SourceIds.NOTIFICATIONS in used && SourceIds.CALENDAR in used && SourceIds.MEDIA in used)
    }

    @Test
    fun `installing twice gives distinct ids and structurally equal workspaces`() {
        variants.forEach { (preset, posture) ->
            val a = WorkspacePresets.installPreset(preset, posture, Counter())
            val b = WorkspacePresets.installPreset(preset, posture, WorkspaceIdFactory { "other-${System.nanoTime()}" })
            assertNotEquals(a.id, b.id)
            val c = WorkspacePresets.installPreset(preset, posture)
            val d = WorkspacePresets.installPreset(preset, posture)
            assertNotEquals(c.id, d.id)
            assertTrue(containerIds(c).intersect(containerIds(d).toSet()).isEmpty())
            assertTrue(containerIds(c).intersect(containerIds(preset.variant(posture)).toSet()).isEmpty())
            assertEquals(stripIds(c), stripIds(d))
            assertEquals(stripIds(c), stripIds(preset.variant(posture)))
        }
    }

    @Test
    fun `an installed preset is an ordinary workspace that validates and can be edited`() {
        val installed = WorkspacePresets.installPreset(WorkspacePresets.ios, PresetPosture.COMPACT, Counter())
        assertEquals(emptyList(), WorkspaceValidation.validate(installed, sources = sources))
        assertEquals(null, installed.skinOverrideId)
        val edited = installed.copy(name = "Mine", pages = installed.pages.drop(1))
        assertEquals(emptyList(), WorkspaceValidation.validate(edited, sources = sources))
    }

    @Test
    fun `posture follows the device class`() {
        assertEquals(PresetPosture.COMPACT, PresetPosture.of(HomeLayoutDeviceClass.PHONE))
        assertEquals(PresetPosture.COMPACT, PresetPosture.of(HomeLayoutDeviceClass.PHONE_LANDSCAPE))
        assertEquals(PresetPosture.EXPANDED, PresetPosture.of(HomeLayoutDeviceClass.FOLDABLE))
        assertEquals(PresetPosture.EXPANDED, PresetPosture.of(HomeLayoutDeviceClass.TABLET))
        assertEquals(PresetPosture.EXPANDED, PresetPosture.of(HomeLayoutDeviceClass.DESKTOP))
        assertEquals(WorkspacePresets.nova.expanded, WorkspacePresets.defaultFor(HomeLayoutDeviceClass.TABLET))
        assertEquals(WorkspacePresets.nova.compact, WorkspacePresets.defaultFor(HomeLayoutDeviceClass.PHONE))
    }

    @Test
    fun `new install helper seeds Nova and leaves stored layouts and migration alone`() {
        val phone = HomeLayoutDeviceClass.PHONE
        val tablet = HomeLayoutDeviceClass.TABLET
        val stored = WorkspaceMigration.defaultFor(phone)
        val set = WorkspaceSet(mapOf(phone to stored))
        val seeded = PresetInstaller.withDefaultsFor(set, listOf(phone, tablet), Counter())
        assertEquals(stored, seeded.workspacesFor(phone))
        val tabletWorkspace = seeded.workspacesFor(tablet).active
        assertEquals("Nova", tabletWorkspace.name)
        assertEquals(seeded.workspacesFor(tablet).activeId, seeded.workspacesFor(tablet).defaultId)
        assertEquals(stripIds(WorkspacePresets.nova.expanded), stripIds(tabletWorkspace))
        val resolution = seeded.resolveActive(tablet, sources = sources)
        assertTrue(resolution is WorkspaceResolution.Resolved)
    }

    @Test
    fun `adding a preset to a layout keeps existing workspaces and can activate it`() {
        val phone = HomeLayoutDeviceClass.PHONE
        val base = WorkspaceSet()
        val added = PresetInstaller.addPreset(base, phone, WorkspacePresets.niagara, activate = true)
        val layout = added.workspacesFor(phone)
        assertEquals(2, layout.workspaces.size)
        assertEquals("Niagara", layout.active.name)
        assertEquals(WorkspaceMigration.defaultFor(phone).default.id, layout.defaultId)
    }

    private fun lensSources(ws: Workspace): Set<SourceId> =
        buildSet {
            fun lens(l: Lens) = addAll(l.sources)
            ws.dock.dynamicSection?.let { lens(it.lens) }
            ws.pages.forEach { page ->
                when (page) {
                    is PageSetContainer -> lens(page.binding.lens)
                    is PageContainer ->
                        when (val c = page.content) {
                            is PageContent.Bound -> lens(c.binding.lens)
                            is PageContent.WidgetGrid -> c.placements.forEach { lens(it.widget.binding.lens) }
                        }
                }
            }
        }

    private fun containerIds(ws: Workspace): List<ContainerId> =
        ws.pages.flatMap { page ->
            listOf<Container>(page) +
                ((page as? PageContainer)?.content as? PageContent.WidgetGrid)?.placements.orEmpty().map { it.widget }
        }.map { it.id }

    /** Replaces every id by its position, so two copies compare equal exactly when only ids differ. */
    private fun stripIds(ws: Workspace): Workspace {
        val order = containerIds(ws)
        val ids = order.withIndex().associate { (i, id) -> id to ContainerId("c$i") }
        return ws.copy(
            id = com.riffle.core.domain.launcher.workspace.WorkspaceId("w"),
            pages =
                ws.pages.map { page ->
                    when (page) {
                        is PageSetContainer -> page.copy(id = ids.getValue(page.id))
                        is PageContainer ->
                            page.copy(
                                id = ids.getValue(page.id),
                                content =
                                    when (val c = page.content) {
                                        is PageContent.Bound -> c
                                        is PageContent.WidgetGrid ->
                                            c.copy(
                                                placements =
                                                    c.placements.map {
                                                        it.copy(widget = it.widget.copy(id = newId(ids, it.widget.id)))
                                                    },
                                            )
                                    },
                            )
                    }
                },
        )
    }

    private fun newId(
        ids: Map<ContainerId, ContainerId>,
        id: ContainerId,
    ) = ids.getValue(id)

    private fun collectKeys(
        value: StoredValue,
        out: MutableSet<String>,
    ) {
        when (value) {
            is StoredValue.Obj ->
                value.fields.forEach { (k, v) ->
                    out += k
                    collectKeys(v, out)
                }
            is StoredValue.Arr -> value.items.forEach { collectKeys(it, out) }
            else -> Unit
        }
    }

    private companion object {
        val ITEM_CONTENT_KEYS =
            setOf("title", "subtitle", "body", "icon", "image", "actions", "target", "items", "ext", "groupLabel")
    }
}
