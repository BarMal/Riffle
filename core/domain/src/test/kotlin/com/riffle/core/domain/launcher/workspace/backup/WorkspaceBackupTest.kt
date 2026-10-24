package com.riffle.core.domain.launcher.workspace.backup

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSetCodec
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRulesCodec
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.llBinding
import com.riffle.core.domain.launcher.workspace.llCounter
import com.riffle.core.domain.launcher.workspace.llLens
import com.riffle.core.domain.launcher.workspace.llPage
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreCodec
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import com.riffle.core.domain.launcher.workspace.pool.W1
import com.riffle.core.domain.launcher.workspace.pool.add
import com.riffle.core.domain.launcher.workspace.pool.at
import com.riffle.core.domain.launcher.workspace.pool.emptyPool
import com.riffle.core.domain.launcher.workspace.pool.poolApp
import com.riffle.core.domain.launcher.workspace.pool.poolWidget
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceBackupTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    private fun workspace(
        binding: LensBinding = llBinding(),
        id: String = "w1",
    ) = Workspace(
        WorkspaceId(id),
        id,
        listOf(llPage("$id-p", binding)),
        WorkspaceDock(llBinding(ExpressionKind.ICON_ROW)),
    )

    private fun layoutWithLibrary(): LayoutWorkspaces {
        val added = LensLibrary().tryAdd("Shared", llLens(), llCounter("lib")) as LibraryAdd.Added
        return LayoutWorkspaces.single(workspace(llBinding(ExpressionKind.LIST, llLens(), added.id)))
            .copy(library = added.library)
    }

    private fun set() = WorkspaceSet(mapOf(phone to layoutWithLibrary()))

    private fun poolState(migrated: Boolean = true) =
        PoolStoreState(
            migrated = migrated,
            pools =
                mapOf(
                    phone to
                        emptyPool(listOf(W1))
                            .add(poolApp("a"), ws = W1, cell = at(0, 0))
                            .add(poolWidget("w", host = 41), ws = W1, cell = at(1, 0, 2, 1)),
                ),
        )

    private fun poolRoundTrip(state: PoolStoreState): PoolRestore? {
        val encoded = PoolStoreCodec.encode(WorkspaceBackup.exportPool(state)!!)
        return WorkspaceBackup.restorePool(PoolStoreCodec.decode(encoded).state)
    }

    private fun roundTrip(set: WorkspaceSet): WorkspaceSet? =
        WorkspaceBackup.restoreSet(
            WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(WorkspaceBackup.exportSet(set)!!)),
        ).also {
            assertNotNull(it)
        }?.set

    @Test
    fun `round trip keeps workspaces, library and refs`() {
        val restored = roundTrip(set())!!.layouts.getValue(phone)
        val original = set().layouts.getValue(phone)
        assertEquals(original.workspaces, restored.workspaces)
        assertEquals(original.library, restored.library)
    }

    @Test
    fun `restore counts workspaces and saved lenses`() {
        val restore = WorkspaceBackup.restoreSet(set())!!
        assertEquals(1, restore.workspaceCount)
        assertEquals(1, restore.savedLensCount)
        assertEquals(0, restore.repairedRefCount)
    }

    @Test
    fun `pool round trip keeps items, arrangements and the import flag but unbinds widgets`() {
        val original = poolState()
        val restore = poolRoundTrip(original)!!
        val restored = restore.state.pools.getValue(phone)
        val before = original.pools.getValue(phone)
        assertTrue(restore.state.migrated)
        assertEquals(before.arrangements, restored.arrangements)
        val widget = restored.items.getValue(PoolItemId("w")) as PoolWidget
        assertNull(widget.hostedId)
        assertEquals((before.items.getValue(PoolItemId("w")) as PoolWidget).provider, widget.provider)
        assertEquals(before.items[PoolItemId("a")], restored.items[PoolItemId("a")])
        assertEquals(1, restore.widgetPlaceholderCount)
        assertFalse(poolRoundTrip(poolState(migrated = false))!!.state.migrated)
    }

    @Test
    fun `pool export never writes a host id`() {
        val exported = WorkspaceBackup.exportPool(poolState())!!
        val widgets = exported.pools.getValue(phone).items.values.filterIsInstance<PoolWidget>()
        assertTrue(widgets.isNotEmpty() && widgets.all { it.hostedId == null })
        assertFalse(PoolStoreCodec.encode(exported).toString().contains("\"host\""))
    }

    @Test
    fun `pool arrangements are kept even when no restored workspace has their id`() {
        // Migrated arrangements are keyed by migrated workspace ids that preset workspaces borrow.
        val restored = WorkspaceBackup.restorePool(poolState())!!.state.pools.getValue(phone)
        assertEquals(setOf(W1), restored.arrangements.keys)
    }

    @Test
    fun `the workspace set no longer carries placed items`() {
        val legacy = WorkspaceSetCodec.encode(set())
        val layouts = legacy.fields.getValue("layouts") as StoredValue.Arr
        assertFalse(layouts.items.any { "pool" in (it as StoredValue.Obj).fields })
    }

    @Test
    fun `a dangling lens ref is repaired to its inline snapshot`() {
        val dangling = LayoutWorkspaces.single(workspace(llBinding(ExpressionKind.LIST, llLens(), LensId("gone"))))
        val restore = WorkspaceBackup.restoreSet(WorkspaceSet(mapOf(phone to dangling)))!!
        val layout = restore.set.layouts.getValue(phone)
        assertEquals(1, restore.repairedRefCount)
        assertEquals(emptyList(), LensLibraryOps.danglingRefs(layout))
        assertTrue(layout.workspaces.flatMap { WorkspaceBindings.sites(it) }.all { it.binding.ref == null })
    }

    @Test
    fun `a resolving ref takes the library lens`() {
        val added = LensLibrary().tryAdd("Shared", llLens(limit = 7), llCounter("lib")) as LibraryAdd.Added
        val stale = LayoutWorkspaces.single(workspace(llBinding(ExpressionKind.LIST, llLens(limit = 2), added.id)))
        val restored =
            WorkspaceBackup.restoreSet(WorkspaceSet(mapOf(phone to stale.copy(library = added.library))))!!
                .set.layouts.getValue(phone)
        val binding = WorkspaceBindings.sites(restored.workspaces.single()).first().binding
        assertEquals(added.id, binding.ref)
        assertEquals(llLens(limit = 7), binding.lens)
    }

    @Test
    fun `absent or empty sections map to null so they are omitted and current data is untouched`() {
        assertNull(WorkspaceBackup.exportSet(null))
        assertNull(WorkspaceBackup.exportSet(WorkspaceSet()))
        assertNull(WorkspaceBackup.restoreSet(null))
        assertNull(WorkspaceBackup.restoreSet(WorkspaceSet()))
        assertNull(WorkspaceBackup.exportExclusions(null))
        assertNull(WorkspaceBackup.exportExclusions(LayoutExclusionRules()))
        assertNull(WorkspaceBackup.restoreExclusions(null))
        assertNull(WorkspaceBackup.exportPool(null))
        assertNull(WorkspaceBackup.exportPool(PoolStoreState()))
        assertNull(WorkspaceBackup.restorePool(null))
        assertNotNull(WorkspaceBackup.exportPool(PoolStoreState(migrated = true)))
    }

    @Test
    fun `exclusions round trip including the migration flag`() {
        val rules =
            LayoutExclusionRules(
                mapOf(phone to ExclusionRuleSet.EMPTY, HomeLayoutDeviceClass.TABLET to ExclusionRuleSet.EMPTY),
                true,
            )
        val exported = WorkspaceBackup.exportExclusions(rules)!!
        assertEquals(
            rules,
            WorkspaceBackup.restoreExclusions(ExclusionRulesCodec.decode(ExclusionRulesCodec.encode(exported))),
        )
        assertNotNull(WorkspaceBackup.exportExclusions(LayoutExclusionRules(legacyMigrated = true)))
    }

    @Test
    fun `a future schema section is read best effort and an older one without a library decodes`() {
        val encoded = WorkspaceSetCodec.encode(WorkspaceBackup.exportSet(set())!!)
        val future =
            encoded.copy(
                fields = encoded.fields + ("version" to StoredValue.Num(99)) + ("novel" to StoredValue.Str("x")),
            )
        assertNotNull(WorkspaceBackup.restoreSet(WorkspaceSetCodec.decode(future)))
        val old = WorkspaceSetCodec.encode(WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(workspace()))))
        val restored = WorkspaceBackup.restoreSet(WorkspaceSetCodec.decode(old))!!
        assertEquals(LensLibrary(), restored.set.layouts.getValue(phone).library)
    }

    @Test
    fun `hostile data never throws and always yields a valid set or null`() {
        repeat(300) { seed ->
            val random = Random(seed)
            val hostileSet = WorkspaceSetCodec.decode(hostile(random, 0))
            val restore = WorkspaceBackup.restoreSet(hostileSet)
            restore?.set?.layouts?.values?.forEach { layout ->
                assertTrue(layout.workspaces.isNotEmpty())
                assertEquals(emptyList(), LensLibraryOps.danglingRefs(layout))
            }
            WorkspaceBackup.restorePool(PoolStoreCodec.decode(hostile(random, 0)).state)
            val rules = WorkspaceBackup.restoreExclusions(ExclusionRulesCodec.decode(hostile(random, 0)))
            assertTrue(
                rules == null ||
                    rules.layouts.values.all {
                            set ->
                        set.rules.map { it.id }.distinct().size == set.rules.size
                    },
            )
        }
    }

    @Test
    fun `fuzzed mutation of a valid blob never throws`() {
        val valid = WorkspaceSetCodec.encode(WorkspaceBackup.exportSet(set())!!)
        repeat(300) { seed ->
            val mutated = mutate(valid, Random(seed))
            WorkspaceBackup.restoreSet(WorkspaceSetCodec.decode(mutated))
        }
    }

    private fun hostile(
        random: Random,
        depth: Int,
    ): StoredValue =
        when (random.nextInt(if (depth > 4) 3 else 5)) {
            0 -> StoredValue.Str(listOf("", "PHONE", "w1", "ICON_ROW", "\u0000", "x".repeat(500)).random(random))
            1 -> StoredValue.Num(listOf(0L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 3L).random(random))
            2 -> StoredValue.Bool(random.nextBoolean())
            3 -> StoredValue.Arr(List(random.nextInt(4)) { hostile(random, depth + 1) })
            else ->
                StoredValue.Obj(
                    HOSTILE_KEYS
                        .shuffled(random)
                        .take(random.nextInt(6))
                        .associateWith { hostile(random, depth + 1) },
                )
        }

    private fun mutate(
        value: StoredValue,
        random: Random,
    ): StoredValue =
        when {
            random.nextInt(12) == 0 -> hostile(random, 3)
            value is StoredValue.Obj ->
                StoredValue.Obj(
                    value.fields.filter { random.nextInt(15) != 0 }.mapValues { (_, v) -> mutate(v, random) },
                )
            value is StoredValue.Arr ->
                StoredValue.Arr(
                    value.items.filter { random.nextInt(15) != 0 }.map { mutate(it, random) },
                )
            else -> value
        }
}

private val HOSTILE_KEYS =
    listOf(
        "layouts", "deviceClass", "workspaces", "pool", "library",
        "rules", "items", "ref", "id", "version", "active", "migrated",
    )
