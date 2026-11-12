package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private class FakeVisibility(
        var hiddenSet: Set<AppIdentity> = emptySet(),
    ) : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = hiddenSet

        override fun hideApp(identity: AppIdentity) {
            hiddenSet = hiddenSet + identity
        }

        override fun showApp(identity: AppIdentity) {
            hiddenSet = hiddenSet - identity
        }
    }

    @Test
    fun mergeAppHidesMovesAllAppsRulesIntoTheOneHiddenListAndDropsThem() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedExclusionRepository(store)
            repository.initialize(emptySet(), emptyList())
            val rule = SourceExclusionRule(ExclusionRuleId("r"), SourceIds.ALL_APPS, ExclusionMatcher.App("chat"))
            val recents = SourceExclusionRule(ExclusionRuleId("q"), SourceIds.RECENT_APPS, ExclusionMatcher.App("mail"))
            repository.update { rules ->
                rules.update(phone) { set -> set.add(rule).add(recents) }
            }
            val visibility = FakeVisibility(setOf(AppIdentity(AppPackageName("old"), AppActivityName("Main"))))
            val installed =
                listOf("chat", "mail", "old").map { pkg ->
                    InstalledApp(AppIdentity(AppPackageName(pkg), AppActivityName("Main")), pkg)
                }

            assertTrue(repository.mergeAppHides(installed, visibility))

            // Nothing hidden became visible, the rule's app joined the list, a recents-only hide stayed a rule.
            assertEquals(setOf("old", "chat"), visibility.hiddenSet.map { it.packageName.value }.toSet())
            assertEquals(listOf(recents), repository.rules(phone).rules)
            // Idempotent, and the dropped rule is stored (writes are asynchronous) so a restart cannot bring it back.
            assertTrue(!repository.mergeAppHides(installed, visibility))
            repeat(WRITE_POLLS) {
                if (store.stored?.forLayout(phone)?.rules != listOf(recents)) Thread.sleep(WRITE_POLL_MILLIS)
            }
            assertEquals(listOf(recents), store.stored?.forLayout(phone)?.rules)
        }

    @Test
    fun anAppUnhiddenAfterTheMergeStaysVisible() =
        runBlocking {
            val repository = CachedExclusionRepository(FakeStore())
            repository.initialize(setOf(hidden), emptyList())
            val installed = listOf(InstalledApp(hidden, "Chat"))
            val visibility = FakeVisibility(setOf(hidden))
            repository.mergeAppHides(installed, visibility)

            visibility.showApp(hidden)
            repository.mergeAppHides(installed, visibility)

            assertTrue(visibility.hiddenSet.isEmpty())
        }

    @Test
    fun mergeAppHidesDoesNothingBeforeInitialize() {
        val repository = CachedExclusionRepository(FakeStore())

        assertTrue(!repository.mergeAppHides(emptyList(), FakeVisibility()))
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

    @Test
    fun aRestoreFromBackupTellsObserversOnceLoaded() =
        runBlocking {
            val store = FakeStore()
            val repository = CachedExclusionRepository(store, CoroutineScope(Dispatchers.Unconfined))
            val backedUp = SourceExclusionRule(ExclusionRuleId("b"), SourceIds.RSS, ExclusionMatcher.Group("g"))
            val restored =
                LayoutExclusionRules(
                    mapOf(phone to ExclusionRuleSet(listOf(backedUp))),
                    legacyMigrated = true,
                )
            var notified = 0
            repository.observe { notified++ }

            repository.replaceFromBackup(restored)
            assertEquals(0, notified)
            assertEquals(restored, store.stored)

            repository.initialize(emptySet(), emptyList())
            assertEquals(1, notified)
            repository.replaceFromBackup(LayoutExclusionRules(legacyMigrated = true))

            assertEquals(2, notified)
            assertTrue(repository.rules(phone).isEmpty)
        }

    private companion object {
        const val WRITE_POLLS = 40
        const val WRITE_POLL_MILLIS = 50L
    }
}
