package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.rss.FeedRefreshConstraints
import com.riffle.core.domain.launcher.rss.FeedRefreshDecision
import com.riffle.core.domain.launcher.rss.FeedRefreshPolicy
import com.riffle.core.domain.launcher.rss.FeedRefreshTrigger
import com.riffle.core.domain.launcher.workspace.ItemTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TaskMappingTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z").toEpochMilli()

    private fun remote(
        id: String,
        date: String? = "2026-11-12",
        at: String? = null,
        priority: Int = 1,
    ) = RemoteTask(id, "Task $id", date?.let(LocalDate::parse), at?.let(Instant::parse), priority, "p1", "Home")

    @Test
    fun mapsFieldsAndFlagsOverdue() {
        val overdueDay = TaskMapping.toContent(remote("a", "2026-11-11"), now, ZoneOffset.UTC)
        val today = TaskMapping.toContent(remote("b"), now, ZoneOffset.UTC)
        val earlier = TaskMapping.toContent(remote("c", "2026-11-12", "2026-11-12T08:00:00Z"), now, ZoneOffset.UTC)
        val later = TaskMapping.toContent(remote("d", "2026-11-12", "2026-11-12T18:00:00Z"), now, ZoneOffset.UTC)
        assertTrue(overdueDay.overdue)
        assertFalse(today.overdue)
        assertTrue(earlier.overdue && earlier.dueHasTime)
        assertFalse(later.overdue)
        assertEquals("Home", today.listName)
        assertEquals(StreamContentId("todoist:b"), today.id)
        assertEquals(ItemTarget.DeepLink("https://app.todoist.com/app/task/b"), today.target)
    }

    @Test
    fun ordersOverdueFirstThenDueThenPriority() {
        val tasks =
            listOf(
                remote("later", "2026-11-12", "2026-11-12T18:00:00Z"),
                remote("urgentToday", "2026-11-12", priority = 4),
                remote("overdue", "2026-11-10"),
                remote("normalToday", "2026-11-12"),
                remote("nodue", null),
            ).map { TaskMapping.toContent(it, now, ZoneOffset.UTC) }
        val ordered = TaskMapping.order(tasks).map { it.id.value.removePrefix("todoist:") }
        assertEquals(listOf("overdue", "urgentToday", "normalToday", "later", "nodue"), ordered)
    }

    @Test
    fun capsTheList() {
        val tasks = (1..10).map { TaskMapping.toContent(remote("t$it"), now, ZoneOffset.UTC) }
        assertEquals(3, TaskMapping.order(tasks, 3).size)
    }

    @Test
    fun clampsPriority() {
        assertEquals(4, TaskMapping.toContent(remote("x", priority = 9), now, ZoneOffset.UTC).priority)
    }
}

class TodoistTaskProviderTest {
    private class FakeApi : TaskApi {
        var list: TaskApiResult<List<RemoteTask>> = TaskApiResult.Ok(emptyList())
        var complete: TaskApiResult<Unit> = TaskApiResult.Ok(Unit)
        var add: TaskApiResult<Unit> = TaskApiResult.Ok(Unit)
        var listCalls = 0
        val completed = ArrayList<String>()
        val added = ArrayList<String>()

        override fun listDueTasks(): TaskApiResult<List<RemoteTask>> = list.also { listCalls++ }

        override fun complete(id: String): TaskApiResult<Unit> = complete.also { completed += id }

        override fun add(text: String): TaskApiResult<Unit> = add.also { added += text }
    }

    private class ManualTimer : TaskTimer {
        var task: Runnable? = null
        var cancelled = 0

        override fun every(
            periodMillis: Long,
            task: Runnable,
        ): () -> Unit {
            this.task = task
            return { cancelled++ }
        }
    }

    private val api = FakeApi()
    private val timer = ManualTimer()
    private val status = TaskConnectionStatus()
    private var token = true
    private var clockMillis = Instant.parse("2026-11-12T10:00:00Z").toEpochMilli()
    private var constraintsBlock = false
    private val states = ArrayList<TypedSourceState<TaskContent>>()
    private val policy =
        object : FeedRefreshPolicy {
            override fun decide(
                trigger: FeedRefreshTrigger,
                constraints: FeedRefreshConstraints,
            ) = if (constraintsBlock) FeedRefreshDecision.SUPPRESSED_METERED_NETWORK else FeedRefreshDecision.ALLOWED
        }
    private val direct = Executor { it.run() }
    private val provider =
        TodoistTaskProvider(
            api = api,
            hasToken = { token },
            status = status,
            executor = direct,
            timer = timer,
            policy = policy,
            clock = { clockMillis },
            zone = { ZoneOffset.UTC },
        )

    private fun remote(id: String) = RemoteTask(id, "Task $id", LocalDate.parse("2026-11-12"), null, 1, null)

    private fun observe() = provider.subscribe { states += it }

    private fun ready() = states.last() as TypedSourceState.Ready<TaskContent>

    @Test
    fun noTokenIsNotConnectedAndMakesNoRequest() {
        token = false
        observe()
        assertEquals(TypedSourceState.NotConnected(NotConnectedReason.NO_ACCOUNT), states.last())
        assertEquals(0, api.listCalls)
        assertEquals(TaskConnectionState.NOT_CONNECTED, status.current)
    }

    @Test
    fun opensWithTasksOnObserve() {
        api.list = TaskApiResult.Ok(listOf(remote("1"), remote("2")))
        observe()
        assertEquals(2, ready().values.size)
        assertEquals(TaskConnectionState.CONNECTED, status.current)
    }

    @Test
    fun failureWithCacheKeepsTasksMarkedStale() {
        api.list = TaskApiResult.Ok(listOf(remote("1")))
        observe()
        api.list = TaskApiResult.Offline
        provider.refresh()
        assertTrue(ready().values.single().stale)
        api.list = TaskApiResult.Ok(listOf(remote("1")))
        provider.refresh()
        assertFalse(ready().values.single().stale)
    }

    @Test
    fun failureWithoutDataIsUnavailable() {
        api.list = TaskApiResult.Failed
        observe()
        assertEquals(TypedSourceState.Unavailable, states.last())
    }

    @Test
    fun authFailureIsNotConnectedWithReasonAndStopsRequests() {
        api.list = TaskApiResult.AuthRejected
        observe()
        assertEquals(TypedSourceState.NotConnected(NotConnectedReason.TOKEN_REJECTED), states.last())
        assertEquals(TaskConnectionState.TOKEN_REJECTED, status.current)
        provider.refresh()
        assertEquals(1, api.listCalls)
        api.list = TaskApiResult.Ok(listOf(remote("1")))
        provider.tokenChanged()
        assertEquals(1, ready().values.size)
        assertEquals(TaskConnectionState.CONNECTED, status.current)
    }

    @Test
    fun removingTheTokenReadsNotConnected() {
        api.list = TaskApiResult.Ok(listOf(remote("1")))
        observe()
        token = false
        provider.tokenChanged()
        assertEquals(TypedSourceState.NotConnected(), states.last())
    }

    @Test
    fun completeIsOptimisticThenRemovedOnSuccess() {
        api.list = TaskApiResult.Ok(listOf(remote("1"), remote("2")))
        observe()
        val first = ready().values.first()
        val seenDuring = ArrayList<Boolean>()
        provider.subscribe { state ->
            (state as? TypedSourceState.Ready)?.values?.firstOrNull { it.id == first.id }
                ?.let { seenDuring += it.completed }
        }
        api.list = TaskApiResult.Ok(listOf(remote("2")))
        assertEquals(TaskActionOutcome.DONE, provider.complete(first))
        assertEquals(listOf("1"), api.completed)
        assertTrue(true in seenDuring)
        assertEquals(listOf("todoist:2"), ready().values.map { it.id.value })
    }

    @Test
    fun completeFailureRollsBack() {
        api.list = TaskApiResult.Ok(listOf(remote("1")))
        observe()
        api.complete = TaskApiResult.Offline
        val outcome = provider.complete(ready().values.single())
        assertEquals(TaskActionOutcome.OFFLINE, outcome)
        assertFalse(ready().values.single().completed)
    }

    @Test
    fun completeAuthFailureRejectsToken() {
        api.list = TaskApiResult.Ok(listOf(remote("1")))
        observe()
        api.complete = TaskApiResult.AuthRejected
        assertEquals(TaskActionOutcome.AUTH_REJECTED, provider.complete(ready().values.single()))
        assertIs<TypedSourceState.NotConnected>(states.last())
    }

    @Test
    fun addRefreshesAndBlankIsRefused() {
        observe()
        api.list = TaskApiResult.Ok(listOf(remote("9")))
        assertEquals(TaskActionOutcome.FAILED, provider.add("   "))
        assertEquals(TaskActionOutcome.DONE, provider.add("  Buy milk "))
        assertEquals(listOf("Buy milk"), api.added)
        assertEquals(1, ready().values.size)
        api.add = TaskApiResult.Failed
        assertEquals(TaskActionOutcome.FAILED, provider.add("x"))
    }

    @Test
    fun openAndScheduledRefreshesHonourGapAndPolicy() {
        api.list = TaskApiResult.Ok(listOf(remote("1")))
        val subscription = observe()
        assertEquals(1, api.listCalls)
        timer.task!!.run() // within the minimum gap
        assertEquals(1, api.listCalls)
        clockMillis += 60_000
        constraintsBlock = true
        timer.task!!.run() // metered or battery saver
        assertEquals(1, api.listCalls)
        provider.refresh() // an explicit refresh always goes
        assertEquals(2, api.listCalls)
        constraintsBlock = false
        clockMillis += 60_000
        timer.task!!.run()
        assertEquals(3, api.listCalls)
        subscription.cancel()
        assertEquals(1, timer.cancelled)
    }
}

class TaskActionPolicyTest {
    private val task = TaskContent(StreamContentId("todoist:1"), "A")

    @Test
    fun taskOffersCompleteAndOpenUnlessRedactedOrDone() {
        assertTrue(StreamActionPolicy.offers(task, StreamAction.Complete))
        assertTrue(StreamActionPolicy.offers(task, StreamAction.Open))
        assertFalse(StreamActionPolicy.offers(task.copy(completed = true), StreamAction.Complete))
        val hidden = task.copy(privacy = StreamPrivacy.REDACTED, actions = emptySet())
        assertFalse(StreamActionPolicy.offers(hidden, StreamAction.Complete))
    }
}
