package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.lens.AsyncLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A rule change reaches live observations through [ContextChanges], and stops with the observation. */
class ContextChangesTest {
    private class Signal : ContextChanges {
        val listeners = CopyOnWriteArrayList<() -> Unit>()

        override fun observe(onChange: () -> Unit): SourceSubscription {
            listeners += onChange
            return SourceSubscription { listeners -= onChange }
        }

        fun fire() = listeners.forEach { it() }
    }

    private val id = SourceIds.NOTIFICATIONS
    private val items =
        listOf("a" to "chat", "b" to "mail").map { (name, pkg) ->
            Item(ItemId(name), id, ItemTarget.App(pkg, "personal"), title = name)
        }
    private val source =
        FakeItemSource(
            SourceDescriptor(id, setOf(SourceCapability.LIVE)),
            com.riffle.core.domain.launcher.workspace.SourceState.Ready(items),
        )
    private val registry = FakeSourceRegistry(listOf(source))
    private var rules = ExclusionRuleSet.EMPTY
    private val signal = Signal()
    private val seen = mutableListOf<List<String>>()

    private fun provider(changes: ContextChanges?) =
        SourceBackedLensResultProvider(
            registry,
            AsyncLensEvaluator(Executor { it.run() }),
            contextChanges = changes,
            context = { LensEvaluationContext(0L, exclusions = rules) },
        )

    private fun observe(changes: ContextChanges?) =
        provider(changes).observe(Lens(sources = listOf(id))) { output ->
            (output.result as? LensResult.Flat)?.let { seen += it.items.map { item -> item.id.value } }
        }

    @Test
    fun `a rule change re-evaluates the live observation without touching the source`() {
        observe(signal)
        assertEquals(listOf("a", "b"), seen.last())
        val before = seen.size

        rules = ExclusionRuleSet(listOf(SourceExclusionRule(ExclusionRuleId("r"), id, ExclusionMatcher.App("chat"))))
        signal.fire()

        assertEquals(before + 1, seen.size)
        assertEquals(listOf("b"), seen.last())

        rules = ExclusionRuleSet.EMPTY
        signal.fire()
        assertEquals(listOf("a", "b"), seen.last())
    }

    @Test
    fun `cancelling detaches from the signal and nothing is delivered afterwards`() {
        val handle = observe(signal)
        assertEquals(1, signal.listeners.size)
        val before = seen.size

        handle.cancel()
        signal.fire()

        assertTrue(signal.listeners.isEmpty())
        assertEquals(before, seen.size)
    }

    @Test
    fun `without a signal nothing changes and a rule change alone is not observed`() {
        observe(null)
        val before = seen.size
        rules = ExclusionRuleSet(listOf(SourceExclusionRule(ExclusionRuleId("r"), id, ExclusionMatcher.App("chat"))))
        signal.fire()
        assertEquals(before, seen.size)
    }

    @Test
    fun `observers of different lenses each re-evaluate`() {
        observe(signal)
        observe(signal)
        assertEquals(2, signal.listeners.size)
        val before = seen.size
        rules = ExclusionRuleSet(listOf(SourceExclusionRule(ExclusionRuleId("r"), id, ExclusionMatcher.App("mail"))))

        signal.fire()

        assertEquals(before + 2, seen.size)
        assertEquals(listOf("a"), seen.last())
    }
}
