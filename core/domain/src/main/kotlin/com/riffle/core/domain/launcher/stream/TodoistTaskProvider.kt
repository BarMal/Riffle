package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.rss.DefaultFeedRefreshPolicy
import com.riffle.core.domain.launcher.rss.FeedRefreshConstraints
import com.riffle.core.domain.launcher.rss.FeedRefreshDecision
import com.riffle.core.domain.launcher.rss.FeedRefreshPolicy
import com.riffle.core.domain.launcher.rss.FeedRefreshTrigger
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import java.time.ZoneId
import java.util.concurrent.Executor

/** Runs a task every [period] while it is not cancelled. The app backs it with a scheduled executor. */
fun interface TaskTimer {
    fun every(
        periodMillis: Long,
        task: Runnable,
    ): () -> Unit
}

/**
 * The Tasks section's source and its actions, over a [TaskApi].
 *
 * One upstream however many sections observe it ([SharedTypedSource]). Network calls run on [executor] (refreshes) or
 * on the caller's thread (actions, which the Stream already runs off the main thread); one request is in flight at a
 * time. A refresh happens when the Stream starts observing, when the user asks, after an action, and every
 * [intervalMillis] while observed. Only the periodic one follows the RSS policy (metered network and battery saver
 * hold it back, see [FeedRefreshPolicy]); nothing polls while nobody observes.
 *
 * States: no token reads [TypedSourceState.NotConnected]; a 401 or 403 reads NotConnected with
 * [NotConnectedReason.TOKEN_REJECTED] and stops all requests until [tokenChanged]; a failed refresh keeps the last
 * fetched tasks marked [TaskContent.stale], or reads [TypedSourceState.Unavailable] if there are none. The tasks are
 * held in memory only, never persisted.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class TodoistTaskProvider(
    private val api: TaskApi,
    private val hasToken: () -> Boolean,
    private val status: TaskConnectionStatus,
    private val executor: Executor,
    private val timer: TaskTimer,
    private val policy: FeedRefreshPolicy = DefaultFeedRefreshPolicy,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val limit: Int = TaskMapping.DEFAULT_LIMIT,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val minGapMillis: Long = DEFAULT_MIN_GAP_MILLIS,
) : TaskProvider, TaskActions {
    private enum class Trigger { OPEN, USER, SCHEDULED }

    private val lock = Any()
    private val opLock = Any()
    private val emitLock = Any()
    private var cache: List<TaskContent>? = null
    private var stale = false
    private var failed = false
    private var rejected = false
    private var lastAttempt: Long? = null
    private val pending = HashSet<StreamContentId>()

    @Volatile private var emitter: ((TypedSourceState<TaskContent>) -> Unit)? = null

    private val shared =
        SharedTypedSource<TaskContent> { emit ->
            emitter = emit
            val cancelTimer = timer.every(intervalMillis) { executor.execute { refreshNow(Trigger.SCHEDULED) } }
            executor.execute { refreshNow(Trigger.OPEN) }
            SourceSubscription {
                cancelTimer()
                emitter = null
            }
        }

    override fun subscribe(observer: (TypedSourceState<TaskContent>) -> Unit): SourceSubscription =
        shared.subscribe(observer)

    override fun refresh() {
        executor.execute { refreshNow(Trigger.USER) }
    }

    /** The token was saved, replaced or removed: forget everything and read the new state. */
    fun tokenChanged() {
        synchronized(lock) { reset() }
        executor.execute { refreshNow(Trigger.USER) }
    }

    override fun complete(task: TaskContent): TaskActionOutcome {
        val remoteId = TodoistLinks.remoteId(task.id) ?: return TaskActionOutcome.FAILED
        synchronized(lock) { pending += task.id }
        publish()
        val result = synchronized(opLock) { api.complete(remoteId) }
        synchronized(lock) {
            pending -= task.id
            if (result is TaskApiResult.Ok) cache = cache?.filterNot { it.id == task.id }
        }
        if (result is TaskApiResult.AuthRejected) markRejected()
        publish()
        if (result is TaskApiResult.Ok) refresh()
        return outcome(result)
    }

    override fun add(text: String): TaskActionOutcome {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return TaskActionOutcome.FAILED
        val result = synchronized(opLock) { api.add(trimmed) }
        if (result is TaskApiResult.AuthRejected) markRejected().also { publish() }
        if (result is TaskApiResult.Ok) refresh()
        return outcome(result)
    }

    private fun outcome(result: TaskApiResult<*>): TaskActionOutcome =
        when (result) {
            is TaskApiResult.Ok -> TaskActionOutcome.DONE
            TaskApiResult.AuthRejected -> TaskActionOutcome.AUTH_REJECTED
            TaskApiResult.Offline -> TaskActionOutcome.OFFLINE
            TaskApiResult.Failed -> TaskActionOutcome.FAILED
        }

    private fun markRejected() {
        synchronized(lock) {
            rejected = true
            cache = null
            pending.clear()
        }
        status.set(TaskConnectionState.TOKEN_REJECTED)
    }

    private fun reset() {
        cache = null
        stale = false
        failed = false
        rejected = false
        lastAttempt = null
        pending.clear()
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun refreshNow(trigger: Trigger) {
        synchronized(opLock) {
            if (!hasToken()) {
                synchronized(lock) { reset() }
                status.set(TaskConnectionState.NOT_CONNECTED)
                publish()
                return
            }
            if (synchronized(lock) { rejected }) {
                publish()
                return
            }
            val now = clock()
            val sinceLast = synchronized(lock) { lastAttempt?.let { now - it } }
            val gapped = trigger != Trigger.USER && sinceLast != null && sinceLast < minGapMillis
            if (gapped || trigger == Trigger.SCHEDULED && !scheduledAllowed()) {
                publish()
                return
            }
            synchronized(lock) { lastAttempt = now }
            status.set(TaskConnectionState.CONNECTED)
            when (val result = api.listDueTasks()) {
                is TaskApiResult.Ok -> {
                    val tasks = TaskMapping.order(result.value.map { TaskMapping.toContent(it, now, zone()) }, limit)
                    synchronized(lock) {
                        cache = tasks
                        stale = false
                        failed = false
                    }
                }
                TaskApiResult.AuthRejected -> markRejected()
                TaskApiResult.Offline, TaskApiResult.Failed ->
                    synchronized(lock) {
                        if (cache != null) stale = true else failed = true
                    }
            }
            publish()
        }
    }

    private fun scheduledAllowed(): Boolean =
        policy.decide(
            FeedRefreshTrigger.SCHEDULED,
            FeedRefreshConstraints(meteredNetwork = false, batterySaver = false),
        ) == FeedRefreshDecision.ALLOWED

    private fun publish() {
        synchronized(emitLock) {
            val target = emitter ?: return
            target(currentState())
        }
    }

    private fun currentState(): TypedSourceState<TaskContent> =
        when {
            !hasToken() -> TypedSourceState.NotConnected(NotConnectedReason.NO_ACCOUNT)
            else ->
                synchronized(lock) {
                    val tasks = cache
                    when {
                        rejected -> TypedSourceState.NotConnected(NotConnectedReason.TOKEN_REJECTED)
                        tasks != null ->
                            TypedSourceState.Ready(
                                tasks.map { task ->
                                    task.copy(completed = task.id in pending || task.completed, stale = stale)
                                },
                            )
                        failed -> TypedSourceState.Unavailable
                        else -> TypedSourceState.Loading
                    }
                }
        }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5L * 60L * 1000L
        const val DEFAULT_MIN_GAP_MILLIS = 15_000L
    }
}
