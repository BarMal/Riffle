package com.riffle.app.launcher

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HomeLayoutWriteQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

internal const val DEFAULT_HOME_LAYOUT_WRITE_DEBOUNCE_MILLIS = 250L

/**
 * A [HomeLayoutRepository] whose reads and writes are in memory and whose storage is written behind.
 *
 * Loaded once, from [initialLayoutSet]; after that the in-memory copy is authoritative and storage
 * is never read back. Each save replaces it at once and schedules a write on [scope] (expected to
 * run off the main thread) after [debounceMillis], so a burst of edits costs one write. Writes are
 * serialized and always persist the newest set, so they may be coalesced but never land out of
 * order. [flush] writes whatever is pending without waiting for the debounce; `onStop` does the same
 * but blocks until the write has landed (see [flushHomeLayoutBlocking]).
 */
internal class WriteBehindHomeLayoutRepository(
    initialLayoutSet: HomeLayoutSet?,
    private val persist: suspend (HomeLayoutSet) -> Unit,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_HOME_LAYOUT_WRITE_DEBOUNCE_MILLIS,
    private val onWriteFailed: (IOException) -> Unit = {},
) : HomeLayoutRepository,
    DefaultLifecycleObserver {
    private val queue = HomeLayoutWriteQueue(initialLayoutSet)
    private val writeMutex = Mutex()

    val hasPendingWrite: Boolean
        get() = queue.hasPendingWrite

    override fun loadHomeLayout(): HomeLayout? = queue.current()?.activeLayout

    override fun saveHomeLayout(layout: HomeLayout) {
        saveHomeLayoutSet(
            queue.current()?.withActiveLayout(layout) ?: HomeLayoutSet.fromLayout(layout),
        )
    }

    override fun loadHomeLayoutSet(): HomeLayoutSet? = queue.current()

    override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
        val version = queue.enqueue(layoutSet)
        scope.launch {
            delay(debounceMillis)
            // A newer save scheduled its own write; let that one carry both.
            if (queue.isLatest(version)) {
                writePending()
            }
        }
    }

    /** Writes the newest unsaved layout set now, skipping the debounce. */
    fun flush(): Job = scope.launch { writePending() }

    /** Writes the newest unsaved layout set, suspending until it has landed (or failed). */
    suspend fun flushNow() {
        writePending()
    }

    override fun onStop(owner: LifecycleOwner) {
        // Once onStop returns, a backgrounded launcher may be killed at any moment with no grace
        // period, so a fire-and-forget flush could still lose the last edit. Wait for it instead.
        flushHomeLayoutBlocking(this)
    }

    private suspend fun writePending() {
        writeMutex.withLock {
            val pending = queue.pendingWrite() ?: return
            try {
                persist(pending.layoutSet)
                queue.markWritten(pending.version)
            } catch (failure: IOException) {
                // Left pending: the next save or flush retries with whatever is newest by then.
                onWriteFailed(failure)
            }
        }
    }
}
