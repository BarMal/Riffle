package com.riffle.app.launcher.exclusions

import com.riffle.app.launcher.workspace.workspaceLensProvider
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class ExclusionsAppLayerTest {
    private class FakeStore(
        var stored: LayoutExclusionRules? = null,
        val failRead: Boolean = false,
    ) : ExclusionStorePort {
        val writes = mutableListOf<LayoutExclusionRules>()

        override suspend fun read(): LayoutExclusionRules? {
            if (failRead) error("disk")
            return stored
        }

        override suspend fun write(rules: LayoutExclusionRules) {
            writes += rules
            stored = rules
        }
    }

    private val hidden = AppIdentity(AppPackageName("chat"), AppActivityName("Main"))
    private val phone = HomeLayoutDeviceClass.PHONE

    @Test
    fun rulesAreEmptyBeforeInitialize() {
        assertTrue(CachedExclusionRepository(FakeStore()).rules(phone).isEmpty)
    }

    @Test
    fun initializeMigratesIntoEveryLayoutOnceAndPersists() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedExclusionRepository(store)
            repository.initialize(setOf(hidden), emptyList())
            HomeLayoutDeviceClass.entries.forEach { assertEquals(1, repository.rules(it).rules.size) }
            assertEquals(1, store.writes.size)

            val second = CachedExclusionRepository(store)
            second.initialize(setOf(hidden), emptyList())
            assertEquals(1, store.writes.size)
            assertEquals(repository.rules(phone), second.rules(phone))
        }

    @Test
    fun initializeNeverReplacesStoredRulesAndSurvivesReadFailure() =
        runBlocking {
            val mine = SourceExclusionRule(ExclusionRuleId("mine"), SourceIds.RSS, ExclusionMatcher.Group("g"))
            val stored = LayoutExclusionRules(mapOf(phone to ExclusionRuleSet(listOf(mine))), legacyMigrated = true)
            val store = FakeStore(stored)
            val repository = CachedExclusionRepository(store)
            repository.initialize(setOf(hidden), emptyList())
            assertEquals(listOf(mine), repository.rules(phone).rules)
            assertTrue(store.writes.isEmpty())

            val failing = FakeStore(stored, failRead = true)
            CachedExclusionRepository(failing).initialize(setOf(hidden), emptyList())
            assertTrue(failing.writes.isEmpty())
        }

    @Test
    fun jsonCodecRoundTripsAndRejectsGarbage() {
        val rule = SourceExclusionRule(ExclusionRuleId("a"), SourceIds.RSS, ExclusionMatcher.Group("g"))
        val rules =
            LayoutExclusionRules(
                mapOf(phone to ExclusionRuleSet(listOf(rule))),
                legacyMigrated = true,
            )
        assertEquals(rules, decodeExclusionRules(encodeExclusionRules(rules)))
        assertNull(decodeExclusionRules("not json"))
        assertNull(decodeExclusionRules("[1,2]"))
        assertNotNull(decodeExclusionRules("{}"))
    }

    @Test
    fun lensProviderAppliesTheLayoutsExclusionsAndDefaultsToNone() {
        val id = SourceIds.NOTIFICATIONS
        val items =
            listOf("a" to "chat", "b" to "mail").map { (name, pkg) ->
                Item(ItemId(name), id, ItemTarget.App(pkg, "personal"), title = name)
            }
        val source = FakeItemSource(SourceDescriptor(id, setOf(SourceCapability.LIVE)), SourceState.Ready(items))
        val registry = FakeSourceRegistry(listOf(source))
        val direct = Executor { it.run() }
        val rules =
            ExclusionRuleSet(listOf(SourceExclusionRule(ExclusionRuleId("r"), id, ExclusionMatcher.App("chat"))))

        fun ids(provider: com.riffle.core.domain.launcher.workspace.container.LensResultProvider): List<String> {
            var out = emptyList<String>()
            provider.observe(Lens(sources = listOf(id))) { output ->
                (output.result as? LensResult.Flat)?.let { out = it.items.map { item -> item.id.value } }
            }
            return out
        }
        assertEquals(listOf("a", "b"), ids(workspaceLensProvider(registry, direct)))
        assertEquals(listOf("b"), ids(workspaceLensProvider(registry, direct, exclusions = { rules })))
    }

    @Test
    fun updateIsRefusedBeforeInitializeAndAfterwardsPersistsNotifiesAndBumpsTheVersion() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedExclusionRepository(store, CoroutineScope(Dispatchers.Unconfined))
            assertNull(repository.update { it })

            var notified = 0
            val subscription = repository.observe { notified++ }
            repository.initialize(emptySet(), emptyList())
            assertEquals(1, notified)
            val version = repository.version.value

            val mine = SourceExclusionRule(ExclusionRuleId("mine"), SourceIds.RSS, ExclusionMatcher.Group("g"))
            val next = repository.update { it.update(phone) { set -> set.add(mine) } }

            assertEquals(listOf(mine), next?.forLayout(phone)?.rules)
            assertEquals(2, notified)
            assertEquals(version + 1, repository.version.value)
            assertEquals(next, store.stored)

            // An update that changes nothing is not a change.
            repository.update { it }
            assertEquals(2, notified)
            assertEquals(version + 1, repository.version.value)

            subscription.cancel()
            repository.update { it.update(phone) { set -> set.remove(mine.id) } }
            assertEquals(2, notified)
        }

    @Test
    fun aFailedReadNeverLetsAnUpdateOverwriteWhatIsOnDisk() =
        runBlocking {
            val stored = LayoutExclusionRules(legacyMigrated = true)
            val store = FakeStore(stored, failRead = true)
            val repository = CachedExclusionRepository(store, CoroutineScope(Dispatchers.Unconfined))
            repository.initialize(emptySet(), emptyList())

            repository.update {
                it.update(
                    phone,
                ) { set ->
                    set.add(
                        SourceExclusionRule(ExclusionRuleId("a"), SourceIds.RSS, ExclusionMatcher.Group("g")),
                    )
                }
            }

            assertEquals(1, repository.rules(phone).rules.size)
            assertTrue(store.writes.isEmpty())
        }
}
