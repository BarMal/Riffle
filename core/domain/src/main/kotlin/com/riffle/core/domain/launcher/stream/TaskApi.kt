package com.riffle.core.domain.launcher.stream

import java.time.Instant
import java.time.LocalDate

/**
 * One task as the remote service reports it, before it becomes [TaskContent]. [dueInstant] is set when the due value
 * carries a time of day; [dueDate] is the calendar day either way. Transient: never persisted or logged.
 */
data class RemoteTask(
    val id: String,
    val title: String,
    val dueDate: LocalDate?,
    val dueInstant: Instant?,
    val priority: Int,
    val projectId: String?,
    val projectName: String? = null,
)

sealed interface TaskApiResult<out T> {
    data class Ok<T>(val value: T) : TaskApiResult<T>

    /** 401 or 403: the token is not accepted. */
    data object AuthRejected : TaskApiResult<Nothing>

    /** No response: offline, DNS, timeout. */
    data object Offline : TaskApiResult<Nothing>

    /** Any other failure (server error, rate limit, unreadable answer). */
    data object Failed : TaskApiResult<Nothing>
}

/** The small surface the Tasks section needs from a task service. Blocking: call off the main thread. */
interface TaskApi {
    /** Open tasks due today or overdue, all pages, with project names filled in when they are cheap to get. */
    fun listDueTasks(): TaskApiResult<List<RemoteTask>>

    fun complete(id: String): TaskApiResult<Unit>

    /** Quick-add: [text] becomes a task due today. */
    fun add(text: String): TaskApiResult<Unit>
}

enum class TaskActionOutcome {
    DONE,

    /** The service could not be reached; nothing changed. */
    OFFLINE,

    /** The token was rejected; nothing changed. */
    AUTH_REJECTED,

    FAILED,
}

/** What the Stream does to tasks beyond reading them. Blocking: call off the main thread. */
interface TaskActions {
    /** Ticks [task] off at once and undoes that if the service refuses. */
    fun complete(task: TaskContent): TaskActionOutcome

    fun add(text: String): TaskActionOutcome

    /** An explicit refresh (the user asked for it). */
    fun refresh()
}

/** Whether a token is saved and whether the service accepted it. Written by the provider and the settings page. */
enum class TaskConnectionState {
    NOT_CONNECTED,
    CONNECTED,
    TOKEN_REJECTED,
}

class TaskConnectionStatus(initial: TaskConnectionState = TaskConnectionState.NOT_CONNECTED) {
    private val lock = Any()
    private val observers = ArrayList<(TaskConnectionState) -> Unit>()

    @Volatile var current: TaskConnectionState = initial
        private set

    fun set(state: TaskConnectionState) {
        val targets =
            synchronized(lock) {
                if (current == state) return
                current = state
                observers.toList()
            }
        targets.forEach { it(state) }
    }

    /** Calls [onChange] with every later change; the returned function stops it. */
    fun observe(onChange: (TaskConnectionState) -> Unit): () -> Unit {
        synchronized(lock) { observers += onChange }
        return { synchronized(lock) { observers -= onChange } }
    }
}

/** Where a task opens: the Todoist app when installed, else the web. */
object TodoistLinks {
    private const val ID_PREFIX = "todoist:"

    fun contentId(remoteId: String): StreamContentId = StreamContentId(ID_PREFIX + remoteId)

    fun remoteId(content: StreamContentId): String? =
        content.value.removePrefix(ID_PREFIX).takeIf {
            content.value.startsWith(ID_PREFIX)
        }

    fun appLink(remoteId: String): String = "todoist://task?id=$remoteId"

    fun webLink(remoteId: String): String = "https://app.todoist.com/app/task/$remoteId"
}
