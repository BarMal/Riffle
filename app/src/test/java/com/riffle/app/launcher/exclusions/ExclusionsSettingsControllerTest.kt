package com.riffle.app.launcher.exclusions

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
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.container.SourceBackedLensResultProvider
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatchMode
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.lens.AsyncLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.HideKind
import com.riffle.core.domain.launcher.workspace.settings.TextRuleDraft
import com.riffle.core.domain.launcher.workspace.settings.TextRuleProblem
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class ExclusionsSettingsControllerTest {
    private class FakeStore(var stored: LayoutExclusionRules? = null) : ExclusionStorePort {
        val writes = mutableListOf<LayoutExclusionRules>()

        override suspend fun read(): LayoutExclusionRules? = stored

        override suspend fun write(rules: LayoutExclusionRules) {
            writes += rules
            stored = rules
        }
    }

    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET
    private val notifications = SourceIds.NOTIFICATIONS
    private val chat = AppIdentity(AppPackageName("chat"), AppActivityName("Main"))

    private val items =
        listOf(
            Item(ItemId("n1"), notifications, ItemTarget.App("chat", "personal"), title = "Big sale today"),
            Item(ItemId("n2"), notifications, ItemTarget.App("chat", "personal"), title = "Hello"),
            Item(ItemId("n3"), notifications, ItemTarget.App("mail", "personal"), title = "Sale ends"),
        )
    private val source =
        FakeItemSource(SourceDescriptor(notifications, setOf(SourceCapability.LIVE)), SourceState.Ready(items))
    private val registry = FakeSourceRegistry(listOf(source))
    private val store = FakeStore()
    private val repository = CachedExclusionRepository(store, CoroutineScope(Dispatchers.Unconfined))
    private var counter = 0
    private var disabled = emptySet<SourceId>()

    private val controller =
        ExclusionsSettingsController(
            repository = repository,
            counterFor = { onCounts -> ExclusionMatchCounter(registry, onCounts) },
            disabledSources = { disabled },
            layoutName = { it.name.lowercase() },
            ids = WorkspaceIdFactory { "r${counter++}" },
            nowEpochMillis = { 5L },
        )

    private fun load(hidden: Set<AppIdentity> = emptySet()) = runBlocking { repository.initialize(hidden, emptyList()) }

    private fun contains(text: String) =
        TextRuleDraft(notifications, ExclusionTextField.TITLE, ExclusionMatchMode.CONTAINS, text)

    private fun available() = HomeLayoutDeviceClass.entries

    @Test
    fun `nothing loads and nothing changes before the rules are initialised`() {
        assertNull(controller.model(phone, available()))
        assertFalse(controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale"))))
        assertNull(controller.feedback.value)
        assertNull(repository.snapshot())
    }

    @Test
    fun `migrated hidden apps show as rules on every layout`() {
        load(setOf(chat))

        val model = controller.model(phone, available())!!

        assertEquals(1, model.ruleCount)
        assertTrue(model.rows.single().migrated)
        assertFalse(model.rows.single().onlyOnThisLayout)
    }

    @Test
    fun `adding a text rule persists it on that layout only and announces it`() {
        load()

        assertTrue(controller.dispatch(tablet, ExclusionsSettingsAction.AddText(contains("Sale"))))

        assertEquals(1, repository.rules(tablet).rules.size)
        assertTrue(repository.rules(phone).isEmpty)
        assertEquals(repository.snapshot(), store.stored)
        assertEquals("Added: Title contains \"sale\"", controller.feedback.value?.message)
        assertTrue(controller.feedback.value!!.canUndo)
        assertEquals(ExclusionRuleId("r0"), repository.rules(tablet).rules.single().id)
    }

    @Test
    fun `a refused add announces the reason and changes nothing`() {
        load()

        assertFalse(controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("ab"))))

        assertTrue(repository.rules(phone).isEmpty)
        assertEquals("Use at least 3 characters (wildcards do not count).", controller.feedback.value?.message)
        assertFalse(controller.feedback.value!!.canUndo)
        assertEquals(TextRuleProblem.TOO_SHORT, controller.problemWith(phone, contains("ab")))
        assertNull(controller.problemWith(phone, contains("abc")))
    }

    @Test
    fun `delete then undo restores the rules exactly`() {
        load()
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale")))
        val before = repository.snapshot()

        controller.dispatch(phone, ExclusionsSettingsAction.Delete(ExclusionRuleId("r0")))
        assertTrue(repository.rules(phone).isEmpty)
        assertTrue(controller.feedback.value!!.canUndo)

        controller.undo()

        assertEquals(before, repository.snapshot())
        assertEquals(before, store.stored)
        assertEquals("Undone", controller.feedback.value?.message)
        assertFalse(controller.feedback.value!!.canUndo)
    }

    @Test
    fun `undo is dropped when anything else changed the rules after the delete`() {
        load()
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale")))
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("other")))
        controller.dispatch(phone, ExclusionsSettingsAction.Delete(ExclusionRuleId("r0")))
        controller.dispatch(phone, ExclusionsSettingsAction.SetEnabled(ExclusionRuleId("r1"), false))
        val newer = repository.snapshot()

        controller.undo()

        assertEquals(newer, repository.snapshot())
        assertFalse(controller.feedback.value!!.canUndo)
    }

    @Test
    fun `undo is refused when the rules changed behind the announcement`() {
        load()
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale")))
        controller.dispatch(phone, ExclusionsSettingsAction.Delete(ExclusionRuleId("r0")))
        val outsider = SourceExclusionRule(ExclusionRuleId("x"), notifications, ExclusionMatcher.App("z"))
        repository.update { current -> current.update(HomeLayoutDeviceClass.DESKTOP) { it.add(outsider) } }
        val newer = repository.snapshot()

        controller.undo()

        assertEquals(newer, repository.snapshot())
        assertEquals(ExclusionsAnnouncements.CANNOT_UNDO, controller.feedback.value?.message)
    }

    @Test
    fun `leaving the page or dismissing the announcement ends the undo offer`() {
        load()
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale")))
        controller.dispatch(phone, ExclusionsSettingsAction.Delete(ExclusionRuleId("r0")))
        controller.leave()

        controller.undo()

        assertTrue(repository.rules(phone).isEmpty)
    }

    @Test
    fun `enable and disable are plain changes with no undo`() {
        load()
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale")))

        controller.dispatch(phone, ExclusionsSettingsAction.SetEnabled(ExclusionRuleId("r0"), false))

        assertFalse(repository.rules(phone).rules.single().enabled)
        assertFalse(controller.feedback.value!!.canUndo)
        assertEquals("Turned off: Title contains \"sale\"", controller.feedback.value?.message)
    }

    @Test
    fun `the contextual hook hides on this layout with undo and on all layouts when asked`() {
        load()
        val article = Item(ItemId("rss:f:1"), SourceIds.RSS, ItemTarget.None, title = "Hello world", groupKey = "f")

        assertTrue(controller.hide(phone, article, HideKind.GROUP))
        assertEquals("Hidden on phone: this feed", controller.feedback.value?.message)
        assertTrue(repository.rules(tablet).isEmpty)
        assertTrue(controller.feedback.value!!.canUndo)

        controller.undo()
        assertTrue(repository.rules(phone).isEmpty)

        assertTrue(controller.hide(phone, article, HideKind.ITEM, allLayouts = true))
        HomeLayoutDeviceClass.entries.forEach { assertEquals(1, repository.rules(it).rules.size) }
        assertFalse(controller.hide(phone, article, HideKind.ITEM, allLayouts = true))
        assertEquals("Already hidden: this article", controller.feedback.value?.message)
    }

    @Test
    fun `counts follow the viewed layout's rules and the items right now`() {
        load()
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale")))
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("hello")))
        controller.dispatch(phone, ExclusionsSettingsAction.SetEnabled(ExclusionRuleId("r1"), false))

        controller.open()
        controller.watch(phone)

        assertEquals(mapOf(ExclusionRuleId("r0") to 2), controller.counts.value)
        val model = controller.model(phone, available())!!
        assertEquals(listOf(2, null), model.rows.map { it.matchCount })

        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("ends")))
        assertEquals(1, controller.counts.value[ExclusionRuleId("r2")])
        controller.close()
        assertTrue(controller.counts.value.isEmpty())
    }

    @Test
    fun `a source that is off has no count and opening and closing releases every subscription`() {
        load()
        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale")))
        assertEquals(0, source.observerCount)

        controller.open()
        controller.watch(phone)
        assertEquals(1, source.observerCount)

        source.emit(SourceState.Off)
        assertTrue(controller.counts.value.isEmpty())
        disabled = setOf(notifications)
        assertEquals(null, controller.model(phone, available())!!.rows.single().matchCount)
        assertTrue(controller.model(phone, available())!!.rows.single().sourceOff)

        controller.close()
        assertEquals(0, source.observerCount)
        controller.close()
    }

    @Test
    fun `changing the viewed layout moves the subscriptions`() {
        load()
        controller.dispatch(tablet, ExclusionsSettingsAction.AddText(contains("sale")))
        controller.open()

        controller.watch(phone)
        assertEquals(0, source.observerCount)
        controller.watch(tablet)
        assertEquals(1, source.observerCount)
        controller.watch(phone)
        assertEquals(0, source.observerCount)
        controller.close()
    }

    @Test
    fun `a rule change re-evaluates a live lens through the repository's change signal`() {
        load()
        val provider =
            SourceBackedLensResultProvider(
                registry,
                AsyncLensEvaluator(Executor { it.run() }),
                contextChanges = repository,
                context = { LensEvaluationContext(0L, exclusions = repository.rules(phone)) },
            )
        var shown = emptyList<String>()
        val handle =
            provider.observe(Lens(sources = listOf(notifications))) { output ->
                (output.result as? LensResult.Flat)?.let { shown = it.items.map { item -> item.id.value } }
            }
        assertEquals(listOf("n1", "n2", "n3"), shown)

        controller.dispatch(phone, ExclusionsSettingsAction.AddText(contains("sale")))
        assertEquals(listOf("n2"), shown)

        controller.dispatch(phone, ExclusionsSettingsAction.SetEnabled(ExclusionRuleId("r0"), false))
        assertEquals(listOf("n1", "n2", "n3"), shown)

        controller.dispatch(phone, ExclusionsSettingsAction.SetEnabled(ExclusionRuleId("r0"), true))
        controller.dispatch(phone, ExclusionsSettingsAction.Delete(ExclusionRuleId("r0")))
        assertEquals(listOf("n1", "n2", "n3"), shown)
        controller.undo()
        assertEquals(listOf("n2"), shown)

        handle.cancel()
        controller.dispatch(phone, ExclusionsSettingsAction.Delete(ExclusionRuleId("r0")))
        assertEquals(listOf("n2"), shown)
    }
}
