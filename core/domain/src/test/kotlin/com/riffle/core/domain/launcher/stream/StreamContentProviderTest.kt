package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.container.ContextChanges
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.sources.DelayedTaskScheduler
import com.riffle.core.domain.launcher.workspace.sources.SharedSourceStream
import com.riffle.core.domain.launcher.workspace.sources.SourceSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StreamContentProviderTest {
    private class FakeRegistry : SourceRegistry {
        val sinks = HashMap<SourceId, SourceSink>()
        val connects = HashMap<SourceId, Int>()

        override fun descriptors(): List<SourceDescriptor> = emptyList()

        override fun source(id: SourceId): ItemSource =
            SharedSourceStream(SourceDescriptor(id)) { sink ->
                connects[id] = (connects[id] ?: 0) + 1
                sinks[id] = sink
                SourceSubscription { sinks.remove(id) }
            }
    }

    private class Changes : ContextChanges {
        var onChange: (() -> Unit)? = null

        override fun observe(onChange: () -> Unit): SourceSubscription {
            this.onChange = onChange
            return SourceSubscription { this.onChange = null }
        }
    }

    private val registry = FakeRegistry()
    private var exclusions = ExclusionRuleSet.EMPTY
    private val changes = Changes()
    private val provider =
        StreamContentProvider(registry, contextChanges = changes) {
            StreamContext(nowEpochMillis = NOW, exclusions = exclusions)
        }

    private fun collect(section: StreamSection): Pair<MutableList<StreamSectionResult>, StreamSubscription> {
        val results = mutableListOf<StreamSectionResult>()
        return results to provider.observe(section) { results += it }
    }

    @Test
    fun `a section starts loading and follows its source`() {
        val (results, _) = collect(StreamSection.NOTIFICATIONS)
        assertEquals(StreamSectionResult.Loading, results.last())

        registry.sinks.getValue(SourceIds.NOTIFICATIONS).emit(SourceState.PermissionRequired)
        assertEquals(StreamSectionResult.PermissionRequired(listOf(SourceIds.NOTIFICATIONS)), results.last())

        registry.sinks.getValue(SourceIds.NOTIFICATIONS).emit(SourceState.Ready(listOf(notificationItem("a"))))
        val ready = assertIs<StreamSectionResult.Ready>(results.last())
        assertEquals("a", (ready.items.single() as NotificationContent).title?.removePrefix("Title "))
    }

    private class ManualScheduler : DelayedTaskScheduler {
        val tasks = ArrayList<Pair<Long, Runnable>>()
        var cancelled = 0

        override fun schedule(
            delayMillis: Long,
            task: Runnable,
        ): SourceSubscription {
            tasks += delayMillis to task
            return SourceSubscription { cancelled++ }
        }

        fun fireAll() = tasks.map { it.second }.forEach(Runnable::run)
    }

    @Test
    fun `a section that gets no first answer in time becomes unavailable, and a late answer still shows`() {
        val scheduler = ManualScheduler()
        val results = mutableListOf<StreamSectionResult>()
        val timed =
            StreamContentProvider(registry, scheduler = scheduler) { StreamContext(nowEpochMillis = NOW) }
        timed.observe(StreamSection.NOTIFICATIONS) { results += it }
        assertEquals(StreamSectionResult.Loading, results.last())
        assertEquals(StreamLoadingRule.TIMEOUT_MILLIS, scheduler.tasks.single().first)

        scheduler.fireAll()
        assertEquals(StreamSectionResult.Unavailable, results.last())

        registry.sinks.getValue(SourceIds.NOTIFICATIONS).emit(SourceState.Ready(listOf(notificationItem("a"))))
        assertIs<StreamSectionResult.Ready>(results.last())
    }

    @Test
    fun `a section that answers in time is never touched by the deadline, and cancelling drops the deadline`() {
        val scheduler = ManualScheduler()
        val results = mutableListOf<StreamSectionResult>()
        val timed =
            StreamContentProvider(registry, scheduler = scheduler) { StreamContext(nowEpochMillis = NOW) }
        val subscription = timed.observe(StreamSection.NOTIFICATIONS) { results += it }
        registry.sinks.getValue(SourceIds.NOTIFICATIONS).emit(SourceState.PermissionRequired)
        scheduler.fireAll()
        assertIs<StreamSectionResult.PermissionRequired>(results.last())

        subscription.cancel()
        assertTrue(scheduler.cancelled >= 1)
    }

    @Test
    fun `two sections reading one source share one upstream`() {
        collect(StreamSection.NOW)
        collect(StreamSection.TODAY)
        collect(StreamSection.TODAY)
        assertEquals(1, registry.connects[SourceIds.CALENDAR])
        assertEquals(1, registry.connects[StreamSourceIds.AGENDA])
        assertEquals(1, registry.connects[SourceIds.ICS])
    }

    @Test
    fun `an exclusion change re-evaluates without touching the sources`() {
        val (results, _) = collect(StreamSection.NOTIFICATIONS)
        registry.sinks.getValue(SourceIds.NOTIFICATIONS).emit(SourceState.Ready(listOf(notificationItem("a"))))
        assertIs<StreamSectionResult.Ready>(results.last())

        exclusions =
            ExclusionRuleSet(
                listOf(
                    SourceExclusionRule(
                        ExclusionRuleId("r"),
                        SourceIds.NOTIFICATIONS,
                        ExclusionMatcher.App("chat", "personal"),
                    ),
                ),
            )
        changes.onChange!!.invoke()

        assertEquals(StreamSectionResult.Empty, results.last())
        assertEquals(1, registry.connects[SourceIds.NOTIFICATIONS])
    }

    @Test
    fun `cancelling detaches from the sources and the changes and delivers nothing more`() {
        val (results, subscription) = collect(StreamSection.NOTIFICATIONS)
        val sink = registry.sinks.getValue(SourceIds.NOTIFICATIONS)
        subscription.cancel()
        val count = results.size

        sink.emit(SourceState.Ready(listOf(notificationItem("a"))))

        assertEquals(count, results.size)
        assertEquals(null, changes.onChange)
        assertEquals(false, registry.sinks.containsKey(SourceIds.NOTIFICATIONS))
    }

    @Test
    fun `the today section reads not connected by default and the now section needs no alarm source`() {
        val (today, _) = collect(StreamSection.TODAY)
        registry.sinks.getValue(StreamSourceIds.AGENDA).emit(SourceState.Ready(emptyList()))
        registry.sinks.getValue(SourceIds.ICS).emit(SourceState.Ready(emptyList()))
        assertEquals(StreamSectionResult.NotConnected(), today.last())

        val (now, _) = collect(StreamSection.NOW)
        registry.sinks.getValue(SourceIds.MEDIA).emit(SourceState.Ready(emptyList()))
        registry.sinks.getValue(SourceIds.CALENDAR).emit(SourceState.Ready(emptyList()))
        registry.sinks.getValue(SourceIds.ICS).emit(SourceState.Ready(emptyList()))
        assertEquals(StreamSectionResult.Empty, now.last())
    }

    @Test
    fun `a task provider feeds the today section and an evaluation failure reads unavailable`() {
        var push: ((TypedSourceState<TaskContent>) -> Unit)? = null
        val tasks =
            object : TaskProvider {
                override fun subscribe(observer: (TypedSourceState<TaskContent>) -> Unit): SourceSubscription {
                    push = observer
                    observer(TypedSourceState.Loading)
                    return SourceSubscription { push = null }
                }
            }
        var fail = false
        val custom =
            StreamContentProvider(registry, tasks = tasks) {
                check(!fail) { "context failed" }
                StreamContext(nowEpochMillis = NOW)
            }
        val results = mutableListOf<StreamSectionResult>()
        custom.observe(StreamSection.TODAY) { results += it }

        push!!(TypedSourceState.Ready(listOf(TaskContent(StreamContentId("t"), "Pay rent"))))
        assertIs<StreamSectionResult.Ready>(results.last())

        fail = true
        push!!(TypedSourceState.Ready(emptyList()))
        assertEquals(StreamSectionResult.Unavailable, results.last())
    }
}
