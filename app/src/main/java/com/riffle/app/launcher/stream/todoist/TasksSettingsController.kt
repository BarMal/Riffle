package com.riffle.app.launcher.stream.todoist

import com.riffle.core.domain.launcher.stream.TaskConnectionState
import com.riffle.core.domain.launcher.stream.TaskConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

/** What Settings > Stream > Tasks shows. Never holds the token: after saving, the field is cleared. */
data class TasksSettingsState(val connection: TaskConnectionState = TaskConnectionState.NOT_CONNECTED)

/**
 * Settings > Stream > Tasks: save or remove the Todoist token. The store (a Keystore operation) is touched on
 * [executor], never the main thread. [onTokenChanged] tells the task provider to forget its tasks and re-read.
 */
class TasksSettingsController(
    private val store: TodoistTokenStore,
    private val status: TaskConnectionStatus,
    private val executor: Executor,
    private val onTokenChanged: () -> Unit,
) {
    private val mutableState = MutableStateFlow(TasksSettingsState(status.current))
    val state: StateFlow<TasksSettingsState> = mutableState

    private val stopObserving = status.observe { mutableState.value = TasksSettingsState(it) }

    /** Pastes often carry spaces and line breaks: they are trimmed. Blank input is ignored. */
    fun save(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return
        executor.execute {
            store.save(TodoistToken(trimmed))
            status.set(TaskConnectionState.CONNECTED)
            onTokenChanged()
        }
    }

    fun remove() {
        executor.execute {
            store.clear()
            status.set(TaskConnectionState.NOT_CONNECTED)
            onTokenChanged()
        }
    }

    fun close() = stopObserving()
}
